package io.casehub.clinical.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.SponsorNotificationExhaustedEvent;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Creates a casehub-work WorkItem for manual resolution when sponsor notification
 * delivery is exhausted (all retry attempts consumed).
 *
 * <p>SLA: CRITICAL deviations require resolution within 24h (ICH E6(R3) §5.17 reporting
 * urgency); MAJOR and MINOR within 72h. The WorkItem routes to {@code site-coordinators}.
 */
@ApplicationScoped
public class SponsorNotificationExhaustedWorkItemListener {

    private static final Logger LOG = Logger.getLogger(SponsorNotificationExhaustedWorkItemListener.class);
    private static final String CANDIDATE_GROUPS = "site-coordinators";
    private static final String CATEGORY = "sponsor-notification-escalation";
    private static final Duration CRITICAL_SLA = Duration.ofHours(24);
    private static final Duration DEFAULT_SLA = Duration.ofHours(72);

    @Inject WorkItemService workItemService;
    @Inject ObjectMapper objectMapper;
    @Inject Clock clock;

    @Transactional
    public void onExhausted(@ObservesAsync final SponsorNotificationExhaustedEvent event) {
        try {
            workItemService.create(WorkItemCreateRequest.builder()
                    .title("[" + event.severity().name() + "] Sponsor notification undeliverable"
                            + " — manual resolution required")
                    .description("All " + event.totalAttempts() + " delivery attempt(s) for"
                            + " notification " + event.notificationId() + " exhausted."
                            + " Reason: " + safeReason(event.failureReason())
                            + ". Deviation " + event.deviationId()
                            + " at site " + event.siteId() + " requires manual sponsor contact.")
                    .types(List.of(CATEGORY))
                    .formKey(CATEGORY)
                    .priority(WorkItemPriority.HIGH)
                    .candidateGroups(CANDIDATE_GROUPS)
                    .createdBy(ClinicalActors.CLINICAL_SERVICE)
                    .payload(buildPayload(event))
                    .claimDeadline(clock.instant().plus(sla(event.severity())))
                    .build());
        } catch (final Exception e) {
            LOG.errorf(e,
                    "Failed to create escalation WorkItem for exhausted sponsor notification %s"
                    + " (deviation %s, site %s)",
                    event.notificationId(), event.deviationId(), event.siteId());
        }
    }

    private String buildPayload(final SponsorNotificationExhaustedEvent event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "notificationId", event.notificationId().toString(),
                    "deviationId", event.deviationId().toString(),
                    "trialId", event.trialId().toString(),
                    "siteId", event.siteId().toString(),
                    "severity", event.severity().name(),
                    "terminalStatus", event.terminalStatus().name(),
                    "failureReason", safeReason(event.failureReason()),
                    "totalAttempts", event.totalAttempts()
            ));
        } catch (final JsonProcessingException e) {
            LOG.errorf(e, "Could not serialise payload for notification %s", event.notificationId());
            return "{}";
        }
    }

    private static Duration sla(final DeviationSeverity severity) {
        return severity == DeviationSeverity.CRITICAL ? CRITICAL_SLA : DEFAULT_SLA;
    }

    private static String safeReason(final String reason) {
        return reason != null ? reason : "";
    }
}
