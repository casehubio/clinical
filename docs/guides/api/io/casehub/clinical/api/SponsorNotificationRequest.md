# io.casehub.clinical.api.SponsorNotificationRequest

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

All context the SponsorNotifier SPI needs to deliver and record a sponsor notification.

<p>`piId` and `piDisplayName` are null when `terminalStatus` is EXPIRED
(system-initiated expiration — no PI actor). Consumers must check before using either field.

<p>`piDisplayName` is the formal name resolved by `PiIdentityResolver` for use
in regulated notification bodies. Falls back to `piId` when no resolver override is
configured (default passthrough).

## Fields

### `deviationId` (`java.util.UUID`)

### `deviationType` (`java.lang.String`)

### `piDisplayName` (`java.lang.String`)

### `piId` (`java.lang.String`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `sponsorNotificationConnectorId` (`java.lang.String`)

### `sponsorNotificationDestination` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)

### `trialId` (`java.util.UUID`)

## Record Components

### `deviationId` (`java.util.UUID`)

### `deviationType` (`java.lang.String`)

### `piDisplayName` (`java.lang.String`)

### `piId` (`java.lang.String`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `sponsorNotificationConnectorId` (`java.lang.String`)

### `sponsorNotificationDestination` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public SponsorNotificationRequest(java.util.UUID trialId, java.util.UUID siteId, java.util.UUID deviationId, java.lang.String deviationType, io.casehub.clinical.api.model.DeviationSeverity severity, io.casehub.clinical.api.model.PiApprovalStatus terminalStatus, java.lang.String piId, java.lang.String piDisplayName, java.lang.String sponsorNotificationConnectorId, java.lang.String sponsorNotificationDestination, java.lang.String tenantId)`

#### Parameters

- `trialId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `deviationId` (`java.util.UUID`)
- `deviationType` (`java.lang.String`)
- `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)
- `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)
- `piId` (`java.lang.String`)
- `piDisplayName` (`java.lang.String`)
- `sponsorNotificationConnectorId` (`java.lang.String`)
- `sponsorNotificationDestination` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.UUID deviationId()`

### `public java.lang.String deviationType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String piDisplayName()`

### `public java.lang.String piId()`

### `public io.casehub.clinical.api.model.DeviationSeverity severity()`

### `public java.util.UUID siteId()`

### `public java.lang.String sponsorNotificationConnectorId()`

### `public java.lang.String sponsorNotificationDestination()`

### `public java.lang.String tenantId()`

### `public io.casehub.clinical.api.model.PiApprovalStatus terminalStatus()`

### `public final java.lang.String toString()`

### `public java.util.UUID trialId()`
