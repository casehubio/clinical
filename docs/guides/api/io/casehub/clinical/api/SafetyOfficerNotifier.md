# io.casehub.clinical.api.SafetyOfficerNotifier

**Package:** `io.casehub.clinical.api`

**Kind:** `interface`

SPI for delivering adverse event notifications to the safety officer.

<p>Deployers override the `@DefaultBean` implementation to customise delivery channel,
routing logic, or integration. Grade information is available in the request — a custom
implementation can route Grade 5 (Death) to an emergency pager and lower grades to Slack.

<p>GCP ICH E6(R3) §5.17 / 21 CFR 312.32: notification delivery (success or failure)
must be recorded in the tamper-evident audit trail. Implementations that replace the
default must also write a ledger entry.

## Methods

### `public abstract void notify(io.casehub.clinical.api.SafetyOfficerNotificationRequest request)`

#### Parameters

- `request` (`io.casehub.clinical.api.SafetyOfficerNotificationRequest`)
