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

package android.app;

import android.annotation.ColorInt;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.app.modes.ContextualMode;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Representation of a rule that modifies the behavior of certain notifications at certain times.
 * Contains a set of filters to determine what notifications are affected, a set of conditions to
 * determine when a rule is active, and an action to apply when a notification matches a given
 * filter and when the user's context matches a given condition.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
public final class NotificationRule implements Parcelable {
    // Rule ids 100-200 are reserved for user owned rules.
    /**
     * Reserved rule id for OS owned rule that highlights promoted notifications
     */
    public static final int RESERVED_ID_PROMOTED = 201;
    /**
     * Reserved rule id for the OS owned rule that highlights priority conversations
     */
    public static final int RESERVED_ID_PRIORITY_CONVERSATIONS = 202;
    /**
     * Reserved rule id for the OS owned rule that bundles lower urgency notifications
     */
    public static final int RESERVED_ID_STATIC_BUNDLES = 203;
    /**
     * Reserved rule id for the notification assistant owned rule that highlights urgent
     * notifications
\     */
    public static final int RESERVED_ID_IMPORTANT_NOTIFICATIONS = 204;

    private final List<Filter> mFilters = new ArrayList<>();
    private boolean mEnabled = true;
    private int mId;
    private @NonNull final String mName;
    private @Nullable final String mEditIntentAction;
    private @Nullable final Action mAction;
    private boolean mCanBeDisabled = true;
    private final List<Condition> mConditions = new ArrayList<>();

    /**
     * Returns the filters for this rule.
     * <p>
     * A notification matches this rule if it matches <em>any</em> of the provided filters.
     * Within a single {@link Filter}, all conditions must be met for the notification to match
     * (logical AND). Across multiple filters, the relationship is logical OR. If the list is empty,
     * all notifications match this rule.
     */
    public @NonNull List<Filter> getFilters() {
        return mFilters;
    }

    /**
     * Returns the action that should be applied to notifications that match this rule.
     */
    public @NonNull Action getAction() {
        return mAction;
    }

    /**
     * Returns whether this rule is enabled.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns the id of this rule. These must be unique within a {@link UserHandle}.
     */
    public int getId() {
        return mId;
    }

    /**
     * Returns the name of this rule.
     */
    public @NonNull String getName() {
        return mName;
    }

    /**
     * Returns whether a 'turn off rule' affordance should be displayed in the rule edit UIs.
     * @hide
     */
    @TestApi
    public boolean canBeDisabled() {
        return mCanBeDisabled;
    }

    /**
     * Returns the {@link Intent#getAction()} for the activity where this rule can be edited.
     * If null, this rule cannot be edited.
     * @hide
     */
    @TestApi
    public @Nullable String getEditIntentAction() {
        return mEditIntentAction;
    }

    /**
     * Returns the list of conditions that define when this rule is active. The rule should be
     * active if any of these conditions are met. If the list is empty the rule is always active.
     */
    public @NonNull List<Condition> getConditions() {
        return mConditions;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NotificationRule)) return false;
        NotificationRule that = (NotificationRule) o;
        return mEnabled == that.mEnabled && mId == that.mId && mCanBeDisabled == that.mCanBeDisabled
                && Objects.equals(mFilters, that.mFilters) && Objects.equals(mName,
                that.mName) && Objects.equals(mEditIntentAction, that.mEditIntentAction)
                && Objects.equals(mAction, that.mAction) && Objects.equals(
                mConditions, that.mConditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFilters, mEnabled, mId, mName, mEditIntentAction, mAction,
                mCanBeDisabled,
                mConditions);
    }

    @Override
    public String toString() {
        return "NotificationRule{" +
                "mFilters=" + mFilters +
                ", mEnabled=" + mEnabled +
                ", mId=" + mId +
                ", mName='" + mName + '\'' +
                ", mEditIntentAction='" + mEditIntentAction + '\'' +
                ", mAction=" + mAction +
                ", mCanBeDisabled=" + mCanBeDisabled +
                ", mConditions=" + mConditions +
                '}';
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeList(mFilters);
        dest.writeBoolean(mEnabled);
        dest.writeInt(mId);
        dest.writeString8(mName);
        dest.writeString8(mEditIntentAction);
        dest.writeParcelable(mAction, flags);
        dest.writeBoolean(mCanBeDisabled);
        dest.writeList(mConditions);
    }

    private NotificationRule(Parcel in) {
        mFilters.addAll(in.readArrayList(Filter.class.getClassLoader(), Filter.class));
        mEnabled = in.readBoolean();
        mId = in.readInt();
        mName = in.readString8();
        mEditIntentAction = in.readString8();
        mAction = in.readParcelable(Action.class.getClassLoader(), Action.class);
        mCanBeDisabled = in.readBoolean();
        in.readList(mConditions, Condition.class.getClassLoader(), Condition.class);
    }

    private NotificationRule(@NonNull List<Filter> filters,
            boolean enabled, int id, @Nullable String name, @Nullable String editIntentAction,
            @Nullable Action action, boolean canBeDisabled,
            @NonNull List<Condition> conditions) {
        mFilters.addAll(filters);
        mEnabled = enabled;
        mId = id;
        mName = name;
        mEditIntentAction = editIntentAction;
        mAction = action;
        mCanBeDisabled = canBeDisabled;
        mConditions.addAll(conditions);
    }

    @NonNull
    public static final Creator<NotificationRule> CREATOR =
            new Creator<>() {
                @Override
                public NotificationRule createFromParcel(Parcel in) {
                    return new NotificationRule(in);
                }

                @Override
                public NotificationRule[] newArray(int size) {
                    return new NotificationRule[size];
                }
            };

    public static final class Builder {
        private final List<Filter> mFilters = new ArrayList<>();
        private boolean mEnabled = true;
        private int mId;
        private @NonNull String mName;
        private @Nullable String mEditIntentAction;
        private @Nullable Action mAction;
        private boolean mCanBeDisabled = true;
        private final List<Condition> mConditions = new ArrayList<>();

        public Builder(@NonNull NotificationRule rule) {
            mFilters.addAll(rule.getFilters());
            mEnabled = rule.isEnabled();
            mId = rule.getId();
            mName = rule.getName();
            mEditIntentAction = rule.getEditIntentAction();
            mAction = rule.getAction();
            mCanBeDisabled = rule.canBeDisabled();
            mConditions.addAll(rule.getConditions());
        }

        public Builder(@IntRange(from=100, to=125) int mId, @NonNull String name) {
            this.mId = mId;
            this.mName = name;
        }

        /**
         * Sets the filters for this rule.
         */
        @NonNull
        public Builder setFilters(@Nullable List<Filter> filters) {
            mFilters.clear();
            if (filters != null) {
                mFilters.addAll(filters);
            }
            return this;
        }

        /**
         * Sets whether this rule is enabled.
         */
        @NonNull
        public Builder setEnabled(boolean enabled) {
            mEnabled = enabled;
            return this;
        }

        /**
         * Sets the name of this rule.
         */
        @NonNull
        public Builder setName(@NonNull String name) {
            mName = name;
            return this;
        }

        /**
         * Sets the {@link Intent#getAction()} for the screen where this rule can be edited.
         * @hide
         */
        @TestApi
        @NonNull
        public Builder setEditIntentAction(@Nullable String editIntentAction) {
            mEditIntentAction = editIntentAction;
            return this;
        }

        /**
         * Sets the action that should be applied to notifications that match this rule.
         */
        @NonNull
        public Builder setAction(@NonNull Action action) {
            mAction = action;
            return this;
        }

        /**
         * Sets whether a 'turn off rule' affordance should be displayed in the rule edit UIs.
         * @hide
         */
        @TestApi
        @NonNull
        public Builder setCanBeDisabled(boolean canBeDisabled) {
            mCanBeDisabled = canBeDisabled;
            return this;
        }

        /**
         * Sets the conditions that define when this rule is active.
         */
        @NonNull
        public Builder setConditions(@Nullable List<Condition> conditions) {
            mConditions.clear();
            if (conditions != null) {
                mConditions.addAll(conditions);
            }
            return this;
        }

        @NonNull
        public NotificationRule build() {
            return new NotificationRule(mFilters, mEnabled, mId, mName, mEditIntentAction,
                    mAction, mCanBeDisabled, mConditions);
        }
    }

    /**
     * Defines when a given rule is active based on the user's context.
     */
    public static final class Condition implements Parcelable {
        /** @hide */
        @IntDef(prefix = {"CONDITION_TYPE_"}, value = {
                CONDITION_TYPE_UNKNOWN, CONDITION_TYPE_LOCATION, CONDITION_TYPE_TIME
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface ConditionType {}

        /**
         * The condition type is unknown. This should be treated as 'always active'.
         */
        public static final int CONDITION_TYPE_UNKNOWN = 0;
        /**
         * The rule should be active when the device is at a specific location.
         */
        public static final int CONDITION_TYPE_LOCATION = 1;
        /**
         * The rule should be active on specific days at a specific time.
         */
        public static final int CONDITION_TYPE_TIME = 2;

        private @ConditionType int mConditionType = CONDITION_TYPE_UNKNOWN;

        // for location
        private double mLatitude;
        private double mLongitude;
        private float mRadiusMeters;

        // for time
        private final List<Integer> mDays = new ArrayList<>();
        private int mStartHour;
        private int mStartMinute;
        private int mEndHour;
        private int mEndMinute;

        private Condition(@NonNull List<Integer> days, int startHour, int startMinute,
                int endHour, int endMinute) {
            mConditionType = CONDITION_TYPE_TIME;
            mDays.addAll(days);
            mStartHour = startHour;
            mStartMinute = startMinute;
            mEndHour = endHour;
            mEndMinute = endMinute;
        }

        private Condition(double latitude, double longitude, float radiusMeters) {
            mConditionType = CONDITION_TYPE_LOCATION;
            mLatitude = latitude;
            mLongitude = longitude;
            mRadiusMeters = radiusMeters;
        }

        public static @NonNull Condition createTimeCondition(@NonNull List<Integer> days,
                int startHour, int startMinute, int endHour, int endMinute) {
            return new Condition(days, startHour, startMinute, endHour, endMinute);
        }

        public static @NonNull Condition createLocationCondition(double latitude, double longitude,
                float radiusMeters) {
            return new Condition(latitude, longitude, radiusMeters);
        }

        /**
         * Returns the type of this condition.
         */
        public @ConditionType int getConditionType() {
            return mConditionType;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_LOCATION} condition, returns the latitude of the
         * latitude of the geofence circle where this rule applies.
         */
        public double getLatitude() {
            return mLatitude;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_LOCATION} condition, returns the longitude of the
         * latitude of the geofence circle where this rule applies.
         */
        public double getLongitude() {
            return mLongitude;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_LOCATION} condition, returns the radius of the
         * geofence circle where this rule applies.
         */
        public float getRadiusMeters() {
            return mRadiusMeters;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_TIME} condition, returns the days on which this rule
         * should be active. See {@link java.util.Calendar#DAY_OF_WEEK}.
         */
        public @NonNull List<Integer> getDays() {
            return mDays;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_TIME} condition, returns the start hour from which
         * this rule should be active. See {@link java.util.Calendar#HOUR_OF_DAY}.
         */
        public int getStartHour() {
            return mStartHour;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_TIME} condition, returns the start minute from which
         * this rule should be active. See {@link java.util.Calendar#MINUTE}.
         */
        public int getStartMinute() {
            return mStartMinute;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_TIME} condition, returns the end hour after which
         * this rule should be inactive. See {@link java.util.Calendar#HOUR_OF_DAY}.
         */
        public int getEndHour() {
            return mEndHour;
        }

        /**
         * If this is a {@link #CONDITION_TYPE_TIME} condition, returns the end minute after which
         * this rule should be inactive. See {@link java.util.Calendar#MINUTE}.
         */
        public int getEndMinute() {
            return mEndMinute;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Condition)) return false;
            Condition condition = (Condition) o;
            return mConditionType == condition.mConditionType && Double.compare(mLatitude,
                    condition.mLatitude) == 0 && Double.compare(mLongitude, condition.mLongitude)
                    == 0 && Float.compare(mRadiusMeters, condition.mRadiusMeters) == 0
                    && mStartHour == condition.mStartHour && mStartMinute == condition.mStartMinute
                    && mEndHour == condition.mEndHour && mEndMinute == condition.mEndMinute
                    && Objects.equals(mDays, condition.mDays);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mConditionType, mLatitude, mLongitude, mRadiusMeters, mDays,
                    mStartHour, mStartMinute, mEndHour, mEndMinute);
        }

        @Override
        public String toString() {
            return "Condition{" +
                    "mConditionType=" + mConditionType +
                    ", mLatitude=" + mLatitude +
                    ", mLongitude=" + mLongitude +
                    ", mRadiusMeters=" + mRadiusMeters +
                    ", mDays=" + mDays +
                    ", mStartHour=" + mStartHour +
                    ", mStartMinute=" + mStartMinute +
                    ", mEndHour=" + mEndHour +
                    ", mEndMinute=" + mEndMinute +
                    '}';
        }

        private Condition(Parcel in) {
            mConditionType = in.readInt();
            mLatitude = in.readDouble();
            mLongitude = in.readDouble();
            mRadiusMeters = in.readFloat();
            mDays.addAll(in.readArrayList(Integer.class.getClassLoader(), Integer.class));
            mStartHour = in.readInt();
            mStartMinute = in.readInt();
        }

        public void writeToParcel(@androidx.annotation.NonNull Parcel dest, int flags) {
            dest.writeInt(mConditionType);
            dest.writeDouble(mLatitude);
            dest.writeDouble(mLongitude);
            dest.writeFloat(mRadiusMeters);
            dest.writeList(mDays);
            dest.writeInt(mStartHour);
            dest.writeInt(mStartMinute);
        }

        @NonNull
        public static final Creator<Condition> CREATOR =
                new Creator<>() {
                    @Override
                    public Condition createFromParcel(Parcel in) {
                        return new Condition(in);
                    }

                    @Override
                    public Condition[] newArray(int size) {
                        return new Condition[size];
                    }
                };
    }

    public static final class Action implements Parcelable {
        /** @hide */
        @IntDef(prefix = {"PRIMARY_ACTION_"}, value = {
                PRIMARY_ACTION_NONE, PRIMARY_ACTION_HIGHLIGHT, PRIMARY_ACTION_DEFAULT,
                PRIMARY_ACTION_LOW, PRIMARY_ACTION_BUNDLE, PRIMARY_ACTION_BLOCK
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface PrimaryAction {}

        /**
         * No primary action should be applied to notifications that match this rule's filter.
         * Specific behavioral overrides may still apply.
         */
        public static final int PRIMARY_ACTION_NONE = 0;
        /**
         * Notifications that match this filter should be highlighted. This means they will have
         * elevated audible and visual alerts.
         */
        public static final int PRIMARY_ACTION_HIGHLIGHT = 1;
        /**
         * Notifications that match this filter should have their importance set to
         * {@link NotificationManager#IMPORTANCE_DEFAULT}.
         */
        public static final int PRIMARY_ACTION_DEFAULT = 2;
        /**
         * Notifications that match this filter should have their importance set to
         * {@link NotificationManager#IMPORTANCE_LOW}.
         */
        public static final int PRIMARY_ACTION_LOW = 3;
        /**
         * Notifications that match this filter should be classified into a bundle.
         */
        public static final int PRIMARY_ACTION_BUNDLE = 4;
        /**
         * Notifications that match this filter should be blocked (not posted).
         */
        public static final int PRIMARY_ACTION_BLOCK = 5;

        private @PrimaryAction int mPrimaryAction = PRIMARY_ACTION_NONE;
        private final @Nullable Uri mSoundHapticOverride;
        private @ColorInt int mLightColor = 0;
        private final List<String> mModeBreakthroughs = new ArrayList<>();
        private final @Nullable String mBundleName;
        private final @Nullable String mEmojiIcon;

        /**
         * Returns the primary action for this rule.
         */
        public @PrimaryAction int getPrimaryAction() {
            return mPrimaryAction;
        }

        /**
         * If non-null, notifications that match this rule's filtering conditions should use this
         * sound and haptics.
         */
        public @Nullable Uri getSoundHapticOverride() {
            return mSoundHapticOverride;
        }

        /**
         * If non-negative, notifications that match this rule's filtering conditions should trigger
         * the notification light with this color.
         */
        public @ColorInt int getLightColorOverride() {
            return mLightColor;
        }

        /**
         * If non-null and non-empty, notifications that match this rule's filtering conditions
         * should bypass the modes in this list.
         */
        public @NonNull List<String> getModeBreakthroughIds() {
            return mModeBreakthroughs;
        }

        /**
         * If this Action's primary action is {@link #PRIMARY_ACTION_BUNDLE}, notifications that
         * match this rule's filtering conditions should be placed in a bundle with this name.
         */
        public @Nullable String getDynamicBundleName() {
            return mBundleName;
        }

        /**
         * If this Action's primary action is {@link #PRIMARY_ACTION_BUNDLE}, notifications that
         * match this rule's filtering conditions should be placed in a bundle with this icon.
         */
        public @Nullable String getDynamicBundleEmojiIcon() {
            return mEmojiIcon;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        private Action(int primaryAction, @Nullable Uri soundHapticOverride, int lightColor,
                @NonNull List<String> modeBreakthroughs, @Nullable String bundleName,
                @Nullable String emojiIcon) {
            mPrimaryAction = primaryAction;
            mSoundHapticOverride = soundHapticOverride;
            mLightColor = lightColor;
            mModeBreakthroughs.addAll(modeBreakthroughs);
            mBundleName = bundleName;
            mEmojiIcon = emojiIcon;
        }

        private Action(Parcel in) {
            mPrimaryAction = in.readInt();
            if (in.readByte() != 0) {
                mSoundHapticOverride = Uri.CREATOR.createFromParcel(in);
            } else {
                mSoundHapticOverride = null;
            }
            mLightColor = in.readInt();
            in.readStringList(mModeBreakthroughs);
            mBundleName = in.readString8();
            mEmojiIcon = in.readString8();
        }

        @Override
        public void writeToParcel(@androidx.annotation.NonNull Parcel dest, int flags) {
            dest.writeInt(mPrimaryAction);
            if (mSoundHapticOverride != null) {
                dest.writeByte((byte) 1);
                mSoundHapticOverride.writeToParcel(dest, flags);
            }
            dest.writeInt(mLightColor);
            dest.writeStringList(mModeBreakthroughs);
            dest.writeString8(mBundleName);
            dest.writeString8(mEmojiIcon);
        }

        @NonNull
        public static final Creator<Action> CREATOR =
                new Creator<>() {
                    @Override
                    public Action createFromParcel(Parcel in) {
                        return new Action(in);
                    }

                    @Override
                    public Action[] newArray(int size) {
                        return new Action[size];
                    }
                };

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Action)) return false;
            Action action = (Action) o;
            return mPrimaryAction == action.mPrimaryAction && mLightColor == action.mLightColor
                    && Objects.equals(mSoundHapticOverride, action.mSoundHapticOverride)
                    && Objects.equals(mModeBreakthroughs, action.mModeBreakthroughs)
                    && Objects.equals(mBundleName, action.mBundleName)
                    && Objects.equals(mEmojiIcon, action.mEmojiIcon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPrimaryAction, mSoundHapticOverride, mLightColor,
                    mModeBreakthroughs, mBundleName, mEmojiIcon);
        }

        @Override
        public String toString() {
            return "Action{" +
                    "mPrimaryAction=" + mPrimaryAction +
                    ", mSoundHapticOverride=" + mSoundHapticOverride +
                    ", mLightColor=" + mLightColor +
                    ", mModeBreakthroughs=" + mModeBreakthroughs +
                    ", mBundleName='" + mBundleName + '\'' +
                    ", mEmojiIcon='" + mEmojiIcon + '\'' +
                    '}';
        }

        public static final class Builder {
            private @PrimaryAction int mPrimaryAction = PRIMARY_ACTION_NONE;
            private Uri mSoundHapticOverride;
            private @ColorInt int mLightColor = 0;
            private final List<String> mModeBreakthroughs = new ArrayList<>();
            private String mBundleName;
            private String mEmojiIcon;

            public Builder(@NonNull Action action) {
                this.mPrimaryAction = action.getPrimaryAction();
                this.mSoundHapticOverride = action.getSoundHapticOverride();
                this.mLightColor = action.getLightColorOverride();
                this.mModeBreakthroughs.addAll(action.getModeBreakthroughIds());
                this.mBundleName = action.getDynamicBundleName();
                this.mEmojiIcon = action.getDynamicBundleEmojiIcon();
            }

            public Builder(@PrimaryAction int primaryAction) {
                mPrimaryAction = primaryAction;
            }

            /**
             * Sets the light color for the lights that should flash when a notification
             * meets this rule's filter conditions.
             * <p>Set to null if this rule should not override the notification's
             * inherent 'lights' behavior (see {@link NotificationChannel#enableLights(boolean)}
             * and {@link NotificationChannel#getLightColor()}).
             */
            public @NonNull Builder setLightColorOverride(@ColorInt int lightColor) {
                mLightColor = lightColor;
                return this;
            }

            /**
             * Sets the uri for the sound and haptics that should play when a notification
             * meets this rule's filter conditions.
             * <p>Set to null if this rule should not override the notification's inherent
             * 'sound and haptics' behavior (see {@link NotificationChannel#getSound()} and
             * {@link NotificationChannel#getVibrationEffect()}).
             */
            public @NonNull Builder setSoundHapticOverride(@Nullable Uri soundHapticOverride) {
                mSoundHapticOverride = soundHapticOverride;
                return this;
            }

            /**
             * Sets the list of {@link ContextualMode#getId() modes} that can be
             * bypassed when a notification meets this rule's filter conditions.
             * <p>Set to null if these notifications should not break through any modes due to this
             * rule.
             */
            public @NonNull Builder setModeBreakthroughIds(
                    @Nullable List<String> modeBreakthroughIds) {
                mModeBreakthroughs.clear();
                if (modeBreakthroughIds != null) {
                    mModeBreakthroughs.addAll(modeBreakthroughIds);
                }
                return this;
            }

            /**
             * If the primary action for this rule is {@link #PRIMARY_ACTION_BUNDLE}, sets the name
             * for the bundle.
             * <p>This has no effect if the primary action is not {@link #PRIMARY_ACTION_BUNDLE}. If
             * the primary action is {@link #PRIMARY_ACTION_BUNDLE} and no bundle name is specified,
             * a default name will be shown.
             */
            public @NonNull Builder setDynamicBundleName(@Nullable String name) {
                mBundleName = name;
                return this;
            }

            /**
             * If the primary action for this rule is {@link #PRIMARY_ACTION_BUNDLE}, sets the emoji
             * icon for the bundle.
             */
            public @NonNull Builder setDynamicBundleEmojiIcon(@Nullable String emojiIcon) {
                mEmojiIcon = emojiIcon;
                return this;
            }

            public @NonNull Action build() {
                return new Action(mPrimaryAction, mSoundHapticOverride, mLightColor,
                        mModeBreakthroughs, mBundleName, mEmojiIcon);
            }
        }
    }

    /**
     * Criteria for which notifications this rule applies to.
     */
    public static final class Filter implements Parcelable {
        /** @hide */
        @IntDef(prefix = {"CONTACT_LEVEL_"}, value = {
                CONTACT_LEVEL_ANY, CONTACT_LEVEL_CONTACT, CONTACT_LEVEL_STARRED,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface ContactLevel {}

        /** @hide */
        @IntDef(prefix = {"CONVERSATION_LEVEL_"}, value = {
                CONVERSATION_LEVEL_ANY, CONVERSATION_LEVEL_PRIORITY,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface ConversationLevel {}

        /**
         * Do not filter on the contact level of a notification.
         */
        public static final int CONTACT_LEVEL_ANY = 0;
        /**
         * Messaging notification is from a contact.
         */
        public static final int CONTACT_LEVEL_CONTACT = 1 ;
        /**
         * Messaging notification is from a starred contact.
         */
        public static final int CONTACT_LEVEL_STARRED = 2;
        /**
         * Do not filter on the conversation level of a notification.
         */
        public static final int CONVERSATION_LEVEL_ANY = 0;
        /**
         * Messaging notification is from a priority conversation.
         */
        public static final int CONVERSATION_LEVEL_PRIORITY = 1;

        final List<Integer> mIncludedPackages = new ArrayList<>();
        final List<Integer> mExcludedPackages = new ArrayList<>();
        int mContactLevel = CONTACT_LEVEL_ANY;
        int mConversationLevel = CONVERSATION_LEVEL_ANY;
        final List<Uri> mContacts = new ArrayList<>();
        final List<String> mShortcutIds = new ArrayList<>();
        final List<String> mKeywords = new ArrayList<>();
        final List<UserHandle> mUsers = new ArrayList<>();
        final List<String> mCategories = new ArrayList<>();
        final List<Integer> mStaticBundleTypes = new ArrayList<>();
        int mFlags = 0;

        private Filter(List<Integer> includedPackages, List<Integer> excludedPackages,
                int contactLevel, int conversationLevel, List<Uri> contacts,
                List<String> shortcutIds,
                List<String> keywords, List<UserHandle> users,
                List<String> categories, List<Integer> staticBundleTypes, int flags) {
            mIncludedPackages.addAll(includedPackages);
            mExcludedPackages.addAll(excludedPackages);
            mContactLevel = contactLevel;
            mConversationLevel = conversationLevel;
            mContacts.addAll(contacts);
            mShortcutIds.addAll(shortcutIds);
            mKeywords.addAll(keywords);
            mUsers.addAll(users);
            mCategories.addAll(categories);
            mStaticBundleTypes.addAll(staticBundleTypes);
            mFlags = flags;
        }

        /**
         * Returns the list of package UIDs that this rule applies to.
         * If empty, this rule applies to all packages (unless excluded).
         * <p>Compare to {@link StatusBarNotification#getUid()}.
         */
        public @NonNull List<Integer> getIncludedPackageUids() {
            return mIncludedPackages;
        }

        /**
         * Returns the list of package UIDs that this rule does NOT apply to.
         * <p>Compare to {@link StatusBarNotification#getUid()}.
         */
        public @NonNull List<Integer> getExcludedPackageUids() {
            return mExcludedPackages;
        }

        /**
         * Returns the contact level required for this rule to apply.
         * <p>Compare to {@link Notification#EXTRA_PEOPLE_LIST} and
         * {@link Notification#EXTRA_PEOPLE}.
         */
        public @ContactLevel int getContactLevel() {
            return mContactLevel;
        }

        /**
         * Returns the conversation level required for this rule to apply.
         * <p>Compare to {@link NotificationChannel#isImportantConversation()}.
         */
        public @ConversationLevel int getConversationLevel() {
            return mConversationLevel;
        }

        /**
         * Returns the list of specific contacts that this rule applies to.
         */
        public @NonNull List<Uri> getContacts() {
            return mContacts;
        }

        /**
         * Returns the list of shortcut IDs that this rule applies to.
         * <p>Compare to {@link Notification#getShortcutId()}.
         */
        public @NonNull List<String> getShortcutIds() {
            return mShortcutIds;
        }

        /**
         * Returns the list of keywords to match against notification text.
         */
        public @NonNull List<String> getKeywords() {
            return mKeywords;
        }

        /**
         * Returns the list of users that this rule applies to.
         * If empty, this rule applies to the current user and all of its profiles.
         * <p>Compare to {@link StatusBarNotification#getUser()}.
         */
        public @NonNull List<UserHandle> getUsers() {
            return mUsers;
        }

        /**
         * Returns the list of {@link Notification#category categories} that this rule applies to.
         * <p>Compare to {@link Notification#category}.
         */
        public @NonNull List<String> getCategories() {
            return mCategories;
        }

        /**
         * Returns the list of static bundle types that this rule applies to.
         * See {@link android.service.notification.Adjustment#TYPE_NEWS},
         * {@link android.service.notification.Adjustment#TYPE_CONTENT_RECOMMENDATION},
         * {@link android.service.notification.Adjustment#TYPE_PROMOTION},
         * {@link android.service.notification.Adjustment#TYPE_SOCIAL_MEDIA}.
         */
        public @NonNull List<Integer> getStaticBundleTypes() {
            return mStaticBundleTypes;
        }

        /**
         * Returns the {@link Notification#flags flags mask} that this rule applies to.
         * <p>Compare to {@link Notification#flags}.
         */
        public int getFlags() {
            return mFlags;
        }

        @Override
        public String toString() {
            return "Filter{" +
                    "mIncludedPackages=" + mIncludedPackages +
                    ", mExcludedPackages=" + mExcludedPackages +
                    ", mContactLevel=" + mContactLevel +
                    ", mConversationLevel=" + mConversationLevel +
                    ", mContacts=" + mContacts +
                    ", mShortcutIds=" + mShortcutIds +
                    ", mKeywords=" + mKeywords +
                    ", mUsers=" + mUsers +
                    ", mCategories=" + mCategories +
                    ", mStaticBundleTypes=" + mStaticBundleTypes +
                    ", mFlags=" + mFlags +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Filter)) return false;
            Filter filter = (Filter) o;
            return mContactLevel == filter.mContactLevel
                    && mConversationLevel == filter.mConversationLevel
                    && mFlags == filter.mFlags
                    && Objects.equals(mIncludedPackages, filter.mIncludedPackages)
                    && Objects.equals(mExcludedPackages, filter.mExcludedPackages)
                    && Objects.equals(mContacts, filter.mContacts) && Objects.equals(
                    mShortcutIds, filter.mShortcutIds) && Objects.equals(mKeywords,
                    filter.mKeywords) && Objects.equals(mUsers, filter.mUsers)
                    && Objects.equals(mCategories, filter.mCategories)
                    && Objects.equals(mStaticBundleTypes, filter.mStaticBundleTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIncludedPackages, mExcludedPackages, mContactLevel,
                    mConversationLevel, mContacts, mShortcutIds, mKeywords, mUsers,
                    mCategories, mStaticBundleTypes, mFlags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @NonNull
        public static final Creator<Filter> CREATOR =
                new Creator<>() {
                    @Override
                    public Filter createFromParcel(Parcel in) {
                        return new Filter(in);
                    }

                    @Override
                    public Filter[] newArray(int size) {
                        return new Filter[size];
                    }
                };

        private Filter(Parcel in) {
            mIncludedPackages.addAll(in.readArrayList(null, Integer.class));
            mExcludedPackages.addAll(in.readArrayList(null, Integer.class));
            mContactLevel = in.readInt();
            mConversationLevel = in.readInt();
            mContacts.addAll(in.readArrayList(Uri.class.getClassLoader(), Uri.class));
            in.readStringList(mShortcutIds);
            mKeywords.addAll(in.readArrayList(null, String.class));
            mUsers.addAll(in.readArrayList(UserHandle.class.getClassLoader(), UserHandle.class));
            in.readStringList(mCategories);
            mStaticBundleTypes .addAll(in.readArrayList(null, Integer.class));
            mFlags = in.readInt();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeList(mIncludedPackages);
            dest.writeList(mExcludedPackages);
            dest.writeInt(mContactLevel);
            dest.writeInt(mConversationLevel);
            dest.writeList(mContacts);
            dest.writeStringList(mShortcutIds);
            dest.writeList(mKeywords);
            dest.writeList(mUsers);
            dest.writeStringList(mCategories);
            dest.writeList(mStaticBundleTypes);
            dest.writeInt(mFlags);
        }

        public static final class Builder {
            final List<Integer> mIncludedPackages = new ArrayList<>();
            final List<Integer> mExcludedPackages = new ArrayList<>();
            int mContactLevel = CONTACT_LEVEL_ANY;
            int mConversationLevel = CONVERSATION_LEVEL_ANY;
            final List<Uri> mContacts = new ArrayList<>();
            final List<String> mShortcutIds  = new ArrayList<>();
            final List<String> mKeywords = new ArrayList<>();
            final List<UserHandle> mUsers = new ArrayList<>();
            final List<String> mCategories = new ArrayList<>();
            final List<Integer> mStaticBundleTypes = new ArrayList<>();
            int mFlags = 0;

            public Builder() {}

            public Builder(@NonNull Filter filter) {
                mIncludedPackages.addAll(filter.getIncludedPackageUids());
                mExcludedPackages.addAll(filter.getExcludedPackageUids());
                mContactLevel = filter.getContactLevel();
                mConversationLevel = filter.getConversationLevel();
                mContacts.addAll(filter.getContacts());
                mShortcutIds.addAll(filter.getShortcutIds());
                mKeywords.addAll(filter.getKeywords());
                mUsers.addAll(filter.getUsers());
                mCategories.addAll(filter.getCategories());
                mStaticBundleTypes.addAll(filter.getStaticBundleTypes());
                mFlags = filter.getFlags();
            }

            /**
             * Apply this rule only to notifications that are sent from any of these packages.
             * <p>Compare to {@link StatusBarNotification#getUid()}.
             * <p>If unspecified this rule will apply to all packages.
             * @param packageList A set of package uids
             */
            @NonNull
            public Builder setIncludedPackageUids(@Nullable List<Integer> packageList) {
                mIncludedPackages.clear();
                if (packageList != null) {
                    mIncludedPackages.addAll(packageList);
                }
                return this;
            }

            /**
             * Do not apply this rule to any notifications that are sent from any of these packages
             * <p>Compare to {@link StatusBarNotification#getUid()}.
             * <p>If unspecified this rule will apply to all packages.
             * @param packageList A set of package uids
             */
            @NonNull
            public Builder setExcludedPackageUids(@Nullable List<Integer> packageList) {
                mExcludedPackages.clear();
                if (packageList != null) {
                    mExcludedPackages.addAll(packageList);
                }
                return this;
            }

            /**
             * Apply this rule to any notification that is from a contact that matches this level.
             * <p>Compare to {@link Notification#EXTRA_PEOPLE_LIST} and
             * {@link Notification#EXTRA_PEOPLE}.
             * @param contactLevel A contact level that this rule should apply to
             */
            @NonNull
            public Builder setContactLevel(@ContactLevel int contactLevel) {
                mContactLevel = contactLevel;
                return this;
            }

            /**
             * Apply this rule to any notification that is from a conversation that matches this
             * level.
             * <p>Compare to {@link NotificationChannel#isImportantConversation()}.
             * @param conversationLevel A conversation level that this rule should apply to
             */
            @NonNull
            public Builder setConversationLevel(@ConversationLevel int conversationLevel) {
                mConversationLevel = conversationLevel;
                return this;
            }

            /**
             * Apply this rule to any notification that is from one of these contacts.
             * <p>Compare to {@link Notification#EXTRA_PEOPLE_LIST} and
             * {@link Notification#EXTRA_PEOPLE}.
             * @param contactList A set of contacts in the contact database that this rule should
             *                    apply to.
             */
            @NonNull
            public Builder setContacts(@Nullable List<Uri> contactList) {
                mContacts.clear();
                if (contactList != null) {
                    mContacts.addAll(contactList);
                }
                return this;
            }

            /**
             * Apply this rule to any notification that is affiliated with one of these shortcuts
             * <p>Compare to {@link Notification#getShortcutId()}.
             * @param shortcutIds A set of {@link ShortcutInfo#getId() shortcut ids} that this rule
             *                    should apply to.
             */
            @NonNull
            public Builder setShortcutIds(@Nullable List<String> shortcutIds) {
                mShortcutIds.clear();
                if (shortcutIds != null) {
                    mShortcutIds.addAll(shortcutIds);
                }
                return this;
            }

            /**
             * Apply this rule to any notification whose visible text matches any of these keywords.
             * @param keywords A list of keywords to match against notification text
             */
            @NonNull
            public Builder setKeywords(@Nullable List<String> keywords) {
                mKeywords.clear();
                if (keywords != null) {
                    mKeywords.addAll(keywords);
                }
                return this;
            }

            /**
             * Apply this rule to notifications from the provided users.
             * <p>Compare to {@link StatusBarNotification#getUser()}.
             * @param users Zero of more users from the set of [current user,
             *              current user's profile(s)]
             */
            @NonNull
            public Builder setUsers(@Nullable List<UserHandle> users) {
                mUsers.clear();
                if (users != null) {
                    mUsers.addAll(users);
                }
                return this;
            }

            /**
             * Apply this rule to notifications that match one of the provided categories
             * <p>Compare to {@link Notification#category}.
             * @param categories A set of categories that this rule should apply to
             */
            @NonNull
            public Builder setCategories(@Nullable List<String> categories) {
                mCategories.clear();
                if (categories != null) {
                    mCategories.addAll(categories);
                }
                return this;
            }

            /**
             * Apply this rule to notifications whose content matches one of these classification
             * types. See {@link android.service.notification.Adjustment#TYPE_NEWS},
             * {@link android.service.notification.Adjustment#TYPE_CONTENT_RECOMMENDATION},
             * {@link android.service.notification.Adjustment#TYPE_PROMOTION},
             * {@link android.service.notification.Adjustment#TYPE_SOCIAL_MEDIA}.
             */
            @NonNull
            public Builder setStaticBundleTypes(@Nullable List<Integer> bundleTypes) {
                mStaticBundleTypes.clear();
                if (bundleTypes != null) {
                    mStaticBundleTypes.addAll(bundleTypes);
                }
                return this;
            }

            /**
             * Apply this rule to notifications that match all the provided flags.
             * <p>Compare to {@link Notification#flags}.
             * @param flagMask A mask of flags that this rule should apply to
             */
            @NonNull
            public Builder setFlags(int flagMask) {
                mFlags = flagMask;
                return this;
            }

            @NonNull
            public Filter build() {
                return new Filter(mIncludedPackages, mExcludedPackages, mContactLevel,
                        mConversationLevel, mContacts, mShortcutIds, mKeywords,
                        mUsers, mCategories, mStaticBundleTypes, mFlags);
            }
        }
    }
}
