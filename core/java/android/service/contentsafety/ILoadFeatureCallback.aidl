package android.service.contentsafety;

/**
  * Interface for a ILoadFeatureCallback for making sure the retrieved features are loaded and ready to be used to ensure contents are safe.
  *
  * @hide
  */
oneway interface ILoadFeatureCallback {
    void onResult() = 1;
    void onError(in int errorCode) = 2;
}
