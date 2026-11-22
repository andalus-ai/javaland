# JavaLand Jupyter ZeroMQ Transport

The `:transport:jupyter-zmq` module owns the ZeroMQ-specific portion of the
Jupyter wire protocol.

- Binds Shell, Control, IOPub, Stdin, and Heartbeat endpoints.
- Owns JeroMQ context and socket lifecycle.
- Preserves ROUTER identities, the `<IDS|MSG>` delimiter, signed JSON frames,
  and trailing binary buffers.
- Multiplexes inbound readiness across all five channels with a context-owned
  JeroMQ poller.

The poller reports ready channels only. It does not decode, dispatch, or execute
messages; those responsibilities belong to the kernel coordinator.

## Migration boundary

The transport is not yet wired into the kernel launcher. Polling and channel
readiness are now available as a foundation for the next kernel integration
slice. The current launcher continues to use JJava until that integration
preserves existing kernel behavior.

## Build

```bash
gradle :transport:jupyter-zmq:test :transport:jupyter-zmq:build
```

Requires JDK 25 and a compatible Gradle version.
