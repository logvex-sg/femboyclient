package com.logvex.crystal.automation.inventory;

import java.util.List;

/**
 * One ordered sequence of inventory slot clicks that the {@link InventoryTransactionManager}
 * runs as an atomic unit.
 *
 * <p>Every inventory-moving module (AutoTotem, AutoArmor, AutoRefill, sorting, trash filtering,
 * container transfer, restock) previously clicked slots on its own tick. Because a server applies
 * clicks in order and expects the client to stay in step, two modules clicking in the same tick
 * could corrupt a move or fight over a slot. A transaction expresses the whole move as a
 * {@link Step} list, and the manager runs exactly one step of exactly one transaction per tick,
 * verifying the world after each step before the next.
 *
 * <p>A transaction is Minecraft-independent: the manager drives it through an {@link Port}, so the
 * sequencing, verification, timeout and arbitration logic are unit tested without a game.
 */
public final class InventoryTransaction {

    /** One click, plus the verification that it took effect. */
    public static final class Step {
        private final String description;
        private final Runnable action;
        private final Check check;

        private Step(String description, Runnable action, Check check) {
            this.description = description;
            this.action = action;
            this.check = check;
        }

        public String description() {
            return description;
        }

        void run() {
            action.run();
        }

        boolean verify() {
            return check == null || check.done();
        }

        public static Step of(String description, Runnable action, Check check) {
            return new Step(description, action, check);
        }

        /** A step with no explicit verification; the manager waits one tick for the server. */
        public static Step of(String description, Runnable action) {
            return new Step(description, action, null);
        }
    }

    /** A verification predicate that reads the live inventory. */
    @FunctionalInterface
    public interface Check {
        boolean done();
    }

    private final String owner;
    private final String description;
    private final List<Step> steps;
    private final String exclusiveGroup;
    private final long timeoutMillis;

    InventoryTransaction(String owner, String description, List<Step> steps,
                         String exclusiveGroup, long timeoutMillis) {
        this.owner = owner;
        this.description = description;
        this.steps = List.copyOf(steps);
        this.exclusiveGroup = exclusiveGroup == null ? "" : exclusiveGroup;
        this.timeoutMillis = timeoutMillis;
    }

    public String owner() {
        return owner;
    }

    public String description() {
        return description;
    }

    public List<Step> steps() {
        return steps;
    }

    /** Actions sharing a non-empty group are mutually exclusive within the manager. */
    public String exclusiveGroup() {
        return exclusiveGroup;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public int size() {
        return steps.size();
    }

    public static Builder builder(String owner, String description) {
        return new Builder(owner, description);
    }

    /** Fluent builder; a transaction with no steps is refused at submit time. */
    public static final class Builder {
        private final String owner;
        private final String description;
        private final java.util.List<Step> steps = new java.util.ArrayList<>();
        private String group = "";
        private long timeout = 2_000L;

        private Builder(String owner, String description) {
            this.owner = owner == null ? "" : owner;
            this.description = description == null ? "" : description;
        }

        /** Adds a step with a verification predicate; the next step runs only once it holds. */
        public Builder step(String description, Runnable action, Check check) {
            steps.add(Step.of(description, action, check));
            return this;
        }

        /** Adds a step without explicit verification. */
        public Builder step(String description, Runnable action) {
            steps.add(Step.of(description, action));
            return this;
        }

        public Builder group(String value) {
            this.group = value == null ? "" : value;
            return this;
        }

        public Builder timeoutMillis(long value) {
            this.timeout = value;
            return this;
        }

        public InventoryTransaction build() {
            return new InventoryTransaction(owner, description, steps, group, timeout);
        }
    }
}
