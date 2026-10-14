package io.casehub.clinical.support;

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.engine.common.internal.history.EventLog;
import io.casehub.engine.common.spi.CrossTenantEventLogRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@DefaultBean
public class StubCrossTenantEventLogRepository implements CrossTenantEventLogRepository {

    @Override
    public List<EventLog> findByTypes(Collection<CaseHubEventType> types) {
        return Collections.emptyList();
    }

    @Override
    public List<EventLog> findByCaseAndTypes(UUID caseId, Collection<CaseHubEventType> types) {
        return Collections.emptyList();
    }

    @Override
    public List<String> findSubmittedWorkWithoutCompletion() {
        return Collections.emptyList();
    }

    @Override
    public Optional<EventLog> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<EventLog> findByCaseAndWorkerAndType(UUID caseId, String workerId, CaseHubEventType type) {
        return Collections.emptyList();
    }

    @Override
    public List<EventLog> findByWorkerAndTypeAcrossTenants(String workerId, CaseHubEventType type) {
        return Collections.emptyList();
    }
}
