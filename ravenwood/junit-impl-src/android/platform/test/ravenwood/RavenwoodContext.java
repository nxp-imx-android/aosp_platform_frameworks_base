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

import static com.android.ravenwood.common.RavenwoodCommonUtils.RAVENWOOD_RESOURCE_APK;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.hardware.ISerialManager;
import android.hardware.SerialManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PermissionEnforcer;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.ravenwood.example.BlueManager;
import android.ravenwood.example.RedManager;
import android.util.ArrayMap;
import android.util.Singleton;

import com.android.internal.annotations.GuardedBy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class RavenwoodContext extends RavenwoodBaseContext {
    private static final String TAG = com.android.ravenwood.common.RavenwoodCommonUtils.TAG;

    private final Object mLock = new Object();
    private final String mPackageName;
    private final HandlerThread mMainThread;

    private final RavenwoodPermissionEnforcer mEnforcer = new RavenwoodPermissionEnforcer();

    private final ArrayMap<Class<?>, String> mClassToName = new ArrayMap<>();
    private final ArrayMap<String, Supplier<?>> mNameToFactory = new ArrayMap<>();

    private final File mDataDir;
    private final Supplier<Resources> mResourcesSupplier;

    private RavenwoodContext mAppContext;

    @GuardedBy("mLock")
    private Resources mResources;

    @GuardedBy("mLock")
    private Resources.Theme mTheme;

    @GuardedBy("mLock")
    private PackageManager mPm;

    private void registerService(Class<?> serviceClass, String serviceName,
            Supplier<?> serviceSupplier) {
        mClassToName.put(serviceClass, serviceName);
        mNameToFactory.put(serviceName, serviceSupplier);
    }

    public RavenwoodContext(String packageName, HandlerThread mainThread,
            Supplier<Resources> resourcesSupplier) throws IOException {
        mPackageName = packageName;
        mMainThread = mainThread;
        mResourcesSupplier = resourcesSupplier;

        mDataDir = Files.createTempDirectory(mPackageName).toFile();

        // Services provided by a typical shipping device
        registerService(ClipboardManager.class,
                Context.CLIPBOARD_SERVICE, memoize(() ->
                        new ClipboardManager(this, getMainThreadHandler())));
        registerService(PermissionEnforcer.class,
                Context.PERMISSION_ENFORCER_SERVICE, () -> mEnforcer);
        registerService(SerialManager.class,
                Context.SERIAL_SERVICE, memoize(() ->
                        new SerialManager(this, ISerialManager.Stub.asInterface(
                                ServiceManager.getService(Context.SERIAL_SERVICE)))
                ));

        // Additional services we provide for testing purposes
        registerService(BlueManager.class,
                BlueManager.SERVICE_NAME, memoize(() -> new BlueManager()));
        registerService(RedManager.class,
                RedManager.SERVICE_NAME, memoize(() -> new RedManager()));
    }

    @Override
    public Object getSystemService(String serviceName) {
        // TODO: pivot to using SystemServiceRegistry
        final Supplier<?> serviceSupplier = mNameToFactory.get(serviceName);
        if (serviceSupplier != null) {
            return serviceSupplier.get();
        } else {
            throw new UnsupportedOperationException(
                    "Service " + serviceName + " not yet supported under Ravenwood");
        }
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        // TODO: pivot to using SystemServiceRegistry
        final String serviceName = mClassToName.get(serviceClass);
        if (serviceName != null) {
            return serviceName;
        } else {
            throw new UnsupportedOperationException(
                    "Service " + serviceClass + " not yet supported under Ravenwood");
        }
    }

    @Override
    public PackageManager getPackageManager() {
        synchronized (mLock) {
            if (mPm == null) {
                mPm = new RavenwoodPackageManager(this);
            }
            return mPm;
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    public Looper getMainLooper() {
        return mMainThread.getLooper();
    }

    @Override
    public Handler getMainThreadHandler() {
        return mMainThread.getThreadHandler();
    }

    @Override
    public Executor getMainExecutor() {
        return mMainThread.getThreadExecutor();
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public String getOpPackageName() {
        return mPackageName;
    }

    @Override
    public String getAttributionTag() {
        return null;
    }

    @Override
    public UserHandle getUser() {
        return UserHandle.of(UserHandle.myUserId());
    }

    @Override
    public int getUserId() {
        return UserHandle.myUserId();
    }

    @Override
    public int getDeviceId() {
        return Context.DEVICE_ID_DEFAULT;
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return new FileInputStream(getFileStreamPath(name));
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        final boolean append = (mode & MODE_APPEND) != 0;
        return new FileOutputStream(getFileStreamPath(name), append);
    }

    @Override
    public boolean deleteFile(String name) {
        return getFileStreamPath(name).delete();
    }

    @Override
    public File getDataDir() {
        return mDataDir;
    }

    @Override
    public File getDir(String name, int mode) {
        name = "app_" + name;
        File file = new File(getDataDir(), name);
        if (!file.exists()) {
            file.mkdir();
        }
        return file;
    }

    private File makePrivateDir(String name) {
        var dir = new File(getDataDir(), name);
        dir.mkdirs();
        return dir;
    }

    private File getPreferencesDir() {
        return makePrivateDir("shared_prefs");
    }

    @Override
    public File getFilesDir() {
        return makePrivateDir("files");
    }

    @Override
    public File getCacheDir() {
        return makePrivateDir("cache");
    }

    @Override
    public File getCodeCacheDir() {
        return makePrivateDir("code_cache");
    }

    @Override
    public File getNoBackupFilesDir() {
        return makePrivateDir("no_backup");
    }

    @Override
    public File getFileStreamPath(String name) {
        return new File(getFilesDir(), name);
    }

    @Override
    public File getSharedPreferencesPath(String name) {
        return new File(getPreferencesDir(), name + ".xml");
    }

    @Override
    public Resources getResources() {
        synchronized (mLock) {
            if (mResources == null) {
                mResources = mResourcesSupplier.get();
            }
            return mResources;
        }
    }

    @Override
    public AssetManager getAssets() {
        return getResources().getAssets();
    }

    @Override
    public Theme getTheme() {
        synchronized (mLock) {
            if (mTheme == null) {
                mTheme = getResources().newTheme();
            }
            return mTheme;
        }
    }

    @Override
    public void setTheme(int resid) {
        synchronized (mLock) {
            getTheme().applyStyle(resid, true);
        }
    }

    @Override
    public String getPackageResourcePath() {
        return new File(RAVENWOOD_RESOURCE_APK).getAbsolutePath();
    }

    public void setApplicationContext(RavenwoodContext appContext) {
        mAppContext = appContext;
    }

    @Override
    public Context getApplicationContext() {
        return mAppContext;
    }

    @Override
    public boolean isRestricted() {
        return false;
    }

    @Override
    public boolean canLoadUnsafeResources() {
        return true;
    }

    /**
     * Wrap the given {@link Supplier} to become memoized.
     *
     * The underlying {@link Supplier} will only be invoked once, and that result will be cached
     * and returned for any future requests.
     */
    private static <T> Supplier<T> memoize(ThrowingSupplier<T> supplier) {
        final Singleton<T> singleton = new Singleton<>() {
            @Override
            protected T create() {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return () -> singleton.get();
    }

    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
