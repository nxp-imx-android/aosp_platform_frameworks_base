/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.security.talisman;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.os.Clock;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

class TalismanSqliteDatabase extends TalismanDatabase {
    private static final String TAG = "TalismanDatabase";
    private static final Duration MINIMUM_VALID_DURATION = Duration.ofMinutes(15);

    private final OpenHelper mOpenHelper;
    private final Clock mClock;

    static TalismanSqliteDatabase create(
            @NonNull Context context, @NonNull File databaseFile, @NonNull Clock clock) {
        OpenHelper helper = new OpenHelper(context, databaseFile);
        // Initialize the database if needed.
        helper.getWritableDatabase();
        return new TalismanSqliteDatabase(helper, clock);
    }

    @Override
    @NonNull
    TalismanSetWithKey getTalismanSet(@TalismanSet.Type int type)
            throws TalismanExhaustedException {
        DatabaseHelper db = getWritableDatabase();
        return db.runInTransaction(
                () -> {
                    Pair<TalismanSetWithKey, Long> talismanAndRowId =
                            db.getTalisman(
                                    type,
                                    Instant.ofEpochMilli(mClock.currentTimeMillis())
                                            .plus(MINIMUM_VALID_DURATION));
                    if (talismanAndRowId == null) {
                        throw new TalismanExhaustedException();
                    }
                    db.deleteTalisman(talismanAndRowId.second);
                    return talismanAndRowId.first;
                });
    }

    @Override
    void addTalismanSets(@NonNull List<TalismanSetWithKey> talismans) {
        if (talismans.isEmpty()) {
            return;
        }
        DatabaseHelper db = getWritableDatabase();
        db.runInTransaction(
                () -> {
                    for (TalismanSetWithKey talisman : talismans) {
                        db.insertTalisman(talisman);
                    }
                });
    }

    private TalismanSqliteDatabase(OpenHelper helper, Clock clock) {
        mOpenHelper = helper;
        mClock = clock;
    }

    private static class Schema {
        private static class Talisman {
            private static String name() {
                return "Talisman";
            }

            private static final String ROWID = "rowid";
            private static final String PUBLIC_KEY = "publicKey";
            private static final String PRIVATE_KEY = "privateKey";
            private static final String TYPE = "type";
            private static final String TALISMAN_SET = "talismanSet";
            private static final String CREATED_AT = "createdAt";
            private static final String EXPIRE_AT = "expireAt";
        }
    }

    private DatabaseHelper getWritableDatabase() {
        return new DatabaseHelper(mOpenHelper.getWritableDatabase());
    }

    private static class DatabaseHelper {
        private final SQLiteDatabase mDatabase;

        DatabaseHelper(SQLiteDatabase db) {
            mDatabase = db;
        }

        @FunctionalInterface
        interface VoidTransactionBody<T extends Throwable> {
            void run() throws T;
        }

        @FunctionalInterface
        interface GenericTransactionBody<R, T extends Throwable> {
            R run() throws T;
        }

        @SuppressWarnings("Finally")
        <R, T extends Throwable> R runInTransaction(GenericTransactionBody<R, T> body) throws T {
            mDatabase.beginTransaction();
            try {
                R result = body.run();
                mDatabase.setTransactionSuccessful();
                return result;
            } finally {
                try {
                    mDatabase.endTransaction();
                } catch (SQLiteException e) {
                    // Ignore the no transaction is active error. It happens when an error
                    // caused the transaction to rollback.
                    if (!e.getMessage().contains("no transaction is active")) {
                        // Log the exception to avoid suppressing one from run().
                        Slog.e(TAG, e.toString());
                    }
                }
            }
        }

        <T extends Throwable> void runInTransaction(VoidTransactionBody<T> body) throws T {
            runInTransaction(
                    () -> {
                        body.run();
                        return null;
                    });
        }

        Pair<TalismanSetWithKey, Long> getTalisman(
                @TalismanSet.Type int type, Instant expireAfter) {
            Cursor cursor =
                    mDatabase.query(
                            /* distinct */ false,
                            /* table */ Schema.Talisman.name(),
                            /* columns */ new String[] {
                                Schema.Talisman.ROWID,
                                Schema.Talisman.PUBLIC_KEY,
                                Schema.Talisman.PRIVATE_KEY,
                                Schema.Talisman.TALISMAN_SET,
                                Schema.Talisman.CREATED_AT,
                                Schema.Talisman.EXPIRE_AT
                            },
                            /* selection */ "type = ? AND expireAt > ?",
                            /* selectionArgs */ new String[] {
                                String.valueOf(type), String.valueOf(expireAfter.toEpochMilli()),
                            },
                            /* groupBy */ null,
                            /* having */ null,
                            /* orderBy */ "expireAt ASC",
                            /* limit */ "1");
            try {
                if (!cursor.moveToNext()) {
                    return null;
                }
                long rowid = cursor.getLong(cursor.getColumnIndexOrThrow(Schema.Talisman.ROWID));
                TalismanKey key =
                        new TalismanKey(
                                cursor.getBlob(
                                        cursor.getColumnIndexOrThrow(Schema.Talisman.PUBLIC_KEY)),
                                cursor.getBlob(
                                        cursor.getColumnIndexOrThrow(Schema.Talisman.PRIVATE_KEY)));
                TalismanSet talisman =
                        new TalismanSet.Builder()
                                .setType(type)
                                .setPublicKey(key.publicKey())
                                .setTalismanSet(
                                        cursor.getBlob(
                                                cursor.getColumnIndexOrThrow(
                                                        Schema.Talisman.TALISMAN_SET)))
                                .setCreatedAt(
                                        Instant.ofEpochMilli(
                                                cursor.getLong(
                                                        cursor.getColumnIndexOrThrow(
                                                                Schema.Talisman.CREATED_AT))))
                                .setExpireAt(
                                        Instant.ofEpochMilli(
                                                cursor.getLong(
                                                        cursor.getColumnIndexOrThrow(
                                                                Schema.Talisman.EXPIRE_AT))))
                                .build();
                return new Pair(new TalismanSetWithKey(key, talisman), rowid);
            } finally {
                cursor.close();
            }
        }

        void insertTalisman(TalismanSetWithKey talismanSetWithKey) {
            TalismanKey key = talismanSetWithKey.key();
            TalismanSet talisman = talismanSetWithKey.talismanSet();
            ContentValues values = new ContentValues();
            values.put(Schema.Talisman.PUBLIC_KEY, key.publicKey().toByteArray());
            values.put(Schema.Talisman.PRIVATE_KEY, key.privateKey().toByteArray());
            values.put(Schema.Talisman.TYPE, talisman.type());
            values.put(Schema.Talisman.TALISMAN_SET, talisman.talismanSet().toByteArray());
            values.put(Schema.Talisman.CREATED_AT, talisman.createdAt().toEpochMilli());
            values.put(Schema.Talisman.EXPIRE_AT, talisman.expireAt().toEpochMilli());
            try {
                mDatabase.insertWithOnConflict(
                        Schema.Talisman.name(),
                        /* nullColumnHack */ null,
                        values,
                        SQLiteDatabase.CONFLICT_ROLLBACK);
            } catch (SQLiteConstraintException e) {
                throw new IllegalArgumentException(
                        "Failed to insert talisman into Talisman table.");
            }
        }

        void deleteTalisman(long rowid) {
            long deletedRows =
                    mDatabase.delete(
                            Schema.Talisman.name(),
                            Schema.Talisman.ROWID + " = ?",
                            new String[] {String.valueOf(rowid)});
            if (deletedRows != 1) {
                throw new IllegalStateException(
                        "failed to delete the talisman from the pending table");
            }
        }
    }

    private static class OpenHelper extends SQLiteOpenHelper {
        private static final int SCHEMA_VERSION = 1;

        OpenHelper(Context context, File databaseFile) {
            super(
                    context,
                    databaseFile.getPath(),
                    SCHEMA_VERSION,
                    new SQLiteDatabase.OpenParams.Builder()
                            .setOpenFlags(
                                    SQLiteDatabase.CREATE_IF_NECESSARY
                                            | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
                            // Losing commits is not the end of the world, but
                            // may result in using the same Talisman more than
                            // once. Since talisman is not performance critical,
                            // we use FULL for slightly better privacy.
                            .setJournalMode(SQLiteDatabase.SYNC_MODE_FULL)
                            .build());
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Slog.i(TAG, "Creating Talisman database " + getDatabaseName());
            db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Talisman (
                          publicKey   BLOB     NOT NULL,
                          privateKey   BLOB     NOT NULL,
                          type        INTEGER  NOT NULL,
                          talismanSet BLOB     NOT NULL,
                          createdAt   INTEGER  NOT NULL,
                          expireAt    INTEGER  NOT NULL,
                          PRIMARY KEY (type, expireAt, publicKey ASC),
                          UNIQUE (publicKey)
                    );
                    """);
        }

        // TODO(b/418280383): Handle upgrade and downgrade more gracefully.

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            resetDatabase(db);
            onCreate(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            resetDatabase(db);
            onCreate(db);
        }

        private void resetDatabase(SQLiteDatabase db) {
            db.execSQL("DROP TABLE Talisman");
        }
    }
}
