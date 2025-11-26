package android.app.contentsafety;

import android.os.Bundle;

/**
  * Interface for a ICheckContentCallback for receiving response from ContentSafetyService when CheckContent is executed against the provided feature.
  *
  * @hide
  */
interface ICheckContentCallback {

    // returning a Bundle, where keys are FeatureType, and values are a list of status codes.
    // Note that success and failure status codes are included in the same bundle.
    void onResult(in Bundle result) = 1;

}
