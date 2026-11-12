package com.sky.job.security;

import com.sky.job.system.mapper.DepartmentMapper;
import com.sky.job.system.mapper.RoleMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DataScopeService {
    private static final String ADMIN_ROLE = "ADMIN";

    private final RoleMapper roleMapper;
    private final DepartmentMapper departmentMapper;

    public DataScopeService(RoleMapper roleMapper, DepartmentMapper departmentMapper) {
        this.roleMapper = roleMapper;
        this.departmentMapper = departmentMapper;
    }

    public EffectiveDataScope resolve(AuthenticatedUser user, String permissionName) {
        if (user == null) {
            return EffectiveDataScope.denied(null);
        }
        if (user.roles().contains(ADMIN_ROLE)) {
            return EffectiveDataScope.all(user.userId());
        }
        List<RoleDataScopeGrant> grants = roleMapper.dataScopeGrants(user.userId(), permissionName);
        if (grants.stream().anyMatch(grant -> grant.dataScope() == DataScopeType.ALL)) {
            return EffectiveDataScope.all(user.userId());
        }

        boolean self = grants.stream().anyMatch(grant -> grant.dataScope() == DataScopeType.SELF);
        Set<Long> departmentIds = new HashSet<>();
        if (user.departmentId() != null && grants.stream().anyMatch(grant -> grant.dataScope() == DataScopeType.DEPARTMENT)) {
            departmentIds.add(user.departmentId());
        }
        if (user.departmentId() != null && grants.stream().anyMatch(grant -> grant.dataScope() == DataScopeType.DEPARTMENT_AND_CHILDREN)) {
            departmentIds.addAll(departmentMapper.descendantIds(user.departmentId()));
        }
        if (grants.stream().anyMatch(grant -> grant.dataScope() == DataScopeType.CUSTOM)) {
            departmentIds.addAll(roleMapper.customDepartmentIds(user.userId(), permissionName));
        }
        return new EffectiveDataScope(user.userId(), false, self, departmentIds);
    }

    public boolean allows(EffectiveDataScope scope, String ownerUserId, Long ownerDepartmentId) {
        if (scope == null || scope.denied()) return false;
        if (scope.all()) return true;
        return (scope.self() && scope.userId() != null && scope.userId().equals(ownerUserId))
                || (ownerDepartmentId != null && scope.departmentIds().contains(ownerDepartmentId));
    }
}
