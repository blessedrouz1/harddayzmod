package com.harddayz.item;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Missile;
import com.harddayz.entity.Vehicle;
import com.harddayz.network.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стрелковое оружие: ЛКМ — огонь (зажать — очередь у автоматического), ПКМ — прицел через мушку
 * (меньше разброс, приближение). Магазин перезаряжается сам, когда опустел, патронами из инвентаря.
 */
public class GunItem extends Item {
    /** Характеристики ствола. {@code rocket} — вместо пули летит граната РПГ. */
    public record Spec(float damage, int cooldown, float spread, float aimSpread, int mag, int reload, Supplier<Item> ammo, double range, float pitch,
            boolean auto, float zoom, float recoil, boolean rocket) {
    }

    public final Spec spec;

    public GunItem(Properties properties, Spec spec) {
        super(properties);
        this.spec = spec;
    }

    private static CompoundTag tag(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    public static int mag(ItemStack s) {
        return tag(s).getIntOr("mag", 0);
    }

    private static void put(ItemStack s, String k, long v) {
        CompoundTag t = tag(s);
        t.putLong(k, v);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    private static void putInt(ItemStack s, String k, int v) {
        CompoundTag t = tag(s);
        t.putInt(k, v);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /** Установленный обвес: "sight" (reddot / scope), "muzzle" (suppressor). */
    public static String part(ItemStack s, String slot) {
        return tag(s).getStringOr(slot, "");
    }

    public static void setPart(ItemStack s, String slot, String value) {
        CompoundTag t = tag(s);
        t.putString(slot, value);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        // для моделей: строка 0 — прицел, флаг 0 — глушитель
        String sight = t.getStringOr("sight", "");
        boolean supp = "suppressor".equals(t.getStringOr("muzzle", ""));
        boolean gl = "gl".equals(t.getStringOr("under", ""));
        s.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(java.util.List.of(), java.util.List.of(supp, gl),
                java.util.List.of(sight), java.util.List.of()));
    }

    /** Приближение с учётом прицела. */
    public float zoom(ItemStack s) {
        return switch (part(s, "sight")) {
            case "scope" -> Math.min(spec.zoom(), 0.28f);
            case "thermal" -> Math.min(spec.zoom(), 0.4f);
            case "reddot" -> Math.min(spec.zoom(), 0.72f);
            default -> spec.zoom();
        };
    }

    private float aimSpread(ItemStack s) {
        return switch (part(s, "sight")) {
            case "scope" -> spec.aimSpread() * 0.35f;
            case "thermal" -> spec.aimSpread() * 0.5f;
            case "reddot" -> spec.aimSpread() * 0.75f;
            default -> spec.aimSpread();
        };
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.harddayz.client.GunRender.INSTANCE);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS; // ПКМ — прицел, обрабатывает клиент
    }

    /** Выстрел (с сервера, по пакету клиента). */
    public void shoot(ServerPlayer p, ItemStack stack, boolean aiming) {
        ServerLevel level = p.level();
        long now = level.getGameTime();
        if (now < tag(stack).getLongOr("next", 0)) {
            return;
        }
        int mag = mag(stack);
        if (mag <= 0) {
            reload(p, stack, now);
            return;
        }
        putInt(stack, "mag", mag - 1);
        put(stack, "next", now + spec.cooldown());
        Vec3 eye = p.getEyePosition();
        float sp = aiming ? aimSpread(stack) : spec.spread();
        Vec3 dir = Vec3.directionFromRotation(p.getXRot() + (float) p.getRandom().nextGaussian() * sp,
                p.getYRot() + (float) p.getRandom().nextGaussian() * sp);
        if (spec.rocket()) {
            Missile.launch(level, Missile.Kind.BOMB, eye.add(dir.scale(1.2)), dir.scale(2.6), eye.add(dir.scale(300)), p).owner(p.getUUID())
                    .powerMul(1.7f).look(6);
            level.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3f, 0.6f);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, true, true, eye.x - dir.x * 1.5, eye.y - 0.3,
                    eye.z - dir.z * 1.5, 15, 0.3, 0.3, 0.3, 0.05);
            return;
        }
        Vec3 end = eye.add(dir.scale(spec.range()));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var hit = com.harddayz.entity.Launcher.rayEntity(level, p, eye, stop, e -> e != p);
        boolean hitBlock = hit == null && block.getType() != HitResult.Type.MISS;
        if (hit != null) {
            stop = hit.getLocation();
            Entity e = hit.getEntity();
            float dmg = spec.damage();
            if (e instanceof Vehicle) {
                dmg *= 0.25f; // броню пулей не пробить толком
            } else if (e instanceof Drone) {
                dmg *= 0.6f;
            } else if (stop.y > e.getEyeY() - 0.25) {
                dmg *= 1.8f; // в голову
            }
            e.hurtServer(level, level.damageSources().playerAttack(p), dmg);
        }
        boolean quiet = "suppressor".equals(part(stack, "muzzle"));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), com.harddayz.registry.ModSounds.MG_FIRE.get(), SoundSource.PLAYERS, quiet ? 0.6f : 3f,
                spec.pitch() * (quiet ? 1.5f : 1f) + p.getRandom().nextFloat() * 0.06f);
        Vec3 muzzle = eye.add(dir.scale(0.8)).add(0, -0.15, 0);
        var fx = new Net.Tracer(muzzle.x, muzzle.y, muzzle.z, stop.x, stop.y, stop.z, hitBlock);
        for (ServerPlayer pl : level.players()) {
            if (pl.distanceToSqr(p) < 200 * 200) {
                Net.toPlayer(pl, fx);
            }
        }
    }

    /** Списать выстрел из магазина и выставить паузу. */
    protected void consumeRound(ItemStack stack, long now) {
        putInt(stack, "mag", mag(stack) - 1);
        put(stack, "next", now + spec.cooldown());
    }

    /** Подствольный гранатомёт (клавиша G): выстрел ВОГом по навесной. */
    public void fireUnder(ServerPlayer p, ItemStack stack) {
        if (!"gl".equals(part(stack, "under"))) {
            return;
        }
        ServerLevel level = p.level();
        long now = level.getGameTime();
        if (now < tag(stack).getLongOr("gl_next", 0)) {
            return;
        }
        var inv = p.getInventory();
        boolean ok = p.isCreative();
        for (int i = 0; i < inv.getContainerSize() && !ok; i++) {
            if (inv.getItem(i).is(com.harddayz.registry.ModItems.VOG_ROUND.get())) {
                inv.getItem(i).shrink(1);
                ok = true;
            }
        }
        if (!ok) {
            p.sendOverlayMessage(Component.translatable("message.harddayz.no_vog").withStyle(ChatFormatting.RED));
            return;
        }
        put(stack, "gl_next", now + 40);
        Vec3 dir = p.getLookAngle();
        Missile.launch(level, Missile.Kind.BOMB, p.getEyePosition().add(dir.scale(1.2)).add(0, -0.2, 0), dir.scale(1.6), p.getEyePosition()
                .add(dir.scale(200)), p).owner(p.getUUID()).powerMul(0.8f);
        level.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 2f, 0.5f);
    }

    private void reload(ServerPlayer p, ItemStack stack, long now) {
        int need = spec.mag();
        int got = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && got < need; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(spec.ammo().get())) {
                int n = p.isCreative() ? need - got : Math.min(need - got, s.getCount());
                if (!p.isCreative()) {
                    s.shrink(n);
                }
                got += n;
            }
        }
        if (got <= 0 && p.isCreative()) {
            got = need;
        }
        if (got <= 0) {
            put(stack, "next", now + 10);
            p.sendOverlayMessage(Component.translatable("message.harddayz.no_ammo").withStyle(ChatFormatting.RED));
            p.level().playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
            return;
        }
        putInt(stack, "mag", got);
        put(stack, "next", now + spec.reload());
        p.sendOverlayMessage(Component.translatable("message.harddayz.reloading").withStyle(ChatFormatting.YELLOW));
        p.level().playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.harddayz.gun.desc").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.harddayz.gun.mag", mag(stack), spec.mag()).withStyle(ChatFormatting.YELLOW));
    }
}
