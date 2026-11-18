package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/plugins/script-environments")
public class ScriptEnvironmentController {
    private final ScriptEnvironmentService service;
    public ScriptEnvironmentController(ScriptEnvironmentService service) { this.service=service; }
    @GetMapping public List<ObjectNode> list() { return List.of(service.environment("node"),service.environment("python")); }
    @GetMapping("/{engine}") public ObjectNode environment(@PathVariable String engine) { return service.environment(engine); }
    @GetMapping("/{engine}/health") public ObjectNode health(@PathVariable String engine) { return service.health(engine); }
    @GetMapping("/{engine}/operations") public List<ObjectNode> operations(@PathVariable String engine) { return service.operations(engine); }
    public record Change(String action,String name,String version) { }
    @PostMapping("/{engine}/operations") public ObjectNode submit(@PathVariable String engine,@RequestBody Change request,@AuthenticationPrincipal AuthenticatedUser user) {
        return service.submit(engine,request.action(),request.name(),request.version(),user.username());
    }
    public record Registry(String url) { }
    @PutMapping("/{engine}/registry") public ObjectNode registry(@PathVariable String engine,@RequestBody Registry request,@AuthenticationPrincipal AuthenticatedUser user) { return service.registry(engine,request.url(),user.username()); }
    @GetMapping("/operations/{id}") public ObjectNode operation(@PathVariable String id) { return service.operation(id); }
    @GetMapping("/operations/{id}/log") public Map<String,String> log(@PathVariable String id) throws IOException { return Map.of("content",service.log(id)); }
    @PostMapping("/operations/{id}/retry") public ObjectNode retry(@PathVariable String id,@AuthenticationPrincipal AuthenticatedUser user) { return service.retry(id,user.username()); }
}
