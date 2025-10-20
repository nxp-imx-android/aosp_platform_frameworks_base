# Running AppMemoryTestCases

This guide provides instructions on how to run `AppMemoryTestCases` on a local device and in the Forrest/ABTD environment.

## Running on a Local Device

Follow these steps to run the tests on a physical device connected to your cloudtop.

1. **Install the superproject** (see go/repo-init) on your cloudtop.

2. **Set up and flash** your test device.

3. **Link the device** to your cloudtop using [pontis](http://go/pontis).

4. Source the build environment and select the target:

```
source build/envsetup.sh
lunch panther-trunk_staging-userdebug
```

5. Navigate to the test directory:

```
cd frameworks/base/tests/AppMemoryTest/
```

6. Execute the test suite:

```
atest AppMemoryTestCases
```

7. **Find the test artifacts** in the output directory specified in the logs. For example:

/tmp/atest_result_yourusernamehere/20250903_180921_clbph7a3/log/invocation_946579573326099876/inv_13336902944655844617/AppMemoryTestCases


## Running on Forrest/ABTD

Follow these steps to execute the tests using the Forrest/ABTD web interface.

1. Click **Create a run**.

2. Click **Run tests**.

3. Select **LGKB** as the test runner.

4. Select the branch **git_main**.

5. Input `AppMemoryTestCases` as the **atest module**.

6. Check the **Advanced** settings checkbox.

7. Set the product to a physical device, e.g., **raven**.

8. Click **Run** to start the test.


## Analyzing Perfetto Traces

After a test run, a Perfetto trace file can be found as a test artifact. Here are two useful queries which can be run in Perfetto UI

###  Get native heap allocation dumps

```sql
WITH AggregatedDumps AS (
  SELECT
    a.ts,
    p.name AS track_name,
    SUM(a.size) AS dump_size_bytes_for_ts
  FROM
    heap_profile_allocation AS a
  JOIN
    process AS p ON a.upid = p.upid
  GROUP BY
    a.ts, p.name
)
SELECT
  ts,
  track_name,
  SUM(dump_size_bytes_for_ts) OVER (PARTITION BY track_name ORDER BY ts) AS cumulative_dump_size_bytes,
  SUM(dump_size_bytes_for_ts) OVER (PARTITION BY track_name ORDER BY ts) / (1024.0 * 1024.0) AS cumulative_dump_size_mib
FROM
  AggregatedDumps
ORDER BY
  ts, track_name;
```

### Get binder transactions in time range of app startup

```sql
INCLUDE PERFETTO MODULE android.binder;
WITH
  testhelper_startup AS (
    SELECT
      ts,
      (ts + dur) AS ts_end
    FROM
      android_startups
    WHERE
      package = 'android.app.memory.testhelper'
    LIMIT 1
  )
SELECT
  *
FROM
  android_binder_txns AS abt
  LEFT JOIN process AS p ON (p.upid = abt.client_upid)
  LEFT JOIN thread AS t ON (t.utid = abt.client_utid)
  JOIN testhelper_startup
    ON (abt.client_ts >= testhelper_startup.ts AND abt.client_ts < testhelper_startup.ts_end)
WHERE
  abt.client_process = 'android.app.memory.testhelper'
```