package com.open.demo;

import com.sky.plugin.api.ConfigField;
import com.sky.plugin.api.ConfigFieldType;
import com.sky.plugin.api.PluginConfig;

import java.util.List;

@PluginConfig("Demo Config")
public class DemoConfig {
    @ConfigField(label = "Message", type = ConfigFieldType.TEXT)
    private String message;

    @ConfigField(label = "认证方式", type = ConfigFieldType.RADIO,options = {"密码","ip"})
    private String auth;

    @ConfigField(label = "密码", type = ConfigFieldType.TEXT,visibleWhenField="auth",visibleWhenEquals="密码",required = true)
    private String auth_password;

    @ConfigField(label = "ip", type = ConfigFieldType.TEXT,visibleWhenField="auth",visibleWhenEquals="ip",required = true)
    private String auth_ip;
    @ConfigField(label = "商品",type = ConfigFieldType.OBJECT_LIST,required = true,itemClass = Goods.class)
    private List<Goods> goodsList;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuth() {
        return auth;
    }


    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getAuth_password() {
        return auth_password;
    }

    public void setAuth_password(String auth_password) {
        this.auth_password = auth_password;
    }

    public String getAuth_ip() {
        return auth_ip;
    }

    public void setAuth_ip(String auth_ip) {
        this.auth_ip = auth_ip;
    }

    public List<Goods> getGoodsList() {
        return goodsList;
    }

    public void setGoodsList(List<Goods> goodsList) {
        this.goodsList = goodsList;
    }

    @Override
    public String toString() {
        return "DemoConfig{" +
                "message='" + message + '\'' +
                ", auth='" + auth + '\'' +
                ", auth_password='" + auth_password + '\'' +
                ", auth_ip='" + auth_ip + '\'' +
                ", goodsList=" + goodsList +
                '}';
    }
}
