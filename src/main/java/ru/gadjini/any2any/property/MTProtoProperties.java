package ru.gadjini.any2any.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mtproto")
public class MTProtoProperties {

    private String api;

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }
}
