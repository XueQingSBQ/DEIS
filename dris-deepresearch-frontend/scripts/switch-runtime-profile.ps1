param(
    [ValidateSet("default", "demo-water")]
    [string]$Profile = "default"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path (Join-Path $scriptDir "..")
$profilesDir = Join-Path $projectRoot "profiles"
$publicDir = Join-Path $projectRoot "public"
$targetFile = Join-Path $publicDir "console-runtime.config.json"

$sourceFile = switch ($Profile) {
    "demo-water" { Join-Path $profilesDir "console-runtime.demo-water.json" }
    default { Join-Path $profilesDir "console-runtime.default.json" }
}

if (-not (Test-Path $sourceFile)) {
    throw "Profile file not found: $sourceFile"
}

New-Item -ItemType Directory -Force -Path $publicDir | Out-Null
Copy-Item -Force $sourceFile $targetFile

Write-Host "Switched frontend runtime profile to: $Profile"
Write-Host "Active config: $targetFile"
Write-Host "Restart Vite dev server to apply updated proxy rules."
