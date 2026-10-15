package com.harddayz.item;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Missile;
import com.harddayz.entity.Vehicle;
import com.harddayz.network.Net;

import net.minecraft.ChatFormatting;
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
    private Supplier<net.minecraft.sounds.SoundEvent> shot = () -> com.harddayz.registry.ModSounds.MG_FIRE.get();

    /** Свой звук выстрела. */
    public GunItem sound(Supplier<net.minecraft.sounds.SoundEvent> s) {
        shot = s;
        return this;
    }

    public GunItem(Properties properties, Spec spec) {
        super(properties);
        this.spec = spec;
    }

    private static CompoundTag tag(ItemStack s) {
        return com.harddayz.Compat.data(s);
    }

    public static int mag(ItemStack s) {
        return com.harddayz.Compat.getInt(tag(s), "mag", 0);
    }

    private static void put(ItemStack s, String k, long v) {
        s.getOrCreateTag().putLong(k, v);
    }

    private static void putInt(ItemStack s, String k, int v) {
        s.getOrCreateTag().putInt(k, v);
    }

    /** Установленный обвес: "sight" (reddot / scope), "muzzle" (suppressor). */
    public static String part(ItemStack s, String slot) {
        return com.harddayz.Compat.getString(tag(s), slot, "");
    }

    public static void setPart(ItemStack s, String slot, String value) {
        // модели обвесов выбирают свойства предмета (см. ClientSetup.itemProperties)
        s.getOrCreateTag().putString(slot, value);
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
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.PASS, player.getItemInHand(hand)); // ПКМ — прицел, обрабатывает клиент
    }

    /** Выстрел (с сервера, по пакету клиента). */
    public void shoot(ServerPlayer p, ItemStack stack, boolean aiming) {
        if (!canFire(p)) {
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (now < com.harddayz.Compat.getLong(tag(stack), "next", 0)) {
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
            com.harddayz.Compat.particles(level, net.minecraft.core.particles.ParticleTypes.CLOUD, eye.x - dir.x * 1.5, eye.y - 0.3, eye.z - dir.z * 1.5, 15, 0.3, 0.3, 0.3, 0.05);
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
            e.hurt(level.damageSources().playerAttack(p), dmg);
        }
        boolean quiet = "suppressor".equals(part(stack, "muzzle"));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), shot.get(), SoundSource.PLAYERS, quiet ? 0.5f : 4f,
                (quiet ? 1.6f : 1f) + (p.getRandom().nextFloat() - 0.5f) * 0.08f);
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

    protected static boolean canFire(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && p.getVehicle() == null && !com.harddayz.war.Medic.isDowned(p);
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
            p.displayClientMessage(Component.translatable("message.harddayz.no_ammo").withStyle(ChatFormatting.RED), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
            return;
        }
        putInt(stack, "mag", got);
        put(stack, "next", now + spec.reload());
        p.displayClientMessage(Component.translatable("message.harddayz.reloading").withStyle(ChatFormatting.YELLOW), true);
        p.level().playSound(null, p.blockPosition(), spec.mag() <= 17 ? com.harddayz.registry.ModSounds.RELOAD_PISTOL.get()
                : com.harddayz.registry.ModSounds.RELOAD_RIFLE.get(), SoundSource.PLAYERS, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.harddayz.gun.desc").withStyle(ChatFormatting.GRAY));
        builder.add(Component.translatable("item.harddayz.gun.mag", mag(stack), spec.mag()).withStyle(ChatFormatting.YELLOW));
    }
}
