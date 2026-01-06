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
import android.security.talisman.TrustConfiguration;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.os.Clock;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

class TrustTokenSqliteDatabase extends TrustTokenDatabase {
    private static final String TAG = "TrustTokenDatabase";
    private static final Duration MINIMUM_VALID_DURATION = Duration.ofMinutes(15);

    private final OpenHelper mOpenHelper;
    private final Clock mClock;

    static TrustTokenSqliteDatabase create(
            @NonNull Context context, @NonNull File databaseFile, @NonNull Clock clock) {
        OpenHelper helper = new OpenHelper(context, databaseFile);
        // Initialize the database if needed.
        helper.getWritableDatabase();
        return new TrustTokenSqliteDatabase(helper, clock);
    }

    @Override
    @NonNull
    TrustTokenSetWithKey getTrustTokenSet(@TrustTokenSet.Type int type)
            throws TrustTokenExhaustedException {
        DatabaseHelper db = getWritableDatabase();
        return db.runInTransaction(
                () -> {
                    Pair<TrustTokenSetWithKey, Long> tokenAndRowId =
                            db.getTrustToken(
                                    type,
                                    Instant.ofEpochMilli(mClock.currentTimeMillis())
                                            .plus(MINIMUM_VALID_DURATION));
                    if (tokenAndRowId == null) {
                        throw new TrustTokenExhaustedException();
                    }
                    db.deleteTrustToken(tokenAndRowId.second);
                    return tokenAndRowId.first;
                });
    }

    @Override
    void addTrustTokenSets(@NonNull List<TrustTokenSetWithKey> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        DatabaseHelper db = getWritableDatabase();
        db.runInTransaction(
                () -> {
                    for (TrustTokenSetWithKey token : tokens) {
                        db.insertTrustToken(token);
                    }
                });
    }

    @Override
    @NonNull
    TrustConfiguration getTrustConfiguration() throws TrustConfigurationUnavailableException {
        DatabaseHelper db = getWritableDatabase();
        TrustConfiguration config = db.getTrustConfiguration();
        if (config == null) {
            throw new TrustConfigurationUnavailableException();
        }
        return config;
    }

    @Override
    void updateTrustConfiguration(@NonNull TrustConfiguration configuration) {
        DatabaseHelper db = getWritableDatabase();
        db.updateTrustConfiguration(configuration);
    }

    private TrustTokenSqliteDatabase(OpenHelper helper, Clock clock) {
        mOpenHelper = helper;
        mClock = clock;
    }

    private static class Schema {
        private static class TrustToken {
            private static String name() {
                return "TrustToken";
            }

            private static final String ROWID = "rowid";
            private static final String PUBLIC_KEY = "publicKey";
            private static final String PRIVATE_KEY = "privateKey";
            private static final String TYPE = "type";
            private static final String TOKEN_SET = "tokenSet";
            private static final String CREATED_AT = "createdAt";
            private static final String EXPIRE_AT = "expireAt";
        }

        private static class Metadata {
            private static String name() {
                return "Metadata";
            }

            private static final String NAME = "name";
            private static final String VALUE = "value";
        }
    }

    private static final String TRUST_CONFIGURATION = "trust_configuration";

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

        Pair<TrustTokenSetWithKey, Long> getTrustToken(
                @TrustTokenSet.Type int type, Instant expireAfter) {
            Cursor cursor =
                    mDatabase.query(
                            /* distinct */ false,
                            /* table */ Schema.TrustToken.name(),
                            /* columns */ new String[] {
                                Schema.TrustToken.ROWID,
                                Schema.TrustToken.PUBLIC_KEY,
                                Schema.TrustToken.PRIVATE_KEY,
                                Schema.TrustToken.TOKEN_SET,
                                Schema.TrustToken.CREATED_AT,
                                Schema.TrustToken.EXPIRE_AT
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
                long rowid = cursor.getLong(cursor.getColumnIndexOrThrow(Schema.TrustToken.ROWID));
                TrustTokenKey key =
                        new TrustTokenKey(
                                cursor.getBlob(
                                        cursor.getColumnIndexOrThrow(Schema.TrustToken.PUBLIC_KEY)),
                                cursor.getBlob(
                                        cursor.getColumnIndexOrThrow(
                                                Schema.TrustToken.PRIVATE_KEY)));
                TrustTokenSet token =
                        new TrustTokenSet.Builder()
                                .setType(type)
                                .setPublicKey(key.getPublicKey())
                                .setTokenSet(
                                        cursor.getBlob(
                                                cursor.getColumnIndexOrThrow(
                                                        Schema.TrustToken.TOKEN_SET)))
                                .setCreatedAt(
                                        Instant.ofEpochMilli(
                                                cursor.getLong(
                                                        cursor.getColumnIndexOrThrow(
                                                                Schema.TrustToken.CREATED_AT))))
                                .setExpireAt(
                                        Instant.ofEpochMilli(
                                                cursor.getLong(
                                                        cursor.getColumnIndexOrThrow(
                                                                Schema.TrustToken.EXPIRE_AT))))
                                .build();
                return new Pair(new TrustTokenSetWithKey(key, token), rowid);
            } finally {
                cursor.close();
            }
        }

        void insertTrustToken(TrustTokenSetWithKey tokenSetWithKey) {
            TrustTokenKey key = tokenSetWithKey.getKey();
            TrustTokenSet token = tokenSetWithKey.getTokenSet();
            ContentValues values = new ContentValues();
            values.put(Schema.TrustToken.PUBLIC_KEY, key.getPublicKey());
            values.put(Schema.TrustToken.PRIVATE_KEY, key.getPrivateKey());
            values.put(Schema.TrustToken.TYPE, token.getType());
            values.put(Schema.TrustToken.TOKEN_SET, token.getTokenSet());
            values.put(Schema.TrustToken.CREATED_AT, token.getCreatedAt().toEpochMilli());
            values.put(Schema.TrustToken.EXPIRE_AT, token.getExpireAt().toEpochMilli());
            try {
                mDatabase.insertWithOnConflict(
                        Schema.TrustToken.name(),
                        /* nullColumnHack */ null,
                        values,
                        SQLiteDatabase.CONFLICT_ROLLBACK);
            } catch (SQLiteConstraintException e) {
                throw new IllegalArgumentException("Failed to insert token into TrustToken table.");
            }
        }

        void deleteTrustToken(long rowid) {
            long deletedRows =
                    mDatabase.delete(
                            Schema.TrustToken.name(),
                            Schema.TrustToken.ROWID + " = ?",
                            new String[] {String.valueOf(rowid)});
            if (deletedRows != 1) {
                throw new IllegalStateException(
                        "failed to delete the trust token from the pending table");
            }
        }

        TrustConfiguration getTrustConfiguration() {
            Cursor cursor =
                    mDatabase.query(
                            /* distinct= */ false,
                            /* table= */ Schema.Metadata.name(),
                            /* columns= */ new String[] {Schema.Metadata.VALUE},
                            /* selection= */ Schema.Metadata.NAME + " = ?",
                            /* selectionArgs */ new String[] {TRUST_CONFIGURATION},
                            /* groupBy= */ null,
                            /* having= */ null,
                            /* orderBy= */ null,
                            /* limit= */ "1");
            try {
                if (!cursor.moveToNext()) {
                    return null;
                }
                byte[] configBlob =
                        cursor.getBlob(cursor.getColumnIndexOrThrow(Schema.Metadata.VALUE));
                try {
                    return TrustConfiguration.deserialize(configBlob);
                } catch (IOException e) {
                    throw new IllegalStateException("failed to deserialize TrustConfiguration", e);
                }
            } finally {
                cursor.close();
            }
        }

        void updateTrustConfiguration(TrustConfiguration configuration) {
            ContentValues values = new ContentValues();
            values.put(Schema.Metadata.NAME, TRUST_CONFIGURATION);
            try {
                values.put(Schema.Metadata.VALUE, configuration.serialize());
            } catch (IOException e) {
                throw new IllegalArgumentException("failed to serialize TrustConfiguration", e);
            }
            mDatabase.replaceOrThrow(Schema.Metadata.name(), /* nullColumnHack= */ null, values);
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
                            // may result in using the same TrustToken more than
                            // once. Since trust token is not performance critical,
                            // we use FULL for slightly better privacy.
                            .setJournalMode(SQLiteDatabase.SYNC_MODE_FULL)
                            .build());
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Slog.i(TAG, "Creating TrustToken database " + getDatabaseName());
            db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS TrustToken (
                          publicKey   BLOB     NOT NULL,
                          privateKey  BLOB     NOT NULL,
                          type        INTEGER  NOT NULL,
                          tokenSet    BLOB     NOT NULL,
                          createdAt   INTEGER  NOT NULL,
                          expireAt    INTEGER  NOT NULL,
                          PRIMARY KEY (type, expireAt, publicKey ASC),
                          UNIQUE (publicKey)
                    );
                    """);
            db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Metadata (
                          name        STRING   PRIMARY KEY,
                          value       BLOB     NOT NULL
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
            db.execSQL("DROP TABLE TrustToken");
            db.execSQL("DROP TABLE Metadata");
        }
    }
}
