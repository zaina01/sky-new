package com.sky.job.system;

import com.sky.job.system.dto.DepartmentResponse;
import com.sky.job.system.dto.UpdateDepartmentStateRequest;
import com.sky.job.system.dto.UpsertDepartmentRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/departments")
public class SystemDepartmentController {
    private final SystemDepartmentService service;

    public SystemDepartmentController(SystemDepartmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DepartmentResponse> tree(@RequestParam(required = false) String departmentName, @RequestParam(required = false) Boolean enabled) {
        return service.tree(departmentName, enabled);
    }

    @GetMapping("/{id}")
    public DepartmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public DepartmentResponse create(@RequestBody UpsertDepartmentRequest request, HttpServletRequest httpRequest) {
        return service.create(request, httpRequest);
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable Long id, @RequestBody UpsertDepartmentRequest request, HttpServletRequest httpRequest) {
        return service.update(id, request, httpRequest);
    }

    @PatchMapping("/{id}/enabled")
    public DepartmentResponse updateEnabled(@PathVariable Long id, @RequestBody UpdateDepartmentStateRequest request, HttpServletRequest httpRequest) {
        return service.updateEnabled(id, request.enabled(), httpRequest);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        service.delete(id, httpRequest);
        return Map.of("success", true);
    }
}
