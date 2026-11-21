# JavaLand Kernel

The kernel layer provides reusable JVM execution and notebook protocol capabilities. The first migrated implementation currently lives in `jupyter/`.

Planned separation (incremental, not one-module-per-concept by default):
- Jupyter protocol and transport
- Persistent JShell execution/session runtime
- Dependency resolution and JBang integration
- Completion and inspection
- MIME display/renderers
- Optional framework integrations

See [Jupyter kernel](jupyter/README.md) for the current implementation and migration caveats.
