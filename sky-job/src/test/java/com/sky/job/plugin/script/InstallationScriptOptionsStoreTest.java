package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.plugin.script.mapper.InstallationScriptOptionsMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstallationScriptOptionsStoreTest {
    ObjectMapper json=new ObjectMapper();
    InstallationScriptOptionsMapper mapper=mock(InstallationScriptOptionsMapper.class);
    Map<String,String> options=new HashMap<>();Map<String,Map<String,String>> variables=new HashMap<>();
    String key=Base64.getEncoder().encodeToString(new byte[32]);
    InstallationScriptOptionsStore store=new InstallationScriptOptionsStore(mapper,json,new ScriptEnvironmentCipher(key));
    @BeforeEach void setup() {
        when(mapper.lockInstallation(anyString())).thenAnswer(i->i.getArgument(0));
        doAnswer(i->{options.putIfAbsent(i.getArgument(0),"{\"arguments\":[],\"revision\":0}");return null;}).when(mapper).ensureOptions(anyString());
        when(mapper.lockOptions(anyString())).thenAnswer(i->options.get(i.getArgument(0)));
        doAnswer(i->{options.put(i.getArgument(0),i.getArgument(1));return null;}).when(mapper).saveOptions(anyString(),anyString());
        when(mapper.variables(anyString())).thenAnswer(i->new ArrayList<>(variables.getOrDefault(i.getArgument(0),Map.of()).values()));
        doAnswer(i->{variables.computeIfAbsent(i.getArgument(0),k->new LinkedHashMap<>()).put(i.getArgument(1),i.getArgument(3));return null;}).when(mapper).insertVariable(anyString(),anyString(),anyString(),anyString());
        doAnswer(i->{variables.get(i.getArgument(0)).put(i.getArgument(1),i.getArgument(3));return null;}).when(mapper).updateVariable(anyString(),anyString(),anyString(),anyString());
        doAnswer(i->{variables.get(i.getArgument(0)).remove(i.getArgument(1));return null;}).when(mapper).deleteVariable(anyString(),anyString());
        when(mapper.legacyJobs(anyString())).thenReturn(List.of());
    }
    ObjectNode variable(String name,String value){return json.createObjectNode().put("name",name).put("value",value);}
    @Test void encryptedCrudRenameWithoutReplacingSecretAndSnapshotIsolation() {
        var created=store.saveVariable("a",null,variable("dxqy","中文@#\nsecond"),"admin");String id=created.path("id").asText();
        assertThat(created.path("enabled").asBoolean()).isTrue();assertThat(created.has("value")).isFalse();
        assertThat(variables.get("a").get(id)).doesNotContain("中文","second");
        var first=store.snapshot("a");assertThat(first.options().getEnvironmentOrThrow("dxqy")).isEqualTo("中文@#\nsecond");
        store.saveVariable("a",id,json.createObjectNode().put("name","chinaTelecomAccount"),"admin");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("chinaTelecomAccount")).isEqualTo("中文@#\nsecond");
        assertThat(first.options().getEnvironmentMap()).containsKey("dxqy");
        assertThat(store.snapshot("a").revision()).isNotEqualTo(first.revision());
        store.saveVariable("a",id,variable("chinaTelecomAccount","").put("sensitive",false),"admin");
        assertThat(store.list("a",null,null,1,20).items().getFirst().path("value").asText()).isEmpty();
        store.batch("a",List.of(id),null,true,"admin");assertThat(store.snapshot("a").options().getEnvironmentMap()).isEmpty();
    }
    @Test void disabledVariablesDoNotRequireKeyAndDuplicateDisabledNamesAreRejected() {
        String id=store.saveVariable("a",null,variable("dxqy","secret"),"admin").path("id").asText();
        store.batch("a",List.of(id),false,false,"admin");
        var noKey=new InstallationScriptOptionsStore(mapper,json,new ScriptEnvironmentCipher(""));
        assertThat(noKey.snapshot("a").options().getEnvironmentMap()).isEmpty();
        assertThatThrownBy(()->store.saveVariable("a",null,variable("DXQY","x"),"admin")).hasMessageContaining("conflict by case");
        store.batch("a",List.of(id),true,false,"admin");
        assertThatThrownBy(()->noKey.snapshot("a")).hasMessageContaining("ENCRYPTION_KEY");
    }
    @Test void batchesValidateAllIdsBeforeMutationAndCipherIsBoundToInstance() {
        String a=store.saveVariable("a",null,variable("token","one"),"admin").path("id").asText();
        String b=store.saveVariable("b",null,variable("token","two"),"admin").path("id").asText();
        assertThatThrownBy(()->store.batch("a",List.of(a,b),false,false,"admin")).hasMessageContaining("not found");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("token")).isEqualTo("one");
        assertThatThrownBy(()->store.batch("a",List.of(a,b),null,true,"admin")).hasMessageContaining("not found");
        variables.get("b").put(a,variables.get("a").get(a));
        assertThatThrownBy(()->store.snapshot("b")).hasMessageContaining("Cannot decrypt");
    }
    @Test void argumentsLimitsFilteringAndLegacyBlock() {
        store.saveArguments("a",json.createArrayNode().add("--limit").add("").add("a b & $()"),"admin");
        assertThat(store.snapshot("a").options().getArgumentsList()).containsExactly("--limit","","a b & $()");
        store.saveVariable("a",null,variable("dxqy","x").put("remark","searchable"),"admin");
        assertThat(store.list("a","search",true,1,20).total()).isEqualTo(1);
        assertThat(store.list("a","search",false,1,20).total()).isZero();
        assertThatThrownBy(()->store.saveVariable("a",null,variable("PATH","x"),"admin")).hasMessageContaining("reserved");
        assertThatThrownBy(()->store.saveArguments("a",json.createArrayNode().add("x".repeat(24577)),"admin")).hasMessageContaining("24 KiB");
        when(mapper.legacyJobs("a")).thenReturn(List.of("42","43"));
        assertThatThrownBy(()->store.snapshot("a")).hasMessageContaining("42", "43");
        assertThat(store.arguments("a").path("legacyJobIds").size()).isEqualTo(2);
    }
    @Test void emptyOptionsNeedNoKeyAndUnchangedStateKeepsRevision() {
        var noKey=new InstallationScriptOptionsStore(mapper,json,new ScriptEnvironmentCipher(""));
        assertThat(noKey.snapshot("a").revision()).isEqualTo("0");
        String id=store.saveVariable("a",null,variable("dxqy","x"),"admin").path("id").asText();
        String revision=store.snapshot("a").revision();
        store.batch("a",List.of(id),true,false,"admin");assertThat(store.snapshot("a").revision()).isEqualTo(revision);
    }
    @Test void disablingPrimaryVariablePreservesFallbackAndIndependentInstance() {
        String id=store.saveVariable("a",null,variable("dxqy","primary"),"admin").path("id").asText();
        store.saveVariable("a",null,variable("chinaTelecomAccount","fallback"),"admin");
        store.saveVariable("b",null,variable("dxqy","other instance"),"admin");
        var running=store.snapshot("a");
        store.batch("a",List.of(id),false,false,"admin");
        assertThat(store.snapshot("a").options().getEnvironmentMap()).containsExactlyEntriesOf(Map.of("chinaTelecomAccount","fallback"));
        assertThat(running.options().getEnvironmentOrThrow("dxqy")).isEqualTo("primary");
        assertThat(store.snapshot("b").options().getEnvironmentOrThrow("dxqy")).isEqualTo("other instance");
    }
    ObjectNode settings(String separator,String... overrides) {
        var request=json.createObjectNode().put("defaultSeparator",separator);
        var rules=request.putArray("overrides");
        for(int i=0;i<overrides.length;i+=2)rules.addObject().put("name",overrides[i]).put("separator",overrides[i+1]);
        return request;
    }
    @Test void mergesOnlyEnabledRowsPreservingBlanksDuplicatesAndLiteralCharacters() {
        store.saveVariable("a",null,variable("key1","aa"),"admin");
        store.saveVariable("a",null,variable("key1",""),"admin");
        store.saveVariable("a",null,variable("key1","bb=中文\n "),"admin");
        store.saveVariable("a",null,variable("key1","aa"),"admin");
        String disabled=store.saveVariable("a",null,variable("key1","hidden").put("enabled",false),"admin").path("id").asText();
        // A corrupt disabled ciphertext proves that merging never decrypts disabled rows.
        var row=json.valueToTree(Map.of());
        try {row=json.readTree(variables.get("a").get(disabled));}catch(Exception e){throw new AssertionError(e);}
        ((ObjectNode)row).put("encryptedValue","invalid");variables.get("a").put(disabled,row.toString());
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("key1")).isEqualTo("aa@@bb=中文\n @aa");
        store.saveEnvironmentSettings("a",settings(""),"admin");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("key1")).isEqualTo("aabb=中文\n aa");
        store.saveEnvironmentSettings("a",settings("\\n"),"admin");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("key1")).isEqualTo("aa\\n\\nbb=中文\n \\naa");
    }
    @Test void defaultsOverridesRemovalAndSnapshotsAreIndependent() {
        for(String name:List.of("dxqy","chinaTelecomAccount")) {
            store.saveVariable("a",null,variable(name,"aa"),"admin");
            store.saveVariable("a",null,variable(name,"bb"),"admin");
        }
        var before=store.snapshot("a");
        store.saveEnvironmentSettings("a",settings("@","chinaTelecomAccount","#"),"admin");
        assertThat(store.snapshot("a").options().getEnvironmentMap()).containsEntry("dxqy","aa@bb").containsEntry("chinaTelecomAccount","aa#bb");
        assertThat(before.options().getEnvironmentOrThrow("chinaTelecomAccount")).isEqualTo("aa@bb");
        assertThat(store.snapshot("a").revision()).isNotEqualTo(before.revision());
        store.saveEnvironmentSettings("a",settings("\n"),"admin");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("chinaTelecomAccount")).isEqualTo("aa\nbb");
        String revision=store.snapshot("a").revision();
        store.saveEnvironmentSettings("a",settings("\n"),"admin");
        assertThat(store.snapshot("a").revision()).isEqualTo(revision);
        assertThat(store.environmentSettings("b").path("defaultSeparator").asText()).isEqualTo("@");
    }
    @Test void stableCreationOrderSurvivesEditingEnablementAndRename()throws Exception {
        String first=store.saveVariable("a",null,variable("key1","first"),"admin").path("id").asText();
        String second=store.saveVariable("a",null,variable("key1","second"),"admin").path("id").asText();
        // Force same timestamp and reverse mapper order; UUID is the deterministic tie-breaker.
        for(String id:List.of(first,second)) {
            var row=(ObjectNode)json.readTree(variables.get("a").get(id));row.put("createdAt","2026-01-01T00:00:00Z");variables.get("a").put(id,row.toString());
        }
        when(mapper.variables("a")).thenAnswer(i->List.of(variables.get("a").get(second),variables.get("a").get(first)));
        String expected=first.compareTo(second)<0?"first@second":"second@first";
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("key1")).isEqualTo(expected);
        store.saveVariable("a",first,json.createObjectNode().put("name","key1").put("remark","edited"),"admin");
        store.batch("a",List.of(first),false,false,"admin");store.batch("a",List.of(first),true,false,"admin");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("key1")).isEqualTo(expected);
        store.saveEnvironmentSettings("a",settings("@","target","#"),"admin");
        for(String id:List.of(first,second))store.saveVariable("a",id,json.createObjectNode().put("name","target"),"admin");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("target")).isEqualTo(expected.replace('@','#'));
        assertThat(store.environmentSettings("a").path("overrides").get(0).path("name").asText()).isEqualTo("target");
    }
    @Test void settingsAndRowsRejectCaseConflictsWithoutChangingConfiguration() {
        store.saveEnvironmentSettings("a",settings("@","future","#"),"admin");
        String revision=store.snapshot("a").revision();
        assertThatThrownBy(()->store.saveVariable("a",null,variable("FUTURE","v"),"admin")).hasMessageContaining("conflict by case");
        assertThatThrownBy(()->store.saveEnvironmentSettings("a",settings("@","future","#","FUTURE","@"),"admin")).hasMessageContaining("conflict by case");
        assertThatThrownBy(()->store.saveEnvironmentSettings("a",settings("@","future","#","future","@"),"admin")).hasMessageContaining("Duplicate");
        String id=store.saveVariable("a",null,variable("future","v"),"admin").path("id").asText();
        store.batch("a",List.of(id),null,true,"admin");
        store.saveVariable("a",null,variable("future","new"),"admin");
        assertThat(store.environmentSettings("a").path("overrides").size()).isEqualTo(1);
        assertThat(store.snapshot("a").revision()).isNotEqualTo(revision);
        assertThatThrownBy(()->store.saveEnvironmentSettings("a",settings("中".repeat(43)),"admin")).hasMessageContaining("128 UTF-8");
        assertThatThrownBy(()->store.saveEnvironmentSettings("a",settings("\0"),"admin")).hasMessageContaining("NUL");
        assertThatThrownBy(()->store.saveEnvironmentSettings("a",settings("@","PATH","#"),"admin")).hasMessageContaining("reserved");
    }
    @Test void mergedSizeIncludesSeparatorsAndFailedChangesDoNotWrite() {
        store.saveVariable("a",null,variable("k","x".repeat(24571)),"admin");
        String second=store.saveVariable("a",null,variable("k","y"),"admin").path("id").asText();
        // k= + NUL (3), values (24572), one @ (1) = exactly 24576 bytes.
        String revision=store.snapshot("a").revision();
        assertThatThrownBy(()->store.saveEnvironmentSettings("a",settings("##"),"admin")).hasMessageContaining("24 KiB");
        assertThat(store.environmentSettings("a").path("defaultSeparator").asText()).isEqualTo("@");
        assertThat(store.snapshot("a").revision()).isEqualTo(revision);
        assertThatThrownBy(()->store.saveVariable("a",second,variable("k","yy"),"admin")).hasMessageContaining("24 KiB");
        store.batch("a",List.of(second),false,false,"admin");
        store.saveEnvironmentSettings("a",settings("##"),"admin");
        assertThatThrownBy(()->store.batch("a",List.of(second),true,false,"admin")).hasMessageContaining("24 KiB");
        assertThat(store.snapshot("a").options().getEnvironmentOrThrow("k")).hasSize(24571);
    }
}
