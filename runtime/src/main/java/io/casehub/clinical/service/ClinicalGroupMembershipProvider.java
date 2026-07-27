package io.casehub.clinical.service;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * Production GroupMembershipProvider resolving CDI ambiguity between
 * MockGroupMembershipProvider (casehub-platform, @DefaultBean) and
 * NoOpGroupMembershipProvider (casehub-work, @DefaultBean).
 * This non-@DefaultBean bean takes CDI priority and suppresses both.
 * Returns empty set — placeholder until real group membership is wired.
 *
 * <p>See casehubio/clinical#55.
 */
@ApplicationScoped
public class ClinicalGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        return Set.of();
    }
}
