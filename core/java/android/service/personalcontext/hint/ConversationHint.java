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

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

import java.util.Objects;

/**
 * A hint that contains a conversation-related event.
 *
 * <p>The data encapsulated in this hint originates from the Android Content Capture API and
 * represents the state of a conversation from on-screen content, primarily from messaging
 * applications. It captures messages and metadata at a specific moment.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ConversationHint extends ContextHint {
    private static final String KEY_CONVERSATION_EVENT = "key_conversation_event";
    private final ConversationEvent mConversationEvent;

    private ConversationHint(
            @NonNull ConstructorParams baseParams, @NonNull ConversationEvent conversationEvent) {
        super(baseParams);
        mConversationEvent = requireNonNull(conversationEvent);
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    ConversationHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        this(
                baseParams,
                requireNonNull(
                        ConversationEvent.fromBundle(
                                requireNonNull(bundle.getBundle(KEY_CONVERSATION_EVENT))),
                        "Bundle must contain conversation event"));
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_CONVERSATION;
    }

    /** Returns the {@link ConversationEvent} contained in this hint. */
    @NonNull
    public ConversationEvent getConversationEvent() {
        return mConversationEvent;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        final Bundle bundle = new Bundle();
        bundle.putBundle(KEY_CONVERSATION_EVENT, mConversationEvent.toBundle());
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConversationHint)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ConversationHint that = (ConversationHint) o;
        return Objects.equals(mConversationEvent, that.mConversationEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mConversationEvent);
    }

    @Override
    public String toString() {
        return "ConversationHint{"
                + "mId="
                + getHintId()
                + ", mConversationEvent="
                + mConversationEvent
                + "}";
    }

    /** Builder for {@link ConversationHint}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final ConversationEvent mConversationEvent;

        /**
         * Creates an instance of {@link Builder} with the {@link ConversationEvent} contained in
         * the hint.
         */
        public Builder(@NonNull ConversationEvent conversationEvent) {
            mConversationEvent = requireNonNull(conversationEvent);
        }

        /**
         * Adds a token to the resulting {@link ConversationHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /** Creates the {@link ConversationHint}. */
        @NonNull
        public ConversationHint build() {
            return new ConversationHint(mBaseBuilder.build(), mConversationEvent);
        }
    }
}
