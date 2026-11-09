package com.logvex.crystal.commandcenter.quick;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.PerformanceReport;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The production {@link QuickActionContext}: a thin adapter that reads the live Command Center
 * state through suppliers.
 *
 * <p>Every value is pulled lazily on demand and every supplier is null-safe, so the context is
 * cheap to build once at bootstrap and can never throw while the engine evaluates a trigger.
 */
public final class ClientQuickActionContext implements QuickActionContext {

    private final Supplier<ClientStatusModel> status;
    private final Supplier<PerformanceReport> performance;
    private final IntSupplier moduleErrors;
    private final IntSupplier crashes;
    private final BooleanSupplier baritone;
    private final BooleanSupplier aiLoaded;
    private final BooleanSupplier world;

    public ClientQuickActionContext(Supplier<ClientStatusModel> status,
                                    Supplier<PerformanceReport> performance,
                                    IntSupplier moduleErrors, IntSupplier crashes,
                                    BooleanSupplier baritone, BooleanSupplier aiLoaded,
                                    BooleanSupplier world) {
        this.status = safe(status);
        this.performance = safe(performance);
        this.moduleErrors = safeInt(moduleErrors);
        this.crashes = safeInt(crashes);
        this.baritone = safeBool(baritone);
        this.aiLoaded = safeBool(aiLoaded);
        this.world = safeBool(world);
    }

    @Override
    public ClientStatusModel status() {
        return status.get();
    }

    @Override
    public PerformanceReport performance() {
        return performance.get();
    }

    @Override
    public int moduleErrorCount() {
        return moduleErrors.getAsInt();
    }

    @Override
    public int crashCount() {
        return crashes.getAsInt();
    }

    @Override
    public boolean baritoneAvailable() {
        return baritone.getAsBoolean();
    }

    @Override
    public boolean aiLoaded() {
        return aiLoaded.getAsBoolean();
    }

    @Override
    public boolean worldAvailable() {
        return world.getAsBoolean();
    }

    private static <T> Supplier<T> safe(Supplier<T> supplier) {
        return supplier == null ? () -> null : supplier;
    }

    private static IntSupplier safeInt(IntSupplier supplier) {
        return supplier == null ? () -> 0 : supplier;
    }

    private static BooleanSupplier safeBool(BooleanSupplier supplier) {
        return supplier == null ? () -> false : supplier;
    }
}
