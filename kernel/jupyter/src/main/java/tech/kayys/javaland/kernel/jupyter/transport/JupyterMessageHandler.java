package tech.kayys.javaland.kernel.jupyter.transport;

import java.util.List;

/** Adapts verified protocol requests to kernel behavior and response/event messages. */
@FunctionalInterface
public interface JupyterMessageHandler {
    List<JupyterOutboundMessage> handle(JupyterInboundMessage request) throws Exception;
}
