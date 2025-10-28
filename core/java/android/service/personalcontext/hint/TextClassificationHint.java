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
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.view.textclassifier.TextClassification;

import androidx.annotation.NonNull;

/** A hint that contains a text classification request from selected text */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class TextClassificationHint extends ContextHint {
    @NonNull private final TextClassification.Request mTextClassificationRequest;

    static final String KEY_TEXT_CLASSIFICATION_REQUEST = "key_text_classification_request";

    /**
     * Creates a new {@link TextClassificationHint}
     *
     * @hide
     */
    TextClassificationHint(@NonNull TextClassification.Request textClassificationRequest) {
        super();
        mTextClassificationRequest = textClassificationRequest;
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    TextClassificationHint(@NonNull Bundle bundle) {
        super(bundle);
        final Bundle hintData = bundle.getBundle(KEY_HINT_DATA);
        requireNonNull(hintData, "Bundle must contain hint data");
        TextClassification.Request classificationRequest =
                hintData.getParcelable(
                        KEY_TEXT_CLASSIFICATION_REQUEST, TextClassification.Request.class);
        requireNonNull(classificationRequest, "Bundle must contain classification request");
        mTextClassificationRequest = classificationRequest;
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_TEXT_CLASSIFICATION;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_TEXT_CLASSIFICATION_REQUEST, mTextClassificationRequest);
        return bundle;
    }

    /** Get the {@link TextClassification.Request} contained in this hint. */
    @NonNull
    public TextClassification.Request getTextClassificationRequest() {
        return mTextClassificationRequest;
    }

    /** Builder used to create a {@link TextClassificationHint}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final TextClassification.Request mTextClassificationRequest;

        /**
         * Creates an instance of {@link TextClassificationHint.Builder} with the {@link
         * TextClassification.Request} contained in the hint.
         */
        public Builder(@NonNull TextClassification.Request textClassificationRequest) {
            requireNonNull(
                    textClassificationRequest, "TextClassification request must be provided");
            mTextClassificationRequest = textClassificationRequest;
        }

        /** Returns the built {@link TextClassificationHint}. */
        @NonNull
        public TextClassificationHint build() {
            return new TextClassificationHint(mTextClassificationRequest);
        }
    }
}
