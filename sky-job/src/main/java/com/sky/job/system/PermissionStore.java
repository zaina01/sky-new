package com.sky.job.system;

import com.sky.job.system.mapper.PermissionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class PermissionStore {
    private final PermissionMapper mapper;

    public PermissionStore(PermissionMapper mapper) {
        this.mapper = mapper;
    }

    public List<Permission> list(String permissionName, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(trim(permissionName), like(permissionName), normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(String permissionName) {
        return mapper.count(trim(permissionName), like(permissionName));
    }

    public Set<String> existingNames(Set<String> permissionNames) {
        if (permissionNames.isEmpty()) {
            return Set.of();
        }
        return mapper.existingNames(permissionNames).stream().collect(Collectors.toUnmodifiableSet());
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
