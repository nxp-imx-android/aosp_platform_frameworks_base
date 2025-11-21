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

package android.aiseal.tests.odim;

import android.aiseal.AiSealManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class AiSealOdimTestService extends Service {
    private static final String TAG = AiSealOdimTestService.class.getSimpleName();
    private final AiSealOdimTestServiceImpl mService = new AiSealOdimTestServiceImpl();

    public class AiSealOdimTestServiceImpl extends IAiSealOdimTestService.Stub {
        private IAiSealOdimPayloadService mGuestService;

        private IAiSealOdimPayloadService getGuestService() {
            if (mGuestService == null) {
                try {
                    AiSealManager mAiSeal =
                            getApplicationContext().getSystemService(AiSealManager.class);
                    IBinder binder =
                            mAiSeal.connectService(IAiSealOdimPayloadService.SERVICE_NAME);
                    mGuestService = IAiSealOdimPayloadService.Stub.asInterface(binder);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to connect to guest service", e);
                    throw new IllegalStateException("Failed to connect to guest service", e);
                }
            }
            return mGuestService;
        }

        @Override
        public String joinStringsWithSpace(String a, String b) {
            Log.d(TAG, "joinStringsWithSpace()");
            var guest = getGuestService();
            try {
                return guest.joinStringsWithSpace(a, b);
            } catch (Exception e) {
                Log.e(TAG, "Failed to call guest service", e);
                throw new IllegalStateException("Failed to call guest service", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mService;
    }
}
