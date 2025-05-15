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
package com.android.ravenwoodtest.coretest;

import static com.google.common.truth.Truth.assertThat;

import android.ravenwood.annotation.RavenwoodSupported.RavenwoodProvidingImplementation;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test to verify {@link android.ravenwood.annotation.RavenwoodSupported}
 * and {@link RavenwoodProvidingImplementation} are used correctly.
 */
public class RavenwoodSupportedAnnotationTest {
    private static final Class<? extends Annotation> RAVENWOOD_SUPPORTED_ANNOT =
            getThrowButSupportedAnnotation();

    @Test
    public void testContext() throws Exception {
        check("android.content.Context", "android.platform.test.ravenwood.RavenwoodContext");
    }

    @Test
    public void testPackageManager() throws Exception {
        check("android.content.pm.PackageManager",
                "android.platform.test.ravenwood.RavenwoodPackageManager");
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> getThrowButSupportedAnnotation() {
        try {
            return (Class<? extends Annotation>) Class.forName(
                    "com.android.hoststubgen.hosthelper.HostStubGenProcessedAsThrowButSupported");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void check(String frameworkClass, String subclass) throws Exception {
        check(Class.forName(frameworkClass), Class.forName(subclass));
    }

    /** Class to hold a Method with its signature. */
    private static class MethodRef {
        public final Method method;
        private final String mSignature;

        MethodRef(Method method) {
            this.method = method;
            this.mSignature = getMethodSignature(method);
        }

        String getSignature() {
            return mSignature;
        }
    }

    /** Build a simple method signature. (method name + arg types only) */
    private static String getMethodSignature(Method method) {
        var sb = new StringBuilder();

        sb.append(method.getName());

        sb.append("(");
        for (var p : method.getParameterTypes()) {
            sb.append(p.getName());
            sb.append(",");
        }
        sb.append(")");

        return sb.toString();
    }


    /** Return all methods (including super and interfaces' ones) from a given type. */
    private static List<MethodRef> findAllMethods(Class<?> cls) throws Exception {
        var ret = new ArrayList<MethodRef>();

        collectAllMethods(cls, ret);
        ret.sort(Comparator.comparing(MethodRef::getSignature));

        return ret;
    }

    /** Collect methods from a given type. */
    private static void collectAllMethods(Class<?> cls, List<MethodRef> methods) throws Exception {
        if (cls == null || cls == Object.class) {
            return;
        }
        for (var m : cls.getDeclaredMethods()) {
            methods.add(new MethodRef(m));
        }

        // Collect super methods.
        collectAllMethods(cls.getSuperclass(), methods);

        // Collect interface methods.
        for (var i : cls.getInterfaces()) {
            collectAllMethods(i, methods);
        }
    }

    /**
     * Find methods declared in {@code subclass} that's in {@code superMethods},
     * meaning methods overriding the super methods.
     */
    private static List<MethodRef> findOverridingMethods(Class<?> subclass,
            List<MethodRef> superMethods) {
        // Create a set of super method signatures.
        var superMethodSet = new HashSet<String>();
        superMethods.forEach(superMethod -> superMethodSet.add(superMethod.getSignature()));

        var ret = new ArrayList<MethodRef>();
        for (var m : subclass.getDeclaredMethods()) {
            var sig = getMethodSignature(m);
            if (superMethodSet.contains(sig)) {
                ret.add(new MethodRef(m));
            }
        }
        return ret;
    }

    /** Convert a method list to a single string for diffing. */
    private static String toMethodListString(List<MethodRef> methods) {
        var sb = new StringBuilder();

        for (var m : methods) {
            sb.append(m.getSignature());
            sb.append("\n");
        }
        // If no methods are found, there's something wrong.
        assertThat(sb.length()).isGreaterThan(0);

        return sb.toString();
    }

    /**
     * Actual test method
     * @param frameworkClass target (base) class
     * @param subclass subclass that provides the implementation.
     */
    private static void check(Class<?> frameworkClass, Class<?> subclass) throws Exception {
        // First, check the class's annotation too.
        var anot = subclass.getAnnotation(RavenwoodProvidingImplementation.class);
        assertThat(anot).isNotNull();
        assertThat(anot.target()).isEqualTo(frameworkClass);

        // List all the methods from the target class.
        var frameworkMethods = findAllMethods(frameworkClass);

        // Extract only methods with @RavenwoodSupported.
        var supportedFrameworkMethods = frameworkMethods.stream()
                .filter(m -> m.method.isAnnotationPresent(RAVENWOOD_SUPPORTED_ANNOT))
                .collect(Collectors.toList());

        // Methods in the subclass that
        var overridingMethods = findOverridingMethods(subclass, frameworkMethods);

        supportedFrameworkMethods.sort(Comparator.comparing(MethodRef::getSignature));
        overridingMethods.sort(Comparator.comparing(MethodRef::getSignature));

        // Then compare them. They should match.
        assertThat(toMethodListString(supportedFrameworkMethods))
                .isEqualTo(toMethodListString(overridingMethods));
    }
}
