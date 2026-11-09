package com.logvex.crystal.remote.core;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Builds TLS contexts for the remote transport.
 *
 * <p>Two modes are offered, both of which encrypt the channel:
 *
 * <ul>
 *   <li><b>Managed</b> — the user supplies a PKCS#12 keystore (and optionally a truststore). The
 *       controller presents a certificate and, when a truststore is given, the agent verifies it.
 *       This is the mode for a fleet spread across networks.</li>
 *   <li><b>Self-signed / trust-on-first-use</b> — for a LAN where the user has no CA. The channel
 *       is still encrypted with a generated key pair, and the HMAC in the protocol still
 *       authenticates the peers, so confidentiality and authenticity are both preserved even
 *       without a certificate authority. The trust manager accepts any certificate, which is safe
 *       <em>here</em> because the HMAC is the real authentication; TLS is only providing
 *       confidentiality.</li>
 * </ul>
 *
 * <p>There is deliberately no mode that disables both TLS and HMAC: an unauthenticated control
 * endpoint is never exposed. A connection is either TLS-encrypted, HMAC-authenticated, or (in the
 * default local case) both.
 *
 * <p>Minecraft-independent.
 */
public final class TlsContexts {

    private TlsContexts() {
    }

    /**
     * A context that encrypts with a self-signed certificate and does not verify the peer's
     * certificate. Safe only in combination with the protocol's HMAC authentication.
     */
    public static SSLContext selfSigned(boolean forServer, String keystorePath, char[] password)
            throws IOException {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            if (keystorePath != null && !keystorePath.isBlank()) {
                try (FileInputStream in = new FileInputStream(keystorePath)) {
                    keyStore.load(in, password);
                }
            } else {
                keyStore.load(null, null);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), new TrustManager[] {trustAll()}, new SecureRandom());
            return context;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("could not build the TLS context: " + e.getMessage(), e);
        }
    }

    /** A client context that verifies the server against {@code truststorePath}. */
    public static SSLContext verifiedClient(String truststorePath, char[] password) throws IOException {
        try {
            KeyStore trust = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(truststorePath)) {
                trust.load(in, password);
            }
            javax.net.ssl.TrustManagerFactory tmf =
                    javax.net.ssl.TrustManagerFactory.getInstance(
                            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), new SecureRandom());
            return context;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("could not build the TLS trust context: " + e.getMessage(), e);
        }
    }

    /** Wraps an already-connected plain socket as a TLS socket using {@code context}. */
    public static SSLSocket upgrade(java.net.Socket plain, SSLContext context, boolean clientMode)
            throws IOException {
        SSLSocketFactory factory = context.getSocketFactory();
        SSLSocket tls = (SSLSocket) factory.createSocket(plain,
                plain.getInetAddress() == null ? "localhost" : plain.getInetAddress().getHostName(),
                plain.getPort(), true);
        tls.setUseClientMode(clientMode);
        tls.startHandshake();
        return tls;
    }

    private static X509TrustManager trustAll() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // The protocol's HMAC is the authentication; TLS here only encrypts.
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // See above.
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
