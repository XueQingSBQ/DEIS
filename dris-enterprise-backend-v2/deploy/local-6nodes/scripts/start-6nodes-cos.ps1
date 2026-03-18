param(
    [switch]$BuildJar = $true,
    [switch]$InitDb = $false,
    [string]$MysqlExe = "mysql",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPassword = "sbq254172",
    [int]$ReadyTimeoutSec = 120,
    [string]$StorageProvider = "cos",
    [string]$StorageEndpoint = "https://cos.ap-beijing.myqcloud.com",
    [string]$StorageRegion = "ap-beijing",
    [string]$DefaultBucket = "dris-repo-prod-1330585503",
    [string]$StoragePublicBaseUrl = "",
    [string]$StorageSecretId = "",
    [string]$StorageSecretKey = "",
    [string]$StorageSessionToken = ""
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$startScript = Join-Path $scriptDir "start-6nodes.ps1"

$previous = @{
    DRIS_STORAGE_PROVIDER = $env:DRIS_STORAGE_PROVIDER
    DRIS_STORAGE_ENDPOINT = $env:DRIS_STORAGE_ENDPOINT
    DRIS_STORAGE_REGION = $env:DRIS_STORAGE_REGION
    DRIS_DEFAULT_BUCKET = $env:DRIS_DEFAULT_BUCKET
    DRIS_STORAGE_PUBLIC_BASE_URL = $env:DRIS_STORAGE_PUBLIC_BASE_URL
    DRIS_STORAGE_SECRET_ID = $env:DRIS_STORAGE_SECRET_ID
    DRIS_STORAGE_SECRET_KEY = $env:DRIS_STORAGE_SECRET_KEY
    DRIS_STORAGE_SESSION_TOKEN = $env:DRIS_STORAGE_SESSION_TOKEN
}

$startParams = @{
    BuildJar = $BuildJar
    InitDb = $InitDb
    MysqlExe = $MysqlExe
    DbHost = $DbHost
    DbPort = $DbPort
    DbUser = $DbUser
    DbPassword = $DbPassword
    ReadyTimeoutSec = $ReadyTimeoutSec
}

try {
    $env:DRIS_STORAGE_PROVIDER = $StorageProvider
    $env:DRIS_STORAGE_ENDPOINT = $StorageEndpoint
    $env:DRIS_STORAGE_REGION = $StorageRegion
    $env:DRIS_DEFAULT_BUCKET = $DefaultBucket
    $env:DRIS_STORAGE_PUBLIC_BASE_URL = $StoragePublicBaseUrl
    $env:DRIS_STORAGE_SECRET_ID = $StorageSecretId
    $env:DRIS_STORAGE_SECRET_KEY = $StorageSecretKey
    $env:DRIS_STORAGE_SESSION_TOKEN = $StorageSessionToken

    Write-Host "Using COS storage settings for local-6nodes:"
    Write-Host "  provider:        $StorageProvider"
    Write-Host "  endpoint:        $StorageEndpoint"
    Write-Host "  region:          $StorageRegion"
    Write-Host "  default bucket:  $DefaultBucket"
    Write-Host "  public base url: $StoragePublicBaseUrl"
    Write-Host "  secret id set:   $([string]::IsNullOrWhiteSpace($StorageSecretId) -eq $false)"
    Write-Host "  secret key set:  $([string]::IsNullOrWhiteSpace($StorageSecretKey) -eq $false)"
    Write-Host "  session token:   $([string]::IsNullOrWhiteSpace($StorageSessionToken) -eq $false)"
    Write-Host ""

    & $startScript @startParams
} finally {
    $env:DRIS_STORAGE_PROVIDER = $previous.DRIS_STORAGE_PROVIDER
    $env:DRIS_STORAGE_ENDPOINT = $previous.DRIS_STORAGE_ENDPOINT
    $env:DRIS_STORAGE_REGION = $previous.DRIS_STORAGE_REGION
    $env:DRIS_DEFAULT_BUCKET = $previous.DRIS_DEFAULT_BUCKET
    $env:DRIS_STORAGE_PUBLIC_BASE_URL = $previous.DRIS_STORAGE_PUBLIC_BASE_URL
    $env:DRIS_STORAGE_SECRET_ID = $previous.DRIS_STORAGE_SECRET_ID
    $env:DRIS_STORAGE_SECRET_KEY = $previous.DRIS_STORAGE_SECRET_KEY
    $env:DRIS_STORAGE_SESSION_TOKEN = $previous.DRIS_STORAGE_SESSION_TOKEN
}
