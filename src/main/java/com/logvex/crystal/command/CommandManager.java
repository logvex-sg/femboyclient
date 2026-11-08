package com.logvex.crystal.command;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.ClickGui;
import com.logvex.crystal.gui.HudEditorScreen;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.ColorSetting;
import com.logvex.crystal.setting.DoubleSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.KeybindSetting;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.setting.StringSetting;
import com.logvex.crystal.theme.ThemePreset;
import com.logvex.crystal.theme.ThemeVariant;
import com.logvex.crystal.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * The {@code ;} command system, dispatched from intercepted chat rather than a
 * server-side Brigadier command.
 *
 * <p>Anarchy servers routinely reject unknown {@code /} commands, so the client never
 * sends anything to the server: a line starting with {@code ;} is intercepted locally
 * and handled here. This keeps the command surface available everywhere. The legacy
 * {@code .lvx} and {@code .crystal} prefixes are still accepted.
 *
 * <p>Subcommands: {@code help, toggle, bind, set, theme, preset, variant, opacity,
 * gradient, glow, radius, scale, animation, decorations, cat, reducedmotion, blur,
 * cursor, sounds, splash, hud, hudedit, assets, save, load, version}.
 */
public final class CommandManager {

    /** The primary chat prefix, Meteor-style but with a semicolon. */
    public static final String PREFIX = ";";
    /** Legacy prefix kept so older muscle memory and scripts keep working. */
    public static final String ALT_PREFIX = ".crystal";
    /** Second legacy prefix. */
    public static final String LEGACY_PREFIX = ".lvx";

    /** Every accepted prefix, longest first so {@code .crystal} wins over {@code .c}. */
    private static final String[] PREFIXES = {ALT_PREFIX, LEGACY_PREFIX, PREFIX};

    private final CrystalClient client;
    /** Commands registered at runtime by extensions, keyed by lower-case name. */
    private final java.util.Map<String, java.util.function.Predicate<java.util.List<String>>>
            dynamicCommands = new java.util.concurrent.ConcurrentHashMap<>();

    public CommandManager(CrystalClient client) {
        this.client = client;
    }

    /**
     * Registers a runtime command (used by extensions). Returns a runnable that unregisters it.
     * A name already handled by the built-in dispatch is rejected so an extension cannot shadow
     * core commands.
     */
    public Runnable registerDynamic(String name, java.util.function.Predicate<java.util.List<String>> handler) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (key.isEmpty() || isBuiltIn(key)) {
            throw new IllegalArgumentException("Command name unavailable: " + name);
        }
        dynamicCommands.put(key, handler);
        return () -> dynamicCommands.remove(key, handler);
    }

    /** True when the given name is a built-in command (checked against the dispatch switch). */
    private static boolean isBuiltIn(String command) {
        return BUILT_IN_COMMANDS.contains(command);
    }

    /** The set of built-in command names, kept in sync with {@link #dispatch}. */
    private static final java.util.Set<String> BUILT_IN_COMMANDS = java.util.Set.of(
            "help", "?", "toggle", "t", "bind", "b", "set", "theme", "preset", "variant",
            "accent", "primary", "secondary", "opacity", "gradient", "glow", "radius", "scale",
            "animation", "anim", "decorations", "cat", "catcosmetics", "reducedmotion", "motion",
            "blur", "cursor", "sounds", "splash", "easteregg", "secret", "hud", "hudedit",
            "waypoint", "wp", "baritone", "bari", "path", "world", "worldinfo", "entities",
            "entity", "browser", "entitybrowser", "notifications", "notifs", "context", "ctx",
            "friend", "friends", "gui", "clickgui", "category", "cat-tab", "favorites", "favs",
            "fav", "recent", "appearance", "appear", "keybinds", "keys", "profile", "profiles",
            "favorite", "favourite", "save", "load", "reload", "list", "modules", "errors",
            "diagnostics", "conflicts", "coord", "automation", "auto", "diag", "crystal", "ac",
            "assets", "version", "ver", "extensions", "ext", "scripts", "script", "ai",
            "center", "commandcenter", "hub");

    /**
     * Handles a raw chat line. Returns true when the line was a Crystal command and must
     * not be sent to the server.
     */
    public boolean handleChat(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String body = stripPrefix(trimmed);
        if (body == null) {
            return false;
        }
        List<String> tokens = CommandTokenizer.tokenize(body);
        try {
            dispatch(tokens);
        } catch (Throwable t) {
            client.notify("Command error: " + t.getMessage(), NotificationLevel.ERROR);
            CrystalClient.LOG.warn("Command failed: {}", body, t);
        }
        return true;
    }

    /**
     * Runs a built-in command from inside the client (not from chat), so a screen or the
     * Command Center can reuse the exact same command surface. Tokens are split the same way
     * chat input is, and a failure is reported rather than thrown.
     */
    public void runFromHost(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return;
        }
        try {
            dispatch(CommandTokenizer.tokenize(commandLine.trim()));
        } catch (Throwable t) {
            client.notify("Command error: " + t.getMessage(), NotificationLevel.ERROR);
            CrystalClient.LOG.warn("Host command failed: {}", commandLine, t);
        }
    }

    /**
     * Strips a recognised prefix from the front of a chat line, returning the command body,
     * or null when the line is ordinary chat. Accepts {@code ;cmd}, {@code ; cmd} and the
     * legacy {@code .lvx} / {@code .crystal} spellings. Package-private for unit testing.
     */
    static String stripPrefix(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            String pl = prefix.toLowerCase(Locale.ROOT);
            if (!lower.startsWith(pl)) {
                continue;
            }
            String rest = line.substring(prefix.length());
            // A legacy multi-character prefix must be followed by a boundary, so ".crystals"
            // is not read as the command "s".
            if (prefix.length() > 1 && !rest.isEmpty() && !Character.isWhitespace(rest.charAt(0))) {
                continue;
            }
            return rest.trim();
        }
        return null;
    }

    private void dispatch(List<String> tokens) {
        if (tokens.isEmpty()) {
            help();
            return;
        }
        String command = tokens.get(0).toLowerCase(Locale.ROOT);
        List<String> args = tokens.subList(1, tokens.size());
        // Extension-registered commands take precedence only for names the core does not own,
        // so an extension can never shadow a built-in.
        var dynamic = dynamicCommands.get(command);
        if (dynamic != null) {
            try {
                dynamic.test(args);
            } catch (Throwable t) {
                client.notify("Command '" + command + "' failed: " + t.getMessage(),
                        NotificationLevel.ERROR);
            }
            return;
        }
        switch (command) {
            case "help", "?" -> help();
            case "toggle", "t" -> toggle(args);
            case "bind", "b" -> bind(args);
            case "set" -> set(args);
            case "theme" -> theme(args);
            case "preset" -> preset(args);
            case "variant" -> variant(args);
            case "accent", "primary" -> accent(args, true);
            case "secondary" -> accent(args, false);
            case "opacity" -> setFloat(args, "opacity", v -> client.theme().settings().backgroundOpacity = v);
            case "gradient" -> setFloat(args, "gradient", v -> client.theme().settings().gradientIntensity = v);
            case "glow" -> setFloat(args, "glow", v -> client.theme().settings().glowIntensity = v);
            case "radius" -> setInt(args, "radius", v -> client.theme().settings().cornerRadius = v);
            case "scale" -> setFloat(args, "scale", v -> client.theme().settings().uiScale = v);
            case "animation", "anim" -> setFloat(args, "animation", v -> client.theme().settings().animationSpeed = v);
            case "decorations" -> toggleFlag(args, "decorations");
            case "cat", "catcosmetics" -> toggleFlag(args, "cat cosmetics");
            case "reducedmotion", "motion" -> toggleFlag(args, "reduced motion");
            case "blur" -> toggleFlag(args, "blur");
            case "cursor" -> toggleFlag(args, "custom cursor");
            case "sounds" -> toggleFlag(args, "gui sounds");
            case "splash" -> toggleFlag(args, "splash text");
            case "easteregg", "secret" -> easterEgg(args);
            case "hud" -> hud(args);
            case "hudedit" -> hudEdit();
            case "waypoint", "wp" -> waypoint(args);
            case "baritone", "bari", "path" -> baritone(args);
            case "world", "worldinfo" -> world();
            case "scan", "scanner" -> scan(args);
            case "wi", "intel", "intelligence" -> worldIntelligence(args);
            case "entities", "entity" -> entities(args);
            case "browser", "entitybrowser" -> openEntityBrowser();
            case "notifications", "notifs" -> notifications(args);
            case "context", "ctx" -> context(args);
            case "friend", "friends" -> friend(args);
            case "gui", "clickgui" -> openGui();
            case "category", "cat-tab" -> openCategory(args);
            case "favorites", "favs", "fav" -> openFavorites();
            case "recent" -> openRecent();
            case "appearance", "appear" -> openAppearance();
            case "keybinds", "keys" -> openKeybinds();
            case "profile", "profiles" -> profile(args);
            case "favorite", "favourite" -> favorite(args);
            case "save" -> save();
            case "load", "reload" -> load();
            case "list", "modules" -> list(args);
            case "errors", "diagnostics" -> errors(args);
            case "conflicts", "coord" -> conflicts(args);
            case "center", "commandcenter", "hub" -> center(args);
            case "automation", "auto" -> automation(args);
            case "ai" -> ai(args);
            case "extensions", "ext" -> extensions(args);
            case "scripts", "script" -> scripts();
            case "diag" -> diagnostics();
            case "crystal", "ac" -> crystalAnalysis();
            case "assets" -> assets(args);
            case "version", "ver" -> client.notify("LOGVEX CRYSTAL " + version(), NotificationLevel.INFO);
            default -> client.notify("Unknown command '" + command + "'. Try ; help",
                    NotificationLevel.WARN);
        }
    }

    // ------------------------------------------------------------ module commands

    private void toggle(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Usage: ; toggle <module>", NotificationLevel.WARN);
            return;
        }
        Module module = client.modules().byName(CommandTokenizer.join(args));
        if (module == null) {
            client.notify("No module named '" + CommandTokenizer.join(args) + "'", NotificationLevel.ERROR);
            return;
        }
        module.toggle(client.modules());
        recordUsage(module);
        client.notify(module.name() + (module.isEnabled() ? " enabled" : " disabled"),
                module.isEnabled() ? NotificationLevel.SUCCESS : NotificationLevel.INFO);
        if (module.lastError() != null) {
            client.notify(module.lastError(), NotificationLevel.WARN);
        }
    }

    /**
     * Records that a module was used so it appears on the ClickGUI's Recent page. Shared by
     * every command that toggles a module, so the recents list reflects command usage as well
     * as GUI clicks. Silently does nothing when the store is unavailable (headless runs).
     */
    private void recordUsage(Module module) {
        var store = client.favorites();
        if (store != null && module != null) {
            store.markUsed(module.name());
        }
    }

    private void bind(List<String> args) {
        if (args.size() < 2) {
            client.notify("Usage: ; bind <module> <key>", NotificationLevel.WARN);
            return;
        }
        Module module = client.modules().byName(args.get(0));
        if (module == null) {
            client.notify("No module named '" + args.get(0) + "'", NotificationLevel.ERROR);
            return;
        }
        Integer key = KeyParser.parse(args.get(1));
        if (key == null) {
            client.notify("Unknown key '" + args.get(1) + "'", NotificationLevel.ERROR);
            return;
        }
        int cleared = client.modules().resolveKeybindConflict(module, key);
        module.keybind().set(key);
        String suffix = cleared > 0 ? " (cleared " + cleared + " conflict" + (cleared == 1 ? "" : "s") + ")" : "";
        client.notify("Bound " + module.name() + " to " + module.keybind().displayValue() + suffix,
                NotificationLevel.SUCCESS);
    }

    private void set(List<String> args) {
        if (args.size() < 3) {
            client.notify("Usage: ; set <module> <setting> <value>", NotificationLevel.WARN);
            return;
        }
        Module module = client.modules().byName(args.get(0));
        if (module == null) {
            client.notify("No module named '" + args.get(0) + "'", NotificationLevel.ERROR);
            return;
        }
        Setting<?> setting = findSetting(module, args.get(1));
        if (setting == null) {
            client.notify("No setting '" + args.get(1) + "' on " + module.name(), NotificationLevel.ERROR);
            return;
        }
        String value = CommandTokenizer.join(args.subList(2, args.size()));
        if (applyValue(setting, value)) {
            client.notify(module.name() + " " + setting.name() + " = " + setting.displayValue(),
                    NotificationLevel.SUCCESS);
        } else {
            client.notify("Invalid value '" + value + "' for " + setting.name(), NotificationLevel.ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean applyValue(Setting<?> setting, String value) {
        try {
            if (setting instanceof BooleanSetting bool) {
                bool.set(parseBool(value));
            } else if (setting instanceof IntSetting integer) {
                integer.set(Integer.parseInt(value.trim()));
            } else if (setting instanceof DoubleSetting decimal) {
                decimal.set(Double.parseDouble(value.trim()));
            } else if (setting instanceof StringSetting string) {
                string.set(value);
            } else if (setting instanceof KeybindSetting keybind) {
                Integer key = KeyParser.parse(value);
                if (key == null) {
                    return false;
                }
                keybind.set(key);
            } else if (setting instanceof ColorSetting color) {
                color.set(parseColor(value));
            } else if (setting instanceof EnumSetting<?> enumeration) {
                return applyEnumByName(enumeration, value);
            } else {
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean applyEnumByName(EnumSetting<?> setting, String value) {
        for (Object candidate : setting.values()) {
            if (((Enum<?>) candidate).name().equalsIgnoreCase(value.trim())) {
                applyEnumConstant(setting, candidate);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyEnumConstant(EnumSetting setting, Object constant) {
        setting.set((Enum) constant);
    }

    private <E extends Enum<E>> boolean applyEnum(EnumSetting<E> setting, String value) {
        for (E candidate : setting.values()) {
            if (candidate.name().equalsIgnoreCase(value.trim())) {
                setting.set(candidate);
                return true;
            }
        }
        return false;
    }

    private Setting<?> findSetting(Module module, String name) {
        for (SettingGroup group : module.groups()) {
            for (Setting<?> setting : group.settings()) {
                if (setting.name().equalsIgnoreCase(name)) {
                    return setting;
                }
            }
        }
        if (module.keybind().name().equalsIgnoreCase(name)) {
            return module.keybind();
        }
        return null;
    }

    // ------------------------------------------------------------ theme commands

    private void theme(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Theme: " + client.theme().palette().preset().label()
                    + " (" + client.theme().palette().variant().label() + ")", NotificationLevel.INFO);
            client.notify("Presets: " + String.join(", ", ThemePreset.labels()), NotificationLevel.INFO);
            return;
        }
        preset(args);
    }

    private void preset(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Presets: " + String.join(", ", ThemePreset.labels()), NotificationLevel.INFO);
            return;
        }
        String name = CommandTokenizer.join(args);
        if (client.theme().applyPreset(name)) {
            client.notify("Theme: " + client.theme().palette().preset().label(), NotificationLevel.SUCCESS);
            client.saveAll();
        } else {
            client.notify("Unknown preset '" + name + "'", NotificationLevel.ERROR);
        }
    }

    private void variant(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Variant: " + client.theme().palette().variant().label(), NotificationLevel.INFO);
            return;
        }
        client.theme().setVariant(ThemeVariant.parse(args.get(0)));
        client.notify("Variant: " + client.theme().palette().variant().label(), NotificationLevel.SUCCESS);
    }

    private void accent(List<String> args, boolean primary) {
        if (args.isEmpty()) {
            client.notify("Usage: ; " + (primary ? "accent" : "secondary") + " <#RRGGBB|reset>",
                    NotificationLevel.WARN);
            return;
        }
        if (args.get(0).equalsIgnoreCase("reset")) {
            client.theme().settings().clearOverrides();
            client.theme().update();
            client.notify("Accent overrides cleared", NotificationLevel.SUCCESS);
            return;
        }
        int color = parseColor(args.get(0));
        if (primary) {
            client.theme().setPrimary(color);
        } else {
            client.theme().setSecondary(color);
        }
        client.notify((primary ? "Primary" : "Secondary") + " accent set to "
                + String.format("#%06X", color & 0xFFFFFF), NotificationLevel.SUCCESS);
    }

    private void setFloat(List<String> args, String label, java.util.function.Consumer<Float> setter) {
        if (args.isEmpty()) {
            client.notify("Usage: ; " + label + " <0..1>", NotificationLevel.WARN);
            return;
        }
        try {
            setter.accept(Float.parseFloat(args.get(0)));
            client.theme().update();
            client.notify(label + " set", NotificationLevel.SUCCESS);
        } catch (NumberFormatException e) {
            client.notify("Invalid number '" + args.get(0) + "'", NotificationLevel.ERROR);
        }
    }

    private void setInt(List<String> args, String label, java.util.function.IntConsumer setter) {
        if (args.isEmpty()) {
            client.notify("Usage: ; " + label + " <int>", NotificationLevel.WARN);
            return;
        }
        try {
            setter.accept(Integer.parseInt(args.get(0)));
            client.theme().update();
            client.notify(label + " set", NotificationLevel.SUCCESS);
        } catch (NumberFormatException e) {
            client.notify("Invalid number '" + args.get(0) + "'", NotificationLevel.ERROR);
        }
    }

    private void toggleFlag(List<String> args, String label) {
        var s = client.theme().settings();
        Boolean explicit = args.isEmpty() ? null : parseBool(args.get(0));
        boolean value;
        switch (label) {
            case "decorations" -> s.decorations = value = (explicit != null ? explicit : !s.decorations);
            case "cat cosmetics" -> s.catCosmetics = value = (explicit != null ? explicit : !s.catCosmetics);
            case "reduced motion" -> s.reducedMotion = value = (explicit != null ? explicit : !s.reducedMotion);
            case "blur" -> s.blur = value = (explicit != null ? explicit : !s.blur);
            case "custom cursor" -> s.customCursor = value = (explicit != null ? explicit : !s.customCursor);
            case "gui sounds" -> s.guiSounds = value = (explicit != null ? explicit : !s.guiSounds);
            case "splash text" -> s.splashText = value = (explicit != null ? explicit : !s.splashText);
            default -> {
                return;
            }
        }
        client.theme().update();
        client.notify(label + ": " + (value ? "on" : "off"), NotificationLevel.SUCCESS);
    }

    private void easterEgg(List<String> args) {
        var s = client.theme().settings();
        s.easterEgg = args.isEmpty() ? !s.easterEgg : parseBool(args.get(0));
        client.theme().update();
        if (s.easterEgg) {
            com.logvex.crystal.cosmetic.GuiSounds.secret();
            client.notify("\u2661 purr... you found the secret \u2661", NotificationLevel.CUTE);
        } else {
            client.notify("Easter egg: off", NotificationLevel.INFO);
        }
    }

    // ------------------------------------------------------------ hud / gui / io

    private void hud(List<String> args) {
        if (args.isEmpty()) {
            client.hud().toggleHud();
            client.notify("HUD " + (client.hud().isHudVisible() ? "shown" : "hidden"), NotificationLevel.SUCCESS);
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "toggle" -> {
                client.hud().toggleHud();
                client.notify("HUD " + (client.hud().isHudVisible() ? "shown" : "hidden"), NotificationLevel.SUCCESS);
            }
            case "edit" -> hudEdit();
            case "on", "off" -> {
                client.hud().setHudVisible(args.get(0).equalsIgnoreCase("on"));
                client.notify("HUD " + args.get(0).toLowerCase(Locale.ROOT), NotificationLevel.SUCCESS);
            }
            default -> {
                if (args.size() >= 2) {
                    boolean visible = parseBool(args.get(1));
                    client.hud().setElementVisible(args.get(0), visible);
                    client.notify(args.get(0) + ": " + (visible ? "on" : "off"), NotificationLevel.SUCCESS);
                } else {
                    client.notify("Usage: ; hud <element> <on|off> | toggle | edit", NotificationLevel.WARN);
                }
            }
        }
    }

    private void hudEdit() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        MinecraftClient.getInstance().setScreen(new HudEditorScreen(client.hud(), client.theme()));
    }

    // ------------------------------------------------------------ waypoints / friends

    private void waypoint(List<String> args) {
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("gui")) {
            if (ScreenGuard.fontsReady()) {
                MinecraftClient.getInstance().setScreen(new com.logvex.crystal.gui.WaypointScreen(client.theme()));
            }
            return;
        }
        var store = client.waypoints();
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "add", "set" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; waypoint add <name> [x y z]", NotificationLevel.WARN);
                    return;
                }
                String name = args.get(1);
                MinecraftClient mc = MinecraftClient.getInstance();
                int x;
                int y;
                int z;
                if (args.size() >= 5) {
                    try {
                        x = (int) Double.parseDouble(args.get(2));
                        y = (int) Double.parseDouble(args.get(3));
                        z = (int) Double.parseDouble(args.get(4));
                    } catch (NumberFormatException e) {
                        client.notify("Coordinates must be numbers", NotificationLevel.ERROR);
                        return;
                    }
                } else if (mc.player != null) {
                    x = (int) Math.floor(mc.player.getX());
                    y = (int) Math.floor(mc.player.getY());
                    z = (int) Math.floor(mc.player.getZ());
                } else {
                    client.notify("Not in a world; supply coordinates", NotificationLevel.ERROR);
                    return;
                }
                String dimension = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
                var candidate = new com.logvex.crystal.util.waypoint.Waypoint(name, x, y, z,
                        client.theme().palette().primary(), dimension);
                var duplicate = store.duplicateOf(candidate, 4);
                store.put(candidate);
                client.saveAll();
                client.notify("Waypoint '" + name + "' at " + x + " " + y + " " + z, NotificationLevel.SUCCESS);
                if (duplicate != null) {
                    client.notify("Note: '" + duplicate.name() + "' is within 4 blocks of this spot",
                            NotificationLevel.WARN);
                }
            }
            case "remove", "del", "delete", "rm" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; waypoint remove <name>", NotificationLevel.WARN);
                    return;
                }
                if (store.remove(args.get(1))) {
                    client.saveAll();
                    client.notify("Removed waypoint '" + args.get(1) + "'", NotificationLevel.SUCCESS);
                } else {
                    client.notify("No waypoint named '" + args.get(1) + "'", NotificationLevel.ERROR);
                }
            }
            case "clear" -> {
                store.clear();
                client.saveAll();
                client.notify("Waypoints cleared", NotificationLevel.SUCCESS);
            }
            case "list" -> {
                if (store.size() == 0) {
                    client.notify("No waypoints saved", NotificationLevel.INFO);
                    return;
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                String dimension = com.logvex.crystal.world.WorldInfo.dimensionId(mc);
                for (var waypoint : store.all()) {
                    String nav = mc.player == null ? ""
                            : " \u00B7 " + com.logvex.crystal.util.waypoint.Navigation.describe(waypoint,
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(), dimension);
                    client.notify(waypoint.name() + " " + waypoint.x() + " " + waypoint.y() + " "
                            + waypoint.z() + nav, NotificationLevel.INFO);
                }
            }
            case "export" -> client.notify("Waypoints are stored at " + storePath()
                    + " (JSON, safe to copy)", NotificationLevel.INFO);
            default -> client.notify("Usage: ; waypoint <add|remove|list|clear|gui> [...]",
                    NotificationLevel.WARN);
        }
    }

    private static String storePath() {
        return com.logvex.crystal.config.ConfigPaths.waypoints().toString();
    }

    /**
     * The {@code ; baritone} command. Every path here routes through the shared
     * {@link com.logvex.crystal.integration.baritone.BaritoneService}; the command never touches
     * Baritone directly. When Baritone is not installed each subcommand reports the reason
     * instead of failing.
     */
    private void baritone(List<String> args) {
        var service = client.baritone();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("status")) {
            reportBaritoneStatus(service);
            return;
        }
        if (args.get(0).equalsIgnoreCase("gui") || args.get(0).equalsIgnoreCase("screen")) {
            if (ScreenGuard.fontsReady()) {
                MinecraftClient.getInstance().setScreen(new com.logvex.crystal.gui.BaritoneScreen(
                        client.theme(), service));
            }
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "goto" -> {
                Integer[] xyz = parseCoords(args, 1);
                if (xyz == null) {
                    return;
                }
                String error = service.goTo(com.logvex.crystal.integration.baritone.BaritoneGoal.block(
                        xyz[0], xyz[1], xyz[2]));
                if (error != null) {
                    client.notify(error, NotificationLevel.ERROR);
                }
            }
            case "near" -> {
                Integer[] xyz = parseCoords(args, 1);
                if (xyz == null) {
                    return;
                }
                int range = 1;
                if (args.size() >= 5) {
                    try {
                        range = Math.max(1, (int) Double.parseDouble(args.get(4)));
                    } catch (NumberFormatException e) {
                        client.notify("Range must be a number", NotificationLevel.ERROR);
                        return;
                    }
                }
                String error = service.followNear(xyz[0], xyz[1], xyz[2], range);
                if (error != null) {
                    client.notify(error, NotificationLevel.ERROR);
                }
            }
            case "wp", "waypoint" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; baritone wp <name>", NotificationLevel.WARN);
                    return;
                }
                var waypoint = client.waypoints().byName(args.get(1));
                if (waypoint == null) {
                    client.notify("No waypoint named '" + args.get(1) + "'", NotificationLevel.ERROR);
                    return;
                }
                String dimension = com.logvex.crystal.world.WorldInfo.dimensionId(MinecraftClient.getInstance());
                if (!com.logvex.crystal.integration.baritone.BaritoneWaypointBridge.reachable(waypoint, dimension)) {
                    client.notify(com.logvex.crystal.integration.baritone.BaritoneWaypointBridge
                            .rejectionReason(waypoint), NotificationLevel.WARN);
                    return;
                }
                String error = service.goTo(com.logvex.crystal.integration.baritone.BaritoneWaypointBridge
                        .goalFor(waypoint, dimension));
                if (error != null) {
                    client.notify(error, NotificationLevel.ERROR);
                }
            }
            case "here" -> {
                String error = service.goToCurrentPosition(MinecraftClient.getInstance());
                if (error != null) {
                    client.notify(error, NotificationLevel.ERROR);
                }
            }
            case "cancel", "stop" -> service.cancel();
            case "pause" -> {
                String error = service.pause();
                if (error != null) {
                    client.notify(error, NotificationLevel.ERROR);
                }
            }
            case "resume", "unpause" -> {
                String error = service.resume();
                if (error != null) {
                    client.notify(error, NotificationLevel.ERROR);
                }
            }
            case "paused" -> client.notify(service.paused() ? "Baritone is paused" : "Baritone is not paused",
                    NotificationLevel.INFO);
            case "set" -> {
                if (args.size() < 3) {
                    client.notify("Usage: ; baritone set <setting> <value>", NotificationLevel.WARN);
                    return;
                }
                String value = CommandTokenizer.join(args.subList(2, args.size()));
                String error = service.setSetting(args.get(1), value);
                if (error != null) {
                    client.notify(error, NotificationLevel.ERROR);
                } else {
                    client.notify("Baritone " + args.get(1) + " = " + value, NotificationLevel.SUCCESS);
                }
            }
            case "settings", "list" -> {
                var names = service.settingNames();
                if (names.isEmpty()) {
                    client.notify(service.available() ? "No settings exposed" : service.unavailableReason(),
                            NotificationLevel.INFO);
                    return;
                }
                client.notify(names.size() + " Baritone settings (first 12 shown)", NotificationLevel.INFO);
                for (int i = 0; i < Math.min(12, names.size()); i++) {
                    client.notify("  " + names.get(i), NotificationLevel.INFO);
                }
            }
            default -> client.notify(
                    "Usage: ; baritone <status|gui|goto|near|wp|here|cancel|pause|resume|paused|set|settings>",
                    NotificationLevel.WARN);
        }
    }

    private void reportBaritoneStatus(com.logvex.crystal.integration.baritone.BaritoneService service) {
        var status = service.status();
        if (!service.probed()) {
            client.notify("Baritone: probing\u2026", NotificationLevel.INFO);
            return;
        }
        if (!status.available()) {
            client.notify("Baritone unavailable: " + status.reason(), NotificationLevel.WARN);
            return;
        }
        client.notify("Baritone " + status.version() + " \u2014 " + status.statusText(),
                NotificationLevel.INFO);
        if (status.hasGoalPosition()) {
            client.notify("Goal at " + status.goalX() + " " + status.goalY() + " " + status.goalZ()
                    + (status.pathLength() > 0 ? " \u00B7 path " + status.pathLength() + " nodes" : ""),
                    NotificationLevel.INFO);
        }
    }

    /** Parses three coordinates at {@code start}, or reports the error and returns null. */
    private Integer[] parseCoords(List<String> args, int start) {
        if (args.size() < start + 3) {
            client.notify("Usage: coordinates <x> <y> <z>", NotificationLevel.WARN);
            return null;
        }
        try {
            return new Integer[]{
                    (int) Double.parseDouble(args.get(start)),
                    (int) Double.parseDouble(args.get(start + 1)),
                    (int) Double.parseDouble(args.get(start + 2))};
        } catch (NumberFormatException e) {
            client.notify("Coordinates must be numbers", NotificationLevel.ERROR);
            return null;
        }
    }

    /** Prints the world-information panel's lines to chat. */
    private void world() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var module = client.modules().byClass(com.logvex.crystal.module.world.WorldInfoModule.class);
        if (module == null) {
            client.notify("WorldInfo module is unavailable", NotificationLevel.ERROR);
            return;
        }
        if (mc.world == null || mc.player == null) {
            client.notify("Not in a world", NotificationLevel.WARN);
            return;
        }
        String[] lines = module.buildLines(mc);
        if (lines.length == 0) {
            client.notify("No world information selected (enable a WorldInfo section)",
                    NotificationLevel.INFO);
            return;
        }
        for (String line : lines) {
            client.notify(line, NotificationLevel.INFO);
        }
    }

    /**
     * Controls the world scanner. Every subcommand maps to a real module method: nothing here is
     * a stub, and the scanner itself only ever reads loaded chunks.
     */
    private void scan(List<String> args) {
        var module = client.modules().byClass(com.logvex.crystal.module.world.WorldScannerModule.class);
        if (module == null) {
            client.notify("WorldScanner module is unavailable", NotificationLevel.ERROR);
            return;
        }
        if (!module.isEnabled()) {
            module.enable(client.modules());
            recordUsage(module);
        }
        String action = args.isEmpty() ? "status" : args.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> {
                var snapshot = module.snapshot();
                if (snapshot == null) {
                    client.notify("Scanner not initialised", NotificationLevel.WARN);
                    return;
                }
                client.notify("scan " + snapshot.state().label() + " " + Math.round(snapshot.progress() * 100) + "%",
                        NotificationLevel.INFO);
                client.notify("dimension " + snapshot.dimension() + " \u00b7 explored " + snapshot.exploredChunks()
                        + " \u00b7 finds " + snapshot.discoveries(), NotificationLevel.INFO);
                client.notify("flight " + snapshot.flightStatus() + " \u00b7 movement " + snapshot.movementStatus(),
                        NotificationLevel.INFO);
            }
            case "start" -> module.start();
            case "pause" -> module.pause();
            case "resume" -> module.resume();
            case "stop" -> module.stop();
            case "reset" -> module.resetSession();
            case "gui", "screen" -> {
                if (ScreenGuard.fontsReady()) {
                    MinecraftClient.getInstance().setScreen(new com.logvex.crystal.gui.ScanDashboardScreen(
                            client.theme(), module));
                }
            }
            case "export" -> {
                module.saveDatabase();
                client.notify("Saved scan database (" + module.database().sessions().size() + " sessions)",
                        NotificationLevel.SUCCESS);
            }
            case "profile" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; scan profile <name>  |  ; scan profile save <name>",
                            NotificationLevel.WARN);
                    return;
                }
                if (args.get(1).equalsIgnoreCase("save") && args.size() >= 3) {
                    module.saveProfile(args.get(2));
                } else if (!module.applyProfile(args.get(1))) {
                    client.notify("No scan profile named '" + args.get(1) + "'", NotificationLevel.WARN);
                } else {
                    client.notify("Applied scan profile '" + args.get(1) + "'", NotificationLevel.SUCCESS);
                }
            }
            default -> client.notify("Usage: ; scan [start|pause|resume|stop|reset|status|gui|export|profile]",
                    NotificationLevel.WARN);
        }
    }

    /**
     * Controls the world-intelligence layer: the discovery database, the autonomous exploration
     * targets, natural-language queries and the dashboard. Every subcommand maps to a real method
     * on the {@link com.logvex.crystal.scan.world.WorldIntelligence} layer; nothing here is a stub,
     * and the layer only ever reads the client's own loaded chunks.
     */
    private void worldIntelligence(List<String> args) {
        var module = client.modules().byClass(com.logvex.crystal.module.world.WorldScannerModule.class);
        if (module == null) {
            client.notify("WorldScanner module is unavailable", NotificationLevel.ERROR);
            return;
        }
        if (!module.isEnabled()) {
            module.enable(client.modules());
            recordUsage(module);
        }
        var intelligence = module.intelligence();
        if (intelligence == null) {
            client.notify("World intelligence is off. Enable the WorldScanner module (WorldIntelligence setting).",
                    NotificationLevel.WARN);
            return;
        }
        String action = args.isEmpty() ? "status" : args.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> {
                var db = intelligence.database();
                client.notify("intelligence \u00b7 " + db.size() + " recorded \u00b7 "
                        + db.discoveries(intelligence.dimension()).size() + " finds \u00b7 "
                        + db.dimensions().size() + " dimension(s)", NotificationLevel.INFO);
                client.notify("regions " + intelligence.regions().touchedRegions(intelligence.dimension())
                        + " touched \u00b7 " + intelligence.regions().completeRegions(intelligence.dimension())
                        + " complete \u00b7 baritone " + intelligence.baritone().statusLine(), NotificationLevel.INFO);
                client.notify("ai " + intelligence.ai().backendDescription(), NotificationLevel.INFO);
            }
            case "gui", "screen", "open" -> {
                if (ScreenGuard.fontsReady()) {
                    MinecraftClient.getInstance().setScreen(new com.logvex.crystal.gui.WorldIntelligenceScreen(
                            client.theme(), module));
                }
            }
            case "find", "search" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; wi find <text>", NotificationLevel.WARN);
                    return;
                }
                String needle = String.join(" ", args.subList(1, args.size()));
                var results = intelligence.database().search(needle, null,
                        com.logvex.crystal.scan.world.DiscoveryDatabase.SortOrder.RECENT, 0, 8);
                if (results.isEmpty()) {
                    client.notify("No observations matching '" + needle + "'", NotificationLevel.WARN);
                    return;
                }
                client.notify(results.size() + " result(s) for '" + needle + "':", NotificationLevel.INFO);
                for (var observation : results) {
                    client.notify("  " + observation.summary() + " \u00b7 " + observation.evidenceLine(),
                            NotificationLevel.INFO);
                }
            }
            case "list", "recent" -> {
                var recent = intelligence.database().recent(10);
                if (recent.isEmpty()) {
                    client.notify("No observations recorded yet", NotificationLevel.WARN);
                    return;
                }
                client.notify("Recent observations:", NotificationLevel.INFO);
                for (var observation : recent) {
                    client.notify("  " + observation.summary() + " \u00b7 " + observation.evidenceLine(),
                            NotificationLevel.INFO);
                }
            }
            case "targets", "route" -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                int fromX = mc != null && mc.player != null ? (int) mc.player.getX() : 0;
                int fromZ = mc != null && mc.player != null ? (int) mc.player.getZ() : 0;
                var targets = intelligence.buildTargets(fromX, fromZ, 8);
                if (targets.isEmpty()) {
                    client.notify("No exploration targets yet (no discoveries recorded)", NotificationLevel.WARN);
                    return;
                }
                client.notify("Exploration targets:", NotificationLevel.INFO);
                for (var target : targets) {
                    client.notify("  " + target.centreBlockX() + ", " + target.centreBlockZ() + " \u00b7 "
                            + target.count() + " chunk(s) \u00b7 " + target.reason(), NotificationLevel.INFO);
                }
            }
            case "ask", "query" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; wi ask <question>", NotificationLevel.WARN);
                    client.notify("Try: " + String.join(" | ",
                            com.logvex.crystal.scan.world.WorldQuery.supportedQuestions()), NotificationLevel.INFO);
                    return;
                }
                String question = String.join(" ", args.subList(1, args.size()));
                var answer = intelligence.askLocal(question);
                for (String line : wrapText(answer.text(), 200)) {
                    client.notify(line, NotificationLevel.INFO);
                }
            }
            case "confirm" -> {
                if (args.size() < 3) {
                    client.notify("Usage: ; wi confirm <chunkX> <chunkZ>", NotificationLevel.WARN);
                    return;
                }
                setVerificationByChunk(intelligence, args, com.logvex.crystal.scan.world.VerificationStatus.CONFIRMED);
            }
            case "dismiss" -> {
                if (args.size() < 3) {
                    client.notify("Usage: ; wi dismiss <chunkX> <chunkZ>", NotificationLevel.WARN);
                    return;
                }
                setVerificationByChunk(intelligence, args, com.logvex.crystal.scan.world.VerificationStatus.REJECTED);
            }
            case "delete", "remove" -> {
                if (args.size() < 3) {
                    client.notify("Usage: ; wi delete <chunkX> <chunkZ>", NotificationLevel.WARN);
                    return;
                }
                Integer chunkX = parseInt(args.get(1));
                Integer chunkZ = parseInt(args.get(2));
                if (chunkX == null || chunkZ == null) {
                    client.notify("Chunk coordinates must be integers", NotificationLevel.WARN);
                    return;
                }
                String id = findObservationId(intelligence, chunkX, chunkZ);
                if (id == null) {
                    client.notify("No observation at chunk " + chunkX + "," + chunkZ, NotificationLevel.WARN);
                    return;
                }
                intelligence.database().delete(id);
                intelligence.save();
                client.notify("Deleted observation " + id, NotificationLevel.SUCCESS);
            }
            case "export", "save" -> {
                String error = intelligence.save();
                client.notify(error == null ? "Saved world intelligence (" + intelligence.database().size()
                        + " records)" : error, error == null ? NotificationLevel.SUCCESS : NotificationLevel.ERROR);
            }
            case "import", "load" -> {
                intelligence.load();
                client.notify("Loaded world intelligence (" + intelligence.database().size() + " records)",
                        NotificationLevel.SUCCESS);
            }
            case "clear" -> {
                intelligence.database().clear();
                intelligence.save();
                client.notify("Cleared all recorded observations", NotificationLevel.SUCCESS);
            }
            default -> client.notify("Usage: ; wi [status|gui|find <text>|list|targets|ask <question>|"
                    + "confirm <cx> <cz>|dismiss <cx> <cz>|delete <cx> <cz>|export|import|clear]",
                    NotificationLevel.WARN);
        }
    }

    private void setVerificationByChunk(com.logvex.crystal.scan.world.WorldIntelligence intelligence,
                                        List<String> args,
                                        com.logvex.crystal.scan.world.VerificationStatus status) {
        Integer chunkX = parseInt(args.get(1));
        Integer chunkZ = parseInt(args.get(2));
        if (chunkX == null || chunkZ == null) {
            client.notify("Chunk coordinates must be integers", NotificationLevel.WARN);
            return;
        }
        String id = findObservationId(intelligence, chunkX, chunkZ);
        if (id == null) {
            client.notify("No observation at chunk " + chunkX + "," + chunkZ, NotificationLevel.WARN);
            return;
        }
        intelligence.database().setVerification(id, status);
        intelligence.save();
        client.notify("Marked " + id + " as " + status.label(), NotificationLevel.SUCCESS);
    }

    private static String findObservationId(com.logvex.crystal.scan.world.WorldIntelligence intelligence,
                                            int chunkX, int chunkZ) {
        for (var observation : intelligence.database().observations()) {
            if (observation.chunkX() == chunkX && observation.chunkZ() == chunkZ) {
                return observation.id();
            }
        }
        return null;
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (current.length() + word.length() + 1 > maxLength && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(word);
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    /**
     * Reads the shared entity snapshot. This is a purely informational view of what the
     * client can already see; it never claims to reveal entities the client cannot observe.
     */
    private void entities(List<String> args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            client.notify("Not in a world", NotificationLevel.WARN);
            return;
        }
        var query = com.logvex.crystal.entity.EntityQuery.get();
        if (query.size() == 0) {
            client.notify("No entities in the current snapshot", NotificationLevel.INFO);
            return;
        }
        String mode = args.isEmpty() ? "list" : args.get(0).toLowerCase(Locale.ROOT);
        switch (mode) {
            case "count" -> {
                var counts = query.counts();
                StringBuilder builder = new StringBuilder("entities " + query.size() + ":");
                for (var entry : counts.entrySet()) {
                    builder.append(' ').append(entry.getKey().label()).append(' ').append(entry.getValue());
                }
                client.notify(builder.toString(), NotificationLevel.INFO);
            }
            case "search" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; entities search <text>", NotificationLevel.WARN);
                    return;
                }
                String needle = CommandTokenizer.join(args.subList(1, args.size())).toLowerCase(Locale.ROOT);
                int shown = 0;
                for (var info : query.all()) {
                    if (info.name().toLowerCase(Locale.ROOT).contains(needle)
                            || info.category().label().toLowerCase(Locale.ROOT).contains(needle)) {
                        client.notify(info.name() + " (" + info.category().label() + ") "
                                + com.logvex.crystal.util.Format.distance(info.distance()),
                                NotificationLevel.INFO);
                        if (++shown >= 12) {
                            break;
                        }
                    }
                }
                if (shown == 0) {
                    client.notify("No entities matching '" + needle + "'", NotificationLevel.INFO);
                }
            }
            default -> {
                int shown = 0;
                for (var info : query.matching(null)) {
                    client.notify(info.name() + " (" + info.category().label() + ") "
                            + com.logvex.crystal.util.Format.distance(info.distance()),
                            NotificationLevel.INFO);
                    if (++shown >= 12) {
                        break;
                    }
                }
                if (query.size() > shown) {
                    client.notify("\u2026 " + (query.size() - shown) + " more (see the entity browser)",
                            NotificationLevel.INFO);
                }
            }
        }
    }

    /** Reads and edits the notification preferences. */
    private void notifications(List<String> args) {
        var settings = client.notifications().settings();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("list")) {
            StringBuilder hidden = new StringBuilder();
            for (var level : settings.hiddenLevels()) {
                if (hidden.length() > 0) {
                    hidden.append(", ");
                }
                hidden.append(level.name());
            }
            client.notify("notifications: " + (settings.sound ? "sound on" : "sound off")
                    + ", " + settings.durationMillis + "ms, scale " + settings.scale
                    + ", " + settings.position.label()
                    + ", duplicates " + (settings.suppressDuplicates ? "suppressed" : "shown")
                    + ", hidden [" + hidden + "]", NotificationLevel.INFO);
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "history", "log" -> {
                var history = client.notifications().history();
                if (history.isEmpty()) {
                    client.notify("No notification history", NotificationLevel.INFO);
                    return;
                }
                int from = Math.max(0, history.size() - 10);
                for (int i = from; i < history.size(); i++) {
                    var entry = history.get(i);
                    client.notify("[" + entry.level().name() + "] " + entry.text(), NotificationLevel.INFO);
                }
            }
            case "clear" -> {
                client.notifications().clearHistory();
                client.notify("Notification history cleared", NotificationLevel.SUCCESS);
            }
            case "sound" -> {
                if (args.size() < 2) {
                    settings.sound = !settings.sound;
                } else {
                    Boolean value = parseBool(args.get(1));
                    if (value == null) {
                        client.notify("Usage: ; notifications sound <on|off>", NotificationLevel.WARN);
                        return;
                    }
                    settings.sound = value;
                }
                client.saveAll();
                client.notify("Notification sound " + (settings.sound ? "on" : "off"), NotificationLevel.SUCCESS);
            }
            case "duration" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; notifications duration <ms>", NotificationLevel.WARN);
                    return;
                }
                try {
                    settings.durationMillis = Long.parseLong(args.get(1));
                    settings.clamp();
                    client.saveAll();
                    client.notify("Notification duration " + settings.durationMillis + "ms",
                            NotificationLevel.SUCCESS);
                } catch (NumberFormatException e) {
                    client.notify("Duration must be a number of milliseconds", NotificationLevel.ERROR);
                }
            }
            case "scale" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; notifications scale <0.5..2.0>", NotificationLevel.WARN);
                    return;
                }
                try {
                    settings.scale = Float.parseFloat(args.get(1));
                    settings.clamp();
                    client.saveAll();
                    client.notify("Notification scale " + settings.scale, NotificationLevel.SUCCESS);
                } catch (NumberFormatException e) {
                    client.notify("Scale must be a number", NotificationLevel.ERROR);
                }
            }
            case "position" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; notifications position <top_right|top_left|bottom_right|bottom_left>",
                            NotificationLevel.WARN);
                    return;
                }
                try {
                    settings.position = com.logvex.crystal.notification.NotificationSettings.Position
                            .valueOf(args.get(1).toUpperCase(Locale.ROOT));
                    client.saveAll();
                    client.notify("Notification position " + settings.position.label(), NotificationLevel.SUCCESS);
                } catch (IllegalArgumentException e) {
                    client.notify("Unknown position '" + args.get(1) + "'", NotificationLevel.ERROR);
                }
            }
            case "duplicates", "dup" -> {
                if (args.size() < 2) {
                    settings.suppressDuplicates = !settings.suppressDuplicates;
                } else {
                    Boolean value = parseBool(args.get(1));
                    if (value == null) {
                        client.notify("Usage: ; notifications duplicates <on|off>", NotificationLevel.WARN);
                        return;
                    }
                    settings.suppressDuplicates = value;
                }
                client.saveAll();
                client.notify("Duplicate suppression "
                        + (settings.suppressDuplicates ? "on" : "off"), NotificationLevel.SUCCESS);
            }
            case "level" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; notifications level <info|success|warn|error|cute> [on|off]",
                            NotificationLevel.WARN);
                    return;
                }
                NotificationLevel level;
                try {
                    level = NotificationLevel.valueOf(args.get(1).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    client.notify("Unknown level '" + args.get(1) + "'", NotificationLevel.ERROR);
                    return;
                }
                boolean shown = args.size() < 3 ? !settings.shows(level) : Boolean.TRUE.equals(parseBool(args.get(2)));
                settings.setShown(level, shown);
                client.saveAll();
                client.notify(level.name() + " notifications " + (shown ? "shown" : "hidden"),
                        NotificationLevel.SUCCESS);
            }
            case "test" -> client.notifications().cute("Test notification \u2661");
            default -> client.notify("Usage: ; notifications <list|history|clear|sound|duration|scale|"
                    + "position|duplicates|level|test>", NotificationLevel.WARN);
        }
    }

    /** Reads and edits opt-in contextual-profile rules. */
    private void context(List<String> args) {
        var service = client.contextProfiles();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("list")) {
            client.notify("context rules: " + (service.enabled() ? "on" : "off")
                    + ", cooldown " + service.cooldownMillis() + "ms, " + service.bindings().size() + " rule(s)",
                    NotificationLevel.INFO);
            for (var binding : service.bindings()) {
                var rule = binding.rule();
                StringBuilder conditions = new StringBuilder();
                if (!rule.dimension().isEmpty()) {
                    conditions.append("dim=").append(rule.dimension());
                }
                if (!rule.serverContains().isEmpty()) {
                    if (conditions.length() > 0) {
                        conditions.append(" ");
                    }
                    conditions.append("server~").append(rule.serverContains());
                }
                if (rule.healthBelow() > 0) {
                    if (conditions.length() > 0) {
                        conditions.append(" ");
                    }
                    conditions.append("hp<").append(rule.healthBelow());
                }
                client.notify((binding.enabled() ? "* " : "  ") + binding.profileName()
                        + " \u2190 " + conditions, NotificationLevel.INFO);
            }
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "on", "off" -> {
                service.setEnabled(args.get(0).equalsIgnoreCase("on"));
                client.saveAll();
                client.notify("Contextual profiles " + (service.enabled() ? "enabled" : "disabled"),
                        NotificationLevel.SUCCESS);
            }
            case "add" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; context add <profile> [dimension=<id>] [server=<text>] [hp<]<n>]",
                            NotificationLevel.WARN);
                    return;
                }
                String profile = args.get(1);
                String dimension = "";
                String server = "";
                double healthBelow = 0;
                for (int i = 2; i < args.size(); i++) {
                    String token = args.get(i);
                    if (token.startsWith("dimension=")) {
                        dimension = token.substring("dimension=".length());
                    } else if (token.startsWith("dim=")) {
                        dimension = token.substring("dim=".length());
                    } else if (token.startsWith("server=")) {
                        server = token.substring("server=".length());
                    } else if (token.startsWith("hp=")) {
                        healthBelow = parseHealth(token.substring("hp=".length()));
                    } else if (token.startsWith("hp<")) {
                        healthBelow = parseHealth(token.substring("hp<".length()));
                    }
                }
                var rule = new com.logvex.crystal.context.ContextRule(dimension, server, healthBelow);
                if (!rule.hasConditions()) {
                    client.notify("A rule needs at least one condition (dimension/server/hp)", NotificationLevel.WARN);
                    return;
                }
                service.add(com.logvex.crystal.context.ContextProfileBinding.of(profile, rule));
                client.saveAll();
                client.notify("Context rule added for profile '" + profile + "'", NotificationLevel.SUCCESS);
            }
            case "remove", "del", "delete" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; context remove <profile>", NotificationLevel.WARN);
                    return;
                }
                int removed = service.remove(args.get(1));
                client.saveAll();
                client.notify("Removed " + removed + " rule(s)", NotificationLevel.SUCCESS);
            }
            case "clear" -> {
                service.clear();
                client.saveAll();
                client.notify("Context rules cleared", NotificationLevel.SUCCESS);
            }
            case "cooldown" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; context cooldown <ms>", NotificationLevel.WARN);
                    return;
                }
                try {
                    service.setCooldownMillis(Long.parseLong(args.get(1)));
                    client.saveAll();
                    client.notify("Context cooldown " + service.cooldownMillis() + "ms", NotificationLevel.SUCCESS);
                } catch (NumberFormatException e) {
                    client.notify("Cooldown must be a number of milliseconds", NotificationLevel.ERROR);
                }
            }
            default -> client.notify("Usage: ; context <on|off|list|add|remove|clear|cooldown>",
                    NotificationLevel.WARN);
        }
    }

    private static double parseHealth(String value) {
        try {
            double v = Double.parseDouble(value);
            return v > 1 ? v / 20.0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void friend(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Usage: ; friend <add|remove|list> [name]", NotificationLevel.WARN);
            return;
        }
        var friends = client.friends();
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; friend add <name>", NotificationLevel.WARN);
                    return;
                }
                String name = CommandTokenizer.join(args.subList(1, args.size()));
                if (friends.add(name)) {
                    client.saveAll();
                    client.notify("Added friend '" + name + "' \u2661", NotificationLevel.CUTE);
                } else {
                    client.notify("'" + name + "' is already a friend", NotificationLevel.INFO);
                }
            }
            case "remove", "del", "rm" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; friend remove <name>", NotificationLevel.WARN);
                    return;
                }
                String name = CommandTokenizer.join(args.subList(1, args.size()));
                if (friends.remove(name)) {
                    client.saveAll();
                    client.notify("Removed friend '" + name + "'", NotificationLevel.SUCCESS);
                } else {
                    client.notify("'" + name + "' is not a friend", NotificationLevel.ERROR);
                }
            }
            case "list" -> client.notify(friends.size() == 0 ? "No friends added"
                    : "Friends: " + String.join(", ", friends.names()), NotificationLevel.INFO);
            default -> client.notify("Usage: ; friend <add|remove|list> [name]", NotificationLevel.WARN);
        }
    }

    private void openGui() {
        ClickGui gui = client.clickGui();
        if (gui != null) {
            gui.open();
        }
    }

    /** Opens the ClickGUI on a named category, or lists categories when none is given. */
    private void openCategory(List<String> args) {
        if (args.isEmpty()) {
            StringBuilder builder = new StringBuilder("Categories: ");
            for (com.logvex.crystal.module.Category category : com.logvex.crystal.module.Category.values()) {
                builder.append(category.displayName()).append(' ');
            }
            client.notify(builder.toString().trim(), NotificationLevel.INFO);
            return;
        }
        for (com.logvex.crystal.module.Category category : com.logvex.crystal.module.Category.values()) {
            if (category.name().equalsIgnoreCase(args.get(0))
                    || category.displayName().equalsIgnoreCase(args.get(0))) {
                ClickGui gui = client.clickGui();
                if (gui != null) {
                    gui.showCategory(category);
                    gui.open();
                }
                return;
            }
        }
        client.notify("Unknown category '" + args.get(0) + "'", NotificationLevel.ERROR);
    }

    private void openFavorites() {
        ClickGui gui = client.clickGui();
        if (gui != null) {
            gui.showFavorites();
            gui.open();
        }
    }

    private void openRecent() {
        ClickGui gui = client.clickGui();
        if (gui != null) {
            gui.showRecent();
            gui.open();
        }
    }

    private void openAppearance() {
        ClickGui gui = client.clickGui();
        if (gui != null) {
            gui.showAppearance();
            gui.open();
        }
    }

    private void openKeybinds() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        MinecraftClient.getInstance().setScreen(
                new com.logvex.crystal.gui.KeybindScreen(client.modules(), client.theme(), null));
    }

    /** Opens the live entity browser. */
    private void openEntityBrowser() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            client.notify("Not in a world", NotificationLevel.WARN);
            return;
        }
        mc.setScreen(new com.logvex.crystal.gui.EntityBrowserScreen(client.theme()));
    }

    /** Manages named profiles: {@code list, save, load, delete, import, export, starters}. */
    private void profile(List<String> args) {
        var service = client.profiles();
        if (service == null) {
            client.notify("Profiles are not available yet", NotificationLevel.WARN);
            return;
        }
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("gui")) {
            if (ScreenGuard.fontsReady()) {
                MinecraftClient.getInstance().setScreen(
                        new com.logvex.crystal.gui.ProfileScreen(service, client.theme()));
            }
            return;
        }
        String action = args.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                var names = service.names();
                client.notify(names.isEmpty() ? "No profiles saved" : "Profiles: " + String.join(", ", names),
                        NotificationLevel.INFO);
                client.notify("Starters: " + String.join(", ", com.logvex.crystal.config.StarterProfiles.names()),
                        NotificationLevel.INFO);
            }
            case "save" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; profile save <name>", NotificationLevel.WARN);
                    return;
                }
                String name = CommandTokenizer.join(args.subList(1, args.size()));
                try {
                    boolean ok = service.save(name, false);
                    if (ok) {
                        client.notify("Saved profile '" + name + "'", NotificationLevel.SUCCESS);
                    } else {
                        client.notify("Profile '" + name + "' exists; use ; profile overwrite <name>",
                                NotificationLevel.WARN);
                    }
                } catch (java.io.IOException e) {
                    client.notify("Save failed: " + e.getMessage(), NotificationLevel.ERROR);
                }
            }
            case "overwrite" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; profile overwrite <name>", NotificationLevel.WARN);
                    return;
                }
                String name = CommandTokenizer.join(args.subList(1, args.size()));
                try {
                    service.save(name, true);
                    client.notify("Overwrote profile '" + name + "'", NotificationLevel.SUCCESS);
                } catch (java.io.IOException e) {
                    client.notify("Save failed: " + e.getMessage(), NotificationLevel.ERROR);
                }
            }
            case "load", "apply" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; profile load <name|starter>", NotificationLevel.WARN);
                    return;
                }
                String name = CommandTokenizer.join(args.subList(1, args.size()));
                if (com.logvex.crystal.config.StarterProfiles.names().contains(name)
                        && !service.profiles().exists(name)) {
                    var starter = com.logvex.crystal.config.StarterProfiles.build(name);
                    if (starter != null) {
                        service.applyProfile(starter);
                        client.theme().update();
                        client.notify("Applied starter '" + name + "'", NotificationLevel.SUCCESS);
                        return;
                    }
                }
                if (service.apply(name)) {
                    client.theme().update();
                    client.notify("Loaded profile '" + name + "'", NotificationLevel.SUCCESS);
                } else {
                    client.notify("No profile named '" + name + "'", NotificationLevel.ERROR);
                }
            }
            case "delete", "del", "rm" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; profile delete <name>", NotificationLevel.WARN);
                    return;
                }
                boolean ok = service.profiles().delete(CommandTokenizer.join(args.subList(1, args.size())));
                client.notify(ok ? "Deleted profile" : "No such profile",
                        ok ? NotificationLevel.SUCCESS : NotificationLevel.ERROR);
            }
            case "starters" -> client.notify("Starters: "
                    + String.join(", ", com.logvex.crystal.config.StarterProfiles.names()), NotificationLevel.INFO);
            default -> client.notify("Usage: ; profile <list|save|load|delete|starters|gui> [name]",
                    NotificationLevel.WARN);
        }
    }

    /** Toggles favourite status: {@code ; favorite <module>}. */
    private void favorite(List<String> args) {
        var store = client.favorites();
        if (store == null) {
            client.notify("Favourites are not available yet", NotificationLevel.WARN);
            return;
        }
        if (args.isEmpty()) {
            var favorites = store.favorites();
            client.notify(favorites.isEmpty() ? "No favourites yet"
                    : "Favourites: " + String.join(", ", favorites), NotificationLevel.INFO);
            return;
        }
        Module module = client.modules().byName(CommandTokenizer.join(args));
        if (module == null) {
            client.notify("No module named '" + CommandTokenizer.join(args) + "'", NotificationLevel.ERROR);
            return;
        }
        boolean nowFavorite = store.toggleFavorite(module.name());
        client.saveAll();
        client.notify(module.name() + (nowFavorite ? " favourited \u2605" : " unfavourited"), NotificationLevel.SUCCESS);
    }

    private void save() {
        client.saveAll();
        client.notify("Configuration saved \u2661", NotificationLevel.SUCCESS);
    }

    private void load() {
        client.reloadAll();
        client.notify("Configuration reloaded", NotificationLevel.SUCCESS);
    }

    private void list(List<String> args) {
        if (args.isEmpty()) {
            client.notify(client.modules().size() + " modules loaded", NotificationLevel.INFO);
            return;
        }
        for (com.logvex.crystal.module.Category category : com.logvex.crystal.module.Category.values()) {
            if (category.name().equalsIgnoreCase(args.get(0))) {
                StringBuilder builder = new StringBuilder(category.displayName() + ": ");
                for (Module module : client.modules().byCategory(category)) {
                    builder.append(module.name()).append(' ');
                }
                client.notify(builder.toString().trim(), NotificationLevel.INFO);
                return;
            }
        }
        client.notify("Unknown category '" + args.get(0) + "'", NotificationLevel.ERROR);
    }

    /**
     * The {@code ; center} command: the central Command Center. It rolls up every subsystem's
     * status, reports module compatibility and performance, and can run any registered action.
     * Subcommands: {@code status, perf, compat [module], actions, run <id>, save, reload}.
     */
    private void center(List<String> args) {
        var service = client.commandCenter();
        if (service == null) {
            client.notify("Command Center unavailable", NotificationLevel.WARN);
            return;
        }
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("status")) {
            centerStatus(service);
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "perf", "performance" -> {
                var report = service.performance();
                client.notify(report.summary(), report.clean() ? NotificationLevel.SUCCESS : NotificationLevel.WARN);
                for (var finding : report.findings()) {
                    client.notify("  " + finding.describe(), NotificationLevel.INFO);
                }
            }
            case "compat", "compatibility" -> centerCompat(service, args);
            case "actions", "list" -> {
                client.notify("Command Center actions (" + service.actions().size() + ")", NotificationLevel.INFO);
                for (String category : service.actions().categories()) {
                    StringBuilder line = new StringBuilder("  " + category + ": ");
                    for (var action : service.actions().byCategory(category)) {
                        line.append(action.id()).append(' ');
                    }
                    client.notify(line.toString().trim(), NotificationLevel.INFO);
                }
            }
            case "run" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; center run <action-id>", NotificationLevel.WARN);
                    return;
                }
                var result = service.run(args.get(1));
                client.notify(result.message(), result.success() ? NotificationLevel.SUCCESS : NotificationLevel.WARN);
            }
            case "save" -> {
                client.saveAll();
                client.notify("Configuration saved \u2661", NotificationLevel.SUCCESS);
            }
            case "reload" -> {
                client.reloadAll();
                client.notify("Configuration reloaded", NotificationLevel.SUCCESS);
            }
            default -> client.notify("Unknown subcommand. Try ; center status", NotificationLevel.WARN);
        }
    }

    /** Prints the rolled-up status: one line per section, then the problem entries. */
    private void centerStatus(com.logvex.crystal.commandcenter.CommandCenterService service) {
        var model = service.snapshot();
        client.notify("\u2661 Command Center \u2014 " + model.summary(), model.healthy()
                ? NotificationLevel.SUCCESS : NotificationLevel.WARN);
        for (var section : model.sections()) {
            client.notify("  " + section.title() + ": " + section.summary(), section.worst()
                    == com.logvex.crystal.commandcenter.HealthStatus.CRITICAL ? NotificationLevel.ERROR
                    : section.worst().needsAttention() ? NotificationLevel.WARN : NotificationLevel.INFO);
        }
        for (var entry : model.problems()) {
            client.notify("  ! " + entry.describe(), NotificationLevel.WARN);
        }
    }

    /** Prints compatibility verdicts: blocked modules when no name is given, else one module. */
    private void centerCompat(com.logvex.crystal.commandcenter.CommandCenterService service,
                              List<String> args) {
        if (args.size() >= 2) {
            var verdict = service.verdictFor(args.get(1));
            if (verdict == null) {
                client.notify("No module named '" + args.get(1) + "'", NotificationLevel.ERROR);
                return;
            }
            client.notify(verdict.describe(), verdict.operable() ? NotificationLevel.SUCCESS
                    : NotificationLevel.WARN);
            return;
        }
        var blocked = service.compatibility().blocked();
        if (blocked.isEmpty()) {
            client.notify("All modules compatible \u2661", NotificationLevel.SUCCESS);
            return;
        }
        client.notify(blocked.size() + " module(s) with compatibility notes:", NotificationLevel.WARN);
        for (var verdict : blocked) {
            client.notify("  " + verdict.describe(), NotificationLevel.WARN);
        }
    }


    /**
     * Reports recent module failures. With {@code clear} it empties the log. This is the
     * in-game view of the same errors written to the log file, so a player can see which
     * module misbehaved without leaving the game.
     */
    private void errors(List<String> args) {
        com.logvex.crystal.logging.ModuleErrorLog log = com.logvex.crystal.client.CrystalClient.errors();
        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("clear")) {
            log.clear();
            client.notify("Module error log cleared", NotificationLevel.SUCCESS);
            return;
        }
        if (log.distinctModules() == 0) {
            client.notify("No module errors recorded \u2661", NotificationLevel.SUCCESS);
            return;
        }
        client.notify("Module errors: " + log.totalRecorded() + " across "
                + log.distinctModules() + " module(s)", NotificationLevel.WARN);
        for (var entry : log.ranked()) {
            client.notify("  " + entry.getKey() + " \u00D7" + entry.getValue(), NotificationLevel.WARN);
        }
        var latest = log.latest();
        if (latest != null) {
            client.notify("Latest: " + latest.module() + " \u2014 " + latest.message(), NotificationLevel.INFO);
        }
    }

    /**
     * Reports the module-coordination inspector's findings: unsatisfied typed dependencies,
     * circular chains, active module conflicts, keybind clashes and current action contention.
     * Read-only; never changes a module's state.
     */
    private void conflicts(List<String> args) {
        var service = client.coordination();
        if (service == null) {
            client.notify("Coordination inspector unavailable", NotificationLevel.WARN);
            return;
        }
        var report = service.inspect();
        if (report.clean()) {
            client.notify("No coordination issues \u2661 (" + client.actions().summary() + ")",
                    NotificationLevel.SUCCESS);
            return;
        }
        client.notify("Coordination: " + report.summary(), NotificationLevel.WARN);
        for (var issue : report.issues()) {
            client.notify("  " + issue.describe(), issue.severity()
                    == com.logvex.crystal.coordination.ConflictReport.Severity.BLOCKING
                    ? NotificationLevel.ERROR : NotificationLevel.INFO);
        }
    }

    /**
     * The {@code ; ai} command: a single surface for the optional mini-AI subsystem.
     *
     * <p>Subcommands: {@code status, gui, enable/on, disable/off, local <on|off>, model [id],
     * crashes, analyse, baritone, report, reports, send <id>, clearreports, mailto <id>}.
     * Nothing here loads a model, downloads a file or sends an email on its own: those happen
     * only through an explicit subcommand.
     */
    private void ai(List<String> args) {
        var settings = client.aiSettings();
        var ai = client.ai();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("status")) {
            client.notify("AI: " + (settings.enabled ? "enabled" : "disabled")
                    + " \u00B7 local analysis " + (settings.localAnalysis ? "on" : "off")
                    + " \u00B7 email " + (settings.emailEnabled ? "on" : "off"), NotificationLevel.INFO);
            if (ai != null) {
                client.notify("  model " + ai.currentSpec().displayName() + " \u00B7 " + ai.state().label()
                        + " \u00B7 queue " + ai.queueDepth() + "/" + ai.queueLimit(), NotificationLevel.INFO);
            }
            client.notify("  crashes captured: " + client.crashMonitor().size()
                    + " \u00B7 reports queued: " + client.reportService().reports().size(), NotificationLevel.INFO);
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "gui", "screen", "open" -> {
                if (ScreenGuard.fontsReady()) {
                    MinecraftClient.getInstance().setScreen(
                            new com.logvex.crystal.gui.AIAssistantScreen(client.theme(), client));
                }
            }
            case "on", "enable" -> {
                settings.enabled = true;
                client.applyAiSettings();
                client.notify("AI assistant enabled", NotificationLevel.SUCCESS);
            }
            case "off", "disable" -> {
                settings.enabled = false;
                client.applyAiSettings();
                client.notify("AI assistant disabled", NotificationLevel.INFO);
            }
            case "local", "analysis" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; ai local <on|off>", NotificationLevel.WARN);
                    return;
                }
                boolean on = args.get(1).equalsIgnoreCase("on") || args.get(1).equalsIgnoreCase("true");
                settings.localAnalysis = on;
                client.applyAiSettings();
                client.notify("Local analysis " + (on ? "enabled" : "disabled"), NotificationLevel.INFO);
            }
            case "model" -> {
                if (args.size() < 2) {
                    for (var spec : com.logvex.crystal.ai.model.ModelCatalog.all()) {
                        boolean installed = ai != null && ai.loader().isInstalled(spec);
                        client.notify("  " + spec.id() + " \u00B7 " + spec.displayName()
                                + (installed ? " (installed)" : ""), NotificationLevel.INFO);
                    }
                    return;
                }
                var chosen = com.logvex.crystal.ai.model.ModelCatalog.byId(args.get(1));
                if (chosen.isEmpty()) {
                    client.notify("No such model: " + args.get(1), NotificationLevel.ERROR);
                    return;
                }
                settings.modelId = chosen.get().id();
                client.applyAiSettings();
                client.notify("Model set to " + chosen.get().displayName(), NotificationLevel.SUCCESS);
            }
            case "crashes", "history" -> {
                var history = client.crashMonitor().history();
                if (history.isEmpty()) {
                    client.notify("No crashes captured \u2661", NotificationLevel.INFO);
                    return;
                }
                for (var record : history) {
                    client.notify("  " + record.category().label() + " \u00B7 " + record.severity().label()
                            + " \u00B7 " + record.fingerprint(), NotificationLevel.INFO);
                }
            }
            case "analyse", "analyze" -> {
                var record = client.crashMonitor().latest();
                if (record == null) {
                    client.notify("No crash to analyse", NotificationLevel.WARN);
                    return;
                }
                var diagnosis = client.crashAnalyzer().diagnose(record);
                client.notify("Cause (" + diagnosis.confidence().label() + "): " + diagnosis.likelyCause(),
                        NotificationLevel.INFO);
                for (String fix : diagnosis.suggestedFixes()) {
                    client.notify("  fix: " + fix, NotificationLevel.INFO);
                }
            }
            case "baritone", "bari" -> {
                var advice = client.baritoneAdvice();
                client.notify("Baritone: " + advice.summary(), NotificationLevel.INFO);
                for (String issue : advice.likelyIssues()) {
                    client.notify("  issue: " + issue, NotificationLevel.INFO);
                }
            }
            case "report" -> {
                var record = client.crashMonitor().latest();
                if (record == null) {
                    client.notify("No crash to report", NotificationLevel.WARN);
                    return;
                }
                var report = client.reportService().queueReport(record,
                        client.crashAnalyzer().diagnose(record));
                client.notify(report == null ? "A report for this crash is already queued"
                        : "Queued report " + report.id(), NotificationLevel.SUCCESS);
            }
            case "reports", "queue" -> {
                var reports = client.reportService().reports();
                if (reports.isEmpty()) {
                    client.notify("No reports queued", NotificationLevel.INFO);
                    return;
                }
                for (var report : reports) {
                    client.notify("  " + report.id() + " \u00B7 " + report.status().label()
                            + " \u00B7 " + report.fingerprint(), NotificationLevel.INFO);
                }
            }
            case "send" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; ai send <id>", NotificationLevel.WARN);
                    return;
                }
                client.reportService().approveAndSend(args.get(1));
                client.notify("Sending report " + args.get(1) + "\u2026", NotificationLevel.INFO);
            }
            case "mailto" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; ai mailto <id>", NotificationLevel.WARN);
                    return;
                }
                var report = client.reportService().reports().stream()
                        .filter(r -> r.id().equals(args.get(1))).findFirst().orElse(null);
                if (report == null) {
                    client.notify("No such report", NotificationLevel.ERROR);
                    return;
                }
                MinecraftClient.getInstance().keyboard.setClipboard(client.reportService().mailtoLink(report));
                client.notify("Copied a mailto link to the clipboard", NotificationLevel.SUCCESS);
            }
            case "clearreports" -> {
                client.reportService().clear();
                client.notify("Cleared the report queue", NotificationLevel.INFO);
            }
            case "clearcrashes" -> {
                client.crashMonitor().clear();
                client.notify("Cleared the crash history", NotificationLevel.INFO);
            }
            default -> client.notify("Usage: ; ai <status|gui|on|off|local|model|crashes|analyse|"
                    + "baritone|report|reports|send|mailto|clearreports|clearcrashes>",
                    NotificationLevel.WARN);
        }
    }

    /**
     * The automation surface: {@code list}, {@code on}, {@code off}, {@code history},
     * {@code add <name> <trigger> [cond...] -> [action...]}, {@code enable/disable <id>},
     * {@code remove <id>}. Rules are plain data and every effect goes through the narrow
     * automation context, so nothing here can run arbitrary code.
     */
    private void automation(List<String> args) {
        var engine = client.automation();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("status")) {
            client.notify(engine.summary(), engine.isEnabled()
                    ? NotificationLevel.SUCCESS : NotificationLevel.INFO);
            return;
        }
        String sub = args.get(0).toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "on", "enable" -> {
                engine.setEnabled(true);
                client.saveAutomation();
                client.notify("Automation enabled", NotificationLevel.SUCCESS);
            }
            case "off", "disable" -> {
                engine.setEnabled(false);
                client.saveAutomation();
                client.notify("Automation disabled", NotificationLevel.INFO);
            }
            case "list", "rules" -> {
                if (engine.rules().isEmpty()) {
                    client.notify("No automation rules", NotificationLevel.INFO);
                    return;
                }
                for (var rule : engine.rules()) {
                    client.notify("  " + rule.id() + " " + rule.describe(), NotificationLevel.INFO);
                }
            }
            case "history" -> {
                if (engine.history().isEmpty()) {
                    client.notify("No automation history", NotificationLevel.INFO);
                    return;
                }
                for (var firing : engine.history()) {
                    client.notify("  " + firing.describe(), NotificationLevel.INFO);
                }
            }
            case "remove", "delete" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; automation remove <id>", NotificationLevel.WARN);
                    return;
                }
                boolean removed = engine.removeRule(args.get(1));
                client.saveAutomation();
                client.notify(removed ? "Rule removed" : "No such rule",
                        removed ? NotificationLevel.SUCCESS : NotificationLevel.WARN);
            }
            case "toggle" -> {
                if (args.size() < 2) {
                    client.notify("Usage: ; automation toggle <id>", NotificationLevel.WARN);
                    return;
                }
                var rule = engine.byId(args.get(1));
                if (rule.isEmpty()) {
                    client.notify("No such rule", NotificationLevel.WARN);
                    return;
                }
                rule.get().enabled(!rule.get().enabled());
                client.saveAutomation();
                client.notify("Rule " + args.get(1) + (rule.get().enabled() ? " enabled" : " disabled"),
                        NotificationLevel.SUCCESS);
            }
            case "add" -> addAutomation(args);
            default -> client.notify("Usage: ; automation <status|on|off|list|history|"
                    + "add|remove|toggle>", NotificationLevel.WARN);
        }
    }

    /**
     * {@code ; automation add <name> <trigger> [cond]... -- [action]...}
     *
     * <p>The trigger is a single token ({@code interval:5s}, {@code lowhealth:40},
     * {@code event:TARGET_CHANGED}). Conditions come before {@code --}, actions after. The
     * tokenizer already stripped quotes, so a multi-word notify message must be joined by hand.
     */
    private void addAutomation(List<String> args) {
        if (args.size() < 3) {
            client.notify("Usage: ; automation add <name> <trigger> [cond]... -- [action]...",
                    NotificationLevel.WARN);
            return;
        }
        String name = args.get(1);
        try {
            var rule = new com.logvex.crystal.automation.AutomationRule(
                    "rule-" + System.currentTimeMillis(), name);
            rule.trigger(com.logvex.crystal.automation.Trigger.parse(args.get(2)));
            int separator = args.indexOf("--");
            List<String> conditionTokens = separator < 0
                    ? args.subList(3, args.size()) : args.subList(3, separator);
            List<String> actionTokens = separator < 0
                    ? List.of() : args.subList(separator + 1, args.size());
            for (String token : conditionTokens) {
                rule.addCondition(com.logvex.crystal.automation.Condition.parse(token));
            }
            for (String token : actionTokens) {
                rule.addAction(com.logvex.crystal.automation.Action.parse(token));
            }
            if (rule.actions().isEmpty()) {
                client.notify("A rule needs at least one action after '--'", NotificationLevel.WARN);
                return;
            }
            client.automation().addRule(rule);
            client.saveAutomation();
            client.notify("Added rule " + rule.id() + ": " + rule.describe(), NotificationLevel.SUCCESS);
        } catch (RuntimeException e) {
            client.notify("Could not parse rule: " + e.getMessage(), NotificationLevel.ERROR);
        }
    }

    /**
     * The extension surface: {@code list}, {@code enable <id>}, {@code disable <id>},
     * {@code reload}. Enabling requires explicit user approval - a discovered extension is
     * never activated automatically.
     */
    private void extensions(List<String> args) {
        var manager = client.extensions();
        if (manager == null) {
            client.notify("Extension system unavailable", NotificationLevel.WARN);
            return;
        }
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("list")) {
            if (manager.installed().isEmpty()) {
                client.notify("No extensions found (see docs/extension-development.md)",
                        NotificationLevel.INFO);
                return;
            }
            for (var ext : manager.installed()) {
                client.notify("  " + ext.summary(), ext.loaded()
                        ? NotificationLevel.SUCCESS : NotificationLevel.INFO);
            }
            return;
        }
        String sub = args.get(0).toLowerCase(Locale.ROOT);
        if (args.size() < 2 && (sub.equals("enable") || sub.equals("disable") || sub.equals("reload"))) {
            client.notify("Usage: ; extensions " + sub + " <id>", NotificationLevel.WARN);
            return;
        }
        String id = args.size() >= 2 ? args.get(1) : "";
        switch (sub) {
            case "enable" -> client.notify(manager.enable(id)
                    ? "Enabled " + id : "Could not enable " + id,
                    manager.isEnabled(id) ? NotificationLevel.SUCCESS : NotificationLevel.WARN);
            case "disable" -> client.notify(manager.disable(id)
                    ? "Disabled " + id : "Could not disable " + id,
                    manager.isEnabled(id) ? NotificationLevel.WARN : NotificationLevel.SUCCESS);
            case "reload" -> {
                manager.reload();
                client.notify("Rescanned extensions", NotificationLevel.INFO);
            }
            default -> client.notify("Usage: ; extensions <list|enable|disable|reload>",
                    NotificationLevel.WARN);
        }
    }

    /** Explains the scripting decision honestly: not implemented, and why. */
    private void scripts() {
        client.notify("Scripting is not implemented in this build.", NotificationLevel.INFO);
        client.notify("  Reason: no maintained embedded runtime can enforce the CPU/memory "
                + "limits required to run user code safely. See docs/scripting.md.",
                NotificationLevel.INFO);
    }

    /** Runs the Diagnostics module's report, enabling it first when necessary. */
    private void diagnostics() {
        Module module = client.modules().byName("Diagnostics");
        if (!(module instanceof com.logvex.crystal.module.misc.DiagnosticsModule diagnostics)) {
            client.notify("Diagnostics module unavailable", NotificationLevel.WARN);
            return;
        }
        if (!module.isEnabled() && !module.enable(client.modules())) {
            client.notify("Could not enable Diagnostics: " + module.lastError(), NotificationLevel.WARN);
            return;
        }
        diagnostics.report();
    }

    private void crystalAnalysis() {
        var module = client.modules().byName("AutoCrystal");
        if (!(module instanceof com.logvex.crystal.module.combat.AutoCrystalModule autoCrystal)) {
            client.notify("AutoCrystal module unavailable", NotificationLevel.WARN);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            client.notify("Crystal analysis needs a loaded world", NotificationLevel.WARN);
            return;
        }
        var target = client.combat().currentTarget();
        if (target == null) {
            client.notify("Crystal: no target in range", NotificationLevel.INFO);
            return;
        }
        var plan = autoCrystal.planPlacement(mc, target.entity());
        if (plan.hasPlacement()) {
            var best = plan.best();
            client.notify(String.format("Crystal: place at %d,%d,%d  dmg %.1f  self %.1f",
                    best.x(), best.y(), best.z(), best.targetDamage(), best.selfDamage()),
                    NotificationLevel.SUCCESS);
        } else {
            client.notify("Crystal: no valid placement (" + plan.rejectedSummary() + ")",
                    NotificationLevel.WARN);
        }
    }

    private void assets(List<String> args) {
        com.logvex.crystal.assets.AssetRegistry registry = com.logvex.crystal.assets.AssetRegistry.get();
        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("list")) {
            client.notify(com.logvex.crystal.assets.AssetManifest.size() + " assets in the pack",
                    NotificationLevel.INFO);
            return;
        }
        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("reload")) {
            registry.releaseAll();
            client.notify("Asset cache cleared; textures reload on next draw", NotificationLevel.SUCCESS);
            return;
        }
        client.notify("Asset pack: " + com.logvex.crystal.assets.AssetManifest.size() + " assets, "
                + registry.registered() + " loaded, " + registry.failures() + " missing",
                registry.failures() > 0 ? NotificationLevel.WARN : NotificationLevel.SUCCESS);
    }

    private void help() {
        client.notify("\u2661 LOGVEX CRYSTAL commands \u2661", NotificationLevel.CUTE);
        for (String line : new String[]{
                "; toggle <module>",
                "; bind <module> <key>",
                "; set <module> <setting> <value>",
                "; preset <name> | theme | variant <dark|light>",
                "; accent <#RRGGBB|reset> | secondary ...",
                "; opacity|gradient|glow|scale|animation <0..1>",
                "; radius <0..12> | decorations|cat|motion|blur|cursor|sounds|splash <on|off>",
                "; hud <element> <on|off> | hudedit | gui",
                "; category <name> | favorites | recent | appearance | keybinds",
                "; profile <list|save|load|delete|starters|gui> [name]",
                "; favorite <module>",
                "; waypoint <add|remove|list|clear> [name] [x y z]",
                "; baritone <status|gui|goto <x y z>|near <x y z> [r]|wp <name>|here|cancel|pause|resume|paused|set|settings>",
                "; world | entities <list|count|search <text>> | browser",
                "; wi <status|gui|find <text>|list|targets|ask <q>|confirm|dismiss|delete|export|import|clear>",
                "; notifications <list|history|clear|sound|duration|scale|position|duplicates|level|test>",
                "; context <on|off|list|add|remove|clear|cooldown>",
                "; friend <add|remove|list> [name]",
                "; ai <status|gui|on|off|local|model|crashes|analyse|baritone|report|reports|send|mailto>",
                "; crystal | save | load | list [category] | errors [clear] | assets [list|reload] | version"
        }) {
            client.notify(line, NotificationLevel.INFO);
        }
    }

    // ------------------------------------------------------------ helpers

    private static Boolean parseBool(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1", "enable", "enabled" -> true;
            case "false", "off", "no", "0", "disable", "disabled" -> false;
            default -> null;
        };
    }

    /** Parses {@code #RRGGBB}, {@code #AARRGGBB} or a decimal integer into an ARGB int. */
    private static int parseColor(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            String hex = trimmed.substring(1);
            if (hex.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
            throw new IllegalArgumentException("Bad hex colour: " + value);
        }
        return (int) Long.parseLong(trimmed);
    }

    private static String version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("logvex-crystal")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    /** Sends a local-only message to the player's chat (never the server). */
    public void printLocal(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    /** Exposes the colour helper for tests. */
    static int colorOf(int r, int g, int b) {
        return ColorUtil.rgb(r, g, b);
    }
}
