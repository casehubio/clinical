package io.casehub.clinical.entity;

import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.persistence.EntityManager;

import java.util.UUID;

public final class TenantEntityLookup {

    private TenantEntityLookup() {}

    public static <T> T findByIdForTenant(EntityManager em, Class<T> type, UUID id, CurrentPrincipal principal) {
        if (principal.isCrossTenantAdmin()) return em.find(type, id);
        String queryName = type.getSimpleName() + ".findByIdAndTenantId";
        return em.createNamedQuery(queryName, type)
                .setParameter("id", id).setParameter("tenantId", principal.tenancyId())
                .getResultStream().findFirst().orElse(null);
    }
}
