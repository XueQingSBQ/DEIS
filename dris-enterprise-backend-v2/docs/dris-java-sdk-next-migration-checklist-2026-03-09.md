# dris-enterprise-backend-v2 -> dris-java-sdk-next Migration Checklist

Date: 2026-03-09
Status: Draft
Priority rule: P0 must be completed before switching backend runtime to dris-java-sdk-next.

## 1. Purpose

This file records the concrete migration checklist for moving `dris-enterprise-backend-v2`
from the legacy `dris-java-sdk` runtime integration to the new thin-kernel `dris-java-sdk-next`.

Primary goals:
- keep SDK thin
- move non-kernel business back to backend
- reduce backend coupling to SDK internals
- preserve current REST behavior and DRIRP distributed behavior
- avoid forgetting mandatory model corrections and migration steps

## 2. Current State Summary

Current backend integration is still tightly coupled to the old SDK in three layers:
- runtime assembly: `DrisCoreService`, `InMemoryDrisCoreService`, `DrisClients.inProcess(...)`
- service calls: `DrisClient` and `DrisResponse`
- API model exposure: controllers directly expose old SDK domain/request/response models

Representative files:
- `src/main/java/com/dris/v2/backend/config/BackendV2Configuration.java`
- `src/main/java/com/dris/v2/backend/service/IdentifierCoreService.java`
- `src/main/java/com/dris/v2/backend/service/IdentifierResourceService.java`
- `src/main/java/com/dris/v2/backend/service/IdentifierUploadService.java`
- `src/main/java/com/dris/v2/backend/service/DrirpResolutionService.java`
- `src/main/java/com/dris/v2/backend/service/DrirpOperationService.java`
- `src/main/java/com/dris/v2/backend/service/search/DistributedSearchSupport.java`

## 3. Why the New SDK Models Changed

The first version of `dris-java-sdk-next` was intentionally trimmed into a thin kernel.
The design target was:
- keep only identifier / metadata / resource core rules
- remove upload orchestration, search, DRIRP, vector, audit, permission, JDBC runtime wiring
- avoid recreating a backend inside the SDK

During that trimming, some fields were removed because they looked like transport-facing or storage-facing details.
After checking the real backend code, the conclusion is:
- some removals were correct and should stay in backend
- some removals were too aggressive and must be restored in the SDK core

So the migration must include targeted SDK model correction, not only backend adaptation.

## 4. P0 Mandatory SDK Corrections

These items are mandatory. They are not optional discussion items anymore.

### P0.1 Restore `componentType` in `dris-java-sdk-next` `Resource`

Reason:
- backend tool execution uses it to assert `tool`
- backend resource/service/tool semantics depend on it
- search filtering and result projection use it
- distributed resolution response validation uses it
- resource version query view also exposes it

If `componentType` stays absent from the new SDK resource model, backend migration will either:
- break existing semantics, or
- force awkward duplication outside the SDK core

Decision:
- `componentType` must be restored into `com.dris.sdk.next.domain.Resource`
- related request / bind flow must preserve it
- validation rules should allow `resource`, `tool`, `service`, and compatible custom values if needed

### P0.2 Keep resource transport/storage extension fields outside the strict kernel core, but preserve compatibility

Fields seen in current backend / tests:
- `etag`
- `versionId` or provider-side version id
- `createdAt`

Decision:
- these do not need to become strict domain invariants
- backend can keep them in backend DTO / persistence mapping / `extra`
- if migration cost becomes too high, a compatibility extension object may be added later

Recommended first step:
- keep them in backend API view and mapper layer
- do not re-bloat the kernel immediately unless a real kernel use case appears

### P0.3 Do not duplicate object status / timestamps in two authoritative places

Current old metadata contains:
- `status`
- `createTime`
- `updateTime`

New SDK currently has:
- `DigitalObject.status`
- `DigitalObject.createdAt`
- `DigitalObject.updatedAt`

Decision:
- keep status and timestamps as aggregate-level truth on `DigitalObject`
- backend API mapper can project them to existing REST fields if needed
- do not create two independent sources of truth inside the kernel

### P0.4 Preserve local-node identifier validation semantics in backend

The old runtime `validateIdentifier(...)` does more than format parsing.
It also enforces local node region / organization semantics.

The new SDK only provides parse / format validation style capability.

Decision:
- backend must add a local validator based on `BackendV2Properties.regionCode` and `organizationCode`
- do not move node-local policy into the generic SDK kernel

### P0.5 Batch behavior must not silently lose failures

The new SDK uses `BatchResult`.
This is correct and must be kept.

Decision:
- backend internal migration should consume `BatchResult`
- REST layer can keep legacy list response temporarily only if it explicitly defines how failures are handled
- long-term direction should be backend DTOs with per-item status

### P0.6 Model Migration Baseline

Model stability is more important than over-trimming.
For the new SDK, do not casually redesign old core models if the field is part of stable business semantics.

Mandatory migration baseline:
- `DigitalObject`: keep as a core aggregate model
- `IdentifierMetadata`: keep the old public field shape as much as possible
- `Resource`: keep the old public field shape as much as possible
- `IdentifierCode`: should be restored as a public identifier value object, or `IdentifierParts` should at least provide equivalent public semantics
- `ResourceVersion`: should be restored as a domain model if resource version is part of the system-level sharing semantics
- `ObjectStatus`: keep and remain compatible with old semantics
- `ComponentType`: restore as a first-class enum or strongly-typed value set

Do not migrate into the new thin kernel SDK:
- `SearchRequest`
- `SearchResult`
- `UploadSession`
- `CreateUploadSessionRequest`
- `DownloadPresignRequest`
- `ExecuteRequest`
- `DrisResponse`
- `UploadCreateSessionResult`
- `DownloadPresignResult`
- `VectorSyncResult`
- `SearchMode`
- `UploadStatus`

Conditional / not recommended as kernel enums:
- `DataCategory`
- `RegistrationType`
- `MetadataStatus`

Reason:
- `dataCategory` and `registrationType` are closer to dictionary / master-data semantics than closed kernel enums
- `metadataStatus` duplicates aggregate status semantics and is not necessary as a separate kernel enum

## 5. P1 Backend Runtime Refactor

### P1.1 Introduce a backend-owned facade

Create a backend-owned facade interface, for example:
- `LocalDrisKernel`

Purpose:
- backend services depend on this interface, not directly on `DrisSdk`
- isolate SDK differences inside one adapter layer
- make future SDK replacement or extension localized

Recommended methods:
- register identifier
- register batch
- parse / validate identifier
- exists
- get metadata / batch metadata
- get digital object / batch digital object
- update metadata
- patch metadata
- delete identifier
- get resource / batch resource
- bind resource
- remove resource

### P1.2 Add `DrisNextLocalDrisKernel` adapter

Implementation direction:
- inject `DrisSdk`
- call `identifiers()`, `metadata()`, `resources()` internally
- translate SDK exceptions into backend `BusinessException` or backend result objects
- translate `BatchResult` into backend DTOs

### P1.3 Replace old runtime assembly in Spring configuration

Current configuration directly creates old SDK runtime objects.
This must be removed during migration.

Current old runtime pieces:
- `DrisCoreService`
- `InMemoryDrisCoreService`
- `DrisClients.inProcess(...)`

New required assembly inputs for `DrisSdkFactory.compose(...)`:
- `DigitalObjectRepository`
- `RegistrationDictionary`
- `TransactionRunner`
- `TimeProvider`
- `IdentifierCredentialGenerator`

### P1.4 Implement backend adapters for new SDK infrastructure contracts

Add backend-side implementations for:
- `DigitalObjectRepository`
- `RegistrationDictionary`
- `TransactionRunner`
- `TimeProvider`
- `IdentifierCredentialGenerator`

Important note:
- this does not mean putting business workflows back into SDK
- this only supplies persistence and infrastructure contracts required by the thin kernel

## 6. P1 Backend Service Migration

### P1.5 Migrate identifier core service first

Target file:
- `src/main/java/com/dris/v2/backend/service/IdentifierCoreService.java`

Replace old `DrisClient` calls with backend facade calls.

Mapping:
- `generateIdentifier` -> `identifiers().register(...)`
- `generateIdentifierBatch` -> `identifiers().registerBatch(...)`
- `existsIdentifier` -> `identifiers().exists(...)`
- `resolveIdentifier` -> `metadata().get(...)`
- `resolveIdentifierBatch` -> `metadata().getBatch(...)`
- `getDigitalObject` -> `identifiers().getDigitalObject(...)`
- `updateIdentifier` -> `metadata().update(...)`
- `partialUpdateIdentifier` -> `metadata().patch(...)`
- `deleteIdentifier` -> `identifiers().delete(...)`

### P1.6 Migrate identifier resource service second

Target file:
- `src/main/java/com/dris/v2/backend/service/IdentifierResourceService.java`

Mapping:
- `getComponent` -> `resources().get(...)`
- `upsertComponent` -> `resources().bind(...)`
- `removeComponent` -> `resources().remove(...)`

Important compatibility rule:
- keep existing REST naming if needed for now
- internal implementation can move to `resource` terminology
- do not break external API behavior during first migration stage

### P1.7 Migrate DRIRP local execution paths to the backend facade

Targets:
- `src/main/java/com/dris/v2/backend/service/DrirpResolutionService.java`
- `src/main/java/com/dris/v2/backend/service/resolve/ResolutionSupport.java`
- `src/main/java/com/dris/v2/backend/service/DrirpOperationService.java`
- `src/main/java/com/dris/v2/backend/service/operation/OperationSupport.java`
- `src/main/java/com/dris/v2/backend/service/search/DistributedSearchSupport.java`

Rule:
- DRIRP protocol logic stays in backend / drirp-java-sdk integration
- only local data access branch switches from old `DrisClient` to backend facade

## 7. P1 Keep These Capabilities in Backend

The following must remain backend-owned and must not be moved back into `dris-java-sdk-next`:
- upload session creation
- upload completion and object storage verification
- download presign generation
- search and distributed search
- vector sync and vector index maintenance
- audit
- permission control
- DRIRP routing / forwarding / security / anti-replay
- system metrics and ops endpoints

Representative current files:
- `src/main/java/com/dris/v2/backend/service/IdentifierUploadService.java`
- `src/main/java/com/dris/v2/backend/service/FederatedSearchService.java`
- `src/main/java/com/dris/v2/backend/service/SystemOpsService.java`
- `src/main/java/com/dris/v2/backend/config/IsoUploadSessionRepository.java`

## 8. P1 API Layer Decoupling

### P1.8 Stop exposing SDK request/response classes directly in controllers

Current controllers still directly expose old SDK models.
This should be reduced in the migration.

Targets:
- `IdentifierCoreController`
- `IdentifierResourceController`
- `IdentifierUploadController`
- `SearchController`

Recommended direction:
- add backend-owned request DTOs and response DTOs
- use mapper layer between REST DTO and SDK kernel domain model
- this protects the backend API contract from future SDK model changes

### P1.9 Remove backend dependence on legacy `DrisResponse`

Current backend still relies heavily on old `DrisResponse`.
Long-term this must be removed.

Recommended direction:
- service layer returns domain objects / backend result objects
- errors are represented by exceptions or backend result wrappers
- controller layer converts to `ApiResponse`

## 9. P2 Compatibility and Cleanup

### P2.1 Keep old and new SDK dependencies in parallel only during migration

Temporary state is acceptable:
- keep old `dris-java-sdk`
- add new `dris-java-sdk-next`
- migrate service by service

Exit rule:
- once all runtime and model usage is moved off old SDK, remove old dependency completely

### P2.2 Clean test fixtures

Test fixtures currently rely on legacy model fields such as:
- metadata `status`
- metadata `createTime`
- metadata `updateTime`
- resource `componentType`
- resource `etag`
- resource `versionId`
- resource `createdAt`

Migration tasks:
- move aggregate timestamps/status assertions to backend DTO or mapper tests
- keep `componentType` after SDK correction
- move resource extension fields into backend DTO / extra mapping tests

## 10. Suggested Migration Order

1. fix mandatory SDK model issue: restore `componentType`
2. add backend local facade interface and implementation
3. add backend adapter implementations for new SDK repository/infrastructure contracts
4. wire `DrisSdk` in Spring configuration
5. migrate `IdentifierCoreService`
6. migrate `IdentifierResourceService`
7. migrate DRIRP local branches
8. migrate controllers away from old SDK models
9. remove legacy `DrisResponse` dependence
10. remove old `dris-java-sdk` dependency

## 11. What Must Be Changed Next in dris-java-sdk-next

Required next code changes in the new SDK:
- restore `componentType` to `Resource`
- make bind/get/copy logic carry `componentType`
- review resource validation so existing `tool/service/resource` semantics still work

Not required immediately in the new SDK:
- upload session models
- download presign models
- search request/result models
- vector sync models
- DRIRP protocol models
- audit or permission models

## 12. Final Migration Principle

The backend should own business flow.
The new SDK should own only reusable kernel rules and aggregate operations.

If a capability needs backend database joins, object storage orchestration, DRIRP routing,
security policy, audit, or cross-node behavior, it belongs in backend.

If a capability is a stable aggregate rule for identifier / metadata / resource,
it belongs in `dris-java-sdk-next`.
## 13. 2026-03-09 Core Rule Alignment Update

The new SDK baseline has been pulled back toward the legacy DRIS core rule set.
These points should now be treated as confirmed migration assumptions:

- `DigitalObject`, `IdentifierMetadata`, and `Resource` remain close to the old SDK core shapes
- `DataCategory` is a fixed core enum and maps to `1/2/3/9`
- `RegistrationType` is a fixed core enum and maps to `1/2/3`
- `IdentifierCode` is restored as a public value object
- `ResourceVersion` exists in the new SDK domain model
- identifier generation, parsing, and validation rules are implemented in `com.dris.sdk.next.rule.IdentifierRules`
- identifier format remains `4 + 4 + 2 + 1 + 1 + 8 + 10 = 30`
- credential remains `9 seed chars + 1 check digit`

Implication for backend migration:
- backend should reuse SDK core rule types and rule utilities instead of re-encoding classification or checksum logic again
- backend should still own node-local policy, persistence mapping, upload flow, search, DRIRP, audit, and permission
