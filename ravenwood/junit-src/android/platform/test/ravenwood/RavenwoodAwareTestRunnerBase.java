/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.platform.test.ravenwood;

import static com.android.ravenwood.common.RavenwoodInternalUtils.RAVENWOOD_VERBOSE_LOGGING;

import android.platform.test.annotations.internal.InnerRunner;
import android.util.Log;

import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;

import org.junit.internal.builders.IgnoredBuilder;
import org.junit.internal.builders.JUnit3Builder;
import org.junit.internal.builders.JUnit4Builder;
import org.junit.internal.builders.SuiteMethodBuilder;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.InvalidOrderingException;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Orderable;
import org.junit.runner.manipulation.Orderer;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.TestClass;

import java.util.Arrays;
import java.util.List;

abstract class RavenwoodAwareTestRunnerBase extends Runner implements Filterable, Orderable {
    public static final String TAG = RavenwoodInternalUtils.TAG;

    private final RunnerBuilder mBuilder = new RavenwoodRunnerBuilder();
    boolean mRealRunnerTakesRunnerBuilder = false;

    /**
     * Inspired from {@link org.junit.internal.builders.AllDefaultPossibilitiesBuilder}
     */
    class RavenwoodRunnerBuilder extends RunnerBuilder {
        private final List<RunnerBuilder> mRunnerBuilders = Arrays.asList(
                ignoredBuilder(),
                annotatedBuilder(),
                suiteMethodBuilder(),
                junit3Builder(),
                junit4Builder());

        @Override
        public Runner runnerForClass(Class<?> testClass) throws Throwable {
            for (RunnerBuilder each : mRunnerBuilders) {
                Runner runner = each.runnerForClass(testClass);
                if (runner != null) {
                    return runner;
                }
            }
            return null;
        }
    }

    class RavenwoodAnnotatedBuilder extends RunnerBuilder {
        @Override
        public Runner runnerForClass(Class<?> testClass) throws Exception {
            final InnerRunner innerRunnerAnnotation = testClass.getAnnotation(InnerRunner.class);
            if (innerRunnerAnnotation != null) {
                var clazz = innerRunnerAnnotation.value();
                if (RAVENWOOD_VERBOSE_LOGGING) {
                    Log.v(TAG, "Initializing the inner runner: " + clazz);
                }
                try {
                    try {
                        return clazz.getConstructor(Class.class).newInstance(testClass);
                    } catch (NoSuchMethodException ignored) {
                        var constructor = clazz.getConstructor(Class.class, RunnerBuilder.class);
                        mRealRunnerTakesRunnerBuilder = true;
                        return constructor.newInstance(testClass, mBuilder);
                    }
                } catch (Exception e) {
                    throw logAndFail("Failed to instantiate " + clazz, e);
                }
            }
            return null;
        }
    }

    RunnerBuilder junit4Builder() {
        return new JUnit4Builder();
    }

    RunnerBuilder junit3Builder() {
        return new JUnit3Builder();
    }

    RunnerBuilder annotatedBuilder() {
        return new RavenwoodAnnotatedBuilder();
    }

    RunnerBuilder ignoredBuilder() {
        return new IgnoredBuilder();
    }

    RunnerBuilder suiteMethodBuilder() {
        return new SuiteMethodBuilder();
    }

    static Error logAndFail(String message, Throwable exception) {
        Log.e(TAG, message, exception);
        return new AssertionError(message, exception);
    }

    final Runner instantiateRealRunner(TestClass testClass) {
        try {
            return mBuilder.runnerForClass(testClass.getJavaClass());
        } catch (Throwable e) {
            SneakyThrow.sneakyThrow(e);
            throw new RuntimeException();
        }
    }

    abstract Runner getRealRunner();

    @Override
    public final Description getDescription() {
        return getRealRunner().getDescription();
    }

    @Override
    public final void filter(Filter filter) throws NoTestsRemainException {
        if (getRealRunner() instanceof Filterable r) {
            r.filter(filter);
        }
    }

    @Override
    public final void order(Orderer orderer) throws InvalidOrderingException {
        if (getRealRunner() instanceof Orderable r) {
            r.order(orderer);
        }
    }

    @Override
    public final void sort(Sorter sorter) {
        if (getRealRunner() instanceof Sortable r) {
            r.sort(sorter);
        }
    }
}
