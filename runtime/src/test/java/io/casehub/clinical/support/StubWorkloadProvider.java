package io.casehub.clinical.support;

import io.casehub.work.api.WorkloadProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@DefaultBean
public class StubWorkloadProvider implements WorkloadProvider {

    @Override
    public int getActiveWorkCount(String agentId) {
        return 0;
    }
}
