#include <WiFi.h>
#include <WebServer.h>
#include <Wire.h>
#include "DHT.h"

// network parameters
const char* WIFI_SSID = "Breathalyzer-ESP32";
const char* WIFI_PASS = "breath1234";  

// pin map for ESP32
const int MQ3_PIN = 34;              
const int DHT_PIN = 4;                
#define DHTTYPE DHT11                 

// resistor values for voltage divider
const float R_TOP = 9700.0;
const float R_BOTTOM = 20500.0;
const float DIVIDER_GAIN = (R_TOP + R_BOTTOM) / R_BOTTOM;

const float STANDARD_TEMP = 20.0;    // standard temperature in C
const float STANDARD_HUMID = 65.0;   // standard relative humidity in %

const unsigned long READ_INTERVAL_MS = 200;    // reading rate

// state 
WebServer server(80);
DHT dht(DHT_PIN, DHTTYPE);

char latestLine[96] = "";            
unsigned long lastTick = 0;

void handleData() {
  server.sendHeader("Cache-Control", "no-store");
  if (latestLine[0] == '\0') {
    server.send(503, "text/plain", "no reading yet");
  } else {
    server.send(200, "text/plain", latestLine);
  }
}

//sensors
void setLine(const char* text) {
  snprintf(latestLine, sizeof(latestLine), "%s", text);
  Serial.println(latestLine);
}

// average of 16 samples using ESP32 factory ADC calibration
float readMq3Volts() {
  uint32_t sum = 0;
  for (int i = 0; i < 16; i++) {
    sum += analogReadMilliVolts(MQ3_PIN);
    delay(2);
  }
  float pinVolts = (sum / 16.0) / 1000.0;
  return pinVolts * DIVIDER_GAIN;      // scales back up to the module's 0-5V range with gain from voltage divider
}

void takeReading() {
  float humidity = dht.readHumidity(); // read humidity in %
  float temperature = dht.readTemperature(); // read temperature in Celsius

  if (isnan(humidity) || isnan(temperature)) {
    setLine("Failed to read from DHT sensor!");
    return;
  }

  float mq3Voltage = readMq3Volts();     
  if (mq3Voltage <= 0) mq3Voltage = 0.001;
  int mq3Raw = (int)((mq3Voltage / 5.0) * 1023.0);   // 10-bit scale for ADC

  float tempFactor = 1.0 + (0.015 * (temperature - STANDARD_TEMP));
  float humidFactor = 1.0 + (0.008 * (humidity - STANDARD_HUMID));

  float compensatedVoltage = mq3Voltage / (tempFactor * humidFactor);

  float mgL = 0.67 * compensatedVoltage;
  float estimatedBAC = mgL * 0.1;
  float RawBAC = 0.67 * mq3Voltage * 0.1;

  char line[96];
  snprintf(line, sizeof(line), "%d,%.2f,%.4f,%.4f,%.1f,%.1f", //output
           mq3Raw, mq3Voltage, RawBAC, estimatedBAC, temperature, humidity);
  setLine(line);
}

void setup() {
  Serial.begin(115200);
  analogSetAttenuation(ADC_11db);      // full input range for ESP32 ADC
  dht.begin();

  // create WiFi on ESP32
  WiFi.mode(WIFI_AP);
  if (!WiFi.softAP(WIFI_SSID, WIFI_PASS)) {
    Serial.println("Failed to start the WiFi access point");
  }
  
  Serial.print("WiFi network: ");
  Serial.println(WIFI_SSID);
  Serial.print("Open http://");
  Serial.println(WiFi.softAPIP());

  server.on("/data", handleData);
  server.begin();
}

void loop() {
  server.handleClient();               // answers the Android app requests

  unsigned long now = millis();
  if (now - lastTick >= READ_INTERVAL_MS) {
    lastTick = now;
    takeReading();
  }

  delay(2);                            
}