$script:StandaloneRoot = Split-Path -Parent $PSScriptRoot
$script:PackRoot = Split-Path -Parent $script:StandaloneRoot
$script:RuntimeRoot = Join-Path $script:StandaloneRoot 'runtime'
$script:PidRoot = Join-Path $script:RuntimeRoot 'pids'
$script:LogRoot = Join-Path $script:RuntimeRoot 'logs'
$script:RunnerRoot = Join-Path $script:RuntimeRoot 'runners'

function Ensure-StandaloneRuntime {
    New-Item -ItemType Directory -Force -Path $script:RuntimeRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $script:PidRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $script:LogRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $script:RunnerRoot | Out-Null
}

function Get-ServiceConfig {
    param([Parameter(Mandatory = $true)][string]$Service)

    $normalized = $Service.Trim().ToLowerInvariant()
    switch ($normalized) {
        'auth' {
            return [pscustomobject]@{
                Key = 'auth'
                DisplayName = 'dris-auth-center'
                ModuleDir = Join-Path $script:PackRoot 'dris-auth-center'
                Port = 8290
                Type = 'java'
                HealthUrl = ''
                StartupTimeoutSec = 90
                Env = @{}
                StartCommand = 'mvn spring-boot:run'
            }
        }
        'backend' {
            return [pscustomobject]@{
                Key = 'backend'
                DisplayName = 'dris-enterprise-backend-v2'
                ModuleDir = Join-Path $script:PackRoot 'dris-enterprise-backend-v2'
                Port = 8190
                Type = 'java'
                HealthUrl = ''
                StartupTimeoutSec = 120
                Env = @{}
                StartCommand = 'mvn spring-boot:run'
            }
        }
        'mcp' {
            return [pscustomobject]@{
                Key = 'mcp'
                DisplayName = 'dris-mcp-server'
                ModuleDir = Join-Path $script:PackRoot 'dris-mcp-server'
                Port = 38082
                Type = 'node'
                HealthUrl = 'http://127.0.0.1:38082/health'
                StartupTimeoutSec = 60
                Env = @{
                    MCP_PORT = '38082'
                    DRIS_BASE_URL = 'http://127.0.0.1:8190'
                }
                StartCommand = 'npm start'
            }
        }
        'orchestrator' {
            return [pscustomobject]@{
                Key = 'orchestrator'
                DisplayName = 'dris-deepresearch-orchestrator'
                ModuleDir = Join-Path $script:PackRoot 'dris-deepresearch-orchestrator'
                Port = 38083
                Type = 'node'
                HealthUrl = 'http://127.0.0.1:38083/health'
                StartupTimeoutSec = 60
                Env = @{
                    PORT = '38083'
                    HOST = '127.0.0.1'
                    CORS_ORIGIN = '*'
                    MCP_SERVER_URL = 'http://127.0.0.1:38082/mcp'
                    PLANNER_MODE = 'heuristic'
                }
                StartCommand = 'npm start'
            }
        }
        'frontend' {
            return [pscustomobject]@{
                Key = 'frontend'
                DisplayName = 'dris-deepresearch-frontend'
                ModuleDir = Join-Path $script:PackRoot 'dris-deepresearch-frontend'
                Port = 5176
                Type = 'node'
                HealthUrl = ''
                StartupTimeoutSec = 45
                Env = @{}
                StartCommand = 'npm run dev -- --host 0.0.0.0 --port 5176'
            }
        }
        default {
            throw "Unsupported service '$Service'. Use one of: auth, backend, mcp, orchestrator, frontend."
        }
    }
}

function Get-PidPath {
    param($Config)
    return Join-Path $script:PidRoot ($Config.Key + '.pid')
}

function Get-LogPath {
    param($Config)
    return Join-Path $script:LogRoot ($Config.Key + '.log')
}

function Get-RunnerPath {
    param($Config)
    return Join-Path $script:RunnerRoot ($Config.Key + '.ps1')
}

function Test-PortOpen {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutMs = 1000
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($async) | Out-Null
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Close()
    }
}

function Wait-PortOpen {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSec = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen -Port $Port -TimeoutMs 1000) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Get-RunningProcess {
    param($Config)

    $pidPath = Get-PidPath -Config $Config
    if (Test-Path $pidPath) {
        $raw = (Get-Content -Path $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($raw) {
            try {
                $proc = Get-Process -Id ([int]$raw) -ErrorAction Stop
                return $proc
            }
            catch {
                Remove-Item -Path $pidPath -Force -ErrorAction SilentlyContinue
            }
        }
        else {
            Remove-Item -Path $pidPath -Force -ErrorAction SilentlyContinue
        }
    }

    $conn = Get-NetTCPConnection -LocalPort $Config.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conn) {
        return $null
    }

    try {
        return Get-Process -Id $conn.OwningProcess -ErrorAction Stop
    }
    catch {
        return $null
    }
}

function New-RunnerScript {
    param($Config)

    $runnerPath = Get-RunnerPath -Config $Config
    $logPath = (Get-LogPath -Config $Config) -replace '\\', '\\\\'
    $moduleDir = $Config.ModuleDir -replace '\\', '\\\\'
    $lines = @()
    $lines += '$ErrorActionPreference = ''Stop'''
    $lines += ('$logPath = ''' + $logPath + '''')
    $lines += ('$moduleDir = ''' + $moduleDir + '''')
    $lines += 'New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logPath) | Out-Null'
    $lines += '"=== $(Get-Date -Format s) service bootstrap ===" | Tee-Object -FilePath $logPath -Append'
    $lines += 'Set-Location $moduleDir'
    foreach ($key in $Config.Env.Keys) {
        $value = [string]$Config.Env[$key]
        $escaped = $value.Replace('''', '''''')
        $lines += ('$env:' + $key + ' = ''' + $escaped + '''')
    }
    if ($Config.Type -eq 'node') {
        $lines += 'if (-not (Test-Path ''node_modules'')) { "node_modules missing, running npm install" | Tee-Object -FilePath $logPath -Append; npm install 2>&1 | Tee-Object -FilePath $logPath -Append }'
    }
    $lines += ('$command = ''' + ($Config.StartCommand.Replace('''', '''''')) + '''')
    $lines += '"working dir: $moduleDir" | Tee-Object -FilePath $logPath -Append'
    $lines += '"command: $command" | Tee-Object -FilePath $logPath -Append'
    $lines += 'cmd.exe /c $command 2>&1 | Tee-Object -FilePath $logPath -Append'
    $lines += '$exitCode = $LASTEXITCODE'
    $lines += '"=== $(Get-Date -Format s) process exit code: $exitCode ===" | Tee-Object -FilePath $logPath -Append'
    $lines += 'exit $exitCode'
    Set-Content -Path $runnerPath -Value ($lines -join "`r`n") -Encoding UTF8
    return $runnerPath
}

function Stop-ProcessTree {
    param(
        [Parameter(Mandatory = $true)][int]$ProcessId
    )

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId ([int]$child.ProcessId)
    }

    try {
        Stop-Process -Id $ProcessId -Force -ErrorAction Stop
    }
    catch {
    }
}


