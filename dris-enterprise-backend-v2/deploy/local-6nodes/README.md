# Local 6-Node Lab (Manual Test)

This folder provides a local multi-node lab for `dris-enterprise-backend-v2`:

- 6 business nodes: `COUNTRY -> PROVINCE -> CITY/COUNTY + CITY/COUNTY`
- startup/stop/status scripts
- manual API test cases and experiment methods

Chinese docs:

- `README.zh-CN.md`
- `tests/manual-functional-cases.zh-CN.md`

## Topology

1. `node1-country` (`COUNTRY`, http `8200`, drirp `19200`)
2. `node2-province` (`PROVINCE`, http `8201`, drirp `19201`)
3. `node3-city-a` (`CITY`, http `8202`, drirp `19202`)
4. `node4-county-a` (`COUNTY`, http `8203`, drirp `19203`)
5. `node5-city-b` (`CITY`, http `8204`, drirp `19204`)
6. `node6-county-b` (`COUNTY`, http `8205`, drirp `19205`)

## Quick Start

1. Ensure MySQL is running.
2. Optional DB init (creates 6 databases):
   - `./scripts/init-local-dbs.ps1`
3. Start all 6 nodes:
   - `./scripts/start-6nodes.ps1 -InitDb`
4. Check status:
   - `./scripts/status-6nodes.ps1`
5. Open real-time logs in 6 visible PowerShell windows:
   - `./scripts/open-log-windows.ps1`
5. Run manual tests:
   - `tests/manual-functional-cases.md`
6. Stop all nodes:
   - `./scripts/stop-6nodes.ps1`

## Notes

- Node config files are under `nodes/`.
- Runtime logs and pid files are under `runtime/`.
- All nodes use local storage provider for easier manual testing.
- Default DB credentials in node yml files follow current project defaults.
