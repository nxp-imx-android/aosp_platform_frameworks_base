package android.app.ondeviceintelligence;

import android.os.PersistableBundle;

/**
  * Interface for receiving the total DMABufs allocated.
  *
  * @hide
  */
oneway interface IDmaBufTotalCallback {
    void onSuccess(in long total) = 1;
    void onFailure(int errorCode, in String errorMessage, in PersistableBundle errorParams) = 3;
}
