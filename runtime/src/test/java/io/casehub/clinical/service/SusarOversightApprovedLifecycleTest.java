package io.casehub.clinical.service;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Approved-path lifecycle test: oversight case starts → gate created →
 * WorkItem approved → engine re-fires WorkflowExecutionCompleted →
 * susarAssessmentComplete written → susar-complete goal satisfied → case terminal.
 *
 * Pattern: IrbGateLifecycleTest approved path.
 * TODO: implement after reading IrbGateLifecycleTest for WorkItem approval mechanism.
 */
@QuarkusTest
class SusarOversightApprovedLifecycleTest {

    @Test
    @Disabled("Pending: implement WorkItem approval following IrbGateLifecycleTest pattern")
    void approved_gate_completes_oversight_case() {
        // Implement after reading IrbGateLifecycleTest for WorkItem completion mechanism
    }
}
