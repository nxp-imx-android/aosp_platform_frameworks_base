# Ravenwood Gradle Sample

This directory contains a sample gradle project to run host tests on
Ravenwood.

We can't build ravenwood-runtime with gradle, so the runtime must be built
separately with `m ravenwood` before running the test with gradle.

To enable Ravenwood, set `enableRavenwood` to `true`, like so:
```
$ ./gradlew :app:testDebugUnitTest --info -PenableRavenwood=true
```
