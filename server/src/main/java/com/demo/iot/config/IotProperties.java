package com.demo.iot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关配置，对应 application.yml 中的 iot 节点。
 */
@ConfigurationProperties(prefix = "iot")
public record IotProperties(Mqtt mqtt, String apiToken, List<String> devices) {

    public record Mqtt(String url, String clientId, String username, String password) {
    }
}
