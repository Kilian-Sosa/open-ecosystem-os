[CmdletBinding()]
param([string]$ApiBaseUrl = "http://127.0.0.1:18087")

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.IO.Compression
$client = [System.Net.Http.HttpClient]::new()
$client.DefaultRequestHeaders.Add("X-Actor-Id", "usr_dev_placeholder")
$client.DefaultRequestHeaders.Add("X-Workspace-Id", "wrk_dev_placeholder")

function Invoke-Drive {
  param([string]$Method, [string]$Path, $Body)
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$ApiBaseUrl/api/drive$Path")
  try {
    if ($null -ne $Body) {
      $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, "application/json")
    }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    try {
      if (-not $response.IsSuccessStatusCode) { throw "Drive smoke failed: $Method $Path returned $([int]$response.StatusCode)." }
      return ,($response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
    } finally { $response.Dispose() }
  } finally { $request.Dispose() }
}

function Invoke-DriveJson {
  param([string]$Method, [string]$Path, $Body)
  $bytes = Invoke-Drive $Method $Path $Body
  return [Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
}

function New-SyntheticFolder {
  param([string]$Name, [AllowNull()][string]$Parent)
  return (Invoke-DriveJson POST /folders @{ name = $Name; parentFolderId = $Parent }).folderId
}

function Add-SyntheticFile {
  param([string]$Name, [string]$Mime, [byte[]]$Bytes, [string]$Folder)
  $multipart = [System.Net.Http.MultipartFormDataContent]::new()
  try {
    $part = [System.Net.Http.ByteArrayContent]::new($Bytes)
    $part.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new($Mime)
    $multipart.Add($part, "file", $Name)
    $multipart.Add([System.Net.Http.StringContent]::new($Folder), "folderId")
    $response = $client.PostAsync("$ApiBaseUrl/api/drive/files", $multipart).GetAwaiter().GetResult()
    try {
      if ([int]$response.StatusCode -ne 201) { throw "Synthetic upload failed: $([int]$response.StatusCode)." }
      return ($response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json)
    } finally { $response.Dispose() }
  } finally { $multipart.Dispose() }
}

function Assert-Bytes {
  param([byte[]]$Actual, [byte[]]$Expected)
  if ([Convert]::ToBase64String($Actual) -cne [Convert]::ToBase64String($Expected)) { throw "Original bytes changed." }
}

try {
  $run = New-SyntheticFolder "Synthetic Drive smoke $([Guid]::NewGuid().ToString('N'))" $null
  $projects = New-SyntheticFolder Projects $run
  $invoices = New-SyntheticFolder Invoices $projects
  $year = New-SyntheticFolder 2026 $invoices
  $null = New-SyntheticFolder Empty $invoices
  $archive = New-SyntheticFolder Archive $run
  $original = [Text.Encoding]::UTF8.GetBytes("Synthetic Drive organization smoke.`nExact original bytes.`n")
  $file = Add-SyntheticFile synthetic-original.txt text/plain $original $year
  $fixture = Join-Path (Split-Path -Parent $PSScriptRoot) "apps/worker/src/test/resources/fixtures/fake-scanned-invoice.png"
  $imageBytes = [IO.File]::ReadAllBytes($fixture)
  $image = Add-SyntheticFile synthetic-image.png image/png $imageBytes $year
  $null = Invoke-DriveJson PATCH "/files/$($file.fileId)/name" @{ name = "synthetic-renamed.txt" }
  $null = Invoke-DriveJson PATCH "/folders/$invoices/name" @{ name = "Receipts" }
  $null = Invoke-DriveJson PATCH "/folders/$invoices/location" @{ destinationFolderId = $archive }
  $null = Invoke-DriveJson PATCH "/files/$($file.fileId)/location" @{ destinationFolderId = $null }
  $root = Invoke-DriveJson GET /folders/contents $null
  if ($file.fileId -notin @($root.files.fileId)) { throw "Moved file is missing at root." }
  $null = Invoke-DriveJson PATCH "/files/$($file.fileId)/location" @{ destinationFolderId = $year }
  $listing = Invoke-DriveJson GET "/folders/contents?folderId=$year" $null
  if ($file.fileId -notin @($listing.files.fileId) -or $image.fileId -notin @($listing.files.fileId)) { throw "Nested files disappeared." }
  Assert-Bytes (Invoke-Drive GET "/files/$($file.fileId)/download" $null) $original
  Assert-Bytes (Invoke-Drive GET "/files/$($file.fileId)/preview" $null) $original
  Assert-Bytes (Invoke-Drive GET "/files/$($image.fileId)/download" $null) $imageBytes
  Assert-Bytes (Invoke-Drive GET "/files/$($image.fileId)/preview" $null) $imageBytes
  $zipBytes = Invoke-Drive GET "/folders/$archive/download" $null
  $stream = [IO.MemoryStream]::new($zipBytes, $false)
  $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read)
  try {
    $expected = @("Receipts/", "Receipts/2026/", "Receipts/Empty/", "Receipts/2026/synthetic-renamed.txt", "Receipts/2026/synthetic-image.png")
    $difference = Compare-Object ($expected | Sort-Object) (@($zip.Entries.FullName) | Sort-Object)
    if ($difference) { throw "ZIP hierarchy differs from the expected synthetic tree." }
    foreach ($item in @(@{ Path = "Receipts/2026/synthetic-renamed.txt"; Bytes = $original }, @{ Path = "Receipts/2026/synthetic-image.png"; Bytes = $imageBytes })) {
      $entryStream = $zip.GetEntry($item.Path).Open()
      $buffer = [IO.MemoryStream]::new()
      try { $entryStream.CopyTo($buffer); Assert-Bytes $buffer.ToArray() $item.Bytes }
      finally { $entryStream.Dispose(); $buffer.Dispose() }
    }
  } finally { $zip.Dispose(); $stream.Dispose() }
  Write-Output "PASS: nested upload, rename, move, text/image preview, original downloads and ZIP bytes ($($expected.Count) entries)."
  Write-Output "Synthetic root: $run; text file: $($file.fileId); image file: $($image.fileId)."
  Write-Output "Use a disposable private stack; this script leaves synthetic files for inspection. Trash and Search indexing are separate subsequent work."
} finally { $client.Dispose() }
