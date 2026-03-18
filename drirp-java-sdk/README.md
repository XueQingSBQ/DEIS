# DRIRP Java SDK

`drirp-java-sdk` is a lightweight Java SDK for identifier-oriented message protocol calls with:

- unified message model (`DrirpMessage`)
- transport channels (`http/https`, `grpc/grpcs`)
- client retry/interceptor/rid support
- optional HMAC request/response auth
- pluggable server dispatcher and filters

## Build

```bash
mvn -f drirp-java-sdk/pom.xml test
mvn -f drirp-java-sdk/pom.xml package
```

## Proto

Protocol definition is in:

- `drirp-java-sdk/drirp.proto`

Java gRPC classes are generated in build by `protobuf-maven-plugin`.

## Core Concepts

- `DrirpMessage`: protocol envelope with transport-agnostic fields
  - `code`, `rid`, `msg`, `body`, `ts`, `auth`
  - `ok`, `errorCode`, `errorMessage`, `details`
  - `version`, `traceId`, `nonce`, `idempotencyKey`
- `DrirpOpcode`: operation code constants
- `DrirpClient`: high-level API (`resolve/query/register/update/delete`)
- `DrirpDispatcher`: server-side opcode routing + filter chain

## Security and Retry

- Optional HMAC signer/verifier: `HmacSha256AuthProvider`
- Replay protection filter: `DrirpReplayProtectionFilter`
- Retry policy semantics:
  - read operations retry by default (`resolve/query/router_query`)
  - write operations retry only with `idempotencyKey`
- Dispatcher idempotency cache:
  - mutation operations (`register/update/delete`) support server-side idempotent replay by `idempotencyKey`
  - configurable TTL and max cache entries

## Basic Server Example

See:

- `drirp-java-sdk/example/DrirpServerExample.java`

## Notes

- This SDK is extracted as an independent protocol module and keeps the same core protocol/transport capabilities as the original DRIRP package.
- For production deployment, pair with your own service layer and storage implementation.
