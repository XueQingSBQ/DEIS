param(
    [int]$TailLines = 80,
    [switch]$IncludeErr = $true
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$labRoot = Resolve-Path (Join-Path $scriptDir "..")
$logDir = Join-Path $labRoot "runtime\logs"

if (-not (Test-Path $logDir)) {
    throw "Log directory not found: $logDir"
}

$nodes = @(
    "node1-country",
    "node2-province",
    "node3-city-a",
    "node4-county-a",
    "node5-city-b",
    "node6-county-b"
)

function Escape-SingleQuoted {
    param([string]$Value)
    if ($null -eq $Value) {
        return ""
    }
    return $Value.Replace("'", "''")
}

foreach ($node in $nodes) {
    $outLog = Join-Path $logDir ($node + ".out.log")
    $errLog = Join-Path $logDir ($node + ".err.log")

    if (-not (Test-Path $outLog)) {
        New-Item -ItemType File -Force -Path $outLog | Out-Null
    }
    if ($IncludeErr -and -not (Test-Path $errLog)) {
        New-Item -ItemType File -Force -Path $errLog | Out-Null
    }

    $outEscaped = Escape-SingleQuoted $outLog
    $errEscaped = Escape-SingleQuoted $errLog
    $title = Escape-SingleQuoted ("DRIS " + $node + " logs")

    if ($IncludeErr) {
        $command = "& { `$Host.UI.RawUI.WindowTitle = '$title'; " +
                "Get-Content -Path '$outEscaped', '$errEscaped' -Tail $TailLines -Wait }"
    } else {
        $command = "& { `$Host.UI.RawUI.WindowTitle = '$title'; " +
                "Get-Content -Path '$outEscaped' -Tail $TailLines -Wait }"
    }

    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $command
    ) | Out-Null
}

Write-Host "Opened log windows for 6 nodes."
Write-Host "Tip: close each PowerShell window when finished."

