package com.logvex.crystal.scan.world;

/**
 * A named set of scan tunables the user can switch between. A profile is just a name plus a
 * {@link ScanConfig}; applying one copies its values into the module's settings.
 */
public record ScanProfile(String name, ScanConfig config) {

    public ScanProfile {
        name = name == null || name.isBlank() ? "default" : name.trim();
        config = config == null ? ScanConfig.defaults() : config;
    }
}
