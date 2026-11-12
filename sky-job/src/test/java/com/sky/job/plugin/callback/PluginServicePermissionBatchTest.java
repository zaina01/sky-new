package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.BatchUpsertPluginServicePermissionRequest;
import com.sky.job.plugin.callback.dto.PluginServicePermissionResponse;
import com.sky.job.plugin.callback.dto.UpsertPluginServicePermissionRequest;
import com.sky.job.plugin.callback.mapper.PluginServicePermissionMapper;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PluginServicePermissionBatchTest {
    private AnnotationConfigApplicationContext context;
    private PluginServicePermissionService service;
    private PluginServicePermissionStore store;
    private PluginRuntimeMetadataStore runtimes;
    private DataScopeService scopes;
    private JdbcTemplate jdbc;
    private final EffectiveDataScope scope = EffectiveDataScope.all("admin");

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(PluginServicePermissionService.class);
        store = context.getBean(PluginServicePermissionStore.class);
        runtimes = context.getBean(PluginRuntimeMetadataStore.class);
        scopes = context.getBean(DataScopeService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("""
                CREATE TABLE plugin_installation (
                  installation_id VARCHAR(128) PRIMARY KEY, plugin_id VARCHAR(128),
                  deleted BOOLEAN DEFAULT FALSE, owner_user_id VARCHAR(128), owner_dept_id BIGINT)
                """);
        jdbc.execute("""
                CREATE TABLE plugin_service_permission (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  caller_installation_id VARCHAR(128) NOT NULL,
                  target_installation_id VARCHAR(128) NOT NULL,
                  operation VARCHAR(128) NOT NULL CHECK (operation <> 'fail-write'),
                  enabled BOOLEAN NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE (caller_installation_id, target_installation_id, operation))
                """);
        jdbc.update("INSERT INTO plugin_installation (installation_id, plugin_id) VALUES ('caller', 'plugin-a'), ('target', 'plugin-b')");
        when(scopes.resolve(null, PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE)).thenReturn(scope);
        for (String id : List.of("caller", "target")) {
            PluginRuntimeRecord runtime = mock(PluginRuntimeRecord.class);
            when(runtime.ownerUserId()).thenReturn(id);
            when(runtime.ownerDepartmentId()).thenReturn(1L);
            when(runtime.capabilitiesJson()).thenReturn("SERVICE");
            when(runtimes.findRuntimeByInstallationId(id)).thenReturn(Optional.of(runtime));
            when(scopes.allows(scope, id, 1L)).thenReturn(true);
        }
        when(runtimes.runtimeCapabilities("SERVICE")).thenReturn(Set.of(PluginCapability.SERVICE));
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) jdbc.execute("SHUTDOWN");
        if (context != null) context.close();
    }

    @Test
    void savesThreeDistinctOperationsAndUpdatesOnlySubmittedPermissions() {
        store.upsert("caller", "target", "untouched", true);
        var first = service.upsertBatch(request(List.of(" first ", "second", "first", "third"), null), null);
        assertThat(first).extracting(PluginServicePermissionResponse::operation).containsExactly("first", "second", "third");
        assertThat(first).allMatch(PluginServicePermissionResponse::enabled);
        var repeated = service.upsertBatch(request(List.of("first", "second", "third"), false), null);
        assertThat(repeated).extracting(PluginServicePermissionResponse::id)
                .containsExactlyElementsOf(first.stream().map(PluginServicePermissionResponse::id).toList());
        assertThat(repeated).noneMatch(PluginServicePermissionResponse::enabled);
        assertThat(count()).isEqualTo(4);
        assertThat(store.isAllowed("caller", "target", "untouched")).isTrue();
    }

    @Test
    void acceptsOneHundredDistinctOperationsAndDeduplicatesBeforeTheLimit() {
        var operations = Stream.concat(IntStream.range(0, 100).mapToObj(i -> "op" + i), Stream.of(" op0 ")).toList();
        assertThat(service.upsertBatch(request(operations, true), null)).hasSize(100);
        assertThat(count()).isEqualTo(100);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsTheEntireInvalidBatchBeforeWriting(BatchUpsertPluginServicePermissionRequest request) {
        assertThatThrownBy(() -> service.upsertBatch(request, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count()).isZero();
    }

    static Stream<BatchUpsertPluginServicePermissionRequest> invalidRequests() {
        return Stream.of(null, request(null, true), request(List.of(), true),
                request(List.of("valid", " "), true), request(Arrays.asList("valid", null), true),
                request(List.of("valid", "bad/name"), true), request(List.of("valid", "*"), true),
                request(List.of("a".repeat(129)), true),
                request(IntStream.rangeClosed(0, 100).mapToObj(i -> "op" + i).toList(), true),
                new BatchUpsertPluginServicePermissionRequest("", "target", List.of("first"), true));
    }

    @Test
    void reportsTheInvalidOperationPosition() {
        assertThatThrownBy(() -> service.upsertBatch(request(List.of("valid", "bad/name"), true), null))
                .hasMessageContaining("index 2");
    }

    @Test
    void rejectsEitherEndpointOutsideTheWritersScope() {
        for (String id : List.of("caller", "target")) {
            when(scopes.allows(scope, id, 1L)).thenReturn(false);
            assertThatThrownBy(() -> service.upsertBatch(request(List.of("first"), true), null))
                    .isInstanceOf(ResourceNotFoundException.class);
            when(scopes.allows(scope, id, 1L)).thenReturn(true);
        }
        assertThat(count()).isZero();
    }

    @Test
    void rejectsMissingInstallationsAndTargetsWithoutServiceCapability() {
        when(runtimes.runtimeCapabilities("SERVICE")).thenReturn(Set.of(PluginCapability.TASK));
        assertThatThrownBy(() -> service.upsertBatch(request(List.of("first"), true), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not support service calls");
        when(runtimes.findRuntimeByInstallationId("target")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.upsertBatch(request(List.of("first"), true), null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(count()).isZero();
    }

    @Test
    void databaseFailureRollsBackBothEarlierUpdatesAndInserts() {
        store.upsert("caller", "target", "existing", false);
        assertThatThrownBy(() -> service.upsertBatch(request(List.of("existing", "new", "fail-write"), true), null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count()).isEqualTo(1);
        assertThat(store.isAllowed("caller", "target", "existing")).isFalse();
        assertThat(store.isAllowed("caller", "target", "new")).isFalse();
    }

    @Test
    void singleOperationEndpointSemanticsRemainCompatible() {
        var single = service.upsert(new UpsertPluginServicePermissionRequest("caller", "target", "first", true), null);
        var batch = service.upsertBatch(request(List.of("first"), false), null);
        assertThat(batch).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isEqualTo(single.id());
            assertThat(saved.enabled()).isFalse();
        });
        assertThat(count()).isEqualTo(1);
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM plugin_service_permission", Long.class);
    }

    private static BatchUpsertPluginServicePermissionRequest request(List<String> operations, Boolean enabled) {
        return new BatchUpsertPluginServicePermissionRequest(" caller ", " target ", operations, enabled);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:permissions-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource source) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/plugin/callback/PluginServicePermissionMapper.xml"));
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean PluginServicePermissionStore permissionStore(SqlSessionTemplate template) {
            return new PluginServicePermissionStore(template.getMapper(PluginServicePermissionMapper.class));
        }
        @Bean PluginRuntimeMetadataStore runtimes() { return mock(PluginRuntimeMetadataStore.class); }
        @Bean DataScopeService scopes() { return mock(DataScopeService.class); }
        @Bean PluginServicePermissionService service(PluginServicePermissionStore store, PluginRuntimeMetadataStore runtimes, DataScopeService scopes) {
            return new PluginServicePermissionService(store, runtimes, scopes);
        }
    }
}
