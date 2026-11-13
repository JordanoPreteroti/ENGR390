package com.coen390.engr390.model;

/** One person in the group, as stored in the profiles table. */
public class Profile {
    private final long id;
    private final String name;
    private final double weightKg;
    private final String sex;          // "M" or "F"
    private final double lastBac;      // BAC from the most recent test
    private final long lastTestTime;   // ms since epoch, 0 if never tested
    private final int points;
    private final int drinks;

    public Profile(long id, String name, double weightKg, String sex,
                   double lastBac, long lastTestTime, int points, int drinks) {
        this.id = id;
        this.name = name;
        this.weightKg = weightKg;
        this.sex = sex;
        this.lastBac = lastBac;
        this.lastTestTime = lastTestTime;
        this.points = points;
        this.drinks = drinks;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public double getWeightKg() { return weightKg; }
    public String getSex() { return sex; }
    public double getLastBac() { return lastBac; }
    public long getLastTestTime() { return lastTestTime; }
    public int getPoints() { return points; }
    public int getDrinks() { return drinks; }
}
