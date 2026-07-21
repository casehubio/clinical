package io.casehub.clinical.cbr;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AeTrajectoryBuilder {

    private final PlanItemStore planItemStore;
    private       java.util.function.Function<UUID, java.util.List<io.casehub.clinical.entity.AeGradeChange>> gradeHistoryFinder = io.casehub.clinical.entity.AeGradeChange::findByAdverseEventId;

    void setGradeHistoryFinder(java.util.function.Function<UUID, java.util.List<io.casehub.clinical.entity.AeGradeChange>> finder) {
        this.gradeHistoryFinder = finder;
    }


    @Inject
    public AeTrajectoryBuilder(PlanItemStore planItemStore) {
        this.planItemStore = planItemStore;
    }

    public List<Map<String, FeatureValue>> buildTrajectory(AdverseEvent ae, String tenantId) {
        return doBuild(ae, tenantId);
    }

    public List<Map<String, FeatureValue>> buildPartialTrajectory(AdverseEvent ae, String tenantId) {
        return doBuild(ae, tenantId);
    }

    private List<Map<String, FeatureValue>> doBuild(AdverseEvent ae, String tenantId) {
        int escalation = ae.engineCaseId != null ? ordinal(AeEscalationStatus.REQUESTED) : ordinal(AeEscalationStatus.NONE);
        int susar      = ordinal(SusarOversightStatus.NONE);
        int regulatory = ordinal(RegulatorySubmissionStatus.NONE);

        var gradeHistory = gradeHistoryFinder.apply(ae.id);
        int currentGrade = !gradeHistory.isEmpty()
                           ? gradeHistory.get(0).newGrade.ordinal() + 1
                           : (ae.grade != null ? ae.grade.ordinal() + 1 : 1);

        List<Observation> observations = new ArrayList<>();
        observations.add(new Observation(0, escalation, susar, regulatory, currentGrade));

        List<PlanItemRecord> allRecords = new ArrayList<>();
        collectRecords(ae.engineCaseId, tenantId, allRecords);
        collectRecords(ae.susarOversightCaseId, tenantId, allRecords);
        collectRecords(ae.regulatorySubmissionCaseId, tenantId, allRecords);

        record TimelineEvent(long seconds, boolean isGradeChange,
                             io.casehub.clinical.entity.AeGradeChange gradeChange,
                             PlanItemRecord planItem) {}

        List<TimelineEvent> timeline = new ArrayList<>();
        for (var gc : gradeHistory) {
            if (gc.previousGrade == null) {continue;}
            long seconds = Duration.between(ae.reportedAt, gc.changedAt).getSeconds();
            if (seconds < 0) {seconds = 0;}
            timeline.add(new TimelineEvent(seconds, true, gc, null));
        }
        for (var record : allRecords) {
            long seconds = Duration.between(ae.reportedAt, record.createdAt()).getSeconds();
            if (seconds < 0) {seconds = 0;}
            timeline.add(new TimelineEvent(seconds, false, null, record));
        }
        timeline.sort(Comparator.comparingLong(TimelineEvent::seconds)
                                .thenComparing(e -> !e.isGradeChange()));

        for (var event : timeline) {
            if (event.isGradeChange()) {
                currentGrade = event.gradeChange().newGrade.ordinal() + 1;
            } else {
                var record = event.planItem();
                if (isSusarBinding(record.bindingName())) {
                    susar = record.status().isTerminal() ? ordinal(SusarOversightStatus.COMPLETED) : ordinal(SusarOversightStatus.REQUESTED);
                } else if (isRegulatoryBinding(record.bindingName())) {
                    regulatory = record.status().isTerminal() ? ordinal(RegulatorySubmissionStatus.FILED) : ordinal(RegulatorySubmissionStatus.PENDING);
                }
                if (record.status().isTerminal() && isEscalationBinding(record.bindingName())) {
                    escalation = ordinal(AeEscalationStatus.COMPLETED);
                }
            }
            observations.add(new Observation(event.seconds(), escalation, susar, regulatory, currentGrade));
        }

        if (!observations.isEmpty()) {
            var last = observations.get(observations.size() - 1);
            last.escalation = ordinal(ae.escalationStatus);
            last.susar      = ordinal(ae.susarOversightStatus);
            last.regulatory = ordinal(ae.regulatorySubmissionStatus);
            last.grade      = ae.grade != null ? ae.grade.ordinal() + 1 : currentGrade;
        }

        return coalesce(observations).stream().map(Observation::toFeatureMap).toList();
    }


    private static List<Observation> coalesce(List<Observation> observations) {
        if (observations.size() <= 1) {return observations;}
        List<Observation> result = new ArrayList<>();
        for (Observation obs : observations) {
            if (!result.isEmpty() && result.get(result.size() - 1).secondsSinceReport == obs.secondsSinceReport) {
                result.set(result.size() - 1, obs);
            } else {
                result.add(obs);
            }
        }
        return result;
    }

    private void collectRecords(UUID caseId, String tenantId, List<PlanItemRecord> target) {
        if (caseId != null) {
            target.addAll(planItemStore.findByCaseId(caseId, tenantId));
        }
    }

    private boolean isEscalationBinding(String name) {
        return name != null && (name.contains("safety-review") || name.contains("dsmb"));
    }

    private boolean isSusarBinding(String name) {
        return name != null && name.contains("susar");
    }

    private boolean isRegulatoryBinding(String name) {
        return name != null && (name.contains("regulatory") || name.contains("ind"));
    }

    static int ordinal(AeEscalationStatus status) {
        return switch (status) {
            case NONE -> 0;
            case REQUESTED -> 1;
            case COMPLETED -> 2;
            case FAILED -> 3;
        };
    }

    static int ordinal(SusarOversightStatus status) {
        return switch (status) {
            case NONE -> 0;
            case REQUESTED -> 1;
            case COMPLETED -> 2;
            case FAILED -> 3;
        };
    }

    static int ordinal(RegulatorySubmissionStatus status) {
        return switch (status) {
            case NONE -> 0;
            case PENDING -> 1;
            case FILED -> 2;
            case DEADLINE_MISSED -> 3;
        };
    }

    private static class Observation {
        long secondsSinceReport;
        int  escalation;
        int  susar;
        int  regulatory;
        int  grade;

        Observation(long secondsSinceReport, int escalation, int susar, int regulatory, int grade) {
            this.secondsSinceReport = secondsSinceReport;
            this.escalation         = escalation;
            this.susar              = susar;
            this.regulatory         = regulatory;
            this.grade              = grade;
        }

        Map<String, FeatureValue> toFeatureMap() {
            return Map.of(
                    "ts", FeatureValue.number(secondsSinceReport),
                    "escalation", FeatureValue.number(escalation),
                    "susar", FeatureValue.number(susar),
                    "regulatory", FeatureValue.number(regulatory),
                    "grade", FeatureValue.number(grade));
        }
    }
}
