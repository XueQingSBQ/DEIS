$ports = 8300, 8301, 8302, 8303
$procIds = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $ports -contains $_.LocalPort } |
    Select-Object -ExpandProperty OwningProcess -Unique

foreach ($procId in $procIds) {
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 2

Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $ports -contains $_.LocalPort } |
    Select-Object LocalPort, OwningProcess |
    Sort-Object LocalPort
