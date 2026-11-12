package com.sky.job.security;

import java.util.Set;

public record EffectiveDataScope(
        String userId,
        boolean all,
        boolean self,
        Set<Long> departmentIds
) {
    public EffectiveDataScope {
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
    }

    public static EffectiveDataScope all(String userId) {
        return new EffectiveDataScope(userId, true, true, Set.of());
    }

    public static EffectiveDataScope denied(String userId) {
        return new EffectiveDataScope(userId, false, false, Set.of());
    }

    public boolean denied() {
        return !all && !self && departmentIds.isEmpty();
    }
}
