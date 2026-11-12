package com.sky.job.plugin.management.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;

/** A setter records field presence so omitted groupId differs from explicit null. */
public class UpdatePluginAccountGroupRequest {
    private Long groupId;
    private boolean groupIdPresent;
    private List<String> accountIds;

    @JsonSetter("groupId")
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
        this.groupIdPresent = true;
    }

    @JsonSetter("accountIds")
    public void setAccountIds(List<String> accountIds) {
        this.accountIds = accountIds;
    }

    public Long groupId() { return groupId; }
    public boolean groupIdPresent() { return groupIdPresent; }
    public List<String> accountIds() { return accountIds; }
}
