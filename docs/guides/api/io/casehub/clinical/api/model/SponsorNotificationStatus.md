# io.casehub.clinical.api.model.SponsorNotificationStatus

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

## Enum Constants

### `DELIVERED` (`io.casehub.clinical.api.model.SponsorNotificationStatus`)

Connector confirmed delivery. Terminal success.

### `EXHAUSTED` (`io.casehub.clinical.api.model.SponsorNotificationStatus`)

All attempts consumed; sponsor unreached. Terminal failure.

### `FAILED` (`io.casehub.clinical.api.model.SponsorNotificationStatus`)

Last attempt failed; retries remain. nextRetryAfter governs next pickup.

### `PENDING` (`io.casehub.clinical.api.model.SponsorNotificationStatus`)

Created by notify(); not yet attempted.

## Constructors

### `private SponsorNotificationStatus()`

## Methods

### `public static io.casehub.clinical.api.model.SponsorNotificationStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.SponsorNotificationStatus[] values()`
