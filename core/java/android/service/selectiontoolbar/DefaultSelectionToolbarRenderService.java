/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.service.selectiontoolbar;

import android.util.Slog;
import android.util.SparseArray;
import android.view.selectiontoolbar.ShowInfo;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * The default implementation of {@link SelectionToolbarRenderService}.
 *
 * <p><b>NOTE:</b> The requests are handled on the service main thread.
 *
 * @hide
 */
// TODO(b/214122495): fix class not found then move to system service folder
public final class DefaultSelectionToolbarRenderService extends SelectionToolbarRenderService {

    private static final String TAG = "DefaultSelectionToolbarRenderService";

    // TODO(b/215497659): handle remove if the client process dies.
    // Only show one toolbar, dismiss the old ones and remove from cache
    // Maps uid -> toolbar instance
    private final SparseArray<RemoteSelectionToolbar> mToolbarCache = new SparseArray<>();

    @Override
    public void onShow(int uid, ShowInfo showInfo,
            SelectionToolbarRenderService.RemoteCallbackWrapper callbackWrapper) {
        RemoteSelectionToolbar existingToolbar = mToolbarCache.get(uid);
        if (existingToolbar != null) {
            // TODO can we remove this check and just update the widget with dismissing?
            Slog.e(TAG, "Do not allow multiple toolbar for the uid : " + uid);
            return;
        }

        RemoteSelectionToolbar toolbar = new RemoteSelectionToolbar(uid, this,
                showInfo, callbackWrapper, this::transferTouch, this::onPasteAction);
        mToolbarCache.put(uid, toolbar);
        toolbar.show(showInfo);
        Slog.v(TAG, "onShow() for uid: " + uid);
    }

    @Override
    public void onHide(int uid) {
        RemoteSelectionToolbar toolbar = mToolbarCache.get(uid);
        if (toolbar != null) {
            Slog.v(TAG, "onHide() for uid: " + uid);
            toolbar.hide(uid);
        }
    }

    @Override
    public void onDismiss(int uid) {
        Slog.v(TAG, "onDismiss() for uid: " + uid);
        removeAndDismissToolbar(uid);
    }

    private void removeAndDismissToolbar(int uid) {
        RemoteSelectionToolbar toolbar = mToolbarCache.removeReturnOld(uid);
        if (toolbar != null) {
            toolbar.dismiss(uid);
        }
    }

    @Override
    public void onUidDied(int uid) {
        Slog.w(TAG, "onUidDied for uid: " + uid);
        removeAndDismissToolbar(uid);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int size = mToolbarCache.size();
        pw.print("number selectionToolbar: ");
        pw.println(size);
        String pfx = "  ";
        for (int i = 0; i < size; i++) {
            pw.print("#");
            pw.println(i);
            int uid = mToolbarCache.keyAt(i);
            pw.print(pfx);
            pw.print("uid: ");
            pw.println(uid);
            RemoteSelectionToolbar selectionToolbar = mToolbarCache.get(uid);
            pw.print(pfx);
            pw.print("selectionToolbar: ");
            selectionToolbar.dump(pfx, pw);
            pw.println();
        }
    }
}

