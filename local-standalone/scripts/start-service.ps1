param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('auth', 'backend', 'mcp', 'orchestrator', 'frontend')]
    [string]$Service,
    [switch]$ShowWindow,
    [switch]$ForceRestart,
    [string]$DrisBaseUrl,
    [string]$McpServerUrl,
    [string]$PlannerMode
)

. (Join-Path $PSScriptRoot 'service-common.ps1')
Ensure-StandaloneRuntime
$config = Get-ServiceConfig -Service $Service

if ($Service -eq 'mcp' -and $DrisBaseUrl) {
    $config.Env['DRIS_BASE_URL'] = $DrisBaseUrl.TrimEnd('/')
}
if ($Service -eq 'orchestrator' -and $McpServerUrl) {
    $config.Env['MCP_SERVER_URL'] = $McpServerUrl.TrimEnd('/')
}
if ($Service -eq 'orchestrator' -and $PlannerMode) {
    $config.Env['PLANNER_MODE'] = $PlannerMode
}

$pidPath = Get-PidPath -Config $config
$logPath = Get-LogPath -Config $config
$runnerPath = New-RunnerScript -Config $config
$existing = Get-RunningProcess -Config $config

if ($existing -and -not $ForceRestart) {
    Write-Host "[$($config.Key)] already running pid=$($existing.Id) port=$($config.Port)"
    Write-Host "log: $logPath"
    exit 0
}

if ($existing -and $ForceRestart) {
    Stop-ProcessTree -ProcessId $existing.Id
    Start-Sleep -Seconds 2
    Remove-Item -Path $pidPath -Force -ErrorAction SilentlyContinue
}

if (-not $existing -and (Test-PortOpen -Port $config.Port -TimeoutMs 800)) {
    Write-Host "[$($config.Key)] port $($config.Port) is already in use by another process"
    Write-Host "Use status-service.ps1 first, or free the port before starting this managed instance."
    exit 1
}

if (Test-Path $logPath) {
    Remove-Item -Path $logPath -Force -ErrorAction SilentlyContinue
}

$argList = @('-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runnerPath)
if ($ShowWindow) {
    $proc = Start-Process -FilePath 'powershell.exe' -ArgumentList $argList -WorkingDirectory $config.ModuleDir -PassThru
}
else {
    $proc = Start-Process -FilePath 'powershell.exe' -ArgumentList $argList -WorkingDirectory $config.ModuleDir -WindowStyle Hidden -PassThru
}

Set-Content -Path $pidPath -Value $proc.Id -Encoding ASCII
Write-Host "[$($config.Key)] started pid=$($proc.Id), waiting for port $($config.Port)..."
if (Wait-PortOpen -Port $config.Port -TimeoutSec $config.StartupTimeoutSec) {
    Write-Host "[$($config.Key)] ready on http://127.0.0.1:$($config.Port)"
    Write-Host "log: $logPath"
    if ($config.HealthUrl) {
        Write-Host "health: $($config.HealthUrl)"
    }
    exit 0
}

Write-Host "[$($config.Key)] startup timeout, port $($config.Port) not ready"
if (Test-Path $logPath) {
    Write-Host 'last log lines:'
    Get-Content -Path $logPath -Tail 40
}
exit 1
