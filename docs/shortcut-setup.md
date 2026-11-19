# iPhone 快捷指令配置步骤

目标：在 iPhone 桌面点一个图标，让 ESP32 的板载 LED 亮起。

## 0. 一键启动（推荐）

双击项目根目录的 **start.bat**，脚本会自动完成：

1. 启动 EMQX 容器（首次会拉取镜像）
2. 构建并启动 Spring Boot 服务
3. 打开测试网页 `http://localhost:8081/`

停止服务：在窗口按 `Ctrl+C`。窗口不要关，关掉服务就停了。

## 1. 前置检查

1. 双击 `start.bat` 后，`http://localhost:8081/health` 返回 `"mqtt":"up"`
2. 查电脑局域网 IP：PowerShell 执行 `ipconfig`，取 IPv4 地址，例如 `192.168.1.10`
3. 手机与电脑在同一个 Wi-Fi

先用电脑自测一次（PowerShell，替换 IP 和 Token）：

```powershell
curl.exe -X POST "http://192.168.1.10:8081/api/devices/light/commands" `
  -H "Content-Type: application/json" `
  -H "X-Token: dev-token-change-me" `
  -d '{\"action\":\"toggle\"}'
```

返回 `{"ok":true,...}` 说明服务端与 Broker 都通。

## 3. 新建快捷指令

1. 打开「快捷指令」App → 右上角 `+`
2. 搜索并添加操作：**获取 URL 内容**
3. 填写下列字段：

| 字段 | 值 |
|---|---|
| URL | `http://192.168.1.10:8081/api/devices/light/commands` |
| 方法 | `POST` |
| 标头 | 键 `X-Token`，值 `dev-token-change-me` |
| 请求体 | `JSON` |
| 请求体字段 | 键 `action`，类型 `文本`，值 `toggle` |

4. 点右上角名称，改名为「切换灯」，选一个图标
5. 点「完成」

## 2. 网页自测（不需要 ESP32）

服务启动后浏览器会自动打开 `http://localhost:8081/`，也可以手动访问。

1. 勾选「模拟设备」，等状态变成「已连接 EMQX」
2. 点「开灯」，灯圈变绿、状态变「已开灯」
3. 点「关灯」或「切换」，验证双向控制

模拟设备用 WebSocket 连 `ws://localhost:8083/mqtt`，订阅的主题与真机完全相同。这一步跑通，说明服务端、Broker、指令链路都正常，只剩烧录固件。

### 电脑执行器（没有 ESP32 时）

页面上的「远程操作」会真的作用于这台电脑：

| 按钮 | 动作 | 说明 |
|---|---|---|
| 远程响铃 | `beep` | 播放一声系统提示音 |
| 桌面通知 | `notify` | 弹出 Windows 消息框 |
| 锁定屏幕 | `lock` | 立即锁定 Windows |
| 打开网页 | `open` | 默认浏览器打开 `OPEN_URL` 指定的网址 |
| 打开计算器 | `calc` | 启动白名单应用 |
| 任务管理器 | `taskmgr` | 打开任务管理器 |
| 资源管理器 | `explorer` | 打开文件资源管理器 |
| 显示桌面 | `desktop` | 最小化所有窗口 |
| 系统设置 | `settings` | 打开 Windows 设置 |
| 静音 | `mute` | 切换系统静音，再点一次恢复 |
| 一键下班 | 场景 | 响铃后锁屏 |

接口与真机完全相同：`POST /api/devices/pc/commands`。动作白名单写死在 `DeviceController` 里，不会执行任意命令。

### 运维控制台能力

| 模块 | 说明 |
|---|---|
| 设备状态 | 主机名、系统版本、CPU、内存、C 盘、运行时长，5 秒刷新 |
| 远程桌面 | 抓取当前屏幕，缩放后回传；锁屏状态下抓不到画面 |
| 操作审计 | 最近 50 条指令，含时间、设备、动作、结果，存于内存 |
| 演示设备 | 灯的开关与浏览器模拟设备，用于 ESP32 到货前验证链路 |

> 页面与图标均为本地实现，仅用于学习与演示。

用 iPhone 测试：手机连**同一个 Wi-Fi**，Safari 打开 `http://<电脑IP>:8081/`（IP 用 `ipconfig` 查）。打不开时先双击项目根目录的 `allow-firewall.bat`，同意 UAC 后放行 8081 与 8083。

## 4. 测试（真机）

1. 回到桌面，点「切换灯」
2. 首次运行如弹出网络权限提示，选择允许
3. 预期：ESP32 板载 LED 状态翻转，串口打印 `执行指令` 与 `上报状态`
4. 再建两个快捷指令，请求体分别是 `on` 和 `off`
5. 想把语音也接上：把快捷指令命名为「开灯」，直接对 Siri 说「开灯」

## 5. 常见错误

| 现象 | 原因 | 处理 |
|---|---|---|
| 401 `invalid token` | X-Token 与服务端不一致 | 检查 `application.yml` 或环境变量 |
| 404 未注册的设备 | deviceId 不在白名单 | 检查 `iot.devices` 与固件里的 `DEVICE_ID` |
| 503 MQTT Broker 不可用 | EMQX 没启动或端口不通 | `docker ps`，访问 `http://localhost:18083` |
| 请求超时 | 手机与电脑不同网段，或防火墙拦截 | 关闭路由器 AP 隔离；放行 8081 入站 |
| 返回成功但灯不动 | ESP32 未连上 Broker | 看串口日志，核对 `MQTT_HOST` 是否为电脑 IP |
| LED 逻辑相反 | 板子为低电平点亮 | 在 `main.cpp` 对调 `LED_ON` / `LED_OFF` |

## 6. 安全提醒

- `X-Token` 只是一个本地口令，不要发到公开渠道
- 纯局域网方案不要把 8081 或 1883 端口映射到公网
- 换设备/换网络后，记得同步修改 `secrets.h` 并重新烧录
