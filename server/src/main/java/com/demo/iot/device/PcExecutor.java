package com.demo.iot.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 没有 ESP32 时，把电脑本身当作执行器。
 * 只允许固定白名单动作，不接收任意命令，避免命令注入。
 */
@Component
public class PcExecutor {

    private static final Logger log = LoggerFactory.getLogger(PcExecutor.class);
    private static final String OPEN_URL = System.getenv().getOrDefault("OPEN_URL", "https://example.com");

    private final AuditLog auditLog;
    private Path muteScript;

    public PcExecutor(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    public void execute(String action) {
        try {
            switch (action) {
                case "beep" -> run("powershell", "-NoProfile", "-Command", "[console]::beep(880,300)");
                case "open" -> run("cmd", "/c", "start", "", OPEN_URL);
                case "lock" -> run("rundll32.exe", "user32.dll,LockWorkStation");
                case "calc" -> run("cmd", "/c", "start", "", "calc");
                case "taskmgr" -> run("cmd", "/c", "start", "", "taskmgr");
                case "explorer" -> run("explorer.exe");
                case "desktop" -> run("powershell", "-NoProfile", "-Command",
                        "(New-Object -ComObject Shell.Application).ToggleDesktop()");
                case "settings" -> run("cmd", "/c", "start", "", "ms-settings:");
                case "mute" -> runMute();
                case "notify" -> run("powershell", "-NoProfile", "-Command",
                        "Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show('来自设备运维控制台的消息','设备通知')");
                default -> {
                    log.warn("忽略未知的电脑动作：{}", action);
                    auditLog.record("pc", action, false, "未知动作");
                    return;
                }
            }
            log.info("电脑执行器已执行动作：{}", action);
            auditLog.record("pc", action, true, "已执行");
        } catch (Exception e) {
            log.error("电脑动作执行失败：{}", action, e);
            auditLog.record("pc", action, false, "执行失败");
        }
    }

    private void run(String... command) throws IOException {
        new ProcessBuilder(command).start();
    }

    /**
     * 通过 Core Audio 直接切换系统静音，比模拟媒体键可靠。
     * 脚本从 jar 内解压到临时文件后执行。
     */
    private void runMute() throws IOException {
        if (muteScript == null || !Files.exists(muteScript)) {
            try (InputStream in = PcExecutor.class.getResourceAsStream("/mute.ps1")) {
                if (in == null) {
                    throw new IOException("找不到 mute.ps1");
                }
                Path file = Files.createTempFile("iot-mute-", ".ps1");
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                file.toFile().deleteOnExit();
                muteScript = file;
            }
        }
        new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", muteScript.toString()).start();
    }
}
