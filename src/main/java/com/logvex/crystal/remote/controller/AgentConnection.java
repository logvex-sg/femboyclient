package com.logvex.crystal.remote.controller;

import com.logvex.crystal.remote.core.LinkListener;
import com.logvex.crystal.remote.core.RemoteLink;
import com.logvex.crystal.remote.protocol.CommandRequest;
import com.logvex.crystal.remote.protocol.CommandResult;
import com.logvex.crystal.remote.protocol.MessageType;
import com.logvex.crystal.remote.protocol.Payloads;
import com.logvex.crystal.remote.protocol.ProtocolVersion;
import com.logvex.crystal.remote.protocol.RemoteMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The controller's end of one agent connection: it runs the controller side of the handshake and
 * routes inbound messages to the {@link RemoteController}.
 *
 * <p>Handshake, controller side:
 * <ol>
 *   <li>agent sends {@code HELLO}; the controller checks the protocol version and looks up the
 *       pairing for the instance id,</li>
 *   <li>the controller replies {@code HELLO_ACK} with the negotiated version and a random
 *       challenge,</li>
 *   <li>the agent replies {@code AUTH} with an HMAC over the challenge, keyed by the shared
 *       secret,</li>
 *   <li>the controller verifies and answers {@code AUTH_OK} (or {@code AUTH_FAIL} and closes).</li>
 * </ol>
 * The secret is installed on the link only after a successful AUTH, so an unauthenticated peer
 * can never get a signed message out of the controller.
 *
 * <p>Minecraft-independent; the link runs on daemon threads, so nothing here touches the game.
 */
public final class AgentConnection implements LinkListener {

    private final RemoteController controller;
    private final RemoteLink link;
    private final String remoteDescription;

    private volatile String instanceId = "";
    private volatile String agentName = "";
    private volatile String challenge = "";
    private volatile boolean authenticated;
    private volatile boolean paired;
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();

    public AgentConnection(RemoteController controller, RemoteLink link) {
        this.controller = controller;
        this.link = link;
        this.remoteDescription = link.describe();
    }

    public RemoteLink link() {
        return link;
    }

    public String instanceId() {
        return instanceId;
    }

    public String agentName() {
        return agentName;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public boolean paired() {
        return paired;
    }

    public String remoteDescription() {
        return remoteDescription;
    }

    /** The request ids sent on this connection that have not yet produced a result. */
    public java.util.Set<String> inFlight() {
        return java.util.Set.copyOf(inFlight.keySet());
    }

    // ------------------------------------------------------------- handshake

    @Override
    public void onMessage(RemoteMessage message) {
        // Any well-formed message from an identified agent is proof of life, so refresh the
        // instance's liveness here. Relying on PONG alone is not enough: two peers that both
        // reply to each other's PINGs suppress their own PINGs, so neither would ever see a PONG.
        if (!instanceId.isBlank()) {
            controller.onPeerActivity(this);
        }
        switch (message.type()) {
            case HELLO -> handleHello(message);
            case AUTH -> handleAuth(message);
            case STATUS -> handleStatus(message);
            case STATUS_UPDATE -> handleStatus(message);
            case COMMAND_RESULT -> handleResult(message);
            case PONG -> {
                // Liveness was already refreshed above for every message.
            }
            case ERROR -> controller.onAgentError(this, com.logvex.crystal.remote.protocol.Json.str(
                    message.payload(), "message", "agent error"));
            case AUTH_FAIL -> {
                controller.onAuthFailure(this, com.logvex.crystal.remote.protocol.Json.str(
                        message.payload(), "reason", "authentication failed"));
                link.close();
            }
            default -> {
                // Unknown-but-well-formed messages are ignored rather than fatal, so a newer
                // agent can add a message type without breaking an older controller.
            }
        }
    }

    private void handleHello(RemoteMessage message) {
        int peerVersion = message.protocolVersion();
        instanceId = com.logvex.crystal.remote.protocol.Json.str(message.payload(), "instanceId", "");
        agentName = com.logvex.crystal.remote.protocol.Json.str(message.payload(), "name", instanceId);
        if (instanceId.isBlank()) {
            controller.rejectConnection(this, "HELLO without an instance id");
            link.close();
            return;
        }
        if (!ProtocolVersion.supported(peerVersion)) {
            controller.rejectConnection(this, "incompatible protocol: " + ProtocolVersion.rejection(peerVersion));
            link.close();
            return;
        }
        int negotiated = ProtocolVersion.negotiate(peerVersion);
        link.setProtocolVersion(negotiated);
        var pairing = controller.pairingFor(instanceId);
        if (pairing == null) {
            // The agent is known only by its id: accept the link far enough to show it as
            // awaiting pairing, but do not authenticate it.
            paired = false;
            controller.onAwaitingPairing(this);
            link.send(new RemoteMessage(negotiated, MessageType.HELLO_ACK, message.requestId(),
                    System.currentTimeMillis(), com.logvex.crystal.remote.protocol.Nonces.fresh(),
                    Payloads.helloAck(negotiated, "", controller.name())));
            return;
        }
        paired = true;
        challenge = com.logvex.crystal.remote.protocol.Nonces.fresh();
        // The controller signs from here on (the agent requires it as soon as it sees the
        // challenge), but it does not yet *require* inbound signatures: the agent's next message
        // may be a heartbeat it queued before it processed HELLO_ACK. Inbound signing is turned
        // on only after AUTH verifies, by which point the agent has certainly switched over.
        link.setSecret(pairing.secret());
        link.signOutbound(true);
        controller.onAgentConnected(this);
        link.send(new RemoteMessage(negotiated, MessageType.HELLO_ACK, message.requestId(),
                System.currentTimeMillis(), com.logvex.crystal.remote.protocol.Nonces.fresh(),
                Payloads.helloAck(negotiated, challenge, controller.name())));
    }

    private void handleAuth(RemoteMessage message) {
        if (!paired) {
            controller.onAuthFailure(this, "AUTH before HELLO pairing");
            link.send(RemoteMessage.of(MessageType.AUTH_FAIL, message.requestId(),
                    failPayload("instance is not paired")));
            link.close();
            return;
        }
        String proof = com.logvex.crystal.remote.protocol.Json.str(message.payload(), "proof", "");
        var pairing = controller.pairingFor(instanceId);
        if (pairing == null || !pairing.active()) {
            controller.onAuthFailure(this, "pairing revoked or missing");
            link.send(RemoteMessage.of(MessageType.AUTH_FAIL, message.requestId(),
                    failPayload("pairing revoked")));
            link.close();
            return;
        }
        if (!com.logvex.crystal.remote.security.RemoteCrypto.verify(pairing.secret(),
                "auth:" + challenge, proof)) {
            controller.onAuthFailure(this, "bad auth proof");
            link.send(RemoteMessage.of(MessageType.AUTH_FAIL, message.requestId(),
                    failPayload("authentication failed")));
            link.close();
            return;
        }
        authenticated = true;
        // The agent has now proven it holds the secret, so from here every inbound message must
        // be signed. This is the point at which the handshake is complete in both directions.
        link.requireInboundSignature(true);
        pairing.markSeen(System.currentTimeMillis());
        var session = controller.onAuthenticated(this);
        link.send(new RemoteMessage(link.protocolVersion(), MessageType.AUTH_OK, message.requestId(),
                System.currentTimeMillis(), com.logvex.crystal.remote.protocol.Nonces.fresh(),
                Payloads.authOk(pairing.permissionNames(), java.util.List.of(), session.sessionId())));
    }

    private static com.google.gson.JsonObject failPayload(String reason) {
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("reason", reason);
        return payload;
    }

    // ------------------------------------------------------------- traffic

    private void handleStatus(RemoteMessage message) {
        if (!authenticated) {
            return;
        }
        var snapshot = Payloads.snapshot(message.payload());
        controller.onStatus(this, snapshot);
    }

    private void handleResult(RemoteMessage message) {
        if (!authenticated) {
            return;
        }
        String requestId = message.requestId();
        inFlight.remove(requestId);
        CommandResult result = Payloads.result(requestId, instanceId, message.payload());
        controller.onCommandResult(this, result);
    }

    @Override
    public void onClosed(String reason, boolean fatal) {
        controller.onAgentDisconnected(this, reason, fatal);
    }

    @Override
    public void onWarning(String message) {
        controller.onLinkWarning(this, message);
    }

    // ------------------------------------------------------------- send

    /** Sends a command to this agent and remembers it as in flight. Returns the request id. */
    public String sendCommand(CommandRequest request) {
        inFlight.put(request.requestId(), System.currentTimeMillis());
        link.send(new RemoteMessage(link.protocolVersion(), MessageType.COMMAND, request.requestId(),
                System.currentTimeMillis(), com.logvex.crystal.remote.protocol.Nonces.fresh(),
                request.toPayload()));
        return request.requestId();
    }

    /** Forgets an in-flight request (after a timeout or a cancellation). */
    public void forget(String requestId) {
        inFlight.remove(requestId);
    }
}
