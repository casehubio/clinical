package io.casehub.clinical.api.view;

import java.time.Instant;

public record DashboardGradeChangeView(String previousGrade, String newGrade, Instant changedAt, String changedBy) {}
