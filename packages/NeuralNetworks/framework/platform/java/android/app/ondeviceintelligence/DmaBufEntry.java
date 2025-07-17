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

package android.app.ondeviceintelligence;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_DMABUF_INFO;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

/**
 * Represents information about one exported DMABuf, including the named exporter,
 * the inode, the process IDs (PIDs) that have access and the current size.
 * @see <a href="https://docs.kernel.org/driver-api/dma-buf.html"> Kernel
 * DMA-BUF </a>
 */
/** @hide */
@FlaggedApi(FLAG_DMABUF_INFO)
@SystemApi
public final class DmaBufEntry implements Parcelable {
    /**
     * inode of the exported DMABuf.
     */
    private final long mInode;

    /**
     * Size of the exported DMABuf.
     */
    private final long mSize;

    /**
     * The name of the exporter of this DMABuf.
     */
    @Nullable
    private final String mExporter;

    @NonNull
    private final int[] mPids;

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(mInode);
        dest.writeLong(mSize);
        dest.writeString(mExporter);
        dest.writeInt(mPids.length);
        dest.writeIntArray(mPids);
    }

    public DmaBufEntry(long inode, long size, @Nullable String exporter, @NonNull int[] pids) {
        this.mInode = inode;
        this.mSize = size;
        this.mExporter = exporter;
        this.mPids = pids;
    }

    private DmaBufEntry(Parcel source) {
        this.mInode = source.readLong();
        this.mSize = source.readLong();
        this.mExporter = source.readString();
        this.mPids = new int[source.readInt()];
        source.readIntArray(this.mPids);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @android.annotation.NonNull Creator<DmaBufEntry> CREATOR =
            new Creator<DmaBufEntry>() {
                public DmaBufEntry createFromParcel(Parcel source) {
                    return new DmaBufEntry(source);
                }

                public DmaBufEntry[] newArray(int size) {
                    return new DmaBufEntry[size];
                }
            };

    @Override
    public String toString() {
        return "{exporter: "
                + mExporter
                + ", inode: "
                + mInode
                + ", size: "
                + mSize
                + ", pids: "
                + Arrays.toString(mPids)
                + " }";
    }

    /**
     * Get the inode of the exported DMABuf.
     *
     * @return inode of the exported DMABuf.
     */
    public long getInode() {
        return mInode;
    }

    /**
     * Get the size of the exported DMABuf.
     *
     * @return size of the exported DMABuf.
     */
    public long getSize() {
        return mSize;
    }

    /**
     * Get the name of the exporter of this DMABuf as set by the exp_name field of
     * dma_buf_export_info. May be null if exp_name is not set.
     *
     * @return name of the exporter of this DMABuf.
     */
    @Nullable
    public String getExporter() {
        return mExporter;
    }

    /**
     * Get the pids of the clients of this DMABuf.
     *
     * @return pids of the clients of this DMABuf.
     */
    @NonNull
    public int[] getPids() {
        return mPids;
    }
}
