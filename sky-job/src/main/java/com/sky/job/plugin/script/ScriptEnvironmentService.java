package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@EnableConfigurationProperties(ScriptRuntimeProperties.class)
public class ScriptEnvironmentService {
    private final ScriptRuntimeProperties properties;
    private final ScriptEnvironmentStore store;
    private final ObjectMapper json;
    private final Map<String,ExecutorService> workers=Map.of("node",Executors.newSingleThreadExecutor(),"python",Executors.newSingleThreadExecutor());
    private final Map<String,Integer> references=new HashMap<>();
    private volatile boolean closing;
    public ScriptEnvironmentService(ScriptRuntimeProperties properties, ScriptEnvironmentStore store, ObjectMapper json) {
        this.properties=properties; this.store=store; this.json=json;
    }
    @PostConstruct void recover() {
        for (var op:store.list("operation/")) if (Set.of("QUEUED","RUNNING").contains(op.path("status").asText())) {
            op.put("status","FAILED").put("error","Host restarted during dependency operation").put("finishedAt",Instant.now().toString());
            store.put("operation/"+op.path("id").asText(),op);
        }
    }
    @PreDestroy void close() {
        closing=true; workers.values().forEach(ExecutorService::shutdownNow);
        for(var worker:workers.values())try {worker.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
    }
    public static String engine(String engine) {
        if (engine==null || !Set.of("node","python").contains(engine)) throw new IllegalArgumentException("Engine must be node or python");
        return engine;
    }
    public synchronized ObjectNode environment(String engine) {
        engine(engine);
        var state=store.get("environment/"+engine);
        if (state.isEmpty()) state.put("engine",engine).put("registry",defaultRegistry(engine)).put("revision","");
        return state;
    }
    public List<ObjectNode> operations(String engine) { engine(engine); return store.list("operation/").stream().filter(o->engine.equals(o.path("engine").asText())).toList(); }
    public ObjectNode operation(String id) {
        UUID.fromString(id);
        var value=store.get("operation/"+id);
        if (value.isEmpty()) throw new IllegalArgumentException("Dependency operation not found");
        return value;
    }
    public String log(String id) throws IOException {
        operation(id);
        Path path=logPath(id);
        return Files.exists(path) ? new String(Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8) : "";
    }
    public synchronized ObjectNode registry(String engine, String url, String actor) {
        engine(engine); URI uri;
        try { uri=URI.create(url); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid package registry URL"); }
        if (uri.getScheme()==null || !Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("Registry must be an HTTP(S) URL without credentials, query or fragment");
        var state=environment(engine); state.put("registry",url).put("updatedBy",actor); store.put("environment/"+engine,state);
        var audit=json.createObjectNode().put("engine",engine).put("registry",url).put("actor",actor).put("at",Instant.now().toString());
        store.put("registry-audit/"+UUID.randomUUID(),audit);
        return state;
    }
    public synchronized ObjectNode submit(String engine, String action, String name, String version, String actor) {
        engine(engine);
        if (closing) throw new IllegalStateException("Host is shutting down");
        if (action==null || !Set.of("install","upgrade","uninstall","initialize").contains(action)) throw new IllegalArgumentException("Invalid dependency action");
        if (!action.equals("initialize")) validatePackage(engine,name,version);
        if (action.equals("initialize") && !environment(engine).path("revision").asText().isBlank()) throw new IllegalArgumentException("Environment already initialized");
        if (operations(engine).stream().filter(o->Set.of("QUEUED","RUNNING").contains(o.path("status").asText())).count()>=50) throw new IllegalStateException("Dependency operation queue is full");
        String id=UUID.randomUUID().toString();
        var op=json.createObjectNode().put("id",id).put("engine",engine).put("action",action).put("name",name==null?"":normalize(engine,name)).put("version",version==null?"":version)
                .put("actor",actor).put("status","QUEUED").put("createdAt",Instant.now().toString()).put("registry",environment(engine).path("registry").asText());
        store.put("operation/"+id,op);
        workers.get(engine).submit(()->execute(op.deepCopy()));
        return op;
    }
    public ObjectNode retry(String id, String actor) {
        var old=operation(id);
        if (!"FAILED".equals(old.path("status").asText())) throw new IllegalArgumentException("Only failed operations can be retried");
        return submit(old.path("engine").asText(),old.path("action").asText(),old.path("name").asText(),old.path("version").asText(),actor);
    }
    static void validatePackage(String engine, String name, String version) {
        String pattern=engine.equals("node") ? "(?:@[a-z0-9._-]+/)?[a-z0-9][a-z0-9._-]*" : "[A-Za-z0-9][A-Za-z0-9._-]*";
        if (name==null || name.length()>214 || !name.matches(pattern)) throw new IllegalArgumentException("Invalid package name");
        if (version!=null && !version.isBlank() && (version.length()>100 || !version.matches("[0-9][A-Za-z0-9.+_-]*"))) throw new IllegalArgumentException("Specify an exact package version or leave blank");
        if (engine.equals("python") && Set.of("grpcio","protobuf","grpcio-tools","pip","setuptools","wheel").contains(normalize(engine,name))) throw new IllegalArgumentException("Package is managed by the platform");
    }
    private static String normalize(String engine,String name) { return engine.equals("python")?name.toLowerCase(Locale.ROOT).replaceAll("[-_.]+","-"):name; }
    private static String defaultRegistry(String engine) { return engine.equals("node")?"https://registry.npmjs.org":"https://pypi.org/simple"; }
    public Path revisionPath(String engine,String revision) { engine(engine); UUID.fromString(revision); return properties.root().toAbsolutePath().normalize().resolve(engine).resolve(revision); }
    private Path logPath(String id) { return properties.root().toAbsolutePath().normalize().resolve("logs").resolve(id+".log"); }
    public Path python(Path revision) { return revision.resolve(ScriptProcesses.isWindows()?"venv/Scripts/python.exe":"venv/bin/python"); }

    public record Lease(String engine,String revision,String interpreterVersion,Path path) { }
    public synchronized Lease acquire(String engine) {
        var state=environment(engine); String revision=state.path("revision").asText();
        if (revision.isBlank()) throw new IllegalStateException("Initialize the "+engine+" environment in Script dependencies first");
        references.merge(engine+"/"+revision,1,Integer::sum);
        return new Lease(engine,revision,state.path("interpreterVersion").asText(),revisionPath(engine,revision));
    }
    public synchronized void release(Lease lease) { references.computeIfPresent(lease.engine()+"/"+lease.revision(),(key,count)->count<=1?null:count-1); }

    private void execute(ObjectNode op) {
        String id=op.path("id").asText(), engine=op.path("engine").asText();
        Path revision=revisionPath(engine,id), log=logPath(id);
        try {
            op.put("status","RUNNING").put("startedAt",Instant.now().toString());store.put("operation/"+id,op);
            Files.createDirectories(revision);
            var state=environment(engine);
            ObjectNode direct=state.path("directDependencies").isObject()?(ObjectNode)state.get("directDependencies").deepCopy():json.createObjectNode();
            String name=op.path("name").asText(), action=op.path("action").asText(), version=op.path("version").asText();
            if (action.equals("uninstall")) {
                if (!direct.has(name)) throw new IllegalArgumentException("Package is not a direct dependency");
                direct.remove(name);
            } else if (!action.equals("initialize")) direct.put(name,version);
            String interpreterVersion=probe(engine,revision,log);
            JsonNode packages;
            if (engine.equals("node")) packages=buildNode(revision,direct,op.path("registry").asText(),log);
            else packages=buildPython(revision,direct,op.path("registry").asText(),log);
            var installed=json.createObjectNode();
            if (engine.equals("node")) {
                packages.path("packages").fields().forEachRemaining(e->{
                    if (!e.getKey().isEmpty()) installed.put(e.getKey(),e.getValue().path("version").asText());
                });
                for (var it=direct.fieldNames();it.hasNext();) { String pkg=it.next();direct.put(pkg,packages.path("packages").path("node_modules/"+pkg).path("version").asText()); }
            } else {
                packages.forEach(p->installed.put(normalize(engine,p.path("name").asText()),p.path("version").asText()));
                for (var it=direct.fieldNames();it.hasNext();) { String pkg=it.next();direct.put(pkg,installed.path(pkg).asText()); }
            }
            for (var it=direct.elements();it.hasNext();) if (it.next().asText().isBlank()) throw new IllegalStateException("Installed dependency version missing");
            var snapshot=json.createObjectNode().put("engine",engine).put("revision",id).put("interpreterVersion",interpreterVersion).put("createdAt",Instant.now().toString());
            snapshot.set("directDependencies",direct); snapshot.set("packages",installed);
            synchronized(this) {
                if (closing || Thread.currentThread().isInterrupted()) throw new InterruptedException("Host is shutting down");
                var latest=environment(engine);
                latest.put("revision",id).put("interpreterVersion",interpreterVersion).put("updatedBy",op.path("actor").asText());
                latest.set("directDependencies",direct);latest.set("packages",installed);
                op.put("status","SUCCEEDED").put("revision",id).put("finishedAt",Instant.now().toString());
                store.publish(engine,id,snapshot,latest,op);
            }
        } catch (Exception e) {
            op.put("status","FAILED").put("error",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally { if (!"SUCCEEDED".equals(op.path("status").asText())) { op.put("finishedAt",Instant.now().toString());store.put("operation/"+id,op); } }
    }
    private String probe(String engine,Path directory,Path log) throws Exception {
        Path versionLog=directory.resolve("interpreter.log");
        String output=ScriptProcesses.run(List.of(engine.equals("node")?properties.nodeCommand():properties.pythonCommand(),"--version"),directory,versionLog,Duration.ofSeconds(10),8192).trim();
        if (engine.equals("node") ? !output.startsWith("v24.") : !output.startsWith("Python 3.12.")) throw new IllegalArgumentException("Supported interpreters are Node 24 and Python 3.12; detected "+output);
        return output;
    }
    private List<String> npm() throws IOException,InterruptedException {
        Path cli=properties.npmCli();
        if (cli==null) {
            Files.createDirectories(properties.root().toAbsolutePath());
            Path probe=Files.createTempFile(properties.root().toAbsolutePath(),"node-path-",".log");
            try {
                String executable=ScriptProcesses.run(List.of(properties.nodeCommand(),"-p","process.execPath"),probe.getParent(),probe,Duration.ofSeconds(10),8192).trim();
                String last=executable.lines().reduce((a,b)->b).orElseThrow();
                Path home=Path.of(last).getParent();
                cli=home.resolve("node_modules/npm/bin/npm-cli.js");
                if (!Files.isRegularFile(cli)) cli=home.resolve("../lib/node_modules/npm/bin/npm-cli.js").normalize();
            } finally { Files.deleteIfExists(probe); }
        }
        if (!Files.isRegularFile(cli)) throw new IllegalArgumentException("Configure sky.plugin.script.npm-cli to npm-cli.js");
        return List.of(properties.nodeCommand(),cli.toAbsolutePath().toString());
    }
    private JsonNode buildNode(Path dir,ObjectNode direct,String registry,Path log) throws Exception {
        ObjectNode manifest=json.createObjectNode().put("name","sky-shared-environment").put("version","1.0.0").put("private",true);
        ObjectNode dependencies=direct.deepCopy(); dependencies.fields().forEachRemaining(e->{if(e.getValue().asText().isBlank())dependencies.put(e.getKey(),"latest");});manifest.set("dependencies",dependencies);
        Files.writeString(dir.resolve("package.json"),manifest.toString());
        var command=new ArrayList<>(npm());command.addAll(List.of("install","--omit=dev","--no-audit","--no-fund","--registry",registry));run(command,dir,log);
        var check=new ArrayList<>(npm());check.addAll(List.of("ls","--all","--omit=dev"));run(check,dir,log);
        run(List.of(properties.nodeCommand(),properties.nodeRunner().toAbsolutePath().toString(),"--check"),dir,log);
        return json.readTree(dir.resolve("package-lock.json").toFile());
    }
    private JsonNode buildPython(Path dir,ObjectNode direct,String registry,Path log) throws Exception {
        run(List.of(properties.pythonCommand(),"-m","venv",dir.resolve("venv").toString()),dir,log);
        Path requirements=properties.pythonRunner().toAbsolutePath().getParent().resolve("requirements.txt");
        String base=Files.readString(requirements);
        StringBuilder requested=new StringBuilder(base).append('\n');
        direct.fields().forEachRemaining(e->requested.append(e.getKey()).append(e.getValue().asText().isBlank()?"":"=="+e.getValue().asText()).append('\n'));
        Files.writeString(dir.resolve("requirements.txt"),requested);
        run(List.of(python(dir).toString(),"-m","pip","install","--index-url",registry,"-r",dir.resolve("requirements.txt").toString()),dir,log);
        run(List.of(python(dir).toString(),"-m","pip","check"),dir,log);
        run(List.of(python(dir).toString(),properties.pythonRunner().toAbsolutePath().toString(),"--check"),dir,log);
        Path list=dir.resolve("installed.json");
        ScriptProcesses.run(List.of(python(dir).toString(),"-m","pip","list","--format=json"),dir,list,properties.installTimeout(),properties.maxLogBytes());
        return json.readTree(list.toFile());
    }
    private void run(List<String> command,Path directory,Path log) throws IOException,InterruptedException { ScriptProcesses.run(command,directory,log,properties.installTimeout(),properties.maxLogBytes()); }
    public ObjectNode health(String engine) {
        engine(engine); var result=json.createObjectNode().put("engine",engine);
        try {
            Path dir=properties.root().toAbsolutePath().resolve("health").resolve(engine).resolve(UUID.randomUUID().toString());
            Files.createDirectories(dir);
            String version=probe(engine,dir,dir.resolve("health.log"));
            if (engine.equals("node")) {
                var command=new ArrayList<>(npm());command.add("--version");run(command,dir,dir.resolve("npm.log"));
                run(List.of(properties.nodeCommand(),properties.nodeRunner().toAbsolutePath().toString(),"--check"),dir,dir.resolve("runner.log"));
            } else {
                var state=environment(engine);
                if (state.path("revision").asText().isBlank()) { run(List.of(properties.pythonCommand(),"-m","venv","--help"),dir,dir.resolve("venv.log")); }
                else run(List.of(python(revisionPath(engine,state.path("revision").asText())).toString(),properties.pythonRunner().toAbsolutePath().toString(),"--check"),dir,dir.resolve("runner.log"));
            }
            result.put("available",true).put("interpreterVersion",version);
        } catch (Exception e) { result.put("available",false).put("message",e.getMessage()); }
        result.put("initialized",!environment(engine).path("revision").asText().isBlank());return result;
    }
}
