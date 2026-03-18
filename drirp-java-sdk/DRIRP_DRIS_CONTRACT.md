# DRIRP x DRIS Contract (v0.1)

This document defines the fixed integration contract between:
- `drirp-java-sdk` (node-to-node identifier resolution protocol SDK)
- `dris-java-sdk-next` or backend service layer (registry/repository/business kernel)

Goal: keep protocol and core rules stable, while allowing custom storage and custom business implementation.

## 1. Operation Mapping

- `RESOLVE` -> read identifier and return resolved object/value metadata.
- `QUERY` -> read by filter/condition and return match list or aggregate result.
- `REGISTER` -> create identifier/object and initial metadata/value set.
- `UPDATE` -> mutate existing identifier/object metadata/value set.
- `DELETE` -> remove or mark deleted (implementation policy decides physical or logical delete).
- `ROUTER_QUERY` -> resolve routing/delegation path across DRIRP nodes.

## 2. Message Envelope (Required Fields)

Every request/response must carry:
- `code` (operation code)
- `rid` (request id, unique per request)
- `version` (protocol version, default `drirp-1.0`)
- `traceId` (cross-service trace id)
- `ts` (epoch millis)
- `nonce` (anti-replay random value)

Write operations (`REGISTER/UPDATE/DELETE`) should carry:
- `idempotencyKey` (required for safe retry and dedup)

Response uniform result fields:
- `ok` (boolean)
- `errorCode` (string/int; empty when success)
- `errorMessage` (string; empty when success)
- `details` (optional map/json for diagnostics)

## 3. Error Contract

Protocol/business layers should map to unified codes (example groups):
- `400xx` request validation errors
- `401xx` auth/authz errors
- `404xx` identifier/object not found
- `409xx` conflict/idempotency duplicate/version conflict
- `429xx` rate-limit or overload
- `500xx` internal/server errors
- `503xx` dependency unavailable/circuit open

DRIRP transport errors should be translated into this envelope and never leak raw stack traces to clients.

## 4. Retry and Idempotency Rules

- Read ops (`RESOLVE/QUERY/ROUTER_QUERY`) can retry by policy.
- Write ops can retry only when `idempotencyKey` exists.
- Fast-fail errors (`rate limit exceeded`, `circuit breaker open`) are not retried.
- Server side should keep idempotency cache/store at least for a configurable TTL window.

## 5. Version Compatibility

- Backward-compatible changes: add optional fields only.
- Breaking changes: bump `version` and support dual-version window in gateway.
- Unknown fields must be ignored (do not fail parsing).

## 6. Extension Boundaries

Fixed core (must not change):
- operation semantics
- envelope fields and error mapping shape
- idempotency + retry safety rules
- trace/rid correlation behavior

Customizable parts (can be replaced by adopters):
- storage engine and schema
- transaction strategy
- cache implementation
- concrete business handlers behind same contract

