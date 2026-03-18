param(
    [string]$CountryBase = "http://127.0.0.1:8200",
    [string]$ProvinceBase = "http://127.0.0.1:8201",
    [string]$CityABase = "http://127.0.0.1:8202",
    [string]$CountyABase = "http://127.0.0.1:8203",
    [string]$CityBBase = "http://127.0.0.1:8204",
    [string]$CountyBBase = "http://127.0.0.1:8205"
)

function Invoke-JsonPost {
    param(
        [string]$Uri,
        [object]$Body
    )
    $json = $Body | ConvertTo-Json -Depth 12
    return Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Body $json
}

function Invoke-JsonPut {
    param(
        [string]$Uri,
        [object]$Body
    )
    $json = $Body | ConvertTo-Json -Depth 12
    return Invoke-RestMethod -Method Put -Uri $Uri -ContentType "application/json" -Body $json
}

function Register-SampleIdentifier {
    param(
        [string]$BaseUrl = "http://127.0.0.1:8203",
        [string]$OrgCode = "CNTA",
        [string]$Name = "sample-meter"
    )
    $body = @{
        regionCode = "1100"
        organizationCode = $OrgCode
        industry = "energy"
        dataCategory = "public"
        registrationType = "resource"
        owner = "manual-lab"
        description = "manual sample"
        applicationScenario = "lab"
        name = $Name
        entityType = "device"
        additionalAttributes = @{
            source = "manual"
        }
    }
    $resp = Invoke-JsonPost -Uri "$BaseUrl/api/v2/identifiers/register" -Body $body
    return $resp.data
}

function Bind-SampleResource {
    param(
        [string]$BaseUrl = "http://127.0.0.1:8203",
        [string]$Identifier
    )
    $body = @{
        componentType = "resource"
        storageType = "local"
        accessMethod = "download"
        contentType = "application/json"
        format = "json"
        locator = "local://manual/$Identifier.json"
        size = 128
        hashValue = ""
        etag = ""
        versionId = ""
        createdAt = ""
        extra = @{
            quality = "manual"
        }
    }
    return Invoke-JsonPut -Uri "$BaseUrl/api/v2/identifiers/$Identifier/component" -Body $body
}

Write-Host "Helper loaded."
Write-Host "Examples:"
Write-Host '  $id = Register-SampleIdentifier -BaseUrl $CountyABase -OrgCode "CNTA" -Name "meter-a"'
Write-Host '  Invoke-RestMethod "$CountryBase/api/v2/identifiers/$id/metadata"'
Write-Host '  Bind-SampleResource -BaseUrl $CountyABase -Identifier $id'
Write-Host '  Invoke-RestMethod -Method Post -Uri "$CountryBase/api/v2/identifiers/resolve/batch/distributed" -ContentType "application/json" -Body (@($id) | ConvertTo-Json)'


