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

package android.view.translation;

import android.annotation.NonNull;
import android.view.ListenerWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A utility class to manage a list of {@link ListenerWrapper}. This class is not thread safe.
 * @param <T> the type of the value to be reported.
 * @hide
 */
public class ListenerGroup<T> {
    private final List<ListenerWrapper<T>> mListeners = new ArrayList<>();

    /**
     * Relays the value to all the registered {@link java.util.function.Consumer}
     */
    public void accept(@NonNull T value) {
        Objects.requireNonNull(value);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).accept(value);
        }
    }

    /**
     * Adds a {@link Consumer} to the group. If the {@link Consumer} is already present then this
     * is a no op.
     */
    public void addListener(@NonNull Executor executor, @NonNull Consumer<T> consumer) {
        if (isContained(consumer)) {
            return;
        }
        mListeners.add(new ListenerWrapper<>(executor, consumer));
    }

    /**
     * Removes a {@link Consumer} from the group. If the {@link Consumer} was not present then this
     * is a no op.
     */
    public void removeListener(@NonNull Consumer<T> consumer) {
        final int index = computeIndex(consumer);
        if (index > -1) {
            mListeners.remove(index);
        }
    }

    /**
     * Returns {@code true} if the {@link Consumer} is present in the list, {@code false}
     * otherwise.
     */
    private boolean isContained(Consumer<T> consumer) {
        return computeIndex(consumer) > -1;
    }

    /**
     * Returns the index of the matching {@link ListenerWrapper} if present, {@code -1} otherwise.
     */
    private int computeIndex(Consumer<T> consumer) {
        for (int i = 0; i < mListeners.size(); i++) {
            if (mListeners.get(i).isConsumerSame(consumer)) {
                return i;
            }
        }
        return -1;
    }
}
