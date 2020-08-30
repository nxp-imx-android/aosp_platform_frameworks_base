/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.alarm;

import static com.android.server.alarm.AlarmManagerService.DEBUG_BATCH;
import static com.android.server.alarm.AlarmManagerService.TAG;
import static com.android.server.alarm.AlarmManagerService.clampPositive;
import static com.android.server.alarm.AlarmManagerService.dumpAlarmList;
import static com.android.server.alarm.AlarmManagerService.isTimeTickAlarm;

import android.app.AlarmManager;
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;

import com.android.internal.util.StatLogger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Batching implementation of an Alarm Store.
 * This keeps the alarms in batches, which are sorted on the start time of their delivery window.
 */
public class BatchingAlarmStore implements AlarmStore {

    private ArrayList<Batch> mAlarmBatches = new ArrayList<>();
    private int mSize;
    private AlarmClockRemovalListener mAlarmClockRemovalListener;

    interface Stats {
        int REBATCH_ALL_ALARMS = 1;
    }

    final StatLogger mStatLogger = new StatLogger("Alarm store stats", new String[]{
            "REBATCH_ALL_ALARMS",
    });

    private static final Comparator<Batch> sBatchOrder = (b1, b2) -> {
        long when1 = b1.mStart;
        long when2 = b2.mStart;
        if (when1 > when2) {
            return 1;
        }
        if (when1 < when2) {
            return -1;
        }
        return 0;
    };

    private static final Comparator<Alarm> sIncreasingTimeOrder = (a1, a2) -> {
        long when1 = a1.whenElapsed;
        long when2 = a2.whenElapsed;
        if (when1 > when2) {
            return 1;
        }
        if (when1 < when2) {
            return -1;
        }
        return 0;
    };

    BatchingAlarmStore(AlarmClockRemovalListener listener) {
        mAlarmClockRemovalListener = listener;
    }

    @Override
    public void add(Alarm a) {
        insertAndBatchAlarm(a);
        mSize++;
    }

    @Override
    public ArrayList<Alarm> remove(Predicate<Alarm> whichAlarms) {
        final ArrayList<Alarm> removed = new ArrayList<>();
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            final Batch b = mAlarmBatches.get(i);
            removed.addAll(b.remove(whichAlarms));
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }
        if (!removed.isEmpty()) {
            mSize -= removed.size();
            rebatchAllAlarms();
        }
        return removed;
    }

    private void rebatchAllAlarms() {
        final long start = mStatLogger.getTime();
        final ArrayList<Batch> oldBatches = (ArrayList<Batch>) mAlarmBatches.clone();
        mAlarmBatches.clear();
        for (final Batch batch : oldBatches) {
            for (int i = 0; i < batch.size(); i++) {
                insertAndBatchAlarm(batch.get(i));
            }
        }
        mStatLogger.logDurationStat(Stats.REBATCH_ALL_ALARMS, start);
    }

    @Override
    public int size() {
        return mSize;
    }

    @Override
    public long getNextWakeupDeliveryTime() {
        for (Batch b : mAlarmBatches) {
            if (b.hasWakeups()) {
                return b.mStart;
            }
        }
        return 0;
    }

    @Override
    public long getNextDeliveryTime() {
        if (mAlarmBatches.size() > 0) {
            return mAlarmBatches.get(0).mStart;
        }
        return 0;
    }

    @Override
    public ArrayList<Alarm> removePendingAlarms(long nowElapsed) {
        final ArrayList<Alarm> removedAlarms = new ArrayList<>();
        while (mAlarmBatches.size() > 0) {
            final Batch batch = mAlarmBatches.get(0);
            if (batch.mStart > nowElapsed) {
                break;
            }
            mAlarmBatches.remove(0);
            for (int i = 0; i < batch.size(); i++) {
                removedAlarms.add(batch.get(i));
            }
        }
        mSize -= removedAlarms.size();
        return removedAlarms;
    }

    @Override
    public boolean recalculateAlarmDeliveries(AlarmDeliveryCalculator deliveryCalculator) {
        boolean changed = false;
        for (final Batch b : mAlarmBatches) {
            for (int i = 0; i < b.size(); i++) {
                changed |= deliveryCalculator.updateAlarmDelivery(b.get(i));
            }
        }
        if (changed) {
            rebatchAllAlarms();
        }
        return changed;
    }

    @Override
    public ArrayList<Alarm> asList() {
        final ArrayList<Alarm> allAlarms = new ArrayList<>();
        for (final Batch batch : mAlarmBatches) {
            for (int i = 0; i < batch.size(); i++) {
                allAlarms.add(batch.get(i));
            }
        }
        return allAlarms;
    }

    @Override
    public void dump(IndentingPrintWriter ipw, long nowElapsed, SimpleDateFormat sdf) {
        ipw.print("Pending alarm batches: ");
        ipw.println(mAlarmBatches.size());
        for (Batch b : mAlarmBatches) {
            ipw.print(b);
            ipw.println(':');
            ipw.increaseIndent();
            dumpAlarmList(ipw, b.mAlarms, nowElapsed, sdf);
            ipw.decreaseIndent();
        }
        mStatLogger.dump(ipw);
    }

    @Override
    public void dumpProto(ProtoOutputStream pos, long nowElapsed) {
        for (Batch b : mAlarmBatches) {
            b.dumpDebug(pos, AlarmManagerServiceDumpProto.PENDING_ALARM_BATCHES, nowElapsed);
        }
    }

    private void insertAndBatchAlarm(Alarm alarm) {
        final int whichBatch = ((alarm.flags & AlarmManager.FLAG_STANDALONE) != 0) ? -1
                : attemptCoalesce(alarm.whenElapsed, alarm.maxWhenElapsed);

        if (whichBatch < 0) {
            addBatch(mAlarmBatches, new Batch(alarm));
        } else {
            final Batch batch = mAlarmBatches.get(whichBatch);
            if (batch.add(alarm)) {
                // The start time of this batch advanced, so batch ordering may
                // have just been broken.  Move it to where it now belongs.
                mAlarmBatches.remove(whichBatch);
                addBatch(mAlarmBatches, batch);
            }
        }
    }

    static void addBatch(ArrayList<Batch> list, Batch newBatch) {
        int index = Collections.binarySearch(list, newBatch, sBatchOrder);
        if (index < 0) {
            index = 0 - index - 1;
        }
        list.add(index, newBatch);
    }

    // Return the index of the matching batch, or -1 if none found.
    private int attemptCoalesce(long whenElapsed, long maxWhen) {
        final int n = mAlarmBatches.size();
        for (int i = 0; i < n; i++) {
            Batch b = mAlarmBatches.get(i);
            if ((b.mFlags & AlarmManager.FLAG_STANDALONE) == 0 && b.canHold(whenElapsed, maxWhen)) {
                return i;
            }
        }
        return -1;
    }

    final class Batch {
        long mStart;     // These endpoints are always in ELAPSED
        long mEnd;
        int mFlags;      // Flags for alarms, such as FLAG_STANDALONE.

        final ArrayList<Alarm> mAlarms = new ArrayList<>();

        Batch(Alarm seed) {
            mStart = seed.whenElapsed;
            mEnd = clampPositive(seed.maxWhenElapsed);
            mFlags = seed.flags;
            mAlarms.add(seed);
        }

        int size() {
            return mAlarms.size();
        }

        Alarm get(int index) {
            return mAlarms.get(index);
        }

        boolean canHold(long whenElapsed, long maxWhen) {
            return (mEnd >= whenElapsed) && (mStart <= maxWhen);
        }

        boolean add(Alarm alarm) {
            boolean newStart = false;
            // narrows the batch if necessary; presumes that canHold(alarm) is true
            int index = Collections.binarySearch(mAlarms, alarm, sIncreasingTimeOrder);
            if (index < 0) {
                index = 0 - index - 1;
            }
            mAlarms.add(index, alarm);
            if (DEBUG_BATCH) {
                Slog.v(TAG, "Adding " + alarm + " to " + this);
            }
            if (alarm.whenElapsed > mStart) {
                mStart = alarm.whenElapsed;
                newStart = true;
            }
            if (alarm.maxWhenElapsed < mEnd) {
                mEnd = alarm.maxWhenElapsed;
            }
            mFlags |= alarm.flags;

            if (DEBUG_BATCH) {
                Slog.v(TAG, "    => now " + this);
            }
            return newStart;
        }

        ArrayList<Alarm> remove(Predicate<Alarm> predicate) {
            final ArrayList<Alarm> removed = new ArrayList<>();
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            int newFlags = 0;
            for (int i = 0; i < mAlarms.size(); ) {
                Alarm alarm = mAlarms.get(i);
                if (predicate.test(alarm)) {
                    removed.add(mAlarms.remove(i));
                    if (alarm.alarmClock != null && mAlarmClockRemovalListener != null) {
                        mAlarmClockRemovalListener.onRemoved();
                    }
                    if (isTimeTickAlarm(alarm)) {
                        // This code path is not invoked when delivering alarms, only when removing
                        // alarms due to the caller cancelling it or getting uninstalled, etc.
                        Slog.wtf(TAG, "Removed TIME_TICK alarm");
                    }
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhenElapsed < newEnd) {
                        newEnd = alarm.maxWhenElapsed;
                    }
                    newFlags |= alarm.flags;
                    i++;
                }
            }
            if (!removed.isEmpty()) {
                // commit the new batch bounds
                mStart = newStart;
                mEnd = newEnd;
                mFlags = newFlags;
            }
            return removed;
        }

        boolean hasWakeups() {
            final int n = mAlarms.size();
            for (int i = 0; i < n; i++) {
                Alarm a = mAlarms.get(i);
                if (a.wakeup) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder(40);
            b.append("Batch{");
            b.append(Integer.toHexString(this.hashCode()));
            b.append(" num=");
            b.append(size());
            b.append(" start=");
            b.append(mStart);
            b.append(" end=");
            b.append(mEnd);
            if (mFlags != 0) {
                b.append(" flgs=0x");
                b.append(Integer.toHexString(mFlags));
            }
            b.append('}');
            return b.toString();
        }

        public void dumpDebug(ProtoOutputStream proto, long fieldId, long nowElapsed) {
            final long token = proto.start(fieldId);

            proto.write(BatchProto.START_REALTIME, mStart);
            proto.write(BatchProto.END_REALTIME, mEnd);
            proto.write(BatchProto.FLAGS, mFlags);
            for (Alarm a : mAlarms) {
                a.dumpDebug(proto, BatchProto.ALARMS, nowElapsed);
            }

            proto.end(token);
        }
    }

    @FunctionalInterface
    interface AlarmClockRemovalListener {
        void onRemoved();
    }
}
