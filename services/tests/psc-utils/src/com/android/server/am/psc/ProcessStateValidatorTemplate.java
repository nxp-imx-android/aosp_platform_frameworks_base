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

/**
 * A non-functional template class for {@link ProcessStateValidator}.
 * This class does not do anything except hold expectations of a ProcessStateValidator.
 * One or more of these templates can be used with {@link ProcessStateValidator#create} to create
 * a functional validator.
 */
public class ProcessStateValidatorTemplate {
    protected Integer mExpectedProcStateLowerBound = null;
    protected Integer mExpectedProcStateUpperBound = null;
    protected Integer mExpectedOomAdjScoreUpperBound = null;
    protected Integer mExpectedOomAdjScoreLowerBound = null;
    protected Boolean mExpectedFreezability = null;
}
