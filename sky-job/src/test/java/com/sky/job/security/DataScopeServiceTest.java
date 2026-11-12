package com.sky.job.security;

import com.sky.job.system.mapper.DepartmentMapper;
import com.sky.job.system.mapper.RoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {
    private static final String USER_ID = "user-1";
    private static final String PERMISSION = "job:read";

    @Mock private RoleMapper roleMapper;
    @Mock private DepartmentMapper departmentMapper;

    @Test
    void adminAlwaysReceivesAllScopeWithoutConsultingRoleGrants() {
        EffectiveDataScope scope = service().resolve(user(Set.of("ADMIN")), PERMISSION);

        assertThat(scope.all()).isTrue();
        assertThat(scope.userId()).isEqualTo(USER_ID);
        verifyNoInteractions(roleMapper, departmentMapper);
    }

    @Test
    void allGrantDominatesEveryOtherRoleScope() {
        when(roleMapper.dataScopeGrants(USER_ID, PERMISSION)).thenReturn(List.of(
                grant(1, DataScopeType.SELF),
                grant(2, DataScopeType.ALL),
                grant(3, DataScopeType.CUSTOM)
        ));

        EffectiveDataScope scope = service().resolve(user(Set.of("reader", "manager")), PERMISSION);

        assertThat(scope.all()).isTrue();
        verify(roleMapper, never()).customDepartmentIds(USER_ID, PERMISSION);
        verifyNoInteractions(departmentMapper);
    }

    @Test
    void combinesSelfDepartmentChildrenAndCustomDepartmentsAcrossRoles() {
        when(roleMapper.dataScopeGrants(USER_ID, PERMISSION)).thenReturn(List.of(
                grant(1, DataScopeType.SELF),
                grant(2, DataScopeType.DEPARTMENT),
                grant(3, DataScopeType.DEPARTMENT_AND_CHILDREN),
                grant(4, DataScopeType.CUSTOM)
        ));
        when(departmentMapper.descendantIds(10L)).thenReturn(List.of(10L, 11L, 12L));
        when(roleMapper.customDepartmentIds(USER_ID, PERMISSION)).thenReturn(List.of(20L, 21L, 11L));

        EffectiveDataScope scope = service().resolve(user(Set.of("self", "department", "custom")), PERMISSION);

        assertThat(scope.all()).isFalse();
        assertThat(scope.self()).isTrue();
        assertThat(scope.departmentIds()).containsExactlyInAnyOrder(10L, 11L, 12L, 20L, 21L);
        assertThat(service().allows(scope, USER_ID, 99L)).isTrue();
        assertThat(service().allows(scope, "another-user", 12L)).isTrue();
        assertThat(service().allows(scope, "another-user", 99L)).isFalse();
        verify(roleMapper).dataScopeGrants(USER_ID, PERMISSION);
        verify(roleMapper).customDepartmentIds(USER_ID, PERMISSION);
        verify(departmentMapper).descendantIds(10L);
    }

    @Test
    void departmentScopeIncludesOnlyPrimaryDepartment() {
        when(roleMapper.dataScopeGrants(USER_ID, PERMISSION))
                .thenReturn(List.of(grant(1, DataScopeType.DEPARTMENT)));

        EffectiveDataScope scope = service().resolve(user(Set.of("department")), PERMISSION);

        assertThat(scope.self()).isFalse();
        assertThat(scope.departmentIds()).containsExactly(10L);
        verifyNoInteractions(departmentMapper);
    }

    @Test
    void noRoleGrantForCurrentPermissionProducesDeniedScope() {
        when(roleMapper.dataScopeGrants(USER_ID, PERMISSION)).thenReturn(List.of());

        EffectiveDataScope scope = service().resolve(user(Set.of("role-with-another-permission")), PERMISSION);

        assertThat(scope.denied()).isTrue();
        assertThat(service().allows(scope, USER_ID, 10L)).isFalse();
        verify(roleMapper).dataScopeGrants(USER_ID, PERMISSION);
        verifyNoInteractions(departmentMapper);
    }

    @Test
    void nullPrincipalProducesDeniedScope() {
        EffectiveDataScope scope = service().resolve(null, PERMISSION);

        assertThat(scope.denied()).isTrue();
        assertThat(scope.userId()).isNull();
        verifyNoInteractions(roleMapper, departmentMapper);
    }

    private DataScopeService service() {
        return new DataScopeService(roleMapper, departmentMapper);
    }

    private AuthenticatedUser user(Set<String> roles) {
        return new AuthenticatedUser(USER_ID, "alice", "", 10L, true, roles, Set.of(PERMISSION));
    }

    private RoleDataScopeGrant grant(long roleId, DataScopeType type) {
        return new RoleDataScopeGrant(roleId, "role-" + roleId, type);
    }
}
