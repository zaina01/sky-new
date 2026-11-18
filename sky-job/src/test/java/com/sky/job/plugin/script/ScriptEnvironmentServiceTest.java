package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.script.mapper.ScriptEnvironmentMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScriptEnvironmentServiceTest {
    @TempDir Path temporary;
    final ObjectMapper json=new ObjectMapper();
    ScriptEnvironmentService service;
    ScriptEnvironmentStore store;
    @BeforeEach void setup() {
        var mapper=mock(ScriptEnvironmentMapper.class);Map<String,String> data=new ConcurrentHashMap<>();
        when(mapper.get(anyString())).thenAnswer(i->data.get(i.getArgument(0)));
        when(mapper.list(anyString())).thenAnswer(i->data.entrySet().stream().filter(e->e.getKey().startsWith(i.getArgument(0))).map(Map.Entry::getValue).toList());
        doAnswer(i->{data.put(i.getArgument(0),i.getArgument(1));return null;}).when(mapper).put(anyString(),anyString());
        store=new ScriptEnvironmentStore(mapper,json);
        service=new ScriptEnvironmentService(new ScriptRuntimeProperties(temporary,null,null,null,null,null,1,1000,null),store,json);
    }
    @AfterEach void close(){service.close();}
    @Test void rejectsCommandsUrlsAndPlatformDependencyOverrides() {
        for(String name:List.of("--help","a;whoami","https://example.org/a","../local"))
            assertThatThrownBy(()->ScriptEnvironmentService.validatePackage("node",name,"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ScriptEnvironmentService.validatePackage("python","grpcio","1.0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ScriptEnvironmentService.validatePackage("node","axios","1.0;whoami")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->ScriptEnvironmentService.validatePackage("node","@scope/name","1.2.3")).doesNotThrowAnyException();
    }
    @Test void leaseKeepsPinnedRevisionAndRecoveryFailsIncompleteOperations() {
        String old=UUID.randomUUID().toString(), next=UUID.randomUUID().toString();
        store.put("environment/node",json.createObjectNode().put("engine","node").put("revision",old));
        var lease=service.acquire("node");
        store.put("environment/node",json.createObjectNode().put("engine","node").put("revision",next));
        assertThat(lease.revision()).isEqualTo(old);assertThat(service.acquire("node").revision()).isEqualTo(next);service.release(lease);
        String id=UUID.randomUUID().toString();store.put("operation/"+id,json.createObjectNode().put("id",id).put("engine","node").put("status","RUNNING"));
        service.recover();assertThat(service.operation(id).path("status").asText()).isEqualTo("FAILED");
        assertThat(service.environment("node").path("revision").asText()).isEqualTo(next);
    }
    @Test void registryRejectsCredentialsAndPersistsActor() {
        assertThatThrownBy(()->service.registry("node","https://user:secret@example.org","admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.registry("node","https://registry.npmjs.org","admin").path("updatedBy").asText()).isEqualTo("admin");
    }
}
