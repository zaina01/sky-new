package com.sky.job.system;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.system.dto.DepartmentResponse;
import com.sky.job.system.dto.UpsertDepartmentRequest;
import com.sky.job.web.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemDepartmentService {
    private static final long ROOT_DEPARTMENT_ID = 1L;

    private final DepartmentStore store;
    private final SystemLogStore systemLogStore;

    public SystemDepartmentService(DepartmentStore store, SystemLogStore systemLogStore) {
        this.store = store;
        this.systemLogStore = systemLogStore;
    }

    public List<DepartmentResponse> tree(String name, Boolean enabled) {
        List<Department> departments = store.list(name, enabled);
        Map<Long, List<Department>> children = new LinkedHashMap<>();
        for (Department department : departments) {
            children.computeIfAbsent(department.parentId(), ignored -> new ArrayList<>()).add(department);
        }
        return build(children, null);
    }

    public DepartmentResponse get(Long id) {
        Department department = requireDepartment(id);
        return DepartmentResponse.from(department, List.of());
    }

    @Transactional
    public DepartmentResponse create(UpsertDepartmentRequest request, HttpServletRequest httpRequest) {
        String name = required(request == null ? null : request.departmentName());
        Long parentId = normalizeParent(request == null ? null : request.parentId());
        requireParent(parentId);
        if (store.nameExists(parentId, name)) throw new IllegalArgumentException("Department name already exists under the parent");
        long id = store.insert(parentId, name, sortOrder(request), enabled(request));
        systemLogStore.record("SYSTEM_DEPARTMENT", "CREATE_DEPARTMENT", "INFO", "SUCCESS", "DEPARTMENT", Long.toString(id), "Department created", httpRequest);
        return get(id);
    }

    @Transactional
    public DepartmentResponse update(Long id, UpsertDepartmentRequest request, HttpServletRequest httpRequest) {
        requireDepartment(id);
        String name = required(request == null ? null : request.departmentName());
        Long parentId = normalizeParent(request == null ? null : request.parentId());
        if (id == ROOT_DEPARTMENT_ID && parentId != null) throw new IllegalStateException("Root department cannot have a parent");
        requireParent(parentId);
        if (parentId != null && (parentId.equals(id) || store.descendantIds(id).contains(parentId))) {
            throw new IllegalArgumentException("Department hierarchy cannot contain a cycle");
        }
        if (store.nameExistsForOther(parentId, name, id)) throw new IllegalArgumentException("Department name already exists under the parent");
        if (store.update(id, parentId, name, sortOrder(request), enabled(request)) == 0) throw new ResourceNotFoundException("Department not found");
        systemLogStore.record("SYSTEM_DEPARTMENT", "UPDATE_DEPARTMENT", "INFO", "SUCCESS", "DEPARTMENT", Long.toString(id), "Department updated", httpRequest);
        return get(id);
    }

    public DepartmentResponse updateEnabled(Long id, boolean enabled, HttpServletRequest httpRequest) {
        requireDepartment(id);
        if (!enabled && store.hasUsers(id)) throw new IllegalStateException("Cannot disable a department with users");
        if (store.updateEnabled(id, enabled) == 0) throw new ResourceNotFoundException("Department not found");
        systemLogStore.record("SYSTEM_DEPARTMENT", enabled ? "ENABLE_DEPARTMENT" : "DISABLE_DEPARTMENT", "INFO", "SUCCESS", "DEPARTMENT", Long.toString(id), "Department state updated", httpRequest);
        return get(id);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        requireDepartment(id);
        if (id == ROOT_DEPARTMENT_ID) throw new IllegalStateException("Root department cannot be deleted");
        if (store.hasChildren(id)) throw new IllegalStateException("Cannot delete a department with child departments");
        if (store.hasUsers(id)) throw new IllegalStateException("Cannot delete a department with users");
        if (store.softDelete(id) == 0) throw new ResourceNotFoundException("Department not found");
        systemLogStore.record("SYSTEM_DEPARTMENT", "DELETE_DEPARTMENT", "INFO", "SUCCESS", "DEPARTMENT", Long.toString(id), "Department deleted", httpRequest);
    }

    private List<DepartmentResponse> build(Map<Long, List<Department>> children, Long parentId) {
        return children.getOrDefault(parentId, List.of()).stream()
                .map(department -> DepartmentResponse.from(department, build(children, department.departmentId())))
                .toList();
    }

    private Department requireDepartment(Long id) {
        return store.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }

    private void requireParent(Long parentId) {
        if (parentId != null && store.findById(parentId).isEmpty()) throw new ResourceNotFoundException("Parent department not found");
    }

    private Long normalizeParent(Long parentId) {
        return parentId == null || parentId == 0 ? null : parentId;
    }

    private int sortOrder(UpsertDepartmentRequest request) {
        return request == null || request.sortOrder() == null ? 0 : Math.max(request.sortOrder(), 0);
    }

    private boolean enabled(UpsertDepartmentRequest request) {
        return request == null || request.enabled() == null || request.enabled();
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Invalid department name");
        return value.trim();
    }
}
