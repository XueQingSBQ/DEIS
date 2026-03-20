$ErrorActionPreference = "Stop"

$ports = 8300, 8301, 8302, 8303

foreach ($port in $ports) {
    $url = "http://127.0.0.1:$port/api/v2/ops/search/index/rebuild"
    try {
        $response = Invoke-RestMethod -Uri $url -Method Post -TimeoutSec 60
        $payload = $response.data
        Write-Host ("[{0}] engine={1}, index={2}, processed={3}, indexed={4}, deleted={5}, failed={6}, skipped={7}" -f `
            $port,
            $payload.engine,
            $payload.indexName,
            $payload.processed,
            $payload.indexed,
            $payload.deleted,
            $payload.failed,
            $payload.skipped)
    } catch {
        Write-Host "[$port] rebuild failed: $($_.Exception.Message)"
    }
}
