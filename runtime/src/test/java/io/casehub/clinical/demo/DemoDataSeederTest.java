package io.casehub.clinical.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DemoDataSeeder constants and deterministic UUID generation.
 *
 * <p>The seeder is {@code @IfBuildProfile("dev")} so CDI does not register it in the test
 * profile. Full lifecycle integration is covered by {@code ThreeSiteShowcaseTest} which
 * exercises the same service calls (AdverseEventService, ProtocolDeviationService,
 * EligibilityScreeningService, SUSAR oversight, PI channel gateway).
 *
 * <p>This test verifies:
 * <ul>
 *   <li>Deterministic UUID matches the TypeScript constant</li>
 *   <li>Site and patient UUIDs are stable across JVM restarts</li>
 *   <li>UUID generation is consistent with {@code UUID.nameUUIDFromBytes}</li>
 * </ul>
 */
class DemoDataSeederTest {

    /** TypeScript constant: {@code 316e3846-4ea7-3b18-a6f7-e01ce6582a69}. */
    private static final String EXPECTED_TRIAL_UUID = "316e3846-4ea7-3b18-a6f7-e01ce6582a69";

    @Test
    void trial_id_matches_typescript_constant() {
        assertThat(DemoDataSeeder.TRIAL_ID.toString()).isEqualTo(EXPECTED_TRIAL_UUID);
    }

    @Test
    void trial_id_is_deterministic() {
        UUID recomputed = UUID.nameUUIDFromBytes("ONCO-2024-001".getBytes(StandardCharsets.UTF_8));
        assertThat(DemoDataSeeder.TRIAL_ID).isEqualTo(recomputed);
    }

    @Test
    void site_ids_are_deterministic_and_distinct() {
        UUID siteA = UUID.nameUUIDFromBytes("SITE-A".getBytes(StandardCharsets.UTF_8));
        UUID siteB = UUID.nameUUIDFromBytes("SITE-B".getBytes(StandardCharsets.UTF_8));
        UUID siteC = UUID.nameUUIDFromBytes("SITE-C".getBytes(StandardCharsets.UTF_8));

        assertThat(DemoDataSeeder.SITE_A_ID).isEqualTo(siteA);
        assertThat(DemoDataSeeder.SITE_B_ID).isEqualTo(siteB);
        assertThat(DemoDataSeeder.SITE_C_ID).isEqualTo(siteC);

        assertThat(siteA).isNotEqualTo(siteB);
        assertThat(siteB).isNotEqualTo(siteC);
        assertThat(siteA).isNotEqualTo(siteC);
    }

    @Test
    void patient_ids_are_deterministic_and_distinct() {
        UUID patA = UUID.nameUUIDFromBytes("PATIENT-A-001".getBytes(StandardCharsets.UTF_8));
        UUID patB = UUID.nameUUIDFromBytes("PATIENT-B-001".getBytes(StandardCharsets.UTF_8));
        UUID patC = UUID.nameUUIDFromBytes("PATIENT-C-001".getBytes(StandardCharsets.UTF_8));

        assertThat(DemoDataSeeder.PATIENT_A1_ID).isEqualTo(patA);
        assertThat(DemoDataSeeder.PATIENT_B1_ID).isEqualTo(patB);
        assertThat(DemoDataSeeder.PATIENT_C1_ID).isEqualTo(patC);

        assertThat(patA).isNotEqualTo(patB);
        assertThat(patB).isNotEqualTo(patC);
    }

    @Test
    void uuid_v3_type_is_name_based() {
        // UUID.nameUUIDFromBytes produces version 3 (name-based MD5)
        assertThat(DemoDataSeeder.TRIAL_ID.version()).isEqualTo(3);
        assertThat(DemoDataSeeder.SITE_A_ID.version()).isEqualTo(3);
    }
}
