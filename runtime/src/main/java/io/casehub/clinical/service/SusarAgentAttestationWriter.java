package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.ClinicalCapabilities;
import io.casehub.clinical.api.ClinicalTrustDimensions;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.event.ActionGateApprovedEvent;
import io.casehub.engine.common.internal.event.ActionGateExpiredEvent;
import io.casehub.engine.common.internal.event.ActionGateRejectedEvent;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.repository.CaseLedgerEntryRepository;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Writes LedgerAttestation quality signals when SUSAR oversight gates are decided.
 *
 * <p>Observes the same three gate event addresses as SusarGateDecisionListener.
 * Uses the same DB-discriminator pattern (findBySusarOversightCaseId) to identify
 * SUSAR oversight gates. The attestation anchors to the WorkerDecisionEntry written
 * by WorkerDecisionEventCapture when the safety-monitoring agent ran in the SUSAR
 * oversight case.
 *
 * <p>TrustScoreJob (casehub-ledger, 24h schedule) reads these attestations and
 * ingests them into Bayesian Beta trust scores per actor/capability pair.
 *
 * <p>attestorType follows SusarDecisionLedgerWriter line 44:
 * HUMAN when named actor present, SYSTEM otherwise (expiry).
 *
 * <p>Not @DefaultBean — future override via quarkus.arc.exclude-types if needed.
 */
@ApplicationScoped
public class SusarAgentAttestationWriter {

    private static final Logger LOG = Logger.getLogger(SusarAgentAttestationWriter.class);

    @Inject CaseLedgerEntryRepository caseLedgerEntryRepository;
    @Inject LedgerEntryRepository ledgerEntryRepository;

    @ConsumeEvent(value = "casehub.action.gate.approved", blocking = true)
    @Transactional
    public void onApproved(ActionGateApprovedEvent event) {
        writeAttestation(event.caseId(), AttestationVerdict.ENDORSED, event.approvedBy(), Instant.now());
    }

    @ConsumeEvent(value = "casehub.action.gate.rejected", blocking = true)
    @Transactional
    public void onRejected(ActionGateRejectedEvent event) {
        writeAttestation(event.caseId(), AttestationVerdict.CHALLENGED, event.rejectedBy(), Instant.now());
    }

    @ConsumeEvent(value = "casehub.action.gate.expired", blocking = true)
    @Transactional
    public void onExpired(ActionGateExpiredEvent event) {
        writeAttestation(event.caseId(), AttestationVerdict.CHALLENGED, ClinicalActors.CLINICAL_SERVICE, Instant.now());
    }

    private void writeAttestation(UUID caseId, AttestationVerdict verdict, String attestorId, Instant now) {
        AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(caseId);
        if (ae == null) return; // not a SUSAR oversight gate

        caseLedgerEntryRepository.findWorkerDecisionsByCaseId(ae.susarOversightCaseId)
                .stream()
                .filter(e -> ClinicalCapabilities.SAFETY_MONITORING.equals(e.capabilityTag))
                .findFirst()
                .ifPresentOrElse(
                        entry -> {
                            LedgerAttestation attestation = new LedgerAttestation();
                            attestation.id = UUID.randomUUID();
                            attestation.ledgerEntryId = entry.id;
                            attestation.subjectId = ae.susarOversightCaseId;
                            attestation.attestorId = attestorId != null ? attestorId : ClinicalActors.CLINICAL_SERVICE;
                            // Mirror SusarDecisionLedgerWriter line 44: HUMAN when named actor, SYSTEM otherwise
                            attestation.attestorType = ClinicalActors.CLINICAL_SERVICE.equals(attestorId) || attestorId == null
                                    ? ActorType.SYSTEM : ActorType.HUMAN;
                            attestation.attestorRole = "safety-gate-outcome";
                            attestation.verdict = verdict;
                            attestation.capabilityTag = ClinicalCapabilities.SAFETY_MONITORING;
                            attestation.trustDimension = ClinicalTrustDimensions.SAFETY_ACCURACY;
                            attestation.confidence = 1.0;
                            attestation.occurredAt = now;
                            ledgerEntryRepository.saveAttestation(attestation, ae.tenantId);
                        },
                        () -> LOG.warnf("SusarAgentAttestationWriter: no WorkerDecisionEntry for " +
                                "susarOversightCaseId=%s — attestation skipped", ae.susarOversightCaseId)
                );
    }
}
