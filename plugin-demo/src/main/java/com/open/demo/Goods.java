package com.open.demo;

import com.sky.plugin.api.ConfigField;

public class Goods {
    @ConfigField(label = "商品名",required = true)
    private String name;
    @ConfigField(label = "数量")
    private String number;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return "Goods{" +
                "name='" + name + '\'' +
                ", number='" + number + '\'' +
                '}';
    }
}
