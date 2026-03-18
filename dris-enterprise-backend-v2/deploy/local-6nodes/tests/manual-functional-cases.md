# Manual Functional Cases and Experiment Methods (6 Nodes)

This document is designed for manual testing.

- Node1 country: `http://127.0.0.1:8200`
- Node2 province: `http://127.0.0.1:8201`
- Node3 city-a: `http://127.0.0.1:8202`
- Node4 county-a: `http://127.0.0.1:8203`
- Node5 city-b: `http://127.0.0.1:8204`
- Node6 county-b: `http://127.0.0.1:8205`

## 0. Start and Baseline

```powershell
cd e:\irp\doip-sdk\dris-enterprise-backend-v2\deploy\local-6nodes
.\scripts\start-6nodes.ps1 -InitDb
.\scripts\status-6nodes.ps1
```

Expected:

- all 6 nodes are `UP`
- parent-child routes are visible from upper nodes

## 1. Helper Variables

Run once in PowerShell:

```powershell
$N1 = "http://127.0.0.1:8200"
$N2 = "http://127.0.0.1:8201"
$N3 = "http://127.0.0.1:8202"
$N4 = "http://127.0.0.1:8203"
$N5 = "http://127.0.0.1:8204"
$N6 = "http://127.0.0.1:8205"
```

## 2. Topology API Cases

### 2.1 Direct descendants and status

```powershell
Invoke-RestMethod "$N1/api/v2/node/direct-descendants"
Invoke-RestMethod "$N1/api/v2/node/direct-descendants/status"
Invoke-RestMethod "$N2/api/v2/node/direct-descendants/status"
```

Expected:

- `N1` has child `node2-province`
- `N2` has children `node3-city-a`, `node5-city-b`
- status `online=true` for started children

### 2.2 Route table

```powershell
Invoke-RestMethod "$N1/api/v2/node/routes"
Invoke-RestMethod "$N2/api/v2/node/routes"
```

Expected:

- `N1` can see routes for lower nodes
- hop count is > 1 for non-direct descendants

## 3. Identifier Core Cases

### 3.1 Register identifier on county-a node

```powershell
$registerBody = @{
  regionCode = "1100"
  organizationCode = "CNTA"
  industry = "energy"
  dataCategory = "dataset"
  registrationType = "new"
  owner = "manual-lab"
  description = "county-a sample object"
  applicationScenario = "lab"
  name = "sample-meter-a"
  entityType = "device"
  additionalAttributes = @{
    source = "sensor-a"
    batch = "manual-001"
  }
} | ConvertTo-Json -Depth 8

$registerResp = Invoke-RestMethod -Method Post -Uri "$N4/api/v2/identifiers/register" -ContentType "application/json" -Body $registerBody
$ID = $registerResp.data
$ID
```

Expected:

- `code=SUCCESS`
- `data` returns a generated identifier string

### 3.2 Validate / exists / metadata (cross-node)

```powershell
Invoke-RestMethod "$N4/api/v2/identifiers/$ID/validate"
Invoke-RestMethod "$N4/api/v2/identifiers/$ID/exists"
Invoke-RestMethod "$N4/api/v2/identifiers/$ID/metadata"
Invoke-RestMethod "$N1/api/v2/identifiers/$ID/metadata"
```

Expected:

- local and cross-node metadata resolve succeed

### 3.3 Update metadata

```powershell
$meta = (Invoke-RestMethod "$N4/api/v2/identifiers/$ID/metadata").data
$meta.description = "county-a sample object (updated)"
$meta.applicationScenario = "lab-updated"
$metaJson = $meta | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Put -Uri "$N4/api/v2/identifiers/$ID/metadata" -ContentType "application/json" -Body $metaJson

$patch = @{ description = "county-a patch update" } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri "$N4/api/v2/identifiers/$ID/metadata" -ContentType "application/json" -Body $patch
```

Expected:

- metadata update and patch both return success

### 3.4 Batch resolve (local + distributed)

```powershell
$batch = @($ID) | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$N4/api/v2/identifiers/resolve/batch" -ContentType "application/json" -Body $batch
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/identifiers/resolve/batch/distributed" -ContentType "application/json" -Body $batch
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/identifiers/resolve/batch/object/distributed" -ContentType "application/json" -Body $batch
```

Expected:

- distributed batch resolve from `N1` returns the identifier hosted in `N4`

## 4. Component/Resource Cases

### 4.1 Upsert component

```powershell
$resourceBody = @{
  componentType = "resource"
  storageType = "local"
  accessMethod = "http"
  contentType = "application/json"
  format = "json"
  locator = "local://manual/$ID.json"
  size = 128
  hashValue = ""
  etag = ""
  versionId = ""
  createdAt = ""
  extra = @{ quality = "test" }
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Put -Uri "$N4/api/v2/identifiers/$ID/component" -ContentType "application/json" -Body $resourceBody
Invoke-RestMethod "$N4/api/v2/identifiers/$ID/component"
Invoke-RestMethod "$N4/api/v2/identifiers/$ID/component/versions?page=1&size=10"
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/identifiers/resolve/batch/component/distributed" -ContentType "application/json" -Body (@($ID) | ConvertTo-Json)
```

Expected:

- component can be queried locally and from distributed resolve

## 5. Search Cases

### 5.1 Force vector sync then search

```powershell
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/ops/vector/sync"
Invoke-RestMethod -Method Post -Uri "$N2/api/v2/ops/vector/sync"
Invoke-RestMethod -Method Post -Uri "$N4/api/v2/ops/vector/sync"
```

Local search:

```powershell
$searchBody = @{
  naturalQuery = "county a sample"
  keywords = @("county", "sample")
  fields = @("description", "name")
  filters = @{}
  limit = 20
  offset = 0
  mode = "HYBRID"
  semanticWeight = 0.7
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post -Uri "$N4/api/v2/search" -ContentType "application/json" -Body $searchBody
```

Global search:

```powershell
$globalBody = @{
  naturalQuery = "county a sample"
  keywords = @("county", "sample")
  fields = @("description", "name")
  filters = @{}
  limit = 20
  offset = 0
  mode = "HYBRID"
  semanticWeight = 0.7
  strategy = "RECURSIVE"
  ttl = 6
  maxFanout = 8
  perNodeLimit = 20
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post -Uri "$N1/api/v2/search/global" -ContentType "application/json" -Body $globalBody
```

Expected:

- global search from `N1` can hit lower nodes

## 6. Upload/Download and Tool Cases

### 6.1 Upload session create (smoke)

```powershell
$uploadBody = @{
  profile = "resource"
  filename = "sample.json"
  size = 128
  sha256 = ""
  serviceEndpoint = ""
} | ConvertTo-Json

$uploadResp = Invoke-RestMethod -Method Post -Uri "$N4/api/v2/identifiers/$ID/upload-sessions" -ContentType "application/json" -Body $uploadBody
$uploadResp
```

### 6.2 Complete upload (negative sample if file not uploaded)

```powershell
$uploadId = $uploadResp.data.uploadId
Invoke-RestMethod -Method Post -Uri "$N4/api/v2/upload-sessions/$uploadId/complete"
```

Expected:

- If object not uploaded, this may fail. Record returned error code/message as a test result.

### 6.3 Download presign

```powershell
$dl = @{ identifier = $ID; expiresInSeconds = 600 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/downloads/presign" -ContentType "application/json" -Body $dl
```

### 6.4 Tool execute (smoke / may fail without tool spec)

```powershell
$tool = @{ args = @{ action = "ping" }; timeoutMs = 2000 } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/identifiers/$ID/tool/execute" -ContentType "application/json" -Body $tool
```

Expected:

- If no tool component is bound, failure is acceptable and should be logged.

## 7. Audit and Ops Cases

```powershell
Invoke-RestMethod "$N4/api/v2/audit/identifier-history?identifier=$ID&page=1&size=20"
Invoke-RestMethod "$N1/api/v2/ops/metrics"
```

## 8. Delete Case

```powershell
Invoke-RestMethod -Method Delete -Uri "$N4/api/v2/identifiers/$ID"
Invoke-RestMethod "$N4/api/v2/identifiers/$ID/exists"
```

Expected:

- delete success
- exists returns false

## 9. Robustness Experiment Methods

### 9.1 Node failure detection experiment

1. Start all 6 nodes.
2. Stop one child node process, for example `node4-county-a`.
3. Poll parent status (`N3`) every 5 seconds:

```powershell
1..20 | ForEach-Object {
  Invoke-RestMethod "$N3/api/v2/node/direct-descendants/status"
  Start-Sleep -Seconds 5
}
```

Expected:

- with `interval=15s` and `offline-failure-threshold=3`, node should become offline around `45~60s`.

### 9.2 Recovery experiment

1. Restart stopped child node with `start-6nodes.ps1` (it skips already-running nodes and starts missing one).
2. Poll parent status.

Expected:

- child returns to `online=true`.

### 9.3 Recursive vs Iterative comparison

1. Use current config (`resolve-mode=recursive`) on initiator node (`node1-country`), run `N` distributed resolves and collect avg/P95 latency.
2. Change `node1-country.yml` to `resolve-mode: iterative`, restart only node1, rerun same test.
3. Compare:
   - success rate
   - avg latency
   - P95 latency
   - timeout rate

Suggested sample size:

- warmup: 20 requests
- measured: 200 requests

## 10. Cleanup

```powershell
cd e:\irp\doip-sdk\dris-enterprise-backend-v2\deploy\local-6nodes
.\scripts\stop-6nodes.ps1
```

