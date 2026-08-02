package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ProtocolAmendmentResolvedEvent;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * CBR writer for protocol amendment resolution events.
 * <p>
 * Records each resolved amendment as a {@link TextualCbrCase} precedent:
 * <ul>
 * <li>problem = proposedChange text</li>
 * <li>solution = supervisor recommendation (PROCEED, HALT, REFER_TO_DSMB)</li>
 * <li>outcome = terminal status (APPROVED, HALTED, SUPERVISED)</li>
 * <li>confidence = 1.0 (all resolutions are accepted precedent)</li>
 * </ul>
 * <p>
 * Future amendment supervision cases can retrieve similar precedents via
 * {@link ClinicalCbrService#retrieveSimilar} with the proposed change text.
 */
@ApplicationScoped
public class AmendmentResolutionCbrWriter {

    private static final Logger LOG = Logger.getLogger(AmendmentResolutionCbrWriter.class);

    @Inject
    ClinicalCbrService cbrService;

    @Inject
    ClinicalScopeResolver scopeResolver;

    /**
     * Observes {@link ProtocolAmendmentResolvedEvent} and stores a CBR precedent.
     * <p>
     * The event fires after {@link io.casehub.clinical.service.ProtocolAmendmentStatusUpdater}
     * commits the terminal status + ledger entry. This method loads the amendment entity
     * to extract the proposed change text and tenantId.
     *
     * @param event resolution event with amendmentId, terminalStatus, and recommendation
     */
    @Transactional
    public void onAmendmentResolved(@ObservesAsync ProtocolAmendmentResolvedEvent event) {
        try {
            ProtocolAmendment amendment = ProtocolAmendment.findById(event.amendmentId());
            if (amendment == null) {
                LOG.warnf("Amendment %s not found — cannot store CBR case", event.amendmentId());
                return;
            }

            java.util.Optional<io.casehub.platform.api.path.Path> scopeOpt = scopeResolver.forAmendment(amendment);
            if (scopeOpt.isEmpty()) {
                LOG.warnf("Cannot resolve scope for amendment %s — skipping CBR storage", event.amendmentId());
                return;
            }
            io.casehub.platform.api.path.Path scope = scopeOpt.get();

            String solution = event.recommendation() != null
                ? event.recommendation().name()
                : "UNKNOWN";

            var cbrCase = new TextualCbrCase(
                amendment.proposedChange,
                solution,
                event.terminalStatus().name(),
                1.0,
                null,
                null
            );

            String caseId = amendment.engineCaseId != null
                ? amendment.engineCaseId.toString()
                : null;

            cbrService.storeIdempotent(
                cbrCase,
                "clinical-amendment",
                event.amendmentId().toString(),
                ClinicalCbrDomains.AMENDMENT,
                amendment.tenantId,
                caseId,
                scope
            );

            LOG.infof("Stored CBR case for amendment %s: %s → %s",
                event.amendmentId(), solution, event.terminalStatus());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to store CBR case for amendment %s", event.amendmentId());
        }
    }
}
