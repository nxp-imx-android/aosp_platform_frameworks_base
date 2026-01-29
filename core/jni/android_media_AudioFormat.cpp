/*
 * Copyright (C) 2026 The Android Open Source Project
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

#include "android_media_AudioFormat.h"

audio_channel_mask_t channelMasksToNative(jint channelPositionMask, jint channelIndexMask,
                                          jboolean isInput) {
    if (channelIndexMask != CHANNEL_INVALID) { // channel index mask takes priority
        // To convert to a native channel mask, the Java channel index mask
        // requires adding the index representation.
        return audio_channel_mask_from_representation_and_bits(AUDIO_CHANNEL_REPRESENTATION_INDEX,
                                                               channelIndexMask);
    }
    // Haptic channels should be preserved between java and native masks.
    uint32_t audioChannels = (channelPositionMask & ~AUDIO_CHANNEL_HAPTIC_ALL);
    uint32_t hapticChannels = (channelPositionMask & AUDIO_CHANNEL_HAPTIC_ALL);
    uint32_t convertedAudioChannels =
            isInput ? inChannelMaskToNative(audioChannels) : outChannelMaskToNative(audioChannels);
    return (audio_channel_mask_t)(convertedAudioChannels | hapticChannels);
}

ScopedLocalRef<jobject> javaChannelMasksFromNativeChannelMask(JNIEnv* env,
                                                              const ChannelMasks::fields_t fields,
                                                              audio_channel_mask_t nMask,
                                                              jboolean isInput) {
    jint positionMask = CHANNEL_INVALID;
    jint indexMask = CHANNEL_INVALID;
    if (audio_channel_mask_get_representation(nMask) == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
        indexMask = audio_channel_mask_get_bits(nMask);
    } else {
        positionMask = isInput ? inChannelMaskFromNative(nMask) : outChannelMaskFromNative(nMask);
    }
    return ScopedLocalRef<jobject>(env,
                                   env->NewObject(fields.clazz, fields.constructID, positionMask,
                                                  indexMask));
}

ScopedLocalRef<jobject> javaChannelMasksArrayFromNative(JNIEnv* env,
                                                        const ChannelMasksArray::fields_t& fields,
                                                        unsigned int num_channel_masks,
                                                        const audio_channel_mask_t* channel_masks,
                                                        jboolean useInMask) {
    size_t numPositionMasks = 0;
    size_t numIndexMasks = 0;
    // count up how many masks are positional and indexed
    for (size_t index = 0; index < num_channel_masks; index++) {
        const audio_channel_mask_t mask = channel_masks[index];
        if (audio_channel_mask_get_representation(mask) == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
            numIndexMasks++;
        } else {
            numPositionMasks++;
        }
    }
    ScopedLocalRef<jintArray> jChannelMasks(env, env->NewIntArray(numPositionMasks));
    ScopedLocalRef<jintArray> jChannelIndexMasks(env, env->NewIntArray(numIndexMasks));
    if (!jChannelMasks.get() || !jChannelIndexMasks.get()) {
        return ScopedLocalRef<jobject>(env);
    }
    // put the masks in the output arrays
    for (size_t maskIndex = 0, posMaskIndex = 0, indexedMaskIndex = 0;
         maskIndex < num_channel_masks; maskIndex++) {
        const audio_channel_mask_t mask = channel_masks[maskIndex];
        if (audio_channel_mask_get_representation(mask) == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
            jint jMask = audio_channel_mask_get_bits(mask);
            env->SetIntArrayRegion(jChannelIndexMasks.get(), indexedMaskIndex++, 1, &jMask);
        } else {
            jint jMask = useInMask ? inChannelMaskFromNative(mask) : outChannelMaskFromNative(mask);
            env->SetIntArrayRegion(jChannelMasks.get(), posMaskIndex++, 1, &jMask);
        }
    }
    return ScopedLocalRef<jobject>(env,
                                   env->NewObject(fields.clazz, fields.constructID,
                                                  jChannelMasks.get(), jChannelIndexMasks.get()));
}
