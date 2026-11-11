package com.logvex.crystal.automation.task;

/**
 * Scheduling priority of an {@link AutomationTask}.
 *
 * <p>The numeric weight is used for two decisions: which pending task starts next when several
 * are ready, and which owner wins a contended resource. The ordering mirrors the convention the
 * action coordinator already uses (movement &lt; inventory &lt; combat &lt; emergency), so a
 * survival action can pre-empt a cosmetic one but a housekeeping task never starves the player.
 */
public enum TaskPriority {

    BACKGROUND(0, "Background"),
    LOW(10, "Low"),
    NORMAL(20, "Normal"),
    HIGH(30, "High"),
    CRITICAL(40, "Critical"),
    EMERGENCY(50, "Emergency");

    private final int weight;
    private final String label;

    TaskPriority(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    /** Higher wins. */
    public int weight() {
        return weight;
    }

    public String label() {
        return label;
    }
}
