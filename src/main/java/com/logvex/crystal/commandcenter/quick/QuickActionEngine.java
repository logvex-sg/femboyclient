package com.logvex.crystal.commandcenter.quick;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.StatusSection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The quick-action engine: it evaluates each {@link QuickAction}'s trigger once per tick
 * against a {@link QuickActionContext} and, when the trigger's rising edge is seen, executes
 * the action's effect through an injected {@link QuickActionHandler}.
 *
 * <p>It is the Command Center's answer to "when this happens, do that", and it is built to be
 * safe by construction:
 * <ul>
 *   <li><b>Global enable</b> - nothing runs until {@link #setEnabled(boolean)} is true.</li>
 *   <li><b>Edge triggers</b> - a state trigger fires on its rising edge, not every tick it
 *       holds, so a permanently degraded client does not run an action sixty times a second.</li>
 *   <li><b>Per-action cooldown</b> - a rule cannot fire more often than its cooldown.</li>
 *   <li><b>Max executions</b> - an optional lifetime cap stops a runaway rule by itself.</li>
 *   <li><b>Global rate limit</b> - a hard ceiling on firings per minute across all rules.</li>
 *   <li><b>Confirmation</b> - a mutating rule marked for confirmation is queued for approval
 *       instead of running, and only runs when {@link #approve} is called.</li>
 *   <li><b>Bounded history</b> - the execution log is a fixed-size ring.</li>
 * </ul>
 *
 * <p>Minecraft-independent: the context and the handler are the only seams, so every guard is
 * directly unit tested with stubs.
 */
public final class QuickActionEngine {

    /** One record of a quick action running (or being declined), for the workspace and diagnostics. */
    public record Execution(String actionId, String actionName, long atMillis, String outcome,
                            boolean success) {
        public String describe() {
            return (success ? "\u2713" : "\u2717") + " " + actionName + " \u2014 " + outcome;
        }
    }

    /** Per-action runtime bookkeeping (never persisted). */
    private static final class State {
        boolean armed = true; // for edge triggers: whether the next true is a rising edge
        long lastFiredMillis;
        int executions;
        boolean pendingConfirmation;
    }

    private static final int HISTORY_CAPACITY = 64;
    private static final long RATE_WINDOW_MILLIS = 60_000L;

    private final Map<String, QuickAction> actions = new LinkedHashMap<>();
    private final Map<String, State> states = new LinkedHashMap<>();
    private final Deque<Execution> history = new ArrayDeque<>();
    private final Deque<Long> recentFirings = new ArrayDeque<>();
    private final QuickActionHandler handler;

    private boolean enabled;
    private long maxFiringsPerMinute = 30L;
    private long suppressedByRateLimit;
    private long suppressedByCooldown;

    public QuickActionEngine(QuickActionHandler handler) {
        this.handler = handler;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public long maxFiringsPerMinute() {
        return maxFiringsPerMinute;
    }

    public void setMaxFiringsPerMinute(long value) {
        this.maxFiringsPerMinute = Math.max(1L, value);
    }

    public void add(QuickAction action) {
        if (action == null || action.id().isBlank()) {
            return;
        }
        actions.put(action.id(), action);
        states.putIfAbsent(action.id(), new State());
    }

    /** Adds or replaces an action with the same id, keeping its runtime counters. */
    public void upsert(QuickAction action) {
        if (action == null || action.id().isBlank()) {
            return;
        }
        actions.put(action.id(), action);
        states.putIfAbsent(action.id(), new State());
    }

    public boolean remove(String id) {
        states.remove(id);
        return actions.remove(id) != null;
    }

    public QuickAction byId(String id) {
        return id == null ? null : actions.get(id);
    }

    public List<QuickAction> actions() {
        return List.copyOf(actions.values());
    }

    public int size() {
        return actions.size();
    }

    public void clear() {
        actions.clear();
        states.clear();
    }

    public List<Execution> history() {
        return new ArrayList<>(history);
    }

    public void clearHistory() {
        history.clear();
    }

    public long suppressedByRateLimit() {
        return suppressedByRateLimit;
    }

    public long suppressedByCooldown() {
        return suppressedByCooldown;
    }

    public int executionCount(String id) {
        State state = states.get(id);
        return state == null ? 0 : state.executions;
    }

    /** True when a mutating action is waiting for the user to approve it. */
    public boolean awaitingConfirmation(String id) {
        State state = states.get(id);
        return state != null && state.pendingConfirmation;
    }

    /**
     * Evaluates every action once. Call on the client thread each tick; the work is a handful
     * of field reads plus, at most, one handler call per fired action.
     *
     * @return the number of actions that ran this tick
     */
    public int tick(QuickActionContext context, long now) {
        if (!enabled || context == null) {
            return 0;
        }
        int fired = 0;
        for (QuickAction action : new ArrayList<>(actions.values())) {
            State state = states.computeIfAbsent(action.id(), k -> new State());
            if (!action.enabled()) {
                state.armed = true;
                continue;
            }
            boolean holds = triggerHolds(action, context);
            if (action.trigger().edge()) {
                boolean rising = holds && state.armed;
                state.armed = !holds;
                if (!rising) {
                    continue;
                }
            } else if (!holds) {
                continue;
            }
            if (!action.runnable()) {
                continue;
            }
            if (state.executions > 0 && now - state.lastFiredMillis < action.cooldownMillis()) {
                suppressedByCooldown++;
                continue;
            }
            if (action.maxExecutions() > 0 && state.executions >= action.maxExecutions()) {
                continue;
            }
            if (rateLimited(now)) {
                suppressedByRateLimit++;
                continue;
            }
            if (action.requiresConfirmation() && action.mutating()) {
                if (!state.pendingConfirmation) {
                    state.pendingConfirmation = true;
                    record(action, now, "awaiting confirmation", false);
                    handler.requestConfirmation(action);
                }
                continue;
            }
            run(action, state, now);
            fired++;
        }
        return fired;
    }

    /** Runs a manual action immediately, bypassing the trigger but keeping the limits. */
    public boolean runManually(String id, long now) {
        QuickAction action = actions.get(id);
        if (action == null) {
            return false;
        }
        State state = states.computeIfAbsent(id, k -> new State());
        if (state.executions > 0 && now - state.lastFiredMillis < action.cooldownMillis()) {
            suppressedByCooldown++;
            record(action, now, "on cooldown", false);
            return false;
        }
        if (action.maxExecutions() > 0 && state.executions >= action.maxExecutions()) {
            record(action, now, "execution limit reached", false);
            return false;
        }
        if (rateLimited(now)) {
            suppressedByRateLimit++;
            record(action, now, "rate limited", false);
            return false;
        }
        if (action.requiresConfirmation() && action.mutating()) {
            state.pendingConfirmation = true;
            record(action, now, "awaiting confirmation", false);
            handler.requestConfirmation(action);
            return true;
        }
        run(action, state, now);
        return true;
    }

    /** Approves a pending confirmation and runs the action now. */
    public boolean approve(String id, long now) {
        QuickAction action = actions.get(id);
        State state = states.get(id);
        if (action == null || state == null || !state.pendingConfirmation) {
            return false;
        }
        state.pendingConfirmation = false;
        run(action, state, now);
        return true;
    }

    /** Declines a pending confirmation. */
    public void decline(String id) {
        QuickAction action = actions.get(id);
        State state = states.get(id);
        if (state != null) {
            state.pendingConfirmation = false;
        }
        if (action != null) {
            record(action, System.currentTimeMillis(), "declined by the user", false);
        }
    }

    /** Resets every action's runtime counters (used after editing a rule). */
    public void resetCounters(String id) {
        State state = states.get(id);
        if (state != null) {
            state.executions = 0;
            state.lastFiredMillis = 0;
            state.pendingConfirmation = false;
            state.armed = true;
        }
    }

    private void run(QuickAction action, State state, long now) {
        boolean ok;
        String outcome;
        try {
            outcome = handler.execute(action);
            ok = true;
        } catch (RuntimeException e) {
            ok = false;
            outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        state.lastFiredMillis = now;
        state.executions++;
        recentFirings.addLast(now);
        record(action, now, outcome == null || outcome.isBlank() ? "done" : outcome, ok);
    }

    private void record(QuickAction action, long now, String outcome, boolean success) {
        history.addLast(new Execution(action.id(), action.name(), now, outcome, success));
        while (history.size() > HISTORY_CAPACITY) {
            history.removeFirst();
        }
    }

    private boolean rateLimited(long now) {
        while (!recentFirings.isEmpty() && now - recentFirings.peekFirst() > RATE_WINDOW_MILLIS) {
            recentFirings.removeFirst();
        }
        return recentFirings.size() >= maxFiringsPerMinute;
    }

    private boolean triggerHolds(QuickAction action, QuickActionContext context) {
        switch (action.trigger()) {
            case MANUAL:
                return false;
            case PROBLEM_APPEARS: {
                ClientStatusModel model = context.status();
                return model != null && model.problemCount() > 0;
            }
            case SECTION_PROBLEM: {
                ClientStatusModel model = context.status();
                if (model == null) {
                    return false;
                }
                if (action.section().isBlank()) {
                    return model.problemCount() > 0;
                }
                StatusSection section = model.section(action.section());
                return section != null && section.worst().needsAttention();
            }
            case MODULE_ERROR:
                return context.moduleErrorCount() > 0;
            case INTEGRATION_UNAVAILABLE:
                return !context.baritoneAvailable();
            case PERFORMANCE_DEGRADED: {
                PerformanceReport report = context.performance();
                return report != null && report.status() != HealthStatus.OK;
            }
            case CRASH_RECORDED:
                return context.crashCount() > 0;
            default:
                return false;
        }
    }

    /** A one-line summary for diagnostics. */
    public String summary() {
        long runnable = actions.values().stream().filter(QuickAction::runnable).count();
        return "quick actions: " + (enabled ? "on" : "off") + ", " + runnable + "/" + actions.size()
                + " runnable, " + history.size() + " recent execution(s)";
    }
}
