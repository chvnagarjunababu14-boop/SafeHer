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
 * Version 4: Added account isolation with userId scoping on guardians and journeys.
 * Version 5: Added isSosEnabled flag on guardians and sos_events audit table for Direct SMS SOS.
 * Version 6: Added background journey monitoring and overdue escalation fields to journeys, and journeyId link to sos_events.
 * Version 7: Added isBatteryAlertEnabled opt-in flag on guardians for low/critical battery SMS alerts.
 */
@Database(entities = {GuardianEntity.class, JourneyEntity.class, JourneyEventEntity.class, SosEventEntity.class}, version = 7, exportSchema = false)
public abstract class SafeHerDatabase extends RoomDatabase {
    public abstract GuardianDao guardianDao();
    public abstract JourneyDao journeyDao();
    public abstract JourneyEventDao journeyEventDao();
    public abstract SosEventDao sosEventDao();

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
                "FOREIGN KEY(`journeyId`) REFERENCES `journeys`(`id`) ON DELETE CASCADE " +
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

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `guardians` ADD COLUMN `userId` TEXT DEFAULT NULL");
            database.execSQL("DROP INDEX IF EXISTS `index_guardians_phoneNumber`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_guardians_userId_phoneNumber` ON `guardians` (`userId`, `phoneNumber`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_guardians_userId` ON `guardians` (`userId`)");

            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `userId` TEXT DEFAULT NULL");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_journeys_userId` ON `journeys` (`userId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_journeys_userId_status` ON `journeys` (`userId`, `status`)");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `guardians` ADD COLUMN `isSosEnabled` INTEGER NOT NULL DEFAULT 1");
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `sos_events` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`message` TEXT, " +
                "`latitude` REAL, " +
                "`longitude` REAL, " +
                "`locationAccuracy` REAL, " +
                "`recipientsCount` INTEGER NOT NULL, " +
                "`successCount` INTEGER NOT NULL, " +
                "`failureCount` INTEGER NOT NULL, " +
                "`dispatchMode` TEXT, " +
                "`detailsJson` TEXT" +
                ")"
            );
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_sos_events_userId` ON `sos_events` (`userId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_sos_events_timestamp` ON `sos_events` (`timestamp`)");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `isMonitoringActive` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `lastLatitude` REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `lastLongitude` REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `lastLocationTimestamp` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `overdueSafetyCheckTimestamp` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `safetyCheckAcknowledged` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `overdueEscalationDeadline` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `journeys` ADD COLUMN `overdueEscalationAttempted` INTEGER NOT NULL DEFAULT 0");

            database.execSQL("ALTER TABLE `sos_events` ADD COLUMN `journeyId` INTEGER DEFAULT NULL");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_sos_events_journeyId` ON `sos_events` (`journeyId`)");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Default OFF: battery alerts must be explicitly enabled per guardian.
            database.execSQL(
                "ALTER TABLE `guardians` ADD COLUMN `isBatteryAlertEnabled` INTEGER NOT NULL DEFAULT 0"
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
