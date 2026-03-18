# DRIS MCP Server

A standalone remote MCP server for `dris-enterprise-backend-v2`.

## Scope

This project does not call internal Java SDK code directly. It integrates with the DRIS backend through existing REST APIs so coupling stays low.

## Exposed MCP tools

- `resolve_metadata`
- `resolve_object`
- `resolve_component`
- `batch_resolve_metadata_distributed`
- `batch_resolve_object_distributed`
- `batch_resolve_component_distributed`
- `search_global`
- `query_audit_logs`
- `get_node_routes`

## Exposed MCP resources

- `dris://system/capabilities`
- `dris://node/topology`

## Exposed MCP prompts

- `cross_node_batch_analysis`
- `identifier_provenance_analysis`

## Environment

Copy `.env.example` to `.env` and adjust values if needed.

- `MCP_PORT`: HTTP port for the MCP server
- `MCP_ROUTE_PATH`: MCP endpoint path, default `/mcp`
- `DRIS_BASE_URL`: base URL of `dris-enterprise-backend-v2`
- `DRIS_AUTH_TOKEN`: optional fallback bearer token when caller does not send `Authorization`
- `MCP_ALLOWED_ORIGIN`: optional CORS origin, default `*`

## Development

```bash
npm install
npm run dev
```

## Build

```bash
npm run typecheck
npm run build
npm start
```

## Runtime model

- Transport: MCP Streamable HTTP
- Session mode: stateful session per MCP client
- Auth strategy: pass-through `Authorization` header from MCP HTTP request to DRIS backend
- Failure model: backend errors are returned as MCP tool errors with structured envelope payload
