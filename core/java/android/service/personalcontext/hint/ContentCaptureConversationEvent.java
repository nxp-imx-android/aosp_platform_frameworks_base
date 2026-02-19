/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.hint;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.service.personalcontext.Flags;
import android.util.Log;
import android.view.autofill.AutofillId;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.Objects;

/**
 * Base class for conversation-related events from the Content Capture API.
 *
 * <p>A single {@link ConversationEnterEvent} will always be the first event sent. Afterwards, any
 * number of pairs of {@link ConversationProcessingEvent} then {@link ConversationUpdateEvent} may
 * be sent, for the initial update and any subsequent messages sent or received. When the
 * conversation is exited, a single {@link ConversationExitEvent} will be sent, after which no other
 * events will be sent for that conversation.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ContentCaptureConversationEvent {
    private static final String TAG = "ContentCaptureConversationEvent";

    private static final String KEY_EVENT_TYPE = "key_event_type";
    private static final String KEY_CONVERSATION_SESSION_ID = "key_conversation_session_id";
    private static final String KEY_CLIENT_EVENT_TIMESTAMP = "key_client_event_timestamp";
    private static final String KEY_EVENT_DATA = "key_event_data";

    /** Indicates an unknown event type. */
    static final int EVENT_TYPE_UNKNOWN = 0;

    /** Indicates a new conversation is visible. Corresponds to {@link ConversationEnterEvent}. */
    static final int EVENT_TYPE_ENTER = 1;

    /**
     * Indicates a conversation is no longer visible. Corresponds to {@link ConversationExitEvent}.
     */
    static final int EVENT_TYPE_EXIT = 2;

    /**
     * Indicates a message in the conversation is being processed. Corresponds to {@link
     * ConversationProcessingEvent}.
     */
    static final int EVENT_TYPE_PROCESSING = 3;

    /**
     * Indicates an update to the conversation data. Corresponds to {@link ConversationUpdateEvent}.
     */
    static final int EVENT_TYPE_UPDATE = 4;

    /** Enumeration of conversation event types. */
    @IntDef(
            prefix = {"EVENT_TYPE_"},
            value = {
                EVENT_TYPE_UNKNOWN,
                EVENT_TYPE_ENTER,
                EVENT_TYPE_EXIT,
                EVENT_TYPE_PROCESSING,
                EVENT_TYPE_UPDATE
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface EventType {}

    private final @NonNull String mConversationSessionId;

    /**
     * The timestamp of the event being created in the ACE client invoking the trigger.
     *
     * <p>If the event is from Content Capture, this is the timestamp from the original {@link
     * android.view.contentcapture.ContentCaptureEvent}.
     */
    private final @NonNull Instant mClientEventTimestamp;

    ContentCaptureConversationEvent(
            @NonNull String conversationSessionId, @NonNull Instant clientEventTimestamp) {
        mConversationSessionId = conversationSessionId;
        mClientEventTimestamp = clientEventTimestamp;
    }

    /**
     * Returns the session ID of the conversation.
     *
     * <p>This is an arbitrary string ID that can be used by the understander to recognize this
     * particular conversation.
     */
    @NonNull
    public String getConversationSessionId() {
        return mConversationSessionId;
    }

    /** Returns the timestamp of the event being created in the ACE client invoking the trigger. */
    @NonNull
    public Instant getClientEventTimestamp() {
        return mClientEventTimestamp;
    }

    /** Returns the {@link EventType} of this conversation event. */
    @EventType
    abstract int getEventType();

    @NonNull
    abstract Bundle toBundleImpl();

    /**
     * Writes the event data to a {@link Bundle}.
     *
     * @hide
     */
    @NonNull
    public Bundle toBundle() {
        final Bundle bundle = toBundleBase();
        bundle.putBundle(KEY_EVENT_DATA, toBundleImpl());
        return bundle;
    }

    /**
     * Writes this event to a bundle for use by {@link ContentCaptureConversationHint#toBundle()},
     * minus the event data itself, which might contain binders or file descriptors that aren't
     * desired in {@link #writeToSignatureParcel(Parcel)}.
     */
    private Bundle toBundleBase() {
        final Bundle bundle = new Bundle();
        bundle.putInt(KEY_EVENT_TYPE, getEventType());
        bundle.putString(KEY_CONVERSATION_SESSION_ID, mConversationSessionId);
        bundle.putLong(KEY_CLIENT_EVENT_TIMESTAMP, mClientEventTimestamp.toEpochMilli());
        return bundle;
    }

    void writeToSignatureParcel(@NonNull Parcel dest) {
        dest.writeBundle(toBundleBase());
        writeToSignatureParcelImpl(dest);
    }

    /**
     * Writes marshallable data used to sign and verify the data contained in this event.
     *
     * <p>This should be overridden by subclasses if their data contains any binders or file
     * descriptors, as these are not marshallable.
     *
     * @see ContextHint#writeToSignatureParcel(Parcel)
     */
    void writeToSignatureParcelImpl(@NonNull Parcel dest) {
        dest.writeBundle(toBundleImpl());
    }

    /**
     * Unbundles a conversation event into the correct subclass of event based on the event type.
     *
     * @hide
     */
    @NonNull
    public static ContentCaptureConversationEvent fromBundle(@NonNull Bundle bundle) {
        final int eventType = bundle.getInt(KEY_EVENT_TYPE);
        final String conversationSessionId = bundle.getString(KEY_CONVERSATION_SESSION_ID);
        final Instant clientEventTimestamp =
                Instant.ofEpochMilli(bundle.getLong(KEY_CLIENT_EVENT_TIMESTAMP));
        final Bundle eventData = bundle.getBundle(KEY_EVENT_DATA);
        Objects.requireNonNull(eventData);
        Objects.requireNonNull(conversationSessionId);
        Objects.requireNonNull(clientEventTimestamp);

        return switch (eventType) {
            case EVENT_TYPE_ENTER ->
                    new ConversationEnterEvent(
                            conversationSessionId, clientEventTimestamp, eventData);
            case EVENT_TYPE_EXIT ->
                    new ConversationExitEvent(
                            conversationSessionId, clientEventTimestamp, eventData);
            case EVENT_TYPE_PROCESSING ->
                    new ConversationProcessingEvent(
                            conversationSessionId, clientEventTimestamp, eventData);
            case EVENT_TYPE_UPDATE ->
                    new ConversationUpdateEvent(
                            conversationSessionId, clientEventTimestamp, eventData);
            default -> {
                Log.wtf(TAG, "Unknown event type: " + eventType);
                yield new ContentCaptureConversationEvent(
                        conversationSessionId, clientEventTimestamp) {
                    @Override
                    int getEventType() {
                        return EVENT_TYPE_UNKNOWN;
                    }

                    @NonNull
                    @Override
                    Bundle toBundleImpl() {
                        return new Bundle();
                    }
                };
            }
        };
    }

    /**
     * An event representing a new conversation being visible on the screen.
     *
     * <p>This event is sent once when the user enters a conversation and will be the first event
     * for the conversation.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationEnterEvent extends ContentCaptureConversationEvent {
        private static final String KEY_ENTER_TIMESTAMP = "key_enter_timestamp";

        /**
         * The timestamp when the conversation was entered.
         *
         * <p>If the event is from Content Capture, this is the timestamp when System Intelligence
         * detected the conversation was entered.
         */
        private final @NonNull Instant mTimestamp;

        /**
         * Creates a new {@link ConversationEnterEvent}.
         *
         * @param conversationSessionId the session ID of the conversation
         * @param clientEventTimestamp the timestamp of the event being created in the ACE client,
         *     e.g. the Content Capture event timestamp
         * @param timestamp the timestamp when the Device Intelligence detected the conversation was
         *     entered
         */
        public ConversationEnterEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Instant timestamp) {
            super(conversationSessionId, clientEventTimestamp);
            mTimestamp = timestamp;
        }

        ConversationEnterEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, clientEventTimestamp);
            mTimestamp = Instant.ofEpochMilli(bundle.getLong(KEY_ENTER_TIMESTAMP));
        }

        /** Returns the timestamp when the conversation was entered. */
        @NonNull
        public Instant getTimestamp() {
            return mTimestamp;
        }

        @Override
        int getEventType() {
            return EVENT_TYPE_ENTER;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            final Bundle bundle = new Bundle();
            bundle.putLong(KEY_ENTER_TIMESTAMP, mTimestamp.toEpochMilli());
            return bundle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConversationEnterEvent)) return false;
            ConversationEnterEvent that = (ConversationEnterEvent) o;
            return Objects.equals(getConversationSessionId(), that.getConversationSessionId())
                    && Objects.equals(getClientEventTimestamp(), that.getClientEventTimestamp())
                    && Objects.equals(mTimestamp, that.mTimestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getEventType(),
                    getConversationSessionId(),
                    getClientEventTimestamp(),
                    mTimestamp);
        }

        @Override
        public String toString() {
            return "ConversationEnterEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "',"
                    + " mClientEventTimestamp="
                    + getClientEventTimestamp()
                    + ", mConversationEnterTimestamp="
                    + mTimestamp
                    + '}';
        }
    }

    /**
     * An event representing a conversation not being visible on the screen anymore.
     *
     * <p>This event is the final event sent when a user exits a conversation, after which no other
     * events will be sent.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationExitEvent extends ContentCaptureConversationEvent {
        private static final String KEY_EXIT_TIMESTAMP = "key_exit_timestamp";

        /**
         * The timestamp when the conversation was exited.
         *
         * <p>If the event is from Content Capture, this is the timestamp when System Intelligence
         * detected the conversation was exited.
         */
        private final @NonNull Instant mTimestamp;

        /**
         * Creates a new {@link ConversationExitEvent}.
         *
         * @param conversationSessionId the session ID of the conversation
         * @param clientEventTimestamp the timestamp of the event being created in the ACE client,
         *     e.g. the Content Capture event timestamp
         * @param timestamp the timestamp when the Device Intelligence detected the conversation was
         *     exited
         */
        public ConversationExitEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Instant timestamp) {
            super(conversationSessionId, clientEventTimestamp);
            mTimestamp = timestamp;
        }

        ConversationExitEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, clientEventTimestamp);
            mTimestamp = Instant.ofEpochMilli(bundle.getLong(KEY_EXIT_TIMESTAMP));
        }

        @Override
        int getEventType() {
            return EVENT_TYPE_EXIT;
        }

        /** Returns the timestamp when the conversation was exited. */
        @NonNull
        public Instant getTimestamp() {
            return mTimestamp;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            final Bundle bundle = new Bundle();
            bundle.putLong(KEY_EXIT_TIMESTAMP, mTimestamp.toEpochMilli());
            return bundle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConversationExitEvent)) return false;
            ConversationExitEvent that = (ConversationExitEvent) o;
            return Objects.equals(getConversationSessionId(), that.getConversationSessionId())
                    && Objects.equals(getClientEventTimestamp(), that.getClientEventTimestamp())
                    && Objects.equals(mTimestamp, that.mTimestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getEventType(),
                    getConversationSessionId(),
                    getClientEventTimestamp(),
                    getTimestamp());
        }

        @Override
        public String toString() {
            return "ConversationExitEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "',"
                    + " mClientEventTimestamp="
                    + getClientEventTimestamp()
                    + ", mConversationExitTimestamp="
                    + getTimestamp()
                    + '}';
        }
    }

    /**
     * An event representing a message from a conversation on screen being processed.
     *
     * <p>This event is sent after the conversation is first entered and anytime a new message is
     * sent or received. After this event, a {@link ConversationUpdateEvent} will be sent.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationProcessingEvent extends ContentCaptureConversationEvent {
        private static final String KEY_START_TIMESTAMP = "key_start_timestamp";
        private static final String KEY_MESSAGE_AUTOFILL_ID = "key_message_autofill_id";

        /**
         * The timestamp when the processing started.
         *
         * <p>If the event is from Content Capture, this is the timestamp when System Intelligence
         * detected the conversation was being processed.
         */
        private final @NonNull Instant mStartProcessingTimestamp;

        private final @NonNull AutofillId mMessageAutofillId;

        /**
         * Creates a new {@link ConversationProcessingEvent}.
         *
         * @param conversationSessionId the session ID of the conversation
         * @param clientEventTimestamp the timestamp of the event being created in the ACE client,
         *     e.g. the Content Capture event timestamp
         * @param startProcessingTimestamp the timestamp when the Device Intelligence detected the
         *     conversation was being processed
         * @param messageAutofillId the autofill id of the message being processed
         */
        public ConversationProcessingEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Instant startProcessingTimestamp,
                @NonNull AutofillId messageAutofillId) {
            super(conversationSessionId, clientEventTimestamp);
            mStartProcessingTimestamp = startProcessingTimestamp;
            mMessageAutofillId = messageAutofillId;
        }

        ConversationProcessingEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, clientEventTimestamp);
            mStartProcessingTimestamp = Instant.ofEpochMilli(bundle.getLong(KEY_START_TIMESTAMP));
            mMessageAutofillId = bundle.getParcelable(KEY_MESSAGE_AUTOFILL_ID, AutofillId.class);
        }

        /** Returns the timestamp when the processing starts. */
        @NonNull
        public Instant getStartProcessingTimestamp() {
            return mStartProcessingTimestamp;
        }

        /** Returns the autofill id of the message. */
        @NonNull
        public AutofillId getMessageAutofillId() {
            return mMessageAutofillId;
        }

        @Override
        int getEventType() {
            return EVENT_TYPE_PROCESSING;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            final Bundle bundle = new Bundle();
            bundle.putLong(KEY_START_TIMESTAMP, mStartProcessingTimestamp.toEpochMilli());
            bundle.putParcelable(KEY_MESSAGE_AUTOFILL_ID, mMessageAutofillId);
            return bundle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConversationProcessingEvent)) return false;
            ConversationProcessingEvent that = (ConversationProcessingEvent) o;
            return Objects.equals(getConversationSessionId(), that.getConversationSessionId())
                    && Objects.equals(getClientEventTimestamp(), that.getClientEventTimestamp())
                    && Objects.equals(mStartProcessingTimestamp, that.mStartProcessingTimestamp)
                    && Objects.equals(mMessageAutofillId, that.mMessageAutofillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getEventType(),
                    getConversationSessionId(),
                    getClientEventTimestamp(),
                    mStartProcessingTimestamp,
                    mMessageAutofillId);
        }

        @Override
        public String toString() {
            return "ConversationProcessingEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "',"
                    + " mClientEventTimestamp="
                    + getClientEventTimestamp()
                    + ", mStartProcessingTimestamp="
                    + mStartProcessingTimestamp
                    + ", mMessageAutofillId="
                    + mMessageAutofillId
                    + '}';
        }
    }

    /**
     * An event representing an update to the data of a conversation visible on the screen.
     *
     * <p>This event is sent when the initial conversation or updates are processed and will always
     * be preceded by a {@link ConversationProcessingEvent}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationUpdateEvent extends ContentCaptureConversationEvent {
        private static final String KEY_CONVERSATION_DATA = "key_conversation_data";
        private static final String KEY_CONVERSATION_UPDATE_TIMESTAMP =
                "key_conversation_update_timestamp";

        private final @NonNull ConversationData mConversationData;

        /**
         * The timestamp when the conversation was updated.
         *
         * <p>If the event is from Content Capture, this is the timestamp when System Intelligence
         * detected the conversation was updated.
         */
        private final @NonNull Instant mConversationUpdateTimestamp;

        /**
         * Creates a new {@link ConversationUpdateEvent}.
         *
         * @param conversationSessionId the session ID of the conversation
         * @param clientEventTimestamp the timestamp of the event being created in the ACE client,
         *     e.g. the Content Capture event timestamp
         * @param conversationUpdateTimestamp the timestamp when the Device Intelligence detected
         *     the conversation was updated
         * @param conversationData the data of the conversation
         */
        public ConversationUpdateEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Instant conversationUpdateTimestamp,
                @NonNull ConversationData conversationData) {
            super(conversationSessionId, clientEventTimestamp);
            mConversationData = conversationData;
            mConversationUpdateTimestamp = conversationUpdateTimestamp;
        }

        ConversationUpdateEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant clientEventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, clientEventTimestamp);
            mConversationData = bundle.getParcelable(KEY_CONVERSATION_DATA, ConversationData.class);
            mConversationUpdateTimestamp =
                    Instant.ofEpochMilli(bundle.getLong(KEY_CONVERSATION_UPDATE_TIMESTAMP));
        }

        /** Returns the data of the conversation. */
        @NonNull
        public ConversationData getConversationData() {
            return mConversationData;
        }

        /** Returns the timestamp when the conversation was updated. */
        @NonNull
        public Instant getConversationUpdateTimestamp() {
            return mConversationUpdateTimestamp;
        }

        @Override
        int getEventType() {
            return EVENT_TYPE_UPDATE;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            final Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_CONVERSATION_DATA, mConversationData);
            bundle.putLong(
                    KEY_CONVERSATION_UPDATE_TIMESTAMP, mConversationUpdateTimestamp.toEpochMilli());
            return bundle;
        }

        @Override
        void writeToSignatureParcelImpl(@NonNull Parcel dest) {
            mConversationData.writeToSignatureParcel(dest);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConversationUpdateEvent)) return false;
            ConversationUpdateEvent that = (ConversationUpdateEvent) o;
            return Objects.equals(getConversationSessionId(), that.getConversationSessionId())
                    && Objects.equals(getClientEventTimestamp(), that.getClientEventTimestamp())
                    && Objects.equals(
                            mConversationUpdateTimestamp, that.mConversationUpdateTimestamp)
                    && Objects.equals(mConversationData, that.mConversationData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getEventType(),
                    getConversationSessionId(),
                    getClientEventTimestamp(),
                    getConversationUpdateTimestamp(),
                    mConversationData);
        }

        @Override
        public String toString() {
            return "ConversationUpdateEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "',"
                    + " mClientEventTimestamp="
                    + getClientEventTimestamp()
                    + ", mConversationUpdateTimestamp="
                    + mConversationUpdateTimestamp
                    + ", mConversationData="
                    + mConversationData
                    + '}';
        }
    }
}
