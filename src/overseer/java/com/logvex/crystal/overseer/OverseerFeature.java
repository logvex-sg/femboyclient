package com.logvex.crystal.overseer;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.notification.NotificationManager;
import com.logvex.crystal.remote.controller.InstanceRecord;
import com.logvex.crystal.remote.controller.RemoteController;
import com.logvex.crystal.release.Edition;
import com.logvex.crystal.release.FleetUpdateModel;
import com.logvex.crystal.release.ReleaseCatalog;
import com.logvex.crystal.variant.ClientFeature;
import com.logvex.crystal.variant.FeatureHost;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Overseer edition's administration feature.
 *
 * <p>It owns the {@link RemoteController} lifecycle: it starts the controller when the Overseer
 * module is enabled, stops it when the module is disabled or the client shuts down, and exposes a
 * dashboard screen and a fleet HUD element. Nothing here is referenced by the core; the core finds
 * this class only because the Overseer build lists it in {@code logvex-features.txt}.
 *
 * <p>The controller binds a loopback TCP socket. Starting it is explicit (the module's toggle), so
 * a user who never enables Overseer never opens a port.
 */
public final class OverseerFeature implements ClientFeature {

    private static final Logger LOG = LoggerFactory.getLogger("LogvexCrystal");

    private FeatureHost host;
    private OverseerModule module;
    private RemoteController controller;
    private OverseerHudElement hudElement;
    private ReleaseCatalog catalog = ReleaseCatalog.empty();
    private boolean wasEnabled;

    @Override
    public String id() {
        return "overseer";
    }

    @Override
    public String displayName() {
        return "Overseer administration";
    }

    @Override
    public Capability capability() {
        return Capability.REMOTE_ADMIN;
    }

    @Override
    public void start(FeatureHost host) {
        this.host = host;
        this.module = new OverseerModule();
        host.modules().register(module);
        this.hudElement = new OverseerHudElement(this);
        host.hud().register(hudElement);
        LOG.info("Overseer feature ready (module registered, fleet HUD added)");
    }

    @Override
    public void tick() {
        if (module == null) {
            return;
        }
        boolean enabled = module.isEnabled();
        if (enabled != wasEnabled) {
            if (enabled) {
                startController();
            } else {
                stopController();
            }
            wasEnabled = enabled;
        }
    }

    private void startController() {
        if (controller != null) {
            return;
        }
        try {
            controller = RemoteController.standard("overseer");
            int port = controller.start(module.port());
            LOG.info("Overseer controller listening on 127.0.0.1:{}", port);
            notify("Overseer listening on port " + port, false);
        } catch (IOException e) {
            LOG.error("Overseer controller failed to start", e);
            notify("Overseer failed to start: " + e.getMessage(), true);
            controller = null;
        }
    }

    private void stopController() {
        if (controller == null) {
            return;
        }
        try {
            controller.close();
            LOG.info("Overseer controller stopped");
        } catch (Throwable t) {
            LOG.warn("Overseer controller shutdown failed", t);
        } finally {
            controller = null;
        }
    }

    private void notify(String message, boolean warning) {
        CrystalClient client = CrystalClient.get();
        if (client == null) {
            return;
        }
        NotificationManager notifications = client.notifications();
        if (notifications == null) {
            return;
        }
        if (warning) {
            notifications.warn(message);
        } else {
            notifications.info(message);
        }
    }

    @Override
    public void stop() {
        stopController();
    }

    /** The live controller, or null when Overseer is not enabled. */
    public RemoteController controller() {
        return controller;
    }

    /** The Overseer module, so the dashboard can read its settings. */
    public OverseerModule module() {
        return module;
    }

    /** The feature host, so the dashboard can open screens and read the theme. */
    public FeatureHost host() {
        return host;
    }

    /** Replaces the release catalog the fleet-update view selects from. */
    public void setReleaseCatalog(ReleaseCatalog catalog) {
        this.catalog = catalog == null ? ReleaseCatalog.empty() : catalog;
    }

    /** The release catalog currently in use. */
    public ReleaseCatalog releaseCatalog() {
        return catalog;
    }

    /**
     * How each paired instance stands against the known releases. The dashboard renders this; the
     * decision rules live in the shared {@link FleetUpdateModel}, so this method only maps the live
     * controller's records into the model's input.
     */
    public List<FleetUpdateModel.Decision> fleetUpdateDecisions() {
        FleetUpdateModel model = new FleetUpdateModel(Edition.OVERSEER, catalog,
                com.logvex.crystal.remote.protocol.ProtocolVersion.MIN,
                com.logvex.crystal.remote.protocol.ProtocolVersion.CURRENT);
        List<FleetUpdateModel.Member> members = new ArrayList<>();
        if (controller != null) {
            for (InstanceRecord record : controller.instances()) {
                members.add(new FleetUpdateModel.Member(
                        record.instanceId(), record.minecraftVersion(), record.clientVersion()));
            }
        }
        return model.decide(members);
    }
}
