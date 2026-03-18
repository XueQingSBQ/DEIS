param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('auth', 'backend', 'mcp', 'orchestrator', 'frontend')]
    [string]$Service
)

. (Join-Path $PSScriptRoot 'service-common.ps1')
Ensure-StandaloneRuntime
$config = Get-ServiceConfig -Service $Service
$pidPath = Get-PidPath -Config $config
$proc = Get-RunningProcess -Config $config

if (-not $proc) {
    Write-Host "[$($config.Key)] not running"
    exit 0
}

try {
    Stop-ProcessTree -ProcessId $proc.Id
    Start-Sleep -Seconds 2
}
catch {
    Write-Host "[$($config.Key)] failed to stop pid=$($proc.Id): $($_.Exception.Message)"
    exit 1
}

Remove-Item -Path $pidPath -Force -ErrorAction SilentlyContinue
Write-Host "[$($config.Key)] stopped pid=$($proc.Id)"

