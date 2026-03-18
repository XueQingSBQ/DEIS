# DRIS DeepResearch Orchestrator

A bounded domain agent for the DRIS system.

## Role

This service sits between the DeepResearch frontend and the MCP server:

`DeepResearch Frontend -> Orchestrator -> MCP Server -> DRIS Backend`

It does four things:

1. Normalize the user's request.
2. Pick a bounded research template.
3. Execute MCP tools in a controlled order.
4. Build a structured report with evidence cards and markdown export content.

## Planner modes

- `heuristic`: no LLM, fixed domain rules only
- `llm`: force LLM planning, fail if LLM is not configured or unavailable
- `auto`: try LLM first, fall back to heuristic

## LLM integration

The orchestrator now supports an OpenAI-compatible chat completion endpoint.

Required environment variables for LLM mode:

- `LLM_BASE_URL`
- `LLM_API_KEY`
- `LLM_MODEL`

When LLM planning succeeds, the response uses:

- `plannerMode: "llm"`

and the report can optionally be enhanced with an LLM executive summary.

## Current bounded templates

- `discovery_overview`
- `identifier_compare`
- `audit_trace`
- `anchor_search`

## Endpoints

- `GET /health`
- `POST /api/research/run`

## Example request

```json
{
  "question": "请比较 verify-owner 相关对象的元数据、对象状态和最近操作历史",
  "mcpUrl": "http://127.0.0.1:38082/mcp",
  "limit": 3,
  "strategy": "RECURSIVE",
  "searchMode": "KEYWORD",
  "auditPageSize": 3
}
```

## Run

```powershell
cd E:\irp\doip-sdk\dris-deepresearch-orchestrator
npm install
npm run dev
```

## Environment

See `.env.example`.
