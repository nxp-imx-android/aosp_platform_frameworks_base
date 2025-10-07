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
package android.platform.test.ravenwood;

import static android.platform.test.ravenwood.RavenwoodAwareTestRunner.getCurrentRunner;

import junit.framework.AssertionFailedError;
import junit.framework.Protectable;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.junit.AssumptionViolatedException;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Describable;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Runs JUnit3 test classes with additional Ravenwood support.
 */
public class RavenwoodAwareJUnit3Runner extends JUnit38ClassRunner {

    /**
     * Creates an adapter from RunNotifier to TestListener. The difference between
     * this class and the default one within JUnit38ClassRunner is that this class
     * also supports assumptions, which is used to skip tests with @DisabledOnRavenwood.
     */
    private record RunNotifierAdapter(RunNotifier notifier) implements TestListener {

        @Override
        public void endTest(Test test) {
            notifier.fireTestFinished(asDescription(test));
        }

        @Override
        public void startTest(Test test) {
            notifier.fireTestStarted(asDescription(test));
        }

        @Override
        public void addError(Test test, Throwable e) {
            Failure failure = new Failure(asDescription(test), e);
            if (e instanceof AssumptionViolatedException) {
                notifier.fireTestAssumptionFailed(failure);
            } else {
                notifier.fireTestFailure(failure);
            }
        }

        @Override
        public void addFailure(Test test, AssertionFailedError t) {
            addError(test, t);
        }

        private Description asDescription(Test test) {
            if (test instanceof Describable desc) {
                return desc.getDescription();
            }
            return Description.createTestDescription(test.getClass(), getName(test));
        }

        private String getName(Test test) {
            if (test instanceof TestCase) {
                return ((TestCase) test).getName();
            } else {
                return test.toString();
            }
        }
    }

    /**
     * Wraps around a TestCase to inject Ravenwood hooks.
     */
    private static class TestCaseHookWrapper implements Test {

        private final TestCase mTestCase;

        private TestCaseHookWrapper(TestCase testCase) {
            mTestCase = testCase;
        }

        @Override
        public int countTestCases() {
            return mTestCase.countTestCases();
        }

        @Override
        public void run(TestResult result) {
            result.startTest(mTestCase);
            result.runProtected(mTestCase, getTestRunProtectable());
            result.endTest(mTestCase);
        }

        private Protectable getTestRunProtectable() {
            return () -> {
                Description methodDesc = Description.createTestDescription(
                        mTestCase.getClass(), mTestCase.getName(), getAnnotations());

                final RavenwoodRule rule;
                if (mTestCase instanceof RavenwoodRule.Provider test) {
                    rule = test.getRavenwoodRule();
                } else {
                    rule = null;
                }

                getCurrentRunner().mState.enterTestMethod(methodDesc);
                try {
                    if (rule != null) {
                        var stmt = new Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                mTestCase.runBare();
                            }
                        };
                        rule.apply(stmt, methodDesc).evaluate();
                    } else {
                        mTestCase.runBare();
                    }
                } finally {
                    getCurrentRunner().mState.exitTestMethod(methodDesc);
                }
            };
        }

        private Annotation[] getAnnotations() {
            try {
                Method m = mTestCase.getClass().getMethod(mTestCase.getName());
                return m.getDeclaredAnnotations();
            } catch (NoSuchMethodException e) {
                return new Annotation[0];
            }
        }
    }

    /**
     * A test suite that add hooks into JUnit3 tests.
     */
    private static class RavenwoodTestSuite extends TestSuite {
        private RavenwoodTestSuite(final Class<?> theClass) {
            super(theClass);
        }

        @Override
        public void addTest(Test test) {
            if (test instanceof TestCase testCase) {
                super.addTest(new TestCaseHookWrapper(testCase));
            } else {
                super.addTest(test);
            }
        }
    }

    public RavenwoodAwareJUnit3Runner(Class<?> klass) {
        super(new RavenwoodTestSuite(klass.asSubclass(TestCase.class)));
    }

    @Override
    public TestListener createAdaptingListener(RunNotifier notifier) {
        return new RunNotifierAdapter(notifier);
    }
}
