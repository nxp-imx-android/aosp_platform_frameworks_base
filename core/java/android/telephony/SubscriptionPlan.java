/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.telephony;

import android.annotation.BytesLong;
import android.annotation.CurrentTimeMillisLong;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.Annotation.NetworkType;
import android.util.Range;
import android.util.RecurrenceRule;

import com.android.internal.telephony.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

/**
 * Description of a billing relationship plan between a carrier and a specific
 * subscriber. This information is used to present more useful UI to users, such
 * as explaining how much mobile data they have remaining, and what will happen
 * when they run out.
 * <p>
 * If specifying network types, the developer must supply at least one plan
 * that applies to all network types (default), and all additional plans
 * may not include a particular network type more than once.
 * This is enforced by {@link SubscriptionManager} when setting the plans.
 * <p>
 * Plan selection will prefer plans that have specific network types defined
 * over plans that apply to all network types.
 *
 * @see SubscriptionManager#setSubscriptionPlans(int, java.util.List)
 * @see SubscriptionManager#getSubscriptionPlans(int)
 */
public final class SubscriptionPlan implements Parcelable {
    /** @hide */
    @IntDef(prefix = "LIMIT_BEHAVIOR_", value = {
            LIMIT_BEHAVIOR_UNKNOWN,
            LIMIT_BEHAVIOR_DISABLED,
            LIMIT_BEHAVIOR_BILLED,
            LIMIT_BEHAVIOR_THROTTLED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LimitBehavior {}

    /**
     * Indicates that the behavior for when a data limit is reached is unknown.
     * <p>
     * This is the default value and should be used when the carrier has not specified what
     * happens when the user's data usage exceeds the limit.
     */
    public static final int LIMIT_BEHAVIOR_UNKNOWN = -1;

    /**
     * Indicates that data access is disabled when the data limit is reached.
     * <p>
     * Once the user's data usage hits the defined limit, their mobile data connection will be
     * turned off until the next billing cycle begins.
     */
    public static final int LIMIT_BEHAVIOR_DISABLED = 0;

    /**
     * Indicates that the user will be billed for data usage beyond the limit.
     * <p>
     * When the user exceeds their data limit, they will incur overage charges. Data access
     * continues, but at an additional cost.
     */
    public static final int LIMIT_BEHAVIOR_BILLED = 1;

    /**
     * Indicates that data access is throttled to a slower speed when the limit is reached.
     * <p>
     * After the user consumes their high-speed data allowance, the data connection remains
     * active but is reduced to a lower bandwidth for the remainder of the billing cycle.
     */
    public static final int LIMIT_BEHAVIOR_THROTTLED = 2;

    /**
     * Value indicating a number of bytes is unknown.
     * <p>
     * This value is used when the carrier has not provided a specific data limit or usage value.
     */
    public static final long BYTES_UNKNOWN = -1;

    /**
     * Value indicating a number of bytes is unlimited.
     * <p>
     * This value is used when the carrier has not specified a data limit.
     */
    public static final long BYTES_UNLIMITED = Long.MAX_VALUE;

    /** Value indicating a timestamp is unknown. */
    public static final long TIME_UNKNOWN = -1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "SUBSCRIPTION_STATUS_" }, value = {
            SUBSCRIPTION_STATUS_UNKNOWN,
            SUBSCRIPTION_STATUS_ACTIVE,
            SUBSCRIPTION_STATUS_INACTIVE,
            SUBSCRIPTION_STATUS_TRIAL,
            SUBSCRIPTION_STATUS_SUSPENDED
    })
    public @interface SubscriptionStatus {}

    /**
     * The subscription status is unknown.
     * <p>
     * This is the default value, used when the carrier is unable to provide the current status
     * of the subscription.
     */
    @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public static final int SUBSCRIPTION_STATUS_UNKNOWN = 0;

    /**
     * The subscription is active.
     * <p>
     * This indicates that the subscription is in good standing and all services are available
     * to the user.
     */
    @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public static final int SUBSCRIPTION_STATUS_ACTIVE = 1;

    /**
     * The subscription is inactive.
     * <p>
     * This status means the subscription is not currently in service. This could be because it
     * has been canceled, has expired, or has not yet been activated.
     */
    @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public static final int SUBSCRIPTION_STATUS_INACTIVE = 2;

    /**
     * The subscription is in a trial period.
     * <p>
     * This indicates that the user is on a promotional or trial plan, which may have different
     * features or limitations than a standard subscription. After the trial period ends, the
     * status will typically change to active or inactive.
     */
    @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public static final int SUBSCRIPTION_STATUS_TRIAL = 3;

    /**
     * The subscription is suspended.
     * <p>
     * A suspended subscription has been temporarily disabled. This can occur due to billing
     * issues, a user's request, or a violation of the carrier's terms of service. Services are
     * unavailable, but the subscription can typically be reactivated.
     */
    @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    public static final int SUBSCRIPTION_STATUS_SUSPENDED = 4;

    /**
     * The billing cycle of the subscription plan. It defines a series of time intervals
     * over which usage is measured and billed.
     * <p>
     * For example, a monthly plan might have a cycle that starts on the 5th of each month and
     * ends on the 4th of the following month.
     *
     * @see #cycleIterator()
     */
    @NonNull
    private final RecurrenceRule mCycleRule;

    /**
     * A user-visible title for this plan. For example, "Unlimited+" or "Family plan".
     */
    @Nullable
    private final CharSequence mTitle;

    /**
     * A brief, human-readable summary of the subscription plan.
     * <p>
     * This could include details about the plan's features, such as "10GB of high-speed data"
     * or "Unlimited talk and text". This summary is intended for display to the user.
     *
     * @see #getSummary()
     */
    @Nullable
    private final CharSequence mSummary;

    /**
     * The data limit for this billing cycle, in bytes. When the user's data usage reaches this
     * limit, the behavior of data access will change as defined by
     * {@link #getDataLimitBehavior()}.
     * <p>
     * This value can be {@link #BYTES_UNKNOWN} if the data limit is unknown, or
     * {@link #BYTES_UNLIMITED} if there is no data limit.
     *
     * @see #getDataLimitBytes()
     * @see #getDataLimitBehavior()
     */
    private final long mDataLimitBytes;

    /**
     * The behavior of data access when usage reaches the data limit defined by
     * {@link #getDataLimitBytes()}. This defines what happens to the user's data connection
     * once they have consumed a certain amount of data.
     * <p>
     * For example, the carrier might throttle the connection to a slower speed, disable it
     * entirely, or start billing for overage.
     *
     * @see #LIMIT_BEHAVIOR_UNKNOWN
     * @see #LIMIT_BEHAVIOR_DISABLED
     * @see #LIMIT_BEHAVIOR_BILLED
     * @see #LIMIT_BEHAVIOR_THROTTLED
     */
    private final int mDataLimitBehavior;

    /**
     * A snapshot of the mobile data usage, in bytes. This value is paired with
     * {@link #getDataUsageTime()} to indicate when the measurement was taken.
     * <p>
     * The usage reported here corresponds to the data consumption within the current billing cycle,
     * as defined by {@link #getCycleRule()}. If the plan specifies different limits for various
     * network types, this value represents the total usage across all applicable networks.
     * <p>
     * May be {@link #BYTES_UNKNOWN} if the carrier has not provided this information.
     *
     * @see #getDataUsageTime()
     * @see #getDataLimitBytes()
     */
    private final long mDataUsageBytes;

    /**
     * The time at which the data usage was measured, in milliseconds since the Unix epoch.
     * <p>
     * This timestamp indicates when the {@link #getDataUsageBytes()} value was recorded.
     * It allows applications to determine how recent the usage information is.
     * <p>
     * This value can be {@link #TIME_UNKNOWN} if the data usage time is not available.
     *
     * @see #getDataUsageTime()
     * @see System#currentTimeMillis()
     */
    private final long mDataUsageTime;

    /**
     * The network types that this plan applies to.
     * <p>
     * An empty array indicates that this plan is not valid on any network.
     * <p>
     * If this is not specified, this plan is considered a "default" plan, which applies to any
     * network type that is not explicitly covered by another plan. Each subscription can have
     * at most one default plan.
     * <p>
     * When selecting a plan, the system prioritizes plans that explicitly match the current
     * network type over the default plan.
     */
    @NonNull
    @NetworkType
    private final int[] mNetworkTypes;

    /**
     * The status of this subscription plan.
     * <p>
     * This indicates the current state of the subscription, such as whether it is active,
     * inactive, in a trial period, or suspended. The status provides context for the plan's
     * availability and can be used to inform the user about their subscription's lifecycle.
     * <p>
     * The value will be one of the {@code SUBSCRIPTION_STATUS_*} constants.
     *
     * @see #getSubscriptionStatus()
     * @see #SUBSCRIPTION_STATUS_UNKNOWN
     * @see #SUBSCRIPTION_STATUS_ACTIVE
     * @see #SUBSCRIPTION_STATUS_INACTIVE
     * @see #SUBSCRIPTION_STATUS_TRIAL
     * @see #SUBSCRIPTION_STATUS_SUSPENDED
     */
    @SubscriptionStatus
    private final int mSubscriptionStatus;

    /**
     * Creates a new SubscriptionPlan by initializing its fields from a {@link Builder}.
     * This constructor is private and is only intended to be called from the
     * {@link Builder#build()} method, which ensures that all plan properties are
     * set correctly.
     *
     * @param builder The builder object containing all the properties for this plan.
     */
    private SubscriptionPlan(Builder builder) {
        this.mCycleRule = Objects.requireNonNull(builder.mCycleRule);
        this.mTitle = builder.mTitle;
        this.mSummary = builder.mSummary;
        this.mDataLimitBytes = builder.mDataLimitBytes;
        this.mDataLimitBehavior = builder.mDataLimitBehavior;
        this.mDataUsageBytes = builder.mDataUsageBytes;
        this.mDataUsageTime = builder.mDataUsageTime;
        this.mNetworkTypes = builder.mNetworkTypes;
        this.mSubscriptionStatus = builder.mSubscriptionStatus;
    }

    /**
     * Reconstruct a SubscriptionPlan from a Parcel.
     *
     * @param in The Parcel containing the flattened SubscriptionPlan data.
     */
    private SubscriptionPlan(Parcel in) {
        mCycleRule = in.readParcelable(RecurrenceRule.class.getClassLoader(), RecurrenceRule.class);
        mTitle = in.readCharSequence();
        mSummary = in.readCharSequence();
        mDataLimitBytes = in.readLong();
        mDataLimitBehavior = in.readInt();
        mDataUsageBytes = in.readLong();
        mDataUsageTime = in.readLong();
        mNetworkTypes = in.createIntArray();
        mSubscriptionStatus = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mCycleRule, flags);
        dest.writeCharSequence(mTitle);
        dest.writeCharSequence(mSummary);
        dest.writeLong(mDataLimitBytes);
        dest.writeInt(mDataLimitBehavior);
        dest.writeLong(mDataUsageBytes);
        dest.writeLong(mDataUsageTime);
        dest.writeIntArray(mNetworkTypes);
        dest.writeInt(mSubscriptionStatus);
    }

    @Override
    public String toString() {
        return "SubscriptionPlan{"
                + "cycleRule=" + mCycleRule
                + " title=" + mTitle
                + " summary=" + mSummary
                + " dataLimitBytes=" + mDataLimitBytes
                + " dataLimitBehavior=" + mDataLimitBehavior
                + " dataUsageBytes=" + mDataUsageBytes
                + " dataUsageTime=" + mDataUsageTime
                + " networkTypes=" + Arrays.toString(mNetworkTypes)
                + " subscriptionStatus=" + mSubscriptionStatus
                + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCycleRule, mTitle, mSummary, mDataLimitBytes, mDataLimitBehavior,
                mDataUsageBytes, mDataUsageTime, Arrays.hashCode(mNetworkTypes),
                mSubscriptionStatus);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof SubscriptionPlan other) {
            return Objects.equals(mCycleRule, other.mCycleRule)
                    && Objects.equals(mTitle, other.mTitle)
                    && Objects.equals(mSummary, other.mSummary)
                    && mDataLimitBytes == other.mDataLimitBytes
                    && mDataLimitBehavior == other.mDataLimitBehavior
                    && mDataUsageBytes == other.mDataUsageBytes
                    && mDataUsageTime == other.mDataUsageTime
                    && Arrays.equals(mNetworkTypes, other.mNetworkTypes)
                    && mSubscriptionStatus == other.mSubscriptionStatus;
        }
        return false;
    }

    @NonNull
    public static final Parcelable.Creator<SubscriptionPlan> CREATOR = new Parcelable.Creator<>() {
        @Override
        public SubscriptionPlan createFromParcel(Parcel source) {
            return new SubscriptionPlan(source);
        }

        @Override
        public SubscriptionPlan[] newArray(int size) {
            return new SubscriptionPlan[size];
        }
    };

    /**
     * Get the recurrence rule for the billing cycle of this plan.
     *
     * @see #cycleIterator()
     * @see #getPlanEndDate()
     *
     * @hide
     */
    @NonNull
    public RecurrenceRule getCycleRule() {
        return mCycleRule;
    }

    /**
     * Returns the end date of this subscription plan.
     * <p>
     * This indicates the specific date and time when the plan is no longer valid. For recurring
     * plans that do not have a defined end, this will be {@code null}.
     *
     * @return The plan's end date as a {@link ZonedDateTime}, or {@code null} if unavailable.
     */
    @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    @Nullable
    public ZonedDateTime getPlanEndDate() {
        // ZonedDateTime is immutable, so no need to create a defensive copy.
        return mCycleRule.end;
    }

    /**
     * Returns a user-visible title for this plan.
     * <p>
     * This title is provided by the carrier to identify the subscription plan, for example,
     * "Unlimited+" or "Family plan". It is intended for display to the user.
     *
     * @return The title of the plan, or {@code null} if not specified.
     */
    @Nullable
    public CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Returns a brief, human-readable summary of the subscription plan.
     * <p>
     * This could include details about the plan's features, such as "10GB of high-speed data"
     * or "Unlimited talk and text". This summary is intended for display to the user.
     *
     * @return A short, user-friendly summary of the plan, or {@code null} if not specified.
     */
    @Nullable
    public CharSequence getSummary() {
        return mSummary;
    }

    /**
     * Returns the data limit for the plan.
     * <p>
     * This is the usage threshold, in bytes, at which data access changes according to
     * the behavior defined by {@link #getDataLimitBehavior()}. For example, once data usage
     * reaches this limit, the connection might be throttled or disabled.
     *
     * @return The data limit in bytes. This may be {@link #BYTES_UNKNOWN} if the limit is not
     * available, or {@link #BYTES_UNLIMITED} if there is no limit.
     */
    @BytesLong
    public long getDataLimitBytes() {
        return mDataLimitBytes;
    }

    /**
     * Returns the behavior of data access when usage reaches the limit defined by
     * {@link #getDataLimitBytes()}.
     * <p>
     * This defines what happens to the user's data connection once they have consumed the amount
     * of data specified in the data limit. For example, the carrier might throttle the connection
     * to a slower speed, disable it entirely, or start billing for overage.
     *
     * @return The data limit behavior, which will be one of {@link #LIMIT_BEHAVIOR_UNKNOWN},
     * {@link #LIMIT_BEHAVIOR_DISABLED}, {@link #LIMIT_BEHAVIOR_BILLED}, or
     * {@link #LIMIT_BEHAVIOR_THROTTLED}.
     */
    @LimitBehavior
    public int getDataLimitBehavior() {
        return mDataLimitBehavior;
    }

    /**
     * Returns a snapshot of the mobile data usage, in bytes, as of the time reported by
     * {@link #getDataUsageTime()}.
     * <p>
     * The usage reported here corresponds to the data consumption within the current billing
     * cycle defined by {@link #getCycleRule()}. If the plan specifies different limits for
     * various network types, this value represents the total usage across all applicable
     * networks.
     *
     * @return The data usage in bytes, or {@link #BYTES_UNKNOWN} if unavailable.
     */
    @BytesLong
    public long getDataUsageBytes() {
        return mDataUsageBytes;
    }

    /**
     * Returns the time at which {@link #getDataUsageBytes()} was measured, in milliseconds
     * since the Unix epoch.
     * <p>
     * This timestamp indicates how recent the data usage information is. It allows applications
     * to decide whether the usage value is current enough for their needs.
     *
     * @return The time of the data usage snapshot as a Unix epoch timestamp, or
     * {@link #TIME_UNKNOWN} if unavailable.
     *
     * @see System#currentTimeMillis()
     */
    @CurrentTimeMillisLong
    public long getDataUsageTime() {
        return mDataUsageTime;
    }

    /**
     * Returns the network types that this plan applies to.
     * <p>
     * A "default" plan, which applies to any network type not explicitly covered by another
     * plan, will include all possible network types in the returned array. Each subscription
     * should have at most one default plan.
     * <p>
     * A RAT-specific plan will return an array containing only the network types it applies to,
     * for example, {@link TelephonyManager#NETWORK_TYPE_LTE}.
     *
     * @return A new copy of the array of network types this plan applies to. The values will be
     * constants from {@link TelephonyManager}, such as {@link TelephonyManager#NETWORK_TYPE_LTE}.
     *
     * @see TelephonyManager#getAllNetworkTypes()
     */
    @NonNull
    @NetworkType
    public int[] getNetworkTypes() {
        return Arrays.copyOf(mNetworkTypes, mNetworkTypes.length);
    }

    /**
     * Returns an iterator that provides the data usage billing cycles for this plan,
     * based on its recurrence rule.
     * <p>
     * The iterator starts from the cycle that is currently active and walks backwards through time.
     * Each cycle is represented as a {@link Range} of {@link ZonedDateTime} objects, indicating the
     * start and end of that period.
     *
     * @return An iterator for the plan's billing cycles.
     */
    public Iterator<Range<ZonedDateTime>> cycleIterator() {
        return mCycleRule.cycleIterator();
    }

    /**
     * Returns the status of the subscription plan.
     * <p>
     * This indicates the current state of the subscription, such as whether it is active,
     * suspended, or in a trial period. This status provides context on the plan's
     * availability and can be used to inform the user about their subscription's lifecycle.
     *
     * @return The subscription status, which is one of the {@code SUBSCRIPTION_STATUS_*}
     * constants, or {@link #SUBSCRIPTION_STATUS_UNKNOWN} if not available.
     *
     * @see #SUBSCRIPTION_STATUS_UNKNOWN
     * @see #SUBSCRIPTION_STATUS_ACTIVE
     * @see #SUBSCRIPTION_STATUS_INACTIVE
     * @see #SUBSCRIPTION_STATUS_TRIAL
     * @see #SUBSCRIPTION_STATUS_SUSPENDED
     */
    @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
    @SubscriptionStatus
    public int getSubscriptionStatus() {
        return mSubscriptionStatus;
    }

    /**
     * Builder for a {@link SubscriptionPlan}.
     */
    public static class Builder {
        /**
         * The billing cycle of the subscription plan. It defines a series of time intervals
         * over which usage is measured and billed.
         * <p>
         * For example, a monthly plan might have a cycle that starts on the 5th of each month and
         * ends on the 4th of the following month.
         *
         * @see #cycleIterator()
         */
        private final RecurrenceRule mCycleRule;

        /**
         * A user-visible title for this plan. For example, "Unlimited+" or "Family plan".
         *
         * @see #getTitle()
         */
        @Nullable
        private CharSequence mTitle = null;

        /**
         * A brief, user-visible summary of the plan.
         * <p>
         * This could include details about the plan's features, such as "10GB of high-speed data"
         * or "Unlimited talk and text". This summary is intended for display to the user.
         *
         * @see #getSummary()
         */
        @Nullable
        private CharSequence mSummary = null;

        /**
         * The data limit for this billing cycle, in bytes. When the user's data usage reaches this
         * limit, the behavior of data access will change as defined by
         * {@link #getDataLimitBehavior()}.
         * <p>
         * This value can be {@link #BYTES_UNKNOWN} if the data limit is unknown, or
         * {@link #BYTES_UNLIMITED} if there is no data limit.
         *
         * @see #getDataLimitBytes()
         * @see #getDataLimitBehavior()
         */
        private long mDataLimitBytes = BYTES_UNKNOWN;

        /**
         * The behavior of data access when usage reaches the data limit defined by
         * {@link #mDataLimitBytes}. This defines what happens to the user's data connection
         * once they have consumed a certain amount of data.
         * <p>
         * For example, the carrier might throttle the connection to a slower speed, disable it
         * entirely, or start billing for overage. The value will be one of the
         * {@code LIMIT_BEHAVIOR_*} constants.
         *
         * @see #LIMIT_BEHAVIOR_UNKNOWN
         * @see #LIMIT_BEHAVIOR_DISABLED
         * @see #LIMIT_BEHAVIOR_BILLED
         * @see #LIMIT_BEHAVIOR_THROTTLED
         */
        @LimitBehavior
        private int mDataLimitBehavior = LIMIT_BEHAVIOR_UNKNOWN;

        /**
         * A snapshot of the mobile data usage, in bytes. This value is paired with
         * {@link #mDataUsageTime} to indicate when the measurement was taken.
         * <p>
         * The usage reported here corresponds to the data consumption within the current billing
         * cycle, as defined by {@link #mCycleRule}.
         * <p>
         * May be {@link #BYTES_UNKNOWN} if the carrier has not provided this information.
         *
         * @see #getDataUsageBytes()
         */
        private long mDataUsageBytes = BYTES_UNKNOWN;

        /**
         * The time at which the data usage was measured, in milliseconds since the Unix epoch.
         * <p>
         * This timestamp indicates when the {@link #mDataUsageBytes} value was recorded.
         * It allows applications to determine how recent the usage information is.
         * <p>
         * This value can be {@link #TIME_UNKNOWN} if the data usage time is not available.
         *
         * @see #getDataUsageTime()
         * @see System#currentTimeMillis()
         */
        @CurrentTimeMillisLong
        private long mDataUsageTime = TIME_UNKNOWN;

        /**
         * The network types that this plan applies to.
         * <p>
         * A "default" plan, which applies to any network type not explicitly covered by another
         * plan, will include all possible network types in the returned array. Each subscription
         * should have at most one default plan.
         * <p>
         * A RAT-specific plan will return an array containing only the network types it applies
         * to, for example, {@link TelephonyManager#NETWORK_TYPE_LTE}.
         *
         * @see #setNetworkTypes(int[])
         * @see #resetNetworkTypes()
         * @see TelephonyManager#getAllNetworkTypes()
         */
        @NetworkType
        private int[] mNetworkTypes = TelephonyManager.getAllNetworkTypes();

        /**
         * The status of this subscription plan.
         * <p>
         * This indicates the current state of the subscription, such as whether it is active,
         * inactive, in a trial period, or suspended. The status provides context for the plan's
         * availability and can be used to inform the user about their subscription's lifecycle.
         * <p>
         * The value will be one of the {@code SUBSCRIPTION_STATUS_*} constants. Defaults to
         * {@link #SUBSCRIPTION_STATUS_UNKNOWN}.
         *
         * @see #getSubscriptionStatus()
         * @see #SUBSCRIPTION_STATUS_UNKNOWN
         * @see #SUBSCRIPTION_STATUS_ACTIVE
         * @see #SUBSCRIPTION_STATUS_INACTIVE
         * @see #SUBSCRIPTION_STATUS_TRIAL
         * @see #SUBSCRIPTION_STATUS_SUSPENDED
         */
        @SubscriptionStatus
        private int mSubscriptionStatus = SUBSCRIPTION_STATUS_UNKNOWN;

        /**
         * Create a {@link Builder} to build a {@link SubscriptionPlan}.
         *
         * @param start The start of the recurrence.
         * @param end The end of the recurrence. It is exclusive. Can be {@code null} if the
         * recurrence is infinite.
         * @param period The period of the recurrence. Can be {@code null} if this is a
         * non-recurring plan.
         *
         * @hide
         */
        public Builder(ZonedDateTime start, ZonedDateTime end, Period period) {
            this.mCycleRule = new RecurrenceRule(start, end, period);
        }

        /**
         * Start defining a {@link SubscriptionPlan} that covers a very specific window of time, and
         * never automatically recurs.
         *
         * @param start The exact time at which the plan starts.
         * @param end The exact time at which the plan ends.
         */
        public static Builder createNonrecurring(ZonedDateTime start, ZonedDateTime end) {
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException(
                        "End " + end + " isn't after start " + start);
            }
            return new Builder(start, end, null);
        }

        /**
         * Start defining a {@link SubscriptionPlan} that starts at a specific time, and
         * automatically recurs after each specific period of time, repeating indefinitely.
         * <p>
         * When the given period is set to exactly one month, the plan will always recur on the day
         * of the month defined by {@link ZonedDateTime#getDayOfMonth()}. When a particular month
         * ends before this day, the plan will recur on the last possible instant of that month.
         *
         * @param start The exact time at which the plan starts.
         * @param period The period after which the plan automatically recurs.
         */
        public static Builder createRecurring(ZonedDateTime start, Period period) {
            if (period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("Period " + period + " must be positive");
            }
            return new Builder(start, null, period);
        }

        /** @hide */
        @SystemApi
        @Deprecated
        public static Builder createRecurringMonthly(ZonedDateTime start) {
            return new Builder(start, null, Period.ofMonths(1));
        }

        /** @hide */
        @SystemApi
        @Deprecated
        public static Builder createRecurringWeekly(ZonedDateTime start) {
            return new Builder(start, null, Period.ofDays(7));
        }

        /** @hide */
        @SystemApi
        @Deprecated
        public static Builder createRecurringDaily(ZonedDateTime start) {
            return new Builder(start, null, Period.ofDays(1));
        }

        public SubscriptionPlan build() {
            return new SubscriptionPlan(this);
        }

        /**
         * Sets a user-visible title for this plan.
         * <p>
         * This title is provided by the carrier to identify the subscription plan, for example,
         * "Unlimited+" or "Family plan". It is intended for display to the user.
         *
         * @param title The title of the plan.
         * @return The same {@link Builder} instance to continue building the plan.
         */
        public Builder setTitle(@Nullable CharSequence title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets a brief, human-readable summary of the subscription plan.
         * <p>
         * This could include details about the plan's features, such as "10GB of high-speed data"
         * or "Unlimited talk and text". This summary is intended for display to the user.
         *
         * @param summary A short, user-friendly summary of the plan, or {@code null} to clear it.
         */
        public Builder setSummary(@Nullable CharSequence summary) {
            mSummary = summary;
            return this;
        }

        /**
         * Set the usage threshold at which data access changes.
         *
         * @param dataLimitBytes the usage threshold at which data access changes
         * @param dataLimitBehavior the behavior of data access when usage reaches the threshold
         */
        public Builder setDataLimit(@BytesLong long dataLimitBytes,
                @LimitBehavior int dataLimitBehavior) {
            if (dataLimitBytes < 0 && dataLimitBytes != BYTES_UNKNOWN) {
                throw new IllegalArgumentException("Limit bytes must be positive or unknown");
            }
            if (dataLimitBehavior < LIMIT_BEHAVIOR_UNKNOWN
                    || dataLimitBehavior > LIMIT_BEHAVIOR_THROTTLED) {
                throw new IllegalArgumentException("Limit behavior must be a valid value");
            }
            mDataLimitBytes = dataLimitBytes;
            mDataLimitBehavior = dataLimitBehavior;
            return this;
        }

        /**
         * Set a snapshot of currently known mobile data usage.
         *
         * @param dataUsageBytes the currently known mobile data usage
         * @param dataUsageTime the time at which this snapshot was valid
         */
        public Builder setDataUsage(@BytesLong long dataUsageBytes,
                @CurrentTimeMillisLong long dataUsageTime) {
            if (dataUsageBytes < 0 && dataUsageBytes != BYTES_UNKNOWN) {
                throw new IllegalArgumentException("Usage bytes must be positive or unknown");
            }
            if (dataUsageTime < 0 && dataUsageTime != TIME_UNKNOWN) {
                throw new IllegalArgumentException("Usage time must be positive or unknown");
            }
            mDataUsageBytes = dataUsageBytes;
            mDataUsageTime = dataUsageTime;
            return this;
        }

        /**
         * Set the network types this SubscriptionPlan applies to. By default the plan will apply
         * to all network types. An empty array means this plan applies to no network types.
         *
         * @param networkTypes an array of all network types that apply to this plan.
         *
         * @see TelephonyManager#getAllNetworkTypes()
         */
        @NonNull
        public Builder setNetworkTypes(@NonNull @NetworkType int[] networkTypes) {
            mNetworkTypes = Arrays.copyOf(networkTypes, networkTypes.length);
            return this;
        }

        /**
         * Reset any network types that were set with {@link #setNetworkTypes(int[])}.
         * This will make the SubscriptionPlan apply to all network types.
         */
        @NonNull
        public Builder resetNetworkTypes() {
            mNetworkTypes = Arrays.copyOf(TelephonyManager.getAllNetworkTypes(),
                    TelephonyManager.getAllNetworkTypes().length);
            return this;
        }

        /**
         * Set the subscription status.
         * <p>
         * This indicates the current state of the subscription, such as whether it is active,
         * suspended, or in a trial period. This status provides context on the plan's
         * availability and can be used to inform the user about their subscription's lifecycle.
         *
         * @param subscriptionStatus the current subscription status
         */
        @FlaggedApi(Flags.FLAG_SUBSCRIPTION_PLAN_ALLOW_STATUS_AND_END_DATE)
        @NonNull
        public Builder setSubscriptionStatus(@SubscriptionStatus int subscriptionStatus) {
            if (subscriptionStatus < SUBSCRIPTION_STATUS_UNKNOWN
                    || subscriptionStatus > SUBSCRIPTION_STATUS_SUSPENDED) {
                throw new IllegalArgumentException(
                        "Subscription status must be defined with a valid value");
            }
            mSubscriptionStatus = subscriptionStatus;
            return this;
        }
    }
}
