[CmdletBinding()]
param(
  [switch]$StartStack,
  [string]$ApiBaseUrl = "http://127.0.0.1:8080",
  [int]$TimeoutSeconds = 240
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot "infra/docker/docker-compose.yml"
$fixture = Join-Path $repositoryRoot "apps/worker/src/test/resources/fixtures/fake-scanned-invoice.png"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

function Wait-Until {
  param([string]$Description, [scriptblock]$Condition)

  while ((Get-Date) -lt $deadline) {
    if (& $Condition) { return }
    Start-Sleep -Seconds 2
  }

  throw "Timed out waiting for $Description."
}

function Invoke-ApiJson {
  param([string]$Path)

  $response = & curl.exe --fail --silent --show-error "$ApiBaseUrl$Path"
  if ($LASTEXITCODE -ne 0) { throw "API request failed for $Path." }
  return $response | ConvertFrom-Json
}

if (-not (Test-Path -LiteralPath $fixture)) {
  throw "The synthetic, test-only OCR fixture is missing: $fixture"
}

Push-Location $repositoryRoot
try {
  if ($StartStack) {
    & docker compose -f $composeFile up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose could not start the OCR smoke stack." }
  }

  Wait-Until "the API health endpoint" {
    try {
      $health = & curl.exe --fail --silent --show-error "$ApiBaseUrl/health"
      return $LASTEXITCODE -eq 0 -and $health
    } catch { return $false }
  }

  & docker compose -f $composeFile exec -T worker tesseract --version
  if ($LASTEXITCODE -ne 0) { throw "The worker container does not provide the Tesseract executable." }
  $languages = & docker compose -f $composeFile exec -T worker tesseract --list-langs
  if ($LASTEXITCODE -ne 0 -or -not ($languages -match "(?m)^eng$")) {
    throw "The worker container does not provide Tesseract English language data."
  }

  $upload = & curl.exe --fail --silent --show-error -F "file=@$fixture;type=image/png" "$ApiBaseUrl/api/drive/files"
  if ($LASTEXITCODE -ne 0) { throw "The synthetic OCR fixture upload failed." }
  $file = $upload | ConvertFrom-Json
  if ([string]::IsNullOrWhiteSpace($file.fileId)) { throw "The Drive upload response did not include a file ID." }

  $script:detail = $null
  Wait-Until "real Tesseract OCR and extraction" {
    $jobs = Invoke-ApiJson "/api/media/ocr-jobs"
    $job = @($jobs.jobs | Where-Object { $_.fileId -eq $file.fileId }) | Select-Object -First 1
    if ($null -eq $job) { return $false }
    if ($job.status -eq "failed") { throw "The OCR job failed with code $($job.failureCode)." }
    $script:detail = Invoke-ApiJson "/api/media/ocr-jobs/$($job.jobId)"
    return $script:detail.status -eq "completed" -and $null -ne $script:detail.ocrResult -and $null -ne $script:detail.extraction
  }

  $detail = $script:detail

  if ($detail.provider -ne "tesseract" -or $detail.ocrResult.provider -ne "tesseract") {
    throw "The smoke result was not produced by the Tesseract provider."
  }
  if ($detail.ocrResult.wordCount -le 0) { throw "The persisted OCR result did not contain structured words." }
  if (-not $detail.extraction.reviewRequired) {
    throw "The synthetic incomplete invoice must remain review-required."
  }

  $fields = @{}
  foreach ($field in $detail.extraction.fields) { $fields[$field.fieldKey] = $field.normalizedValue }
  $expectedFields = @{
    invoice_number = "TEST-2026-0007"
    supplier_name = "Example Test Supplies Ltd"
    total_amount = "123.45"
    currency = "EUR"
    issue_date = "2026-07-10"
  }
  foreach ($key in $expectedFields.Keys) {
    if ($fields[$key] -ne $expectedFields[$key]) {
      throw "The persisted $key field was missing or did not match the synthetic fixture."
    }
  }

  Write-Host "Real Tesseract OCR smoke passed for the clearly labelled synthetic test invoice."
} finally {
  Pop-Location
}
