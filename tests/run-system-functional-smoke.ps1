param(
    [string]$CountryBase = "http://127.0.0.1:8200",
    [string]$ProvinceBase = "http://127.0.0.1:8201",
    [string]$CityABase = "http://127.0.0.1:8202",
    [string]$CountyABase = "http://127.0.0.1:8203",
    [string]$CityBBase = "http://127.0.0.1:8204",
    [string]$CountyBBase = "http://127.0.0.1:8205",
    [string]$AuthBase = "http://127.0.0.1:8290",
    [string]$McpBase = "http://127.0.0.1:38082",
    [string]$OrchestratorBase = "http://127.0.0.1:38083",
    [string]$FrontendBase = "http://127.0.0.1:5176",
    [string]$OutputDir = ".\\output"
)

$ErrorActionPreference = 'Stop'
$script:Results = New-Object System.Collections.Generic.List[object]
$script:RunStartedAt = Get-Date
$suffix = Get-Date -Format 'yyyyMMddHHmmss'

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

function Convert-BodyToJson {
    param([object]$Body)
    if ($null -eq $Body) { return $null }
    if ($Body -is [string]) { return $Body }
    return $Body | ConvertTo-Json -Depth 20
}

function Read-ErrorBody {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)
    if ($null -eq $ErrorRecord.Exception.Response) {
        return $ErrorRecord.Exception.Message
    }
    try {
        $stream = $ErrorRecord.Exception.Response.GetResponseStream()
        if ($null -eq $stream) { return $ErrorRecord.Exception.Message }
        $reader = New-Object System.IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } catch {
        return $ErrorRecord.Exception.Message
    }
}

function Invoke-JsonRequest {
    param(
        [string]$Method = 'GET',
        [string]$Uri,
        [object]$Body,
        [hashtable]$Headers,
        [int]$TimeoutSec = 30,
        [string]$ContentType = 'application/json'
    )
    $jsonBody = Convert-BodyToJson -Body $Body
    try {
        $invokeParams = @{
            UseBasicParsing = $true
            Method = $Method
            Uri = $Uri
            TimeoutSec = $TimeoutSec
        }
        if ($Headers) { $invokeParams.Headers = $Headers }
        if ($null -ne $jsonBody) {
            $invokeParams.Body = $jsonBody
            $invokeParams.ContentType = $ContentType
        }
        $response = Invoke-WebRequest @invokeParams
        $parsed = $null
        try { $parsed = $response.Content | ConvertFrom-Json } catch { }
        return [pscustomobject]@{
            Success = $true
            StatusCode = [int]$response.StatusCode
            Content = $response.Content
            Json = $parsed
            Error = $null
        }
    } catch {
        $bodyText = Read-ErrorBody -ErrorRecord $_
        $parsed = $null
        try { $parsed = $bodyText | ConvertFrom-Json } catch { }
        $statusCode = -1
        try { $statusCode = [int]$_.Exception.Response.StatusCode } catch { }
        return [pscustomobject]@{
            Success = $false
            StatusCode = $statusCode
            Content = $bodyText
            Json = $parsed
            Error = $_.Exception.Message
        }
    }
}

function Add-TestResult {
    param(
        [string]$Id,
        [string]$Module,
        [string]$Name,
        [bool]$Passed,
        [string]$Expectation,
        [string]$Actual,
        [int]$StatusCode = -1,
        [object]$Evidence = $null
    )
    $script:Results.Add([pscustomobject]@{
        id = $Id
        module = $Module
        name = $Name
        passed = $Passed
        statusCode = $StatusCode
        expectation = $Expectation
        actual = $Actual
        evidence = $Evidence
    }) | Out-Null
}

function Expect-ResponseOk {
    param([object]$Response)
    return ($Response.Success -and $Response.StatusCode -eq 200 -and $null -ne $Response.Json -and $Response.Json.ok -eq $true)
}

function Invoke-Test {
    param(
        [string]$Id,
        [string]$Module,
        [string]$Name,
        [scriptblock]$Action,
        [scriptblock]$Predicate,
        [string]$Expectation,
        [switch]$ReturnResponse
    )
    $response = & $Action
    $passed = & $Predicate $response
    $actual = if ($response.Success) { "HTTP $($response.StatusCode)" } else { "HTTP $($response.StatusCode): $($response.Error)" }
    Add-TestResult -Id $Id -Module $Module -Name $Name -Passed $passed -Expectation $Expectation -Actual $actual -StatusCode $response.StatusCode -Evidence $response.Json
    if ($ReturnResponse) { return $response }
}

$healthRoutes = Invoke-Test -Id 'SYS-001' -Module 'system' -Name 'backend routes health' -Action { Invoke-JsonRequest -Uri "$CountryBase/api/v2/node/routes" } -Predicate { param($r) Expect-ResponseOk $r } -Expectation 'country node route table should be reachable and return ok=true' -ReturnResponse
Invoke-Test -Id 'SYS-002' -Module 'system' -Name 'auth public key health' -Action { Invoke-JsonRequest -Uri "$AuthBase/auth/public-key" } -Predicate { param($r) Expect-ResponseOk $r } -Expectation 'auth center public key endpoint should return ok=true' | Out-Null
Invoke-Test -Id 'SYS-003' -Module 'system' -Name 'mcp health' -Action { Invoke-JsonRequest -Uri "$McpBase/health" } -Predicate { param($r) $r.Success -and $r.StatusCode -eq 200 -and $r.Json.ok -eq $true } -Expectation 'mcp health should return ok=true' | Out-Null
Invoke-Test -Id 'SYS-004' -Module 'system' -Name 'orchestrator health' -Action { Invoke-JsonRequest -Uri "$OrchestratorBase/health" } -Predicate { param($r) $r.Success -and $r.StatusCode -eq 200 -and $r.Json.ok -eq $true } -Expectation 'orchestrator health should return ok=true' | Out-Null
Invoke-Test -Id 'SYS-005' -Module 'system' -Name 'frontend index' -Action { Invoke-JsonRequest -Uri $FrontendBase -TimeoutSec 15 } -Predicate { param($r) $r.Success -and $r.StatusCode -eq 200 -and $r.Content -match '<!doctype html>' } -Expectation 'frontend home page should be reachable' | Out-Null

$registerUserBody = @{
    username = "tester_$suffix"
    password = 'Passw0rd!'
    displayName = "Tester $suffix"
    orgCode = 'CNTA'
    email = "tester_$suffix@example.com"
    phone = '13800000000'
}
$registerUserResp = Invoke-Test -Id 'AUTH-001' -Module 'auth' -Name 'register user' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$AuthBase/auth/register" -Body $registerUserBody } -Predicate { param($r) Expect-ResponseOk $r } -Expectation 'register should create a new user and issue access token' -ReturnResponse
$accessToken = if (Expect-ResponseOk $registerUserResp) { $registerUserResp.Json.data.accessToken } else { '' }
Invoke-Test -Id 'AUTH-002' -Module 'auth' -Name 'login user' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$AuthBase/auth/login" -Body @{ username = $registerUserBody.username; password = $registerUserBody.password } } -Predicate { param($r) Expect-ResponseOk $r } -Expectation 'login should succeed with the registered credential' | Out-Null
Invoke-Test -Id 'AUTH-003' -Module 'auth' -Name 'userinfo' -Action { Invoke-JsonRequest -Uri "$AuthBase/auth/userinfo" -Headers @{ Authorization = "Bearer $accessToken" } } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data.username -eq $registerUserBody.username } -Expectation 'userinfo should parse current bearer token' | Out-Null

Invoke-Test -Id 'BE-001' -Module 'backend' -Name 'direct descendants status' -Action { Invoke-JsonRequest -Uri "$ProvinceBase/api/v2/node/direct-descendants/status" } -Predicate { param($r) (Expect-ResponseOk $r) -and @($r.Json.data).Count -ge 2 } -Expectation 'province node should expose child node statuses' | Out-Null
Invoke-Test -Id 'BE-002' -Module 'backend' -Name 'ops metrics' -Action { Invoke-JsonRequest -Uri "$CountryBase/api/v2/ops/metrics" } -Predicate { param($r) Expect-ResponseOk $r } -Expectation 'country node metrics snapshot should be queryable' | Out-Null

$commonAttrs = @{ source = 'system-test'; batch = $suffix }
$registerABody = @{
    regionCode = '1100'
    organizationCode = 'CNTA'
    industry = 'energy'
    dataCategory = 'public'
    registrationType = 'resource'
    owner = "owner-a-$suffix"
    description = "county-a sample object $suffix"
    applicationScenario = 'lab'
    name = "sample-meter-a-$suffix"
    entityType = 'device'
    additionalAttributes = $commonAttrs + @{ sample = 'a' }
}
$registerBBody = @{
    regionCode = '1100'
    organizationCode = 'CNTB'
    industry = 'energy'
    dataCategory = 'public'
    registrationType = 'resource'
    owner = "owner-b-$suffix"
    description = "county-b sample object $suffix"
    applicationScenario = 'lab'
    name = "sample-meter-b-$suffix"
    entityType = 'device'
    additionalAttributes = $commonAttrs + @{ sample = 'b' }
}
$registerAResp = Invoke-Test -Id 'ID-001' -Module 'identifier' -Name 'register identifier on county-a' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountyABase/api/v2/identifiers/register" -Body $registerABody } -Predicate { param($r) (Expect-ResponseOk $r) -and -not [string]::IsNullOrWhiteSpace($r.Json.data) } -Expectation 'county-a should register one identifier successfully' -ReturnResponse
$registerBResp = Invoke-Test -Id 'ID-002' -Module 'identifier' -Name 'register identifier on county-b' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountyBBase/api/v2/identifiers/register" -Body $registerBBody } -Predicate { param($r) (Expect-ResponseOk $r) -and -not [string]::IsNullOrWhiteSpace($r.Json.data) } -Expectation 'county-b should register one identifier successfully' -ReturnResponse
$idA = if (Expect-ResponseOk $registerAResp) { [string]$registerAResp.Json.data } else { '' }
$idB = if (Expect-ResponseOk $registerBResp) { [string]$registerBResp.Json.data } else { '' }

Invoke-Test -Id 'ID-003' -Module 'identifier' -Name 'validate and resolve metadata cross-node' -Action {
    $validate = Invoke-JsonRequest -Uri "$CountyABase/api/v2/identifiers/$idA/validate"
    $exists = Invoke-JsonRequest -Uri "$CountyABase/api/v2/identifiers/$idA/exists"
    $localMeta = Invoke-JsonRequest -Uri "$CountyABase/api/v2/identifiers/$idA/metadata"
    $remoteMeta = Invoke-JsonRequest -Uri "$CountryBase/api/v2/identifiers/$idA/metadata"
    [pscustomobject]@{
        Success = ($validate.Success -and $exists.Success -and $localMeta.Success -and $remoteMeta.Success)
        StatusCode = if ($remoteMeta.StatusCode -gt 0) { $remoteMeta.StatusCode } else { $localMeta.StatusCode }
        Json = [pscustomobject]@{
            ok = ($validate.Json.ok -and $exists.Json.ok -and $localMeta.Json.ok -and $remoteMeta.Json.ok)
            validate = $validate.Json
            exists = $exists.Json
            localMeta = $localMeta.Json
            remoteMeta = $remoteMeta.Json
        }
        Error = $null
    }
} -Predicate { param($r) $r.Success -and $r.Json.ok -and $r.Json.validate.data -eq $true -and $r.Json.exists.data -eq $true -and $r.Json.remoteMeta.data.identifier -eq $idA } -Expectation 'identifier should validate locally and metadata should resolve from country node' | Out-Null

$metaResp = Invoke-JsonRequest -Uri "$CountyABase/api/v2/identifiers/$idA/metadata"
if (-not (Expect-ResponseOk $metaResp)) { throw 'failed to load metadata before update' }
$metaBody = $metaResp.Json.data
$metaBody.description = "county-a sample object updated $suffix"
$metaBody.applicationScenario = 'lab-updated'
Invoke-Test -Id 'ID-004' -Module 'identifier' -Name 'full update metadata' -Action { Invoke-JsonRequest -Method 'PUT' -Uri "$CountyABase/api/v2/identifiers/$idA/metadata" -Body $metaBody } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data -eq $true } -Expectation 'full metadata update should succeed' | Out-Null
Invoke-Test -Id 'ID-005' -Module 'identifier' -Name 'patch metadata' -Action { Invoke-JsonRequest -Method 'PATCH' -Uri "$CountyABase/api/v2/identifiers/$idA/metadata" -Body @{ description = "county-a patch update $suffix" } } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data -eq $true } -Expectation 'patch metadata update should succeed' | Out-Null

$resourceA1 = @{
    componentType = 'resource'
    role = 'primary'
    storageType = 'external_uri'
    accessMethod = 'download'
    contentType = 'application/json'
    format = 'json'
    locator = "local://manual/$idA-v1.json"
    size = 128
    hashValue = ''
    etag = 'etag-v1'
    versionId = 'v1'
    createdAt = (Get-Date).ToString('s')
    extra = @{ quality = 'test'; revision = 1 }
}
$resourceA2 = @{
    componentType = 'resource'
    role = 'primary'
    storageType = 'external_uri'
    accessMethod = 'download'
    contentType = 'application/json'
    format = 'json'
    locator = "local://manual/$idA-v2.json"
    size = 256
    hashValue = ''
    etag = 'etag-v2'
    versionId = 'v2'
    createdAt = (Get-Date).ToString('s')
    extra = @{ quality = 'test'; revision = 2 }
}
$resourceB = @{
    componentType = 'resource'
    role = 'primary'
    storageType = 'external_uri'
    accessMethod = 'download'
    contentType = 'application/json'
    format = 'json'
    locator = "local://manual/$idB.json"
    size = 144
    hashValue = ''
    etag = 'etag-b1'
    versionId = 'v1'
    createdAt = (Get-Date).ToString('s')
    extra = @{ quality = 'test'; revision = 1 }
}
Invoke-Test -Id 'RES-001' -Module 'resource' -Name 'upsert component v1 on county-a' -Action { Invoke-JsonRequest -Method 'PUT' -Uri "$CountyABase/api/v2/identifiers/$idA/component" -Body $resourceA1 } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data -eq $true } -Expectation 'resource component should bind successfully to identifier A' | Out-Null
Invoke-Test -Id 'RES-002' -Module 'resource' -Name 'upsert component v2 on county-a' -Action { Invoke-JsonRequest -Method 'PUT' -Uri "$CountyABase/api/v2/identifiers/$idA/component" -Body $resourceA2 } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data -eq $true } -Expectation 'second resource upsert should succeed and create a new version snapshot' | Out-Null
Invoke-Test -Id 'RES-003' -Module 'resource' -Name 'upsert component on county-b' -Action { Invoke-JsonRequest -Method 'PUT' -Uri "$CountyBBase/api/v2/identifiers/$idB/component" -Body $resourceB } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data -eq $true } -Expectation 'resource component should bind successfully to identifier B' | Out-Null
Invoke-Test -Id 'RES-004' -Module 'resource' -Name 'query component and version page' -Action {
    $component = Invoke-JsonRequest -Uri "$CountyABase/api/v2/identifiers/$idA/component"
    $versions = Invoke-JsonRequest -Uri "$CountyABase/api/v2/identifiers/$idA/component/versions?page=1&size=10"
    [pscustomobject]@{
        Success = ($component.Success -and $versions.Success)
        StatusCode = if ($versions.StatusCode -gt 0) { $versions.StatusCode } else { $component.StatusCode }
        Json = [pscustomobject]@{ ok = ($component.Json.ok -and $versions.Json.ok); component = $component.Json; versions = $versions.Json }
        Error = $null
    }
} -Predicate { param($r) $r.Success -and $r.Json.ok -and $r.Json.component.data.locator -like '*-v2.json' -and $r.Json.versions.data.total -ge 2 } -Expectation 'latest component and historical version page should both be queryable' | Out-Null

Invoke-Test -Id 'DIST-001' -Module 'distributed' -Name 'distributed batch resolve metadata' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountryBase/api/v2/identifiers/resolve/batch/distributed" -Body @($idA, $idB) } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data.successCount -ge 2 } -Expectation 'country node should resolve metadata across both county nodes' | Out-Null
Invoke-Test -Id 'DIST-002' -Module 'distributed' -Name 'distributed batch resolve object' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountryBase/api/v2/identifiers/resolve/batch/object/distributed" -Body @($idA, $idB) } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data.successCount -ge 2 } -Expectation 'country node should resolve digital objects across both county nodes' | Out-Null
Invoke-Test -Id 'DIST-003' -Module 'distributed' -Name 'distributed batch resolve component' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountryBase/api/v2/identifiers/resolve/batch/component/distributed" -Body @($idA, $idB) } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data.successCount -ge 2 } -Expectation 'country node should resolve resource components across both county nodes' | Out-Null

Invoke-Test -Id 'SEARCH-001' -Module 'search' -Name 'vector sync' -Action {
    $r1 = Invoke-JsonRequest -Method 'POST' -Uri "$CountryBase/api/v2/ops/vector/sync"
    $r2 = Invoke-JsonRequest -Method 'POST' -Uri "$CountyABase/api/v2/ops/vector/sync"
    $r3 = Invoke-JsonRequest -Method 'POST' -Uri "$CountyBBase/api/v2/ops/vector/sync"
    [pscustomobject]@{ Success = ($r1.Success -and $r2.Success -and $r3.Success); StatusCode = $r3.StatusCode; Json = [pscustomobject]@{ ok = ($r1.Json.ok -and $r2.Json.ok -and $r3.Json.ok); country = $r1.Json; countyA = $r2.Json; countyB = $r3.Json }; Error = $null }
} -Predicate { param($r) $r.Success -and $r.Json.ok } -Expectation 'vector index sync should succeed on country and county nodes' | Out-Null
$searchKeyword = "sample-meter"
Invoke-Test -Id 'SEARCH-002' -Module 'search' -Name 'local search on county-a' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountyABase/api/v2/search" -Body @{ naturalQuery = $searchKeyword; keywords = @('sample','meter'); fields = @('description','name','owner'); filters = @{}; limit = 10; offset = 0; mode = 'HYBRID'; semanticWeight = 0.7 } } -Predicate { param($r) (Expect-ResponseOk $r) -and @($r.Json.data).Count -ge 1 } -Expectation 'local search should return the identifier bound on county-a' | Out-Null
Invoke-Test -Id 'SEARCH-003' -Module 'search' -Name 'global search recursive' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountryBase/api/v2/search/global" -Body @{ naturalQuery = $searchKeyword; keywords = @('sample','meter'); fields = @('description','name','owner'); filters = @{}; limit = 10; offset = 0; mode = 'KEYWORD'; semanticWeight = 0.0; strategy = 'RECURSIVE'; ttl = 6; maxFanout = 8; perNodeLimit = 10; globalDeadlineMs = 5000; hopTimeoutMs = 1500; traceId = "smoke-rec-$suffix" } } -Predicate { param($r) (Expect-ResponseOk $r) -and @($r.Json.data.items).Count -ge 2 } -Expectation 'recursive global search should find identifiers from both county nodes' | Out-Null
Invoke-Test -Id 'SEARCH-004' -Module 'search' -Name 'global search iterative' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountryBase/api/v2/search/global" -Body @{ naturalQuery = $searchKeyword; keywords = @('sample','meter'); fields = @('description','name','owner'); filters = @{}; limit = 10; offset = 0; mode = 'KEYWORD'; semanticWeight = 0.0; strategy = 'ITERATIVE'; ttl = 6; maxFanout = 8; perNodeLimit = 10; globalDeadlineMs = 5000; hopTimeoutMs = 1500; traceId = "smoke-it-$suffix" } } -Predicate { param($r) (Expect-ResponseOk $r) -and @($r.Json.data.items).Count -ge 2 } -Expectation 'iterative global search should also find identifiers from both county nodes' | Out-Null

Invoke-Test -Id 'AUDIT-001' -Module 'audit' -Name 'query identifier history' -Action { Invoke-JsonRequest -Uri "$CountyABase/api/v2/audit/identifier-history?identifier=$idA&page=1&size=20" } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data.total -ge 1 } -Expectation 'audit history should contain registration and update records for identifier A' | Out-Null

$uploadSessionResp = Invoke-Test -Id 'UPLOAD-001' -Module 'upload' -Name 'create upload session' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountyABase/api/v2/identifiers/$idA/upload-sessions" -Body @{ profile = 'data_json'; filename = 'sample.json'; size = 128; sha256 = ''; serviceEndpoint = '' } } -Predicate { param($r) (Expect-ResponseOk $r) -and -not [string]::IsNullOrWhiteSpace($r.Json.data.uploadId) } -Expectation 'upload session should be created for identifier A' -ReturnResponse
$uploadId = if (Expect-ResponseOk $uploadSessionResp) { [string]$uploadSessionResp.Json.data.uploadId } else { '' }
Invoke-Test -Id 'UPLOAD-002' -Module 'upload' -Name 'complete upload without actual object' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountyABase/api/v2/upload-sessions/$uploadId/complete" } -Predicate { param($r) (-not $r.Success) -and $r.StatusCode -ge 400 } -Expectation 'completing upload without a real object should fail as a negative case' | Out-Null
Invoke-Test -Id 'DOWNLOAD-001' -Module 'download' -Name 'create download presign' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$CountryBase/api/v2/downloads/presign" -Body @{ identifier = $idA; expiresInSeconds = 600 } } -Predicate { param($r) Expect-ResponseOk $r } -Expectation 'download presign should return a downloadable locator or signed descriptor' | Out-Null

$oldMcp = $env:MCP_SERVER_URL
$oldSmokeId = $env:SMOKE_IDENTIFIER
$oldSmokeQuery = $env:SMOKE_QUERY
$env:MCP_SERVER_URL = "$McpBase/mcp"
$env:SMOKE_IDENTIFIER = $idA
$env:SMOKE_QUERY = $searchKeyword
try {
    $mcpSmokeText = & cmd /c "cd /d e:\irp\doip-sdk\dris-system-deploy-20260310\dris-mcp-server && npm run smoke" 2>&1 | Out-String
    $mcpSmokePassed = ($LASTEXITCODE -eq 0)
    $mcpActual = if ($mcpSmokePassed) { 'npm run smoke exit code 0' } else { "npm run smoke exit code $LASTEXITCODE" }; $mcpStatus = if ($mcpSmokePassed) { 0 } else { $LASTEXITCODE }; Add-TestResult -Id 'MCP-001' -Module 'mcp' -Name 'mcp smoke run' -Passed $mcpSmokePassed -Expectation 'mcp smoke should list tools and complete core tool calls' -Actual $mcpActual -StatusCode $mcpStatus -Evidence $mcpSmokeText
} finally {
    $env:MCP_SERVER_URL = $oldMcp
    $env:SMOKE_IDENTIFIER = $oldSmokeId
    $env:SMOKE_QUERY = $oldSmokeQuery
}

Invoke-Test -Id 'AGENT-001' -Module 'agent' -Name 'list llm models' -Action { Invoke-JsonRequest -Uri "$OrchestratorBase/api/llm/models" } -Predicate { param($r) $r.Success -and $r.StatusCode -eq 200 -and $r.Json.ok -eq $true } -Expectation 'orchestrator should expose model list or an empty configured result' | Out-Null
$agentBody = @{ question = "compare $idA and $idB metadata, resource and audit history"; identifiersText = "$idA`n$idB"; mcpUrl = "$McpBase/mcp"; strategy = 'RECURSIVE'; searchMode = 'KEYWORD'; limit = 5; auditPageSize = 3 }
Invoke-Test -Id 'AGENT-002' -Module 'agent' -Name 'orchestrator run research' -Action { Invoke-JsonRequest -Method 'POST' -Uri "$OrchestratorBase/api/research/run" -Body $agentBody -TimeoutSec 120 } -Predicate { param($r) $r.Success -and $r.StatusCode -eq 200 -and $r.Json.ok -eq $true -and @($r.Json.focusedIdentifiers).Count -ge 2 } -Expectation 'orchestrator should run a bounded compare plan through MCP successfully' | Out-Null
Invoke-Test -Id 'AGENT-003' -Module 'agent' -Name 'orchestrator stream research' -Action {
    $body = Convert-BodyToJson -Body $agentBody
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Method 'POST' -Uri "$OrchestratorBase/api/research/stream" -Headers @{ Accept = 'text/event-stream' } -ContentType 'application/json' -Body $body -TimeoutSec 120
        [pscustomobject]@{ Success = $true; StatusCode = [int]$resp.StatusCode; Content = $resp.Content; Json = [pscustomobject]@{ ok = $true }; Error = $null }
    } catch {
        $bodyText = Read-ErrorBody -ErrorRecord $_
        [pscustomobject]@{ Success = $false; StatusCode = -1; Content = $bodyText; Json = $null; Error = $_.Exception.Message }
    }
} -Predicate { param($r) $r.Success -and $r.StatusCode -eq 200 -and $r.Content -match 'event: done' } -Expectation 'orchestrator streaming endpoint should finish with a done event' | Out-Null

Invoke-Test -Id 'ID-006' -Module 'identifier' -Name 'delete identifier on county-b' -Action { Invoke-JsonRequest -Method 'DELETE' -Uri "$CountyBBase/api/v2/identifiers/$idB" } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data -eq $true } -Expectation 'identifier B should be deletable after tests' | Out-Null
Invoke-Test -Id 'ID-007' -Module 'identifier' -Name 'confirm identifier B deleted' -Action { Invoke-JsonRequest -Uri "$CountyBBase/api/v2/identifiers/$idB/exists" } -Predicate { param($r) (Expect-ResponseOk $r) -and $r.Json.data -eq $false } -Expectation 'identifier B should no longer exist after deletion' | Out-Null

$summary = [pscustomobject]@{
    runStartedAt = $script:RunStartedAt.ToString('s')
    runFinishedAt = (Get-Date).ToString('s')
    suffix = $suffix
    identifiers = [pscustomobject]@{ countyA = $idA; countyB = $idB }
    totals = [pscustomobject]@{
        total = $script:Results.Count
        passed = @($script:Results | Where-Object { $_.passed }).Count
        failed = @($script:Results | Where-Object { -not $_.passed }).Count
    }
    results = $script:Results
}

$jsonPath = Join-Path $OutputDir ("system-functional-smoke-" + $suffix + ".json")
$summary | ConvertTo-Json -Depth 20 | Set-Content -Path $jsonPath -Encoding utf8

$failed = @($script:Results | Where-Object { -not $_.passed })
Write-Host "Smoke run finished."
Write-Host "JSON report: $jsonPath"
Write-Host ("Passed {0}/{1}" -f $summary.totals.passed, $summary.totals.total)
if ($failed.Count -gt 0) {
    Write-Host 'Failed cases:'
    $failed | ForEach-Object { Write-Host (" - {0} {1}" -f $_.id, $_.name) }
}



