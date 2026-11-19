package com.demo.iot.mqtt;

import com.demo.iot.config.IotProperties;
import com.demo.iot.device.PcExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 收发网关：后台线程负责连接与重连，Broker 不可用时应用仍可正常启动。
 */
@Component
public class MqttGateway {

    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);

    private final IotProperties props;
    private final PcExecutor pcExecutor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mqtt-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile MqttClient client;

    public MqttGateway(IotProperties props, PcExecutor pcExecutor) {
        this.props = props;
        this.pcExecutor = pcExecutor;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::ensureConnectedQuietly, 0, 5, TimeUnit.SECONDS);
    }

    private void ensureConnectedQuietly() {
        try {
            ensureConnected();
        } catch (Exception e) {
            log.warn("MQTT 尚未连接：{}", e.getMessage());
        }
    }

    private synchronized void ensureConnected() throws MqttException {
        if (client == null) {
            client = new MqttClient(props.mqtt().url(), props.mqtt().clientId(), null);
            client.setCallback(new Callback());
        }
        if (client.isConnected()) {
            return;
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(30);
        String username = props.mqtt().username();
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
            options.setPassword(props.mqtt().password() == null
                    ? new char[0] : props.mqtt().password().toCharArray());
        }
        client.connect(options);
        client.subscribe("home/+/state", 1);
        client.subscribe("home/+/status", 1);
        // 电脑执行器：服务端订阅自己发布的指令，把电脑当作设备
        client.subscribe("home/pc/cmd", 1);
        log.info("MQTT 已连接 {}", props.mqtt().url());
    }

    /**
     * 下发指令，QoS 1。Broker 不可用时抛出 IllegalStateException，由接口层转成 503。
     */
    public void publishCommand(String deviceId, String action) {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            throw new IllegalStateException("MQTT Broker 不可用，请确认 EMQX 已启动");
        }
        try {
            MqttMessage message = new MqttMessage(action.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            current.publish("home/" + deviceId + "/cmd", message);
        } catch (MqttException e) {
            throw new IllegalStateException("指令下发失败：" + e.getMessage(), e);
        }
    }

    public boolean isConnected() {
        MqttClient current = client;
        return current != null && current.isConnected();
    }

    public DeviceState stateOf(String deviceId) {
        return states.getOrDefault(deviceId, new DeviceState(deviceId, "unknown", false, 0));
    }

    public List<DeviceState> allStates() {
        return props.devices().stream().map(this::stateOf).toList();
    }

    private final class Callback implements MqttCallback {

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("MQTT 连接断开：{}", cause == null ? "unknown" : cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String[] parts = topic.split("/");
            if (parts.length != 3) {
                return;
            }
            String deviceId = parts[1];
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
            if ("cmd".equals(parts[2]) && "pc".equals(deviceId)) {
                pcExecutor.execute(payload);
                return;
            }
            if ("status".equals(parts[2])) {
                DeviceState previous = stateOf(deviceId);
                states.put(deviceId, new DeviceState(deviceId, previous.state(),
                        "online".equals(payload), System.currentTimeMillis()));
            } else if ("state".equals(parts[2])) {
                states.put(deviceId, new DeviceState(deviceId, parseState(payload),
                        true, System.currentTimeMillis()));
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // 不需要处理
        }
    }

    private String parseState(String payload) {
        try {
            JsonNode node = mapper.readTree(payload);
            JsonNode state = node.get("state");
            return state == null ? "unknown" : state.asText();
        } catch (Exception e) {
            log.warn("无法解析设备状态：{}", payload);
            return "unknown";
        }
    }

    @PreDestroy
    public void close() {
        scheduler.shutdownNow();
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException ignored) {
            // 关闭阶段忽略异常
        }
    }
}
