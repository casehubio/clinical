# io.casehub.clinical.api.model.RegulatorySubmissionStatus

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

## Enum Constants

### `DEADLINE_MISSED` (`io.casehub.clinical.api.model.RegulatorySubmissionStatus`)

IND reporting deadline exhausted without submission.

### `FILED` (`io.casehub.clinical.api.model.RegulatorySubmissionStatus`)

IND expedited safety report filed.

### `NONE` (`io.casehub.clinical.api.model.RegulatorySubmissionStatus`)

Default — AE does not meet IND reportable criteria; no submission triggered.

### `PENDING` (`io.casehub.clinical.api.model.RegulatorySubmissionStatus`)

Grade 3/4/5 + unexpected confirmed; regulatory submission case started.

## Constructors

### `private RegulatorySubmissionStatus()`

## Methods

### `public static io.casehub.clinical.api.model.RegulatorySubmissionStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.RegulatorySubmissionStatus[] values()`
