package com.harddayz.war;

import com.harddayz.registry.ModBlocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/** Команды /war, загрузка состояния, проверка штабов. */
public final class WarEvents {
    private WarEvents() {
    }

    public static void init() {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener((ServerStartedEvent e) -> {
            War.load(e.getServer());
            com.harddayz.world.FrontWorld.started(e.getServer());
        });
        bus.addListener((ServerStoppingEvent e) -> {
            War.unload();
            RadarNet.clear();
            Frontline.clear();
        });
        bus.addListener(WarEvents::commands);
        bus.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                tick(e.getServer());
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            War.syncTeams();
            War.sendLives();
            if (e.getEntity() instanceof ServerPlayer sp) {
                var g = com.harddayz.world.FrontWorld.generator(sp.getServer());
                com.harddayz.network.Net.toPlayer(sp, new com.harddayz.network.Net.FrontInfo(g != null, g == null ? 0 : g.seed()));
            }
        });
        // подготовка: игроки друг друга не ранят
        bus.addListener((net.minecraftforge.event.entity.living.LivingAttackEvent e) -> {
            if (War.prep() && e.getEntity() instanceof ServerPlayer v && e.getSource().getEntity() != null && e.getSource().getEntity() != v) {
                e.setCanceled(true);
            }
        });
        bus.addListener((net.minecraftforge.event.entity.living.LivingDeathEvent e) -> {
            if (onDeath(e)) {
                e.setCanceled(true);
            }
        });
    }

    /** true — отменить смерть (игрок упал раненым). */
    private static boolean onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        {
            if (e.getEntity() instanceof ServerPlayer p0 && Medic.tryDown(p0, e.getSource())) {
                return true; // упал раненым — ещё не смерть
            }
            if (!(e.getEntity() instanceof ServerPlayer dead) || !War.active()) {
                return false;
            }
            War.Faction df = War.of(dead.getUUID());
            if (df != null) {
                df.deaths++;
                if (War.livesMode() && !df.defeated) {
                    df.lives = Math.max(0, df.lives - 1);
                    War.broadcast(Component.translatable("message.harddayz.lives_left", df.title(), df.lives)
                            .withStyle(df.lives <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW));
                    War.sendLives();
                    if (df.lives <= 0) {
                        df.defeated = true;
                        War.broadcast(Component.translatable("message.harddayz.lives_out", df.title()).withStyle(ChatFormatting.DARK_RED,
                                ChatFormatting.BOLD));
                        Frontline.checkVictory();
                    }
                }
            }
            var src = e.getSource().getEntity();
            java.util.UUID killer = src instanceof ServerPlayer sp ? sp.getUUID() : src instanceof com.harddayz.entity.Threat t ? t.owner()
                    : src instanceof com.harddayz.entity.Vehicle v ? v.owner() : src instanceof com.harddayz.entity.Soldier s ? s.side() : null;
            War.Faction kf = War.of(killer);
            if (kf != null && kf != df) {
                kf.kills++;
            }
            War.save();
            return false;
        }
    }

    // ------------------------------------------------------------------ проверка штабов: уничтожен — снимаем

    /** Метки карты для игрока: точки, свои игроки, отряды, техника, штабы, замеченные враги, засечки РЛС. */
    private static java.util.List<String> mapState(ServerPlayer p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        ServerLevel level = p.getServer().overworld();
        War.Faction f = War.of(p.getUUID());
        for (Frontline.Point pt : War.points()) {
            War.Faction o = pt.owner == null ? null : War.byId(pt.owner);
            out.add("point;" + pt.x + ";" + pt.z + ";" + (o == null ? -1 : War.presetIndex(o)) + ";" + pt.name.replace(';', ' '));
        }
        if (War.siege() && War.city() != null) {
            out.add("city;" + War.city()[0] + ";" + War.city()[1] + ";" + War.city()[2] + ";");
            if (f != null && f == War.defense()) {
                for (int[] o : War.objects()) {
                    out.add("object;" + o[0] + ";" + o[2] + ";0;");
                }
            }
        }
        if (f == null) {
            return out;
        }
        for (int i = 0; i < f.hqs.size(); i++) {
            out.add("hq;" + f.hqs.get(i)[0] + ";" + f.hqs.get(i)[2] + ";" + War.presetIndex(f) + ";");
        }
        for (int[] w : f.warehouses) {
            out.add("store;" + w[0] + ";" + w[2] + ";" + War.presetIndex(f) + ";");
        }
        for (ServerPlayer m : Supply.members(p.getServer(), f)) {
            if (m != p && m.level() == level) {
                out.add("ally;" + m.getBlockX() + ";" + m.getBlockZ() + ";" + (int) m.getYRot() + ";" + m.getScoreboardName());
            }
        }
        for (BotCommander.Squad s : War.squads()) {
            if (s.men > 0 && s.faction.equals(f.id)) {
                out.add("squad;" + (int) s.x + ";" + (int) s.z + ";" + s.men + ";");
            } else if (s.men > 0 && BotCommander.spotted(level, f, s)) {
                out.add("enemy;" + (int) s.x + ";" + (int) s.z + ";" + s.men + ";");
            }
        }
        int n = 0;
        for (var l : level.getEntities(com.harddayz.registry.ModEntities.LAUNCHER.get(), l -> !l.wrecked() && l.owner() != null && f.has(l.owner()))) {
            if (n++ < 60) {
                out.add("vehicle;" + l.getBlockX() + ";" + l.getBlockZ() + ";" + (int) l.getYRot() + ";" + l.kind().name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        String key = RadarNet.key(p.getUUID());
        long now = level.getGameTime();
        for (var c : RadarNet.contacts(key, now)) {
            out.add("contact;" + (int) c.pos.x + ";" + (int) c.pos.z + ";" + (now - c.seen) + ";" + c.type);
        }
        for (var l : RadarNet.launches(key, now)) {
            out.add("launch;" + (int) l.pos.x + ";" + (int) l.pos.z + ";" + (int) Math.ceil(l.err) + ";" + l.type);
        }
        return out;
    }

    /** Отсчёт подготовки (раз в секунду). */
    private static void tickPrep() {
        if (!War.prep()) {
            return;
        }
        int before = War.prepLeft();
        War.setPrep(before - 20);
        int left = War.prepLeft();
        for (int s : new int[] {600, 300, 180, 60, 30, 10}) {
            if (before > s * 20 && left <= s * 20) {
                War.broadcast(Component.translatable("message.harddayz.prep_left", s / 60, String.format("%02d", s % 60)).withStyle(ChatFormatting.YELLOW));
            }
        }
        if (left <= 0) {
            War.broadcast(Component.translatable("message.harddayz.prep_over").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            War.save();
        }
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        RadarNet.tick(server);
        if (server.getTickCount() % 20 == 7) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                com.harddayz.network.Net.toPlayer(p, new com.harddayz.network.Net.MapState(mapState(p)));
            }
        }
        if (server.getTickCount() % 40 == 0) {
            ServerLevel lv = server.overworld();
            for (var list : War.sirens().values()) {
                list.removeIf(h -> {
                    BlockPos pos = new BlockPos(h[0], h[1], h[2]);
                    return lv.isLoaded(pos) && !lv.getBlockState(pos).is(ModBlocks.SIREN.get());
                });
            }
        }
        if (!War.active()) {
            return;
        }
        BotCommander.tickStrikes(server.overworld());
        if (server.getTickCount() % 20 == 0) {
            tickPrep();
            if (!War.prep()) {
                BotCommander.tick(server);
            }
            Supply.tick(server);
            Frontline.tick(server);
        }
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        if (War.siege()) {
            for (int[] h : new java.util.ArrayList<>(War.objects())) {
                BlockPos pos = new BlockPos(h[0], h[1], h[2]);
                if (level.isLoaded(pos) && !level.getBlockState(pos).is(ModBlocks.CITY_OBJECT.get())) {
                    War.objects().remove(h);
                    War.objectLost();
                    War.broadcast(Component.translatable("message.harddayz.object_lost", War.objectsAlive(), War.objectsTotal())
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
            int before = War.siegeLeft();
            if (!War.prep()) {
                War.tickSiege(40);
            }
            int left = War.siegeLeft();
            for (int m : new int[] {30, 10, 5, 1}) {
                if (before > m * 1200 && left <= m * 1200) {
                    War.broadcast(Component.translatable("message.harddayz.siege_time", m).withStyle(ChatFormatting.GOLD));
                }
            }
            if (server.getTickCount() % 200 == 0) {
                Component bar = Component.translatable("hud.harddayz.siege", War.objectsAlive(), War.objectsTotal(), left / 1200,
                        String.format("%02d", left / 20 % 60)).withStyle(ChatFormatting.YELLOW);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (War.of(p.getUUID()) != null) {
                        p.displayClientMessage(bar, true);
                    }
                }
            }
        }
        for (War.Faction f : War.factions()) {
            f.warehouses.removeIf(h -> {
                BlockPos pos = new BlockPos(h[0], h[1], h[2]);
                return level.isLoaded(pos) && !level.getBlockState(pos).is(ModBlocks.WAREHOUSE.get());
            });
            for (int[] h : new java.util.ArrayList<>(f.hqs)) {
                BlockPos pos = new BlockPos(h[0], h[1], h[2]);
                if (level.isLoaded(pos) && !level.getBlockState(pos).is(ModBlocks.HQ.get())) {
                    War.removeHq(pos);
                    War.broadcast(Component.translatable("message.harddayz.hq_lost", f.title(), f.hqs.size()).withStyle(ChatFormatting.RED));
                    if (f.defeated) {
                        War.broadcast(Component.translatable("message.harddayz.faction_defeated", f.title()).withStyle(ChatFormatting.DARK_RED,
                                ChatFormatting.BOLD));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ /war

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("war")
                .executes(WarEvents::status)
                .then(Commands.literal("status").executes(WarEvents::status))
                .then(Commands.literal("create")
                        .executes(c -> create(c, 2, 5000))
                        .then(Commands.argument("factions", IntegerArgumentType.integer(2, 4))
                                .executes(c -> create(c, IntegerArgumentType.getInteger(c, "factions"), 5000))
                                .then(Commands.argument("budget", IntegerArgumentType.integer(0, 1000000))
                                        .executes(c -> create(c, IntegerArgumentType.getInteger(c, "factions"), IntegerArgumentType.getInteger(c, "budget")))
                                        .then(Commands.literal("lives").then(Commands.argument("lives", IntegerArgumentType.integer(1, 10000))
                                                .executes(c -> create(c, IntegerArgumentType.getInteger(c, "factions"),
                                                        IntegerArgumentType.getInteger(c, "budget"), IntegerArgumentType.getInteger(c, "lives"))))))))
                .then(Commands.literal("menu").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    com.harddayz.network.Net.toPlayer(p, menuState(p));
                    return 1;
                }))
                .then(Commands.literal("siege")
                        .executes(c -> createSiege(c, 8000, 60))
                        .then(Commands.argument("budget", IntegerArgumentType.integer(0, 1000000))
                                .executes(c -> createSiege(c, IntegerArgumentType.getInteger(c, "budget"), 60))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(5, 600))
                                        .executes(c -> createSiege(c, IntegerArgumentType.getInteger(c, "budget"), IntegerArgumentType.getInteger(c, "minutes"))))))
                .then(Commands.literal("city").then(Commands.argument("radius", IntegerArgumentType.integer(30, 2000)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!War.siege()) {
                        c.getSource().sendFailure(Component.translatable("message.harddayz.object_no_siege"));
                        return 0;
                    }
                    if (!War.isCreator(p.getUUID()) && !c.getSource().hasPermission(2)) {
                        c.getSource().sendFailure(Component.translatable("message.harddayz.city_not_creator"));
                        return 0;
                    }
                    int r = IntegerArgumentType.getInteger(c, "radius");
                    War.setCity(p.blockPosition(), r);
                    War.broadcast(Component.translatable("message.harddayz.city_set", p.getBlockX(), p.getBlockZ(), r).withStyle(ChatFormatting.GOLD));
                    return 1;
                })))
                .then(Commands.literal("join").then(Commands.argument("faction", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(War.factions().stream().map(f -> f.id), b))
                        .executes(WarEvents::join)))
                .then(Commands.literal("leave").executes(c -> {
                    War.leave(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.translatable("message.harddayz.war_left"), false);
                    return 1;
                }))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(WarEvents::invite)))
                .then(Commands.literal("end").executes(WarEvents::end))
                .then(Commands.literal("off").executes(WarEvents::end))
                .then(Commands.literal("target").then(Commands.argument("score", IntegerArgumentType.integer(1, 10000)).executes(c -> {
                    War.setTargetScore(IntegerArgumentType.getInteger(c, "score"));
                    War.broadcast(Component.translatable("message.harddayz.score_target_set", War.targetScore()).withStyle(ChatFormatting.GOLD));
                    return 1;
                }))));
    }

    /** Что показать по J: выбор режима или ход идущего матча. */
    public static com.harddayz.network.Net.OpenWarMenu menuState(ServerPlayer p) {
        if (!War.active()) {
            return new com.harddayz.network.Net.OpenWarMenu(false, true, java.util.List.of());
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        String mode = War.coop() ? "screen.harddayz.war_mode.3" : War.siege() ? "screen.harddayz.war_mode.2" : War.livesMode() ? "screen.harddayz.war_mode.1" : "screen.harddayz.war_mode.0";
        lines.add(Component.translatable(mode).getString());
        if (War.siege()) {
            int left = War.siegeLeft();
            lines.add(Component.translatable("hud.harddayz.siege", War.objectsAlive(), War.objectsTotal(), left / 1200, String.format("%02d", left / 20 % 60))
                    .getString());
            int[] c = War.city();
            if (c != null) {
                lines.add(Component.translatable("screen.harddayz.match_city", c[0], c[1], c[2]).getString());
            }
        } else if (!War.livesMode()) {
            lines.add(Component.translatable("screen.harddayz.match_target", War.targetScore()).getString());
        }
        if (War.prep()) {
            lines.add("§e" + Component.translatable("screen.harddayz.match_prep", War.prepLeft() / 1200, String.format("%02d", War.prepLeft() / 20 % 60)).getString());
        }
        for (Frontline.Point pt : War.points()) {
            War.Faction o = pt.owner == null ? null : War.byId(pt.owner);
            String use = pt.kind == null ? "" : "  §8" + Component.translatable("point.harddayz." + pt.kind.toLowerCase(java.util.Locale.ROOT)).getString();
            lines.add((o == null ? "§7○ " : "§" + o.format().getChar() + "● ") + pt.name + use);
        }
        lines.add("");
        War.Faction mine = War.of(p.getUUID());
        for (War.Faction f : War.factions()) {
            String hq = f == mine ? " · " + Component.translatable("screen.harddayz.match_hq", f.hqs.size()).getString() : "";
            String lives = War.livesMode() ? " · " + Component.translatable("screen.harddayz.match_lives", f.lives).getString() : "";
            lines.add("§" + f.format().getChar() + "§l" + f.name + "§r  " + Component.translatable("screen.harddayz.match_row", f.members.size() + (f.bots > 0 ? "+" + BotCommander.men(f) : ""), f.score,
                    f.kills, f.destroyed, f.lost).getString() + lives + hq + (f.defeated ? "  §c" + Component.translatable("message.harddayz.war_defeated").getString() : "")
                    + (f == mine ? "  §a◀" : ""));
        }
        return new com.harddayz.network.Net.OpenWarMenu(true, War.isCreator(p.getUUID()) || p.hasPermissions(2), lines);
    }

    public static void endFromMenu(ServerPlayer p) {
        if (War.active() && (War.isCreator(p.getUUID()) || p.hasPermissions(2))) {
            War.broadcast(Component.translatable("message.harddayz.match_ended_by", p.getDisplayName()).withStyle(ChatFormatting.GOLD));
            War.sendSummary(null);
            War.end();
            War.sendLives();
        }
    }

    /** Матч из меню: те же правила, что у команд. */
    public static void setup(ServerPlayer p, com.harddayz.network.Net.WarSetup s) {
        if (War.active()) {
            // матч уже идёт — заново из меню не запускаем, сначала его надо закончить
            p.displayClientMessage(Component.translatable("message.harddayz.match_running").withStyle(ChatFormatting.RED), true);
            com.harddayz.network.Net.toPlayer(p, menuState(p));
            return;
        }
        int budget = Math.max(0, Math.min(1000000, s.budget()));
        int bots = Math.max(0, Math.min(32, s.bots()));
        int dif = Math.max(1, Math.min(3, s.difficulty()));
        if (s.mode() == 3) {
            if (com.harddayz.world.FrontWorld.generator(p.getServer()) == null) {
                p.sendSystemMessage(Component.translatable("message.harddayz.coop_need_map").withStyle(ChatFormatting.RED));
                return;
            }
            War.createCoop(p, budget);
            War.setBots(bots, dif);
            com.harddayz.world.FrontWorld.setup(p.getServer());
            BotCommander.fortify(p.getServer().overworld(), War.enemyBots());
            War.broadcast(Component.translatable("message.harddayz.coop_created", p.getDisplayName(), dif).withStyle(ChatFormatting.GOLD));
            War.broadcast(Component.translatable("message.harddayz.coop_rules", War.targetScore()).withStyle(ChatFormatting.GRAY));
        } else if (s.mode() == 2) {
            int minutes = Math.max(5, Math.min(600, s.minutes()));
            War.createSiege(p, budget, minutes);
            War.broadcast(Component.translatable("message.harddayz.siege_created", p.getDisplayName(), minutes).withStyle(ChatFormatting.GOLD));
            if (!com.harddayz.world.FrontWorld.setup(p.getServer())) { // на фронтовой карте город уже известен
                War.setCity(p.blockPosition(), Math.max(30, Math.min(2000, s.radius())));
                War.broadcast(Component.translatable("message.harddayz.city_set", p.getBlockX(), p.getBlockZ(), s.radius()).withStyle(ChatFormatting.GOLD));
            }
            War.broadcast(Component.translatable("message.harddayz.siege_rules").withStyle(ChatFormatting.GRAY));
        } else {
            int lives = s.mode() == 1 ? Math.max(1, Math.min(10000, s.lives())) : 0;
            War.create(p, Math.max(2, Math.min(4, s.factions())), budget, lives);
            com.harddayz.world.FrontWorld.setup(p.getServer());
            War.sendLives();
            if (lives > 0) {
                War.broadcast(Component.translatable("message.harddayz.lives_mode", lives).withStyle(ChatFormatting.GOLD));
            }
            War.broadcast(Component.translatable("message.harddayz.war_created", p.getDisplayName(), budget).withStyle(ChatFormatting.GOLD));
        }
        if (s.mode() != 3) {
            War.setBots(bots, dif);
        }
        BotCommander.announce(bots);
        War.setPrep(Math.max(0, Math.min(30, s.prep())) * 1200);
        if (War.prep()) {
            War.broadcast(Component.translatable("message.harddayz.prep_start", s.prep()).withStyle(ChatFormatting.YELLOW));
        }
        War.broadcast(joinButtons());
    }

    private static int createSiege(CommandContext<CommandSourceStack> c, int budget, int minutes)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        War.createSiege(p, budget, minutes);
        com.harddayz.world.FrontWorld.setup(p.getServer());
        War.broadcast(Component.translatable("message.harddayz.siege_created", p.getDisplayName(), minutes).withStyle(ChatFormatting.GOLD));
        War.broadcast(Component.translatable("message.harddayz.siege_rules").withStyle(ChatFormatting.GRAY));
        War.broadcast(joinButtons());
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> c, int count, int budget) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return create(c, count, budget, 0);
    }

    private static int create(CommandContext<CommandSourceStack> c, int count, int budget, int lives)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        War.create(p, count, budget, lives);
        com.harddayz.world.FrontWorld.setup(p.getServer());
        War.sendLives();
        if (lives > 0) {
            War.broadcast(Component.translatable("message.harddayz.lives_mode", lives).withStyle(ChatFormatting.GOLD));
        }
        War.broadcast(Component.translatable("message.harddayz.war_created", p.getDisplayName(), budget).withStyle(ChatFormatting.GOLD));
        War.broadcast(joinButtons());
        return 1;
    }

    /** Строка с кнопками «вступить» во все фракции. */
    private static Component joinButtons() {
        MutableComponent line = Component.translatable("message.harddayz.war_choose").withStyle(ChatFormatting.GRAY);
        for (War.Faction f : War.factions()) {
            if (f.ai) {
                continue;
            }
            line.append("  ").append(Component.literal("[" + f.name + "]").withStyle(s -> s.withColor(f.format()).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/war join " + f.id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.harddayz.war_join_hover", f.name)))));
        }
        return line;
    }

    private static int join(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!War.active()) {
            c.getSource().sendFailure(Component.translatable("message.harddayz.war_none"));
            return 0;
        }
        War.Faction f = War.byId(StringArgumentType.getString(c, "faction"));
        if (f == null || f.ai) {
            c.getSource().sendFailure(Component.translatable("message.harddayz.war_no_faction"));
            return 0;
        }
        War.join(p, f);
        com.harddayz.world.FrontWorld.joined(p, f);
        if (!f.kitGiven) {
            // стартовый набор командиру: штабы, склад, снабжение, радар
            f.kitGiven = true;
            War.save();
            give(p, new net.minecraft.world.item.ItemStack(ModBlocks.HQ_ITEM.get(), War.MAX_HQ));
            give(p, new net.minecraft.world.item.ItemStack(ModBlocks.WAREHOUSE_ITEM.get()));
            give(p, new net.minecraft.world.item.ItemStack(com.harddayz.registry.ModItems.SUPPLY_TABLET.get()));
            give(p, new net.minecraft.world.item.ItemStack(com.harddayz.registry.ModItems.RADAR_DISPLAY.get()));
            give(p, new net.minecraft.world.item.ItemStack(ModBlocks.SIREN_ITEM.get()));
            p.sendSystemMessage(Component.translatable("message.harddayz.war_kit").withStyle(ChatFormatting.GOLD));
        } else {
            give(p, new net.minecraft.world.item.ItemStack(com.harddayz.registry.ModItems.SUPPLY_TABLET.get()));
        }
        War.broadcast(Component.translatable("message.harddayz.war_joined", p.getDisplayName(), f.title()));
        if (p.getUUID().toString().equals(f.commander)) {
            p.sendSystemMessage(Component.translatable("message.harddayz.war_commander").withStyle(ChatFormatting.GOLD));
        }
        return 1;
    }

    private static void give(ServerPlayer p, net.minecraft.world.item.ItemStack s) {
        if (!p.getInventory().add(s)) {
            p.spawnAtLocation(s);
        }
    }

    private static int invite(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        if (!War.active()) {
            c.getSource().sendFailure(Component.translatable("message.harddayz.war_none"));
            return 0;
        }
        target.sendSystemMessage(Component.translatable("message.harddayz.war_invited", c.getSource().getDisplayName()).withStyle(ChatFormatting.GOLD));
        target.sendSystemMessage(joinButtons());
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!War.active()) {
            c.getSource().sendFailure(Component.translatable("message.harddayz.war_none"));
            return 0;
        }
        if (!War.isCreator(p.getUUID()) && !c.getSource().hasPermission(2)) {
            c.getSource().sendFailure(Component.translatable("message.harddayz.war_no_rights"));
            return 0;
        }
        War.sendSummary(null);
        War.end();
        War.sendLives();
        War.broadcast(Component.translatable("message.harddayz.war_ended", p.getDisplayName()).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        if (!War.active()) {
            src.sendSuccess(() -> Component.translatable("message.harddayz.war_none_hint").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        src.sendSuccess(() -> Component.translatable("message.harddayz.war_status_title").withStyle(ChatFormatting.GOLD), false);
        var server = War.server();
        for (War.Faction f : War.factions()) {
            StringBuilder names = new StringBuilder();
            for (String m : f.members) {
                var pl = server == null ? null : server.getPlayerList().getPlayer(java.util.UUID.fromString(m));
                String n = pl != null ? pl.getScoreboardName() : m.substring(0, 8);
                if (m.equals(f.commander)) {
                    n = "★" + n;
                }
                names.append(names.length() == 0 ? "" : ", ").append(n);
            }
            Component line = Component.empty().append(f.title())
                    .append(Component.translatable("message.harddayz.war_status_score", f.score, War.targetScore()).withStyle(ChatFormatting.GOLD))
                    .append(War.livesMode() ? Component.translatable("message.harddayz.war_status_lives", f.lives).withStyle(ChatFormatting.RED)
                            : Component.empty())
                    .append(Component.translatable("message.harddayz.war_status_line", f.members.size(), f.budget, f.hqs.size(), War.MAX_HQ,
                            f.defeated ? Component.translatable("message.harddayz.war_defeated") : Component.empty()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(names.length() == 0 ? "" : "  " + names).withStyle(ChatFormatting.DARK_GRAY));
            src.sendSuccess(() -> line, false);
        }
        for (Frontline.Point p : War.points()) {
            War.Faction o = p.owner == null ? null : War.byId(p.owner);
            src.sendSuccess(() -> Component.translatable("message.harddayz.point_line", p.name, p.x, p.y, p.z,
                    o == null ? Component.translatable("message.harddayz.point_neutral") : o.title()).withStyle(ChatFormatting.GRAY), false);
        }
        src.sendSuccess(WarEvents::joinButtons, false);
        return 1;
    }
}
