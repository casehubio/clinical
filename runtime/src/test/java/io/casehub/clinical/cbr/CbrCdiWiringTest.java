package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test to verify CBR CDI wiring is correct: CbrCaseMemoryStore and
 * ClinicalCbrService should both resolve.
 */
@QuarkusTest
class CbrCdiWiringTest {

    @Inject
    CbrCaseMemoryStore store;

    @Inject
    ClinicalCbrService service;

    @Test
    void cbrCaseMemoryStore_injected() {
        assertThat(store).isNotNull();
    }

    @Test
    void clinicalCbrService_injected() {
        assertThat(service).isNotNull();
    }
}
