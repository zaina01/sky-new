package com.sky.job.system;

import com.sky.job.system.mapper.RoleMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RoleStore {
    private final RoleMapper mapper;

    public RoleStore(RoleMapper mapper) {
        this.mapper = mapper;
    }

    public List<Role> list(String roleName, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(trim(roleName), like(roleName), normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(String roleName) {
        return mapper.count(trim(roleName), like(roleName));
    }

    public Optional<Role> findById(Long roleId) {
        return Optional.ofNullable(mapper.findById(roleId));
    }

    public Optional<Role> findByName(String roleName) {
        return Optional.ofNullable(mapper.findByName(roleName));
    }

    public boolean existsByName(String roleName) {
        return mapper.countByName(roleName) > 0;
    }

    public boolean existsByNameForOtherRole(String roleName, Long roleId) {
        return mapper.countByNameForOtherRole(roleName, roleId) > 0;
    }

    public long insert(String roleName, String description) {
        Map<String, Object> values = new HashMap<>();
        values.put("roleName", roleName);
        values.put("description", description);
        mapper.insert(values);
        Object roleId = values.get("roleId");
        if (roleId instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Role was not saved");
    }

    public int update(Long roleId, String roleName, String description) {
        return mapper.update(roleId, roleName, description);
    }

    public int delete(Long roleId) {
        mapper.deleteRolePermissions(roleId);
        mapper.deleteRoleDepartments(roleId);
        return mapper.delete(roleId);
    }

    public Set<String> permissions(Long roleId) {
        return mapper.permissions(roleId).stream().collect(Collectors.toUnmodifiableSet());
    }

    public void replacePermissions(Long roleId, Set<String> permissionNames) {
        mapper.deleteRolePermissions(roleId);
        for (String permissionName : permissionNames) {
            mapper.assignPermission(roleId, permissionName);
        }
    }

    public boolean isAssigned(Long roleId) {
        return mapper.countAssigned(roleId) > 0;
    }

    public Set<Long> departmentIds(Long roleId) {
        return mapper.departmentIds(roleId).stream().collect(Collectors.toUnmodifiableSet());
    }

    public void replaceDataScope(Long roleId, com.sky.job.security.DataScopeType dataScope, Set<Long> departmentIds) {
        mapper.updateDataScope(roleId, dataScope.name());
        mapper.deleteRoleDepartments(roleId);
        for (Long departmentId : departmentIds) mapper.assignDepartment(roleId, departmentId);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
