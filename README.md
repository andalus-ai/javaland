# JavaLand

JavaLand is the Java-first interactive developer environment for the Andalus ecosystem.

It provides a Jupyter-compatible Java kernel built around the modern JDK JShell APIs, with JBang-oriented installation and dependency workflows. JavaLand is designed to work with ordinary Java libraries independently; integrations for Aljabr, AQLI, and Tafkir are optional ecosystem extensions.

## Goals

- A persistent JVM-backed JShell session for notebook cells.
- Jupyter messaging, execution, completion, inspection, interrupt, and restart support.
- JBang-first installation, dependency resolution, script interoperability, and reproducible examples.
- Rich notebook output through extensible MIME renderers.
- A stable extension boundary for Java libraries and the Andalus AI Triad.
- Clear separation between protocol, session lifecycle, Java execution, dependency management, and framework integrations.

## Andalus AI Triad integrations

| Project | Responsibility | JavaLand integration |
| --- | --- | --- |
| [Aljabr](https://github.com/andalus-ai/aljabr) | Tensor, ML and compute foundation | Optional tensor/model display and compute inspection |
| [AQLI](https://github.com/andalus-ai/aqli) | Inference and serving runtime | Optional model inference and runtime inspection |
| [Tafkir](https://github.com/andalus-ai/javaland) | Training and adaptation | Optional training, dataset, evaluation and experiment workflows |

JavaLand must not own or duplicate any of these projects' domain logic.

## Current migration status

The initial kernel implementation is being migrated from Tafkir's former Jupyter integration. The first migration preserves the existing implementation and its tests before extracting framework-specific behavior into optional extensions. Until that extraction is complete, some implementation packages, build coordinates, and notebook helpers may still carry Tafkir naming or dependencies.

The migration is intentionally staged:
1. Preserve the working kernel implementation and its operational tooling.
2. Establish JavaLand as the owner of the Jupyter protocol and generic JShell runtime.
3. Separate Tafkir-specific helpers and dependencies behind an optional integration.
4. Add independent Aljabr and AQLI integrations using their published APIs.
5. Make JBang the consistent developer-facing installation and launch experience.

## Development

The kernel currently targets JDK 25 and uses JShell plus the JJava Jupyter protocol implementation. See the kernel module README for current build, installation, and diagnostic instructions.

## Architecture principles

- Separation of concerns across protocol, execution, session lifecycle, dependencies, display, and integrations.
- SOLID applied proportionally; avoid abstractions without a concrete variation point.
- One source of truth for runtime configuration and framework behavior.
- Depend on published public APIs, not implementation details from sibling repositories.
- Keep the generic Java kernel useful without any Andalus AI dependency.
- Document implemented capabilities separately from roadmap intentions.

## License

Apache License 2.0.