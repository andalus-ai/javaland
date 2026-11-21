# JavaLand Execution Runtime

The `:runtime:execution` module provides JavaLand's reusable Java execution-session API.

## Public API

`tech.kayys.javaland.runtime.JavaExecutionSession` owns one persistent JDK JShell session:

- Evaluate one or multiple Java snippets across cells.
- Check source completeness using JShell's source analysis.
- Configure startup source and compiler options.
- Add JARs or directories to the active class path.
- Interrupt evaluation and close the session.
- Expose the JDK JShell instance for editor integrations such as completion.

```java
try (var session = JavaExecutionSession.create()) {
    session.eval("int answer = 40;");
    Object result = session.eval("answer + 2");
    System.out.println(result);
}
```

## Implementation and dependencies

The execution engine is implemented directly on the JDK `jdk.jshell` API.
This module has no JJava dependency and no project dependency on
`:kernel:jupyter`. It can be consumed by other JavaLand runtimes and tools
without adopting the Jupyter protocol.

The Jupyter kernel is an adapter that depends on this module. It remains
responsible for Jupyter messaging, MIME display, and notebook-specific behavior.

Session operations are serialized because JShell is not thread-safe.
Interruption is deliberately callable from another thread and delegates to
`JShell.stop()`.

## Build

From the repository root:

```bash
gradle :runtime:execution:test :runtime:execution:build
```

The repository currently does not include a Gradle wrapper, so use an installed
Gradle version compatible with the project and JDK 25.
