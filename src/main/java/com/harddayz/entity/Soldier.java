package com.harddayz.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.harddayz.network.Net;
import com.harddayz.registry.ModSounds;
import com.harddayz.war.BotCommander;
import com.harddayz.war.Medic;
import com.harddayz.war.RadarNet;
import com.harddayz.war.War;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Солдат-бот. Идёт за приказом своего отряда (BotCommander), ищет врагов в прямой видимости, стреляет очередями,
 * меняет позицию, раненым отходит. В мир не сохраняется: вдали от игроков отряд живёт только в расчётах.
 */
public class Soldier extends PathfinderMob {
    public static final int RIFLE = 0;
    public static final int MG = 1;
    public static final int RPG = 2;
    public static final int SNIPER = 3;
    private static final EntityDataAccessor<Integer> FACTION = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);

    private int squad = -1;
    private @Nullable UUID side;
    private @Nullable Entity enemy;
    private int seen;
    private int lost;
    private int burst;
    private int cooldown;
    private int moveAt;
    private int stuck;
    /** Где последний раз видел врага, и когда бросал гранату. */
    private Vec3 lastSeen;
    private int nextGrenade;
    private Vec3 stuckAt = Vec3.ZERO;
    private final double offX = (Math.random() - 0.5) * 9;
    private final double offZ = (Math.random() - 0.5) * 9;

    public Soldier(EntityType<? extends Soldier> type, Level level) {
        super(type, level);
        setMaxUpStep(1.1f);
        xpReward = 0;
        if (getNavigation() instanceof GroundPathNavigation g) {
            g.setCanOpenDoors(true);
            g.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    public void setup(UUID side, int squad, int role) {
        this.side = side;
        this.squad = squad;
        entityData.set(FACTION, Math.max(0, War.factionIndex(side)));
        entityData.set(ROLE, role);
        setItemSlot(EquipmentSlot.MAINHAND, weapon(role, faction() % 2));
        if (War.difficulty() >= 3) { // тяжёлая сложность: бронежилеты
            var hp = getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(28);
                setHealth(28);
            }
        }
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    /** Оружие в руках — из TaCZ, если он стоит; на стрельбу бота это не влияет. */
    private static ItemStack weapon(int role, int style) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun"));
        if (item == null || item == Items.AIR) {
            return new ItemStack(Items.CROSSBOW);
        }
        ItemStack s = new ItemStack(item);
        CompoundTag t = s.getOrCreateTag();
        t.putString("GunId", switch (role) {
            case MG -> style == 0 ? "tacz:m249" : "tacz:rpk";
            case RPG -> "tacz:rpg7";
            case SNIPER -> style == 0 ? "tacz:m700" : "tacz:ai_awp";
            default -> style == 0 ? "tacz:m4a1" : "tacz:ak47";
        });
        t.putString("GunFireMode", "SEMI");
        t.putInt("GunCurrentAmmoCount", 1);
        return s;
    }

    public int faction() {
        return Math.floorMod(entityData.get(FACTION), 4);
    }

    public int role() {
        return entityData.get(ROLE);
    }

    public @Nullable UUID side() {
        return side;
    }

    public int squad() {
        return squad;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FACTION, 0);
        entityData.define(ROLE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ------------------------------------------------------------------ свой/чужой

    /** Сторона существа или техники в войне. */
    public static @Nullable UUID sideOf(Entity e) {
        return e instanceof Player p ? p.getUUID() : e instanceof Soldier s ? s.side : e instanceof Vehicle v ? v.owner() : e instanceof Drone d ? d.owner() : null;
    }

    private boolean hostile(Entity e) {
        if (!e.isAlive() || e == this) {
            return false;
        }
        if (e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator() || Medic.isDowned(p) || War.of(p.getUUID()) == null)) {
            return false;
        }
        if (e instanceof Launcher l && (l.wrecked() || l.hidden())) {
            return false;
        }
        if (e instanceof Drone d && !d.engineOn()) {
            return false;
        }
        UUID o = sideOf(e);
        return o != null && War.of(o) != null && !War.allied(side, o);
    }

    // ------------------------------------------------------------------ мозги

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        BotCommander.Squad s = BotCommander.squad(squad);
        if (side == null || !War.active() || s == null && squad >= 0) {
            discard();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if ((tickCount + getId()) % 8 == 0) {
            sense(level);
        }
        if (enemy != null) {
            fight(level);
        } else {
            march(s);
        }
        unstick(level);
    }

    private double sight() {
        return role() == SNIPER ? 90 : 48 + 8 * War.difficulty();
    }

    private void sense(ServerLevel level) {
        if (enemy != null && (!hostile(enemy) || distanceTo(enemy) > sight() * 1.3)) {
            enemy = null;
            seen = 0;
        }
        if (enemy != null) {
            if (sees(level, enemy)) {
                lost = 0;
                seen += 8;
                lastSeen = enemy.getBoundingBox().getCenter();
            } else if ((lost += 8) > 80) {
                enemy = null;
                seen = 0;
            }
        }
        if (enemy != null && lost == 0 && (tickCount & 31) != 0) {
            return;
        }
        double r = sight();
        Entity best = null;
        double bestK = 1.0E9;
        for (Entity e : level.getEntities(this, getBoundingBox().inflate(r, 30, r), this::hostile)) {
            double d = distanceTo(e);
            // дрон видно и слышно вблизи; пехоту сбоку и сзади замечают хуже; техника — забота гранатомётчика
            double k = d * (e instanceof Vehicle ? role() == RPG ? 0.3 : 3 : e instanceof Drone ? 1.4 : 1);
            if (e instanceof Drone && d > 40 || k >= bestK || d > r) {
                continue;
            }
            Vec3 look = getViewVector(1f);
            double dot = look.dot(e.position().subtract(position()).normalize());
            if (dot < 0.1 && d > 18 && e != getLastHurtByMob()) {
                continue;
            }
            if (e.isCrouching() && d > 30 && random.nextInt(3) != 0) {
                continue;
            }
            if (sees(level, e)) {
                best = e;
                bestK = k;
            }
        }
        if (best != null) {
            if (RadarNet.spot(RadarNet.key(side), best, level.getGameTime())) {
                BotCommander.contact(level, this, best);
            }
        }
        if (enemy != null) {
            RadarNet.spot(RadarNet.key(side), enemy, level.getGameTime());
        }
        if (best != null && best != enemy) {
            enemy = best;
            seen = 0;
            lost = 0;
            cooldown = Math.max(cooldown, (int) (14 + distanceTo(best) * 0.35 / War.difficulty()));
        }
    }

    /** Прямая видимость: стены и дымовая завеса закрывают. */
    private boolean sees(ServerLevel level, Entity e) {
        if (!hasLineOfSight(e)) {
            return false;
        }
        Vec3 a = getEyePosition(), b = e.getBoundingBox().getCenter();
        for (GasCloud g : level.getEntitiesOfClass(GasCloud.class, new AABB(a, b).inflate(8), g -> g.type() == GasCloud.SMOKE)) {
            Vec3 c = g.position().add(0, 1.5, 0), ab = b.subtract(a);
            double t = Mth.clamp(c.subtract(a).dot(ab) / Math.max(0.01, ab.lengthSqr()), 0, 1);
            if (a.add(ab.scale(t)).distanceTo(c) < g.radius() * 0.8) {
                return false;
            }
        }
        return true;
    }

    private void fight(ServerLevel level) {
        Entity e = enemy;
        getLookControl().setLookAt(e, 40, 40);
        double d = distanceTo(e);
        if (tickCount >= moveAt) {
            moveAt = tickCount + 40 + random.nextInt(60);
            double want = switch (role()) {
                case MG -> 38;
                case SNIPER -> 65;
                case RPG -> 32;
                default -> 24;
            };
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1).normalize();
            // обход: половина отряда заходит слева, половина справа, на сложности 3 — шире
            int side = (getId() & 1) == 0 ? 1 : -1;
            double flank = War.difficulty() >= 3 && role() != MG ? 8 + random.nextDouble() * 8 : 3 + random.nextDouble() * 6;
            Vec3 sideways = new Vec3(-to.z, 0, to.x).scale((random.nextInt(4) == 0 ? -side : side) * flank);
            double fwd = getHealth() < 8 ? -14 : lost > 0 ? 10 : d > want + 8 ? 9 : d < want - 12 ? -5 : 0;
            Vec3 p = position().add(to.scale(fwd)).add(sideways);
            getNavigation().moveTo(p.x, p.y, p.z, getHealth() < 8 ? 1.2 : 1.0);
        }
        if (lost > 0 && cooldown <= 0 && !War.prep() && lastSeen != null) {
            // враг скрылся: граната за укрытие, иначе огонь на подавление туда, где видели
            double ld = getEyePosition().distanceTo(lastSeen);
            if (War.difficulty() >= 2 && ld > 7 && ld < 26 && tickCount >= nextGrenade && role() != SNIPER) {
                grenade(level, lastSeen);
                nextGrenade = tickCount + 260 + random.nextInt(200);
                cooldown = 30;
            } else if (lost < 70 && (role() == MG || role() == RIFLE) && random.nextInt(3) > 0) {
                suppress(level, lastSeen);
                cooldown = role() == MG ? 4 : 12;
            }
            return;
        }
        if (lost > 0 || cooldown > 0 || d > sight() * 1.1 || War.prep()) {
            return;
        }
        boolean armor = e instanceof Vehicle;
        if (role() == RPG && (armor || random.nextInt(4) == 0 && d > 10) && d < 80 && !(e instanceof Drone)) {
            rocket(level, e);
            cooldown = 170 + random.nextInt(80);
            return;
        }
        if (burst <= 0) {
            burst = switch (role()) {
                case MG -> 7 + random.nextInt(6);
                case SNIPER -> 1;
                default -> 2 + random.nextInt(4);
            };
        }
        shoot(level, e, d);
        burst--;
        cooldown = burst > 0 ? 3 : role() == SNIPER ? 60 + random.nextInt(30) : 16 + random.nextInt(24);
    }

    private void shoot(ServerLevel level, Entity e, double d) {
        Vec3 eye = getEyePosition();
        Vec3 at = e.getBoundingBox().getCenter().add(0, e.getBbHeight() * 0.15, 0);
        float err = switch (role()) {
            case SNIPER -> 0.5f;
            case MG -> 2.6f;
            default -> 1.9f;
        } * (War.difficulty() == 1 ? 1.6f : War.difficulty() == 2 ? 1.1f : 0.75f);
        double moving = e.position().subtract(e.xo, e.yo, e.zo).length();
        err *= 1 + Math.min(1.5, moving * 4);
        if (seen < 24) {
            err *= 1.8f; // первая очередь навскидку
        }
        if (e instanceof Drone) {
            err *= 1.5f;
        }
        if (getDeltaMovement().horizontalDistanceSqr() > 0.01) {
            err *= 1.4f;
        }
        Vec3 to = at.subtract(eye).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z)) + (float) random.nextGaussian() * err;
        float pitch = (float) -Math.toDegrees(Math.asin(to.y)) + (float) random.nextGaussian() * err;
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = eye.add(dir.scale(d + 30));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getLocation();
        // сквозь своих пули не бьют
        var hit = Launcher.rayEntity(level, this, eye, stop, x -> x != this && (x instanceof LivingEntity || x instanceof Vehicle || x instanceof Drone)
                && !War.allied(side, sideOf(x)));
        if (hit != null) {
            stop = hit.getLocation();
            Entity v = hit.getEntity();
            float dmg = role() == SNIPER ? 10 : 3.2f;
            if (v instanceof Vehicle) {
                dmg *= 0.25f;
            }
            if (v instanceof LivingEntity l) {
                l.invulnerableTime = 0;
            }
            v.hurt(level.damageSources().mobAttack(this), dmg);
        }
        var snd = role() == MG ? ModSounds.GUN_PKM : role() == SNIPER ? ModSounds.GUN_SVD : faction() % 2 == 0 ? ModSounds.GUN_M4 : ModSounds.GUN_AK;
        ModSounds.launch(level, eye.x, eye.y, eye.z, snd.get(), 170, 0.95f + random.nextFloat() * 0.1f);
        Vec3 muzzle = eye.add(dir.scale(0.9)).add(0, -0.2, 0);
        var fx = new Net.Tracer(muzzle.x, muzzle.y, muzzle.z, stop.x, stop.y, stop.z, hit == null && block.getType() != HitResult.Type.MISS);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 200 * 200) {
                Net.toPlayer(p, fx);
            }
        }
    }

    /** Граната по навесной: долетает до точки и рвётся по запалу. */
    private void grenade(ServerLevel level, Vec3 at) {
        Vec3 eye = getEyePosition();
        Vec3 flat = at.subtract(eye).multiply(1, 0, 1);
        double d = flat.length(), g = Missile.Kind.BOMB.gravity;
        double t = Math.max(12, d / 0.9); // время полёта, тиков
        Vec3 v = flat.scale(1 / t).add(0, (at.y - eye.y) / t + g * t / 2, 0);
        Missile.launch(level, Missile.Kind.BOMB, eye.add(flat.normalize().scale(0.6)), v, at, this).owner(side).powerMul(1.1f).fuse((int) t + 12);
        level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, net.minecraft.sounds.SoundSource.HOSTILE, 1f, 0.6f);
    }

    /** Очередь в сторону, где враг скрылся: прижимает, а если высунется — может зацепить. */
    private void suppress(ServerLevel level, Vec3 at) {
        Vec3 eye = getEyePosition();
        getLookControl().setLookAt(at.x, at.y, at.z, 40, 40);
        Vec3 dir = at.subtract(eye).normalize().add(random.nextGaussian() * 0.05, random.nextGaussian() * 0.04, random.nextGaussian() * 0.05).normalize();
        Vec3 end = eye.add(dir.scale(eye.distanceTo(at) + 20));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getLocation();
        var hit = Launcher.rayEntity(level, this, eye, stop, x -> x != this && x instanceof LivingEntity && !War.allied(side, sideOf(x)));
        if (hit != null) {
            stop = hit.getLocation();
            hit.getEntity().hurt(level.damageSources().mobAttack(this), 3f);
        }
        var snd = role() == MG ? ModSounds.GUN_PKM : faction() % 2 == 0 ? ModSounds.GUN_M4 : ModSounds.GUN_AK;
        ModSounds.launch(level, eye.x, eye.y, eye.z, snd.get(), 170, 0.95f + random.nextFloat() * 0.1f);
        Vec3 muzzle = eye.add(dir.scale(0.9)).add(0, -0.2, 0);
        var fx = new Net.Tracer(muzzle.x, muzzle.y, muzzle.z, stop.x, stop.y, stop.z, hit == null);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 200 * 200) {
                Net.toPlayer(p, fx);
            }
        }
    }

    private void rocket(ServerLevel level, Entity e) {
        Vec3 eye = getEyePosition();
        double t = distanceTo(e) / 2.6;
        Vec3 at = e.getBoundingBox().getCenter().add(e.getDeltaMovement().scale(t)).add(0, 0.03 * t * t, 0);
        Vec3 dir = at.subtract(eye).normalize().add(random.nextGaussian() * 0.02, random.nextGaussian() * 0.015, random.nextGaussian() * 0.02).normalize();
        Missile.launch(level, Missile.Kind.BOMB, eye.add(dir.scale(1.3)), dir.scale(2.6), eye.add(dir.scale(300)), this).owner(side).powerMul(1.5f).look(6);
        ModSounds.launch(level, eye.x, eye.y, eye.z, ModSounds.ROCKET_LAUNCH.get(), 200, 1.1f);
    }

    /** Вне боя: за приказом отряда. */
    private void march(@Nullable BotCommander.Squad s) {
        if (s == null || (tickCount + getId()) % 15 != 0) {
            return;
        }
        double dx = s.tx + 0.5 + offX - getX(), dz = s.tz + 0.5 + offZ - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 3) {
            if (random.nextInt(5) == 0) { // на месте: осматривается и переходит
                setYRot(random.nextFloat() * 360);
                yHeadRot = getYRot();
                getNavigation().moveTo(getX() + random.nextGaussian() * 5, getY(), getZ() + random.nextGaussian() * 5, 0.8);
            }
            return;
        }
        double step = Math.min(d, 22);
        int x = Mth.floor(getX() + dx / d * step), z = Mth.floor(getZ() + dz / d * step);
        int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - getY()) > 8) {
            y = getBlockY(); // крыша или обрыв — идём по своему уровню
        }
        getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.05);
    }

    /** Застрял (окоп, ров, стена): вылезает к цели. */
    private void unstick(ServerLevel level) {
        if (tickCount % 20 != 0) {
            return;
        }
        BotCommander.Squad s = BotCommander.squad(squad);
        if (enemy != null || s == null || Math.hypot(s.tx - getX(), s.tz - getZ()) < 14 || position().distanceToSqr(stuckAt) > 4) {
            stuck = 0;
            stuckAt = position();
            return;
        }
        if (++stuck < 7) {
            return;
        }
        stuck = 0;
        Vec3 to = new Vec3(s.tx - getX(), 0, s.tz - getZ()).normalize();
        for (int i = 3; i < 14; i += 2) {
            int x = Mth.floor(getX() + to.x * i), z = Mth.floor(getZ() + to.z * i);
            BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(p.getY() - getY()) < 6 && level.getFluidState(p.below()).isEmpty()) {
                teleportTo(x + 0.5, p.getY(), z + 0.5);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ урон

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.getEntity() != null && !src.is(DamageTypes.EXPLOSION) && !src.is(DamageTypes.PLAYER_EXPLOSION)) {
            UUID o = sideOf(src.getEntity());
            if (o != null && War.allied(side, o)) {
                return false; // своя пуля
            }
        }
        if (src.is(DamageTypes.FALL)) {
            amount *= 0.3f;
        }
        invulnerableTime = 0;
        boolean r = super.hurt(src, amount);
        if (r && src.getEntity() != null && hostile(src.getEntity()) && enemy == null) {
            enemy = src.getEntity();
            lost = 1;
            moveAt = 0;
        }
        return r;
    }

    @Override
    public void die(DamageSource src) {
        boolean was = !dead;
        super.die(src);
        if (was && dead && !level().isClientSide()) {
            BotCommander.died(this, src);
            if (random.nextInt(6) == 0) {
                spawnAtLocation(com.harddayz.registry.ModItems.BANDAGE.get());
            }
        }
    }
}
