# DRIS Java SDK Next

`dris-java-sdk-next` is the thin kernel SDK module for DRIS.

It keeps stable core capabilities:

- identifier register, parse, validate, and delete
- legacy-compatible identifier rule implementation inside the SDK kernel
- fixed `DataCategory` and `RegistrationType` classification rules
- 30-character identifier composition and check digit validation
- metadata get, batch get, update, and patch
- resource bind, get, batch get, and remove
- optimistic revision control for write requests
- stable kernel exceptions and error codes
- unified SDK facade through `DrisSdk` and `DrisSdkFactory`
- repository and infrastructure abstractions for backend integration

It intentionally keeps these concerns out of the SDK kernel:

- HTTP controllers and external transport
- upload session orchestration and object storage flows
- audit, permission, cross-node routing, search, vector index
- JDBC-specific runtime wiring
- DRIRP protocol logic

## Package Layout

- `api`: application service interfaces and unified facade
- `application`: default application service implementations
- `rule`: public identifier rule utilities and legacy-compatible rule logic
- `domain`: core domain objects and value objects
- `enumtype`: fixed core enums such as classification and status
- `request`: write-side request objects
- `result`: batch result models
- `exception`: kernel exceptions and error codes
- `repository`: persistence abstractions
- `infrastructure`: transaction, time, and identifier credential abstractions
- `adapter.memory`: lightweight in-memory adapter for tests and demos

## Core Rule Notes

The identifier core rule is aligned to the legacy DRIS format:

- `4 region + 4 org + 2 industry + 1 dataCategory + 1 registrationType + 8 date + 10 credential`
- total length: `30`
- credential: `9 random chars + 1 check digit`
- fixed data category codes: `1/2/3/9`
- fixed registration type codes: `1/2/3`

See `com.dris.sdk.next.rule.IdentifierRules` for composition, parsing, validation, and check digit logic.

## Design Doc

See `docs/sdk-kernel-design.md`.

## Run Tests

```bash
mvn test
```
