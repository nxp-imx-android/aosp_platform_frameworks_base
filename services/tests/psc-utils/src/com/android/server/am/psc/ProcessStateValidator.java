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

package com.android.server.am.psc;

import static com.android.server.am.psc.OomAdjuster.ALL_CPU_TIME_CAPABILITIES;

import static org.junit.Assert.fail;

import android.annotation.Nullable;
import android.app.ActivityManager.ProcessState;

/**
 * ProcessStateValidator is a utility for validating {@link ProcessRecordInternal} importance.
 */
public class ProcessStateValidator extends ProcessStateValidatorTemplate {
    /**
     * Assert.fail() will be called if the supplied {@code proc} does not meet the set
     * expectations of this validator.
     */
    public void validate(ProcessRecordInternal proc) {
        final int actualProcState = proc.getCurProcState();
        final int actualOomAdjScore = proc.getSetAdj();
        final boolean actualFreezability =
                (proc.getSetCapability() & ALL_CPU_TIME_CAPABILITIES) == 0;

        if (checkState(actualProcState, actualOomAdjScore, actualFreezability)) {
            // Passed
            return;
        }

        // Fail with a dump of the state.
        StringBuilder str = new StringBuilder();
        str.append("Process State Validation failed for ");
        str.append(proc);
        str.append("\n");
        str.append("ProcState - actual:");
        str.append(actualProcState);
        if (mExpectedProcStateUpperBound != null && mExpectedProcStateLowerBound != null) {
            str.append(" expected:");
            if (mExpectedProcStateUpperBound.equals(mExpectedProcStateLowerBound)) {
                str.append(mExpectedProcStateUpperBound);
            } else {
                str.append("[");
                str.append(mExpectedProcStateLowerBound);
                str.append(",");
                str.append(mExpectedProcStateUpperBound);
                str.append("]");
            }
        } else if (mExpectedProcStateUpperBound != null) {
            str.append(" expected:<=");
            str.append(mExpectedProcStateUpperBound);
        } else if (mExpectedProcStateLowerBound != null) {
            str.append(" expected:>=");
            str.append(mExpectedProcStateLowerBound);
        }
        str.append("\n");

        str.append("OomAdjScore - actual:");
        str.append(actualOomAdjScore);
        if (mExpectedOomAdjScoreUpperBound != null && mExpectedOomAdjScoreLowerBound != null) {
            str.append(" expected:");
            if (mExpectedOomAdjScoreUpperBound.equals(mExpectedOomAdjScoreLowerBound)) {
                str.append(mExpectedOomAdjScoreUpperBound);
            } else {
                str.append("[");
                str.append(mExpectedOomAdjScoreLowerBound);
                str.append(",");
                str.append(mExpectedOomAdjScoreUpperBound);
                str.append("]");
            }
        } else if (mExpectedOomAdjScoreUpperBound != null) {
            str.append(" expected:<=");
            str.append(mExpectedOomAdjScoreUpperBound);
        } else if (mExpectedOomAdjScoreLowerBound != null) {
            str.append(" expected:>=");
            str.append(mExpectedOomAdjScoreLowerBound);
        }
        str.append("\n");

        str.append("Freezability - actual:");
        str.append(actualFreezability);
        if (mExpectedFreezability != null) {
            str.append(" expected:");
            str.append(mExpectedFreezability);
        }
        str.append("\n");

        fail(str.toString());
    }

    private boolean checkState(int actualProcState, int actualOomAdjScore,
            boolean actualFreezability) {
        if (mExpectedProcStateUpperBound != null
                && mExpectedProcStateUpperBound < actualProcState) {
            return false;
        }
        if (mExpectedProcStateLowerBound != null
                && mExpectedProcStateLowerBound > actualProcState) {
            return false;
        }
        if (mExpectedOomAdjScoreUpperBound != null
                && mExpectedOomAdjScoreUpperBound < actualOomAdjScore) {
            return false;
        }
        if (mExpectedOomAdjScoreLowerBound != null
                && mExpectedOomAdjScoreLowerBound > actualOomAdjScore) {
            return false;
        }
        if (mExpectedFreezability != null && mExpectedFreezability != actualFreezability) {
            return false;
        }
        return true;
    }

    /**
     * Create a validator that satisfies all expectations from the provided validators.
     * NOTE: it is possible to create an overconstrained validator that is impossible to pass.
     */
    public static ProcessStateValidator create(ProcessStateValidatorTemplate... validators) {
        final ProcessStateValidator merged = new ProcessStateValidator();
        for (ProcessStateValidatorTemplate validator : validators) {
            merged.mergeProcStateLowerBound(validator.mExpectedProcStateLowerBound);
            merged.mergeProcStateUpperBound(validator.mExpectedProcStateUpperBound);
            merged.mergeOomAdjScoreLowerBound(validator.mExpectedOomAdjScoreLowerBound);
            merged.mergeOomAdjScoreUpperBound(validator.mExpectedOomAdjScoreUpperBound);
            merged.mergeFreezeabilityExpectations(validator.mExpectedFreezability);
        }
        return merged;
    }

    /**
     * If any proc state of oom adj score bounds are unset, clamp them to the corresponding bound.
     */
    public ProcessStateValidator clamp() {
        if (mExpectedProcStateLowerBound == null) {
            mExpectedProcStateLowerBound = mExpectedProcStateUpperBound;
        }
        if (mExpectedProcStateUpperBound == null) {
            mExpectedProcStateUpperBound = mExpectedProcStateLowerBound;
        }
        if (mExpectedOomAdjScoreLowerBound == null) {
            mExpectedOomAdjScoreLowerBound = mExpectedOomAdjScoreUpperBound;
        }
        if (mExpectedOomAdjScoreUpperBound == null) {
            mExpectedOomAdjScoreUpperBound = mExpectedOomAdjScoreLowerBound;
        }
        return this;
    }

    /**
     * Set the expected {@link ProcessState} for the validated process.
     */
    public ProcessStateValidator expectedProcState(@ProcessState int state) {
        expectedProcStateAtLeast(state);
        expectedProcStateAtMost(state);
        return this;
    }

    /**
     * Set the lower bound {@link ProcessState} for the validated process.
     */
    public ProcessStateValidator expectedProcStateAtLeast(@ProcessState int state) {
        mExpectedProcStateLowerBound = state;
        return this;
    }

    private void mergeProcStateLowerBound(@Nullable @ProcessState Integer state) {
        if (state == null) return;
        if (mExpectedProcStateLowerBound == null) {
            mExpectedProcStateLowerBound = state;
            return;
        }
        // Choose the more constrained bound.
        if (mExpectedProcStateLowerBound < state) {
            mExpectedProcStateLowerBound = state;
        }
    }

    /**
     * Set the upper bound {@link ProcessState} for the validated process.
     */
    public ProcessStateValidator expectedProcStateAtMost(@ProcessState int state) {
        mExpectedProcStateUpperBound = state;
        return this;
    }

    private void mergeProcStateUpperBound(@Nullable @ProcessState Integer state) {
        if (state == null) return;
        if (mExpectedProcStateUpperBound == null) {
            mExpectedProcStateUpperBound = state;
            return;
        }
        // Choose the more constrained bound.
        if (mExpectedProcStateUpperBound > state) {
            mExpectedProcStateUpperBound = state;
        }
    }

    /**
     * Set the expected oomAdj score for the validated process.
     */
    public ProcessStateValidator expectedOomAdjScore(int value) {
        // Just set the upper and lower bounds to the value.
        expectedOomAdjScoreAtLeast(value);
        expectedOomAdjScoreAtMost(value);
        return this;
    }

    /**
     * Set the lower bound oomAdj score for the validated process.
     */
    public ProcessStateValidator expectedOomAdjScoreAtLeast(int value) {
        mExpectedOomAdjScoreLowerBound = value;
        return this;
    }

    private void mergeOomAdjScoreLowerBound(@Nullable Integer value) {
        if (value == null) return;
        if (mExpectedOomAdjScoreLowerBound == null) {
            mExpectedOomAdjScoreLowerBound = value;
            return;
        }
        // Choose the more constrained bound.
        if (mExpectedOomAdjScoreLowerBound < value) {
            mExpectedOomAdjScoreLowerBound = value;
        }
    }

    /**
     * Set the upper bound oomAdj score for the validated process.
     */
    public ProcessStateValidator expectedOomAdjScoreAtMost(int value) {
        mExpectedOomAdjScoreUpperBound = value;
        return this;
    }

    private void mergeOomAdjScoreUpperBound(@Nullable Integer value) {
        if (value == null) return;
        if (mExpectedOomAdjScoreUpperBound == null) {
            mExpectedOomAdjScoreUpperBound = value;
            return;
        }
        // Choose the more constrained bound.
        if (mExpectedOomAdjScoreUpperBound > value) {
            mExpectedOomAdjScoreUpperBound = value;
        }
    }

    /**
     * Set the expected freezability state for the validated process.
     */
    public ProcessStateValidator expectedFreezability(boolean freezable) {
        mExpectedFreezability = freezable;
        return this;
    }

    private void mergeFreezeabilityExpectations(@Nullable Boolean freezable) {
        if (freezable == null) return;
        if (mExpectedFreezability == null) {
            mExpectedFreezability = freezable;
        }

        // A process should only be considered freezable if all expectations are considered
        // freezable.
        mExpectedFreezability = mExpectedFreezability && freezable;
    }
}
