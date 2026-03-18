param(
    [string]$MysqlExe = "mysql",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPassword = "sbq254172"
)

$ErrorActionPreference = "Stop"

$databases = @(
    "dris_lab_node1",
    "dris_lab_node2",
    "dris_lab_node3",
    "dris_lab_node4",
    "dris_lab_node5",
    "dris_lab_node6"
)

$sql = ($databases | ForEach-Object { "CREATE DATABASE IF NOT EXISTS $_ CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" }) -join " "

Write-Host "Creating local lab databases on ${DbHost}:$DbPort ..."
& $MysqlExe "--host=$DbHost" "--port=$DbPort" "--user=$DbUser" "--password=$DbPassword" -e $sql
if ($LASTEXITCODE -ne 0) {
    throw "mysql command failed with exit code $LASTEXITCODE"
}

Write-Host "Done. Databases:"
$databases | ForEach-Object { Write-Host " - $_" }
