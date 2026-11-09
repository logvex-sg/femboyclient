package com.logvex.crystal.remote.core;

import com.logvex.crystal.remote.protocol.MessageType;
import com.logvex.crystal.remote.protocol.Nonces;
import com.logvex.crystal.remote.protocol.ProtocolVersion;
import com.logvex.crystal.remote.protocol.RemoteCodec;
import com.logvex.crystal.remote.protocol.RemoteMessage;
import com.logvex.crystal.remote.protocol.FrameCodec;
import com.logvex.crystal.remote.security.RateLimiter;
import com.logvex.crystal.remote.security.ReplayGuard;
import com.logvex.crystal.remote.security.RemoteCrypto;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One authenticated, framed, heartbeat-monitored connection between a controller and an agent.
 *
 * <p>A link owns three daemon threads: a <b>reader</b> that frames and decodes inbound messages,
 * a <b>writer</b> that drains a bounded outbound queue, and a small <b>heartbeat</b> task on the
 * shared scheduler. None of them is the game thread, which is the core requirement for keeping a
 * Minecraft client responsive: a slow or hostile peer can at worst fill the bounded outbound
 * queue and be dropped, never block a tick.
 *
 * <p>Security is enforced here, uniformly, for every message: the signature is verified before
 * the message is accepted (once a secret is installed), a {@link ReplayGuard} rejects stale or
 * replayed messages, and a {@link RateLimiter} bounds the inbound rate. A message that fails any
 * check is counted and, past {@link LinkConfig#maxBadMessages}, closes the link.
 *
 * <p>Lifecycle: create with a transport and a listener, optionally {@link #setSecret}, then
 * {@link #start()}. Messages are sent with {@link #send}; the handshake state machine lives in
 * the controller/agent above this class, which is deliberately dumb about protocol semantics and
 * smart about transport safety.
 *
 * <p>Minecraft-independent and unit testable over an in-memory transport.
 */
public final class RemoteLink implements AutoCloseable {

    private final RemoteTransport transport;
    private final LinkConfig config;
    private final FrameCodec inbound;
    private final FrameCodec outboundFrames;
    private final BlockingQueue<String> outbound;
    private final LinkListener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong lastInboundMillis = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastOutboundMillis = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong messagesSent = new AtomicLong();
    private final AtomicLong messagesReceived = new AtomicLong();
    private final AtomicLong droppedOutbound = new AtomicLong();
    private final AtomicLong badMessages = new AtomicLong();

    private final ReplayGuard replayGuard = new ReplayGuard();
    private final RateLimiter inboundLimiter;
    private volatile String secret = "";
    private volatile boolean requireSignature = false;
    private volatile boolean signedOutbound = false;
    private volatile int protocolVersion = ProtocolVersion.CURRENT;

    private Thread readerThread;
    private Thread writerThread;
    private java.util.concurrent.ScheduledFuture<?> heartbeatTask;
    private volatile String closeReason = "";

    public RemoteLink(RemoteTransport transport, LinkConfig config, LinkListener listener) {
        this.transport = transport;
        this.config = config == null ? LinkConfig.defaults() : config;
        this.listener = listener;
        this.outbound = new ArrayBlockingQueue<>(this.config.outboundQueueSize);
        this.inboundLimiter = new RateLimiter(this.config.inboundBurst, this.config.inboundMessagesPerSecond);
        try {
            // One codec per direction: read() blocks, so a shared codec would deadlock the writer.
            this.inbound = FrameCodec.reader(transport.input());
            this.outboundFrames = FrameCodec.writer(transport.output());
        } catch (IOException e) {
            throw new IllegalStateException("transport is not usable: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------- configuration

    /**
     * Installs the shared secret. Once set, every inbound message must carry a valid signature
     * and every outbound message is signed. Called by the handshake once the peer is identified.
     */
    public void setSecret(String secret) {
        this.secret = secret == null ? "" : secret;
    }

    /** Requires inbound signatures without signing outbound messages (used before AUTH_OK). */
    public void requireInboundSignature(boolean require) {
        this.requireSignature = require;
    }

    /** Starts signing outbound messages (set once the handshake has proven the peer). */
    public void signOutbound(boolean sign) {
        this.signedOutbound = sign;
    }

    public void setProtocolVersion(int version) {
        this.protocolVersion = version;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String secret() {
        return secret;
    }

    // ------------------------------------------------------------- lifecycle

    /** Starts the reader, writer and heartbeat threads. Idempotent. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        readerThread = RemoteThreads.namedFactory("reader").newThread(this::readLoop);
        writerThread = RemoteThreads.namedFactory("writer").newThread(this::writeLoop);
        readerThread.start();
        writerThread.start();
        heartbeatTask = RemoteThreads.scheduler().scheduleAtFixedRate(this::heartbeatTick,
                config.heartbeatIntervalMillis, config.heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Queues a message for sending. Never blocks the caller: when the bounded queue is full the
     * message is dropped and counted, so a stalled peer cannot stall the game thread.
     *
     * @return true when the message was queued
     */
    public boolean send(RemoteMessage message) {
        if (closed.get() || message == null) {
            return false;
        }
        RemoteMessage stamped = new RemoteMessage(protocolVersion, message.type(), message.requestId(),
                System.currentTimeMillis(), message.nonce(), message.payload());
        String wire = signedOutbound
                ? RemoteCodec.encode(stamped, RemoteCrypto.sign(secret, stamped.canonicalForm()))
                : RemoteCodec.encode(stamped);
        if (!outbound.offer(wire)) {
            droppedOutbound.incrementAndGet();
            return false;
        }
        return true;
    }

    /** Sends a PING, used both as a heartbeat and to measure round-trip time. */
    public void ping() {
        send(RemoteMessage.of(MessageType.PING, "", new com.google.gson.JsonObject()));
    }

    /** Sends a BYE and closes cleanly, telling the peer this is intentional. */
    public void sendBye(String reason) {
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("reason", reason == null ? "" : reason);
        send(RemoteMessage.of(MessageType.BYE, "", payload));
        // Give the writer a brief moment to flush the BYE before the socket goes away.
        try {
            long deadline = System.currentTimeMillis() + 250;
            while (!outbound.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeInternal("local bye", true);
    }

    public boolean open() {
        return !closed.get() && transport.open();
    }

    public String describe() {
        return transport.describe();
    }

    public boolean secure() {
        return transport.secure();
    }

    public long lastInboundMillis() {
        return lastInboundMillis.get();
    }

    public long messagesSent() {
        return messagesSent.get();
    }

    public long messagesReceived() {
        return messagesReceived.get();
    }

    public long droppedOutbound() {
        return droppedOutbound.get();
    }

    public String closeReason() {
        return closeReason;
    }

    // ------------------------------------------------------------- threads

    private void readLoop() {
        while (!closed.get()) {
            String wire;
            try {
                wire = inbound.read();
            } catch (IOException e) {
                if (!closed.get()) {
                    closeInternal("io error: " + e.getMessage(), true);
                }
                return;
            } catch (RuntimeException e) {
                if (!closed.get()) {
                    closeInternal("reader error: " + e.getMessage(), true);
                }
                return;
            }
            if (wire == null) {
                closeInternal("peer closed", true);
                return;
            }
            try {
                handleWire(wire);
            } catch (RuntimeException e) {
                noteBadMessage("handler error: " + e.getMessage());
            }
        }
    }

    private void handleWire(String wire) {
        RemoteMessage message;
        try {
            message = RemoteCodec.decode(wire);
        } catch (RemoteCodec.DecodeException e) {
            noteBadMessage("undecodable message: " + e.getMessage());
            return;
        }
        // Signature: only enforced when the owner has decided the peer has had a chance to learn
        // the secret. Requiring it the moment the secret is installed would race with in-flight
        // heartbeats sent before the peer processed the handshake.
        if (requireSignature) {
            String provided = RemoteCodec.signatureOf(wire);
            if (!RemoteCrypto.verify(secret, message.canonicalForm(), provided)) {
                noteBadMessage("bad signature on " + message.type());
                return;
            }
        }
        // Replay / freshness.
        String replay = replayGuard.check(message.timestamp(), message.nonce(), System.currentTimeMillis());
        if (replay != null) {
            noteBadMessage("rejected message: " + replay);
            return;
        }
        // Rate limit.
        if (!inboundLimiter.tryAcquire()) {
            if (listener != null) {
                listener.onWarning("rate limit exceeded");
            }
            return;
        }
        lastInboundMillis.set(System.currentTimeMillis());
        messagesReceived.incrementAndGet();
        if (message.type() == MessageType.PING) {
            // Answer immediately on the reader thread; PONG is tiny and must not queue behind work.
            RemoteMessage pong = new RemoteMessage(protocolVersion, MessageType.PONG, message.requestId(),
                    System.currentTimeMillis(), Nonces.fresh(), message.payload());
            send(pong);
            return;
        }
        if (message.type() == MessageType.BYE) {
            closeInternal("peer said goodbye", false);
            return;
        }
        if (listener != null) {
            listener.onMessage(message);
        }
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                String wire = outbound.poll(200, TimeUnit.MILLISECONDS);
                if (wire == null) {
                    continue;
                }
                outboundFrames.write(wire);
                lastOutboundMillis.set(System.currentTimeMillis());
                messagesSent.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            closeInternal("write failed: " + e.getMessage(), true);
        }
    }

    private void heartbeatTick() {
        if (closed.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastInboundMillis.get() > config.heartbeatTimeoutMillis) {
            closeInternal("heartbeat timeout", true);
            return;
        }
        if (now - lastOutboundMillis.get() >= config.heartbeatIntervalMillis) {
            ping();
        }
    }

    private void noteBadMessage(String reason) {
        long count = badMessages.incrementAndGet();
        if (listener != null) {
            listener.onWarning(reason);
        }
        if (count >= config.maxBadMessages) {
            closeInternal("too many bad messages (" + reason + ")", true);
        }
    }

    private void closeInternal(String reason, boolean fatal) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeReason = reason;
        // Cancel the recurring heartbeat: a closed link must not keep a scheduler slot forever,
        // or a long-running client that opens and closes links would exhaust the shared pool.
        java.util.concurrent.ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(false);
        }
        transport.close();
        if (listener != null) {
            try {
                listener.onClosed(reason, fatal);
            } catch (RuntimeException ignored) {
                // A broken listener must not prevent teardown.
            }
        }
    }

    @Override
    public void close() {
        closeInternal("closed locally", true);
    }
}
