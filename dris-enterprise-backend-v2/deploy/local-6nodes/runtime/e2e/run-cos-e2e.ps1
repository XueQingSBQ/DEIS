$ErrorActionPreference='Stop'
$api='http://127.0.0.1:8203'
$work='E:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes\runtime\e2e'
New-Item -ItemType Directory -Force -Path $work | Out-Null
$stamp=Get-Date -Format 'yyyyMMddHHmmss'
$file=Join-Path $work ("cos-e2e-" + $stamp + '.json')
@{
  scenario='cos-e2e-upload'
  createdAt=(Get-Date).ToString('o')
  source='codex'
  note='real createUploadSession PUT completeUpload test'
} | ConvertTo-Json -Depth 6 | Set-Content -Path $file -Encoding UTF8
$size=(Get-Item $file).Length
$sha=(Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLower()
$registerBody=@{
  regionCode='1100'
  organizationCode='CNTA'
  industry='traffic'
  dataCategory='个人数据'
  registrationType='数据产品/服务'
  owner='county-traffic-center'
  description='COS end-to-end upload verification object'
  applicationScenario='traffic_dispatch'
  name=('cos_e2e_' + $stamp)
  entityType='dataset'
} | ConvertTo-Json -Depth 6
$registerResp=Invoke-RestMethod -Method Post -Uri "$api/api/v2/identifiers/register" -ContentType 'application/json; charset=utf-8' -Body $registerBody
if(-not $registerResp.ok){ throw ('register failed: ' + ($registerResp | ConvertTo-Json -Depth 8)) }
$identifier=[string]$registerResp.data
$createBody=@{
  profile='data_json'
  filename=[System.IO.Path]::GetFileName($file)
  size=$size
  sha256=$sha
} | ConvertTo-Json -Depth 6
$createResp=Invoke-RestMethod -Method Post -Uri "$api/api/v2/identifiers/$identifier/upload-sessions" -ContentType 'application/json; charset=utf-8' -Body $createBody
if(-not $createResp.ok){ throw ('createUploadSession failed: ' + ($createResp | ConvertTo-Json -Depth 8)) }
$uploadId=[string]$createResp.data.uploadId
$putUrl=[string]$createResp.data.put.url
$headers=@{}
foreach($prop in $createResp.data.put.headers.PSObject.Properties){ $headers[$prop.Name]=[string]$prop.Value }
$putRespFile=Join-Path $work ('put-response-' + $stamp + '.txt')
if(Test-Path $putRespFile){ Remove-Item $putRespFile -Force }
$curlArgs=@('-sS','-o',$putRespFile,'-w','%{http_code}','-X','PUT')
foreach($key in $headers.Keys){ $curlArgs += @('-H', ($key + ': ' + $headers[$key])) }
$curlArgs += @('--upload-file',$file,$putUrl)
$httpCode = (& curl.exe @curlArgs).Trim()
$putBody = if(Test-Path $putRespFile){ Get-Content -Raw $putRespFile } else { '' }
Start-Sleep -Seconds 2
$completeResp=Invoke-RestMethod -Method Post -Uri "$api/api/v2/upload-sessions/$uploadId/complete"
$downloadBody=@{ identifier=$identifier; expiresInSeconds=600 } | ConvertTo-Json -Depth 4
$downloadResp=Invoke-RestMethod -Method Post -Uri "$api/api/v2/downloads/presign" -ContentType 'application/json; charset=utf-8' -Body $downloadBody
[ordered]@{
  identifier=$identifier
  uploadId=$uploadId
  filePath=$file
  fileSize=$size
  sha256=$sha
  putUrlHost=([System.Uri]$putUrl).Host
  putHeaderKeys=@($headers.Keys)
  putHttpCode=$httpCode
  putResponseBody=$putBody
  completeResponse=$completeResp
  downloadResponse=$downloadResp
} | ConvertTo-Json -Depth 12
