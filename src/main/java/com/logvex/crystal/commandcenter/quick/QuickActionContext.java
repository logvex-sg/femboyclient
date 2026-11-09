package com.logvex.crystal.commandcenter.quick;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.PerformanceReport;

/**
 * The read-only facts a quick action's trigger and conditions are evaluated against.
 *
 * <p>Like the automation engine's context, this is a narrow interface rather than the live
 * client, so the whole quick-action policy is unit testable with a stub and the set of things
 * a workflow can observe is an explicit list. Every value is already computed by the Command
 * Center, so evaluating a rule costs nothing beyond a few field reads.
 */
public interface QuickActionContext {

    /** The current rolled-up status, or null before the first snapshot. */
    ClientStatusModel status();

    /** The current performance verdict, or null before the first sample. */
    PerformanceReport performance();

    /** The total module error count, used to detect a rise. */
    int moduleErrorCount();

    /** The number of crashes recorded so far. */
    int crashCount();

    /** True when Baritone is available. */
    boolean baritoneAvailable();

    /** True when the AI model is loaded. */
    boolean aiLoaded();

    /** True when a player and world are present. */
    boolean worldAvailable();
}
