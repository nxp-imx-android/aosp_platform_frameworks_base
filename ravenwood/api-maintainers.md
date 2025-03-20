# Ravenwood for API Maintainers

By default, Android APIs aren’t opted-in to Ravenwood, and they default to throwing when called under the Ravenwood environment.

To opt-in to supporting an API under Ravenwood, you can use the inline annotations documented below to customize your API behavior when running under Ravenwood.  Because these annotations are inline in the relevant platform source code, they serve as valuable reminders to future API maintainers of Ravenwood support expectations.

> **Note:** to ensure that API teams are well-supported during early Ravenwood onboarding, the Ravenwood team is manually maintaining an allow-list of classes that are able to use Ravenwood annotations.  Please reach out to ravenwood@ so we can offer design advice and allow-list your APIs.

These Ravenwood-specific annotations have no bearing on the status of an API being public, `@SystemApi`, `@TestApi`, `@hide`, etc.  Ravenwood annotations are an orthogonal concept that are only consumed by the internal `hoststubgen` tool during a post-processing step that generates the Ravenwood runtime environment.  Teams that own APIs can continue to refactor opted-in `@hide` implementation details, as long as the test-visible behavior continues passing.

As described in our Guiding Principles, when a team opts-in an API, we’re requiring that they bring along “bivalent” tests (such as the relevant CTS) to validate that Ravenwood behaves just like a physical device.  At the moment this means adding the bivalent tests to relevant `TEST_MAPPING` files to ensure they remain consistently passing over time.  These bivalent tests are important because they progressively provide the foundation on which higher-level unit tests place their trust.

## Opt-in to supporting a single method while other methods remained opt-out

```
@RavenwoodKeepPartialClass
public class MyManager {
    @RavenwoodKeep
    public static String modeToString(int mode) {
        // This method implementation runs as-is on both devices and Ravenwood
    }

    public static void doComplex() {
        // This method implementation runs as-is on devices, but because there
        // is no method-level annotation, and the class-level default is
        // “keep partial”, this method is not supported under Ravenwood and
        // will throw
    }
}
```

## Opt-in an entire class with opt-out of specific methods

```
@RavenwoodKeepWholeClass
public class MyStruct {
    public void doSimple() {
        // This method implementation runs as-is on both devices and Ravenwood,
        // implicitly inheriting the class-level annotation
    }

    @RavenwoodThrow
    public void doComplex() {
        // This method implementation runs as-is on devices, but the
        // method-level annotation overrides the class-level annotation, so
        // this method is not supported under Ravenwood and will throw
    }
}
```

## Replace a complex method when under Ravenwood

```
@RavenwoodKeepWholeClass
public class MyStruct {
    @RavenwoodReplace
    public void doComplex() {
        // This method implementation runs as-is on devices, but the
        // implementation is replaced/substituted by the
        // doComplex$ravenwood() method implementation under Ravenwood
    }

    private void doComplex$ravenwood() {
        // This method implementation only runs under Ravenwood.
        // The visibility of this replacement method does not need to match
        // the original method, so it's recommmended to always use
        // private methods so that these methods won't be accidentally used
        // by unexpected users.
    }
}
```

## Redirect a complex method to a separate class when under Ravenwood

```
@RavenwoodKeepPartialClass
@RavenwoodRedirectionClass("MyComplexClass_ravenwood")
public class MyComplexClass {
    @RavenwoodRedirect
    public void doComplex(int i, int j, int k) {
        // This method implementation runs as-is on devices, but calls to this
        // method is redirected to MyComplexClass_ravenwood#doComplex()
        // under Ravenwood.
    }

    @RavenwoodRedirect
    public static void staticDoComplex(int i, int j, int k) {
        // This method implementation runs as-is on devices, but calls to this
        // method is redirected to MyComplexClass_ravenwood#staticDoComplex()
        // under Ravenwood.
    }

/**
 * This class is only available in the Ravenwood environment.
 */
class MyComplexClass_ravenwood {
    public static void doComplex(MyComplexClass obj, int i, int j, int k) {
        // Because the original method is a non-static method, the current
        // object instance of the original method (the "this" reference)
        // will be passed over as the first argument of the redirection method,
        // with the remaining arguments to follow.
    }

    public static void staticDoComplex(int i, int j, int k) {
        // Because the original method is a static method, there is no current
        // object instance, so the parameter list of the redirection method
        // is the same as the original method.
    }
}
```

## General strategies for side-stepping tricky dependencies

The “replace” strategy described above is quite powerful, and can be used in creative ways to sidestep tricky underlying dependencies that aren’t ready yet.

For example, consider a constructor or static initializer that relies on unsupported functionality from another team.  By factoring the unsupported logic into a dedicated method, that method can then be replaced under Ravenwood to offer baseline functionality.

## Strategies for JNI

At the moment, JNI support is considered "experimental". To ensure that teams are well-supported, for any JNI usage, please reach out to ravenwood@ so we can offer design advice and help you onboard native methods. If a native method can be trivially re-implemented in pure-Java, using the replacement or redirection mechanisms described above is also a viable option.

The main caveat regarding JNI support on Ravenwood is due to how native code is built for Ravenwood. Unlike Java/Kotlin code where Ravenwood directly uses the same intermediate artifacts when building the real target for Android, Ravenwood uses the "host" variant of native libraries. The "host" variant of native libraries usually either don't exist, are extremely limited, or behave drastically different compared to the Android variant.
