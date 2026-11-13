package com.coen390.engr390.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.coen390.engr390.model.Profile;

import java.util.ArrayList;
import java.util.List;

/** SQLite database: user profiles, reading history and session scores. */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "breathalyzer.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_PROFILES = "profiles";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_WEIGHT = "weight_kg";
    public static final String COL_SEX = "sex";
    public static final String COL_LAST_BAC = "last_bac";
    public static final String COL_LAST_TEST = "last_test_time";
    public static final String COL_POINTS = "points";
    public static final String COL_DRINKS = "drinks";

    public static final String TABLE_READINGS = "readings";
    public static final String COL_PROFILE_ID = "profile_id";
    public static final String COL_BAC = "bac";
    public static final String COL_TIME = "timestamp";

    private static DatabaseHelper instance;

    /** Use one shared instance so every screen talks to the same database. */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true); // needed for ON DELETE CASCADE
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PROFILES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_WEIGHT + " REAL NOT NULL, "
                + COL_SEX + " TEXT NOT NULL, "
                + COL_LAST_BAC + " REAL NOT NULL DEFAULT 0, "
                + COL_LAST_TEST + " INTEGER NOT NULL DEFAULT 0, "
                + COL_POINTS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DRINKS + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_READINGS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PROFILE_ID + " INTEGER NOT NULL REFERENCES "
                + TABLE_PROFILES + "(" + COL_ID + ") ON DELETE CASCADE, "
                + COL_BAC + " REAL NOT NULL, "
                + COL_TIME + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Fine during development; bump DB_VERSION after changing the tables.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_READINGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROFILES);
        onCreate(db);
    }

    // ---------- Profiles ----------

    public long addProfile(String name, double weightKg, String sex) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_WEIGHT, weightKg);
        values.put(COL_SEX, sex);
        return getWritableDatabase().insert(TABLE_PROFILES, null, values);
    }

    // region Profiles: edit - update only name, weight and sex; id, BAC and readings stay as they are
    public void updateProfile(Profile profile) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, profile.getName());
        values.put(COL_WEIGHT, profile.getWeightKg());
        values.put(COL_SEX, profile.getSex());
        getWritableDatabase().update(TABLE_PROFILES, values, COL_ID + " = ?",
                new String[]{String.valueOf(profile.getId())});
    }
    // endregion

    public List<Profile> getAllProfiles() {
        return queryProfiles(COL_NAME + " COLLATE NOCASE ASC");
    }

    public List<Profile> getLeaderboard() {
        return queryProfiles(COL_POINTS + " DESC, " + COL_NAME + " COLLATE NOCASE ASC");
    }

    public Profile getProfile(long id) {
        try (Cursor c = getReadableDatabase().query(TABLE_PROFILES, null,
                COL_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    /** Deletes the profile; its readings are removed automatically (cascade). */
    public void deleteProfile(long id) {
        getWritableDatabase().delete(TABLE_PROFILES, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    // ---------- Readings and session ----------

    /** Saves a test result and updates the profile's latest BAC, drinks and points. */
    public void addReading(long profileId, double bac, int pointsEarned) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            ContentValues reading = new ContentValues();
            reading.put(COL_PROFILE_ID, profileId);
            reading.put(COL_BAC, bac);
            reading.put(COL_TIME, now);
            db.insert(TABLE_READINGS, null, reading);

            db.execSQL("UPDATE " + TABLE_PROFILES + " SET "
                            + COL_LAST_BAC + " = ?, "
                            + COL_LAST_TEST + " = ?, "
                            + COL_DRINKS + " = " + COL_DRINKS + " + 1, "
                            + COL_POINTS + " = " + COL_POINTS + " + ? "
                            + "WHERE " + COL_ID + " = ?",
                    new Object[]{bac, now, pointsEarned, profileId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Clears points and drink counts for a new night. Reading history is kept. */
    public void resetSession() {
        ContentValues values = new ContentValues();
        values.put(COL_POINTS, 0);
        values.put(COL_DRINKS, 0);
        getWritableDatabase().update(TABLE_PROFILES, values, null, null);
    }

    // ---------- Helpers ----------

    private List<Profile> queryProfiles(String orderBy) {
        List<Profile> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_PROFILES, null,
                null, null, null, null, orderBy)) {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        }
        return list;
    }

    private Profile fromCursor(Cursor c) {
        return new Profile(
                c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_NAME)),
                c.getDouble(c.getColumnIndexOrThrow(COL_WEIGHT)),
                c.getString(c.getColumnIndexOrThrow(COL_SEX)),
                c.getDouble(c.getColumnIndexOrThrow(COL_LAST_BAC)),
                c.getLong(c.getColumnIndexOrThrow(COL_LAST_TEST)),
                c.getInt(c.getColumnIndexOrThrow(COL_POINTS)),
                c.getInt(c.getColumnIndexOrThrow(COL_DRINKS)));
    }
}
