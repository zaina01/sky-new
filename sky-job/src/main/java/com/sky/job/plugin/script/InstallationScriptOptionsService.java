package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.*;
import com.sky.job.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import java.util.*;

@Service
public class InstallationScriptOptionsService {
    private final InstallationScriptOptionsStore store;
    private final PluginRuntimeMetadataStore runtimes;
    private final DataScopeService scopes;
    private final PluginAuditStore audit;
    private final ObjectMapper json;
    public InstallationScriptOptionsService(InstallationScriptOptionsStore store,PluginRuntimeMetadataStore runtimes,DataScopeService scopes,PluginAuditStore audit,ObjectMapper json) {
        this.store=store;this.runtimes=runtimes;this.scopes=scopes;this.audit=audit;this.json=json;
    }
    private PluginRuntimeRecord require(String id,AuthenticatedUser user,boolean write) {
        String permission=write?PermissionNames.PLUGIN_CONFIG_WRITE:PermissionNames.PLUGIN_CONFIG_READ;
        if(!user.roles().contains("ADMIN")&&!user.permissions().contains(permission))throw new org.springframework.security.access.AccessDeniedException("Missing permission: "+permission);
        var runtime=runtimes.findRuntimeByInstallationId(id).orElseThrow(()->new ResourceNotFoundException("Plugin installation not found"));
        if(!scopes.allows(scopes.resolve(user,permission),runtime.ownerUserId(),runtime.ownerDepartmentId()))throw new ResourceNotFoundException("Plugin installation not found");
        if(!"standalone".equals(runtime.executionMode()))throw new IllegalArgumentException("Instance script options are available for standalone scripts only");
        return runtime;
    }
    public PageResponse<ObjectNode> list(String id,String keyword,Boolean enabled,Integer page,Integer size,AuthenticatedUser user) {
        require(id,user,false);return store.list(id,keyword,enabled,page,size);
    }
    public ObjectNode arguments(String id,AuthenticatedUser user){require(id,user,false);return store.arguments(id);}
    public ObjectNode environmentSettings(String id,AuthenticatedUser user){require(id,user,false);return store.environmentSettings(id);}
    @Transactional
    public ObjectNode saveEnvironmentSettings(String id,JsonNode request,AuthenticatedUser user) {
        var runtime=require(id,user,true);var result=store.saveEnvironmentSettings(id,request,user.username());
        log(runtime,user,"SCRIPT_ENVIRONMENT_SETTINGS_UPDATE",json.createObjectNode()
                .put("overrideCount",result.path("overrides").size()).put("revision",result.path("revision").asText()));
        return result;
    }
    @Transactional
    public ObjectNode saveArguments(String id,JsonNode arguments,AuthenticatedUser user) {
        var runtime=require(id,user,true);var result=store.saveArguments(id,arguments,user.username());
        log(runtime,user,"SCRIPT_ARGUMENTS_UPDATE",json.createObjectNode().put("count",arguments.size()).put("revision",result.path("revision").asText()));return result;
    }
    @Transactional
    public ObjectNode saveVariable(String id,String variableId,JsonNode request,AuthenticatedUser user) {
        var runtime=require(id,user,true);var result=store.saveVariable(id,variableId,request,user.username());
        log(runtime,user,variableId==null?"SCRIPT_VARIABLE_CREATE":"SCRIPT_VARIABLE_UPDATE",json.createObjectNode().put("id",result.path("id").asText()).put("name",result.path("name").asText()));return result;
    }
    @Transactional
    public Map<String,Integer> batch(String id,List<String> ids,Boolean enabled,boolean delete,AuthenticatedUser user) {
        var runtime=require(id,user,true);var affected=store.batch(id,ids,enabled,delete,user.username());
        var detail=json.createObjectNode();detail.set("variables",json.valueToTree(affected));if(!delete)detail.put("enabled",enabled);
        log(runtime,user,delete?"SCRIPT_VARIABLE_DELETE":"SCRIPT_VARIABLE_ENABLED",detail);return Map.of("changed",affected.size());
    }
    private void log(PluginRuntimeRecord runtime,AuthenticatedUser user,String action,JsonNode detail) {
        audit.insert(runtime.installationId(),runtime.pluginId(),runtime.instanceId(),user.username(),action,detail.toString());
    }
    @EventListener(ApplicationReadyEvent.class)
    public void checkLegacyOptions() {
        var ids=store.legacyJobs();
        if(!ids.isEmpty())org.slf4j.LoggerFactory.getLogger(getClass()).warn("Legacy task script options require migration; affected installations cannot execute. Job IDs: {}",ids);
    }
}
