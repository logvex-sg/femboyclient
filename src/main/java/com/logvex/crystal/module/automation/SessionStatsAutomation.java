package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.world.SessionStats;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;

/**
 * Session Statistics (automation): reports the running session's playtime, distance travelled and
 * how far the player has strayed from the first logged position, on demand and in the status line.
 *
 * <p>It reads the shared {@link SessionStats} service rather than keeping a second copy, so the
 * HUD, the automation panel and this module always agree.
 */
public final class SessionStatsAutomation extends AutomationModule {

    private final BooleanSetting trackDistance =
            add("TrackDistance", "Report distance walked since the session started.", true);
    private final BooleanSetting announceMilestones =
            add("AnnounceMilestones", "Announce every 1000 blocks travelled.", true);

    private long lastMilestone;

    public SessionStatsAutomation() {
        super(Category.AUTOMATION, "SessionStats+", "Tracks playtime and distance for this session.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client)) {
            return;
        }
        if (!trackDistance.value() || !announceMilestones.value()) {
            return;
        }
        long milestone = (long) (SessionStats.get().distanceWalked() / 1000.0);
        if (milestone > lastMilestone) {
            lastMilestone = milestone;
            context().notifications().info("Travelled " + milestone + " km this session \u2661");
        }
    }

    /** A one-line summary used by the Automation GUI and the {@code ; session} command. */
    public String report() {
        SessionStats stats = SessionStats.get();
        long seconds = stats.activeMillis() / 1000;
        String time = String.format(Locale.ROOT, "%d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        return "session " + time
                + ", walked " + String.format(Locale.ROOT, "%.0f", stats.distanceWalked()) + "m"
                + ", max " + String.format(Locale.ROOT, "%.0f", stats.maxDistanceFromOrigin()) + "m out";
    }

    @Override
    public String statusLine() {
        long seconds = SessionStats.get().activeMillis() / 1000;
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
