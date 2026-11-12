package com.open.demo;

import com.sky.plugin.api.AccountField;
import com.sky.plugin.api.AccountFieldType;
import com.sky.plugin.api.AccountId;
import com.sky.plugin.api.PluginAccount;

@PluginAccount("Demo Account")
public class DemoAccount {
    @AccountId
    @AccountField(label = "Account ID", type = AccountFieldType.TEXT, required = true, readonly = true)
    private String accountId;

    @AccountField(label = "Secret", type = AccountFieldType.PASSWORD, sensitive = true)
    private String secret;

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
}
