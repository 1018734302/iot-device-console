package com.demo.iot;

import com.demo.iot.config.IotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IotProperties.class)
public class IotApplication {

    public static void main(String[] args) {
        // 必须在 Spring 初始化 AWT 之前关闭 headless，否则远程截图不可用
        System.setProperty("java.awt.headless", "false");
        SpringApplication application = new SpringApplication(IotApplication.class);
        application.setHeadless(false);
        application.run(args);
    }
}
