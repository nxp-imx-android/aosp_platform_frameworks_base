/*
 * Copyright (C) 2006 The Android Open Source Project
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
package android.app;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.FileUtils;
import android.platform.test.ravenwood.RavenwoodPackageManager;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class ContextImpl_ravenwood {
    private static final String TAG = "ContextImpl_ravenwood";

    /** Indicates a {@link Context} is really a {@link ContextImpl}. */
    @Target({FIELD, METHOD, PARAMETER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReallyContextImpl {
    }

    /** Backdoor to a package-private method. */
    public static ContextImpl createSystemContext(ActivityThread at) {
        return ContextImpl.createSystemContext(at);
    }

    /** Backdoor to a package-private method. */
    public static ContextImpl createAppContext(ActivityThread at, LoadedApk la) {
        return ContextImpl.createAppContext(at, la);
    }

    static PackageManager getPackageManagerInner(ContextImpl contextImpl) {
        return new RavenwoodPackageManager(contextImpl);
    }

    static File ensurePrivateDirExists(File file, int mode, int gid, String xattr) {
        if (!file.exists()) {
            final String path = file.getAbsolutePath();
            file.mkdirs();

            // setPermissions() may fail and return an error status, but we just ignore
            // it just like the real method ignores exceptions.
            // setPermissions() does a log though.
            FileUtils.setPermissions(path, mode, -1, -1);
        }
        return file;
    }
}
