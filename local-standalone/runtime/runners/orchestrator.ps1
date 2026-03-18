$ErrorActionPreference = 'Stop'
$logPath = 'E:\\\\irp\\\\doip-sdk\\\\dris-system-deploy-20260310\\\\local-standalone\\\\runtime\\\\logs\\\\orchestrator.log'
$moduleDir = 'E:\\\\irp\\\\doip-sdk\\\\dris-system-deploy-20260310\\\\dris-deepresearch-orchestrator'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logPath) | Out-Null
"=== $(Get-Date -Format s) service bootstrap ===" | Tee-Object -FilePath $logPath -Append
Set-Location $moduleDir
$env:CORS_ORIGIN = '*'
$env:PLANNER_MODE = 'auto'
$env:MCP_SERVER_URL = 'http://127.0.0.1:38082/mcp'
$env:HOST = '127.0.0.1'
$env:PORT = '38083'
if (-not (Test-Path 'node_modules')) { "node_modules missing, running npm install" | Tee-Object -FilePath $logPath -Append; npm install 2>&1 | Tee-Object -FilePath $logPath -Append }
$command = 'npm start'
"working dir: $moduleDir" | Tee-Object -FilePath $logPath -Append
"command: $command" | Tee-Object -FilePath $logPath -Append
cmd.exe /c $command 2>&1 | Tee-Object -FilePath $logPath -Append
$exitCode = $LASTEXITCODE
"=== $(Get-Date -Format s) process exit code: $exitCode ===" | Tee-Object -FilePath $logPath -Append
exit $exitCode
