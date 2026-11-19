# iPhone 物联网控制 Demo — 技术规格说明书

- 版本：v0.1
- 日期：2026-11-19
- 状态：待评审
- 作者：开发（单人项目）

---

## 1. 背景与目标

用 iPhone（系统「快捷指令」）控制局域网内的 ESP32 设备（继电器/灯），由自建 Java 服务完成协议转换与鉴权，MQTT 作为设备侧通信通道。

**目标**

1. 点击快捷指令后，设备动作时延 ≤ 1 秒（局域网内）。
2. 全链路运行在局域网，零云服务成本。
3. 架构可扩展到多设备、多动作（开/关/切换）。

**非目标（v1 不做）**

- 公网远程控制（预留方案，见 §12）
- 自建 iOS App、HomeKit 接入
- 多用户、账号体系、权限分级
- 历史数据持久化与图表

---

## 2. 名词定义

| 名词 | 含义 |
|---|---|
| 指令端 | iPhone「快捷指令」App |
| 服务端 | Spring Boot 网关，接收 HTTP 并转发 MQTT |
| Broker | EMQX，MQTT 消息中间件 |
| 设备端 | ESP32 开发板 + 继电器/LED |
| deviceId | 设备逻辑标识，如 `light` |

---

## 3. 系统架构

```
┌──────────────┐  HTTP/JSON  ┌──────────────────┐  MQTT publish  ┌────────┐  subscribe  ┌────────────┐
│ iPhone       │ ──────────> │ Spring Boot 网关  │ ─────────────> │ EMQX   │ ──────────> │ ESP32      │
│ 快捷指令      │ <────────── │ :8081            │ <───────────── │ :1883  │ <────────── │ + 继电器    │
└──────────────┘  状态查询    └──────────────────┘  MQTT state     └────────┘   state     └────────────┘
```

**职责边界**

- 快捷指令：只负责发 HTTP 请求，不保存任何协议细节。
- 服务端：鉴权、参数校验、HTTP→MQTT 转换、设备状态缓存、超时与错误映射。
- Broker：消息路由、QoS、遗嘱消息（LWT）。
- 设备端：订阅指令、驱动 GPIO、回报状态、断线重连。

---

## 4. 功能需求

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-1 | 下发开关指令：on / off / toggle | P0 |
| FR-2 | 查询单个设备当前状态 | P0 |
| FR-3 | 查询设备列表 | P1 |
| FR-4 | 设备在线/离线状态（MQTT 遗嘱消息） | P1 |
| FR-5 | 请求鉴权：Header `X-Token` 校验 | P0 |
| FR-6 | 服务端在 Broker 不可用时返回 503，不阻塞请求线程超 2s | P1 |
| FR-7 | 电脑执行器：无硬件时用 beep / notify / open / lock / calc / taskmgr / explorer / desktop / settings / mute 验证链路，动作白名单限定 | P1 |
| FR-8 | 设备监控：主机名、系统版本、CPU、内存、磁盘、运行时长 | P1 |
| FR-9 | 远程截图：抓取当前桌面，最大宽度 1280px 回传 | P1 |
| FR-10 | 操作审计：最近 50 条指令与执行结果，内存存储 | P1 |

---

## 5. 接口契约

### 5.1 HTTP API

**下发指令**

```
POST /api/devices/{deviceId}/commands
Headers:
  Content-Type: application/json
  X-Token: <API_TOKEN>
Body:
  { "action": "on" }        // on | off | toggle
Response 200:
  { "ok": true, "deviceId": "light", "action": "on", "ts": 1732000000000 }
Response 401: Token 错误
Response 404: deviceId 未注册
Response 503: Broker 不可用
```

**查询状态**

```
GET /api/devices/{deviceId}
Response 200:
  { "deviceId": "light", "state": "on", "online": true, "updatedAt": 1732000000000 }
```

**设备列表**

```
GET /api/devices
Response 200:
  [ { "deviceId": "light", "state": "off", "online": true }, ... ]
```

### 5.2 MQTT Topic

| 方向 | Topic | Payload | QoS | Retain |
|---|---|---|---|---|
| 下行 | `home/{deviceId}/cmd` | `on` / `off` / `toggle` | 1 | false |
| 上行 | `home/{deviceId}/state` | `{"state":"on"}`（时间戳由服务端补齐） | 1 | true |
| 状态 | `home/{deviceId}/status` | `online` / `offline`（LWT） | 1 | true |

### 5.3 超时与失败策略

- 服务端 publish 超时：2 秒，失败返回 503。
- 指令发出后不等待设备 ACK（异步）；状态以 `state` 主题为准。
- 快捷指令侧超时：5 秒，失败弹提示。

---

## 6. 数据模型

```java
record DeviceState(
    String deviceId,
    String state,      // on | off | unknown
    boolean online,
    long updatedAt
)
```

- v1：`ConcurrentHashMap` 内存存储，服务重启后状态丢失（可接受）。
- v1.1：接 H2 持久化，保留最近 7 天状态变更。

设备注册：`application.yml` 中静态配置设备白名单，未注册的 deviceId 直接 404。

---

## 7. 技术栈

| 层次 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 设备端芯片 | ESP32 DevKit（ESP32-WROOM-32） | — | 仅支持 2.4GHz Wi-Fi |
| 设备端框架 | Arduino Framework + PlatformIO | PlatformIO 6.x | 比 Arduino IDE 更适合版本管理 |
| 设备端库 | PubSubClient / ArduinoJson | 2.8 / 7.x | MQTT 客户端、JSON 序列化 |
| 消息中间件 | EMQX | 5.x（Docker） | 内置 Dashboard 便于调试 |
| 运行时 | JDK | 17 LTS | 本机已装 17.0.20，Spring Boot 3.3 最低要求 17 |
| 后端框架 | Spring Boot | 3.3.x | `spring-boot-starter-web` |
| 构建工具 | Maven | 3.9.x | — |
| MQTT 客户端 | Eclipse Paho MQTT v3 | 1.2.5 | 与 Spring Boot 3 兼容性稳定 |
| 参数校验 | `spring-boot-starter-validation` | 随 Boot | 指令白名单校验 |
| 存储 | 内存（v1）/ H2（v1.1） | — | v1 不引入数据库 |
| 手机端 | iOS 快捷指令 | iOS 16+ | 「获取 URL 内容」动作，POST JSON |
| 测试页 | 原生 HTML/CSS + MQTT.js | 5.x | 浏览器模拟 ESP32，通过 WebSocket 连 EMQX 8083 |
| 截图 | java.awt.Robot + ImageIO | JDK 17 | 免第三方依赖，需非 headless |
| 容器 | Docker Desktop | 最新 | 只跑 EMQX |
| 串口驱动 | CP210x / CH340 | 按板载芯片 | Windows 需装驱动 |
| 版本管理 | Git | — | 敏感配置文件不入库 |

**选型说明**

- 不用 Node-RED/Home Assistant：避免引入第二套运行时，Java 服务端完全可控。
- 不用 Spring Integration MQTT：当前只有收发两类消息，Paho 原生 API 更轻。
- 不用云 IoT 平台：局域网场景无必要，且增加账号与费用依赖。

---

## 8. 配置与密钥管理（重要）

**禁止通过聊天、截图、代码提交传递任何真实密码。** 所有敏感值由开发者本人在本地填写。

| 配置项 | 存放位置 | 说明 |
|---|---|---|
| `WIFI_SSID` / `WIFI_PASSWORD` | `firmware/include/secrets.h` | 不入库，模板为 `secrets.example.h` |
| `MQTT_HOST` / `MQTT_PORT` / `MQTT_USER` / `MQTT_PASSWORD` | `server/src/main/resources/application-local.yml` | 不入库，模板为 `application-local.example.yml` |
| `API_TOKEN` | 同上 | ≥32 位随机字符串，可用 `openssl rand -hex 32` 生成 |
| `SERVER_BASE_URL` | 快捷指令内手填 | 如 `http://192.168.1.10:8081` |

仓库中同时提交 `.gitignore`，包含：`secrets.h`、`application-local.yml`、`*.env`。

---

## 9. 非功能需求

| 类别 | 指标 |
|---|---|
| 时延 | 局域网内 P95 < 1s（快捷指令 → 设备动作） |
| 重连 | Broker 重启后，ESP32 在 5s 内自动重连并重新订阅 |
| 安全 | 服务仅监听局域网；v1 允许 EMQX 匿名（不暴露公网），v1.1 再启用用户名密码；Token 校验失败不触发设备动作 |
| 日志 | 记录 deviceId、action、结果、耗时；禁止记录 Token 与密码 |
| 资源 | ESP32 固件 RAM 占用 < 40KB；服务端常驻内存 < 300MB |
| 可维护性 | 单文件配置，改设备不需要改代码 |

---

## 10. 验收标准

| 编号 | 场景 | 通过条件 |
|---|---|---|
| AC-1 | 点击「开灯」快捷指令 | 1 秒内继电器吸合/LED 亮 |
| AC-2 | 断开 ESP32 电源 10 秒后恢复 | 自动重连，可再次控制 |
| AC-3 | 使用错误 Token 请求 | 返回 401，设备无动作 |
| AC-4 | 重启 Spring Boot 服务 | 服务恢复后仍可控制（状态可丢失） |
| AC-5 | EMQX Dashboard 观察 | 能看到设备上下线记录与消息计数 |
| AC-6 | 手机切到蜂窝网络 | 请求超时并给出明确提示，不静默失败 |

---

## 11. 里程碑

| 阶段 | 交付内容 |
|---|---|
| M1 | Docker 启动 EMQX；Spring Boot 空工程，健康检查通过 |
| M2 | ESP32 连上 Wi-Fi 与 Broker，能订阅并点亮板载 LED |
| M3 | 快捷指令 → 服务端 → MQTT → 设备，闭环跑通 |
| M4 | 状态回传、设备列表、离线检测、错误码完善 |
| M5（可选） | 公网访问（frp / Cloudflare Tunnel + HTTPS） |

---

## 12. 风险与限制

| 风险 | 影响 | 应对 |
|---|---|---|
| ESP32 只支持 2.4GHz | 连不上 5GHz Wi-Fi | 路由器开启 2.4GHz 频段 |
| 路由器 AP 隔离 | 手机访问不到电脑的 8081 | 关闭 AP isolation |
| Windows 防火墙拦截 | 接口不可达 | 放行 8081，或首次运行时允许 |
| iOS 后台挂起 | 长连接会断 | 快捷指令为前台触发，不受影响 |
| 明文 HTTP | 局域网内可被嗅探 | v1 接受；公网访问必须上 HTTPS |
| MQTT 暴露公网 | 设备可被任意控制 | 1883 只在内网，公网仅开 443 |

**公网方案（M5）**：frp 或 Cloudflare Tunnel 把 8081 映射出去，配 HTTPS 域名；不改 Broker 暴露面。

---

## 13. 已确定事项

| 事项 | 结论 |
|---|---|
| 设备形态 | v1 先用电脑执行器（beep / open / lock）和网页模拟设备验证；ESP32 到货后改烧固件 |
| Broker 部署 | 本机 Docker Desktop 运行 EMQX，不用树莓派或云服务器 |
| 外网访问 | 不做，纯局域网；M5 公网方案本期取消 |
| 设备数量 | 1 台（`light`），内存存储足够，不引入数据库 |
| 本机环境 | JDK 17.0.20、Maven 3.9.16、Docker Desktop 4.88.1 已就绪（docker CLI 未进 PATH，start.ps1 自动定位）；PlatformIO 待安装 |

---

## 14. 交付物清单

```
物联网手机/
├── start.bat                   # 双击启动：EMQX + 服务端 + 打开测试页
├── start.ps1                   # 启动逻辑，自动定位 docker.exe
├── docker-compose.yml          # EMQX（1883 / 8083 / 18083）
├── SPEC.md                     # 本文档
├── .gitignore
├── server/                     # Spring Boot 工程
│   ├── pom.xml
│   └── src/main/
│       ├── java/...
│       └── resources/static/index.html   # 网页测试台 + 模拟设备
├── firmware/                   # PlatformIO 工程
│   ├── platformio.ini
│   ├── src/main.cpp
│   └── include/secrets.example.h
└── docs/
    └── shortcut-setup.md       # 启动、网页自测、快捷指令配置
```
