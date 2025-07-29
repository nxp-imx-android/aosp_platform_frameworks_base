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

import static com.android.ravenwood.common.RavenwoodInternalUtils.log;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnabledOnRavenwood;

import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;

import org.junit.runner.Description;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Calculates which tests need to be executed on Ravenwood.
 */
public class RavenwoodEnablementChecker {
    private static final String TAG = RavenwoodInternalUtils.TAG;

    /**
     * When this flag is enabled, all disabled tests will run, and all enabled tests will
     * be skipped to detect cases where a test is able to pass despite being marked as
     * {@link DisabledOnRavenwood}.
     *
     * This is typically helpful for internal maintainers discovering tests that had previously
     * been ignored, but now have enough Ravenwood-supported functionality to be enabled.
     *
     * RavenwoodCoreTest modifies it, so not final.
     */
    public static volatile boolean RUN_DISABLED_TESTS = "1".equals(
            System.getenv("RAVENWOOD_RUN_DISABLED_TESTS"));

    /**
     * When using RAVENWOOD_RUN_DISABLED_TESTS, you may still want to skip certain tests,
     * for example because the test would crash the JVM.
     *
     * This regex defines the tests that should still be disabled even if
     * RAVENWOOD_RUN_DISABLED_TESTS is set.
     *
     * Before running each test class and method, we check if this pattern can be found in
     * the full test name (either [class full name], or [class full name] + "#" + [method name]),
     * and if so, we skip it.
     *
     * For example, if you want to skip an entire test class, use:
     * RAVENWOOD_REALLY_DISABLE='\.CustomTileDefaultsRepositoryTest$'
     *
     * For example, if you want to skip a test method, use:
     * RAVENWOOD_REALLY_DISABLE='\.CustomTileDefaultsRepositoryTest#testSimple$'
     *
     * To ignore multiple classes, use (...|...), for example:
     * RAVENWOOD_REALLY_DISABLE='\.(ClassA|ClassB)$'
     *
     * Because we use a regex-find, setting "." would disable all tests.
     *
     * RavenwoodCoreTest modifies it, so not final.
     */
    public static volatile Pattern REALLY_DISABLED_PATTERN = Pattern.compile(
            Objects.requireNonNullElse(System.getenv("RAVENWOOD_REALLY_DISABLED"), ""));

    /**
     * When using RAVENWOOD_TEST_ENABLEMENT_POLICY, you can provide an external policy text file
     * to change whether each test classes or methods are enabled in Ravenwood without the need
     * to use {@link DisabledOnRavenwood} or {@link EnabledOnRavenwood} annotations.
     *
     * The policy file are lines, each with 2 space delimited fields in the following format:
     *
     * [signature: string] [enabled: boolean]
     *
     * The "signature" field has 2 subcomponents: a class name and method name, separated with the
     * '#' symbol (e.g. com.example.TestClass#testMethod). Method name can be omitted if the
     * policy in question applies to the entire class, not a specific method.
     *
     * When the "signature" field is the special value "*", the "enable" field sets the
     * global default enablement status.
     */
    private static final String RAVENWOOD_TEST_ENABLEMENT_POLICY
            = System.getenv("RAVENWOOD_TEST_ENABLEMENT_POLICY");

    private static final EnablementPolicy sEnablementPolicy = new EnablementPolicy();

    private static class ClassEnablementPolicy {
        Boolean mEnabled;
        Map<String, Boolean> mMethods;
    }

    private static class EnablementPolicy {
        final Map<String, Boolean> mModules = new HashMap<>();
        final Map<String, ClassEnablementPolicy> mClasses = new HashMap<>();

        private boolean shouldEnableModule(String testModule) {
            return mModules.getOrDefault(testModule, true);
        }

        boolean shouldEnableClass(String testModule, String className) {
            final boolean moduleEnabled = shouldEnableModule(testModule);
            if (mClasses.isEmpty()) {
                return moduleEnabled;
            }
            var clazz = mClasses.get(className);
            if (clazz == null) {
                return moduleEnabled;
            }
            return clazz.mEnabled != null ? clazz.mEnabled : moduleEnabled;
        }

        Boolean shouldEnableMethod(String className, String methodName) {
            if (mClasses.isEmpty()) {
                return null;
            }
            var clazz = mClasses.get(className);
            if (clazz == null) {
                return null;
            }
            if (clazz.mMethods == null) {
                return null;
            }
            return clazz.mMethods.get(methodName);
        }

        void parseLine(String line) {
            var columns = line.split("\\s", 2);
            if (columns.length != 2) return;
            var signature = columns[0];
            boolean enable = Boolean.parseBoolean(columns[1]);
            if (signature.contains("*")) {
                // Setting the test module default policy
                parseWildcard(signature, enable);
            } else {
                var s = signature.split("\\#");
                var clazz = s[0];
                var method = s.length > 1 ? s[1] : null;
                var policy = mClasses.computeIfAbsent(clazz, k -> new ClassEnablementPolicy());
                if (method != null) {
                    if (policy.mMethods == null) policy.mMethods = new HashMap<>();
                    policy.mMethods.put(method, enable);
                } else {
                    policy.mEnabled = enable;
                }
            }
        }

        private void parseWildcard(String signature, boolean enabled) {
            // Only "[TEST-MODULE]:*" is supported, for now.
            if (signature.endsWith(":*")) {
                var module = signature.substring(0, signature.length() - 2);
                mModules.put(module, enabled);
                return;
            }
            throw new RuntimeException(
                    "Invalid use of '*' in enablement policy '" + signature + "': "
                    + " Only '[TEST-MODULE]:*' is supported");
        }

        void clear() {
            mModules.clear();
            mClasses.clear();
        }
    }

    static {
        if (RUN_DISABLED_TESTS) {
            log(TAG, "$RAVENWOOD_RUN_DISABLED_TESTS enabled: running only disabled tests");
            if (!REALLY_DISABLED_PATTERN.pattern().isEmpty()) {
                log(TAG, "$RAVENWOOD_REALLY_DISABLED=" + REALLY_DISABLED_PATTERN.pattern());
            }
        }

        if (RAVENWOOD_TEST_ENABLEMENT_POLICY != null) {
            try {
                var policy = Files.readString(Path.of(RAVENWOOD_TEST_ENABLEMENT_POLICY));
                setTestEnablementPolicy(policy);
            } catch (IOException e) {
                SneakyThrow.sneakyThrow(e);
            }
        }
    }

    private RavenwoodEnablementChecker() {
    }

    public static void setTestEnablementPolicy(String policy) {
        sEnablementPolicy.clear();
        policy.lines().map(String::strip)
                // Remove inline comments
                .map(line -> line.replaceAll("\\s+\\#.*$", "").strip())
                // Ignore empty lines and full line comments
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(sEnablementPolicy::parseLine);
    }

    /**
     * Determine if the given {@link Description} should be enabled when running on the
     * Ravenwood test environment.
     *
     * A more specific method-level annotation always takes precedence over any class-level
     * annotation, and an {@link EnabledOnRavenwood} annotation always takes precedence over
     * an {@link DisabledOnRavenwood} annotation.
     */
    public static boolean shouldEnableOnRavenwood(Description description,
            boolean checkRunDisabledTestsFlag) {
        // First, consult any method-level annotations
        if (description.isTest()) {
            Boolean result = null;

            if (description.getAnnotation(EnabledOnRavenwood.class) != null) {
                result = true;
            } else if (description.getAnnotation(DisabledOnRavenwood.class) != null) {
                result = false;
            } else {
                result = sEnablementPolicy.shouldEnableMethod(
                        description.getClassName(), description.getMethodName());
            }
            if (result != null) {
                if (checkRunDisabledTestsFlag && RUN_DISABLED_TESTS) {
                    // Invert the result + check the really disable pattern
                    result = !result && !shouldReallyDisableTest(
                            description.getTestClass(), description.getMethodName());
                }
            }
            if (result != null) {
                return result;
            }
        }

        // Otherwise, consult any class-level annotations
        return shouldRunClassOnRavenwood(description.getTestClass(),
                checkRunDisabledTestsFlag);
    }

    public static boolean shouldRunClassOnRavenwood(@NonNull Class<?> testClass,
            boolean checkRunDisabledTestsFlag) {
        boolean result;
        if (testClass.getAnnotation(EnabledOnRavenwood.class) != null) {
            result = true;
        } else if (testClass.getAnnotation(DisabledOnRavenwood.class) != null) {
            result = false;
        } else {
            result = sEnablementPolicy.shouldEnableClass(
                    RavenwoodEnvironment.getInstance().getTestModuleName(), testClass.getName());
        }
        if (checkRunDisabledTestsFlag && RUN_DISABLED_TESTS) {
            // Invert the result + check the really disable pattern
            result = !result && !shouldReallyDisableTest(testClass, null);
        }
        return result;
    }

    /**
     * Check if a test should _still_ disabled even if {@link #RUN_DISABLED_TESTS}
     * is true, using {@link #REALLY_DISABLED_PATTERN}.
     *
     * This only works on tests, not on classes.
     */
    private static boolean shouldReallyDisableTest(@NonNull Class<?> testClass,
            @Nullable String methodName) {
        if (REALLY_DISABLED_PATTERN.pattern().isEmpty()) {
            return false;
        }

        final var fullname = testClass.getName() + (methodName != null ? "#" + methodName : "");

        if (REALLY_DISABLED_PATTERN.matcher(fullname).find()) {
            System.out.println("Still ignoring " + fullname);
            return true;
        }
        return false;
    }
}
