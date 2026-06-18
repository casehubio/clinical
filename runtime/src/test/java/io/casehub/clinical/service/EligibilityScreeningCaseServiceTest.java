package io.casehub.clinical.service;

// Phase 1 (prepareAndMark) calls PatientEnrollment.findById() inside @Transactional,
// which is incompatible with @InjectMocks (no JPA session). Coverage lives in
// EligibilityScreeningIntegrationTest (@QuarkusTest) instead.
class EligibilityScreeningCaseServiceTest {
}
