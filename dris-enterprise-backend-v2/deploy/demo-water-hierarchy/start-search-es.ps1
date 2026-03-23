$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir "..\..\..\.."))
$esHome = Join-Path $repoRoot "runtime\elastic\elasticsearch-8.19.12"
$esBat = Join-Path $esHome "bin\elasticsearch.bat"
$logDir = Join-Path $scriptDir "runtime\es"
$stdoutLog = Join-Path $logDir "elasticsearch.out.log"
$stderrLog = Join-Path $logDir "elasticsearch.err.log"
$port = 9200

if (-not (Test-Path $esBat)) {
    throw "Elasticsearch not found: $esBat"
}

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

$listener = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -eq $port } |
    Select-Object -First 1

if ($listener) {
    Write-Host "Elasticsearch already running on port $port (PID=$($listener.OwningProcess))."
    exit 0
}

$process = Start-Process -FilePath $esBat `
    -WorkingDirectory (Split-Path -Parent $esBat) `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

$started = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$port" -TimeoutSec 3
        if ($response.version.number) {
            $started = $true
            Write-Host "Elasticsearch started on http://127.0.0.1:$port (PID=$($process.Id), version=$($response.version.number))."
            break
        }
    } catch {
    }
}

if (-not $started) {
    Write-Host "Elasticsearch start timed out. Check logs:"
    Write-Host "  $stdoutLog"
    Write-Host "  $stderrLog"
    exit 1
}
