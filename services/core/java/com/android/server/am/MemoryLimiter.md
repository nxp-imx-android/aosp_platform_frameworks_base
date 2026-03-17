# MemoryLimiter

MemoryLimiter is a system service that monitors and limits the memory usage of
application processes using Linux cgroups (v2). It provides a mechanism to
prevent individual apps from consuming excessive amounts of system memory, which
could otherwise lead to system-wide memory pressure and aggressive Out-Of-Memory
(OOM) killing of critical processes.

## How it Works

### Architecture

MemoryLimiter consists of a Java component within `system_server` and a native
component linked via JNI.

1.  **Java Layer (`MemoryLimiter.java`)**: Manages the high-level logic,
    including process state tracking and configuration. It communicates with the
    `ActivityManagerService` (AMS) to stay informed about process lifecycle
    events and state changes.
2.  **JNI Layer (`com_android_server_am_MemoryLimiter.cpp`)**: Interfaces with
    the Linux kernel's cgroup filesystem. It sets limits and uses `inotify` to
    monitor cgroup event files for limit breaches.
3.  **Cgroups**: MemoryLimiter leverages the `memory.high` and `memory.swap.max`
    attributes in the `memory` controller of cgroups v2.
    -   `memory.high`: A soft limit. When exceeded, the process is throttled and
        the kernel attempts to reclaim memory from it.
    -   `memory.swap.max`: Limits the amount of swap space the process can use.

### Process Monitoring

Only application processes (UID >= 10000) are monitored by default. System
processes are generally exempt to ensure core system stability.

MemoryLimiter assigns memory limits based on the process's current state:

-   **Visible Processes**: Processes that are currently perceptible to the user
    (e.g., foreground activities, foreground services, or otherwise
    jank-perceptible states).

-   **Not Visible Processes**: Background processes that are not currently
    interacting with or visible to the user.

When a process exceeds its assigned `memory.high` limit, the native layer
notifies the Java layer. This event can be used to trigger debugging actions,
such as capturing a memory profile or logging an anomaly to `statsd`.

## Configuration

MemoryLimiter is configured via an XML file located on the vendor partition.

-   **File Path**: `/vendor/etc/memory-limiter-config.xml`
-   **Default Configuration**: If no config file is found, it defaults to:
    -   Visible processes: 50% of total available RAM.
    -   Not Visible processes: 25% of total available RAM.

### XML Format

The configuration file follows the schema defined in
`memory-limiter-config.xsd`.

```xml
<MemoryLimiterConfig>
    <version>1</version>
    <visible>50</visible>
    <notVisible>25</notVisible>
</MemoryLimiterConfig>
```

-   **version**: A positive integer identifying the configuration version.
-   **visible**: Percentage (1-100) of total memory allowed for visible
    processes.
-   **notVisible**: Percentage (1-100) of total memory allowed for non-visible
    processes.

### Modifying Configuration

To change the system-wide limits:

1.  Modify `/vendor/etc/memory-limiter-config.xml`.
2.  Reboot the device or restart `system_server` for the changes to take effect.

## Shell Commands

The `am memory-limiter` command allows developers and testers to interact with
the service at runtime.

### Command Usage

```bash
am memory-limiter <SUBCOMMAND>
```

### Subcommands

#### `status`

Reports the current operational status of the MemoryLimiter.

```bash
$ adb shell am memory-limiter status
Memory limiter
  enabled                  monitoring=true          ignored=none
  visibleMem=1948MB        visibleSwap=974MB        notVisibleMem=974MB      notVisibleSwap=487MB
  started=36               watched=36               watch-failed=0           events=0
  processes=36             process-hwm=36
```

-   **monitoring**: Whether the limiter is actively watching processes.
-   **visibleMem / notVisibleMem**: The calculated absolute memory limits for
    each state.
-   **events**: The number of times a process has exceeded its limit.
-   **processes**: Current number of processes being monitored.

#### `ignore`

Temporarily excludes a UID or all processes from being limited. This is useful
for performance testing or when a specific app needs to be allowed to exceed its
limits without interference.

```bash
# Ignore a specific UID
$ adb shell am memory-limiter ignore 10087

# Ignore all processes (effectively disables limiting)
$ adb shell am memory-limiter ignore all

# Resume normal operation
$ adb shell am memory-limiter ignore none
```

#### `manual`

Overrides the calculated limits for a specific process (by PID) with a custom
percentage.

```bash
# Set a 10% limit for PID 1234
$ adb shell am memory-limiter manual 1234 10

# Remove the manual override for PID 1234
$ adb shell am memory-limiter manual 1234 none
```

**Note**: Manual overrides are applied to the process at its current lifecycle
state. If the process is restarted, or changes state (e.g. goes from background
to foreground), it will return to the configured limits based on its new state.
This is unless `memory-limiter ignore` is also in effect.
