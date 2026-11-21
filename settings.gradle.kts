rootProject.name = "javaland"

include(
    ":runtime:execution",
    ":protocol:jupyter",
    ":transport:jupyter-zmq",
    ":kernel:jupyter"
)
