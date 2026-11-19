package com.demo.iot.mqtt;

/**
 * 设备状态快照。state 取值：on / off / unknown。
 */
public record DeviceState(String deviceId, String state, boolean online, long updatedAt) {
}
