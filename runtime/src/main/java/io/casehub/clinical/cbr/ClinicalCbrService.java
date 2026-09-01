package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ClinicalCbrService {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(ClinicalCbrService.class);

    private final CbrCaseMemoryStore                                   store;
    private final io.casehub.neocortex.memory.cbr.ExplanationRenderer  explanationRenderer;
    private final io.casehub.clinical.service.CbrRetrievalLedgerWriter ledgerWriter;
    private final java.time.Clock                                      clock;

    @Inject
    public ClinicalCbrService(final CbrCaseMemoryStore store,
                              final io.casehub.neocortex.memory.cbr.ExplanationRenderer explanationRenderer,
                              final io.casehub.clinical.service.CbrRetrievalLedgerWriter ledgerWriter,
                              final java.time.Clock clock) {
        this.store               = store;
        this.explanationRenderer = explanationRenderer;
        this.ledgerWriter        = ledgerWriter;
        this.clock               = clock;
    }

    public String storeIdempotent(final CbrCase cbrCase, final String caseType,
                                  final String entityId, final MemoryDomain domain,
                                  final String tenantId, final String caseId,
                                  final io.casehub.platform.api.path.Path scope) {
        store.eraseEntity(entityId, tenantId);
        return store.store(cbrCase, caseType, entityId, domain, tenantId, caseId, scope);
    }

    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(final CbrQuery query,
                                                                      final Class<C> caseType) {
        return store.retrieveSimilar(query, caseType);
    }

    public <C extends CbrCase> AuditedRetrievalResult<C> retrieveWithAudit(
            final CbrQuery query, final Class<C> caseType,
            final java.util.UUID subjectId, final String actorId) {
        List<ScoredCbrCase<C>>                            cases = retrieveSimilar(query, caseType);
        io.casehub.neocortex.memory.cbr.CbrRetrievalTrace trace = buildTrace(query, cases);

        String explanation;
        try {
            explanation = explanationRenderer.render(trace);
        } catch (Exception e) {
            LOG.warnf(e, "ExplanationRenderer failed for trace %s — recording with null explanation", trace.traceId());
            explanation = null;
        }

        ledgerWriter.record(trace, explanation, subjectId, actorId);
        return new AuditedRetrievalResult<>(cases, trace.traceId(), explanation);
    }

    private <C extends CbrCase> io.casehub.neocortex.memory.cbr.CbrRetrievalTrace buildTrace(
            CbrQuery query, List<ScoredCbrCase<C>> cases) {
        List<io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase> tracedCases = cases.stream()
                                                                                              .map(sc -> new io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase(sc.caseId(), sc.score(), sc.reranked(), sc.featureSimilarities(), sc.cbrCase().confidence(), null, null, null))
                                                                                              .toList();
        return new io.casehub.neocortex.memory.cbr.CbrRetrievalTrace(
                java.util.UUID.randomUUID().toString(), query, tracedCases, clock.instant());
    }
}
