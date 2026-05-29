package io.casehub.clinical.api.spi;

/**
 * Maps deviation context to an IRB committee assignment.
 * Mirrors {@link DeviationResponsePolicy} — implement as
 * {@code @ApplicationScoped @Alternative @Priority(1)} to override the default.
 */
@FunctionalInterface
public interface IrbCommitteeAssignmentPolicy {
    IrbCommitteeAssignment evaluate(IrbCommitteeContext context);
}
