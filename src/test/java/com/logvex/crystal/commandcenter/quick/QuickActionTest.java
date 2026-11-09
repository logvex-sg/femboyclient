package com.logvex.crystal.commandcenter.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the quick-action rule model: defaults, strict validation and immutability of a copy. */
class QuickActionTest {

    @Test
    void newActionIsDisabledAndManualByDefault() {
        QuickAction action = new QuickAction("a1", "Test");
        assertFalse(action.enabled());
        assertEquals(QuickTrigger.MANUAL, action.trigger());
        assertEquals(QuickActionType.SHOW_DIAGNOSTIC, action.type());
        assertFalse(action.runnable());
    }

    @Test
    void runnableRequiresEnabledAndAValidArgument() {
        QuickAction toggle = new QuickAction("a1", "Toggle sprint")
                .type(QuickActionType.TOGGLE_MODULE);
        assertFalse(toggle.runnable()); // disabled
        toggle.enabled(true);
        assertFalse(toggle.runnable()); // no argument yet
        toggle.argument("Sprint");
        assertTrue(toggle.runnable());
    }

    @Test
    void diagnosticNeedsAMessageRatherThanAnArgument() {
        QuickAction diagnostic = new QuickAction("a1", "Warn").enabled(true);
        assertFalse(diagnostic.runnable());
        diagnostic.message("Look out!");
        assertTrue(diagnostic.runnable());
        // A diagnostic ignores the argument entirely.
        diagnostic.argument("");
        assertTrue(diagnostic.runnable());
    }

    @Test
    void mutatingTypesAreFlagged() {
        assertTrue(new QuickAction("a", "a").type(QuickActionType.TOGGLE_MODULE).mutating());
        assertTrue(new QuickAction("a", "a").type(QuickActionType.SWITCH_PROFILE).mutating());
        assertFalse(new QuickAction("a", "a").type(QuickActionType.OPEN_WORKSPACE).mutating());
        assertFalse(new QuickAction("a", "a").type(QuickActionType.SHOW_DIAGNOSTIC).mutating());
    }

    @Test
    void nullSettersFallBackToSafeDefaults() {
        QuickAction action = new QuickAction(null, null);
        assertEquals("", action.id());
        assertEquals("", action.name());
        action.trigger(null).type(null).argument(null).message(null).section(null);
        assertEquals(QuickTrigger.MANUAL, action.trigger());
        assertEquals(QuickActionType.SHOW_DIAGNOSTIC, action.type());
        assertEquals("", action.argument());
    }

    @Test
    void negativeLimitsAreClampedToZero() {
        QuickAction action = new QuickAction("a", "a");
        action.cooldownMillis(-10).maxExecutions(-3);
        assertEquals(0, action.cooldownMillis());
        assertEquals(0, action.maxExecutions());
    }

    @Test
    void copyIsIndependent() {
        QuickAction original = new QuickAction("a1", "Original").enabled(true)
                .type(QuickActionType.TOGGLE_MODULE).argument("Sprint").cooldownMillis(5000);
        QuickAction copy = original.copy();
        copy.name("Changed").argument("Sneak");
        assertEquals("Original", original.name());
        assertEquals("Sprint", original.argument());
        assertEquals(5000, copy.cooldownMillis());
    }

    @Test
    void describeMentionsEveryRelevantField() {
        QuickAction action = new QuickAction("a1", "On crash").enabled(true)
                .trigger(QuickTrigger.CRASH_RECORDED).type(QuickActionType.OPEN_WORKSPACE)
                .argument("CRASHES");
        String described = action.describe();
        assertTrue(described.contains("On crash"));
        assertTrue(described.contains("CRASHES"));
        assertTrue(described.startsWith("[on]"));
    }
}
