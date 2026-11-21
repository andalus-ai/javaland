# JavaLand Jupyter Protocol

The `:protocol:jupyter` module contains JavaLand-owned protocol primitives.
It is independent of the JShell execution runtime and does not depend on the
Jupyter kernel adapter or JJava.

## Initial scope

- Jupyter connection-file configuration and validation for TCP and IPC transports.
- Protocol message header and generic message envelope.
- JSON frame encoding and decoding.
- HMAC signing and verification for supported Jupyter signature schemes.
- Binary buffer preservation without forcing a transport dependency.

The codec handles the signed JSON frames in protocol order: header,
parent header, metadata, and content. Binary buffers are carried separately
and are not included in the message signature. ZeroMQ identity frames and the
`<IDS|MSG>` delimiter are transport responsibilities.

The standard Jupyter `hmac-sha256` configuration is supported. Connection keys
are redacted from configuration string representations.

## Module boundary

`protocol:jupyter` has no dependency on `:runtime:execution`. It can be used
by another JavaLand kernel or protocol client without adopting JShell.

The current `:kernel:jupyter` still uses JJava for its ZeroMQ transport and
kernel dispatch. Migration of those runtime responsibilities will be staged
after these protocol primitives are established.

## Build

```bash
gradle :protocol:jupyter:test :protocol:jupyter:build
```

Requires JDK 25 and a Gradle version compatible with the repository.
