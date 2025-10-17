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

package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Pair;

import com.android.internal.os.Zygote;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

/**
 * Maintains communication state with the native zygote processes. This class is responsible
 * for starting processes on behalf of the {@link android.os.Process} class.
 *
 * TODO(b/442732151): This class is still evolving and we plan to add more methods and achieve
 * closer parity with matching some of the provided arguments.
 *
 * @hide
 */
public class NativeZygoteProcess implements IZygoteProcess {
    private LocalSocketAddress mSocketAddress;
    private LocalSocket mSocket;

    public NativeZygoteProcess() {
        mSocketAddress = new LocalSocketAddress(Zygote.NATIVE_SOCKET_NAME,
                                                LocalSocketAddress.Namespace.RESERVED);
        mSocket = new LocalSocket(LocalSocket.SOCKET_SEQPACKET);
    }

    private static native int nativeStartNativeProcess(FileDescriptor fd, int uid, int gid,
            long startSeq, String packageName, String niceName, int targetSdkVersion,
            boolean startChildZygote, int runtimeFlags, String seInfo);

    @Override
    public final Process.ProcessStartResult start(@NonNull final String processClass,
                                                  final String niceName,
                                                  int uid, int gid, @Nullable int[] gids,
                                                  int runtimeFlags, int mountExternal,
                                                  int targetSdkVersion,
                                                  @Nullable String seInfo,
                                                  @NonNull String abi,
                                                  @Nullable String instructionSet,
                                                  @Nullable String appDataDir,
                                                  @Nullable String invokeWith,
                                                  @Nullable String packageName,
                                                  int zygotePolicyFlags,
                                                  boolean isTopApp,
                                                  @Nullable long[] disabledCompatChanges,
                                                  @Nullable Map<String, Pair<String, Long>>
                                                          pkgDataInfoMap,
                                                  @Nullable Map<String, Pair<String, Long>>
                                                          allowlistedDataInfoList,
                                                  boolean bindMountAppsData,
                                                  boolean bindMountAppStorageDirs,
                                                  boolean bindOverrideSysprops,
                                                  long startSeq,
                                                  @Nullable String[] zygoteArgs) {
        try {
            mSocket.connect(mSocketAddress);
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to socket.");
        }
        int pid = nativeStartNativeProcess(mSocket.getFileDescriptor(), uid, gid, startSeq,
                packageName, niceName, targetSdkVersion, /*startChildZygote=*/false, runtimeFlags,
                seInfo);
        if (pid == -1) {
            throw new RuntimeException("Failed to fork a native process.");
        }
        Process.ProcessStartResult result = new Process.ProcessStartResult();
        result.pid = pid;
        result.usingWrapper = false;
        return result;
    }
}
