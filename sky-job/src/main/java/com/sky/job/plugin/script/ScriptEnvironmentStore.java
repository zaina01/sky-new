package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.plugin.script.mapper.ScriptEnvironmentMapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class ScriptEnvironmentStore {
    private final ScriptEnvironmentMapper mapper;
    private final ObjectMapper json;
    public ScriptEnvironmentStore(ScriptEnvironmentMapper mapper, ObjectMapper json) { this.mapper=mapper; this.json=json; }
    public ObjectNode get(String key) { return parse(mapper.get(key)); }
    public List<ObjectNode> list(String prefix) { return mapper.list(prefix).stream().map(this::parse).toList(); }
    public void put(String key, ObjectNode value) { mapper.put(key, value.toString()); }
    @org.springframework.transaction.annotation.Transactional
    public void publish(String engine, String revision, ObjectNode snapshot, ObjectNode environment, ObjectNode operation) {
        put("revision/"+engine+"/"+revision,snapshot);
        put("environment/"+engine,environment);
        put("operation/"+revision,operation);
    }
    private ObjectNode parse(String value) {
        try { return value == null ? json.createObjectNode() : (ObjectNode)json.readTree(value); }
        catch (Exception e) { throw new IllegalStateException("Invalid script environment metadata", e); }
    }
}
