package android.app.ondeviceintelligence;

import android.app.ondeviceintelligence.DmaBufEntry;
import android.os.PersistableBundle;

/**
  * Interface for receiving information about allocaed DMABufs.
  *
  * @hide
  */
oneway interface IDmaBufInfoCallback {
    void onSuccess(in DmaBufEntry[] dmaBufs) = 2;
    void onFailure(int errorCode, in String errorMessage, in PersistableBundle errorParams) = 3;
}
