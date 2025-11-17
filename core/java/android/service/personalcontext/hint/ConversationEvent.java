/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law of or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.hint;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.util.Log;
import android.view.autofill.AutofillId;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Base class for conversation-related events. */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ConversationEvent {
    private static final String TAG = "ConversationEvent";

    private static final String KEY_EVENT_TYPE = "key_event_type";
    private static final String KEY_CONVERSATION_SESSION_ID = "key_conversation_session_id";
    private static final String KEY_EVENT_TIMESTAMP = "key_event_timestamp";
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
    private final @NonNull Instant mEventTimestamp;

    ConversationEvent(@NonNull String conversationSessionId, @NonNull Instant eventTimestamp) {
        mConversationSessionId = conversationSessionId;
        mEventTimestamp = eventTimestamp;
    }

    /** Returns the session ID of the conversation. */
    @NonNull
    public String getConversationSessionId() {
        return mConversationSessionId;
    }

    /** Returns the timestamp of the event being created in the ACE client invoking the trigger. */
    @NonNull
    public Instant getEventTimestamp() {
        return mEventTimestamp;
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
        final Bundle bundle = new Bundle();
        bundle.putInt(KEY_EVENT_TYPE, getEventType());
        bundle.putString(KEY_CONVERSATION_SESSION_ID, mConversationSessionId);
        bundle.putLong(KEY_EVENT_TIMESTAMP, mEventTimestamp.toEpochMilli());
        bundle.putBundle(KEY_EVENT_DATA, toBundleImpl());
        return bundle;
    }

    /**
     * Unbundles a conversation event into the correct subclass of event based on the event type.
     *
     * @hide
     */
    @NonNull
    public static ConversationEvent fromBundle(@NonNull Bundle bundle) {
        final int eventType = bundle.getInt(KEY_EVENT_TYPE);
        final String conversationSessionId = bundle.getString(KEY_CONVERSATION_SESSION_ID);
        final Instant eventTimestamp = Instant.ofEpochMilli(bundle.getLong(KEY_EVENT_TIMESTAMP));
        final Bundle eventData = bundle.getBundle(KEY_EVENT_DATA);
        Objects.requireNonNull(eventData);
        Objects.requireNonNull(conversationSessionId);
        Objects.requireNonNull(eventTimestamp);

        return switch (eventType) {
            case EVENT_TYPE_ENTER ->
                    new ConversationEnterEvent(conversationSessionId, eventTimestamp, eventData);
            case EVENT_TYPE_EXIT ->
                    new ConversationExitEvent(conversationSessionId, eventTimestamp, eventData);
            case EVENT_TYPE_PROCESSING ->
                    new ConversationProcessingEvent(
                            conversationSessionId, eventTimestamp, eventData);
            case EVENT_TYPE_UPDATE ->
                    new ConversationUpdateEvent(conversationSessionId, eventTimestamp, eventData);
            default -> {
                Log.wtf(TAG, "Unknown event type: " + eventType);
                yield new ConversationEvent(conversationSessionId, eventTimestamp) {
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

    /** An event representing a new conversation being visible on the screen. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationEnterEvent extends ConversationEvent {
        private static final String KEY_ENTER_TIMESTAMP = "key_enter_timestamp";

        private final @NonNull Instant mConversationEnterTimestamp;

        public ConversationEnterEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant eventTimestamp,
                @NonNull Instant conversationEnterTimestamp) {
            super(conversationSessionId, eventTimestamp);
            mConversationEnterTimestamp = conversationEnterTimestamp;
        }

        ConversationEnterEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant eventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, eventTimestamp);
            mConversationEnterTimestamp = Instant.ofEpochMilli(bundle.getLong(KEY_ENTER_TIMESTAMP));
        }

        /** Returns the timestamp when the conversation was entered. */
        @NonNull
        public Instant getConversationEnterTimestamp() {
            return mConversationEnterTimestamp;
        }

        @Override
        int getEventType() {
            return EVENT_TYPE_ENTER;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            final Bundle bundle = new Bundle();
            bundle.putLong(KEY_ENTER_TIMESTAMP, mConversationEnterTimestamp.toEpochMilli());
            return bundle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConversationEnterEvent)) return false;
            ConversationEnterEvent that = (ConversationEnterEvent) o;
            return Objects.equals(getConversationSessionId(), that.getConversationSessionId())
                    && Objects.equals(getEventTimestamp(), that.getEventTimestamp())
                    && Objects.equals(
                            mConversationEnterTimestamp, that.mConversationEnterTimestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getEventType(),
                    getConversationSessionId(),
                    getEventTimestamp(),
                    mConversationEnterTimestamp);
        }

        @Override
        public String toString() {
            return "ConversationEnterEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "'"
                    + ", mEventTimestamp="
                    + getEventTimestamp()
                    + ", mConversationEnterTimestamp="
                    + mConversationEnterTimestamp
                    + '}';
        }
    }

    /** An event representing a conversation not being visible on the screen anymore. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationExitEvent extends ConversationEvent {
        public ConversationExitEvent(
                @NonNull String conversationSessionId, @NonNull Instant eventTimestamp) {
            super(conversationSessionId, eventTimestamp);
        }

        ConversationExitEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant eventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, eventTimestamp);
        }

        @Override
        int getEventType() {
            return EVENT_TYPE_EXIT;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            return new Bundle();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConversationExitEvent)) return false;
            ConversationExitEvent that = (ConversationExitEvent) o;
            return Objects.equals(getConversationSessionId(), that.getConversationSessionId())
                    && Objects.equals(getEventTimestamp(), that.getEventTimestamp());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getEventType(), getConversationSessionId(), getEventTimestamp());
        }

        @Override
        public String toString() {
            return "ConversationExitEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "'"
                    + ", mEventTimestamp="
                    + getEventTimestamp()
                    + '}';
        }
    }

    /** An event representing a message from a conversation on screen being processed. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationProcessingEvent extends ConversationEvent {
        private static final String KEY_START_TIMESTAMP = "key_start_timestamp";
        private static final String KEY_MESSAGE_AUTOFILL_ID = "key_message_autofill_id";

        private final @NonNull Instant mStartProcessingTimestamp;
        private final @NonNull AutofillId mMessageAutofillId;

        public ConversationProcessingEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant eventTimestamp,
                @NonNull Instant startProcessingTimestamp,
                @NonNull AutofillId messageAutofillId) {
            super(conversationSessionId, eventTimestamp);
            mStartProcessingTimestamp = startProcessingTimestamp;
            mMessageAutofillId = messageAutofillId;
        }

        ConversationProcessingEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant eventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, eventTimestamp);
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
                    && Objects.equals(getEventTimestamp(), that.getEventTimestamp())
                    && Objects.equals(mStartProcessingTimestamp, that.mStartProcessingTimestamp)
                    && Objects.equals(mMessageAutofillId, that.mMessageAutofillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getEventType(),
                    getConversationSessionId(),
                    getEventTimestamp(),
                    mStartProcessingTimestamp,
                    mMessageAutofillId);
        }

        @Override
        public String toString() {
            return "ConversationProcessingEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "'"
                    + ", mEventTimestamp="
                    + getEventTimestamp()
                    + ", mStartProcessingTimestamp="
                    + mStartProcessingTimestamp
                    + ", mMessageAutofillId="
                    + mMessageAutofillId
                    + '}';
        }
    }

    /** An event representing an update to the data of a conversation visible on the screen. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class ConversationUpdateEvent extends ConversationEvent {
        private static final String KEY_CONVERSATION_DATA = "key_conversation_data";

        private final @NonNull ConversationData mConversationData;

        public ConversationUpdateEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant eventTimestamp,
                @NonNull ConversationData conversationData) {
            super(conversationSessionId, eventTimestamp);
            mConversationData = conversationData;
        }

        ConversationUpdateEvent(
                @NonNull String conversationSessionId,
                @NonNull Instant eventTimestamp,
                @NonNull Bundle bundle) {
            super(conversationSessionId, eventTimestamp);
            mConversationData = bundle.getParcelable(KEY_CONVERSATION_DATA, ConversationData.class);
        }

        /** Returns the data of the conversation. */
        @NonNull
        public ConversationData getConversationData() {
            return mConversationData;
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
            return bundle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConversationUpdateEvent)) return false;
            ConversationUpdateEvent that = (ConversationUpdateEvent) o;
            return Objects.equals(getConversationSessionId(), that.getConversationSessionId())
                    && Objects.equals(getEventTimestamp(), that.getEventTimestamp())
                    && Objects.equals(mConversationData, that.mConversationData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getEventType(),
                    getConversationSessionId(),
                    getEventTimestamp(),
                    mConversationData);
        }

        @Override
        public String toString() {
            return "ConversationUpdateEvent{"
                    + "mConversationSessionId='"
                    + getConversationSessionId()
                    + "'"
                    + ", mEventTimestamp="
                    + getEventTimestamp()
                    + ", mConversationData="
                    + mConversationData
                    + '}';
        }
    }
}
