package com.logvex.crystal.variant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers the optional features this build carries.
 *
 * <p>Features are listed by class name in a classpath resource, {@code logvex-features.txt}, one
 * per line ({@code #} starts a comment). The standard build ships an empty manifest; the Overseer
 * build ships one naming its administration feature. The core reads that resource and instantiates
 * whatever it names, so the core never imports a feature class and the two editions stay genuinely
 * independent.
 *
 * <p>Instantiation uses the no-argument constructor. A class that cannot be loaded or constructed
 * is logged and skipped rather than failing the client: a broken optional feature must never stop
 * the base client from starting.
 */
public final class FeatureRegistry {

    private static final Logger LOG = LoggerFactory.getLogger("LogvexCrystal");
    private static final String MANIFEST = "/logvex-features.txt";

    private final List<ClientFeature> features;
    private final Variant variant;

    private FeatureRegistry(List<ClientFeature> features, Variant variant) {
        this.features = List.copyOf(features);
        this.variant = variant;
    }

    /** Loads the features named in this build's manifest. Never throws. */
    public static FeatureRegistry discover() {
        List<String> classNames = readManifest();
        List<ClientFeature> loaded = new ArrayList<>();
        for (String name : classNames) {
            try {
                Class<?> type = Class.forName(name);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (instance instanceof ClientFeature feature) {
                    loaded.add(feature);
                } else {
                    LOG.warn("Feature '{}' does not implement ClientFeature; skipped", name);
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.warn("Feature '{}' could not be loaded; skipped", name, e);
            }
        }
        Variant variant = loaded.isEmpty() ? Variant.STANDARD : Variant.OVERSEER;
        return new FeatureRegistry(loaded, variant);
    }

    private static List<String> readManifest() {
        List<String> names = new ArrayList<>();
        try (InputStream in = FeatureRegistry.class.getResourceAsStream(MANIFEST)) {
            if (in == null) {
                return names;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        names.add(trimmed);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read {}; running with no optional features", MANIFEST, e);
        }
        return names;
    }

    /** The loaded features, in manifest order. */
    public List<ClientFeature> all() {
        return features;
    }

    /** True when this build carries no optional features (the standard client). */
    public boolean isEmpty() {
        return features.isEmpty();
    }

    /** The variant implied by what loaded. */
    public Variant variant() {
        return variant;
    }

    /** True when any loaded feature provides {@code capability}. */
    public boolean has(ClientFeature.Capability capability) {
        return features.stream().anyMatch(f -> f.capability() == capability);
    }
}
