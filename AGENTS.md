# JavaLand Agent Guidance

## Mission
JavaLand owns the reusable Java interactive development environment: Jupyter protocol integration, JShell execution, session lifecycle, dependency workflows, display, completion, and developer tooling.

## Boundaries
- Keep the generic kernel independent of Aljabr, AQLI, Tafkir, and any other ML framework.
- Framework-specific behavior belongs in optional integrations that consume published public APIs.
- Aljabr owns tensor/compute/ML primitives; AQLI owns inference and serving; Tafkir owns training and adaptation.
- Do not move domain logic into the kernel or duplicate framework APIs for notebook convenience.
- JBang is a first-class distribution and developer workflow, not a replacement for JShell's persistent interactive execution engine.

## Engineering principles
- Separate protocol transport, execution engine, session lifecycle, dependency resolution, display, completion, and integrations.
- Apply SOLID proportionally. Avoid abstractions without a demonstrated variation point.
- Maintain one source of truth for configuration and lifecycle ownership.
- Prefer modern JDK APIs, especially JShell APIs, over custom compilers or interpreters.
- Keep session state isolated between kernel processes; define cleanup for threads, sockets, classloaders, and native resources.
- Treat notebook execution as arbitrary code execution. Never claim sandboxing without actual process/container isolation.
- Preserve compatibility deliberately and document migration/deprecation behavior.

## Current migration caveat
The initial Jupyter implementation was migrated from Tafkir. Some package names, helper behavior, build metadata, and tests still reflect the original Tafkir integration. Do not present those as the final JavaLand public API. Refactor incrementally, preserving working behavior and tests while extracting framework-specific features into optional integrations.

## Verification
- Add focused tests for protocol, execution, session lifecycle, completion, interruption, dependency handling, and MIME output.
- Test Jupyter startup and kernel handshake in a real local environment when possible.
- Verify JBang installation and launch from a clean user environment.
- Keep documentation aligned with tested capabilities; label roadmap items explicitly.
