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

package com.android.server.contentrestriction;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.Environment;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.Xml;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Provides storage and retrieval of content restriction user data.
 *
 * <p>The storage is managed as a singleton, ensuring a single point of access for persistent user
 * data.
 */
public class ContentRestrictionSettings {

    private static final String TAG = "ContentRestrictionSettings";
    private static final boolean DEBUG = false;

    private final SparseArray<ContentRestrictionUserData> mUserData = new SparseArray<>();

    private static final String PREF_DATA = "contentrestriction_data";
    private static final String PREF_USER_DATA = "contentrestriction_user_data";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ENABLED = "contentrestriction_enabled";
    private static final String KEY_ROLE_HOLDERS_LIST = "contentrestriction_role_holders_list";
    private static final String KEY_ROLE_HOLDER = "contentrestriction_role_holder";

    private AtomicFile userDataFile =
            new AtomicFile(
                    new File(
                            Environment.getDataSystemDirectory(),
                            "contentrestriction_settings.xml"),
                    "contentrestriction");

    @VisibleForTesting
    public ContentRestrictionSettings(File parent) {
        userDataFile =
                new AtomicFile(
                        new File(parent, "contentrestriction_settings.xml"), "contentrestriction");
        loadUserData();
    }

    public ContentRestrictionSettings() {
        loadUserData();
    }

    /** Gets data about a specific user. */
    @NonNull
    public ContentRestrictionUserData getUserData(@UserIdInt int userId) {
        ContentRestrictionUserData data = mUserData.get(userId);
        if (data == null) {
            data = new ContentRestrictionUserData(userId);
            mUserData.append(userId, data);
        }
        return data;
    }

    /** Removes data of a specific user. */
    public void removeUserData(int userId) {
        mUserData.remove(userId);
        saveUserData();
    }

    /** Loads user data from persistent storage. */
    public void loadUserData() {
        if (DEBUG) {
            Slog.d(TAG, "Restoring content restriction state");
        }
        mUserData.clear();
        if (!userDataFile.getBaseFile().exists()) {
            return;
        }
        try (FileInputStream stream = userDataFile.openRead()) {
            final TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            XmlUtils.beginDocument(parser, PREF_DATA);
            final int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (PREF_USER_DATA.equals(parser.getName())) {
                    parseUserData(parser);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Slog.e(TAG, "Failed to restore content restriction state", e);
        }
    }

    /** Saves user data to persistent storage. */
    // TODO(b/461856546): Split into per user files.
    public void saveUserData() {
        FileOutputStream stream = null;
        if (DEBUG) {
            Slog.d(TAG, "Writing content restriction state");
        }
        try {
            stream = userDataFile.startWrite();
            final TypedXmlSerializer xml = Xml.resolveSerializer(stream);
            xml.startDocument(null, true);
            xml.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xml.startTag(null, PREF_DATA);
            for (int i = 0; i < mUserData.size(); i++) {
                ContentRestrictionUserData data = mUserData.valueAt(i);
                xml.startTag(null, PREF_USER_DATA);
                xml.attributeInt(null, KEY_USER_ID, data.userId);
                xml.attributeBoolean(null, KEY_ENABLED, data.contentRestrictionEnabled);
                if (data.contentRestrictionRoleHolders != null
                        && !data.contentRestrictionRoleHolders.isEmpty()) {
                    xml.startTag(null, KEY_ROLE_HOLDERS_LIST);
                    for (String roleHolder : data.contentRestrictionRoleHolders) {
                        xml.startTag(null, KEY_ROLE_HOLDER);
                        xml.attribute(null, "package", roleHolder);
                        xml.endTag(null, KEY_ROLE_HOLDER);
                    }
                    xml.endTag(null, KEY_ROLE_HOLDERS_LIST);
                }
                xml.endTag(null, PREF_USER_DATA);
            }
            xml.endTag(null, PREF_DATA);
            xml.endDocument();
            userDataFile.finishWrite(stream);
        } catch (IOException e) {
            userDataFile.failWrite(stream);
            Slog.e(TAG, "Failed to save content restriction state", e);
        }
    }

    private void parseUserData(TypedXmlPullParser parser)
            throws IOException, XmlPullParserException {
        final int userId = parser.getAttributeInt(null, KEY_USER_ID);
        final ContentRestrictionUserData data = getUserData(userId);

        data.contentRestrictionRoleHolders = new ArraySet<>();
        data.contentRestrictionEnabled = parser.getAttributeBoolean(null, KEY_ENABLED);

        final int depth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, depth)) {
            final String tagName = parser.getName();
            if (KEY_ROLE_HOLDERS_LIST.equals(tagName)) {
                parseRoleHolders(parser, data);
            } else if (tagName != null) {
                Slog.w(TAG, "Unknown tag under <" + PREF_USER_DATA + ">: " + tagName);
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    private void parseRoleHolders(TypedXmlPullParser parser, ContentRestrictionUserData data)
            throws IOException, XmlPullParserException {
        final int roleHoldersDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, roleHoldersDepth)) {
            if (KEY_ROLE_HOLDER.equals(parser.getName())) {
                final String roleHolder = parser.getAttributeValue(null, "package");
                if (roleHolder != null) {
                    data.contentRestrictionRoleHolders.add(roleHolder);
                }
            }
        }
    }
}
