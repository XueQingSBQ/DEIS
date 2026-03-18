param(
    [switch]$BuildJar = $true,
    [switch]$InitDb = $false,
    [string]$MysqlExe = "mysql",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPassword = "sbq254172",
    [int]$ReadyTimeoutSec = 120
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$labRoot = Resolve-Path (Join-Path $scriptDir "..")
$projectRoot = Resolve-Path (Join-Path $labRoot "..\..")
$nodesDir = Join-Path $labRoot "nodes"
$runtimeDir = Join-Path $labRoot "runtime"
$pidDir = Join-Path $runtimeDir "pids"
$logDir = Join-Path $runtimeDir "logs"

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
New-Item -ItemType Directory -Force -Path $pidDir | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Get-JarPath {
    param([string]$TargetDir)
    $jar = Get-ChildItem -Path $TargetDir -Filter "dris-enterprise-backend-v2-*.jar" -File `
        | Where-Object { $_.Name -notlike "*original*" } `
        | Sort-Object LastWriteTime -Descending `
        | Select-Object -First 1
    if ($null -eq $jar) {
        throw "Cannot find packaged jar in $TargetDir. Run with -BuildJar."
    }
    return $jar.FullName
}

function Wait-HttpReady {
    param(
        [int]$Port,
        [int]$TimeoutSec
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $uri = "http://127.0.0.1:$Port/api/v2/node/routes"
    while ((Get-Date) -lt $deadline) {
        try {
            $null = Invoke-RestMethod -Method Get -Uri $uri -TimeoutSec 3
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "Node http://127.0.0.1:$Port not ready within $TimeoutSec seconds"
}

function Register-Child {
    param(
        [int]$ParentHttpPort,
        [string]$ChildNodeName,
        [string]$RegionCode,
        [string]$OrgCode,
        [string]$ChildDrirpUrl
    )
    $uri = "http://127.0.0.1:$ParentHttpPort/api/v2/node/register"
    $payload = @{
        childNodeName = $ChildNodeName
        regionCode = $RegionCode
        orgCode = $OrgCode
        childDrirpUrl = $ChildDrirpUrl
    } | ConvertTo-Json

    for ($i = 1; $i -le 15; $i++) {
        try {
            $resp = Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/json" -Body $payload -TimeoutSec 5
            $ok = $false
            if ($resp) {
                if ($resp.ok -eq $true) {
                    $ok = $true
                } elseif ($resp.code -eq "SUCCESS" -or $resp.code -eq "OK") {
                    $ok = $true
                }
            }
            if ($ok) {
                return
            }
        } catch {
            $statusCode = $null
            try {
                $statusCode = [int]$_.Exception.Response.StatusCode
            } catch {
            }
            if ($statusCode -eq 409) {
                Write-Host "Register child already exists, treat as success: $ChildNodeName"
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Register child failed: parentPort=$ParentHttpPort child=$ChildNodeName"
}

function Start-Node {
    param(
        [string]$NodeName,
        [int]$HttpPort,
        [string]$ConfigFile,
        [string]$JarPath
    )
    $pidFile = Join-Path $pidDir "$NodeName.pid"
    if (Test-Path $pidFile) {
        $existingPid = (Get-Content -Raw $pidFile).Trim()
        if ($existingPid -match "^\d+$") {
            $proc = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "[$NodeName] already running, pid=$existingPid"
                return
            }
        }
        Remove-Item -Force $pidFile
    }

    $configPath = Join-Path $nodesDir $ConfigFile
    if (-not (Test-Path $configPath)) {
        throw "Missing node config: $configPath"
    }
    $stdoutFile = Join-Path $logDir "$NodeName.out.log"
    $stderrFile = Join-Path $logDir "$NodeName.err.log"
    $javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

    $args = @(
        "-jar"
        "`"$JarPath`""
        "--spring.config.additional-location=`"$configPath`""
    )

    $proc = Start-Process -FilePath $javaExe -ArgumentList $args -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile
    Set-Content -Path $pidFile -Value $proc.Id -Encoding ascii
    Write-Host "[$NodeName] started pid=$($proc.Id), waiting for HTTP $HttpPort ..."
    Wait-HttpReady -Port $HttpPort -TimeoutSec $ReadyTimeoutSec
    Write-Host "[$NodeName] ready"
}

if ($InitDb) {
    & (Join-Path $scriptDir "init-local-dbs.ps1") `
        -MysqlExe $MysqlExe `
        -DbHost $DbHost `
        -DbPort $DbPort `
        -DbUser $DbUser `
        -DbPassword $DbPassword
}

if ($BuildJar) {
    Write-Host "Packaging backend jar ..."
    Push-Location $projectRoot
    try {
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "mvn package failed: $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$jarPath = Get-JarPath -TargetDir (Join-Path $projectRoot "target")
Write-Host "Using jar: $jarPath"

# Start country
Start-Node -NodeName "node1-country" -HttpPort 8200 -ConfigFile "node1-country.yml" -JarPath $jarPath

# Register and start province
Register-Child -ParentHttpPort 8200 -ChildNodeName "node2-province" -RegionCode "1100" -OrgCode "PRAA" -ChildDrirpUrl "grpc://127.0.0.1:19201"
Start-Node -NodeName "node2-province" -HttpPort 8201 -ConfigFile "node2-province.yml" -JarPath $jarPath

# Register city branches under province
Register-Child -ParentHttpPort 8201 -ChildNodeName "node3-city-a" -RegionCode "1100" -OrgCode "CTAA" -ChildDrirpUrl "grpc://127.0.0.1:19202"
Register-Child -ParentHttpPort 8201 -ChildNodeName "node5-city-b" -RegionCode "1100" -OrgCode "CTAB" -ChildDrirpUrl "grpc://127.0.0.1:19204"

Start-Node -NodeName "node3-city-a" -HttpPort 8202 -ConfigFile "node3-city-a.yml" -JarPath $jarPath
Start-Node -NodeName "node5-city-b" -HttpPort 8204 -ConfigFile "node5-city-b.yml" -JarPath $jarPath

# Register counties under each city
Register-Child -ParentHttpPort 8202 -ChildNodeName "node4-county-a" -RegionCode "1100" -OrgCode "CNTA" -ChildDrirpUrl "grpc://127.0.0.1:19203"
Register-Child -ParentHttpPort 8204 -ChildNodeName "node6-county-b" -RegionCode "1100" -OrgCode "CNTB" -ChildDrirpUrl "grpc://127.0.0.1:19205"

Start-Node -NodeName "node4-county-a" -HttpPort 8203 -ConfigFile "node4-county-a.yml" -JarPath $jarPath
Start-Node -NodeName "node6-county-b" -HttpPort 8205 -ConfigFile "node6-county-b.yml" -JarPath $jarPath

Write-Host ""
Write-Host "All 6 nodes started."
Write-Host "Country API:  http://127.0.0.1:8200"
Write-Host "Province API: http://127.0.0.1:8201"
Write-Host "City-A API:   http://127.0.0.1:8202"
Write-Host "County-A API: http://127.0.0.1:8203"
Write-Host "City-B API:   http://127.0.0.1:8204"
Write-Host "County-B API: http://127.0.0.1:8205"
Write-Host ""

& (Join-Path $scriptDir "status-6nodes.ps1")

