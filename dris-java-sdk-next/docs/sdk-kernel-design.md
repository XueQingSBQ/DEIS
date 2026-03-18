# DRIS Java SDK Next Design

## 1. Goal

This module is a thin SDK kernel, not a full backend.
It keeps stable identifier-domain rules, request contracts, core models, batch result models, and replaceable service abstractions.

## 2. Scope

Included:
- identifier registration, parsing, validation, deletion
- legacy-compatible identifier generation and checksum rules
- fixed classification enums for `DataCategory` and `RegistrationType`
- metadata read, update, patch
- resource binding and query
- batch register and batch query
- optimistic revision conflict control
- unified facade for third-party access

Excluded:
- HTTP transport
- JDBC implementation
- object storage orchestration
- permission, audit, cross-node routing
- DRIRP protocol runtime

## 3. Package Design

### api
- `IdentifierApplicationService`
- `MetadataApplicationService`
- `ResourceApplicationService`
- `DrisSdk`
- `DrisSdkFactory`

### application
- default service implementations for identifier, metadata, and resource aggregate operations
- transaction boundary orchestration only
- no HTTP or storage-specific workflow logic

### rule
- `IdentifierRules`
- public kernel rule entry for parsing, composing, validating, and check digit calculation
- keeps the old DRIS identifier specification inside the SDK kernel

### domain
- `DigitalObject`
- `IdentifierMetadata`
- `IdentifierCode`
- `IdentifierParts` (deprecated compatibility shape)
- `Resource`
- `ResourceVersion`

### enumtype
- `DataCategory`
- `RegistrationType`
- `ComponentType`
- `MetadataStatus`
- `ObjectStatus`

### request
- write requests for register, update, patch, delete, bind, remove
- requests carry only operation input, not persistence logic

### result
- `BatchResult<T>`
- `BatchItemResult<T>`
- `BatchItemStatus`
- batch methods do not silently drop failures

### exception
- `DrisKernelErrorCode`
- `DrisKernelException`
- `ValidationException`
- `NotFoundException`
- `ConflictException`

### repository
- `DigitalObjectRepository`
- `RegistrationDictionary`

### infrastructure
- `TransactionRunner`
- `TimeProvider`
- `IdentifierCredentialGenerator`

## 4. Identifier Core Rule

The current SDK keeps the legacy identifier format as a first-class kernel rule:

- `regionCode`: 4 digits
- `organizationCode`: 4 uppercase letters excluding `I` and `O`
- `industryCode`: 2 digits
- `dataCategoryCode`: fixed by `DataCategory`
- `registrationTypeCode`: fixed by `RegistrationType`
- `date`: `yyyyMMdd`
- `credential`: 9 seed chars plus 1 check digit

Full identifier format:
- `4 + 4 + 2 + 1 + 1 + 8 + 10 = 30`

Validation includes:
- regex shape validation
- date validation
- check digit validation
- optional dictionary-backed region, organization, and industry validation

## 5. Why Identifier Is Not Updated

`identifier` is the aggregate primary key.
If the SDK allows changing it directly, it breaks repository identity, external references, and revision assumptions.
Therefore the SDK only updates metadata and resource under the identifier.

## 6. Why BatchResult Is Added

Earlier batch methods returned simple collections, which lost failure reasons.
`BatchResult<T>` keeps:
- total count
- success count
- failure count
- per-item status
- per-item error code and message

This is more suitable for SDK callers and for future multi-node orchestration.

## 7. Why DrisSdk Facade Is Added

A third-party caller should not need to manually assemble three service references every time.
`DrisSdk` gives one unified entry while preserving separated services internally.
This avoids both extremes:
- too many flat public methods
- too many independent entry objects with no shared root

## 8. Current Status

This version is a usable kernel baseline for backend migration.
What remains outside the SDK on purpose:
- upload session and object storage verification
- search and distributed search
- audit and permission control
- DRIRP routing and protocol handling
- database-specific adapters
