package com.harddayz.war;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.harddayz.HardDayz;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Состояние войны на сервере: фракции, участники, штабы, бюджет. Хранится в папке мира
 * (harddayz_war.json). Свой/чужой для ПВО, РЭБ, мин и пультов определяется здесь.
 */
public final class War {
    public static final class Faction {
        public String id;
        public String name;
        public String color;
        public Set<String> members = new LinkedHashSet<>();
        public @Nullable String commander;
        public int budget;
        /** Штабы: первый — главный, остальные — резервные. */
        public List<int[]> hqs = new ArrayList<>();
        public boolean defeated;
        public boolean hadHq;
        public List<Supply.Order> orders = new ArrayList<>();
        public List<int[]> warehouses = new ArrayList<>();
        public boolean kitGiven;
        public int score;
        /** Режим «жизни»: сколько смертей ещё может позволить себе команда. */
        public int lives;
        // статистика матча
        public int kills;
        public int deaths;
        public int lost;
        public int destroyed;
        public int spent;
        public int captured;

        Faction() {
        }

        Faction(String id, String name, ChatFormatting color, int budget) {
            this.id = id;
            this.name = name;
            this.color = color.name();
            this.budget = budget;
        }

        public ChatFormatting format() {
            try {
                return ChatFormatting.valueOf(color.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ChatFormatting.WHITE;
            }
        }

        public Component title() {
            return Component.literal(name).withStyle(format(), ChatFormatting.BOLD);
        }

        public boolean has(UUID u) {
            return members.contains(u.toString());
        }
    }

    static final class Data {
        boolean active;
        @Nullable String creator;
        int startBudget = 5000;
        List<Faction> factions = new ArrayList<>();
        java.util.Map<String, List<int[]>> sirens = new java.util.HashMap<>();
        List<Frontline.Point> points = new ArrayList<>();
        int targetScore = 30;
        /** "hq" — штабы и рубежи; "lives" — общий запас жизней команды. */
        String mode = "hq";
    }

    public static List<Frontline.Point> points() {
        if (data.points == null) {
            data.points = new ArrayList<>();
        }
        return data.points;
    }

    public static int targetScore() {
        return data.targetScore <= 0 ? 30 : data.targetScore;
    }

    public static void setTargetScore(int v) {
        data.targetScore = v;
        save();
    }

    public static java.util.Map<String, List<int[]>> sirens() {
        if (data.sirens == null) {
            data.sirens = new java.util.HashMap<>();
        }
        return data.sirens;
    }

    public static void addSiren(String key, BlockPos pos) {
        sirens().computeIfAbsent(key, k -> new ArrayList<>()).add(new int[] {pos.getX(), pos.getY(), pos.getZ()});
        save();
    }

    public static final int MAX_HQ = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static @Nullable MinecraftServer server;

    private War() {
    }

    // ------------------------------------------------------------------ загрузка/сохранение

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("harddayz_war.json");
    }

    public static void load(MinecraftServer s) {
        server = s;
        data = new Data();
        Path f = file(s);
        if (Files.exists(f)) {
            try {
                Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                if (d != null) {
                    data = d;
                }
            } catch (IOException | RuntimeException e) {
                HardDayz.LOGGER.warn("Не удалось прочитать состояние войны", e);
            }
        }
        syncTeams();
    }

    public static void save() {
        if (server == null) {
            return;
        }
        try {
            Files.writeString(file(server), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            HardDayz.LOGGER.warn("Не удалось сохранить состояние войны", e);
        }
    }

    public static void unload() {
        save();
        server = null;
        data = new Data();
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    // ------------------------------------------------------------------ запросы

    public static boolean active() {
        return data.active;
    }

    public static int startBudget() {
        return data.startBudget;
    }

    public static boolean isCreator(UUID u) {
        return u.toString().equals(data.creator);
    }

    public static List<Faction> factions() {
        return data.factions;
    }

    /** Номер фракции игрока для опознавательных знаков (синие 0, красные 1, зелёные 2, жёлтые 3), или -1. */
    public static int factionIndex(@Nullable UUID player) {
        Faction f = of(player);
        if (f == null) {
            return -1;
        }
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i][0].equals(f.id)) {
                return i;
            }
        }
        return -1;
    }

    public static @Nullable Faction byId(String id) {
        for (Faction f : data.factions) {
            if (f.id.equalsIgnoreCase(id)) {
                return f;
            }
        }
        return null;
    }

    public static @Nullable Faction of(@Nullable UUID player) {
        if (player == null || !data.active) {
            return null;
        }
        for (Faction f : data.factions) {
            if (f.has(player)) {
                return f;
            }
        }
        return null;
    }

    /** Свои: один и тот же игрок или игроки одной фракции. */
    public static boolean allied(@Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        Faction fa = of(a);
        return fa != null && fa.has(b);
    }

    public static boolean isCommander(ServerPlayer p) {
        Faction f = of(p.getUUID());
        return f != null && p.getUUID().toString().equals(f.commander);
    }

    // ------------------------------------------------------------------ управление матчем

    private static final String[][] PRESETS = {{"blue", "Синие", "blue"}, {"red", "Красные", "red"}, {"green", "Зелёные", "green"},
            {"yellow", "Жёлтые", "yellow"}};

    public static boolean livesMode() {
        return "lives".equals(data.mode);
    }

    public static void create(ServerPlayer by, int count, int budget) {
        create(by, count, budget, 0);
    }

    /** {@code lives > 0} — режим «жизни». */
    public static void create(ServerPlayer by, int count, int budget, int lives) {
        end();
        data.active = true;
        data.mode = lives > 0 ? "lives" : "hq";
        data.creator = by.getUUID().toString();
        data.startBudget = budget;
        for (int i = 0; i < Math.max(2, Math.min(4, count)); i++) {
            String[] p = PRESETS[i];
            data.factions.add(new Faction(p[0], p[1], ChatFormatting.valueOf(p[2].toUpperCase(java.util.Locale.ROOT)), budget));
            data.factions.get(data.factions.size() - 1).lives = lives;
        }
        syncTeams();
        save();
    }

    /** Итоги матча всем игрокам (экран конца матча). */
    public static void sendSummary(@Nullable Faction winner) {
        if (server == null) {
            return;
        }
        List<String> rows = new ArrayList<>();
        for (Faction f : data.factions) {
            rows.add(f.id + ";" + f.title().getString() + ";" + f.score + ";" + f.kills + ";" + f.deaths + ";" + f.destroyed + ";" + f.lost + ";"
                    + f.spent + ";" + f.captured + ";" + f.members.size());
        }
        var msg = new com.harddayz.network.Net.MatchEnd(winner == null ? "" : winner.id, rows);
        for (var p : server.getPlayerList().getPlayers()) {
            com.harddayz.network.Net.toPlayer(p, msg);
        }
    }

    /** Разослать жизни команд (режим «жизни»). */
    public static void sendLives() {
        if (server == null) {
            return;
        }
        List<String> rows = new ArrayList<>();
        if (active() && livesMode()) {
            for (Faction f : data.factions) {
                rows.add(f.id + ";" + f.title().getString() + ";" + f.lives);
            }
        }
        var msg = new com.harddayz.network.Net.Lives(rows);
        for (var p : server.getPlayerList().getPlayers()) {
            com.harddayz.network.Net.toPlayer(p, msg);
        }
    }

    public static void end() {
        if (server != null) {
            Scoreboard sb = server.getScoreboard();
            for (Faction f : data.factions) {
                PlayerTeam t = sb.getPlayerTeam(teamName(f));
                if (t != null) {
                    sb.removePlayerTeam(t);
                }
            }
        }
        var keep = sirens();
        data = new Data();
        data.sirens = keep;
        save();
    }

    public static void join(ServerPlayer p, Faction f) {
        for (Faction o : data.factions) {
            o.members.remove(p.getUUID().toString());
            if (p.getUUID().toString().equals(o.commander)) {
                o.commander = o.members.isEmpty() ? null : o.members.iterator().next();
            }
        }
        f.members.add(p.getUUID().toString());
        if (f.commander == null) {
            f.commander = p.getUUID().toString(); // первый вступивший — командир
        }
        syncTeams();
        save();
    }

    public static void leave(ServerPlayer p) {
        for (Faction o : data.factions) {
            o.members.remove(p.getUUID().toString());
            if (p.getUUID().toString().equals(o.commander)) {
                o.commander = o.members.isEmpty() ? null : o.members.iterator().next();
            }
        }
        if (server != null && server.getScoreboard().getPlayersTeam(p.getScoreboardName()) != null) {
            server.getScoreboard().removePlayerFromTeam(p.getScoreboardName());
        }
        save();
    }

    // ------------------------------------------------------------------ штабы

    /** Зарегистрировать штаб. Возвращает номер (1 — главный) или 0, если мест нет. */
    public static int addHq(Faction f, BlockPos pos) {
        if (f.hqs.size() >= MAX_HQ) {
            return 0;
        }
        f.hqs.add(new int[] {pos.getX(), pos.getY(), pos.getZ()});
        f.hadHq = true;
        f.defeated = false;
        save();
        return f.hqs.size();
    }

    public static @Nullable Faction hqOwner(BlockPos pos) {
        for (Faction f : data.factions) {
            for (int[] h : f.hqs) {
                if (h[0] == pos.getX() && h[1] == pos.getY() && h[2] == pos.getZ()) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Штаб уничтожен/снят. Возвращает фракцию-владельца или null. */
    public static @Nullable Faction removeHq(BlockPos pos) {
        for (Faction f : data.factions) {
            for (int i = 0; i < f.hqs.size(); i++) {
                int[] h = f.hqs.get(i);
                if (h[0] == pos.getX() && h[1] == pos.getY() && h[2] == pos.getZ()) {
                    f.hqs.remove(i);
                    if (f.hqs.isEmpty() && f.hadHq) {
                        f.defeated = true;
                    }
                    save();
                    return f;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ команды игры: цвет ников и без огня по своим

    private static String teamName(Faction f) {
        return "hd_" + f.id;
    }

    public static void syncTeams() {
        if (server == null || !data.active) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        for (Faction f : data.factions) {
            PlayerTeam t = sb.getPlayerTeam(teamName(f));
            if (t == null) {
                t = sb.addPlayerTeam(teamName(f));
            }
            t.setColor(java.util.Optional.of(net.minecraft.world.scores.TeamColor.valueOf(f.format().name())));
            t.setPlayerPrefix(Component.literal("[" + f.name + "] ").withStyle(f.format()));
            t.setAllowFriendlyFire(true);
            for (String m : f.members) {
                ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(m));
                if (p != null) {
                    sb.addPlayerToTeam(p.getScoreboardName(), t);
                }
            }
        }
    }

    public static void broadcast(Component msg) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }
}
