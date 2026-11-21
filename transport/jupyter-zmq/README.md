# JavaLand Jupyter ZeroMQ Transport

The `:transport:jupyter-zmq` module owns the ZeroMQ-specific portion of the
Jupyter wire protocol.

- Binds Shell, Control, IOPub, Stdin, and Heartbeat endpoints.
- Owns JeroMQ context and socket lifecycle.
- Preserves ROUTER identities, the `<IDS|MSG>` delimiter, signed JSON frames,
  and trailing binary buffers.
- Exposes raw heartbeat receive/reply operations.

It depends on `:protocol:jupyter` for connection settings and serialized
messages. It does not depend on the kernel dispatcher or JShell runtime.

## Migration boundary

This is the transport foundation, not yet the complete kernel event loop.
Polling, dispatch, execution scheduling, IOPub publication policy, and
coordinated shutdown will be added in the kernel integration stage. The
current launcher continues to use JJava until that integration is ready.

## Build

```bash
gradle :transport:jupyter-zmq:test :transport:jupyter-zmq:build
```

Requires JDK 25 and a compatible Gradle version.
