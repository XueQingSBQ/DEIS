param(
    [string]$DatasetPath = "$PSScriptRoot\open_catalog_dataset_360.json",
    [int]$Limit = 0,
    [int]$ChunkSize = 20,
    [switch]$SkipBind,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-JsonFile {
    param([string]$Path)
    return (Get-Content -Raw -Encoding UTF8 $Path | ConvertFrom-Json)
}

function Write-JsonFile {
    param(
        [string]$Path,
        [object]$Value
    )
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    $json = $Value | ConvertTo-Json -Depth 100
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Get-HttpErrorText {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)
    $response = $null
    if ($null -ne $ErrorRecord.Exception -and $ErrorRecord.Exception.PSObject.Properties.Match('Response').Count -gt 0) {
        $response = $ErrorRecord.Exception.Response
    }
    if ($null -eq $response) {
        return $ErrorRecord.Exception.Message
    }
    try {
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        return $reader.ReadToEnd()
    }
    catch {
        return $ErrorRecord.Exception.Message
    }
}
function Chunk-Array {
    param(
        [object[]]$Items,
        [int]$Size
    )
    $chunks = @()
    for ($i = 0; $i -lt $Items.Count; $i += $Size) {
        $end = [Math]::Min($i + $Size - 1, $Items.Count - 1)
        $bucket = @()
        for ($j = $i; $j -le $end; $j++) {
            $bucket += $Items[$j]
        }
        $chunks += ,$bucket
    }
    return $chunks
}

$records = @(Read-JsonFile -Path $DatasetPath)
if ($Limit -gt 0) {
    $records = @($records | Select-Object -First $Limit)
}
if (-not $records.Count) {
    throw "No records found in $DatasetPath"
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultDir = Join-Path $PSScriptRoot 'import-results'
$resultPath = Join-Path $resultDir "import-$timestamp.json"

$results = New-Object System.Collections.Generic.List[object]
$grouped = $records | Group-Object { $_.targetNode.baseUrl }

Write-Host "Import records:" $records.Count
Write-Host "Node groups:" $grouped.Count

foreach ($group in $grouped) {
    $baseUrl = [string]$group.Name
    $items = @($group.Group)
    Write-Host "-> node $baseUrl records=$($items.Count)"

    for ($offset = 0; $offset -lt $items.Count; $offset += $ChunkSize) {
        $last = [Math]::Min($offset + $ChunkSize - 1, $items.Count - 1)
        $chunk = @($items[$offset..$last])

        if ($DryRun) {
            foreach ($item in $chunk) {
                $results.Add([pscustomobject]@{
                    recordId = $item.recordId
                    baseUrl = $baseUrl
                    identifier = $null
                    registerOk = $false
                    bindOk = $false
                    skipped = $true
                    reason = 'dry-run'
                })
            }
            continue
        }

        $idMap = @{}
        $batchFailed = $false
        try {
            $batchBody = @($chunk | ForEach-Object { $_.registrationRequest }) | ConvertTo-Json -Depth 30
            $registerResp = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v2/identifiers/register/batch" -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($batchBody))
            $ids = @($registerResp.data)
            if ($ids.Count -ne $chunk.Count) {
                throw "registerBatch count mismatch on ${baseUrl}: ids=$($ids.Count) chunk=$($chunk.Count)"
            }
            for ($i = 0; $i -lt $chunk.Count; $i++) {
                $idMap[$chunk[$i].recordId] = [string]$ids[$i]
            }
        }
        catch {
            $batchFailed = $true
            Write-Warning ("Batch register failed on {0}, fallback to single requests. {1}" -f $baseUrl, (Get-HttpErrorText $_))
            foreach ($item in $chunk) {
                try {
                    $singleBody = $item.registrationRequest | ConvertTo-Json -Depth 30
                    $singleResp = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v2/identifiers/register" -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($singleBody))
                    $idMap[$item.recordId] = [string]$singleResp.data
                }
                catch {
                    $idMap[$item.recordId] = ""
                    $results.Add([pscustomobject]@{
                        recordId = $item.recordId
                        baseUrl = $baseUrl
                        identifier = $null
                        registerOk = $false
                        bindOk = $false
                        skipped = $false
                        reason = Get-HttpErrorText $_
                    })
                }
            }
        }

        for ($i = 0; $i -lt $chunk.Count; $i++) {
            $item = $chunk[$i]
            $existing = $results | Where-Object { $_.recordId -eq $item.recordId }
            if (@($existing).Count -gt 0) {
                continue
            }
            $identifier = $idMap[$item.recordId]
            $registerOk = -not [string]::IsNullOrWhiteSpace([string]$identifier)
            $bindOk = $false
            $reason = $null

            if ($registerOk -and -not $SkipBind) {
                try {
                    $bindBody = $item.resourceBinding | ConvertTo-Json -Depth 30
                    $null = Invoke-RestMethod -Method Put -Uri "$baseUrl/api/v2/identifiers/$identifier/component" -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($bindBody))
                    $bindOk = $true
                }
                catch {
                    $reason = Get-HttpErrorText $_
                }
            }
            elseif ($registerOk -and $SkipBind) {
                $bindOk = $false
                $reason = 'bind skipped'
            }
            else {
                $reason = if ($batchFailed) { 'single register failed' } else { 'register returned null identifier' }
            }

            $results.Add([pscustomobject]@{
                recordId = $item.recordId
                baseUrl = $baseUrl
                identifier = $identifier
                registerOk = $registerOk
                bindOk = $bindOk
                skipped = $false
                reason = $reason
            })
        }
    }
}

$summary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString('s')
    datasetPath = $DatasetPath
    totalRecords = $records.Count
    registerSuccess = @($results | Where-Object { $_.registerOk }).Count
    bindSuccess = @($results | Where-Object { $_.bindOk }).Count
    bindFailed = @($results | Where-Object { $_.registerOk -and -not $_.bindOk -and -not $_.skipped }).Count
    skipped = @($results | Where-Object { $_.skipped }).Count
    results = $results
}

Write-JsonFile -Path $resultPath -Value $summary
Write-Host "Result manifest:" $resultPath
Write-Host "Register success:" $summary.registerSuccess
Write-Host "Bind success:" $summary.bindSuccess
Write-Host "Bind failed:" $summary.bindFailed
