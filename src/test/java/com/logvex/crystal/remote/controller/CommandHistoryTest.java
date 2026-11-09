package com.logvex.crystal.remote.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.remote.protocol.CommandResult;
import com.logvex.crystal.remote.protocol.CommandStatus;
import com.logvex.crystal.remote.protocol.RemoteAction;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CommandHistory}, with no sockets involved.
 *
 * <p>They pin the behaviour a live dispatch depends on: a command starts with a PENDING outcome
 * per instance, and the result that later arrives for that instance must <em>replace</em> it. If a
 * second outcome were appended instead, the entry would keep a non-terminal PENDING forever and
 * {@link CommandHistory.Entry#complete()} would never become true — the bug these tests guard
 * against.
 */
class CommandHistoryTest {

    @Test
    void resultReplacesThePendingOutcomeForTheSameInstance() {
        CommandHistory history = CommandHistory.standard();
        CommandHistory.Entry entry = history.begin("cmd-1", RemoteAction.ENABLE_MODULE, "enable", 0L);
        entry.add(new CommandHistory.Outcome("inst-1", "Main", CommandStatus.PENDING, "sent", 1L));
        assertFalse(entry.complete(), "a pending outcome must not count as complete");

        history.record(CommandResult.ok("cmd-1", "inst-1", "done"), "Main");

        assertEquals(1, entry.outcomes().size(), "the result must replace, not duplicate");
        assertTrue(entry.complete(), "once the only instance has answered the entry is complete");
        assertEquals(CommandStatus.OK, entry.outcomes().get(0).status());
        assertEquals(1, entry.okCount());
    }

    @Test
    void bulkEntryCompletesOnlyWhenEveryInstanceHasAnswered() {
        CommandHistory history = CommandHistory.standard();
        CommandHistory.Entry entry = history.begin("cmd-2", RemoteAction.DISABLE_MODULE, "disable", 0L);
        entry.add(new CommandHistory.Outcome("inst-a", "A", CommandStatus.PENDING, "sent", 1L));
        entry.add(new CommandHistory.Outcome("inst-b", "B", CommandStatus.PENDING, "sent", 1L));

        history.record(CommandResult.ok("cmd-2", "inst-a", "done"), "A");
        assertFalse(entry.complete(), "one instance is still pending");

        history.record(CommandResult.unsupported("cmd-2", "inst-b", "nope"), "B");
        assertTrue(entry.complete());
        assertEquals(1, entry.okCount());
        assertEquals(1, entry.unsupportedCount());
        assertEquals(0, entry.failureCount(), "unsupported is not counted as a failure");
    }

    @Test
    void aLateDuplicateResultIsIgnored() {
        CommandHistory history = CommandHistory.standard();
        CommandHistory.Entry entry = history.begin("cmd-3", RemoteAction.REQUEST_DIAGNOSTICS, "diag", 0L);
        entry.add(new CommandHistory.Outcome("inst-1", "Main", CommandStatus.PENDING, "sent", 1L));

        history.record(CommandResult.ok("cmd-3", "inst-1", "first"), "Main");
        // A retry's late answer, or a replayed frame, must not add a second outcome.
        history.record(CommandResult.failed("cmd-3", "inst-1", "late"), "Main");

        assertEquals(1, entry.outcomes().size());
        assertEquals(CommandStatus.OK, entry.outcomes().get(0).status(), "the first result wins");
    }

    @Test
    void aResultForAnUnknownRequestIdIsDropped() {
        CommandHistory history = CommandHistory.standard();
        history.begin("cmd-4", RemoteAction.ENABLE_MODULE, "enable", 0L);
        history.record(CommandResult.ok("cmd-other", "inst-1", "done"), "Main");
        assertEquals(1, history.size(), "no phantom entry is created for an unknown request");
    }
}
