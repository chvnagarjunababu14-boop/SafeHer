package com.safeher.app.data.local.db;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Main Room database for SafeHer local persistence.
 * Version 1: Initial trusted guardians table.
 * Version 2: Added safety journeys table with indexes (Phase 5).
 * Version 3: Added persistent journey event history table with indexes (Phase 6).
 */
@Database(entities = {GuardianEntity.class, JourneyEntity.class, JourneyEventEntity.class}, version = 3, exportSchema = false)
public abstract class SafeHerDatabase extends RoomDatabase {
    public abstract GuardianDao guardianDao();
    public abstract JourneyDao journeyDao();
    public abstract JourneyEventDao journeyEventDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `journeys` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT, " +
                "`startLocation` TEXT, " +
                "`destination` TEXT, " +
                "`startLatitude` REAL, " +
                "`startLongitude` REAL, " +
                "`destinationLatitude` REAL, " +
                "`destinationLongitude` REAL, " +
                "`startTimestamp` INTEGER NOT NULL, " +
                "`expectedArrivalTimestamp` INTEGER NOT NULL, " +
                "`completedTimestamp` INTEGER NOT NULL, " +
                "`status` TEXT, " +
                "`lastCheckInTimestamp` INTEGER NOT NULL, " +
                "`checkInCount` INTEGER NOT NULL, " +
                "`checkInHistory` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL" +
                ")"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_journeys_status` ON `journeys` (`status`)"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_journeys_startTimestamp` ON `journeys` (`startTimestamp`)"
            );
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `journey_events` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`journeyId` INTEGER NOT NULL, " +
                "`eventType` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`metadata` TEXT, " +
                "FOREIGN KEY(`journeyId`) REFERENCES `journeys`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE " +
                ")"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_journey_events_journeyId` ON `journey_events` (`journeyId`)"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_journey_events_timestamp` ON `journey_events` (`timestamp`)"
            );
        }
    };

    private static volatile SafeHerDatabase INSTANCE;

    public static SafeHerDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SafeHerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        SafeHerDatabase.class,
                        "safeher_guardians.db"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
