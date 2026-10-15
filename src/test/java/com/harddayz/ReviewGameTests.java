package com.harddayz;

import com.harddayz.entity.Drone;
import com.harddayz.item.GunItem;
import com.harddayz.network.Net;
import com.harddayz.registry.ModEntities;
import com.harddayz.registry.ModItems;
import com.harddayz.war.Medic;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("harddayz")
@PrefixGameTestTemplate(false)
public class ReviewGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "review"));
    }

    private static Object decode(String name, FriendlyByteBuf buf) throws Exception {
        var m = Net.class.getDeclaredMethod(name, FriendlyByteBuf.class);
        m.setAccessible(true);
        try {
            return m.invoke(null, buf);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof DecoderException failure) throw failure;
            throw e;
        }
    }

    @GameTest(template = "empty")
    public static void packetLengths(GameTestHelper h) throws Exception {
        for (int n : new int[]{-1, 1, 2, 49, Integer.MAX_VALUE}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeBoolean(false);
                buf.writeVarInt(n);
                // Include payload: rejection must be due to size/shape, not buffer underflow.
                for (int i = 0; i < 49; i++) buf.writeVarInt(i);
                boolean rejected = false;
                try { decode("decLogEdit", buf); } catch (DecoderException expected) { rejected = true; }
                h.assertTrue(rejected, "Accepted invalid coordinate count " + n);
            } finally { buf.release(); }
        }
        for (int n : new int[]{0, 48}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeBoolean(true);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeVarInt(i);
                var result = (Net.LogEdit) decode("decLogEdit", buf);
                h.assertTrue(result.offhand() && result.pts().size() == n && !buf.isReadable(), "Valid log failed");
            } finally { buf.release(); }
        }
        for (int n : new int[]{-1, 4097}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeVarInt(1).writeVarInt(n);
                boolean rejected = false;
                try { decode("decRadarState", buf); } catch (DecoderException expected) { rejected = true; }
                h.assertTrue(rejected, "Accepted invalid contact count");
            } finally { buf.release(); }
        }
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(2).writeVarInt(1).writeUtf("contact").writeBoolean(true);
            var result = (Net.RadarState) decode("decRadarState", buf);
            h.assertTrue(result.open() && result.contacts().equals(java.util.List.of("contact")) && !buf.isReadable(), "Trailing flag corrupted");
        } finally { buf.release(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void droneInvalidMovement(GameTestHelper h) {
        var p = player(h);
        var d = ModEntities.DRONE.get().create(h.getLevel());
        d.setKind(Drone.Kind.RECON);
        d.setPos(0, 100, 0);
        h.assertTrue(d.connect(p), "Cannot connect pilot");
        Vec3 start = d.position();
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            d.pilotMove(p, new Vec3(bad, 100, 0), 0, 0);
            d.pilotMove(p, new Vec3(0, bad, 0), 0, 0);
            d.pilotMove(p, new Vec3(0, 100, bad), 0, 0);
            d.pilotMove(p, start, (float) bad, 0);
            d.pilotMove(p, start, 0, (float) bad);
            h.assertTrue(d.position().equals(start) && Float.isFinite(d.getYRot()) && Float.isFinite(d.getXRot()), "Invalid movement applied");
        }
        d.pilotMove(p, start.add(13, 0, 0), 0, 0);
        h.assertTrue(d.position().equals(start), "Teleport accepted");
        d.pilotMove(p, start.add(1, 0, 0), 45, 10);
        h.assertTrue(d.position().equals(start.add(1, 0, 0)) && d.getYRot() == 45, "Normal movement rejected");
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Integer> state(String name) throws Exception {
        var f = Medic.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Map<UUID, Integer>) f.get(null);
    }

    @GameTest(template = "empty")
    public static void medicalStateAfterDeath(GameTestHelper h) throws Exception {
        var p = player(h);
        p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(Medic.tryDown(p, p.damageSources().generic()), "Player did not become downed");
        state("REVIVE").put(p.getUUID(), 80);
        state("GIVE_UP").put(p.getUUID(), 59);
        h.assertTrue(!Medic.tryDown(p, p.damageSources().generic()), "Finishing hit was cancelled");
        h.assertTrue(!Medic.isDowned(p) && p.getForcedPose() == null
                && !state("REVIVE").containsKey(p.getUUID()) && !state("GIVE_UP").containsKey(p.getUUID()), "Medical state leaked after death");
        h.assertTrue(Medic.tryDown(p, p.damageSources().generic()), "Second injury failed");
        h.assertTrue(!state("REVIVE").containsKey(p.getUUID()), "Old revival progress reused");
        Medic.tryDown(p, p.damageSources().genericKill());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void javelinReloadDelay(GameTestHelper h) {
        var p = player(h);
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)).getCenter());
        var target = ModEntities.LAUNCHER.get().create(h.getLevel());
        target.setPos(p.getEyePosition().add(p.getLookAngle().scale(10)).add(0, -1, 0));
        h.getLevel().addFreshEntity(target);
        try {
            h.assertTrue(com.harddayz.item.JavelinItem.target(h.getLevel(), p) != null, "No target for cooldown test");
            var stack = new ItemStack(ModItems.JAVELIN.get());
            stack.getOrCreateTag().putInt("mag", 1);
            stack.getOrCreateTag().putLong("next", h.getLevel().getGameTime() + 80);
            ((GunItem) stack.getItem()).shoot(p, stack, true);
            h.assertTrue(GunItem.mag(stack) == 1, "Javelin fired during reload delay");
        } finally { target.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void incapacitatedGunfire(GameTestHelper h) {
        var p = player(h);
        var stack = new ItemStack(ModItems.JAVELIN.get());
        var gun = (GunItem) stack.getItem();
        stack.getOrCreateTag().putInt("mag", 1);
        p.setGameMode(GameType.SPECTATOR);
        gun.shoot(p, stack, true);
        h.assertTrue(GunItem.mag(stack) == 1, "Spectator fired");
        p.setGameMode(GameType.SURVIVAL);
        Medic.tryDown(p, p.damageSources().generic());
        gun.shoot(p, stack, true);
        h.assertTrue(GunItem.mag(stack) == 1, "Downed player fired");
        Medic.tryDown(p, p.damageSources().genericKill());
        h.succeed();
    }
}
