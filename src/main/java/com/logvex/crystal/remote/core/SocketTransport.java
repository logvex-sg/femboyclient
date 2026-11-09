package com.logvex.crystal.remote.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link RemoteTransport} over a TCP socket, optionally upgraded to TLS by
 * {@link TransportFactory}.
 *
 * <p>It owns exactly one socket and closes it once. The streams are read/written by the link's
 * reader and writer threads, never the game thread, which is the contract that keeps a slow or
 * dead peer from stalling Minecraft.
 *
 * <p>Minecraft-independent.
 */
public final class SocketTransport implements RemoteTransport {

    private final Socket socket;
    private final boolean secure;
    private final String description;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SocketTransport(Socket socket, boolean secure, String description) {
        this.socket = socket;
        this.secure = secure;
        this.description = description == null ? "socket" : description;
    }

    @Override
    public InputStream input() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public OutputStream output() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public String describe() {
        return description;
    }

    @Override
    public boolean secure() {
        return secure;
    }

    @Override
    public boolean open() {
        return !closed.get() && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // teardown
            }
        }
    }

    /** The remote address, for logs; empty when the socket is already closed. */
    public String remoteAddress() {
        return socket.getRemoteSocketAddress() == null ? "" : socket.getRemoteSocketAddress().toString();
    }
}
