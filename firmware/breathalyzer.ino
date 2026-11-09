#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_BME680.h>

const int MQ3_PIN = A0;
const int BATTERY_PIN = A1;
Adafruit_BME680 bme;

const float STANDARD_TEMP = 20.0;
const float STANDARD_HUMID = 65.0;

void setup() {
  Serial.begin(115200);

  pinMode(MQ3_PIN, INPUT);
  pinMode(BATTERY_PIN, INPUT);

  if (!bme.begin(0x77)) {
    Serial.println(F("Could not find a valid BME680 sensor, check wiring!"));
    while (1);
  }

  bme.setTemperatureOversampling(BME680_OS_8X);
  bme.setHumidityOversampling(BME680_OS_2X);
}

void loop() {
  if (!bme.performReading()) {
    Serial.println(F("Failed to perform reading from BME680!"));
    delay(2000);
    return;
  }

  float temperature = bme.temperature;
  float humidity = bme.humidity;

  int mq3Raw = analogRead(MQ3_PIN);
  float mq3Voltage = mq3Raw * (5.0 / 1023.0);
  if (mq3Voltage <= 0) mq3Voltage = 0.001;

  float tempFactor = 1.0 + 0.015 * (temperature - STANDARD_TEMP);
  float humidFactor = 1.0 + 0.008 * (humidity - STANDARD_HUMID);
  float compensatedVoltage = mq3Voltage / (tempFactor * humidFactor);

  float mgL = 0.67 * compensatedVoltage;
  float estimatedBAC = mgL * 0.1;
  float RawBAC = 0.67 * mq3Voltage * 0.1;

  int batRaw = analogRead(BATTERY_PIN);
  float batVoltage = batRaw * (5.0 / 1023.0) * 2.0;
  int batPercent = constrain((int)((batVoltage - 3.2) / (4.2 - 3.2) * 100), 0, 100);

  Serial.print(mq3Raw);
  Serial.print(",");
  Serial.print(mq3Voltage, 2);
  Serial.print(",");
  Serial.print(RawBAC, 4);
  Serial.print(",");
  Serial.print(estimatedBAC, 4);
  Serial.print(",");
  Serial.print(temperature, 1);
  Serial.print(",");
  Serial.print(humidity, 1);
  Serial.print(",");
  Serial.println(batPercent);

  delay(1000);
}