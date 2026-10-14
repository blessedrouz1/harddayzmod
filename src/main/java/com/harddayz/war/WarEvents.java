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
        ServerStartedEvent.BUS.addListener(e -> War.load(e.getServer()));
        ServerStoppingEvent.BUS.addListener(e -> {
            War.unload();
            RadarNet.clear();
            Frontline.clear();
        });
        RegisterCommandsEvent.BUS.addListener(WarEvents::commands);
        TickEvent.ServerTickEvent.Post.BUS.addListener(WarEvents::tick);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e -> War.syncTeams());
        net.minecraftforge.event.entity.living.LivingDeathEvent.BUS.addListener(e -> {
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
                    : src instanceof com.harddayz.entity.Vehicle v ? v.owner() : null;
            War.Faction kf = War.of(killer);
            if (kf != null && kf != df) {
                kf.kills++;
            }
            War.save();
            return false;
        });
    }

    // ------------------------------------------------------------------ проверка штабов: уничтожен — снимаем

    private static void tick(TickEvent.ServerTickEvent.Post event) {
        var server = event.server();
        RadarNet.tick(server);
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
        if (server.getTickCount() % 20 == 0) {
            Supply.tick(server);
            Frontline.tick(server);
        }
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
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

    private static int create(CommandContext<CommandSourceStack> c, int count, int budget) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return create(c, count, budget, 0);
    }

    private static int create(CommandContext<CommandSourceStack> c, int count, int budget, int lives)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        War.create(p, count, budget, lives);
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
            line.append("  ").append(Component.literal("[" + f.name + "]").withStyle(s -> s.withColor(f.format()).withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand("/war join " + f.id))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.harddayz.war_join_hover", f.name)))));
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
        if (f == null) {
            c.getSource().sendFailure(Component.translatable("message.harddayz.war_no_faction"));
            return 0;
        }
        War.join(p, f);
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
            p.spawnAtLocation(p.level(), s);
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
        if (!War.isCreator(p.getUUID()) && !c.getSource().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
            c.getSource().sendFailure(Component.translatable("message.harddayz.war_no_rights"));
            return 0;
        }
        War.sendSummary(null);
        War.end();
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
                names.append(names.isEmpty() ? "" : ", ").append(n);
            }
            Component line = Component.empty().append(f.title())
                    .append(Component.translatable("message.harddayz.war_status_score", f.score, War.targetScore()).withStyle(ChatFormatting.GOLD))
                    .append(War.livesMode() ? Component.translatable("message.harddayz.war_status_lives", f.lives).withStyle(ChatFormatting.RED)
                            : Component.empty())
                    .append(Component.translatable("message.harddayz.war_status_line", f.members.size(), f.budget, f.hqs.size(), War.MAX_HQ,
                            f.defeated ? Component.translatable("message.harddayz.war_defeated") : Component.empty()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(names.isEmpty() ? "" : "  " + names).withStyle(ChatFormatting.DARK_GRAY));
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
