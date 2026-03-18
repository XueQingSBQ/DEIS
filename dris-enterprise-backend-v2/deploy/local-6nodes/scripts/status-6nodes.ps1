$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$labRoot = Resolve-Path (Join-Path $scriptDir "..")
$pidDir = Join-Path $labRoot "runtime\pids"

$nodes = @(
    @{ Name = "node1-country"; HttpPort = 8200; DrirpPort = 19200 },
    @{ Name = "node2-province"; HttpPort = 8201; DrirpPort = 19201 },
    @{ Name = "node3-city-a"; HttpPort = 8202; DrirpPort = 19202 },
    @{ Name = "node4-county-a"; HttpPort = 8203; DrirpPort = 19203 },
    @{ Name = "node5-city-b"; HttpPort = 8204; DrirpPort = 19204 },
    @{ Name = "node6-county-b"; HttpPort = 8205; DrirpPort = 19205 }
)

function Test-Http {
    param([int]$Port)
    try {
        $null = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/v2/node/routes" -TimeoutSec 2
        return $true
    } catch {
        return $false
    }
}

function Test-Tcp {
    param([int]$Port)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(1000, $false)
        if ($ok -and $client.Connected) {
            $client.EndConnect($iar) | Out-Null
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

$rows = @()
foreach ($node in $nodes) {
    $pidValue = ""
    $processRunning = $false
    $pidFile = Join-Path $pidDir ($node.Name + ".pid")
    if (Test-Path $pidFile) {
        $raw = (Get-Content -Raw $pidFile).Trim()
        if ($raw -match "^\d+$") {
            $pidValue = $raw
            $p = Get-Process -Id ([int]$raw) -ErrorAction SilentlyContinue
            $processRunning = $null -ne $p
        }
    }
    $rows += [PSCustomObject]@{
        Node = $node.Name
        PID = $pidValue
        Process = if ($processRunning) { "UP" } else { "DOWN" }
        Http = if (Test-Http -Port $node.HttpPort) { "UP" } else { "DOWN" }
        Drirp = if (Test-Tcp -Port $node.DrirpPort) { "UP" } else { "DOWN" }
        HttpPort = $node.HttpPort
        DrirpPort = $node.DrirpPort
    }
}

$rows | Format-Table -AutoSize
