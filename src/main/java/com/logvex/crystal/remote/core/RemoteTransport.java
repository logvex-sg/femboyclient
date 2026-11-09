package com.logvex.crystal.remote.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * One bidirectional byte channel between a controller and an agent.
 *
 * <p>The protocol layer is written against this interface, never against a socket directly, so
 * the same controller/agent code runs over a plain local socket, a TLS socket, or an in-memory
 * pipe in a test. That is what makes the whole feature testable without a network, and it is what
 * lets {@link com.logvex.crystal.remote.core.TransportFactory} upgrade a connection to TLS without
 * the layers above knowing.
 *
 * <p>Implementations must be safe to close more than once. Minecraft-independent.
 */
public interface RemoteTransport extends AutoCloseable {

    /** The stream this end reads from. */
    InputStream input() throws IOException;

    /** The stream this end writes to. */
    OutputStream output() throws IOException;

    /** A short description for logs, e.g. {@code "tls://192.168.1.5:45871"}. */
    String describe();

    /** True when the channel is encrypted. */
    boolean secure();

    /** True while the channel is believed open. */
    boolean open();

    /** Closes the channel; must be idempotent. */
    @Override
    void close();
}
