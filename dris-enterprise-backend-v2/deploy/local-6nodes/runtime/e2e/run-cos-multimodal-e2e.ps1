$ErrorActionPreference='Stop'
$api='http://127.0.0.1:8203'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$assetDir = Join-Path $root 'assets'
$cases = @(
    @{ profile='image_png'; file=(Join-Path $assetDir 'sample-image.png'); label='image' },
    @{ profile='table_excel'; file=(Join-Path $assetDir 'sample-table.xlsx'); label='table' },
    @{ profile='doc_word'; file=(Join-Path $assetDir 'sample-doc.docx'); label='document' }
)

function Invoke-ApiJson {
    param([string]$Method, [string]$Uri, [string]$Body = '')
    if ([string]::IsNullOrWhiteSpace($Body)) {
        return Invoke-RestMethod -Method $Method -Uri $Uri
    }
    return Invoke-RestMethod -Method $Method -Uri $Uri -ContentType 'application/json; charset=utf-8' -Body $Body
}

function Read-ErrorBody {
    param($Exception)
    try {
        $resp = $Exception.Response
        if ($resp -ne $null) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $content = $sr.ReadToEnd()
            $sr.Close()
            return $content
        }
    } catch {
    }
    return $Exception.Message
}

$results = @()
foreach ($case in $cases) {
    $stamp = Get-Date -Format 'yyyyMMddHHmmssfff'
    $file = $case.file
    $size = (Get-Item $file).Length
    $sha = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLower()
    $row = [ordered]@{
        label = $case.label
        profile = $case.profile
        file = $file
        size = $size
        sha256 = $sha
        identifier = ''
        uploadId = ''
        putHttpCode = ''
        completeOk = $false
        downloadOk = $false
        verifyStatus = ''
        locator = ''
        error = ''
    }
    try {
        $registerBody = @{
            regionCode='1100'
            organizationCode='CNTA'
            industry='traffic'
            dataCategory='个人数据'
            registrationType='数据产品/服务'
            owner='county-traffic-center'
            description=('multimodal ' + $case.label + ' upload test')
            applicationScenario='traffic_dispatch'
            name=('multimodal_' + $case.label + '_' + $stamp)
            entityType='dataset'
        } | ConvertTo-Json -Depth 6
        $registerResp = Invoke-ApiJson -Method Post -Uri "$api/api/v2/identifiers/register" -Body $registerBody
        if (-not $registerResp.ok) { throw ('register failed: ' + ($registerResp | ConvertTo-Json -Depth 8)) }
        $identifier = [string]$registerResp.data
        $row.identifier = $identifier

        $createBody = @{
            profile=$case.profile
            filename=[System.IO.Path]::GetFileName($file)
            size=$size
            sha256=$sha
        } | ConvertTo-Json -Depth 6
        $createResp = Invoke-ApiJson -Method Post -Uri "$api/api/v2/identifiers/$identifier/upload-sessions" -Body $createBody
        if (-not $createResp.ok) { throw ('createUploadSession failed: ' + ($createResp | ConvertTo-Json -Depth 8)) }
        $uploadId = [string]$createResp.data.uploadId
        $row.uploadId = $uploadId
        $putUrl = [string]$createResp.data.put.url
        $headers = @{}
        foreach ($prop in $createResp.data.put.headers.PSObject.Properties) { $headers[$prop.Name] = [string]$prop.Value }
        $putRespFile = Join-Path $root ('put-' + $case.label + '-' + $stamp + '.txt')
        if (Test-Path $putRespFile) { Remove-Item $putRespFile -Force }
        $curlArgs = @('-sS','-o',$putRespFile,'-w','%{http_code}','-X','PUT')
        foreach ($key in $headers.Keys) { $curlArgs += @('-H', ($key + ': ' + $headers[$key])) }
        $curlArgs += @('--upload-file', $file, $putUrl)
        $row.putHttpCode = (& curl.exe @curlArgs).Trim()
        Start-Sleep -Milliseconds 1200

        $completeResp = Invoke-ApiJson -Method Post -Uri "$api/api/v2/upload-sessions/$uploadId/complete"
        $row.completeOk = [bool]$completeResp.ok
        if (-not $completeResp.ok) { throw ('completeUpload failed: ' + ($completeResp | ConvertTo-Json -Depth 10)) }
        $row.locator = [string]$completeResp.data.locator
        $row.verifyStatus = [string]$completeResp.data.extra._verifyStatus

        $downloadBody = @{ identifier=$identifier; expiresInSeconds=600 } | ConvertTo-Json -Depth 4
        $downloadResp = Invoke-ApiJson -Method Post -Uri "$api/api/v2/downloads/presign" -Body $downloadBody
        $row.downloadOk = [bool]$downloadResp.ok
        if (-not $downloadResp.ok) { throw ('downloadPresign failed: ' + ($downloadResp | ConvertTo-Json -Depth 10)) }
    } catch {
        $row.error = Read-ErrorBody $_.Exception
    }
    $results += [pscustomobject]$row
}
$results | ConvertTo-Json -Depth 8
