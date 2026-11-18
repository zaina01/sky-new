package com.sky.job.plugin.script;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;

@RestController
@RequestMapping("/plugins/installations/{installationId}")
public class InstallationScriptOptionsController {
    private final InstallationScriptOptionsService service;
    public InstallationScriptOptionsController(InstallationScriptOptionsService service){this.service=service;}
    @GetMapping("/script-environment-settings")
    public ObjectNode environmentSettings(@PathVariable String installationId,@AuthenticationPrincipal AuthenticatedUser user){return service.environmentSettings(installationId,user);}
    @PutMapping("/script-environment-settings")
    public ObjectNode environmentSettings(@PathVariable String installationId,@RequestBody JsonNode body,@AuthenticationPrincipal AuthenticatedUser user){return service.saveEnvironmentSettings(installationId,body,user);}
    @GetMapping("/script-environment-variables")
    public PageResponse<ObjectNode> list(@PathVariable String installationId,@RequestParam(required=false) String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,@AuthenticationPrincipal AuthenticatedUser user){return service.list(installationId,keyword,enabled,page,size,user);}
    @PostMapping("/script-environment-variables")
    public ObjectNode create(@PathVariable String installationId,@RequestBody JsonNode body,@AuthenticationPrincipal AuthenticatedUser user){return service.saveVariable(installationId,null,body,user);}
    @PutMapping("/script-environment-variables/{id}")
    public ObjectNode update(@PathVariable String installationId,@PathVariable String id,@RequestBody JsonNode body,@AuthenticationPrincipal AuthenticatedUser user){return service.saveVariable(installationId,id,body,user);}
    public record State(Boolean enabled) { }
    public record Batch(List<String> ids,Boolean enabled) { }
    @PatchMapping("/script-environment-variables/{id}/enabled")
    public Map<String,Integer> enabled(@PathVariable String installationId,@PathVariable String id,@RequestBody State body,@AuthenticationPrincipal AuthenticatedUser user){return service.batch(installationId,List.of(id),body.enabled(),false,user);}
    @DeleteMapping("/script-environment-variables/{id}")
    public Map<String,Integer> delete(@PathVariable String installationId,@PathVariable String id,@AuthenticationPrincipal AuthenticatedUser user){return service.batch(installationId,List.of(id),null,true,user);}
    @PostMapping("/script-environment-variables/batch-enabled")
    public Map<String,Integer> enabledBatch(@PathVariable String installationId,@RequestBody Batch body,@AuthenticationPrincipal AuthenticatedUser user){return service.batch(installationId,body.ids(),body.enabled(),false,user);}
    @PostMapping("/script-environment-variables/batch-delete")
    public Map<String,Integer> deleteBatch(@PathVariable String installationId,@RequestBody Batch body,@AuthenticationPrincipal AuthenticatedUser user){return service.batch(installationId,body.ids(),null,true,user);}
    @GetMapping("/script-arguments")
    public ObjectNode arguments(@PathVariable String installationId,@AuthenticationPrincipal AuthenticatedUser user){return service.arguments(installationId,user);}
    public record Arguments(JsonNode arguments) { }
    @PutMapping("/script-arguments")
    public ObjectNode arguments(@PathVariable String installationId,@RequestBody Arguments body,@AuthenticationPrincipal AuthenticatedUser user){return service.saveArguments(installationId,body.arguments(),user);}
}
