package com.coen390.engr390.controller;

/**
 * All BAC calculations live here, so the formulas can change without
 * touching the screens (see Design Document 3.2 / 3.4).
 */
public final class BACController {

    public static final double CAUTION_LIMIT = 0.05;
    public static final double LEGAL_LIMIT = 0.08;
    public static final double DANGER_LIMIT = 0.20;
    /** Average rate the body removes alcohol, in BAC % per hour. */
    public static final double ELIMINATION_PER_HOUR = 0.015;

    public enum Status { UNDER, CAUTION, OVER, DANGER }

    private BACController() { }

    // Same constants as the ESP32 sketch so both sides agree.
    private static final double STANDARD_TEMP = 20.0;   // °C
    private static final double STANDARD_HUMID = 65.0;  // % RH
    private static final double TEMP_COEFF = 0.015;
    private static final double HUMID_COEFF = 0.008;
    private static final double MGL_PER_VOLT = 0.67;    // TODO: replace with the SIM-1 curve fit
    private static final double BAC_PER_MGL = 0.1;      // TODO: confirm (a 2100:1 ratio gives 0.21)

    /**
     * Estimates BAC from the MQ-3 voltage, corrected for temperature and humidity.
     * The clean-air baseline measured after warm-up is subtracted, so a sober
     * user reads 0.00 instead of a small positive value.
     */
    public static double estimateBac(double volts, double tempC, double humidity,
                                     double baselineVolts) {
        double tempFactor = 1.0 + TEMP_COEFF * (tempC - STANDARD_TEMP);
        double humidFactor = 1.0 + HUMID_COEFF * (humidity - STANDARD_HUMID);
        double corrected = (volts - Math.max(0.0, baselineVolts)) / (tempFactor * humidFactor);
        if (corrected <= 0) {
            return 0.0;
        }
        return Math.min(corrected * MGL_PER_VOLT * BAC_PER_MGL, 0.40);
    }

    public static Status getStatus(double bac) {
        if (bac >= DANGER_LIMIT) return Status.DANGER;
        if (bac >= LEGAL_LIMIT) return Status.OVER;
        if (bac >= CAUTION_LIMIT) return Status.CAUTION;
        return Status.UNDER;
    }

    /** Estimates the BAC now, based on the last test and the time since then. */
    public static double currentBac(double lastBac, long lastTestTimeMs) {
        if (lastTestTimeMs <= 0) {
            return 0.0;
        }
        double hours = (System.currentTimeMillis() - lastTestTimeMs) / 3_600_000.0;
        return Math.max(0.0, lastBac - ELIMINATION_PER_HOUR * hours);
    }

    public static double hoursUntilSober(double bac) {
        return bac / ELIMINATION_PER_HOUR;
    }

    /** Leaderboard points reward staying under the limit, not drinking more. */
    public static int pointsFor(double bac) {
        switch (getStatus(bac)) {
            case UNDER:
                return 20;
            case CAUTION:
                return 5;
            default:
                return 0;
        }
    }
}
