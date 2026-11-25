package com.harddayz.client;

import com.harddayz.entity.Missile;
import com.harddayz.registry.ModSounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Звук летящего снаряда: реактивные — рёв двигателя, баллистика — низкий гул, зенитные — свист. */
public class MissileSound extends RangeSound {
    private static final java.util.List<MissileSound> ALL = new java.util.ArrayList<>();
    private final Missile m;
    private final float base;
    private final float loud;
    private int age;
    private float duck = 1;

    /** Сколько петель полёта звучит сейчас (остановленные движком тоже выкидываем). */
    public static int active() {
        var sm = net.minecraft.client.Minecraft.getInstance().getSoundManager();
        ALL.removeIf(s -> s.isStopped() || s.age > 10 && !sm.isActive(s));
        return ALL.size();
    }

    private static boolean mortar(Missile m) {
        return m.look() == 7 || m.look() == 8;
    }

    private static SoundEvent pick(Missile m) {
        if (mortar(m)) {
            return ModSounds.FLY_MORTAR.get();
        }
        if (m.kind() == Missile.Kind.SAM || m.look() == 5) {
            return ModSounds.FLY_JET.get();
        }
        if (m.kind() == Missile.Kind.BALLISTIC) {
            return ModSounds.FLY_MISSILE.get();
        }
        return ModSounds.FLY_ROCKET.get();
    }

    /** Дальность слышимости полёта, блоков. */
    static double rangeOf(Missile m) {
        return mortar(m) ? 50 : m.look() == 6 ? 70 : m.look() == 5 ? 90 : switch (m.kind()) {
            case BALLISTIC -> 320;
            case SAM -> 180;
            default -> 170;
        };
    }

    /** Гранаты и суббоеприпасы летят молча, выстрел РПГ шипит. */
    public static boolean wants(Missile m) {
        return (m.kind() != Missile.Kind.BOMB || m.look() == 6) && m.look() != 11;
    }

    public MissileSound(Missile m) {
        super(pick(m), SoundSource.HOSTILE, rangeOf(m));
        this.m = m;
        ALL.add(this);
        this.looping = true;
        this.base = m.kind() == Missile.Kind.SAM ? 1.35f : 1f;
        this.loud = mortar(m) ? 0.5f : 1f;
        this.pitch = base;
        this.x = m.getX();
        this.y = m.getY();
        this.z = m.getZ();
    }

    /** Пролёт рядом: свой сведённый звук, петля на это время тише. Проверяется у всех снарядов, даже без петли. */
    static void flyby(Missile m) {
        if (m.flybyAt >= 0 || m.kind() == Missile.Kind.SAM || m.kind() == Missile.Kind.BOMB || m.look() == 8 || m.look() == 11) {
            return;
        }
        boolean heavy = m.kind() == Missile.Kind.BALLISTIC;
        var s = m.look() == 7 ? ModSounds.FLYBY_MORTAR.get() : heavy ? ModSounds.FLYBY_BALLISTIC.get() : ModSounds.FLYBY_MLRS.get();
        if (Flyby.check(m, s, heavy ? 80 : m.look() == 7 ? 30 : 50, 1f)) {
            m.flybyAt = m.tickCount;
        }
    }

    @Override
    public void tick() {
        age++;
        if (m.isRemoved()) {
            stop();
            return;
        }
        x = m.getX();
        y = m.getY();
        z = m.getZ();
        boolean passing = m.flybyAt >= 0 && m.tickCount - m.flybyAt < 100;
        duck += ((passing ? 0.2f : 1f) - duck) * 0.2f;
        double speed = m.position().subtract(m.xo, m.yo, m.zo).length();
        // осветительная висит на парашюте — не свистит
        float k = m.look() == 8 && speed < 1 ? 0 : 1;
        volume = loud * gainHere() * duck * k;
        pitch = base * (0.9f + (float) Math.min(0.3, speed * 0.05));
    }
}
