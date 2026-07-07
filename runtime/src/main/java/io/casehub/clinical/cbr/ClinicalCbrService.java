package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Thin wrapper over {@link CbrCaseMemoryStore} with erase-before-store semantics.
 * <p>
 * Ensures idempotent case storage: each entity can have at most one CBR case
 * representation at any given time. When a case is updated (e.g., adverse event
 * escalation completes and outcome is known), the old precedent is erased and
 * replaced with the updated one.
 */
@ApplicationScoped
public class ClinicalCbrService {

    private final CbrCaseMemoryStore store;

    @Inject
    public ClinicalCbrService(final CbrCaseMemoryStore store) {
        this.store = store;
    }

    /**
     * Store a CBR case with idempotent semantics: erase any existing case for
     * the same entityId, then store the new one.
     *
     * @param cbrCase  the case to store (problem, solution, outcome, features)
     * @param caseType schema identifier (e.g., "clinical-ae")
     * @param entityId domain entity identifier (e.g., "ae-{aeId}")
     * @param domain   memory domain (e.g., ClinicalCbrDomains.AE)
     * @param tenantId tenant identifier
     * @param caseId   CaseHub case ID (nullable for non-case-linked precedents)
     * @return the generated CBR case ID
     */
    public String storeIdempotent(final CbrCase cbrCase, final String caseType,
                                  final String entityId, final MemoryDomain domain,
                                  final String tenantId, final String caseId) {
        store.eraseEntity(entityId, tenantId);
        return store.store(cbrCase, caseType, entityId, domain, tenantId, caseId);
    }

    /**
     * Retrieve similar cases for a given query.
     *
     * @param query    feature-based query with topK limit
     * @param caseType class of CBR case to retrieve
     * @param <C>      concrete CBR case type
     * @return list of scored cases, ordered by similarity descending
     */
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(final CbrQuery query,
                                                                        final Class<C> caseType) {
        return store.retrieveSimilar(query, caseType);
    }
}
