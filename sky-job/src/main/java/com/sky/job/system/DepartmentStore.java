package com.sky.job.system;

import com.sky.job.system.mapper.DepartmentMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DepartmentStore {
    private final DepartmentMapper mapper;

    public DepartmentStore(DepartmentMapper mapper) {
        this.mapper = mapper;
    }

    public List<Department> list(String name, Boolean enabled) {
        return mapper.list(trim(name), like(name), enabled);
    }

    public Optional<Department> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public List<Long> descendantIds(Long id) {
        return mapper.descendantIds(id);
    }

    public boolean nameExists(Long parentId, String name) {
        return mapper.countByName(parentId, name) > 0;
    }

    public boolean nameExistsForOther(Long parentId, String name, Long id) {
        return mapper.countByNameForOther(parentId, name, id) > 0;
    }

    public long insert(Long parentId, String name, int sortOrder, boolean enabled) {
        Map<String, Object> values = new HashMap<>();
        values.put("parentId", parentId);
        values.put("departmentName", name);
        values.put("sortOrder", sortOrder);
        values.put("enabled", enabled);
        mapper.insert(values);
        Object id = values.get("departmentId");
        if (id instanceof Number number) return number.longValue();
        throw new IllegalStateException("Department was not saved");
    }

    public int update(Long id, Long parentId, String name, int sortOrder, boolean enabled) {
        return mapper.update(id, parentId, name, sortOrder, enabled);
    }

    public int updateEnabled(Long id, boolean enabled) {
        return mapper.updateEnabled(id, enabled);
    }

    public boolean hasChildren(Long id) {
        return mapper.countChildren(id) > 0;
    }

    public boolean hasUsers(Long id) {
        return mapper.countUsers(id) > 0;
    }

    public int softDelete(Long id) {
        return mapper.softDelete(id);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }
}
