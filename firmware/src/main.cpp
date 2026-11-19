#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "secrets.h"

// 设备标识，必须与 server/src/main/resources/application.yml 的 devices 白名单一致
constexpr char DEVICE_ID[] = "light";

// ESP32 DevKit 板载 LED 一般接 GPIO2，高电平点亮。
// 若你的板子是低电平点亮，把 LED_ON / LED_OFF 对调即可。
constexpr int LED_PIN = 2;
constexpr int LED_ON = HIGH;
constexpr int LED_OFF = LOW;

WiFiClient net;
PubSubClient mqtt(net);

String topicCmd;
String topicState;
String topicStatus;

unsigned long lastWifiAttempt = 0;
unsigned long lastMqttAttempt = 0;

void publishState() {
    JsonDocument doc;
    doc["state"] = (digitalRead(LED_PIN) == LED_ON) ? "on" : "off";
    char buffer[64];
    size_t len = serializeJson(doc, buffer, sizeof(buffer));
    mqtt.publish(topicState.c_str(), reinterpret_cast<const uint8_t *>(buffer), len, true);
    Serial.printf("上报状态：%s\n", buffer);
}

void onMessage(char *topic, byte *payload, unsigned int length) {
    String command;
    command.reserve(length);
    for (unsigned int i = 0; i < length; i++) {
        command += static_cast<char>(payload[i]);
    }
    command.trim();

    bool current = digitalRead(LED_PIN) == LED_ON;
    bool target;
    if (command == "on") {
        target = true;
    } else if (command == "off") {
        target = false;
    } else if (command == "toggle") {
        target = !current;
    } else {
        Serial.printf("忽略未知指令：%s\n", command.c_str());
        return;
    }

    digitalWrite(LED_PIN, target ? LED_ON : LED_OFF);
    Serial.printf("执行指令：%s -> %s\n", command.c_str(), target ? "on" : "off");
    publishState();
}

bool connectMqtt() {
    Serial.print("连接 MQTT ...");
    bool ok;
    if (strlen(MQTT_USERNAME) > 0) {
        ok = mqtt.connect(DEVICE_ID, MQTT_USERNAME, MQTT_PASSWORD,
                          topicStatus.c_str(), 1, true, "offline");
    } else {
        ok = mqtt.connect(DEVICE_ID, nullptr, nullptr,
                          topicStatus.c_str(), 1, true, "offline");
    }
    if (!ok) {
        Serial.printf("失败，rc=%d\n", mqtt.state());
        return false;
    }
    Serial.println("成功");
    mqtt.publish(topicStatus.c_str(), "online", true);
    mqtt.subscribe(topicCmd.c_str(), 1);
    publishState();
    return true;
}

void setup() {
    Serial.begin(115200);
    delay(200);
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LED_OFF);

    topicCmd = String("home/") + DEVICE_ID + "/cmd";
    topicState = String("home/") + DEVICE_ID + "/state";
    topicStatus = String("home/") + DEVICE_ID + "/status";

    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.printf("连接 Wi-Fi：%s\n", WIFI_SSID);

    mqtt.setServer(MQTT_HOST, MQTT_PORT);
    mqtt.setCallback(onMessage);
    mqtt.setBufferSize(512);
}

void loop() {
    if (WiFi.status() != WL_CONNECTED) {
        if (millis() - lastWifiAttempt > 5000) {
            lastWifiAttempt = millis();
            Serial.println("Wi-Fi 断开，重试中 ...");
            WiFi.reconnect();
        }
        delay(50);
        return;
    }

    if (!mqtt.connected() && millis() - lastMqttAttempt > 5000) {
        lastMqttAttempt = millis();
        connectMqtt();
    }

    mqtt.loop();
    delay(10);
}
