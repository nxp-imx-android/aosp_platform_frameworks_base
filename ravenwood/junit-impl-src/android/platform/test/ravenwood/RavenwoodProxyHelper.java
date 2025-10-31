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

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import org.objectweb.asm.Type;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Helper class to create proxy instances for AIDL interfaces.
 */
public class RavenwoodProxyHelper {
    private static final String TAG = "RavenwoodProxyHelper";

    private RavenwoodProxyHelper() {
    }

    private static String getAidlDescriptor(Class<?> clazz) {
        try {
            // Use reflection to get the DESCRIPTOR field from the Stub class
            Class<?> stubClass = Class.forName(clazz.getName() + "$Stub");
            return (String) stubClass.getField("DESCRIPTOR").get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Error getting descriptor for " + clazz.getName(), e);
        }
    }

    /**
     * Creates a new Proxy object for a IInterface type, which also logs all called method names.
     */
    public static <T extends IInterface> T newProxy(Class<T> clazz, InvocationHandler ih) {
        return newProxy(clazz, getAidlDescriptor(clazz), ih, false);
    }

    /**
     * Creates a new Proxy object for a IInterface type, which also logs all called method names.
     */
    public static <T extends IInterface> T newExperimentalProxy(
            Class<T> clazz, InvocationHandler ih) {
        return newProxy(clazz, getAidlDescriptor(clazz), ih, true);
    }

    /**
     * Creates a new Proxy object for a IInterface type, which also logs all called method names.
     */
    @SuppressWarnings("unchecked")
    public static <T extends IInterface> T newProxy(
            Class<T> clazz, String descriptor, InvocationHandler ih, boolean isExperimental) {
        var handler = new IInterfaceInvocationWrapper(ih, isExperimental);
        T proxy = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
        handler.setBinder(new Binder() {
            @Override
            public IInterface queryLocalInterface(String desc) {
                if (descriptor.equals(desc)) {
                    return proxy;
                }
                throw new RuntimeException("Unknown descriptor: " + desc);
            }
        });
        return proxy;
    }

    /**
     * InvocationHandler that always returns the default value.
     */
    public static final InvocationHandler sDefaultHandler = new DefaultReturningInvocationHandler();

    /**
     * InvocationHandler that always throws {@link RavenwoodUnsupportedApiException}.
     */
    public static final InvocationHandler sNotImplementedHandler = (p, m, a) -> {
        var method = m.getDeclaringClass().getName() + "#" + m.getName();
        throw new RavenwoodUnsupportedApiException("Method " + method).setReason(method);
    };

    /**
     * Wraps another {@link InvocationHandler}, which implements {@link IInterface#asBinder()}
     * for the proxy.
     */
    private static class IInterfaceInvocationWrapper implements InvocationHandler {

        private final LoggingInvocationWrapper mInner;
        private final boolean mIsExperimental;
        private IBinder mBinder;

        private IInterfaceInvocationWrapper(InvocationHandler inner, boolean isExperimental) {
            mInner = new LoggingInvocationWrapper(inner);
            mIsExperimental = isExperimental;
        }

        private void setBinder(IBinder binder) {
            mBinder = binder;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == IInterface.class
                    && method.getName().equals("asBinder")) {
                return mBinder;
            }
            if (mIsExperimental) {
                RavenwoodExperimentalApiChecker.onExperimentalApiCalled(
                        method.getDeclaringClass(), method.getName(),
                        Type.getMethodDescriptor(method));
            }
            return mInner.invoke(proxy, method, args);
        }
    }

    /**
     * Wraps another {@link InvocationHandler}, and prints the method information
     * in every call.
     */
    private static class LoggingInvocationWrapper implements InvocationHandler {
        private final InvocationHandler mInner;

        private LoggingInvocationWrapper(InvocationHandler inner) {
            mInner = inner;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.w(TAG, "Proxy called: "
                    + method.getDeclaringClass().getName() + "#" + method.getName());
            return mInner.invoke(proxy, method, args);
        }
    }

    /**
     * InvocationHandler that always returns the default value.
     */
    private static class DefaultReturningInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            var t = method.getReturnType();
            if (t == boolean.class || t == Boolean.class) {
                return false;
            }
            if (t == int.class || t == Integer.class) {
                return 0;
            }
            if (t == long.class || t == Long.class) {
                return 0L;
            }
            if (t == short.class || t == Short.class) {
                return (short) 0;
            }
            if (t == char.class || t == Character.class) {
                return (char) 0;
            }
            if (t == byte.class || t == Byte.class) {
                return (byte) 0;
            }
            if (t == float.class || t == Float.class) {
                return (float) 0;
            }
            if (t == double.class || t == Double.class) {
                return (double) 0;
            }
            return null;
        }
    }
}
