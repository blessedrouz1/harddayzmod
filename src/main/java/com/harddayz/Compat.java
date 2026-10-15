package com.harddayz;

import java.util.Comparator;
import java.util.Optional;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/** Мелкие помощники для 1.20.1: то, что в новых версиях Minecraft есть «из коробки». */
public final class Compat {
    /** Временный тикет чанков для летящих ракет и дронов: истекает сам через 2 секунды (как тикет эндер-жемчуга в новых версиях). */
    public static final TicketType<ChunkPos> FLIGHT = TicketType.create("harddayz_flight", Comparator.comparingLong(ChunkPos::toLong), 40);

    private Compat() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(HardDayz.MODID, path);
    }

    /** Держать чанки вокруг точки загруженными и «живыми» ещё 40 тиков. */
    public static void ticket(ServerLevel level, ChunkPos pos, int radius) {
        level.getChunkSource().addRegionTicket(FLIGHT, pos, radius, pos);
    }

    /** Частицы, видные издалека (до 512 блоков), всем игрокам мира. */
    public static <T extends ParticleOptions> void particles(ServerLevel level, T type, double x, double y, double z, int count, double dx, double dy,
            double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** Списать один предмет (в творческом режиме не тратится). */
    public static void consume(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.player.Player player) {
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** Свои данные предмета (в новых версиях — компонент CUSTOM_DATA), копия или пустой тег. */
    public static CompoundTag data(net.minecraft.world.item.ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? new CompoundTag() : t.copy();
    }

    // ------------------------------------------------------------------ NBT с значениями по умолчанию

    public static int getInt(CompoundTag t, String k, int def) {
        return t.contains(k, Tag.TAG_ANY_NUMERIC) ? t.getInt(k) : def;
    }

    public static float getFloat(CompoundTag t, String k, float def) {
        return t.contains(k, Tag.TAG_ANY_NUMERIC) ? t.getFloat(k) : def;
    }

    public static double getDouble(CompoundTag t, String k, double def) {
        return t.contains(k, Tag.TAG_ANY_NUMERIC) ? t.getDouble(k) : def;
    }

    public static long getLong(CompoundTag t, String k, long def) {
        return t.contains(k, Tag.TAG_ANY_NUMERIC) ? t.getLong(k) : def;
    }

    public static boolean getBoolean(CompoundTag t, String k, boolean def) {
        return t.contains(k, Tag.TAG_ANY_NUMERIC) ? t.getBoolean(k) : def;
    }

    public static String getString(CompoundTag t, String k, String def) {
        return t.contains(k, Tag.TAG_STRING) ? t.getString(k) : def;
    }

    public static Optional<String> optString(CompoundTag t, String k) {
        return t.contains(k, Tag.TAG_STRING) ? Optional.of(t.getString(k)) : Optional.empty();
    }

    // ------------------------------------------------------------------ плавное движение на клиенте

    /**
     * Линейная интерполяция позиции, присланной сервером (в новых версиях — InterpolationHandler).
     * Сущность переопределяет {@code lerpTo} и зовёт {@link #tick} в клиентском тике.
     */
    public static final class Lerp {
        private final int steps;
        private int left;
        private double x;
        private double y;
        private double z;
        private float yRot;
        private float xRot;

        public Lerp(int steps) {
            this.steps = steps;
        }

        public void set(double x, double y, double z, float yRot, float xRot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.left = steps;
        }

        public void tick(Entity e) {
            if (left <= 0) {
                return;
            }
            double nx = e.getX() + (x - e.getX()) / left;
            double ny = e.getY() + (y - e.getY()) / left;
            double nz = e.getZ() + (z - e.getZ()) / left;
            float ny0 = e.getYRot() + Mth.wrapDegrees(yRot - e.getYRot()) / left;
            float nx0 = e.getXRot() + (xRot - e.getXRot()) / left;
            left--;
            e.setPos(nx, ny, nz);
            e.setYRot(ny0 % 360);
            e.setXRot(nx0);
        }

        public void stop() {
            left = 0;
        }
    }
}
