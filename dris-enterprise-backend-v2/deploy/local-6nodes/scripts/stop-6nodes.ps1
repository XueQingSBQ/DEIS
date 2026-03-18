$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$labRoot = Resolve-Path (Join-Path $scriptDir "..")
$pidDir = Join-Path $labRoot "runtime\pids"

if (-not (Test-Path $pidDir)) {
    Write-Host "No pid directory found: $pidDir"
    exit 0
}

$pidFiles = Get-ChildItem -Path $pidDir -Filter "*.pid" -File
if (-not $pidFiles) {
    Write-Host "No running node pid files found."
    exit 0
}

foreach ($file in $pidFiles) {
    $nodeName = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    $pidRaw = (Get-Content -Raw $file.FullName).Trim()
    if ($pidRaw -notmatch "^\d+$") {
        Remove-Item -Force $file.FullName
        continue
    }
    $procId = [int]$pidRaw
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "[$nodeName] stopped pid=$procId"
        } catch {
            Write-Host "[$nodeName] failed to stop pid=$procId : $($_.Exception.Message)"
        }
    } else {
        Write-Host "[$nodeName] process not found (pid=$procId)"
    }
    Remove-Item -Force $file.FullName -ErrorAction SilentlyContinue
}

Write-Host "Done."
