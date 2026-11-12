package com.sky.job.it.plugin;

import com.sky.plugin.api.AccountField;
import com.sky.plugin.api.AccountFieldType;
import com.sky.plugin.api.AccountId;
import com.sky.plugin.api.PluginAccount;

@PluginAccount("Frontend Integration Account")
public class FrontendIntegrationAccount {
    @AccountId
    @AccountField(label = "Account ID", type = AccountFieldType.TEXT, required = true, readonly = true)
    private String accountId;

    @AccountField(label = "Secret", type = AccountFieldType.PASSWORD, sensitive = true)
    private String secret;

    @AccountField(label = "Region", type = AccountFieldType.SELECT, options = {"east", "west"})
    private String region;

    @AccountField(label = "Expires At", type = AccountFieldType.DATETIME)
    private String expireAt;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(String expireAt) {
        this.expireAt = expireAt;
    }
}
