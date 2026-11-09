package com.logvex.crystal.remote.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Length-prefixed framing for a stream of protocol messages.
 *
 * <p>A TCP stream has no message boundaries, so each message is written as a 4-byte big-endian
 * length followed by that many UTF-8 bytes. The length is validated against
 * {@link RemoteCodec#MAX_MESSAGE_BYTES} <em>before</em> any allocation, so a hostile or
 * corrupt peer cannot make the reader allocate an enormous buffer by claiming a huge frame.
 *
 * <p>An instance wraps exactly one direction: construct it with an {@link InputStream} to read
 * frames, or an {@link OutputStream} to write them. It is deliberately not synchronized: the
 * reader and writer each own their own instance on their own thread. Sharing one instance
 * between the two would be a deadlock, because {@link #read()} blocks while holding the monitor
 * and the writer could then never take it.
 *
 * <p>{@link #read()} blocks until a whole frame arrives, the peer closes, or the socket is closed
 * from another thread (which is how a heartbeat timeout interrupts the read). It must not be
 * given a socket with a read timeout: a timeout part-way through a frame would consume some
 * bytes and leave the stream desynced.
 *
 * <p>Minecraft-independent.
 */
public final class FrameCodec {

    private final DataInputStream in;
    private final DataOutputStream out;

    private FrameCodec(InputStream in, OutputStream out) {
        this.in = in == null ? null : new DataInputStream(in);
        this.out = out == null ? null : new DataOutputStream(out);
    }

    /** A reader for the inbound direction of a connection. */
    public static FrameCodec reader(InputStream in) {
        return new FrameCodec(in, null);
    }

    /** A writer for the outbound direction of a connection. */
    public static FrameCodec writer(OutputStream out) {
        return new FrameCodec(null, out);
    }

    /**
     * Reads one frame.
     *
     * @return the decoded string, or null at a clean end of stream
     * @throws IOException when the stream ends mid-frame, a frame exceeds the size limit, or this
     *                     codec was constructed as a writer
     */
    public String read() throws IOException {
        if (in == null) {
            throw new IOException("this frame codec is write-only");
        }
        int length;
        try {
            length = in.readInt();
        } catch (EOFException e) {
            return null;
        }
        if (length < 0 || length > RemoteCodec.MAX_MESSAGE_BYTES) {
            throw new IOException("frame length " + length + " out of range");
        }
        if (length == 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        in.readFully(buffer);
        return new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Writes one frame and flushes it, so the peer sees the message without waiting for more.
     *
     * @throws IOException when the message exceeds the size limit, the write fails, or this codec
     *                     was constructed as a reader
     */
    public void write(String message) throws IOException {
        if (out == null) {
            throw new IOException("this frame codec is read-only");
        }
        if (message == null) {
            return;
        }
        byte[] bytes = RemoteCodec.utf8(message);
        if (bytes.length > RemoteCodec.MAX_MESSAGE_BYTES) {
            throw new IOException("message of " + bytes.length + " bytes exceeds the frame limit");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    /** Closes the underlying stream, ignoring failures (used on teardown). */
    public void close() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
            // teardown
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
            // teardown
        }
    }
}
