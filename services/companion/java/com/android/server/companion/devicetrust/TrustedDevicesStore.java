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

package com.android.server.companion.devicetrust;

import static com.android.internal.util.XmlUtils.readByteArrayAttribute;
import static com.android.internal.util.XmlUtils.readIntAttribute;
import static com.android.internal.util.XmlUtils.readStringAttribute;
import static com.android.internal.util.XmlUtils.writeByteArrayAttribute;
import static com.android.internal.util.XmlUtils.writeIntAttribute;
import static com.android.internal.util.XmlUtils.writeStringAttribute;
import static com.android.server.companion.utils.DataStoreUtils.createStorageFileForUser;
import static com.android.server.companion.utils.DataStoreUtils.isEndOfTag;
import static com.android.server.companion.utils.DataStoreUtils.isStartOfTag;
import static com.android.server.companion.utils.DataStoreUtils.writeToFileSafely;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * Manages disk storage for trusted devices session keys for Companion Device Manager.
 */
public class TrustedDevicesStore {
    private static final String TAG = "CDM_TrustedDevicesStore";

    private static final int CURRENT_PERSISTENCE_VERSION = 1;

    private static final String FILE_NAME = "cdm_trusted_devices.xml";

    private static final String XML_TAG_STATE = "trusted-devices-keys";
    private static final String XML_TAG_SESSION_KEY = "session-key";

    private static final String XML_ATTR_PERSISTENCE_VERSION = "persistence-version";
    private static final String XML_ATTR_ASSOCIATION_ID = "association-id";
    private static final String XML_ATTR_KEY = "key";
    private static final String XML_ATTR_ROOT_OF_TRUST = "root";

    private final @NonNull ConcurrentMap<Integer, AtomicFile> mUserIdToStorageFile =
            new ConcurrentHashMap<>();
    private final @NonNull ConcurrentMap<Integer, SparseArray<byte[]>> mSessionKeys =
            new ConcurrentHashMap<>();
    private final @NonNull SparseArray<String> mRootsOfTrust = new SparseArray<>();

    public TrustedDevicesStore() {
    }

    /**
     * Get a session key for a given association id.
     */
    public byte[] getSessionKey(@UserIdInt int userId, int associationId) {
        final SparseArray<byte[]> userKeys = mSessionKeys.get(userId);
        if (userKeys == null) {
            return null;
        }
        return userKeys.get(associationId);
    }

    /**
     * Store a session key for a given association id.
     */
    public void storeSessionKey(@UserIdInt int userId, int associationId, byte[] sessionKey) {
        mSessionKeys.computeIfAbsent(userId, k -> new SparseArray<>())
                .put(associationId, sessionKey);
        writeSessionKeysForUser(userId);
    }

    /**
     * Fetches the root of trust for an association.
     */
    public String getRootOfTrust(int associationId) {
        return mRootsOfTrust.get(associationId);
    }

    /**
     * Sets the root of trust for an association. This data will not be written to storage until
     * the association successfully completes the session verification handshake.
     */
    public void setRootOfTrust(int associationId, String root) {
        mRootsOfTrust.set(associationId, root);
    }

    /**
     * Remove a session key for a given association id.
     */
    public void removeSessionKey(@UserIdInt int userId, int associationId) {
        final SparseArray<byte[]> userKeys = mSessionKeys.get(userId);
        if (userKeys == null || userKeys.contains(associationId)) {
            return;
        }
        userKeys.remove(associationId);
        writeSessionKeysForUser(userId);
    }

    /**
     * Reads session keys for a given user from disk.
     */
    public void readSessionKeysForUser(@UserIdInt int userId) {
        Slog.i(TAG, "Reading trusted devices session keys for user " + userId + " from disk.");
        final AtomicFile file = getStorageFileForUser(userId);
        final SparseArray<byte[]> sessionKeys = new SparseArray<>();
        final SparseArray<String> rootsOfTrust = new SparseArray<>();

        synchronized (file) {
            if (!file.getBaseFile().exists()) {
                mSessionKeys.put(userId, sessionKeys);
                return;
            }

            try (FileInputStream in = file.openRead()) {
                final TypedXmlPullParser parser = Xml.resolvePullParser(in);
                XmlUtils.beginDocument(parser, XML_TAG_STATE);

                final int version = readIntAttribute(parser, XML_ATTR_PERSISTENCE_VERSION, 0);
                if (version > CURRENT_PERSISTENCE_VERSION) {
                    Slog.w(TAG, "Unknown persistence version " + version);
                    mSessionKeys.put(userId, sessionKeys);
                    return;
                }

                while (true) {
                    parser.nextTag();
                    if (isEndOfTag(parser, XML_TAG_STATE)) {
                        break;
                    }
                    if (isStartOfTag(parser, XML_TAG_SESSION_KEY)) {
                        int associationId = readIntAttribute(parser, XML_ATTR_ASSOCIATION_ID);
                        byte[] key = readByteArrayAttribute(parser, XML_ATTR_KEY);
                        if (key != null) {
                            sessionKeys.put(associationId, key);
                        }
                        String root = readStringAttribute(parser, XML_ATTR_ROOT_OF_TRUST);
                        rootsOfTrust.put(associationId, root);
                    }
                }
            } catch (XmlPullParserException | IOException e) {
                Slog.e(TAG, "Error while reading trusted devices session keys file", e);
            }
        }
        mSessionKeys.put(userId, sessionKeys);
        for (int i = 0; i < rootsOfTrust.size(); i++) {
            int associationId = rootsOfTrust.keyAt(i);
            String root = rootsOfTrust.get(associationId);
            mRootsOfTrust.put(associationId, root);
        }
    }

    /**
     * Writes session keys for a given user to disk.
     */
    private void writeSessionKeysForUser(@UserIdInt int userId) {
        final SparseArray<byte[]> sessionKeys = mSessionKeys.get(userId);
        if (sessionKeys == null) {
            return;
        }

        Slog.i(TAG, "Writing trusted devices session keys for user " + userId + " to disk");

        final AtomicFile file = getStorageFileForUser(userId);
        synchronized (file) {
            writeToFileSafely(file, out -> {
                final TypedXmlSerializer serializer = Xml.resolveSerializer(out);
                serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output",
                        true);
                serializer.startDocument("UTF-8", true);
                serializer.startTag(null, XML_TAG_STATE);
                writeIntAttribute(serializer,
                        XML_ATTR_PERSISTENCE_VERSION, CURRENT_PERSISTENCE_VERSION);

                for (int i = 0; i < sessionKeys.size(); i++) {
                    int associationId = sessionKeys.keyAt(i);
                    byte[] key = sessionKeys.valueAt(i);
                    String root = mRootsOfTrust.get(i);

                    serializer.startTag(null, XML_TAG_SESSION_KEY);
                    writeIntAttribute(serializer, XML_ATTR_ASSOCIATION_ID, associationId);
                    writeByteArrayAttribute(serializer, XML_ATTR_KEY, key);
                    if (root != null) {
                        writeStringAttribute(serializer, XML_ATTR_ROOT_OF_TRUST, root);
                    }
                    serializer.endTag(null, XML_TAG_SESSION_KEY);
                }

                serializer.endTag(null, XML_TAG_STATE);
                serializer.endDocument();
            });
        }
    }

    @NonNull
    private AtomicFile getStorageFileForUser(@UserIdInt int userId) {
        return mUserIdToStorageFile.computeIfAbsent(userId,
                u -> createStorageFileForUser(userId, FILE_NAME));
    }
}
