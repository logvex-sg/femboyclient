package com.logvex.crystal.scan.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Answers natural-language questions about recorded scan data, deterministically and offline.
 *
 * <p>This is the local fallback the mission requires: the AI layer must work without any cloud
 * provider, so the questions the scanner advertises are parsed and answered here with plain rules
 * over the {@link DiscoveryDatabase}. It is not a language model and does not pretend to be: it
 * recognises the intents it supports, answers them exactly from stored data, and says plainly when
 * it does not understand a question rather than inventing an answer.
 *
 * <p>Every answer is derived from recorded observations, so the query layer can never fabricate a
 * discovery. A question it cannot parse returns {@link Answer#unsupported} with the list of
 * questions it can answer.
 */
public final class WorldQuery {

    /**
     * An answer to a question.
     *
     * @param question    the question as asked
     * @param intent      the recognised intent, or {@code ""} when unsupported
     * @param text        the answer text (plain, multi-line)
     * @param observationIds the observations the answer is based on, for the dashboard to select
     * @param supported   whether the intent was recognised
     */
    public record Answer(String question, String intent, String text, List<String> observationIds,
                         boolean supported) {

        public Answer {
            observationIds = observationIds == null ? List.of() : List.copyOf(observationIds);
        }

        static Answer unsupported(String question, String help) {
            return new Answer(question, "", help, List.of(), false);
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    public WorldQuery() {
    }

    /** The questions the local engine can answer, for the AI workspace's hint text. */
    public static List<String> supportedQuestions() {
        return List.of(
                "show unexplored regions",
                "summarize today's exploration",
                "find all recorded villages",
                "show structures classified as farms",
                "which regions still need analysis",
                "export my current world map",
                "how many discoveries are recorded",
                "show uncertain observations",
                "find storage structures",
                "what was found in <dimension>");
    }

    /**
     * The context a question is answered against: the store, the live region progress and the
     * scan area. Bundled so the engine has one dependency rather than four.
     */
    public record Context(DiscoveryDatabase database, RegionTracker regions, String dimension,
                          ScanBounds bounds) {
    }

    /** Parses and answers a question against the context. Never throws. */
    public Answer ask(String question, Context context) {
        if (question == null || question.isBlank() || context == null) {
            return Answer.unsupported(question == null ? "" : question, help());
        }
        String q = question.trim().toLowerCase(Locale.ROOT);
        try {
            return dispatch(question, q, context);
        } catch (RuntimeException e) {
            return Answer.unsupported(question, "I could not answer that from the recorded data.\n" + help());
        }
    }

    private Answer dispatch(String question, String q, Context context) {
        // "export" is an action, not a data question: the caller performs it.
        if (containsAny(q, "export", "save my map", "download")) {
            return new Answer(question, "export",
                    "Exporting the current map to world-intelligence.json.", List.of(), true);
        }
        // Unexplored / needs-analysis regions.
        if (containsAny(q, "unexplored", "not explored", "unscanned", "need analysis",
                "needs analysis", "still need")) {
            return unexplored(question, context);
        }
        if (containsAny(q, "summar", "today", "exploration so far", "what happened")) {
            return summary(question, context);
        }
        if (containsAny(q, "village")) {
            return byCategory(question, context, StructureCategory.VILLAGE);
        }
        if (containsAny(q, "farm")) {
            return byCategory(question, context, StructureCategory.FARM);
        }
        if (containsAny(q, "storage", "chest", "stash", "shulker")) {
            return byCategory(question, context, StructureCategory.STORAGE);
        }
        if (containsAny(q, "uncertain", "guess", "unconfirmed", "not sure", "possible")) {
            return uncertain(question, context);
        }
        if (containsAny(q, "how many", "count", "number of")) {
            return count(question, context);
        }
        if (containsAny(q, "what was found in", "found in ")) {
            return dimension(question, q, context);
        }
        return Answer.unsupported(question, "I do not understand that question.\n" + help());
    }

    private Answer unexplored(String question, Context context) {
        RegionTracker regions = context.regions();
        ScanBounds bounds = context.bounds();
        if (regions == null || bounds == null) {
            return new Answer(question, "unexplored",
                    "No scan area is configured, so there is nothing to report as unexplored.",
                    List.of(), true);
        }
        List<Long> unexplored = regions.unexploredRegions(context.dimension(),
                bounds.minChunkX(), bounds.minChunkZ(), bounds.maxChunkX(), bounds.maxChunkZ());
        int totalRegions = RegionGrid.regionCount(bounds.width()) * RegionGrid.regionCount(bounds.depth());
        StringBuilder text = new StringBuilder();
        text.append(unexplored.size()).append(" of ").append(totalRegions)
                .append(" region(s) are unexplored in ").append(shortDimension(context.dimension()))
                .append('.');
        if (unexplored.isEmpty()) {
            text.append("\nEvery region in the scan area has been observed.");
        } else {
            text.append("\nNext: ");
            int shown = Math.min(6, unexplored.size());
            for (int i = 0; i < shown; i++) {
                long key = unexplored.get(i);
                text.append('(').append(RegionGrid.keyX(key)).append(", ").append(RegionGrid.keyZ(key)).append(')');
                if (i < shown - 1) {
                    text.append(", ");
                }
            }
            if (unexplored.size() > shown) {
                text.append(", \u2026");
            }
        }
        return new Answer(question, "unexplored", text.toString(), List.of(), true);
    }

    private Answer summary(String question, Context context) {
        DiscoveryDatabase db = context.database();
        long since = startOfDay();
        List<Observation> today = new ArrayList<>();
        for (Observation observation : db.observations()) {
            if (observation.atMillis() >= since) {
                today.add(observation);
            }
        }
        Map<StructureCategory, Integer> byCategory = new LinkedHashMap<>();
        int confirmed = 0;
        int uncertain = 0;
        for (Observation observation : today) {
            byCategory.merge(observation.category(), 1, Integer::sum);
            if (observation.verification() == VerificationStatus.CONFIRMED) {
                confirmed++;
            } else if (!observation.verification().countsAsDiscovery()) {
                uncertain++;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("Today: ").append(today.size()).append(" observation(s) recorded, ")
                .append(confirmed).append(" confirmed, ").append(uncertain).append(" uncertain.");
        if (!byCategory.isEmpty()) {
            text.append("\nBy category: ");
            byCategory.forEach((category, count) ->
                    text.append(category.label()).append(' ').append(count).append(", "));
            text.setLength(text.length() - 2);
        }
        text.append("\nTotal in the database: ").append(db.size()).append(" observation(s).");
        List<String> ids = today.stream().map(Observation::id).toList();
        return new Answer(question, "summary", text.toString(), ids, true);
    }

    private Answer byCategory(String question, Context context, StructureCategory category) {
        DiscoveryDatabase db = context.database();
        List<Observation> matches = new ArrayList<>();
        for (Observation observation : db.observations()) {
            if (observation.category() == category) {
                matches.add(observation);
            }
        }
        if (matches.isEmpty()) {
            return new Answer(question, "category:" + category.name(),
                    "No observations classified as " + category.label() + " have been recorded.",
                    List.of(), true);
        }
        StringBuilder text = new StringBuilder();
        text.append(matches.size()).append(" ").append(category.label()).append(" observation(s):");
        int shown = Math.min(8, matches.size());
        for (int i = 0; i < shown; i++) {
            Observation observation = matches.get(i);
            text.append("\n  \u00b7 ").append(observation.blockX()).append(", ")
                    .append(observation.blockZ()).append(" \u00b7 ")
                    .append(observation.verification().label())
                    .append(" \u00b7 ").append(Math.round(observation.confidence() * 100)).append('%');
            if (!observation.note().isEmpty()) {
                text.append(" \u2014 ").append(observation.note());
            }
        }
        if (matches.size() > shown) {
            text.append("\n  \u2026 and ").append(matches.size() - shown).append(" more.");
        }
        List<String> ids = matches.stream().map(Observation::id).toList();
        return new Answer(question, "category:" + category.name(), text.toString(), ids, true);
    }

    private Answer uncertain(String question, Context context) {
        List<Observation> matches = new ArrayList<>();
        for (Observation observation : context.database().observations()) {
            if (!observation.verification().countsAsDiscovery()) {
                matches.add(observation);
            }
        }
        if (matches.isEmpty()) {
            return new Answer(question, "uncertain",
                    "No uncertain observations are stored. Every recorded observation is confirmed or likely.",
                    List.of(), true);
        }
        StringBuilder text = new StringBuilder();
        text.append(matches.size()).append(" uncertain observation(s) \u2014 these are guesses, not finds:");
        int shown = Math.min(8, matches.size());
        for (int i = 0; i < shown; i++) {
            Observation observation = matches.get(i);
            text.append("\n  \u00b7 ").append(observation.summary());
        }
        if (matches.size() > shown) {
            text.append("\n  \u2026 and ").append(matches.size() - shown).append(" more.");
        }
        List<String> ids = matches.stream().map(Observation::id).toList();
        return new Answer(question, "uncertain", text.toString(), ids, true);
    }

    private Answer count(String question, Context context) {
        DiscoveryDatabase db = context.database();
        int total = db.size();
        int discoveries = 0;
        for (Observation observation : db.observations()) {
            if (observation.discovered()) {
                discoveries++;
            }
        }
        String text = total + " observation(s) recorded, " + discoveries + " of them discoveries "
                + "(confirmed or likely). " + (total - discoveries) + " are uncertain.";
        return new Answer(question, "count", text, List.of(), true);
    }

    private Answer dimension(String question, String q, Context context) {
        String dimension = null;
        for (String candidate : context.database().dimensions()) {
            String shortName = shortDimension(candidate).toLowerCase(Locale.ROOT);
            if (q.contains(shortName) || q.contains(candidate.toLowerCase(Locale.ROOT))) {
                dimension = candidate;
                break;
            }
        }
        if (dimension == null) {
            StringBuilder text = new StringBuilder("Recorded dimensions: ");
            var dims = context.database().dimensions();
            if (dims.isEmpty()) {
                text.append("none yet.");
            } else {
                text.append(String.join(", ", dims.stream().map(WorldQuery::shortDimension).toList()));
                text.append(".\nAsk again naming one of them.");
            }
            return new Answer(question, "dimension", text.toString(), List.of(), true);
        }
        List<Observation> matches = context.database().forDimension(dimension);
        Map<StructureCategory, Integer> counts = context.database().categoryCounts(dimension);
        StringBuilder text = new StringBuilder();
        text.append(shortDimension(dimension)).append(": ").append(matches.size()).append(" observation(s).");
        if (!counts.isEmpty()) {
            text.append("\nBy category: ");
            counts.forEach((category, value) -> text.append(category.label()).append(' ').append(value).append(", "));
            text.setLength(text.length() - 2);
        }
        List<String> ids = matches.stream().map(Observation::id).toList();
        return new Answer(question, "dimension", text.toString(), ids, true);
    }

    private static long startOfDay() {
        return Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** A short, human-friendly dimension name ({@code minecraft:overworld} -> {@code overworld}). */
    public static String shortDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "unknown";
        }
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    private static String help() {
        StringBuilder builder = new StringBuilder("I can answer:\n");
        for (String example : supportedQuestions()) {
            builder.append("  \u00b7 ").append(example).append('\n');
        }
        return builder.toString().trim();
    }
}
