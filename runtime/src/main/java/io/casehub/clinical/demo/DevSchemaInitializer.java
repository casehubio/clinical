package io.casehub.clinical.demo;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.sql.SQLException;

@ApplicationScoped
@IfBuildProfile("dev")
public class DevSchemaInitializer {

    @Inject
    @io.quarkus.agroal.DataSource("qhorus")
    AgroalDataSource qhorusDataSource;

    void onStart(@Observes @Priority(1) StartupEvent event) throws SQLException {
        try (var conn = qhorusDataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS ledger_subject_sequence ("
                    + "subject_id UUID NOT NULL, "
                    + "tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce', "
                    + "next_seq INT NOT NULL DEFAULT 1, "
                    + "CONSTRAINT pk_ledger_subject_sequence PRIMARY KEY (subject_id, tenancy_id))");
        }
    }
}
