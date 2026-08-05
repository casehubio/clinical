# io.casehub.clinical.api.SponsorNotificationRetryPolicy

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

Retry policy for sponsor notification delivery.

<p>`maxAttempts` is the total number of delivery attempts, including
the first. Minimum 1 (one attempt, no retry). With maxAttempts=1 and a
failure, the notification goes straight to EXHAUSTED — equivalent to
fire-and-forget but with a full audit trail.

<p>`retryInterval` is the base wait between attempts.

<p>`backoffMultiplier` (≥1.0, default 1.0) scales the delay exponentially:
`delay = retryInterval × backoffMultiplier^(attemptNumber \u2212 1)`.
With 1.0 the interval is fixed; with 2.0 it doubles each attempt.

<p>`maxInterval` caps the computed delay (null = no cap).

<p>Configured via MicroProfile Config:
<pre>
  casehub.platform.preferences.defaults.casehubio.clinical.sponsorNotifierRetryPolicy=3,30
  casehub.platform.preferences.defaults.casehubio.clinical.sponsorNotifierRetryPolicy=3,15,2.0
  casehub.platform.preferences.defaults.casehubio.clinical.sponsorNotifierRetryPolicy=3,15,2.0,120
</pre>
Format: `"<maxAttempts>,<retryIntervalMinutes>[,<backoffMultiplier>[,<maxIntervalMinutes>]]"`

## Fields

### `DEFAULT` (`io.casehub.clinical.api.SponsorNotificationRetryPolicy`)

### `KEY` (`PreferenceKey<io.casehub.clinical.api.SponsorNotificationRetryPolicy>`)

### `backoffMultiplier` (`double`)

### `maxAttempts` (`int`)

### `maxInterval` (`java.time.Duration`)

### `retryInterval` (`java.time.Duration`)

## Record Components

### `backoffMultiplier` (`double`)

### `maxAttempts` (`int`)

### `maxInterval` (`java.time.Duration`)

### `retryInterval` (`java.time.Duration`)

## Constructors

### `public SponsorNotificationRetryPolicy(int maxAttempts, java.time.Duration retryInterval, double backoffMultiplier, java.time.Duration maxInterval)`

#### Parameters

- `maxAttempts` (`int`)
- `retryInterval` (`java.time.Duration`)
- `backoffMultiplier` (`double`)
- `maxInterval` (`java.time.Duration`)

## Methods

### `public double backoffMultiplier()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int maxAttempts()`

### `public java.time.Duration maxInterval()`

### `public java.time.Duration retryInterval()`

### `public java.lang.String toSerializedValue()`

### `public final java.lang.String toString()`
