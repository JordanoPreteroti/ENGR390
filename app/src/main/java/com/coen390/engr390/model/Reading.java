package com.coen390.engr390.model;

/** One line from the ESP32's /data endpoint: raw,volts,rawBAC,estBAC,temp,hum */
public class Reading {
    private final int raw;
    private final double volts;
    private final double deviceRawBac;
    private final double deviceEstBac;
    private final double tempC;
    private final double humidity;
    private final long timestamp;

    public Reading(int raw, double volts, double deviceRawBac, double deviceEstBac,
                   double tempC, double humidity) {
        this.raw = raw;
        this.volts = volts;
        this.deviceRawBac = deviceRawBac;
        this.deviceEstBac = deviceEstBac;
        this.tempC = tempC;
        this.humidity = humidity;
        this.timestamp = System.currentTimeMillis();
    }

    public int getRaw() { return raw; }
    public double getVolts() { return volts; }
    public double getDeviceRawBac() { return deviceRawBac; }
    public double getDeviceEstBac() { return deviceEstBac; }
    public double getTempC() { return tempC; }
    public double getHumidity() { return humidity; }
    public long getTimestamp() { return timestamp; }
}
