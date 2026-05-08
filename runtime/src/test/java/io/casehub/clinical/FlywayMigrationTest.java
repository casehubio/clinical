package io.casehub.clinical;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FlywayMigrationTest {

    @Test
    void migrations_apply_cleanly() {
        // If Quarkus starts without exception, all 6 migrations ran successfully.
    }
}
