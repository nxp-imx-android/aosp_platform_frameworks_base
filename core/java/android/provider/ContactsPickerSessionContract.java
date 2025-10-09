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

package android.provider;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.content.flags.Flags;
import android.net.Uri;
import android.provider.ContactsContract.Data;

/**
 * <p>
 * The contract between the Contacts Picker session provider and client applications.
 * This contract defines the intent actions, extras, and URI structure used to interact
 * with the Contacts Picker to select contacts and retrieve their data.
 * </p>
 * <p>
 * It allows apps to select one or more contacts without requiring
 * {@link android.Manifest.permission#READ_CONTACTS}. The client app receives one-time access
 * to the user-selected contacts data.
 * </p>
 */
@FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
public final class ContactsPickerSessionContract {

    private ContactsPickerSessionContract() {}

    /**
     * Intent action to launch the system contacts picker to select one or more contacts. This is
     * different than {@link android.content.Intent#ACTION_PICK} and
     * {@link android.content.Intent#ACTION_GET_CONTENT} in that
     * <ul>
     *     <li>This action is specifically used for only picking contacts.</li>
     *     <li>caller gets read access to user picked items even without contacts permissions.</li>
     *     <li>users get consistent and secure UI to grant apps temporary access to contact data.
     *     </li>
     *     <li>clients can choose to get multiple data fields corresponding to the selected
     *      contacts as a response.</li>
     * </ul>
     * <p>
     * To use this intent, build an {@link android.content.Intent} with this action and launch it
     * using {@link android.app.Activity#startActivityForResult(android.content.Intent, int)}.
     * The System Contacts Picker UI will be displayed, allowing the user to select one or more
     * contacts.
     *
     * <p>
     * The selection behavior can be customized using the following extras:
     * <ul>
     *     <li>{@link #EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS}: (Required) A list of data
     *     MIME types to be returned for the selected contacts.</li>
     *     <li>{@link #EXTRA_PICK_CONTACTS_SELECTION_LIMIT}: (Optional) The maximum number of
     *     contacts the user can select.</li>
     *     <li>{@link android.content.Intent#EXTRA_ALLOW_MULTIPLE}: (Optional) If set to
     *     {@code true}, the user can select multiple contacts. The default is single-select.</li>
     * </ul>
     *
     * <p>
     * Upon successful selection, the {@link android.app.Activity#onActivityResult(int, int,
     * android.content.Intent)} callback will be invoked with
     * {@link android.app.Activity#RESULT_OK}. The returned {@link android.content.Intent}
     * will contain a session URI in its data field (see {@link Session#CONTENT_URI}), which
     * can be used to query the selected contact data.
     *
     * <p>
     * Starting from Android 17, this intent is handled by a system application by default.
     * Third-party applications should generally not handle this intent as they will be ignored
     * when the system attempts to resolve it.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public static final String ACTION_PICK_CONTACTS = "android.provider.action.PICK_CONTACTS";

    /**
     * A {@code List<String>} extra that specifies the types of contact data the client
     * application is requesting. This extra must be populated with one or more of the following
     * MIME types:
     *
     * <ul>
     *  <li>{@link ContactsContract.CommonDataKinds.Email#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.Phone#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.StructuredPostal#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.Organization#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.Relation#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.Event#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.Photo#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.GroupMembership#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.Website#CONTENT_ITEM_TYPE}</li>
     *  <li>{@link ContactsContract.CommonDataKinds.Nickname#CONTENT_ITEM_TYPE}</li>
     * </ul>
     *
     * <p>System contacts picker will return all {@link ContactsContract.Data} rows having at least
     * one of the MIME types specified in this extra, for all contacts selected by the user.
     * Picker will throw an {@link IllegalArgumentException}, in case any of the mime-types passed
     * do not match against the ones specified here.
     * Clients are required to set this extra to ensure the picker can determine which
     * information should be made available for selection.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public static final String EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS =
            "android.provider.extra.PICK_CONTACTS_REQUESTED_DATA_FIELDS";

    /**
     * An integer extra that defines the maximum number of contacts a user can select in a single
     * session. The Contacts Picker uses this value to configure its UI.
     *
     * <p>If not set, the default value is 50. The maximum allowed value is 100.
     *
     * <p>Clients should not set this value higher than the documented maximum limit. The
     * application handling {@link #ACTION_PICK_CONTACTS} will throw an
     * {@link IllegalArgumentException} for values exceeding the limit.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public static final String EXTRA_PICK_CONTACTS_SELECTION_LIMIT =
            "android.provider.extra.PICK_CONTACTS_SELECTION_LIMIT";

    /**
     * The authority for the Contacts Picker session provider. This is used in the content URI.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public static final String AUTHORITY = "com.android.contacts.picker.sessions";

    /**
     * The base {@code content://} style URI for the Contacts Picker session provider.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    @NonNull
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * Defines the contract for a picker session, which represents the set of contacts selected
     * by the user in a single picking operation.
     *
     * <p>Each row in this table corresponds to a single picker session and acts as a pointer to
     * the underlying contact data. Querying a session URI effectively projects rows from the
     * {@link ContactsContract.Data} table, providing access to the
     * {@link #EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS} for the contacts that the user selected.
     *
     * <p>Access to session data is restricted. A client application can only access the session
     * it initiated using the session URI returned by the picker. This is enforced through
     * {@link android.content.Intent#FLAG_GRANT_READ_URI_PERMISSION}. Privileged system applications
     * with {@code MANAGE_CONTACTS_PICKER_SESSIONS} permission can still access all session data.
     *
     * <p>Because a session URI projects data from {@link ContactsContract.Data}, clients can use
     * the columns from {@link ContactsContract.Data} in their query projection, selection, and
     * sort order.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public static final class Session implements BaseColumns {

        private Session() {}

        /**
         * The base {@code content://} style URI for this table.
         *
         * <p>Querying this URI directly is not supported because it represents all picker
         * sessions across all clients. To access a specific session, clients must append a
         * session ID, in the format {@code /<session_id>}, to this URI. The complete URI for a
         * session is returned by the picker upon a successful selection.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
        @NonNull
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "sessions");

        /**
         * The MIME type for a directory of session items. A session URI, whether for a single
         * session (e.g., {@code CONTENT_URI/<session_id>}) or for the base URI, will have this
         * MIME type.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
        public static final String CONTENT_TYPE = Data.CONTENT_TYPE;
    }
}
