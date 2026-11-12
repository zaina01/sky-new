package com.sky.job.security;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeMapperXmlTest {
    @Test
    void roleScopeQueriesUseOnlyRolesGrantingTheRequestedPermission() throws Exception {
        URL resource = getClass().getClassLoader().getResource("mapper/system/RoleMapper.xml");

        assertThat(resource).isNotNull();
        String mapperXml = new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        String grantQuery = statement(mapperXml, "dataScopeGrants");
        String customQuery = statement(mapperXml, "customDepartmentIds");

        assertThat(grantQuery)
                .contains("JOIN sky_user_role")
                .contains("JOIN sky_role_permission")
                .contains("JOIN sky_permission")
                .contains("p.permission_name = #{permissionName}");
        assertThat(customQuery)
                .contains("p.permission_name = #{permissionName}")
                .contains("r.data_scope = 'CUSTOM'");
    }

    @Test
    void departmentRecursionIncludesTheSelectedDepartmentAndItsChildren() throws Exception {
        URL resource = getClass().getClassLoader().getResource("mapper/system/DepartmentMapper.xml");

        assertThat(resource).isNotNull();
        String mapperXml = new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        String descendantQuery = statement(mapperXml, "descendantIds");

        assertThat(descendantQuery)
                .contains("WITH RECURSIVE department_tree")
                .contains("WHERE department_id = #{departmentId}")
                .contains("child.parent_id = parent.department_id");
    }

    private String statement(String xml, String id) {
        int start = xml.indexOf("id=\"" + id + "\"");
        assertThat(start).as("mapper statement %s", id).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).as("mapper statement end %s", id).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
