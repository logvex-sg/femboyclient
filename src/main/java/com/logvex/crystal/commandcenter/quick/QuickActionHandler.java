package com.logvex.crystal.commandcenter.quick;

/**
 * The one seam between the quick-action engine and the live client.
 *
 * <p>The engine decides <em>when</em> an action runs; this handler decides <em>how</em>. Keeping
 * them apart means the whole firing policy - edge detection, cooldowns, limits, confirmation -
 * is unit tested with a recording stub, and the production handler is a thin adapter over the
 * services the client already exposes.
 *
 * <p>Every method must not throw for a reason the engine cannot handle: the engine catches a
 * {@link RuntimeException} and records the failure, but a handler that can report "the module
 * does not exist" as a normal outcome should return that message instead.
 */
public interface QuickActionHandler {

    /**
     * Runs {@code action}'s effect and returns a short human outcome, e.g. {@code "enabled Sprint"}.
     * A blank return is treated as success with a generic message.
     */
    String execute(QuickAction action);

    /** Called when a mutating action needs the user to approve it before it runs. */
    void requestConfirmation(QuickAction action);
}
