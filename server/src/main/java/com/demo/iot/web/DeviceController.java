package com.demo.iot.web;

import com.demo.iot.device.AuditLog;
import com.demo.iot.mqtt.DeviceState;
import com.demo.iot.mqtt.MqttGateway;
import com.demo.iot.config.IotProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Map<String, Set<String>> ALLOWED_ACTIONS = Map.of(
            "light", Set.of("on", "off", "toggle"),
            "pc", Set.of("beep", "open", "lock", "calc", "notify",
                    "taskmgr", "explorer", "desktop", "settings", "mute"));

    private final MqttGateway gateway;
    private final IotProperties props;
    private final AuditLog auditLog;

    public DeviceController(MqttGateway gateway, IotProperties props, AuditLog auditLog) {
        this.gateway = gateway;
        this.props = props;
        this.auditLog = auditLog;
    }

    @PostMapping("/{deviceId}/commands")
    public CommandResponse command(@PathVariable String deviceId,
                                   @Valid @RequestBody CommandRequest request) {
        requireRegistered(deviceId);
        requireAllowedAction(deviceId, request.action());
        try {
            gateway.publishCommand(deviceId, request.action());
        } catch (IllegalStateException e) {
            auditLog.record(deviceId, request.action(), false, "下发失败");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        auditLog.record(deviceId, request.action(), true, "已下发");
        return new CommandResponse(true, deviceId, request.action(), System.currentTimeMillis());
    }

    @GetMapping("/{deviceId}")
    public DeviceState state(@PathVariable String deviceId) {
        requireRegistered(deviceId);
        return gateway.stateOf(deviceId);
    }

    @GetMapping
    public List<DeviceState> list() {
        return gateway.allStates();
    }

    private void requireRegistered(String deviceId) {
        if (!props.devices().contains(deviceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未注册的设备：" + deviceId);
        }
    }

    private void requireAllowedAction(String deviceId, String action) {
        Set<String> allowed = ALLOWED_ACTIONS.getOrDefault(deviceId, Set.of());
        if (!allowed.contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的动作：" + action);
        }
    }

    public record CommandRequest(@NotBlank String action) {
    }

    public record CommandResponse(boolean ok, String deviceId, String action, long ts) {
    }
}
