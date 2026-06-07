package io.casehub.clinical.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SponsorNotificationRetryPolicyTest {

    // ── 2-field string (existing format) ─────────────────────────────────────

    @Test
    void parse_2field_string_uses_default_multiplier_and_no_max_interval() {
        final SponsorNotificationRetryPolicy p = parse("3,30");
        assertThat(p.maxAttempts()).isEqualTo(3);
        assertThat(p.retryInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(p.backoffMultiplier()).isEqualTo(1.0);
        assertThat(p.maxInterval()).isNull();
    }

    // ── 3-field string ────────────────────────────────────────────────────────

    @Test
    void parse_3field_string_sets_backoff_multiplier() {
        final SponsorNotificationRetryPolicy p = parse("3,15,2.0");
        assertThat(p.maxAttempts()).isEqualTo(3);
        assertThat(p.retryInterval()).isEqualTo(Duration.ofMinutes(15));
        assertThat(p.backoffMultiplier()).isEqualTo(2.0);
        assertThat(p.maxInterval()).isNull();
    }

    @Test
    void parse_3field_multiplier_1_0_equals_fixed_interval() {
        final SponsorNotificationRetryPolicy p = parse("5,10,1.0");
        assertThat(p.backoffMultiplier()).isEqualTo(1.0);
        assertThat(p.maxInterval()).isNull();
    }

    // ── 4-field string ────────────────────────────────────────────────────────

    @Test
    void parse_4field_string_sets_multiplier_and_max_interval() {
        final SponsorNotificationRetryPolicy p = parse("3,15,2.0,120");
        assertThat(p.backoffMultiplier()).isEqualTo(2.0);
        assertThat(p.maxInterval()).isEqualTo(Duration.ofMinutes(120));
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    void parse_multiplier_less_than_1_throws() {
        assertThatThrownBy(() -> parse("3,30,0.5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backoffMultiplier");
    }

    @Test
    void parse_zero_multiplier_throws() {
        assertThatThrownBy(() -> parse("3,30,0.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_negative_max_interval_throws() {
        assertThatThrownBy(() -> parse("3,30,2.0,-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIntervalMinutes");
    }

    @Test
    void parse_zero_max_interval_throws() {
        assertThatThrownBy(() -> parse("3,30,2.0,0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIntervalMinutes");
    }

    @Test
    void parse_too_many_fields_throws() {
        assertThatThrownBy(() -> parse("3,30,2.0,120,extra"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── DEFAULT constant ──────────────────────────────────────────────────────

    @Test
    void default_policy_has_multiplier_1_and_no_max_interval() {
        assertThat(SponsorNotificationRetryPolicy.DEFAULT.backoffMultiplier()).isEqualTo(1.0);
        assertThat(SponsorNotificationRetryPolicy.DEFAULT.maxInterval()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static SponsorNotificationRetryPolicy parse(final String s) {
        return SponsorNotificationRetryPolicy.KEY.parser().apply(s);
    }
}
