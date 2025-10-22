# Running AppMemoryTestCases

This guide provides instructions on how to run `AppMemoryTestCases` on a local device and in the Forrest/ABTD environment.

## Running on a Local Device

Follow these steps to run the tests on a physical device connected to your cloudtop.

1. **Install the superproject** (see go/repo-init) on your cloudtop.

2. **Set up and flash** your test device.

3. **Link the device** to your cloudtop using [pontis](http://go/pontis).

4. Source the build environment and select a target (example below):

```
source build/envsetup.sh
lunch panther-trunk_staging-userdebug
```

5. Navigate to vendor/google_testing/integration/tests/scenarios/tests/configs (local test run through crystal ball)

```
cd vendor/google_testing/integration/tests/scenarios/tests/configs
```

6. Execute the test suite:

```
atest appmemorytest
```

7. **Test results** will be displayed as a [ab/](go/ab)


## Running on Forrest/ABTD

Example ABTD run [here](https://android-build.corp.google.com/builds/abtd/run/L81600030030985934).

Users can rerun this test to get a mostly prefilled ABTD test that is ready to execute.

Follow these steps to execute the tests using the Forrest/ABTD web interface.

1. Click **Create a run**.

2. Click **Run tests**.

3. Select **LGKB** as the test runner.

4. Select the branch **git_main**.

5. Select ATP

6. Input 'v2/android-crystalball-eng/health/appmemorytest/appmemorytest' as test

7. Optionally configure any advanced settings

8. Click **Run** to start the test.


## Adding PerfettoSQL metrics

To add metrics as PerfettoSQL queries to be run on the perfetto trace artifact of appmemorytest:

1. Add a .textproto file in the `metrics/` folder

2. Add the file and metric id in vendor/google_testing/integration/tests/scenarios/tests/configs/appmemorytest.xml