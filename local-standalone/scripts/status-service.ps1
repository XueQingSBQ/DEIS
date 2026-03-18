param(
    [ValidateSet('auth', 'backend', 'mcp', 'orchestrator', 'frontend')]
    [string]$Service
)

. (Join-Path $PSScriptRoot 'service-common.ps1')
Ensure-StandaloneRuntime
$targets = @()
if ($Service) {
    $targets += (Get-ServiceConfig -Service $Service)
}
else {
    foreach ($name in @('auth', 'backend', 'mcp', 'orchestrator', 'frontend')) {
        $targets += (Get-ServiceConfig -Service $name)
    }
}

$result = foreach ($config in $targets) {
    $proc = Get-RunningProcess -Config $config
    [pscustomobject]@{
        service = $config.Key
        pid = if ($proc) { $proc.Id } else { $null }
        running = [bool]$proc
        port = $config.Port
        portOpen = Test-PortOpen -Port $config.Port -TimeoutMs 800
        log = Get-LogPath -Config $config
        health = $config.HealthUrl
    }
}

$result | Format-Table -AutoSize
