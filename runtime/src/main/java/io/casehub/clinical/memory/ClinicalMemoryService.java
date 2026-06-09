package io.casehub.clinical.memory;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.MemoryAttributeKeys;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.api.memory.MemoryQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ClinicalMemoryService {

    private static final Logger LOG = Logger.getLogger(ClinicalMemoryService.class);
    private static final String ACTOR = "clinical-service";

    private final CaseMemoryStore store;

    @Inject
    public ClinicalMemoryService(final CaseMemoryStore store) {
        this.store = store;
    }

    public void storeAeReport(final UUID aeId, final UUID enrollmentId, final UUID siteId,
                              final UUID trialId, final CtcaeGrade grade, final String tenantId) {
        final Map<String, String> attrs = Map.of(
            MemoryAttributeKeys.ACTOR_ID, ACTOR,
            MemoryAttributeKeys.OUTCOME, "REPORTED",
            ClinicalMemoryAttributes.GRADE, grade.name());
        final String text = "Grade " + grade.name() + " AE " + aeId + " reported for enrollment "
            + enrollmentId + " at site " + siteId;

        try {
            store.store(new MemoryInput("patient:" + enrollmentId, ClinicalMemoryDomains.PATIENT,
                tenantId, null, text, attrs));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: storeAeReport failed for aeId=%s — ignored", aeId);
        }
        try {
            store.store(new MemoryInput("site:" + siteId, ClinicalMemoryDomains.SITE,
                tenantId, null, text, attrs));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: storeAeReport (site) failed for siteId=%s — ignored", siteId);
        }
        if (trialId != null && siteId != null) {
            try {
                store.store(new MemoryInput("trial:" + trialId, ClinicalMemoryDomains.DRUG,
                    tenantId, null, text,
                    Map.of(MemoryAttributeKeys.ACTOR_ID, ACTOR,
                        MemoryAttributeKeys.OUTCOME, "REPORTED",
                        ClinicalMemoryAttributes.GRADE, grade.name(),
                        ClinicalMemoryAttributes.SITE_ID, siteId.toString())));
            } catch (Exception e) {
                LOG.warnf(e, "ClinicalMemoryService: storeAeReport (drug) failed for trialId=%s — ignored", trialId);
            }
        }
    }

    public void storeAeOutcome(final UUID aeId, final UUID enrollmentId, final CtcaeGrade grade,
                               final String safetyReview, final boolean dsmbEscalated,
                               final String tenantId) {
        final String outcome = dsmbEscalated ? "DSMB_ESCALATED" : "ESCALATED";
        try {
            store.store(new MemoryInput(
                "patient:" + enrollmentId,
                ClinicalMemoryDomains.PATIENT,
                tenantId, null,
                "AE " + aeId + " escalation completed: safetyReview=" + safetyReview + ", dsmb=" + dsmbEscalated,
                Map.of(MemoryAttributeKeys.ACTOR_ID, ACTOR, MemoryAttributeKeys.OUTCOME, outcome,
                    ClinicalMemoryAttributes.GRADE, grade.name())));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: storeAeOutcome failed for aeId=%s — ignored", aeId);
        }
    }

    public void storeDeviationReport(final UUID deviationId, final UUID siteId,
                                     final String deviationType, final DeviationSeverity severity,
                                     final String tenantId) {
        try {
            store.store(new MemoryInput(
                "site:" + siteId,
                ClinicalMemoryDomains.SITE,
                tenantId, null,
                "Protocol deviation " + deviationId + ": type=" + deviationType + ", severity=" + severity,
                Map.of(MemoryAttributeKeys.ACTOR_ID, ACTOR, MemoryAttributeKeys.OUTCOME, severity.name())));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: storeDeviationReport failed for deviationId=%s — ignored", deviationId);
        }
    }

    public void storePiDecision(final UUID deviationId, final UUID siteId,
                                final String deviationType, final PiApprovalStatus status,
                                final String tenantId) {
        final String outcome = (status == PiApprovalStatus.EXPIRED) ? "TIMELINE_BREACH" : status.name();
        try {
            store.store(new MemoryInput(
                "site:" + siteId,
                ClinicalMemoryDomains.SITE,
                tenantId, null,
                "PI decision for deviation " + deviationId + " (" + deviationType + "): " + status,
                Map.of(MemoryAttributeKeys.ACTOR_ID, ACTOR, MemoryAttributeKeys.OUTCOME, outcome)));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: storePiDecision failed for deviationId=%s — ignored", deviationId);
        }
    }

    public ClinicalPatientContext queryPatientContext(final UUID enrollmentId, final String tenantId) {
        try {
            final var query = MemoryQuery.forEntity("patient:" + enrollmentId, ClinicalMemoryDomains.PATIENT, tenantId);
            return new ClinicalPatientContext(store.query(query));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: queryPatientContext failed for enrollmentId=%s — returning empty", enrollmentId);
            return ClinicalPatientContext.empty();
        }
    }

    public ClinicalSiteContext querySiteContext(final UUID siteId, final String tenantId) {
        try {
            final var query = MemoryQuery.forEntity("site:" + siteId, ClinicalMemoryDomains.SITE, tenantId)
                .withSince(Instant.now().minus(180, ChronoUnit.DAYS))
                .withLimit(50);
            return new ClinicalSiteContext(store.query(query));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: querySiteContext failed for siteId=%s — returning empty", siteId);
            return ClinicalSiteContext.empty();
        }
    }

    public ClinicalDrugContext queryDrugContext(final UUID trialId, final String tenantId) {
        try {
            final var query = MemoryQuery.forEntity("trial:" + trialId, ClinicalMemoryDomains.DRUG, tenantId)
                .withLimit(200);
            return new ClinicalDrugContext(store.query(query));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: queryDrugContext failed for trialId=%s — returning empty", trialId);
            return ClinicalDrugContext.empty();
        }
    }

    public void storeIrbDecision(final UUID approvalId, final UUID siteId,
                                 final String deviationType, final IrbDecision decision,
                                 final String tenantId) {
        if (deviationType == null || deviationType.isBlank()) return;
        final String siteStr = siteId != null ? siteId.toString() : "";
        try {
            store.store(new MemoryInput(
                "deviation-type:" + deviationType,
                ClinicalMemoryDomains.IRB,
                tenantId, null,
                "IRB " + decision + " for deviation type " + deviationType + " at site " + siteStr,
                Map.of(MemoryAttributeKeys.ACTOR_ID, ACTOR,
                    MemoryAttributeKeys.OUTCOME, decision.name(),
                    ClinicalMemoryAttributes.SITE_ID, siteStr)));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: storeIrbDecision failed for approvalId=%s — ignored", approvalId);
        }
    }

    public ClinicalIrbContext queryIrbContext(final String deviationType, final String tenantId) {
        if (deviationType == null || deviationType.isBlank()) return ClinicalIrbContext.empty();
        try {
            final var query = MemoryQuery.forEntity("deviation-type:" + deviationType, ClinicalMemoryDomains.IRB, tenantId)
                .withLimit(200);
            return new ClinicalIrbContext(store.query(query));
        } catch (Exception e) {
            LOG.warnf(e, "ClinicalMemoryService: queryIrbContext failed for deviationType=%s — returning empty", deviationType);
            return ClinicalIrbContext.empty();
        }
    }
}
