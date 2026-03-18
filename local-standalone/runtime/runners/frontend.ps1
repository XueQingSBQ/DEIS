$ErrorActionPreference = 'Stop'
$logPath = 'E:\\\\irp\\\\doip-sdk\\\\dris-system-deploy-20260310\\\\local-standalone\\\\runtime\\\\logs\\\\frontend.log'
$moduleDir = 'E:\\\\irp\\\\doip-sdk\\\\dris-system-deploy-20260310\\\\dris-deepresearch-frontend'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logPath) | Out-Null
"=== $(Get-Date -Format s) service bootstrap ===" | Tee-Object -FilePath $logPath -Append
Set-Location $moduleDir
if (-not (Test-Path 'node_modules')) { "node_modules missing, running npm install" | Tee-Object -FilePath $logPath -Append; npm install 2>&1 | Tee-Object -FilePath $logPath -Append }
$command = 'npm run dev -- --host 0.0.0.0 --port 5176'
"working dir: $moduleDir" | Tee-Object -FilePath $logPath -Append
"command: $command" | Tee-Object -FilePath $logPath -Append
cmd.exe /c $command 2>&1 | Tee-Object -FilePath $logPath -Append
$exitCode = $LASTEXITCODE
"=== $(Get-Date -Format s) process exit code: $exitCode ===" | Tee-Object -FilePath $logPath -Append
exit $exitCode
