package android.app.contentsafety;

/**
  * Interface for a IIsFeatureEnabledCallback to be passed to the service implementation.

  * @hide
  */
oneway interface IIsFeatureEnabledCallback {
    void onSuccess(in boolean isFeatureEnabledResult) = 1;
    void onFailure(int failureStatus) = 2;
}
