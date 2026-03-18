$ErrorActionPreference = 'Stop'
$logPath = 'E:\\\\irp\\\\doip-sdk\\\\dris-system-deploy-20260310\\\\local-standalone\\\\runtime\\\\logs\\\\backend.log'
$moduleDir = 'E:\\\\irp\\\\doip-sdk\\\\dris-system-deploy-20260310\\\\dris-enterprise-backend-v2'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logPath) | Out-Null
"=== $(Get-Date -Format s) service bootstrap ===" | Tee-Object -FilePath $logPath -Append
Set-Location $moduleDir
$command = 'mvn spring-boot:run'
"working dir: $moduleDir" | Tee-Object -FilePath $logPath -Append
"command: $command" | Tee-Object -FilePath $logPath -Append
cmd.exe /c $command 2>&1 | Tee-Object -FilePath $logPath -Append
$exitCode = $LASTEXITCODE
"=== $(Get-Date -Format s) process exit code: $exitCode ===" | Tee-Object -FilePath $logPath -Append
exit $exitCode
