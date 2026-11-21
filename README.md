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
| [Tafkir](https://github.com/andalus-ai/tafkir) | Training and adaptation | Optional training, dataset, evaluation and experiment workflows |

JavaLand must not own or duplicate any of these projects' domain logic.

## Architecture

- `:runtime:execution` owns persistent JDK JShell sessions.
- `:protocol:jupyter` owns Jupyter message/configuration/signature primitives.
- `:transport:jupyter-zmq` owns ZeroMQ sockets, channel endpoints, multipart framing, and routing identities.
- `:kernel:jupyter` adapts Jupyter requests to execution sessions and notebook renderers.
- Optional ecosystem integrations consume published public APIs without coupling the generic Java kernel to AI frameworks.

The protocol module is intentionally independent of JShell and the kernel adapter. Its codec owns the signed JSON message frames; `:transport:jupyter-zmq` owns ZeroMQ framing and socket lifecycle. The kernel will coordinate the two layers.

## Migration status

The reusable execution session is extracted into `:runtime:execution` and implemented directly on JDK JShell. The initial JavaLand-owned Jupyter protocol core is now in `:protocol:jupyter`: connection-file parsing, message envelopes, JSON frame serialization, and HMAC signing/verification.

The reusable execution session is extracted into the independent `:runtime:execution` Gradle module. Its implementation uses the JDK JShell API directly and has no JJava dependency. The `:kernel:jupyter` module consumes this runtime.

The protocol primitives and first JavaLand-owned ZeroMQ transport foundation now exist. The kernel launcher still uses JJava for dispatch and event-loop coordination; the transport module is not yet wired into the kernel.

Remaining work is staged:

1. Add a poller/event-loop abstraction and integrate the new transport with the kernel dispatcher.
2. Replace JJava's kernel lifecycle and message dispatch incrementally, preserving protocol behavior.
3. Move remaining framework-specific display behavior behind optional integrations.
4. Add independent Aljabr and AQLI integrations using their published APIs.
5. Make JBang the consistent developer-facing installation and launch experience.

## Development

The runtime and kernel target JDK 25. The runtime module can be built and tested independently; see its README for details.

## Architecture principles

- Separate protocol, transport, execution, session lifecycle, dependencies, display, and integrations.
- Apply SOLID proportionally; avoid abstractions without a concrete variation point.
- Maintain one source of truth for runtime configuration and framework behavior.
- Depend on published public APIs, not implementation details from sibling repositories.
- Keep the generic Java kernel useful without any Andalus AI dependency.
- Document implemented capabilities separately from roadmap intentions.

## License

Apache License 2.0.
