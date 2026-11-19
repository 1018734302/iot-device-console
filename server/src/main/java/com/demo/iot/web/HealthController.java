package com.demo.iot.web;

import com.demo.iot.mqtt.MqttGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 探活接口，不需要 Token，方便启动后确认服务状态。
 */
@RestController
public class HealthController {

    private final MqttGateway gateway;

    public HealthController(MqttGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "mqtt", gateway.isConnected() ? "up" : "down");
    }
}
