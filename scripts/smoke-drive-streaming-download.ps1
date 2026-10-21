[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$ApiBaseUrl,
  [Parameter(Mandatory = $true)][string]$WebBaseUrl,
  [Parameter(Mandatory = $true)][string]$OutputDirectory,
  [string]$DeniedFolderId
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.IO.Compression
foreach ($base in @($ApiBaseUrl, $WebBaseUrl)) {
  $uri = $null
  if (-not [Uri]::TryCreate($base, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -notin @('http', 'https') -or $uri.AbsolutePath -ne '/' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw "API and web bases must be absolute HTTP(S) origins."
  }
}
$ApiBaseUrl = $ApiBaseUrl.TrimEnd('/')
$WebBaseUrl = $WebBaseUrl.TrimEnd('/')
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputPath) { throw "Use a new task-owned output directory." }
$null = New-Item -ItemType Directory -Path $outputPath

# Generate printable deterministic fixtures in bounded blocks without slow per-byte PowerShell loops.
if (-not ('DriveStreamingSyntheticFixture' -as [type])) {
  Add-Type -TypeDefinition @'
using System;
using System.IO;
public static class DriveStreamingSyntheticFixture {
  public static void Write(string path, int seed) {
    var random = new Random(seed);
    var buffer = new byte[65536];
    using (var output = File.Create(path)) {
      for (int block = 0; block < 256; block++) {
        random.NextBytes(buffer);
        for (int i = 0; i < buffer.Length; i++) buffer[i] = (byte)(32 + buffer[i] % 95);
        output.Write(buffer, 0, buffer.Length);
      }
    }
  }
}
'@
}

$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromMinutes(5)
$identity = @{ 'X-Actor-Id' = 'usr_dev_placeholder'; 'X-Workspace-Id' = 'wrk_dev_placeholder' }

function New-SyntheticFolder {
  $message = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$ApiBaseUrl/api/drive/folders")
  try {
    foreach ($key in $identity.Keys) { $message.Headers.Add($key, $identity[$key]) }
    $body = @{ name = "Synthetic streaming smoke $([Guid]::NewGuid().ToString('N'))"; parentFolderId = $null } | ConvertTo-Json -Compress
    $message.Content = [System.Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, 'application/json')
    $response = $client.SendAsync($message).GetAwaiter().GetResult()
    try {
      if ([int]$response.StatusCode -ne 201) { throw "Synthetic folder creation failed: $([int]$response.StatusCode)." }
      return ($response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json).folderId
    } finally { $response.Dispose() }
  } finally { $message.Dispose() }
}

function Get-StreamHash {
  param([IO.Stream]$Stream)
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return [BitConverter]::ToString($sha.ComputeHash($Stream)).Replace('-', '').ToLowerInvariant() }
  finally { $sha.Dispose() }
}

try {
  $folderId = New-SyntheticFolder
  $expected = @{}
  for ($index = 1; $index -le 3; $index++) {
    $name = "synthetic-streaming-$index.txt"
    $path = Join-Path $outputPath $name
    [DriveStreamingSyntheticFixture]::Write($path, (20261021 + $index))
    $source = [IO.File]::OpenRead($path)
    try { $expected[$name] = Get-StreamHash $source } finally { $source.Dispose() }
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $message = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$ApiBaseUrl/api/drive/files")
    try {
      $part = [System.Net.Http.StreamContent]::new([IO.File]::OpenRead($path))
      $part.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('text/plain')
      $multipart.Add($part, 'file', $name)
      $multipart.Add([System.Net.Http.StringContent]::new($folderId), 'folderId')
      $message.Content = $multipart
      foreach ($key in $identity.Keys) { $message.Headers.Add($key, $identity[$key]) }
      $response = $client.SendAsync($message).GetAwaiter().GetResult()
      try { if ([int]$response.StatusCode -ne 201) { throw "Synthetic upload failed: $([int]$response.StatusCode)." } }
      finally { $response.Dispose() }
    } finally { $message.Dispose(); $multipart.Dispose() }
  }
  $url = "$WebBaseUrl/downloads/drive/folders/$([Uri]::EscapeDataString($folderId))"
  $response = $client.GetAsync($url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
  $zipPath = Join-Path $outputPath 'synthetic-folder.zip'
  try {
    if ([int]$response.StatusCode -ne 200 -or $response.Content.Headers.ContentType.MediaType -ne 'application/zip' -or $response.Content.Headers.ContentDisposition.DispositionType -ne 'attachment') { throw "Expected a successful ZIP attachment." }
    foreach ($pair in @(@('Cache-Control','no-store'), @('X-Content-Type-Options','nosniff'), @('Referrer-Policy','no-referrer'))) {
      if (($response.Headers.GetValues($pair[0]) -join ',') -ne $pair[1]) { throw "Missing safe response header: $($pair[0])." }
    }
    $contentLength = $response.Content.Headers.ContentLength
    $inputStream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $outputStream = [IO.File]::Create($zipPath)
    try { $inputStream.CopyTo($outputStream) }
    finally { $outputStream.Dispose(); $inputStream.Dispose() }
  } finally { $response.Dispose() }
  $zipSize = (Get-Item -LiteralPath $zipPath).Length
  if ($zipSize -le 25MB -or ($null -ne $contentLength -and $zipSize -ne $contentLength)) { throw "ZIP size is incomplete or does not exceed 25 MiB." }
  $zipFile = [IO.File]::OpenRead($zipPath)
  $zip = [IO.Compression.ZipArchive]::new($zipFile, [IO.Compression.ZipArchiveMode]::Read)
  try {
    if ($zip.Entries.Count -ne 3) { throw "Unexpected ZIP entry count." }
    foreach ($entry in $zip.Entries) {
      if (-not $expected.ContainsKey($entry.FullName) -or $entry.Length -ne 16MB) { throw "Unexpected ZIP entry." }
      $stream = $entry.Open()
      try { if ((Get-StreamHash $stream) -cne $expected[$entry.FullName]) { throw "ZIP entry digest mismatch." } }
      finally { $stream.Dispose() }
    }
  } finally { $zip.Dispose(); $zipFile.Dispose() }

  # Provision an inaccessible root separately in the disposable stack; no identity-management API exists.
  $errorIds = @('folder_synthetic_missing')
  if ($DeniedFolderId) { $errorIds += $DeniedFolderId }
  foreach ($id in $errorIds) {
    $errorResponse = $client.GetAsync("$WebBaseUrl/downloads/drive/folders/$id").GetAwaiter().GetResult()
    try {
      if ([int]$errorResponse.StatusCode -ne 404 -or $errorResponse.Content.Headers.ContentDisposition -or $errorResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() -cne 'Folder is not available.') { throw "Expected sanitized non-attachment 404." }
    } finally { $errorResponse.Dispose() }
  }
  $stream = [IO.File]::OpenRead($zipPath)
  try { $digest = Get-StreamHash $stream } finally { $stream.Dispose() }
  $result = @{ folderId = $folderId; zipBytes = $zipSize; zipSha256 = $digest; entries = 3; entryDigests = $expected; url = $url; zipPath = $zipPath; errors = 'missing 404 sanitized, no attachment'; deniedFixtureChecked = [bool]$DeniedFolderId }
  $result | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $outputPath 'result.json')
  Write-Output ($result | ConvertTo-Json -Depth 3 -Compress)
  Write-Output 'PASS: three 16 MiB synthetic originals match streamed ZIP entries. Remove this task-owned output directory and disposable stack after browser verification.'
} finally { $client.Dispose(); $handler.Dispose() }
