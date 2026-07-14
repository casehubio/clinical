package io.casehub.clinical.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AdverseEventService {

    @Inject
    WorkItemService                    workItemService;
    @Inject
    AdverseEventLedgerWriter           ledgerWriter;
    @Inject
    ObjectMapper                       objectMapper;
    @Inject
    AdverseEventEscalationPolicy       policy;
    @Inject
    Event<AdverseEventReportedEvent>   reportedEvents;
    @Inject
    ClinicalMemoryService              memoryService;
    @Inject
    TransactionSynchronizationRegistry txSync;

    @Transactional
    public void reportAdverseEvent(AdverseEvent ae) {
        ae.reportedAt  = Instant.now();
        ae.slaDeadline = ae.reportedAt.plus(ae.grade.sla().orElseThrow());

        PatientEnrollment enrollment = PatientEnrollment.findById(ae.enrollmentId);
        UUID              siteId     = enrollment != null ? enrollment.siteId : null;
        ae.tenantId = enrollment != null ? enrollment.tenantId : "default";

        TrialSite site    = siteId != null ? TrialSite.findById(siteId) : null;
        UUID      trialId = site != null ? site.trialId : null;
        AdverseEventEscalationRequirements requirements =
                policy.evaluate(new AdverseEventContext(ae.id, ae.enrollmentId, siteId, ae.grade));

        if (!requirements.engineCaseRequired()) {
            var workItem = workItemService.create(WorkItemCreateRequest.builder()
                                                                       .title("Adverse Event — " + ae.grade.label())
                                                                       .description("Grade " + ae.grade.label() + " AE for enrollment "
                                                                                    + ae.enrollmentId + ". GCP SLA: "
                                                                                    + ae.grade.sla().orElseThrow().toHours() + "h from " + ae.reportedAt)
                                                                       .types(java.util.List.of("adverse-event"))
                                                                       .formKey("adverse-event-review")
                                                                       .priority(priority(ae))
                                                                       .candidateGroups(requirements.candidateGroups())
                                                                       .createdBy("system")
                                                                       .payload(payload(ae))
                                                                       .claimDeadline(ae.slaDeadline)
                                                                       .build());
            ae.workItemId = workItem.id;
        }

        ae.persist();
        ledgerWriter.writeReportEntry(ae);
        memoryService.storeAeReport(ae.id, ae.enrollmentId, siteId, trialId, ae.grade, ae.tenantId);

        if (requirements.engineCaseRequired()) {
            var event = new AdverseEventReportedEvent(
                    ae.id, ae.enrollmentId, siteId, ae.grade, ae.reportedAt, ae.tenantId);
            txSync.registerInterposedSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        reportedEvents.fireAsync(event);
                    }
                }
            });
        }
    }

    private WorkItemPriority priority(AdverseEvent ae) {
        return switch (ae.grade) {
            case GRADE_5 -> WorkItemPriority.URGENT;
            case GRADE_3, GRADE_4 -> WorkItemPriority.HIGH;
            default -> WorkItemPriority.MEDIUM;
        };
    }

    private String payload(AdverseEvent ae) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "enrollmentId", ae.enrollmentId.toString(),
                    "grade", ae.grade.name(),
                    "occurredAt", ae.occurredAt.toString()));
        } catch (JsonProcessingException e) {
            return "{\"enrollmentId\":\"" + ae.enrollmentId + "\"}";
        }
    }
}
