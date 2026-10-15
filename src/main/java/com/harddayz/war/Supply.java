package com.harddayz.war;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.harddayz.registry.ModBlocks;
import com.harddayz.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Снабжение фракции: каталог с ценами и временем доставки, заказы с таймером, доставка на склад.
 * Тяжёлую технику заказывает только командир, одновременно — не больше 3 тяжёлых заказов.
 */
public final class Supply {
    public record Entry(Supplier<Item> item, int count, int price, int seconds, boolean heavy) {
        public ItemStack stack() {
            return new ItemStack(item.get(), count);
        }
    }

    public static final List<Entry> CATALOG = List.of(
            // техника
            new Entry(() -> ModItems.GRAD.get(), 1, 1500, 60, true),
            new Entry(() -> ModItems.ISKANDER.get(), 1, 3000, 90, true),
            new Entry(() -> ModItems.SAM.get(), 1, 2000, 75, true),
            new Entry(() -> ModItems.S400.get(), 1, 3500, 100, true),
            new Entry(() -> ModItems.TOS.get(), 1, 2500, 80, true),
            new Entry(() -> ModItems.HIMARS.get(), 1, 2800, 80, true),
            new Entry(() -> ModItems.PATRIOT.get(), 1, 4000, 110, true),
            new Entry(() -> ModItems.NASAMS.get(), 1, 1600, 60, true),
            new Entry(() -> ModItems.JERRYCAN.get(), 2, 60, 10, false),
            new Entry(() -> ModItems.PICKUP_MG.get(), 1, 500, 30, false),
            new Entry(() -> ModItems.RELAY.get(), 1, 400, 30, false),
            new Entry(() -> ModItems.PICKUP_SAM.get(), 1, 800, 40, false),
            new Entry(() -> ModItems.BMP2.get(), 1, 1800, 70, true),
            new Entry(() -> ModItems.BRADLEY.get(), 1, 2200, 80, true),
            new Entry(() -> ModItems.T72.get(), 1, 3200, 100, true),
            new Entry(() -> ModItems.ABRAMS.get(), 1, 3800, 110, true),
            new Entry(() -> ModItems.KA52.get(), 1, 4500, 120, true),
            new Entry(() -> ModItems.MI8.get(), 1, 3000, 100, true),
            new Entry(() -> ModItems.S8_ROCKET.get(), 20, 300, 20, false),
            new Entry(() -> ModItems.MORTAR.get(), 1, 300, 20, false),
            new Entry(() -> ModItems.MORTAR_SHELL.get(), 6, 120, 10, false),
            new Entry(() -> ModItems.TANK_SHELL.get(), 10, 400, 20, false),
            new Entry(() -> ModItems.CANNON_SHELLS.get(), 60, 200, 15, false),
            new Entry(() -> ModItems.AK74.get(), 1, 120, 10, false),
            new Entry(() -> ModItems.PM.get(), 1, 40, 5, false),
            new Entry(() -> ModItems.GLOCK.get(), 1, 60, 5, false),
            new Entry(() -> ModItems.PISTOL_AMMO.get(), 64, 25, 5, false),
            new Entry(() -> ModItems.COMBAT_KNIFE.get(), 1, 20, 5, false),
            new Entry(() -> ModItems.MACHETE.get(), 1, 30, 5, false),
            new Entry(() -> ModItems.M4.get(), 1, 140, 10, false),
            new Entry(() -> ModItems.PKM.get(), 1, 250, 15, false),
            new Entry(() -> ModItems.SVD.get(), 1, 300, 15, false),
            new Entry(() -> ModItems.RPG7.get(), 1, 300, 15, false),
            new Entry(() -> ModItems.RIFLE_AMMO.get(), 64, 40, 5, false),
            new Entry(() -> ModItems.SNIPER_AMMO.get(), 20, 40, 5, false),
            new Entry(() -> ModItems.RPG_ROUND.get(), 2, 120, 10, false),
            new Entry(() -> ModItems.FRAG_GRENADE.get(), 4, 60, 5, false),
            new Entry(() -> ModItems.BANDAGE.get(), 6, 30, 5, false),
            new Entry(() -> ModItems.TOURNIQUET.get(), 4, 30, 5, false),
            new Entry(() -> ModItems.MEDKIT.get(), 2, 80, 5, false),
            new Entry(() -> ModItems.PAINKILLER.get(), 4, 50, 5, false),
            new Entry(() -> ModItems.FIELD_SHOVEL.get(), 1, 20, 5, false),
            new Entry(() -> ModBlocks.SANDBAGS_ITEM.get(), 32, 60, 10, false),
            new Entry(() -> ModBlocks.SANDBAG_WALL_ITEM.get(), 32, 50, 10, false),
            new Entry(() -> ModBlocks.CONCRETE_FORT_ITEM.get(), 32, 120, 15, false),
            new Entry(() -> ModBlocks.BUNKER_CONCRETE_ITEM.get(), 16, 300, 30, false),
            new Entry(() -> ModBlocks.BUNKER_SLAB_ITEM.get(), 16, 160, 30, false),
            new Entry(() -> ModBlocks.BUNKER_STAIRS_ITEM.get(), 16, 240, 30, false),
            new Entry(() -> ModBlocks.EMBRASURE_ITEM.get(), 8, 200, 30, false),
            new Entry(() -> ModBlocks.ARMOR_PLATE_ITEM.get(), 8, 400, 40, false),
            new Entry(() -> ModBlocks.BUNKER_DOOR_ITEM.get(), 1, 150, 20, false),
            new Entry(() -> ModBlocks.GABION_ITEM.get(), 16, 80, 10, false),
            new Entry(() -> ModBlocks.LOG_ROOF_ITEM.get(), 32, 40, 10, false),
            new Entry(() -> ModBlocks.STEEL_BEAM_ITEM.get(), 8, 120, 15, false),
            new Entry(() -> ModBlocks.CONCRETE_BARRIER_ITEM.get(), 16, 100, 15, false),
            new Entry(() -> ModBlocks.HEDGEHOG_ITEM.get(), 8, 80, 10, false),
            new Entry(() -> ModBlocks.DRAGON_TEETH_ITEM.get(), 8, 80, 10, false),
            new Entry(() -> ModBlocks.BARBED_WIRE_ITEM.get(), 16, 40, 10, false),
            new Entry(() -> ModBlocks.CAMO_NET_ITEM.get(), 16, 40, 10, false),
            new Entry(() -> ModBlocks.CAMO_NET_WALL_ITEM.get(), 16, 40, 10, false),
            new Entry(() -> ModBlocks.AMMO_CRATE_ITEM.get(), 4, 20, 5, false),
            new Entry(() -> ModBlocks.REVETMENT_ITEM.get(), 32, 30, 10, false),
            new Entry(() -> ModItems.RED_DOT.get(), 1, 60, 5, false),
            new Entry(() -> ModItems.SCOPE.get(), 1, 120, 5, false),
            new Entry(() -> ModItems.SUPPRESSOR.get(), 1, 80, 5, false),
            new Entry(() -> ModItems.THERMAL_SIGHT.get(), 1, 300, 10, false),
            new Entry(() -> ModItems.UNDERBARREL.get(), 1, 150, 10, false),
            new Entry(() -> ModItems.VOG_ROUND.get(), 6, 90, 5, false),
            new Entry(() -> ModItems.RANGEFINDER.get(), 1, 150, 5, false),
            new Entry(() -> ModItems.SMOKE_GRENADE.get(), 4, 40, 5, false),
            new Entry(() -> ModItems.SHAHED_LAUNCHER.get(), 1, 1200, 60, true),
            new Entry(() -> ModItems.EW_STATION.get(), 1, 1500, 60, true),
            new Entry(() -> ModItems.SAM_POST.get(), 1, 700, 45, false),
            new Entry(() -> ModItems.SAM_MANUAL.get(), 1, 400, 30, false),
            new Entry(() -> ModItems.MG_NEST.get(), 1, 200, 20, false),
            new Entry(() -> ModItems.RAMP.get(), 1, 300, 30, false),
            new Entry(() -> ModItems.GRAD_P.get(), 1, 250, 30, false),
            // боеприпасы и дроны
            new Entry(() -> ModItems.GRAD_ROCKET.get(), 10, 300, 15, false),
            new Entry(() -> ModItems.BALLISTIC_MISSILE.get(), 1, 1200, 45, false),
            new Entry(() -> ModItems.SAM_MISSILE.get(), 4, 400, 20, false),
            new Entry(() -> ModItems.TOS_ROCKET.get(), 8, 500, 20, false),
            new Entry(() -> ModItems.HIMARS_ROCKET.get(), 2, 700, 30, false),
            new Entry(() -> ModItems.PATRIOT_MISSILE.get(), 1, 900, 40, false),
            new Entry(() -> ModItems.NASAMS_MISSILE.get(), 3, 450, 25, false),
            new Entry(() -> ModItems.JAVELIN.get(), 1, 300, 20, false),
            new Entry(() -> ModItems.JAVELIN_MISSILE.get(), 1, 250, 20, false),
            new Entry(() -> ModItems.INTERCEPTOR.get(), 2, 250, 15, false),
            new Entry(() -> ModItems.LANCET.get(), 1, 450, 25, false),
            new Entry(() -> ModItems.WING_RECON.get(), 1, 600, 30, false),
            new Entry(() -> ModItems.MG_BELT.get(), 4, 60, 10, false),
            new Entry(() -> ModItems.SHAHED_DRONE.get(), 1, 250, 20, false),
            new Entry(() -> ModItems.SHAHED_238.get(), 1, 600, 30, false),
            new Entry(() -> ModItems.SHAHED_131.get(), 1, 150, 15, false),
            new Entry(() -> ModItems.GERBERA.get(), 1, 60, 15, false),
            new Entry(() -> ModItems.FPV_DRONE.get(), 2, 120, 15, false),
            new Entry(() -> ModItems.RECON_DRONE.get(), 1, 250, 20, false),
            new Entry(() -> ModItems.MINE_AP.get(), 4, 80, 10, false),
            new Entry(() -> ModItems.MINE_AT.get(), 2, 150, 15, false),
            // управление и база
            new Entry(() -> ModItems.DESIGNATOR.get(), 1, 100, 10, false),
            new Entry(() -> ModItems.TARGET_LOG.get(), 1, 20, 10, false),
            new Entry(() -> ModItems.OPERATOR_TERMINAL.get(), 1, 150, 15, false),
            new Entry(() -> ModBlocks.WAREHOUSE_ITEM.get(), 1, 200, 20, false),
            new Entry(() -> ModItems.RADAR_TRUCK.get(), 1, 1800, 60, true),
            new Entry(() -> ModItems.RADAR_DISPLAY.get(), 1, 300, 20, false),
            new Entry(() -> ModBlocks.SIREN_ITEM.get(), 1, 150, 15, false),
            new Entry(() -> ModBlocks.HQ_ITEM.get(), 1, 1000, 45, true));

    public static final int MAX_HEAVY = 3;

    /** Заказ фракции. */
    public static final class Order {
        public int entry;
        public long due;
        public String by = "";

        Order() {
        }

        Order(int entry, long due, String by) {
            this.entry = entry;
            this.due = due;
            this.by = by;
        }
    }

    private Supply() {
    }

    /** Оформить заказ. Возвращает сообщение об ошибке или null при успехе. */
    public static Component buy(ServerPlayer p, int index) {
        if (!War.active()) {
            return Component.translatable("message.harddayz.war_none");
        }
        War.Faction f = War.of(p.getUUID());
        if (f == null) {
            return Component.translatable("message.harddayz.shop_no_faction");
        }
        if (index < 0 || index >= CATALOG.size()) {
            return null;
        }
        Entry e = CATALOG.get(index);
        if (e.heavy() && !War.isCommander(p)) {
            return Component.translatable("message.harddayz.shop_commander_only");
        }
        if (f.budget < e.price()) {
            return Component.translatable("message.harddayz.shop_no_money", e.price(), f.budget);
        }
        if (e.heavy() && f.orders.stream().filter(o -> CATALOG.get(o.entry).heavy()).count() >= MAX_HEAVY) {
            return Component.translatable("message.harddayz.shop_heavy_limit", MAX_HEAVY);
        }
        f.budget -= e.price();
        f.spent += e.price();
        long now = p.level().getServer().overworld().getGameTime();
        f.orders.add(new Order(index, now + e.seconds() * 20L, p.getScoreboardName()));
        War.save();
        for (ServerPlayer m : members(p.level().getServer(), f)) {
            m.sendSystemMessage(Component.translatable("message.harddayz.shop_ordered", p.getDisplayName(), e.stack().getHoverName(), e.count(), e.price(),
                    e.seconds()).withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    /** Доставить готовые заказы: на склад фракции, иначе к главному штабу, иначе заказчику. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (War.Faction f : War.factions()) {
            if (f.orders.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (Order o : new ArrayList<>(f.orders)) {
                if (now < o.due) {
                    continue;
                }
                f.orders.remove(o);
                changed = true;
                deliver(server, f, o);
            }
            if (changed) {
                War.save();
            }
        }
    }

    private static void deliver(MinecraftServer server, War.Faction f, Order o) {
        Entry e = CATALOG.get(o.entry);
        ItemStack stack = e.stack();
        ServerLevel level = server.overworld();
        BlockPos at = null;
        if (!f.hqs.isEmpty()) {
            int[] h = f.hqs.get(0);
            BlockPos hq = new BlockPos(h[0], h[1], h[2]);
            level.getChunk(hq);
            if (level.getBlockEntity(hq) instanceof HqBlockEntity be) {
                stack = be.insert(stack);
                if (stack.isEmpty()) {
                    level.playSound(null, hq, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.7f);
                    for (ServerPlayer m : members(server, f)) {
                        m.sendSystemMessage(Component.translatable("message.harddayz.shop_arrived", e.stack().getHoverName(), e.count(),
                                Component.translatable("message.harddayz.shop_where_hq")).withStyle(ChatFormatting.GREEN));
                    }
                    return;
                }
            }
        }
        if (!f.warehouses.isEmpty()) {
            int[] w = f.warehouses.get(0);
            at = new BlockPos(w[0], w[1] + 1, w[2]);
        } else if (!f.hqs.isEmpty()) {
            int[] h = f.hqs.get(0);
            at = new BlockPos(h[0], h[1] + 1, h[2]);
        }
        if (at != null) {
            level.getChunk(at); // склад может быть далеко от игроков
            ItemEntity ie = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, stack);
            ie.setDefaultPickUpDelay();
            ie.setUnlimitedLifetime();
            level.addFreshEntity(ie);
            level.playSound(null, at, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.7f);
        } else {
            ServerPlayer buyer = server.getPlayerList().getPlayerByName(o.by);
            if (buyer != null && !buyer.getInventory().add(stack)) {
                buyer.spawnAtLocation(stack);
            }
        }
        Component where = at != null ? Component.translatable("message.harddayz.shop_where_base", at.getX(), at.getY(), at.getZ())
                : Component.translatable("message.harddayz.shop_where_player", o.by);
        for (ServerPlayer m : members(server, f)) {
            m.sendSystemMessage(Component.translatable("message.harddayz.shop_arrived", e.stack().getHoverName(), e.count(), where)
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    static List<ServerPlayer> members(MinecraftServer server, War.Faction f) {
        List<ServerPlayer> out = new ArrayList<>();
        for (String m : f.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(m));
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }
}
