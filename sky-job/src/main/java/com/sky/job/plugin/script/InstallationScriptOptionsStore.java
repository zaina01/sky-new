package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.plugin.script.mapper.InstallationScriptOptionsMapper;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.web.ResourceNotFoundException;
import com.sky.plugin.protocol.StandaloneTaskOptions;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Repository
public class InstallationScriptOptionsStore {
    private final InstallationScriptOptionsMapper mapper;
    private final ObjectMapper json;
    private final ScriptEnvironmentCipher cipher;

    public InstallationScriptOptionsStore(InstallationScriptOptionsMapper mapper,ObjectMapper json,ScriptEnvironmentCipher cipher) {
        this.mapper=mapper;this.json=json;this.cipher=cipher;
    }
    private ObjectNode parse(String value) {
        try {return (ObjectNode)json.readTree(value);}
        catch(Exception e){throw new IllegalStateException("Invalid instance script configuration");}
    }
    private ObjectNode lock(String id) {
        // Serialize initialization, edits, snapshots and uninstall even before the options row exists.
        if(mapper.lockInstallation(id)==null)throw new ResourceNotFoundException("Plugin installation not found");
        mapper.ensureOptions(id);
        return parse(mapper.lockOptions(id));
    }
    // Mapper uses a locking current read, so an outer REPEATABLE READ transaction cannot see old rows.
    private List<ObjectNode> variables(String id){
        return new ArrayList<>(mapper.variables(id).stream().map(this::parse)
                .sorted(Comparator.comparing((ObjectNode row) -> Instant.parse(row.path("createdAt").asText()))
                        .thenComparing(row -> row.path("id").asText())).toList());
    }
    private ObjectNode find(List<ObjectNode> rows,String variableId) {
        return rows.stream().filter(v->variableId.equals(v.path("id").asText())).findFirst().orElseThrow(()->new ResourceNotFoundException("Environment variable not found in this installation"));
    }
    private Instant creationTime(List<ObjectNode> rows) {
        Instant now=Instant.now();
        if(rows.isEmpty())return now;
        Instant last=Instant.parse(rows.getLast().path("createdAt").asText());
        // System clocks may have coarse resolution or move backwards; new rows still append.
        return now.isAfter(last)?now:last.plusNanos(1);
    }
    private String identity(String id,ObjectNode variable){return id+":"+variable.path("id").asText();}
    private ObjectNode response(String id,ObjectNode row) {
        var result=row.deepCopy();result.remove("encryptedValue");result.remove("valueBytes");result.put("hasValue",true);
        if(!row.path("sensitive").asBoolean(true))result.put("value",cipher.decrypt(row.path("encryptedValue").asText(),identity(id,row)));
        return result;
    }
    private void changed(String id,ObjectNode options,String actor) {
        options.put("revision",options.path("revision").asLong()+1).put("updatedBy",actor).put("updatedAt",Instant.now().toString());
        mapper.saveOptions(id,options.toString());
    }
    private ObjectNode publicOptions(ObjectNode options) {
        var result=options.deepCopy();result.remove("environmentSettings");result.put("revision",options.path("revision").asText("0"));return result;
    }
    private ObjectNode settingsResponse(ObjectNode options) {
        return ScriptEnvironmentMergeSettings.fromOptions(options).toJson(json)
                .put("revision",options.path("revision").asText("0"));
    }
    @Transactional
    public ObjectNode environmentSettings(String id) {
        return settingsResponse(lock(id));
    }
    @Transactional
    public ObjectNode saveEnvironmentSettings(String id,JsonNode request,String actor) {
        var options=lock(id);
        var settings=ScriptEnvironmentMergeSettings.parse(request).toJson(json);
        var candidate=options.deepCopy();candidate.set("environmentSettings",settings);
        validateSize(candidate,variables(id));
        if(!ScriptEnvironmentMergeSettings.fromOptions(options).toJson(json).equals(settings))changed(id,candidate,actor);
        return settingsResponse(candidate);
    }
    @Transactional
    public PageResponse<ObjectNode> list(String id,String keyword,Boolean enabled,Integer page,Integer size) {
        lock(id);int p=Math.max(page==null?1:page,1),s=Math.min(Math.max(size==null?20:size,1),100);
        String query=keyword==null?"":keyword.toLowerCase(Locale.ROOT);
        var filtered=variables(id).stream().filter(v->enabled==null||v.path("enabled").asBoolean()==enabled)
                .filter(v->v.path("name").asText().toLowerCase(Locale.ROOT).contains(query)||v.path("remark").asText().toLowerCase(Locale.ROOT).contains(query)).toList();
        return new PageResponse<>(filtered.stream().skip((long)(p-1)*s).limit(s).map(v->response(id,v)).toList(),p,s,filtered.size());
    }
    @Transactional
    public ObjectNode arguments(String id) {
        var result=publicOptions(lock(id));result.set("legacyJobIds",json.valueToTree(mapper.legacyJobs(id)));return result;
    }
    @Transactional
    public ObjectNode saveArguments(String id,JsonNode arguments,String actor) {
        var options=lock(id);validateArguments(arguments);
        var candidate=options.deepCopy();candidate.set("arguments",arguments.deepCopy());validateSize(candidate,variables(id));
        if(!options.path("arguments").equals(arguments))changed(id,candidate,actor);
        return publicOptions(candidate);
    }
    @Transactional
    public ObjectNode saveVariable(String id,String variableId,JsonNode request,String actor) {
        var options=lock(id);var rows=variables(id);boolean create=variableId==null;
        if(request==null||!request.isObject())throw new IllegalArgumentException("Environment variable object required");
        if(create&&rows.size()>=128)throw new IllegalArgumentException("At most 128 environment variables per installation");
        var original=create?null:find(rows,variableId);
        var variable=create?json.createObjectNode().put("id",UUID.randomUUID().toString()).put("enabled",true).put("sensitive",true).put("createdAt",creationTime(rows).toString()).put("createdBy",actor):original.deepCopy();
        if(!request.path("name").isTextual())throw new IllegalArgumentException("Variable name must be text");
        String name=request.path("name").asText();ScriptOptionsValidation.validateName(name);
        variable.put("name",name);
        for(String field:List.of("sensitive","enabled"))if(request.has(field)) {
            if(!request.get(field).isBoolean())throw new IllegalArgumentException(field+" must be boolean");
            if(!create&&field.equals("enabled")&&request.get(field).asBoolean()!=variable.path(field).asBoolean())throw new IllegalArgumentException("Use the enabled endpoint to change variable state");
            variable.put(field,request.get(field).asBoolean());
        }
        if(request.has("remark")) {
            if(!request.get("remark").isTextual()||request.get("remark").asText().length()>1000)throw new IllegalArgumentException("Remark must be text up to 1000 characters");
            variable.put("remark",request.get("remark").asText());
        } else if(create)variable.put("remark","");
        if(request.has("value")) {
            var value=request.get("value");validateText(value);
            int bytes=bytes(value.asText());if(bytes>24576)throw new IllegalArgumentException("Variable exceeds 24 KiB");
            variable.put("encryptedValue",cipher.encrypt(value.asText(),identity(id,variable))).put("valueBytes",bytes);
        } else if(create)throw new IllegalArgumentException("Value is required for new environment variables");
        if(!create&&variable.equals(original))return response(id,variable);
        if(!create)rows.remove(original);rows.add(variable);validateSize(options,rows);
        variable.put("updatedBy",actor).put("updatedAt",Instant.now().toString());
        if(create)mapper.insertVariable(id,variable.path("id").asText(),name.toUpperCase(Locale.ROOT),variable.toString());
        else mapper.updateVariable(id,variableId,name.toUpperCase(Locale.ROOT),variable.toString());
        changed(id,options,actor);
        return response(id,variable);
    }
    @Transactional
    public List<ObjectNode> batch(String id,List<String> ids,Boolean enabled,boolean delete,String actor) {
        var options=lock(id);var rows=variables(id);
        if(ids==null||ids.isEmpty()||ids.size()>128||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Provide 1 to 128 distinct variable IDs");
        if(!delete&&enabled==null)throw new IllegalArgumentException("enabled is required");
        var selected=ids.stream().map(v->find(rows,v)).toList(); // Validate every ID before modifying anything.
        var affected=new ArrayList<ObjectNode>();
        for(var row:selected) {
            if(delete){rows.remove(row);affected.add(row);}
            else if(row.path("enabled").asBoolean()!=enabled){row.put("enabled",enabled).put("updatedBy",actor).put("updatedAt",Instant.now().toString());affected.add(row);}
        }
        validateSize(options,rows);
        for(var row:affected) {
            if(delete)mapper.deleteVariable(id,row.path("id").asText());
            else mapper.updateVariable(id,row.path("id").asText(),row.path("name").asText().toUpperCase(Locale.ROOT),row.toString());
        }
        if(!affected.isEmpty())changed(id,options,actor);
        return affected.stream().map(v->json.createObjectNode().put("id",v.path("id").asText()).put("name",v.path("name").asText())).toList();
    }
    public record Snapshot(String revision,StandaloneTaskOptions options) { }
    @Transactional
    public Snapshot snapshot(String id) {
        var options=lock(id);var legacy=mapper.legacyJobs(id);
        if(!legacy.isEmpty())throw new IllegalStateException("Legacy job scriptOptions must be cleared or migrated before running this installation. Job IDs: "+legacy);
        var rows=variables(id);validateArguments(options.path("arguments"));validateSize(options,rows);
        var result=StandaloneTaskOptions.newBuilder();options.path("arguments").forEach(v->result.addArguments(v.asText()));
        var settings=ScriptEnvironmentMergeSettings.fromOptions(options);
        Map<String,List<String>> grouped=new LinkedHashMap<>();
        for(var variable:rows)if(variable.path("enabled").asBoolean()) {
            grouped.computeIfAbsent(variable.path("name").asText(),name->new ArrayList<>())
                    .add(cipher.decrypt(variable.path("encryptedValue").asText(),identity(id,variable)));
        }
        grouped.forEach((name,values)->result.putEnvironment(name,String.join(settings.separatorFor(name),values)));
        return new Snapshot(options.path("revision").asText("0"),result.build());
    }
    @Transactional
    public void deleteInstallation(String id){lock(id);mapper.deleteVariables(id);mapper.deleteOptions(id);}
    public List<String> legacyJobs(){return mapper.legacyJobs(null);}
    private static int bytes(String value){return value.getBytes(StandardCharsets.UTF_8).length;}
    private static void validateText(JsonNode value){if(!value.isTextual()||value.asText().indexOf('\0')>=0)throw new IllegalArgumentException("Values must be strings without NUL");}
    private static void validateArguments(JsonNode args) {
        if(args==null||!args.isArray()||args.size()>256)throw new IllegalArgumentException("arguments must be an array of at most 256 strings");
        args.forEach(InstallationScriptOptionsStore::validateText);
    }
    private static void validateSize(ObjectNode options,List<ObjectNode> rows) {
        var settings=ScriptEnvironmentMergeSettings.fromOptions(options);settings.validateNames(rows);
        long size=0;for(var arg:options.path("arguments"))size+=bytes(arg.asText())+1;
        Set<String> names=new HashSet<>();
        for(var row:rows)if(row.path("enabled").asBoolean()) {
            String name=row.path("name").asText();
            size+=row.path("valueBytes").asLong();
            size+=names.add(name)?bytes(name)+2:bytes(settings.separatorFor(name));
        }
        if(size>24576)throw new IllegalArgumentException("Enabled environment variables and arguments exceed 24 KiB");
    }
}
