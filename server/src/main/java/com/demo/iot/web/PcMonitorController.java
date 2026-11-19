package com.demo.iot.web;

import com.demo.iot.device.AuditLog;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.List;

/**
 * 电脑侧监控接口：系统指标、屏幕截图、审计记录。
 */
@RestController
@RequestMapping("/api/pc")
public class PcMonitorController {

    private static final int MAX_SHOT_WIDTH = 1280;

    private final AuditLog auditLog;

    public PcMonitorController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @GetMapping("/stats")
    public Stats stats() throws Exception {
        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        File disk = new File("C:\\");

        double cpu = os.getCpuLoad();
        if (cpu < 0) {
            cpu = 0;
        }
        long memTotal = os.getTotalMemorySize();
        long memUsed = memTotal - os.getFreeMemorySize();

        return new Stats(
                InetAddress.getLocalHost().getHostName(),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                runtime.getUptime() / 1000,
                Math.min(cpu, 1.0),
                memUsed,
                memTotal,
                disk.getTotalSpace() - disk.getUsableSpace(),
                disk.getTotalSpace(),
                InetAddress.getLocalHost().getHostAddress());
    }

    @GetMapping(value = "/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> screenshot() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境不支持截图");
        }
        var size = Toolkit.getDefaultToolkit().getScreenSize();
        BufferedImage shot = new Robot().createScreenCapture(new Rectangle(size));

        double scale = Math.min(1.0, (double) MAX_SHOT_WIDTH / size.width);
        int width = (int) (size.width * scale);
        int height = (int) (size.height * scale);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(shot, 0, 0, width, height, null);
        g.dispose();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(out, "png", buffer);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(buffer.toByteArray());
    }

    @GetMapping("/audit")
    public List<AuditLog.Entry> audit() {
        return auditLog.recent();
    }

    public record Stats(String host, String os, long uptimeSeconds, double cpu,
                        long memUsed, long memTotal, long diskUsed, long diskTotal, String ip) {
    }
}
