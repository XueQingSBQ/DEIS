$port = 9200

$procIds = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -eq $port } |
    Select-Object -ExpandProperty OwningProcess -Unique

if (-not $procIds) {
    Write-Host "Elasticsearch is not running on port $port."
    exit 0
}

foreach ($procId in $procIds) {
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 2

$listener = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -eq $port } |
    Select-Object -First 1

if ($listener) {
    Write-Host "Elasticsearch is still listening on port $port (PID=$($listener.OwningProcess))."
    exit 1
}

Write-Host "Elasticsearch stopped."
