package io.casehub.clinical;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FlywayMigrationTest {

    @Test
    void quarkus_starts_cleanly_with_qhorus_on_classpath() {
        // Tests use drop-and-create (Flyway disabled in test profile to avoid classpath migration
        // version conflicts between casehub-work V1–V21 and casehub-qhorus V1–V9 at db/migration).
        // This test verifies that Quarkus starts successfully with qhorus, work, and ledger
        // all on the classpath without reactive or PU configuration failures.
    }
}
