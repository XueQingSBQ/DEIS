param(
    [string]$SourceRoot = "",
    [string]$MinistryBaseUrl = "http://127.0.0.1:8300",
    [string]$SystemBaseUrl = "http://127.0.0.1:8303",
    [int]$TargetPerFolder = 200,
    [string]$PlanFile = "",
    [string]$OnlyBaseUrl = "",
    [int]$WorkerIndex = -1,
    [int]$WorkerCount = 1,
    [switch]$ExecuteImport,
    [switch]$SkipIndexRebuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$mysql = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'
$mysqlPassword = 'sbq254172'
$rootDir = Split-Path -Parent $PSCommandPath
$runtimeDir = Join-Path $rootDir 'runtime'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$planPath = Join-Path $runtimeDir ("flood-docs-plan-" + $timestamp + ".json")
$resultPath = Join-Path $runtimeDir ("flood-docs-import-" + $timestamp + ".json")

$NodeLabelMinistry = [string]::Concat([char]0x6C34, [char]0x5229, [char]0x90E8)
$NodeLabelWaterBureau = [string]::Concat([char]0x6C34, [char]0x5229, [char]0x5C40)
$IndustryWater = [string]::Concat([char]0x6C34, [char]0x5229, [char]0x7BA1, [char]0x7406, [char]0x4E1A)
$ScenarioText = 'flash-flood document retrieval and research'
$SourceSiteText = 'China Flash Flood Prevention Website'

function Write-JsonFile {
    param(
        [string]$Path,
        [object]$Value
    )
    $json = $Value | ConvertTo-Json -Depth 100
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Read-JsonFile {
    param([string]$Path)
    return (Get-Content -Raw -Encoding UTF8 $Path | ConvertFrom-Json)
}

function Invoke-JsonApi {
    param(
        [ValidateSet('GET', 'POST', 'PUT')]
        [string]$Method,
        [string]$Uri,
        [object]$Body
    )
    if ($Method -eq 'GET') {
        return Invoke-RestMethod -Method Get -Uri $Uri
    }
    $json = if ($null -eq $Body) { '' } else { $Body | ConvertTo-Json -Depth 30 }
    return Invoke-RestMethod -Method $Method -Uri $Uri -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
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

function Get-ProfileByExtension {
    param([string]$Extension)
    $ext = if ($null -eq $Extension) { '' } else { $Extension.ToLowerInvariant() }
    switch ($ext) {
        '.pdf' { return 'doc_pdf' }
        '.docx' { return 'doc_word' }
        '.doc' { return 'doc_word' }
        '.xlsx' { return 'table_excel' }
        '.xls' { return 'table_excel' }
        default { return 'generic_file' }
    }
}

function Get-ContentTypeByExtension {
    param([string]$Extension)
    $ext = if ($null -eq $Extension) { '' } else { $Extension.ToLowerInvariant() }
    switch ($ext) {
        '.pdf' { return 'application/pdf' }
        '.docx' { return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
        '.doc' { return 'application/msword' }
        '.xlsx' { return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }
        '.xls' { return 'application/vnd.ms-excel' }
        default { return 'application/octet-stream' }
    }
}

function Get-FormatByExtension {
    param([string]$Extension)
    $ext = if ($null -eq $Extension) { '' } else { $Extension.TrimStart('.').ToLowerInvariant() }
    if ([string]::IsNullOrWhiteSpace($ext)) {
        return 'bin'
    }
    return $ext
}

function Get-CleanTitle {
    param([string]$FileName)
    $base = [System.IO.Path]::GetFileNameWithoutExtension($FileName)
    $base = $base -replace '^\d+_', ''
    $base = $base -replace '^[\d\-_ ]+', ''
    return $base.Trim()
}

function Get-RegionFromTitle {
    param([string]$Title)
    $text = if ($null -eq $Title) { '' } else { $Title.Trim() }
    if ([string]::IsNullOrWhiteSpace($text)) {
        return ''
    }
    $match = [regex]::Match($text, '([\p{IsCJKUnifiedIdeographs}]{2,16}(?:省|市|县|区|州|自治区|流域))')
    if ($match.Success) {
        return $match.Groups[1].Value.Trim()
    }
    return ''
}

function Resolve-DefaultSourceRoot {
    $candidateBase = 'E:\xwechat_files\wxid_8u2abyqy4rb922_9d05\msg\file\2026-03'
    if (-not (Test-Path $candidateBase)) {
        throw "Candidate base does not exist: $candidateBase"
    }
    $dirs = @()
    $dirs += @(Get-ChildItem -Path $candidateBase -Directory)
    $dirs += @(Get-ChildItem -Path $candidateBase -Recurse -Directory)
    foreach ($dir in $dirs) {
        $subDirs = @(Get-ChildItem -Path $dir.FullName -Directory -ErrorAction SilentlyContinue)
        if ($subDirs.Count -lt 7) {
            continue
        }
        $suffixes = @('01', '02', '03', '04', '05', '06', '07')
        $ok = $true
        foreach ($suffix in $suffixes) {
            if (-not ($subDirs | Where-Object { $_.Name -match ($suffix + '$') } | Select-Object -First 1)) {
                $ok = $false
                break
            }
        }
        if ($ok) {
            return $dir.FullName
        }
    }
    throw 'Unable to auto-detect source root under 2026-03'
}

function Resolve-FolderNameBySuffix {
    param(
        [string]$Root,
        [string]$Suffix
    )
    $match = Get-ChildItem -Path $Root -Directory | Where-Object { $_.Name -match ($Suffix + '$') } | Select-Object -First 1
    if ($null -eq $match) {
        throw "Cannot resolve folder by suffix: $Suffix"
    }
    return $match.Name
}

function Get-ThemeFromFolderName {
    param([string]$FolderName)
    $name = $FolderName
    if ($name -match '^[^_]+_(.+?)\d+$') {
        return $matches[1]
    }
    return $FolderName
}

function New-KeywordArray {
    param(
        [string]$Theme,
        [string]$Title,
        [string]$Region,
        [string]$NodeTarget
    )
    $set = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($value in @($Theme, '山洪灾害', $Title, $Region, $NodeTarget)) {
        $trimmed = if ($null -eq $value) { '' } else { $value.Trim() }
        if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
            [void]$set.Add($trimmed)
        }
    }
    return @($set)
}

function Get-ExistingImportedDocs {
    param([hashtable]$FolderMapPath)
    $query = @"
SELECT 'node1' AS node_name, JSON_UNQUOTE(JSON_EXTRACT(m.additional_attributes,'$.sourceFilename')) AS source_filename,
       m.entity_type, IFNULL(r.format,'') AS format
FROM dris_demo_water_node1.identifier_metadata m
LEFT JOIN dris_demo_water_node1.resource r ON r.identifier=m.identifier
UNION ALL
SELECT 'node4' AS node_name, JSON_UNQUOTE(JSON_EXTRACT(m.additional_attributes,'$.sourceFilename')) AS source_filename,
       m.entity_type, IFNULL(r.format,'') AS format
FROM dris_demo_water_node4.identifier_metadata m
LEFT JOIN dris_demo_water_node4.resource r ON r.identifier=m.identifier;
"@
    $rows = & $mysql -uroot "-p$mysqlPassword" --default-character-set=utf8mb4 -N -e $query
    $existing = New-Object 'System.Collections.Generic.List[object]'
    foreach ($line in $rows) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $parts = $line -split "`t", 4
        $nodeName = $parts[0]
        $sourceFilename = if ($parts.Count -gt 1) { $parts[1] } else { '' }
        $entityType = if ($parts.Count -gt 2) { $parts[2] } else { '' }
        $format = if ($parts.Count -gt 3) { $parts[3] } else { '' }
        if ([string]::IsNullOrWhiteSpace($sourceFilename)) {
            continue
        }
        if ($entityType -eq 'image') {
            continue
        }
        if ($format -match '^(jpeg|jpg|png|gif|bmp|webp)$') {
            continue
        }
        $folder = ''
        if ($FolderMapPath.ContainsKey($sourceFilename)) {
            $folder = $FolderMapPath[$sourceFilename]
        }
        $existing.Add([pscustomobject]@{
            nodeName = $nodeName
            sourceFilename = $sourceFilename
            folder = $folder
        })
    }
    return $existing
}

function Invoke-CurlUpload {
    param(
        [string]$FilePath,
        [string]$PutUrl,
        [hashtable]$Headers
    )
    $responseFile = Join-Path $runtimeDir ("curl-upload-" + [guid]::NewGuid().ToString('N') + ".txt")
    if (Test-Path $responseFile) {
        Remove-Item -Force $responseFile
    }
    $curlArgs = @('-sS', '-o', $responseFile, '-w', '%{http_code}', '-X', 'PUT')
    foreach ($key in $Headers.Keys) {
        $curlArgs += @('-H', ($key + ': ' + $Headers[$key]))
    }
    $curlArgs += @('--upload-file', $FilePath, $PutUrl)
    $httpCode = (& curl.exe @curlArgs).Trim()
    $body = if (Test-Path $responseFile) { Get-Content -Raw $responseFile } else { '' }
    Remove-Item -Force $responseFile -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        httpCode = $httpCode
        body = $body
    }
}

if ([string]::IsNullOrWhiteSpace($SourceRoot)) {
    $SourceRoot = Resolve-DefaultSourceRoot
}

if (-not (Test-Path $SourceRoot)) {
    throw "Source root does not exist: $SourceRoot"
}

$folderPlans = @(
    @{
        folder = Resolve-FolderNameBySuffix -Root $SourceRoot -Suffix '01'
        baseUrl = $MinistryBaseUrl
        regionCode = '1100'
        organizationCode = 'SLMB'
        owner = $NodeLabelMinistry
        nodeTarget = $NodeLabelMinistry
    },
    @{
        folder = Resolve-FolderNameBySuffix -Root $SourceRoot -Suffix '02'
        baseUrl = $MinistryBaseUrl
        regionCode = '1100'
        organizationCode = 'SLMB'
        owner = $NodeLabelMinistry
        nodeTarget = $NodeLabelMinistry
    },
    @{
        folder = Resolve-FolderNameBySuffix -Root $SourceRoot -Suffix '03'
        baseUrl = $MinistryBaseUrl
        regionCode = '1100'
        organizationCode = 'SLMB'
        owner = $NodeLabelMinistry
        nodeTarget = $NodeLabelMinistry
    },
    @{
        folder = Resolve-FolderNameBySuffix -Root $SourceRoot -Suffix '04'
        baseUrl = $MinistryBaseUrl
        regionCode = '1100'
        organizationCode = 'SLMB'
        owner = $NodeLabelMinistry
        nodeTarget = $NodeLabelMinistry
    },
    @{
        folder = Resolve-FolderNameBySuffix -Root $SourceRoot -Suffix '05'
        baseUrl = $SystemBaseUrl
        regionCode = '1100'
        organizationCode = 'SYSA'
        owner = $NodeLabelWaterBureau
        nodeTarget = $NodeLabelWaterBureau
    },
    @{
        folder = Resolve-FolderNameBySuffix -Root $SourceRoot -Suffix '06'
        baseUrl = $SystemBaseUrl
        regionCode = '1100'
        organizationCode = 'SYSA'
        owner = $NodeLabelWaterBureau
        nodeTarget = $NodeLabelWaterBureau
    },
    @{
        folder = Resolve-FolderNameBySuffix -Root $SourceRoot -Suffix '07'
        baseUrl = $SystemBaseUrl
        regionCode = '1100'
        organizationCode = 'SYSA'
        owner = $NodeLabelWaterBureau
        nodeTarget = $NodeLabelWaterBureau
    }
)

$folderMapByFilename = @{}
$rawFilesByFolder = @{}
foreach ($plan in $folderPlans) {
    $folder = [string]$plan.folder
    $folderPath = Join-Path $SourceRoot $folder
    if (-not (Test-Path $folderPath)) {
        throw "Folder not found: $folderPath"
    }
    $files = Get-ChildItem -Path $folderPath -Recurse -File | Where-Object {
        $_.Extension -match '^\.(pdf|doc|docx|xls|xlsx)$'
    } | Sort-Object Name
    $rawFilesByFolder[$folder] = @($files)
    foreach ($file in $files) {
        $folderMapByFilename[$file.Name] = $folder
    }
}

$existingDocs = @(Get-ExistingImportedDocs -FolderMapPath $folderMapByFilename)
$existingNameSet = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($item in $existingDocs) {
    [void]$existingNameSet.Add($item.sourceFilename)
}

$planRecords = @()
$folderSummaries = @()

if ([string]::IsNullOrWhiteSpace($PlanFile)) {
    $planRecordsBuffer = New-Object 'System.Collections.Generic.List[object]'
    $folderSummariesBuffer = New-Object 'System.Collections.Generic.List[object]'

    foreach ($plan in $folderPlans) {
        $folder = [string]$plan.folder
        $theme = Get-ThemeFromFolderName -FolderName $folder
        $files = @($rawFilesByFolder[$folder])
        $availableCount = $files.Count
        $targetCount = [Math]::Min($TargetPerFolder, $availableCount)
        $existingInFolder = @($existingDocs | Where-Object { $_.folder -eq $folder }).Count
        $neededCount = [Math]::Max(0, $targetCount - $existingInFolder)
        $selected = New-Object 'System.Collections.Generic.List[object]'
        foreach ($file in $files) {
            if ($selected.Count -ge $neededCount) {
                break
            }
            if ($existingNameSet.Contains($file.Name)) {
                continue
            }
            [void]$existingNameSet.Add($file.Name)
            $title = Get-CleanTitle -FileName $file.Name
            $region = Get-RegionFromTitle -Title $title
            $keywords = New-KeywordArray -Theme $theme -Title $title -Region $region -NodeTarget ([string]$plan.nodeTarget)
            $selected.Add([pscustomobject]@{
                baseUrl = [string]$plan.baseUrl
                folder = $folder
                theme = $theme
                owner = [string]$plan.owner
                nodeTarget = [string]$plan.nodeTarget
                regionCode = [string]$plan.regionCode
                organizationCode = [string]$plan.organizationCode
                filePath = $file.FullName
                fileName = $file.Name
                fileSize = [int64]$file.Length
                extension = $file.Extension.ToLowerInvariant()
                title = $title
                region = $region
                keywords = $keywords
            })
        }
        foreach ($item in $selected) {
            $planRecordsBuffer.Add($item)
        }
        $folderSummariesBuffer.Add([pscustomobject]@{
            folder = $folder
            theme = $theme
            availableCount = $availableCount
            targetCount = $targetCount
            existingDocCount = $existingInFolder
            selectedCount = $selected.Count
            baseUrl = [string]$plan.baseUrl
        })
    }

    $planRecords = @($planRecordsBuffer | ForEach-Object { $_ })
    $folderSummaries = @($folderSummariesBuffer | ForEach-Object { $_ })
    $planOutput = [pscustomobject]@{
        generatedAt = (Get-Date).ToString('o')
        sourceRoot = $SourceRoot
        targetPerFolder = $TargetPerFolder
        executeImport = [bool]$ExecuteImport
        folderSummaries = $folderSummaries
        totalSelected = $planRecords.Count
        records = $planRecords
    }
    Write-JsonFile -Path $planPath -Value $planOutput
    Write-Host "Plan file:" $planPath
    Write-Host "Selected records:" $planRecords.Count
    foreach ($summary in $folderSummaries) {
        Write-Host ("- {0}: available={1}, existing={2}, selected={3}, target={4}" -f $summary.folder, $summary.availableCount, $summary.existingDocCount, $summary.selectedCount, $summary.targetCount)
    }
}
else {
    $planPath = $PlanFile
    $planData = Read-JsonFile -Path $PlanFile
    $planRecords = @($planData.records)
    $folderSummaries = @($planData.folderSummaries)
    Write-Host "Loaded plan file:" $planPath
    Write-Host "Loaded records:" $planRecords.Count
}

if (-not [string]::IsNullOrWhiteSpace($OnlyBaseUrl)) {
    $planRecords = @($planRecords | Where-Object { $_.baseUrl -eq $OnlyBaseUrl })
}

if ($WorkerCount -gt 1 -and $WorkerIndex -ge 0) {
    $filtered = New-Object 'System.Collections.Generic.List[object]'
    for ($i = 0; $i -lt $planRecords.Count; $i++) {
        if (($i % $WorkerCount) -eq $WorkerIndex) {
            $filtered.Add($planRecords[$i])
        }
    }
    $planRecords = @($filtered | ForEach-Object { $_ })
}

Write-Host "Effective records for this run:" $planRecords.Count

if (-not $ExecuteImport) {
    Write-Host "Plan generated only. Use -ExecuteImport to run actual import."
    return
}

$results = New-Object 'System.Collections.Generic.List[object]'
$total = $planRecords.Count
$current = 0
foreach ($record in $planRecords) {
    $current++
    Write-Host ("[{0}/{1}] Importing {2}" -f $current, $total, $record.fileName)
    $identifier = ''
    $uploadId = ''
    $registerOk = $false
    $uploadOk = $false
    $completeOk = $false
    $reason = ''
    try {
        $registrationBody = @{
            regionCode = $record.regionCode
            organizationCode = $record.organizationCode
            industry = $IndustryWater
            dataCategory = 'public'
            registrationType = 'resource'
            owner = $record.owner
            description = ("{0} | {1} | imported flood-control document" -f $record.theme, $record.title)
            applicationScenario = $ScenarioText
            name = $record.title
            entityType = 'document'
            additionalAttributes = @{
                column = $record.theme
                region = $record.region
                nodeTarget = $record.nodeTarget
                sourceFolder = $record.folder
                sourceFilename = $record.fileName
                sourceRelativePath = ($record.folder + '/' + $record.fileName)
                sourceSite = $SourceSiteText
                keywords = $record.keywords
                importBatch = 'flood-docs-20260320-200'
            }
        }
        $registerResp = Invoke-JsonApi -Method POST -Uri ($record.baseUrl + '/api/v2/identifiers/register') -Body $registrationBody
        if (-not $registerResp.ok) {
            throw ("register failed: " + ($registerResp | ConvertTo-Json -Depth 20))
        }
        $identifier = [string]$registerResp.data
        $registerOk = -not [string]::IsNullOrWhiteSpace($identifier)
        if (-not $registerOk) {
            throw 'register returned empty identifier'
        }

        $sha256 = (Get-FileHash -Algorithm SHA256 -Path $record.filePath).Hash.ToLowerInvariant()
        $profile = Get-ProfileByExtension -Extension $record.extension
        $createBody = @{
            profile = $profile
            filename = $record.fileName
            size = [int64]$record.fileSize
            sha256 = $sha256
        }
        $createResp = Invoke-JsonApi -Method POST -Uri ($record.baseUrl + '/api/v2/identifiers/' + $identifier + '/upload-sessions') -Body $createBody
        if (-not $createResp.ok) {
            throw ("create upload session failed: " + ($createResp | ConvertTo-Json -Depth 20))
        }
        $uploadId = [string]$createResp.data.uploadId
        $headers = @{}
        foreach ($prop in $createResp.data.put.headers.PSObject.Properties) {
            $headers[$prop.Name] = [string]$prop.Value
        }
        $putResult = Invoke-CurlUpload -FilePath $record.filePath -PutUrl ([string]$createResp.data.put.url) -Headers $headers
        if ($putResult.httpCode -notin @('200', '201')) {
            throw ("upload failed httpCode=" + $putResult.httpCode + " body=" + $putResult.body)
        }
        $uploadOk = $true
        Start-Sleep -Milliseconds 300
        $completeResp = Invoke-JsonApi -Method POST -Uri ($record.baseUrl + '/api/v2/upload-sessions/' + $uploadId + '/complete') -Body $null
        if (-not $completeResp.ok) {
            throw ("complete upload failed: " + ($completeResp | ConvertTo-Json -Depth 20))
        }
        $completeOk = $true
    }
    catch {
        $reason = Get-HttpErrorText $_
    }
    $results.Add([pscustomobject]@{
        fileName = $record.fileName
        folder = $record.folder
        baseUrl = $record.baseUrl
        identifier = $identifier
        uploadId = $uploadId
        registerOk = $registerOk
        uploadOk = $uploadOk
        completeOk = $completeOk
        reason = $reason
    })
}

$summary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString('o')
    planPath = $planPath
    total = $results.Count
    registerSuccess = @($results | Where-Object { $_.registerOk }).Count
    uploadSuccess = @($results | Where-Object { $_.uploadOk }).Count
    completeSuccess = @($results | Where-Object { $_.completeOk }).Count
    failed = @($results | Where-Object { -not $_.completeOk }).Count
    results = $results
}
Write-JsonFile -Path $resultPath -Value $summary
Write-Host "Import result file:" $resultPath
Write-Host "Register success:" $summary.registerSuccess
Write-Host "Upload success:" $summary.uploadSuccess
Write-Host "Complete success:" $summary.completeSuccess
Write-Host "Failed:" $summary.failed

if (-not $SkipIndexRebuild) {
    foreach ($baseUrl in @($MinistryBaseUrl, $SystemBaseUrl)) {
        try {
            $rebuildResp = Invoke-JsonApi -Method POST -Uri ($baseUrl + '/api/v2/ops/search/index/rebuild') -Body @{}
            Write-Host ("Rebuild index on {0}: {1}" -f $baseUrl, ($rebuildResp | ConvertTo-Json -Depth 10))
        }
        catch {
            Write-Warning ("Index rebuild failed on {0}: {1}" -f $baseUrl, (Get-HttpErrorText $_))
        }
    }
}
