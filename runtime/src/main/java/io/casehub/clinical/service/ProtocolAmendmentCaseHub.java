package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ProtocolAmendmentCaseHub extends YamlCaseHub {

    @Inject
    ProtocolAmendmentAdvisor advisor;
    @Inject
    EntityManager em;


    public ProtocolAmendmentCaseHub() {super("clinical/protocol-amendment.yaml");}

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.builder()
                                          .name("protocol-amendment-advisor-worker")
                                          .capabilityName("protocol-amendment-advisor")
                                          .function((Map<String, Object> ctx) -> {
                                              String amendmentIdStr = (String) ctx.get("amendmentId");
                                              String trialIdStr     = (String) ctx.get("trialId");
                                              if (amendmentIdStr == null || trialIdStr == null) {
                                                  return WorkerResult.failed("protocol-amendment-advisor: missing context keys (amendmentId, trialId)");
                                              }
                                              UUID                trialId  = UUID.fromString(trialIdStr);
                                              Map<String, Object> snapshot = buildTrialSnapshot(trialId);
                                              ProtocolAmendmentContext pac = new ProtocolAmendmentContext(
                                                      UUID.fromString(amendmentIdStr), trialId,
                                                      (String) ctx.get("proposedChange"), snapshot);
                                              AmendmentRecommendation rec = advisor.advise(pac);
                                              return WorkerResult.of(Map.of("advisorRecommendation", rec.name()));
                                          })
                                          .build());
    }

    private Map<String, Object> buildTrialSnapshot(UUID trialId) {
        return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> buildTrialSnapshotInTx(trialId));
    }

    private Map<String, Object> buildTrialSnapshotInTx(UUID trialId) {
        java.util.HashMap<String, Object>        snapshot = new java.util.HashMap<>();
        io.casehub.clinical.entity.ClinicalTrial trial    = em.find(io.casehub.clinical.entity.ClinicalTrial.class, trialId);
        if (trial == null) {return snapshot;}
        snapshot.put("trialPhase", trial.phase != null ? trial.phase.name() : "UNKNOWN");
        snapshot.put("trialStatus", trial.status != null ? trial.status.name() : "UNKNOWN");
        snapshot.put("sponsor", trial.sponsor);
        long totalAes = em.createQuery("SELECT COUNT(a) FROM AdverseEvent a WHERE a.enrollmentId IN (SELECT p.id FROM PatientEnrollment p WHERE p.siteId IN (SELECT s.id FROM TrialSite s WHERE s.trialId = :trialId))", Long.class)
                .setParameter("trialId", trialId).getSingleResult();
        snapshot.put("totalAdverseEvents", totalAes);
        long grade3Plus = em.createQuery("SELECT COUNT(a) FROM AdverseEvent a WHERE a.grade IN (:g3, :g4, :g5) AND a.enrollmentId IN (SELECT p.id FROM PatientEnrollment p WHERE p.siteId IN (SELECT s.id FROM TrialSite s WHERE s.trialId = :trialId))", Long.class)
                .setParameter("g3", io.casehub.clinical.api.model.CtcaeGrade.GRADE_3).setParameter("g4", io.casehub.clinical.api.model.CtcaeGrade.GRADE_4).setParameter("g5", io.casehub.clinical.api.model.CtcaeGrade.GRADE_5).setParameter("trialId", trialId).getSingleResult();
        snapshot.put("grade3PlusCount", grade3Plus);
        boolean hasGrade5 = em.createQuery("SELECT COUNT(a) FROM AdverseEvent a WHERE a.grade = :grade AND a.enrollmentId IN (SELECT p.id FROM PatientEnrollment p WHERE p.siteId IN (SELECT s.id FROM TrialSite s WHERE s.trialId = :trialId))", Long.class)
                .setParameter("grade", io.casehub.clinical.api.model.CtcaeGrade.GRADE_5).setParameter("trialId", trialId).getSingleResult() > 0;
        snapshot.put("hasGrade5", hasGrade5);
        snapshot.put("priorAmendmentCount", em.createNamedQuery("ProtocolAmendment.findByTrialId", io.casehub.clinical.entity.ProtocolAmendment.class).setParameter("trialId", trialId).getResultList().size());
        return snapshot;
    }
}
