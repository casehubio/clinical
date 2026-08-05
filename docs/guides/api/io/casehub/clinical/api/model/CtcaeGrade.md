# io.casehub.clinical.api.model.CtcaeGrade

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

CTCAE v5.0 adverse event severity grades with reporting SLA durations.

<p>GCP ICH E6(R3) §5.17 defines reporting SLAs per grade:
<ul>
  <li>Grade 1-2 (non-serious): 7 days</li>
  <li>Grade 3-4 (serious): 24 hours</li>
  <li>Grade 5 (death): 1 hour (internal policy — stricter than ICH minimum)</li>
</ul>

## Fields

### `label` (`java.lang.String`)

### `sla` (`java.time.Duration`)

## Enum Constants

### `GRADE_1` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `GRADE_2` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `GRADE_3` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `GRADE_4` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `GRADE_5` (`io.casehub.clinical.api.model.CtcaeGrade`)

## Constructors

### `private CtcaeGrade(java.lang.String label, java.time.Duration sla)`

#### Parameters

- `label` (`java.lang.String`)
- `sla` (`java.time.Duration`)

## Methods

### `public java.lang.String label()`

CTCAE v5.0 human-readable grade name, e.g. "Severe". Used in reports and audit records.

### `public java.util.Optional<java.time.Duration> sla()`

Reporting SLA per GCP ICH E6(R3) §5.17. Present for all grades.

### `public static io.casehub.clinical.api.model.CtcaeGrade valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.CtcaeGrade[] values()`
