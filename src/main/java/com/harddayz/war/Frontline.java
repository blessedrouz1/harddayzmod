package com.harddayz.war;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.harddayz.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

/**
 * Рубежи: точки захвата. Фракция, которая одна стоит у рубежа 30 секунд, захватывает его.
 * Раз в минуту фракции получают доход (база + за каждый рубеж) и очки за удержание.
 * Победа — набрать нужные очки или остаться единственной фракцией со штабами.
 */
public final class Frontline {
    public static final int RADIUS = 12;
    public static final int CAPTURE_SECONDS = 30;
    public static final int BASE_INCOME = 100;
    public static final int POINT_INCOME = 150;

    /** Рубеж (хранится в War). */
    public static final class Point {
        public int x;
        public int y;
        public int z;
        public String name = "";
        public @Nullable String owner;
        public @Nullable String capturing;
        public int progress;

        BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();

    private Frontline() {
    }

    public static void add(BlockPos pos) {
        List<Point> pts = War.points();
        Point p = new Point();
        p.x = pos.getX();
        p.y = pos.getY();
        p.z = pos.getZ();
        p.name = String.valueOf((char) ('A' + pts.size() % 26));
        pts.add(p);
        War.save();
        War.broadcast(Component.translatable("message.harddayz.point_added", p.name, p.x, p.y, p.z).withStyle(ChatFormatting.GOLD));
    }

    private static BossEvent.BossBarColor color(@javax.annotation.Nullable War.Faction f) {
        if (f == null) {
            return BossEvent.BossBarColor.WHITE;
        }
        return switch (f.id) {
            case "blue" -> BossEvent.BossBarColor.BLUE;
            case "red" -> BossEvent.BossBarColor.RED;
            case "green" -> BossEvent.BossBarColor.GREEN;
            default -> BossEvent.BossBarColor.YELLOW;
        };
    }

    /** Раз в секунду. */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        boolean changed = false;
        for (Point p : War.points().toArray(new Point[0])) {
            BlockPos pos = p.pos();
            if (level.isLoaded(pos) && !level.getBlockState(pos).is(ModBlocks.FRONTLINE.get())) {
                War.points().remove(p);
                ServerBossEvent bar = BARS.remove(p.name);
                if (bar != null) {
                    bar.removeAllPlayers();
                }
                changed = true;
                continue;
            }
            // кто у рубежа
            War.Faction present = null;
            boolean contested = false;
            List<ServerPlayer> near = level.getPlayers(pl -> !pl.isSpectator() && pl.isAlive() && pos.distToCenterSqr(pl.position()) < RADIUS * RADIUS);
            for (ServerPlayer pl : near) {
                War.Faction f = War.of(pl.getUUID());
                if (f == null || f.defeated) {
                    continue;
                }
                if (present == null) {
                    present = f;
                } else if (present != f) {
                    contested = true;
                }
            }
            if (!contested && present != null && !present.id.equals(p.owner)) {
                if (!present.id.equals(p.capturing)) {
                    p.capturing = present.id;
                    p.progress = 0;
                }
                p.progress++;
                if (p.progress >= CAPTURE_SECONDS) {
                    p.owner = present.id;
                    present.captured++;
                    p.capturing = null;
                    p.progress = 0;
                    War.broadcast(Component.translatable("message.harddayz.point_captured", p.name, present.title()).withStyle(ChatFormatting.GOLD));
                    level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 3f, 1f);
                }
                changed = true;
            } else if (!contested && present == null && p.progress > 0) {
                p.progress--; // ушли — прогресс тает
                changed = true;
            }
            // столб частиц цвета владельца
            War.Faction owner = p.owner == null ? null : War.byId(p.owner);
            int rgb = owner == null ? 0xE0E0E0 : switch (owner.id) {
                case "blue" -> 0x5070FF;
                case "red" -> 0xFF4040;
                case "green" -> 0x40E040;
                default -> 0xFFE040;
            };
            for (int i = 0; i < 6; i++) {
                com.harddayz.Compat.particles(level, new DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(rgb).toVector3f(), 2.0f), p.x + 0.5, p.y + 1.5 + i * 2, p.z + 0.5, 2, 0.15, 0.4, 0.15, 0);
            }
            bossBar(p, level, near, contested);
        }
        // раз в минуту: доход и очки
        if (now % 1200 < 20) {
            for (War.Faction f : War.factions()) {
                if (f.defeated || f.members.isEmpty()) {
                    continue;
                }
                long held = War.points().stream().filter(p -> f.id.equals(p.owner)).count();
                int income = BASE_INCOME + (int) held * POINT_INCOME;
                f.budget += income;
                f.score += (int) held;
                for (ServerPlayer m : Supply.members(server, f)) {
                    m.sendSystemMessage(Component.translatable("message.harddayz.income", income, held, f.score, War.targetScore())
                            .withStyle(ChatFormatting.DARK_GREEN));
                }
            }
            changed = true;
            checkVictory();
        }
        if (changed) {
            War.save();
        }
    }

    private static void bossBar(Point p, ServerLevel level, List<ServerPlayer> near, boolean contested) {
        ServerBossEvent bar = BARS.computeIfAbsent(p.name, k -> new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.NOTCHED_10));
        War.Faction owner = p.owner == null ? null : War.byId(p.owner);
        War.Faction cap = p.capturing == null ? null : War.byId(p.capturing);
        Component title;
        if (contested) {
            title = Component.translatable("bar.harddayz.contested", p.name);
        } else if (cap != null && p.progress > 0) {
            title = Component.translatable("bar.harddayz.capturing", p.name, cap.title());
        } else if (owner != null) {
            title = Component.translatable("bar.harddayz.held", p.name, owner.title());
        } else {
            title = Component.translatable("bar.harddayz.neutral", p.name);
        }
        bar.setName(title);
        bar.setColor(color(cap != null && p.progress > 0 ? cap : owner));
        bar.setProgress(cap != null && p.progress > 0 ? p.progress / (float) CAPTURE_SECONDS : owner != null ? 1f : 0f);
        // показывать тем, кто рядом
        for (ServerPlayer pl : List.copyOf(bar.getPlayers())) {
            if (!near.contains(pl) && p.pos().distToCenterSqr(pl.position()) > (RADIUS + 8) * (RADIUS + 8)) {
                bar.removePlayer(pl);
            }
        }
        for (ServerPlayer pl : near) {
            bar.addPlayer(pl);
        }
    }

    /** Победа: набрали очки или остались одни со штабами. */
    public static void checkVictory() {
        if (!War.active()) {
            return;
        }
        War.Faction winner = null;
        for (War.Faction f : War.factions()) {
            if (f.score >= War.targetScore()) {
                winner = f;
            }
        }
        if (War.siege()) {
            int total = War.objectsTotal();
            if (total >= 3 && War.objectsLost() >= Math.ceil(total * 0.8)) {
                winner = War.attack(); // город разрушен
            } else if (War.siegeLeft() <= 0) {
                winner = War.defense(); // город выстоял
            } else if (War.attack() != null && War.attack().defeated) {
                winner = War.defense(); // штаб атаки найден и уничтожен
            }
        }
        if (winner == null && !War.siege()) {
            List<War.Faction> alive = War.factions().stream().filter(f -> !f.defeated && !f.members.isEmpty()).toList();
            boolean anyHq = War.factions().stream().anyMatch(f -> f.hadHq);
            if ((anyHq || War.livesMode()) && alive.size() == 1) {
                winner = alive.get(0);
            }
        }
        if (winner != null) {
            War.broadcast(Component.translatable("message.harddayz.victory", winner.title(), winner.score).withStyle(ChatFormatting.GOLD,
                    ChatFormatting.BOLD));
            for (ServerBossEvent b : BARS.values()) {
                b.removeAllPlayers();
            }
            BARS.clear();
            War.sendSummary(winner);
            War.end();
            War.sendLives();
        }
    }

    public static void clear() {
        for (ServerBossEvent b : BARS.values()) {
            b.removeAllPlayers();
        }
        BARS.clear();
    }
}
