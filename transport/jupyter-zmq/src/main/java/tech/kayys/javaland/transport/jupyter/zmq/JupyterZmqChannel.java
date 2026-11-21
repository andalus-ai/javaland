package tech.kayys.javaland.transport.jupyter.zmq;

import org.zeromq.SocketType;

/** Standard Jupyter channels and their ZeroMQ socket roles. */
public enum JupyterZmqChannel {
    SHELL(SocketType.ROUTER), CONTROL(SocketType.ROUTER), IOPUB(SocketType.PUB),
    STDIN(SocketType.ROUTER), HEARTBEAT(SocketType.REP);
    private final SocketType socketType;
    JupyterZmqChannel(SocketType socketType) { this.socketType = socketType; }
    SocketType socketType() { return socketType; }
}
