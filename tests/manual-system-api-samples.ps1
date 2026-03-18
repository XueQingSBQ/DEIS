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
    [string]$FrontendBase = "http://127.0.0.1:5176"
)

function Convert-RequestJson {
    param([object]$Body)
    if ($Body -is [string]) {
        return $Body
    }
    return $Body | ConvertTo-Json -Depth 20
}

function Invoke-DrisJsonGet {
    param([string]$Uri, [hashtable]$Headers)
    Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers
}

function Invoke-DrisJsonPost {
    param([string]$Uri, [object]$Body, [hashtable]$Headers)
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType 'application/json' -Body (Convert-RequestJson $Body)
}

function Invoke-DrisJsonPut {
    param([string]$Uri, [object]$Body, [hashtable]$Headers)
    Invoke-RestMethod -Method Put -Uri $Uri -Headers $Headers -ContentType 'application/json' -Body (Convert-RequestJson $Body)
}

function Invoke-DrisJsonPatch {
    param([string]$Uri, [object]$Body, [hashtable]$Headers)
    Invoke-RestMethod -Method Patch -Uri $Uri -Headers $Headers -ContentType 'application/json' -Body (Convert-RequestJson $Body)
}

function Invoke-DrisJsonDelete {
    param([string]$Uri, [hashtable]$Headers)
    Invoke-RestMethod -Method Delete -Uri $Uri -Headers $Headers
}

function New-SampleSuffix {
    Get-Date -Format 'yyyyMMddHHmmss'
}

function Register-SampleIdentifier {
    param(
        [string]$BaseUrl = 'http://127.0.0.1:8203',
        [string]$OrgCode = 'CNTA',
        [string]$Name = 'sample-meter',
        [string]$Owner = 'manual-lab',
        [string]$Description = 'manual sample object',
        [hashtable]$AdditionalAttributes = @{ source = 'manual' }
    )

    $body = @{
        regionCode = '1100'
        organizationCode = $OrgCode
        industry = 'energy'
        dataCategory = 'public'
        registrationType = 'resource'
        owner = $Owner
        description = $Description
        applicationScenario = 'lab'
        name = $Name
        entityType = 'device'
        additionalAttributes = $AdditionalAttributes
    }

    $resp = Invoke-DrisJsonPost -Uri "$BaseUrl/api/v2/identifiers/register" -Body $body
    return $resp.data
}

function Bind-SampleResource {
    param(
        [string]$BaseUrl = 'http://127.0.0.1:8203',
        [string]$Identifier,
        [string]$Locator,
        [long]$Size = 128,
        [string]$Format = 'json',
        [string]$ContentType = 'application/json'
    )

    if ([string]::IsNullOrWhiteSpace($Locator)) {
        $Locator = "local://manual/$Identifier.$Format"
    }

    $body = @{
        componentType = 'resource'
        role = 'primary'
        storageType = 'external_uri'
        accessMethod = 'download'
        contentType = $ContentType
        format = $Format
        locator = $Locator
        size = $Size
        hashValue = ''
        etag = ''
        versionId = ''
        createdAt = (Get-Date).ToString('s')
        extra = @{ quality = 'manual' }
    }

    Invoke-DrisJsonPut -Uri "$BaseUrl/api/v2/identifiers/$Identifier/component" -Body $body
}

function New-UploadSession {
    param(
        [string]$BaseUrl = 'http://127.0.0.1:8203',
        [string]$Identifier,
        [string]$Profile = 'data_json',
        [string]$Filename = 'sample.json',
        [long]$Size = 128
    )

    $body = @{
        profile = $Profile
        filename = $Filename
        size = $Size
        sha256 = ''
        serviceEndpoint = ''
    }

    Invoke-DrisJsonPost -Uri "$BaseUrl/api/v2/identifiers/$Identifier/upload-sessions" -Body $body
}

function Complete-UploadSession {
    param(
        [string]$BaseUrl = 'http://127.0.0.1:8203',
        [string]$UploadId
    )

    Invoke-DrisJsonPost -Uri "$BaseUrl/api/v2/upload-sessions/$UploadId/complete" -Body @{}
}

function Invoke-LocalSearch {
    param(
        [string]$BaseUrl = 'http://127.0.0.1:8203',
        [string]$Query
    )

    $body = @{
        naturalQuery = $Query
        keywords = @('sample', 'meter')
        fields = @('description', 'name', 'owner')
        filters = @{}
        limit = 10
        offset = 0
        mode = 'HYBRID'
        semanticWeight = 0.7
    }

    Invoke-DrisJsonPost -Uri "$BaseUrl/api/v2/search" -Body $body
}

function Invoke-GlobalSearch {
    param(
        [string]$BaseUrl = 'http://127.0.0.1:8200',
        [string]$Query,
        [ValidateSet('RECURSIVE','ITERATIVE')]
        [string]$Strategy = 'RECURSIVE'
    )

    $body = @{
        naturalQuery = $Query
        keywords = @('sample', 'meter')
        fields = @('description', 'name', 'owner')
        filters = @{}
        limit = 10
        offset = 0
        mode = 'KEYWORD'
        semanticWeight = 0.0
        strategy = $Strategy
        ttl = 6
        maxFanout = 8
        perNodeLimit = 10
        globalDeadlineMs = 5000
        hopTimeoutMs = 1500
        traceId = "manual-$Strategy-$(Get-Date -Format 'yyyyMMddHHmmss')"
    }

    Invoke-DrisJsonPost -Uri "$BaseUrl/api/v2/search/global" -Body $body
}

function Register-AuthUser {
    param(
        [string]$Username,
        [string]$Password = 'Passw0rd!',
        [string]$DisplayName = 'Manual Tester',
        [string]$OrgCode = 'CNTA'
    )

    $body = @{
        username = $Username
        password = $Password
        displayName = $DisplayName
        orgCode = $OrgCode
        email = "$Username@example.com"
        phone = '13800000000'
    }

    Invoke-DrisJsonPost -Uri "$AuthBase/auth/register" -Body $body
}

function Login-AuthUser {
    param(
        [string]$Username,
        [string]$Password = 'Passw0rd!'
    )

    Invoke-DrisJsonPost -Uri "$AuthBase/auth/login" -Body @{ username = $Username; password = $Password }
}

function Invoke-AgentRun {
    param(
        [string]$IdentifierA,
        [string]$IdentifierB,
        [string]$Question = ''
    )

    if ([string]::IsNullOrWhiteSpace($Question)) {
        $Question = "compare $IdentifierA and $IdentifierB metadata, resource and audit history"
    }

    $body = @{
        question = $Question
        identifiersText = "$IdentifierA`n$IdentifierB"
        mcpUrl = "$McpBase/mcp"
        strategy = 'RECURSIVE'
        searchMode = 'KEYWORD'
        limit = 5
        auditPageSize = 3
    }

    Invoke-DrisJsonPost -Uri "$OrchestratorBase/api/research/run" -Body $body
}

function New-AuthSampleUsername {
    param([string]$Prefix = 'tester')

    return "$Prefix`_$(Get-Date -Format 'yyyyMMddHHmmss')"
}

function Get-AuthPublicKey {
    Invoke-DrisJsonGet -Uri "$AuthBase/auth/public-key"
}

function Invoke-AuthPasswordToken {
    param(
        [string]$Username,
        [string]$Password = 'Passw0rd!'
    )

    $body = "grant_type=password&username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($Password))"
    Invoke-RestMethod -Method Post -Uri "$AuthBase/oauth2/token" -ContentType 'application/x-www-form-urlencoded' -Body $body
}

function Get-AuthUserInfo {
    param([string]$AccessToken)

    Invoke-DrisJsonGet -Uri "$AuthBase/auth/userinfo" -Headers @{ Authorization = "Bearer $AccessToken" }
}

function Get-McpHealth {
    Invoke-DrisJsonGet -Uri "$McpBase/health"
}

function Invoke-McpSmoke {
    param(
        [string]$Identifier = '',
        [string]$Query = 'sample',
        [string]$McpUrl = "$McpBase/mcp"
    )

    $moduleDir = Resolve-Path (Join-Path $PSScriptRoot '..\dris-mcp-server')
    $oldMcpUrl = $env:MCP_SERVER_URL
    $oldSmokeId = $env:SMOKE_IDENTIFIER
    $oldSmokeQuery = $env:SMOKE_QUERY
    $env:MCP_SERVER_URL = $McpUrl
    $env:SMOKE_IDENTIFIER = $Identifier
    $env:SMOKE_QUERY = $Query
    try {
        return (& cmd /c "cd /d $moduleDir && npm run smoke" 2>&1 | Out-String)
    } finally {
        $env:MCP_SERVER_URL = $oldMcpUrl
        $env:SMOKE_IDENTIFIER = $oldSmokeId
        $env:SMOKE_QUERY = $oldSmokeQuery
    }
}

function Get-OrchestratorHealth {
    Invoke-DrisJsonGet -Uri "$OrchestratorBase/health"
}

function Get-AgentModels {
    Invoke-DrisJsonGet -Uri "$OrchestratorBase/api/llm/models"
}

function Invoke-AgentStream {
    param(
        [string]$IdentifierA,
        [string]$IdentifierB,
        [string]$Question = ''
    )

    if ([string]::IsNullOrWhiteSpace($Question)) {
        $Question = "compare $IdentifierA and $IdentifierB metadata, resource and audit history"
    }

    $body = @{
        question = $Question
        identifiersText = "$IdentifierA`n$IdentifierB"
        mcpUrl = "$McpBase/mcp"
        strategy = 'RECURSIVE'
        searchMode = 'KEYWORD'
        limit = 5
        auditPageSize = 3
    } | ConvertTo-Json -Depth 10

    Invoke-WebRequest -Method Post -Uri "$OrchestratorBase/api/research/stream" -Headers @{ Accept = 'text/event-stream' } -ContentType 'application/json' -Body $body
}

function Get-FrontendHome {
    Invoke-WebRequest -Uri $FrontendBase -UseBasicParsing
}

function New-AgentSamplePair {
    param(
        [string]$CountyAOwner = 'manual-agent-a',
        [string]$CountyBOwner = 'manual-agent-b'
    )

    $suffix = New-SampleSuffix
    $idA = Register-SampleIdentifier -BaseUrl $CountyABase -OrgCode 'CNTA' -Name ("agent-sample-a-$suffix") -Owner $CountyAOwner -Description 'agent sample object county-a'
    $idB = Register-SampleIdentifier -BaseUrl $CountyBBase -OrgCode 'CNTB' -Name ("agent-sample-b-$suffix") -Owner $CountyBOwner -Description 'agent sample object county-b'
    Bind-SampleResource -BaseUrl $CountyABase -Identifier $idA -Locator "local://manual/$idA.json" -Size 256 -Format 'json' -ContentType 'application/json' | Out-Null

    return [pscustomobject]@{
        IdentifierA = $idA
        IdentifierB = $idB
    }
}

Write-Host 'System helper loaded.'
Write-Host 'Examples:'
Write-Host '  $idA = Register-SampleIdentifier -BaseUrl $CountyABase -OrgCode "CNTA" -Name ("sample-meter-a-" + (New-SampleSuffix))'
Write-Host '  $idB = Register-SampleIdentifier -BaseUrl $CountyBBase -OrgCode "CNTB" -Name ("sample-meter-b-" + (New-SampleSuffix))'
Write-Host '  Bind-SampleResource -BaseUrl $CountyABase -Identifier $idA'
Write-Host '  $u = New-AuthSampleUsername'
Write-Host '  Register-AuthUser -Username $u'
Write-Host '  $login = Login-AuthUser -Username $u'
Write-Host '  Get-AuthUserInfo -AccessToken $login.data.accessToken'
Write-Host '  Get-McpHealth'
Write-Host '  Invoke-McpSmoke -Identifier $idA'
Write-Host '  Get-OrchestratorHealth'
Write-Host '  $pair = New-AgentSamplePair'
Write-Host '  Invoke-AgentRun -IdentifierA $pair.IdentifierA -IdentifierB $pair.IdentifierB'
Write-Host '  Invoke-AgentStream -IdentifierA $pair.IdentifierA -IdentifierB $pair.IdentifierB'