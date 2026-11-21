# JavaLand Execution Runtime

The `:runtime:execution` module provides the reusable Java execution-session API.

## Public API

`tech.kayys.javaland.runtime.JavaExecutionSession` owns one persistent Java execution session:

- Evaluate source across cells.
- Check source completeness.
- Configure startup source and compiler options.
- Add JARs or directories to the active class path.
- Interrupt evaluation and close the session.

```java
try (var session = JavaExecutionSession.create()) {
    session.eval("int answer = 40;");
    Object result = session.eval("answer + 2");
    System.out.println(result);
}
```

The module is independently consumable and has no project dependency on
`:kernel:jupyter`. The Jupyter kernel is an adapter that depends on this module.

## Current implementation boundary

The API is JavaLand-owned and exposes JDK JShell types only. The current
implementation uses JJava's `CodeEvaluator` internally to preserve the
migrated kernel's evaluation and output behavior. This is a transitional
implementation dependency, not part of the public API. A future change can
replace it with a direct JDK JShell implementation without changing consumers.

## Build

From the repository root:

```bash
./gradlew :runtime:execution:test :runtime:execution:build
```

The repository currently does not include a Gradle wrapper, so use an installed
Gradle version compatible with the project when building locally.
