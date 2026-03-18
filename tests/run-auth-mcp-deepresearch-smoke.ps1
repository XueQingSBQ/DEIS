param(
    [string]$CountryBase = "http://127.0.0.1:8200",
    [string]$CountyABase = "http://127.0.0.1:8203",
    [string]$CountyBBase = "http://127.0.0.1:8205",
    [string]$AuthBase = "http://127.0.0.1:8290",
    [string]$McpBase = "http://127.0.0.1:38082",
    [string]$OrchestratorBase = "http://127.0.0.1:38083",
    [string]$FrontendBase = "http://127.0.0.1:5176",
    [string]$OutputDir = ".\\output"
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\manual-system-api-samples.ps1" -CountryBase $CountryBase -CountyABase $CountyABase -CountyBBase $CountyBBase -AuthBase $AuthBase -McpBase $McpBase -OrchestratorBase $OrchestratorBase -FrontendBase $FrontendBase

$script:Results = New-Object System.Collections.Generic.List[object]
$runId = Get-Date -Format 'yyyyMMddHHmmss'
if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

function Format-Evidence {
    param([object]$Evidence)

    if ($null -eq $Evidence) {
        return $null
    }
    if ($Evidence -is [string]) {
        return $Evidence
    }
    try {
        return ($Evidence | ConvertTo-Json -Depth 12 -Compress)
    } catch {
        return [string]$Evidence
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
        [object]$Evidence = $null
    )

    $script:Results.Add([pscustomobject]@{
        id = $Id
        module = $Module
        name = $Name
        passed = $Passed
        expectation = $Expectation
        actual = $Actual
        evidence = (Format-Evidence -Evidence $Evidence)
    }) | Out-Null
}

function Invoke-TestCase {
    param(
        [string]$Id,
        [string]$Module,
        [string]$Name,
        [scriptblock]$Action,
        [scriptblock]$Assert,
        [string]$Expectation,
        [switch]$ReturnValue
    )

    try {
        $value = & $Action
        $passed = & $Assert $value
        $actual = if ($passed) { 'success' } else { 'assertion failed' }
        Add-TestResult -Id $Id -Module $Module -Name $Name -Passed $passed -Expectation $Expectation -Actual $actual -Evidence $value
        if ($ReturnValue) {
            return $value
        }
    } catch {
        Add-TestResult -Id $Id -Module $Module -Name $Name -Passed $false -Expectation $Expectation -Actual $_.Exception.Message -Evidence $null
        if ($ReturnValue) {
            return $null
        }
    }
}

function Invoke-AgentStreamText {
    param(
        [string]$IdentifierA,
        [string]$IdentifierB,
        [string]$Question
    )

    $body = @{
        question = $Question
        identifiersText = "$IdentifierA`n$IdentifierB"
        mcpUrl = "$McpBase/mcp"
        strategy = 'RECURSIVE'
        searchMode = 'KEYWORD'
        limit = 5
        auditPageSize = 3
    } | ConvertTo-Json -Depth 10

    $tmpFile = Join-Path $env:TEMP ("dris-agent-stream-$runId.json")
    Set-Content -Path $tmpFile -Value $body -Encoding UTF8
    try {
        return (& curl.exe -sS -N -X POST "$OrchestratorBase/api/research/stream" -H "Accept: text/event-stream" -H "Content-Type: application/json" --data-binary "@$tmpFile" | Out-String)
    } finally {
        Remove-Item -Path $tmpFile -Force -ErrorAction SilentlyContinue
    }
}

$authUser = Invoke-TestCase -Id 'AUTH-001' -Module 'auth' -Name 'register and login user' -Action {
    $username = New-AuthSampleUsername -Prefix 'authmcp'
    $register = Register-AuthUser -Username $username -Password 'Passw0rd!' -DisplayName 'Auth MCP Tester' -OrgCode 'CNTA'
    $login = Login-AuthUser -Username $username -Password 'Passw0rd!'
    [pscustomobject]@{
        username = $username
        register = $register
        login = $login
    }
} -Assert {
    param($value)
    $null -ne $value -and $value.register.ok -eq $true -and $value.login.ok -eq $true -and -not [string]::IsNullOrWhiteSpace([string]$value.login.data.accessToken)
} -Expectation 'auth center should support user registration and login with a non-empty access token' -ReturnValue

$accessToken = if ($authUser) { [string]$authUser.login.data.accessToken } else { '' }
$username = if ($authUser) { [string]$authUser.username } else { '' }

Invoke-TestCase -Id 'AUTH-002' -Module 'auth' -Name 'oauth2 password token' -Action {
    Invoke-AuthPasswordToken -Username $username -Password 'Passw0rd!'
} -Assert {
    param($value)
    $null -ne $value -and $value.ok -eq $true -and -not [string]::IsNullOrWhiteSpace([string]$value.data.accessToken)
} -Expectation 'oauth2 password flow should issue an access token for the same test user' | Out-Null

Invoke-TestCase -Id 'AUTH-003' -Module 'auth' -Name 'userinfo with bearer token' -Action {
    Get-AuthUserInfo -AccessToken $accessToken
} -Assert {
    param($value)
    $null -ne $value -and $value.ok -eq $true -and [string]$value.data.username -eq $username
} -Expectation 'userinfo should return the same username as the login subject' | Out-Null

Invoke-TestCase -Id 'AUTH-004' -Module 'auth' -Name 'public key endpoint' -Action {
    Get-AuthPublicKey
} -Assert {
    param($value)
    $null -ne $value -and $value.ok -eq $true -and [string]$value.data.algorithm -eq 'RS256'
} -Expectation 'public key endpoint should expose RS256 metadata and PEM content' | Out-Null

$pair = Invoke-TestCase -Id 'SETUP-001' -Module 'setup' -Name 'prepare research identifiers' -Action {
    New-AgentSamplePair
} -Assert {
    param($value)
    $null -ne $value -and -not [string]::IsNullOrWhiteSpace([string]$value.IdentifierA) -and -not [string]::IsNullOrWhiteSpace([string]$value.IdentifierB)
} -Expectation 'two identifiers should be created across county nodes and at least one should have a bound resource for research' -ReturnValue

$idA = if ($pair) { [string]$pair.IdentifierA } else { '' }
$idB = if ($pair) { [string]$pair.IdentifierB } else { '' }
$question = "compare $idA and $idB metadata, resource and audit history"

Invoke-TestCase -Id 'MCP-001' -Module 'mcp' -Name 'health endpoint' -Action {
    Get-McpHealth
} -Assert {
    param($value)
    $null -ne $value -and $value.ok -eq $true -and [string]$value.drisBaseUrl -eq $CountryBase
} -Expectation 'mcp health should report ok=true and point to the 6-node entry backend' | Out-Null

Invoke-TestCase -Id 'MCP-002' -Module 'mcp' -Name 'npm smoke run' -Action {
    Invoke-McpSmoke -Identifier $idA -Query 'agent sample'
} -Assert {
    param($value)
    ($value -match '\[smoke\] tools:') -and ($value -match 'resolve_metadata') -and ($value -match 'get_node_routes')
} -Expectation 'mcp smoke should list registered tools and complete core read-only tool calls' | Out-Null

Invoke-TestCase -Id 'AGENT-001' -Module 'deepresearch' -Name 'orchestrator health' -Action {
    Get-OrchestratorHealth
} -Assert {
    param($value)
    $null -ne $value -and $value.ok -eq $true -and -not [string]::IsNullOrWhiteSpace([string]$value.mcpServerUrl)
} -Expectation 'orchestrator health should expose planner mode and bound MCP server URL' | Out-Null

Invoke-TestCase -Id 'AGENT-002' -Module 'deepresearch' -Name 'model list endpoint' -Action {
    Get-AgentModels
} -Assert {
    param($value)
    $null -ne $value -and $value.ok -eq $true -and $null -ne $value.models
} -Expectation 'llm model list endpoint should return ok=true and a models field even when configuration changes' | Out-Null

Invoke-TestCase -Id 'AGENT-003' -Module 'deepresearch' -Name 'run research api' -Action {
    Invoke-AgentRun -IdentifierA $idA -IdentifierB $idB -Question $question
} -Assert {
    param($value)
    $null -ne $value -and $value.ok -eq $true -and @($value.focusedIdentifiers).Count -ge 2 -and $null -ne $value.report
} -Expectation 'run api should produce a bounded research plan, focused identifiers and a report' | Out-Null

Invoke-TestCase -Id 'AGENT-004' -Module 'deepresearch' -Name 'stream research api' -Action {
    Invoke-AgentStreamText -IdentifierA $idA -IdentifierB $idB -Question $question
} -Assert {
    param($value)
    ($value -match 'event: plan') -and ($value -match 'event: report') -and ($value -match 'event: done')
} -Expectation 'stream api should emit plan, report and done events in SSE format' | Out-Null

Invoke-TestCase -Id 'FRONT-001' -Module 'frontend' -Name 'frontend home page' -Action {
    Get-FrontendHome
} -Assert {
    param($value)
    $null -ne $value -and [int]$value.StatusCode -eq 200 -and ($value.Content -match 'DRIS DeepResearch' -or $value.Content -match 'id="app"')
} -Expectation 'deepresearch frontend should load successfully and return the built index html' | Out-Null

$passed = @($script:Results | Where-Object { $_.passed }).Count
$total = $script:Results.Count
$summary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString('s')
    total = $total
    passed = $passed
    failed = $total - $passed
    authBase = $AuthBase
    mcpBase = $McpBase
    orchestratorBase = $OrchestratorBase
    frontendBase = $FrontendBase
    identifierA = $idA
    identifierB = $idB
    results = $script:Results
}
$outputPath = Join-Path $OutputDir ("auth-mcp-deepresearch-smoke-$runId.json")
$summary | ConvertTo-Json -Depth 20 | Set-Content -Path $outputPath -Encoding UTF8
Write-Host "saved: $outputPath"
$summary | ConvertTo-Json -Depth 8