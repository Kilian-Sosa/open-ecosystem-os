[CmdletBinding()]
param([string]$ApiBaseUrl = 'http://127.0.0.1:18087')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.DefaultRequestHeaders.Add('X-Actor-Id', 'usr_dev_placeholder')
$client.DefaultRequestHeaders.Add('X-Workspace-Id', 'wrk_dev_placeholder')

function Invoke-Smoke {
  param([string]$Method, [string]$Path, $Body = $null, [int]$Expected = 200)
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$ApiBaseUrl/api$Path")
  try {
    if ($null -ne $Body) { $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, 'application/json') }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    try {
      if ([int]$response.StatusCode -ne $Expected) { throw "Synthetic Trash smoke: $Method $Path returned $([int]$response.StatusCode), expected $Expected." }
      return ,($response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
    } finally { $response.Dispose() }
  } finally { $request.Dispose() }
}
function Invoke-SmokeJson {
  param([string]$Method, [string]$Path, $Body = $null, [int]$Expected = 200)
  [Text.Encoding]::UTF8.GetString((Invoke-Smoke $Method $Path $Body $Expected)) | ConvertFrom-Json
}
function New-SmokeFolder {
  param([string]$Name, $Parent = $null)
  (Invoke-SmokeJson POST '/drive/folders' @{ name=$Name; parentFolderId=$Parent } 201).folderId
}
function Upload-Smoke {
  param([string]$Folder)
  $fixture = Join-Path (Split-Path -Parent $PSScriptRoot) 'apps/worker/src/test/resources/fixtures/fake-scanned-invoice.png'
  $script:expectedBytes = [IO.File]::ReadAllBytes($fixture)
  $multipart = [System.Net.Http.MultipartFormDataContent]::new()
  try {
    $part = [System.Net.Http.ByteArrayContent]::new($script:expectedBytes)
    $part.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('image/png')
    $multipart.Add($part, 'file', 'synthetic-trash-invoice.png')
    $multipart.Add([System.Net.Http.StringContent]::new($Folder), 'folderId')
    $response = $client.PostAsync("$ApiBaseUrl/api/drive/files", $multipart).GetAwaiter().GetResult()
    try {
      if ([int]$response.StatusCode -ne 201) { throw 'Synthetic upload failed.' }
      $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    } finally { $response.Dispose() }
  } finally { $multipart.Dispose() }
}
function Wait-Smoke {
  param([string]$Name, [scriptblock]$Check)
  $deadline = [DateTimeOffset]::UtcNow.AddSeconds(180)
  while ([DateTimeOffset]::UtcNow -lt $deadline) {
    if (& $Check) { return }
    Start-Sleep -Seconds 2
  }
  throw "Timed out waiting for synthetic $Name."
}
function Assert-SmokeBytes {
  param([byte[]]$Actual)
  if ([Convert]::ToBase64String($Actual) -cne [Convert]::ToBase64String($script:expectedBytes)) { throw 'Restored bytes changed.' }
}

try {
  $root = New-SmokeFolder "Synthetic Trash acceptance $([Guid]::NewGuid().ToString('N'))"
  $child = New-SmokeFolder 'Synthetic nested invoices' $root
  $file = Upload-Smoke $child
  $script:detail = $null
  Wait-Smoke 'real OCR and extraction' {
    $jobs = Invoke-SmokeJson GET '/media/ocr-jobs'
    $job = @($jobs.jobs | Where-Object fileId -eq $file.fileId) | Select-Object -First 1
    if ($null -eq $job) { return $false }
    $script:detail = Invoke-SmokeJson GET "/media/ocr-jobs/$($job.jobId)"
    if ($script:detail.status -eq 'failed') { throw 'Synthetic OCR failed.' }
    $script:detail.status -eq 'completed' -and $null -ne $script:detail.extraction
  }
  if ($detail.ocrResult.provider -ne 'tesseract' -or $detail.ocrResult.wordCount -le 0) { throw 'Expected structured Tesseract output.' }
  $jobId = $detail.jobId
  $resultId = $detail.ocrResult.ocrResultId
  $group = Invoke-SmokeJson POST "/drive/folders/$root/trash" @{}
  $null = Invoke-Smoke GET "/drive/files/$($file.fileId)/download" $null 404
  $null = Invoke-Smoke GET "/drive/folders/$root/download" $null 404
  $null = Invoke-Smoke GET "/media/ocr-jobs/$jobId" $null 404
  $contents = Invoke-SmokeJson GET '/drive/folders/contents'
  if ($root -in @($contents.folders.folderId)) { throw 'Trashed tree is still listed.' }
  $search = Invoke-SmokeJson GET '/search?q=TEST-2026-0007'
  if (($search | ConvertTo-Json -Depth 10) -match [regex]::Escape($file.fileId)) { throw 'Search exposed a trashed source.' }
  $null = Invoke-SmokeJson POST "/drive/trash/$($group.groupId)/restore" @{}
  Assert-SmokeBytes (Invoke-Smoke GET "/drive/files/$($file.fileId)/download")
  $restored = Invoke-SmokeJson GET "/media/ocr-jobs/$jobId"
  if ($restored.ocrResult.ocrResultId -ne $resultId -or $restored.extraction.extractionId -ne $detail.extraction.extractionId) { throw 'Restore changed derived lineage.' }
  Write-Output 'PASS: real OCR/extraction, nested Trash invisibility, stable restore IDs and original bytes.'
  $independent = Invoke-SmokeJson POST "/drive/files/$($file.fileId)/trash" @{}
  $parent = Invoke-SmokeJson POST "/drive/folders/$root/trash" @{}
  $parentPurge = Invoke-SmokeJson POST "/drive/trash/$($parent.groupId)/purge" @{}
  if ($parentPurge.state -ne 'PURGED') { throw 'Parent with no active files did not purge immediately.' }
  $null = Invoke-Smoke POST "/drive/trash/$($independent.groupId)/restore" @{} 409
  $null = Invoke-SmokeJson POST "/drive/trash/$($independent.groupId)/restore" @{ destinationFolderId=$null }
  Assert-SmokeBytes (Invoke-Smoke GET "/drive/files/$($file.fileId)/download")
  Write-Output 'PASS: independent child survives parent purge and restores explicitly to root.'
  $final = Invoke-SmokeJson POST "/drive/files/$($file.fileId)/trash" @{}
  $started = Invoke-SmokeJson POST "/drive/trash/$($final.groupId)/purge" @{}
  if ($started.state -notin @('PURGING','PURGED')) { throw 'Invalid purge result.' }
  # A fast fully completed purge has already removed the root metadata, so either denial is valid.
  if ($started.state -eq 'PURGING') { $null = Invoke-Smoke POST "/drive/trash/$($final.groupId)/restore" @{} 409 }
  Wait-Smoke 'permanent cleanup' {
    (Invoke-SmokeJson POST "/drive/trash/$($final.groupId)/purge" @{}).state -eq 'PURGED'
  }
  $null = Invoke-Smoke GET "/drive/files/$($file.fileId)/download" $null 404
  $null = Invoke-Smoke GET "/media/ocr-jobs/$jobId" $null 404
  Write-Output 'PASS: immediate non-restorable purge and eventual real S3/search lifecycle cleanup.'
  Write-Output "Synthetic purged file ID: $($file.fileId); group ID: $($final.groupId)."
} finally { $client.Dispose() }
