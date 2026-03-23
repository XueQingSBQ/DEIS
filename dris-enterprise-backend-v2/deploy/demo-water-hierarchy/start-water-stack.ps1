$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

& (Join-Path $scriptDir "start-search-es.ps1")
& (Join-Path $scriptDir "start-water-nodes.cmd")

Write-Host "Waiting for water nodes to become ready..."
Start-Sleep -Seconds 12

& (Join-Path $scriptDir "rebuild-search-index.ps1")

Write-Host ""
Write-Host "Listening ports:"
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { @(8300, 8301, 8302, 8303, 9200) -contains $_.LocalPort } |
    Sort-Object LocalPort |
    Select-Object LocalPort, OwningProcess
