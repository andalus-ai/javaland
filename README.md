# JavaLand

JavaLand is the Java-first interactive developer environment for the Andalus ecosystem.

It provides a Jupyter-compatible Java kernel built around the modern JDK JShell APIs. JavaLand works with ordinary Java libraries independently; integrations for Aljabr, AQLI, and Tafkir are optional extensions.

## Goals

- Persistent JVM-backed JShell sessions for notebook cells.
- Jupyter messaging, execution, completion, inspection, interrupt, and restart support.
- JBang-first installation, dependency resolution, script interoperability, and reproducible examples.
- Rich notebook output through extensible MIME renderers.
- Stable extension boundaries for Java libraries and the Andalus AI projects.
- Clear separation between protocol, session lifecycle, Java execution, dependency management, and framework integrations.

## Ecosystem integrations

| Project | Responsibility | JavaLand integration |
| --- | --- | --- |
| [Aljabr](https://github.com/andalus-ai/aljabr) | Tensor, ML and compute foundation | Optional tensor/model display and compute inspection |
| [AQLI](https://github.com/andalus-ai/aqli) | Inference and serving runtime | Optional model inference and runtime inspection |
| [Tafkir](https://github.com/andalus-ai/javaland) | Training and adaptation | Optional training, dataset, evaluation and experiment workflows |

JavaLand must not own or duplicate any of these projects' domain logic.

## Migration status

The initial implementation originated in an earlier Jupyter integration. Its public Java package, kernel entry point, launcher, and default JShell startup environment are now JavaLand-owned and framework-neutral. Framework-specific notebook helpers are not part of the generic kernel startup path; they will be provided through optional integrations.

The remaining work is staged:
1. Extract reusable JShell execution and session lifecycle APIs from the Jupyter transport adapter.
2. Move any remaining framework-specific display behavior behind optional integrations.
3. Add independent Aljabr and AQLI integrations using their published APIs.
4. Make JBang the consistent developer-facing installation and launch experience.

## Development

The kernel targets JDK 25 and uses JShell plus the JJava Jupyter protocol implementation. See the kernel module README for build, installation, and diagnostic instructions.

## Architecture principles

- Separate protocol, execution, session lifecycle, dependencies, display, and integrations.
- Apply SOLID proportionally; avoid abstractions without a concrete variation point.
- Maintain one source of truth for runtime configuration and framework behavior.
- Depend on published public APIs, not implementation details from sibling repositories.
- Keep the generic Java kernel useful without any Andalus AI dependency.
- Document implemented capabilities separately from roadmap intentions.

## License

Apache License 2.0.
