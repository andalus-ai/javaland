package tech.kayys.javaland.kernel.jupyter.transport;

import java.util.List;

/** Optional handler contract for messages produced asynchronously during a request. */
public interface JupyterAsyncMessageHandler extends JupyterMessageHandler {
    /** Drains messages that must be sent by the coordinator's ZeroMQ-owning thread. */
    List<JupyterOutboundMessage> drainAsyncMessages();
}
