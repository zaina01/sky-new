package com.sky.job.system;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.notification.NotificationChannel;
import com.sky.job.notification.NotificationChannelStore;
import com.sky.job.notification.NotificationRule;
import com.sky.job.notification.NotificationRuleStore;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.ScheduledJob;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.system.dto.DataOwnershipTransferRequest;
import com.sky.job.system.dto.DataOwnershipTransferResponse;
import com.sky.job.system.mapper.DataOwnershipMapper;
import com.sky.job.user.User;
import com.sky.job.user.UserStore;
import com.sky.job.web.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DataOwnershipTransferService {
    private final UserStore userStore;
    private final PluginRuntimeMetadataStore runtimeStore;
    private final JobStore jobStore;
    private final NotificationChannelStore channelStore;
    private final NotificationRuleStore ruleStore;
    private final DataOwnershipMapper ownershipMapper;
    private final DataScopeService dataScopeService;
    private final SystemLogStore systemLogStore;

    public DataOwnershipTransferService(
            UserStore userStore,
            PluginRuntimeMetadataStore runtimeStore,
            JobStore jobStore,
            NotificationChannelStore channelStore,
            NotificationRuleStore ruleStore,
            DataOwnershipMapper ownershipMapper,
            DataScopeService dataScopeService,
            SystemLogStore systemLogStore
    ) {
        this.userStore = userStore;
        this.runtimeStore = runtimeStore;
        this.jobStore = jobStore;
        this.channelStore = channelStore;
        this.ruleStore = ruleStore;
        this.ownershipMapper = ownershipMapper;
        this.dataScopeService = dataScopeService;
        this.systemLogStore = systemLogStore;
    }

    @Transactional
    public DataOwnershipTransferResponse transfer(
            DataOwnershipTransferRequest request,
            AuthenticatedUser actor,
            HttpServletRequest httpRequest
    ) {
        if (request == null || request.targetUserId() == null || request.targetUserId().isBlank()) {
            throw new IllegalArgumentException("Target user is required");
        }
        User target = userStore.findByUserId(request.targetUserId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));
        if (!target.enabled() || !target.departmentEnabled()) {
            throw new IllegalArgumentException("Target user and department must be enabled");
        }
        if (target.departmentId() == null) {
            throw new IllegalArgumentException("Target user has no primary department");
        }
        if (request.targetDepartmentId() != null && !request.targetDepartmentId().equals(target.departmentId())) {
            throw new IllegalArgumentException("Target department must be the target user's primary department");
        }

        Set<String> selectedInstallations = stringIds(request.pluginInstallationIds(), "plugin installation id");
        Set<Long> selectedJobs = longIds(request.jobIds(), "job id");
        Set<String> selectedChannels = stringIds(request.notificationChannelIds(), "notification channel id");
        Set<String> selectedRules = stringIds(request.notificationRuleIds(), "notification rule id");
        if (selectedInstallations.isEmpty() && selectedJobs.isEmpty() && selectedChannels.isEmpty() && selectedRules.isEmpty()) {
            throw new IllegalArgumentException("At least one resource must be selected for transfer");
        }

        EffectiveDataScope actorScope = dataScopeService.resolve(actor, PermissionNames.SYSTEM_DATA_OWNERSHIP_TRANSFER);
        Map<String, PluginRuntimeRecord> selectedInstallationRecords = loadInstallations(selectedInstallations, true);
        Map<Long, ScheduledJob> selectedJobRecords = loadJobs(selectedJobs, true);
        Map<String, NotificationChannel> selectedChannelRecords = loadChannels(selectedChannels, true);
        Map<String, NotificationRule> selectedRuleRecords = loadRules(selectedRules, true);
        requireActorScope(actorScope, selectedInstallationRecords.values(), selectedJobRecords.values(), selectedChannelRecords.values(), selectedRuleRecords.values());

        Set<String> requiredInstallations = new LinkedHashSet<>(selectedInstallations);
        Set<Long> requiredJobs = new LinkedHashSet<>(selectedJobs);
        Set<String> requiredChannels = new LinkedHashSet<>(selectedChannels);
        Set<String> requiredRules = new LinkedHashSet<>(selectedRules);

        for (String installationId : selectedInstallations) {
            requiredJobs.addAll(ownershipMapper.jobIdsByInstallationId(installationId));
            requiredChannels.addAll(runtimeStore.notificationChannelIds(installationId));
            requiredRules.addAll(ownershipMapper.ruleIdsByInstallationId(installationId));
            requiredInstallations.addAll(ownershipMapper.servicePeerInstallationIds(installationId));
        }
        for (Long jobId : selectedJobs) {
            ScheduledJob job = selectedJobRecords.get(jobId);
            requiredInstallations.add(job.pluginInstallationId());
            requiredChannels.addAll(jobStore.notificationChannelIds(jobId));
            requiredRules.addAll(ownershipMapper.ruleIdsByJobId(jobId));
        }
        for (String channelId : selectedChannels) {
            requiredInstallations.addAll(ownershipMapper.installationIdsByChannelId(channelId));
            requiredJobs.addAll(ownershipMapper.jobIdsByChannelId(channelId));
            requiredRules.addAll(ownershipMapper.ruleIdsByChannelId(channelId));
        }
        for (NotificationRule rule : selectedRuleRecords.values()) {
            addRuleLinks(rule, requiredInstallations, requiredJobs, requiredChannels);
        }

        // Jobs and rules pulled in as direct links contribute their own target
        // resources, but unrelated reverse bindings are intentionally not traversed.
        Map<Long, ScheduledJob> requiredJobRecords = loadJobs(requiredJobs, false);
        for (ScheduledJob job : requiredJobRecords.values()) {
            requiredInstallations.add(job.pluginInstallationId());
            requiredChannels.addAll(jobStore.notificationChannelIds(job.id()));
        }
        Map<String, NotificationRule> requiredRuleRecords = loadRules(requiredRules, false);
        for (NotificationRule rule : requiredRuleRecords.values()) {
            addRuleLinks(rule, requiredInstallations, requiredJobs, requiredChannels);
        }

        requiredJobRecords = loadJobs(requiredJobs, false);
        for (ScheduledJob job : requiredJobRecords.values()) {
            requiredInstallations.add(job.pluginInstallationId());
            requiredChannels.addAll(jobStore.notificationChannelIds(job.id()));
        }
        Map<String, PluginRuntimeRecord> requiredInstallationRecords = loadInstallations(requiredInstallations, false);
        Map<String, NotificationChannel> requiredChannelRecords = loadChannels(requiredChannels, false);
        requiredRuleRecords = loadRules(requiredRules, false);

        AuthenticatedUser targetPrincipal = AuthenticatedUser.from(target, userStore.roles(target.userId()), userStore.permissions(target.userId()));
        validateTargetVisibility(
                targetPrincipal,
                target,
                selectedInstallations,
                selectedJobs,
                selectedChannels,
                selectedRules,
                requiredInstallationRecords,
                requiredJobRecords,
                requiredChannelRecords,
                requiredRuleRecords
        );

        String targetUserId = target.userId();
        Long targetDepartmentId = target.departmentId();
        for (String installationId : selectedInstallations) {
            if (runtimeStore.transferOwnership(installationId, targetUserId, targetDepartmentId) == 0) {
                throw new ResourceNotFoundException("Plugin installation not found");
            }
        }
        for (Long jobId : selectedJobs) {
            if (jobStore.transferOwnership(jobId, targetUserId, targetDepartmentId, actor.username()) == 0) {
                throw new ResourceNotFoundException("Job not found");
            }
        }
        for (String channelId : selectedChannels) {
            if (channelStore.transferOwnership(channelId, targetUserId, targetDepartmentId, actor.username()) == 0) {
                throw new ResourceNotFoundException("Notification channel not found");
            }
        }
        for (String ruleId : selectedRules) {
            if (ruleStore.transferOwnership(ruleId, targetUserId, targetDepartmentId, actor.username()) == 0) {
                throw new ResourceNotFoundException("Notification rule not found");
            }
        }

        systemLogStore.record(
                "DATA_OWNERSHIP",
                "TRANSFER",
                "INFO",
                "SUCCESS",
                "USER",
                targetUserId,
                "Transferred ownership: installations=" + selectedInstallations.size()
                        + ", jobs=" + selectedJobs.size()
                        + ", channels=" + selectedChannels.size()
                        + ", rules=" + selectedRules.size(),
                httpRequest
        );
        return new DataOwnershipTransferResponse(
                targetUserId,
                target.username(),
                targetDepartmentId,
                target.departmentName(),
                selectedInstallations.size(),
                selectedJobs.size(),
                selectedChannels.size(),
                selectedRules.size()
        );
    }

    private void validateTargetVisibility(
            AuthenticatedUser targetPrincipal,
            User target,
            Set<String> selectedInstallations,
            Set<Long> selectedJobs,
            Set<String> selectedChannels,
            Set<String> selectedRules,
            Map<String, PluginRuntimeRecord> installations,
            Map<Long, ScheduledJob> jobs,
            Map<String, NotificationChannel> channels,
            Map<String, NotificationRule> rules
    ) {
        EffectiveDataScope installationScope = dataScopeService.resolve(targetPrincipal, PermissionNames.PLUGIN_RUNTIME_READ);
        EffectiveDataScope jobScope = dataScopeService.resolve(targetPrincipal, PermissionNames.JOB_READ);
        EffectiveDataScope channelScope = dataScopeService.resolve(targetPrincipal, PermissionNames.NOTIFICATION_CHANNEL_READ);
        EffectiveDataScope ruleScope = dataScopeService.resolve(targetPrincipal, PermissionNames.NOTIFICATION_RULE_READ);

        installations.forEach((id, resource) -> requireTargetVisibility(
                "plugin installation", id, installationScope,
                selectedInstallations.contains(id) ? target.userId() : resource.ownerUserId(),
                selectedInstallations.contains(id) ? target.departmentId() : resource.ownerDepartmentId()));
        jobs.forEach((id, resource) -> requireTargetVisibility(
                "job", Long.toString(id), jobScope,
                selectedJobs.contains(id) ? target.userId() : resource.ownerUserId(),
                selectedJobs.contains(id) ? target.departmentId() : resource.ownerDepartmentId()));
        channels.forEach((id, resource) -> requireTargetVisibility(
                "notification channel", id, channelScope,
                selectedChannels.contains(id) ? target.userId() : resource.ownerUserId(),
                selectedChannels.contains(id) ? target.departmentId() : resource.ownerDepartmentId()));
        rules.forEach((id, resource) -> requireTargetVisibility(
                "notification rule", id, ruleScope,
                selectedRules.contains(id) ? target.userId() : resource.ownerUserId(),
                selectedRules.contains(id) ? target.departmentId() : resource.ownerDepartmentId()));
    }

    private void requireTargetVisibility(String type, String id, EffectiveDataScope scope, String ownerUserId, Long ownerDepartmentId) {
        if (!dataScopeService.allows(scope, ownerUserId, ownerDepartmentId)) {
            throw new IllegalArgumentException("Target user cannot access linked " + type + ": " + id + "; transfer it together or remove the binding first");
        }
    }

    private void requireActorScope(
            EffectiveDataScope scope,
            Iterable<PluginRuntimeRecord> installations,
            Iterable<ScheduledJob> jobs,
            Iterable<NotificationChannel> channels,
            Iterable<NotificationRule> rules
    ) {
        for (PluginRuntimeRecord resource : installations) {
            requireActorResource(scope, resource.ownerUserId(), resource.ownerDepartmentId(), "Plugin installation not found");
        }
        for (ScheduledJob resource : jobs) {
            requireActorResource(scope, resource.ownerUserId(), resource.ownerDepartmentId(), "Job not found");
        }
        for (NotificationChannel resource : channels) {
            requireActorResource(scope, resource.ownerUserId(), resource.ownerDepartmentId(), "Notification channel not found");
        }
        for (NotificationRule resource : rules) {
            requireActorResource(scope, resource.ownerUserId(), resource.ownerDepartmentId(), "Notification rule not found");
        }
    }

    private void requireActorResource(EffectiveDataScope scope, String ownerUserId, Long ownerDepartmentId, String message) {
        if (!dataScopeService.allows(scope, ownerUserId, ownerDepartmentId)) {
            throw new ResourceNotFoundException(message);
        }
    }

    private void addRuleLinks(NotificationRule rule, Set<String> installations, Set<Long> jobs, Set<String> channels) {
        if (rule.pluginInstallationId() != null && !rule.pluginInstallationId().isBlank()) {
            installations.add(rule.pluginInstallationId());
        }
        if (rule.jobId() != null && !rule.jobId().isBlank()) {
            try {
                jobs.add(Long.parseLong(rule.jobId()));
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Notification rule contains an invalid job id: " + rule.ruleId(), exception);
            }
        }
        channels.addAll(rule.channelIds());
    }

    private Map<String, PluginRuntimeRecord> loadInstallations(Set<String> ids, boolean selected) {
        Map<String, PluginRuntimeRecord> resources = new LinkedHashMap<>();
        for (String id : ids) {
            PluginRuntimeRecord resource = runtimeStore.findRuntimeByInstallationId(id)
                    .orElseThrow(() -> missing(selected, "Plugin installation not found: " + id));
            resources.put(id, resource);
        }
        return resources;
    }

    private Map<Long, ScheduledJob> loadJobs(Set<Long> ids, boolean selected) {
        Map<Long, ScheduledJob> resources = new LinkedHashMap<>();
        for (Long id : ids) {
            ScheduledJob resource = jobStore.findByIdUnscoped(id)
                    .orElseThrow(() -> missing(selected, "Job not found: " + id));
            resources.put(id, resource);
        }
        return resources;
    }

    private Map<String, NotificationChannel> loadChannels(Set<String> ids, boolean selected) {
        Map<String, NotificationChannel> resources = new LinkedHashMap<>();
        for (String id : ids) {
            NotificationChannel resource = channelStore.findByChannelIdUnscoped(id)
                    .orElseThrow(() -> missing(selected, "Notification channel not found: " + id));
            resources.put(id, resource);
        }
        return resources;
    }

    private Map<String, NotificationRule> loadRules(Set<String> ids, boolean selected) {
        Map<String, NotificationRule> resources = new LinkedHashMap<>();
        for (String id : ids) {
            NotificationRule resource = ruleStore.findByRuleIdUnscoped(id)
                    .orElseThrow(() -> missing(selected, "Notification rule not found: " + id));
            resources.put(id, resource);
        }
        return resources;
    }

    private RuntimeException missing(boolean selected, String message) {
        return selected ? new ResourceNotFoundException(message) : new IllegalStateException("Linked resource is missing: " + message);
    }

    private Set<String> stringIds(List<String> values, String label) {
        Set<String> ids = new LinkedHashSet<>();
        if (values == null) return ids;
        for (String value : values) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Invalid " + label);
            ids.add(value.trim());
        }
        return ids;
    }

    private Set<Long> longIds(List<Long> values, String label) {
        Set<Long> ids = new LinkedHashSet<>();
        if (values == null) return ids;
        for (Long value : values) {
            if (value == null || value <= 0) throw new IllegalArgumentException("Invalid " + label);
            ids.add(value);
        }
        return ids;
    }
}
