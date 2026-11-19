#pragma once

// 复制本文件为 secrets.h 并填写真实值。
// secrets.h 已被 .gitignore 忽略，不会提交到仓库，也不要发到聊天里。

#define WIFI_SSID "你的Wi-Fi名称"
#define WIFI_PASSWORD "你的Wi-Fi密码"

// 运行 Spring Boot 服务的电脑局域网 IP，不是 127.0.0.1
#define MQTT_HOST "192.168.1.10"
#define MQTT_PORT 1883

// EMQX 默认允许匿名连接，留空即可；启用认证后填写
#define MQTT_USERNAME ""
#define MQTT_PASSWORD ""
