package com.harddayz.network;

import com.harddayz.HardDayz;
import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Сетевые пакеты мода (канал Forge). */
public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HardDayz.MODID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    /** Обработчик пакета на главном потоке: пакет и контекст (отправитель). */
    private interface Handler<T> {
        void handle(T msg, NetworkEvent.Context ctx);
    }

    private static <T> void reg(Class<T> type, NetworkDirection dir, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
            Handler<T> handler) {
        CHANNEL.messageBuilder(type, nextId++, dir).encoder(enc).decoder(dec).consumerMainThread((T msg, Supplier<NetworkEvent.Context> ctx) -> {
            handler.handle(msg, ctx.get());
        }).add();
    }

    private static void writeList(FriendlyByteBuf b, List<String> l) {
        b.writeVarInt(l.size());
        for (String s : l) {
            b.writeUtf(s);
        }
    }

    private static List<String> readList(FriendlyByteBuf b, int max) {
        int n = readCount(b, max);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(b.readUtf());
        }
        return out;
    }

    private Net() {
    }

    /** Сервер → клиент: эффекты взрыва. */
    public record BlastFx(double x, double y, double z, float power) {
    }

    /** Клиент → сервер: приказ пусковой из окна управления. */
    public record FireOrder(int entity, int x, int y, int z, boolean fire, int slot) {
    }

    /** Клиент пилота → сервер: позиция дрона. */
    public record DroneMove(int entity, double x, double y, double z, float yaw, float pitch) {
    }

    /** Клиент пилота → сервер: огонь / отпустить управление. */
    public record DroneAction(int entity, int action) {
        public static final int FIRE = 0;
        public static final int RELEASE = 1;
        public static final int ENGINE = 2;
    }

    /** Клиент → сервер: новый список точек журнала целей в руке (x, y, z подряд). */
    public record LogEdit(boolean offhand, java.util.List<Integer> pts) {
    }

    /** Сервер → клиент: итоги матча. Строка фракции: id;имя;очки;убийства;смерти;уничтожил;потерял;потратил;рубежи;игроков. */
    public record MatchEnd(String winner, java.util.List<String> rows) {
    }

    /** Клиент пилота → сервер: записать точку, куда смотрит камера разведдрона. */
    public record DroneMark(int entity, int x, int y, int z) {
    }

    /** Сервер → клиент: трассер пулемёта. */
    public record Tracer(double x1, double y1, double z1, double x2, double y2, double z2, boolean block) {
    }

    /** Клиент → сервер: стрелок нажал ЛКМ. */
    /** Клиент → сервер: выстрел из ручного оружия в руке. */
    public record GunShot(boolean aiming) {
    }

    /** Клиент → сервер: подствольник (G). */
    public record UnderFire() {
    }

    /** Клиент → сервер: точка дальномера в журнал целей. */
    public record LogMark(int x, int y, int z) {
    }

    /** Сервер → клиент: жизни команд в режиме «жизни» (строки «id;имя;жизни»), пусто — режим выключен. */
    public record Lives(java.util.List<String> rows) {
    }

    /** Клиент пилота → сервер: оружие вертолёта / удар. */
    public record HeliFire(int entity, int weapon) {
    }

    public record GunnerFire(int entity) {
    }

    /** Сервер → клиент: состояние снабжения фракции (открыть/обновить магазин). */
    public record ShopState(String faction, int budget, boolean commander, java.util.List<String> orders, boolean open) {
    }

    /** Клиент → сервер: купить позицию каталога (-1 — просто обновить состояние). */
    public record ShopBuy(int index) {
    }

    /** Собрать состояние магазина для игрока. */
    public static ShopState shopState(ServerPlayer p, boolean open) {
        var f = com.harddayz.war.War.of(p.getUUID());
        java.util.List<String> orders = new java.util.ArrayList<>();
        if (f == null) {
            return new ShopState("", 0, false, orders, open);
        }
        long now = p.getServer().overworld().getGameTime();
        for (var o : f.orders) {
            orders.add(o.entry + ";" + Math.max(0, (o.due - now) / 20) + ";" + o.by);
        }
        return new ShopState(f.name, f.budget, com.harddayz.war.War.isCommander(p), orders, open);
    }

    /** Клиент → сервер: запросить картину радара (open — открыть экран). */
    public record RadarRequest(boolean open) {
    }

    /** Сервер → клиент: метки целей относительно игрока «тип;dx;dz;высота;возраст». */
    public record RadarState(int radars, java.util.List<String> contacts, boolean open) {
    }

    /** Сервер → клиент: тревога на экране. */
    public record Alert(String text) {
    }

    public static RadarState radarState(ServerPlayer p, boolean open) {
        String key = com.harddayz.war.RadarNet.key(p.getUUID());
        long now = p.level().getGameTime();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var c : com.harddayz.war.RadarNet.contacts(key, now)) {
            out.add(c.type + ";" + (int) (c.pos.x - p.getX()) + ";" + (int) (c.pos.z - p.getZ()) + ";" + (int) c.pos.y + ";" + (now - c.seen));
        }
        return new RadarState(com.harddayz.war.RadarNet.radarCount(key), out, open);
    }

    public static void init() {
        reg(RadarRequest.class, NetworkDirection.PLAY_TO_SERVER, Net::encRadarRequest, Net::decRadarRequest, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null) {
                        toPlayer(sp, radarState(sp, p.open()));
                    }
                });
        reg(RadarState.class, NetworkDirection.PLAY_TO_CLIENT, Net::encRadarState, Net::decRadarState, (p, ctx) -> com.harddayz.client.RadarScreen.onState(p));
        reg(Alert.class, NetworkDirection.PLAY_TO_CLIENT, Net::encAlert, Net::decAlert, (p, ctx) -> com.harddayz.client.ClientSetup.alert(p.text()));
        reg(ShopState.class, NetworkDirection.PLAY_TO_CLIENT, Net::encShopState, Net::decShopState, (p, ctx) -> com.harddayz.client.ShopScreen.onState(p));
        reg(ShopBuy.class, NetworkDirection.PLAY_TO_SERVER, Net::encShopBuy, Net::decShopBuy, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp == null) {
                        return;
                    }
                    if (p.index() >= 0) {
                        var err = com.harddayz.war.Supply.buy(sp, p.index());
                        if (err != null) {
                            sp.displayClientMessage(err.copy().withStyle(net.minecraft.ChatFormatting.RED), true);
                        }
                    }
                    toPlayer(sp, shopState(sp, false));
                });
        reg(Tracer.class, NetworkDirection.PLAY_TO_CLIENT, Net::encTracer, Net::decTracer, (p, ctx) -> com.harddayz.client.ClientGunner.tracer(p));
        reg(UnderFire.class, NetworkDirection.PLAY_TO_SERVER, Net::encUnderFire, Net::decUnderFire, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.getMainHandItem().getItem() instanceof com.harddayz.item.GunItem g) {
                        g.fireUnder(sp, sp.getMainHandItem());
                    }
                });
        reg(LogMark.class, NetworkDirection.PLAY_TO_SERVER, Net::encLogMark, Net::decLogMark, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
                    if (sp == null || !sp.getMainHandItem().is(com.harddayz.registry.ModItems.RANGEFINDER.get())
                            || pos.distToCenterSqr(sp.position()) > 900 * 900) {
                        return;
                    }
                    for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                        var s = sp.getInventory().getItem(i);
                        if (s.is(com.harddayz.registry.ModItems.TARGET_LOG.get())) {
                            int n = com.harddayz.item.TargetLogItem.add(s, pos);
                            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.point_saved", n, p.x(), p.y(), p.z())
                                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
                            return;
                        }
                    }
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.no_log")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                });
        reg(Lives.class, NetworkDirection.PLAY_TO_CLIENT, Net::encLives, Net::decLives, (p, ctx) -> com.harddayz.client.ClientAccess.lives(p.rows()));
        reg(HeliFire.class, NetworkDirection.PLAY_TO_SERVER, Net::encHeliFire, Net::decHeliFire, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.level().getEntity(p.entity()) instanceof Launcher l) {
                        l.heliFire(sp, p.weapon());
                    }
                });
        reg(GunShot.class, NetworkDirection.PLAY_TO_SERVER, Net::encGunShot, Net::decGunShot, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.getVehicle() == null && !com.harddayz.war.Medic.isDowned(sp)
                            && sp.getMainHandItem().getItem() instanceof com.harddayz.item.GunItem g) {
                        g.shoot(sp, sp.getMainHandItem(), p.aiming());
                    }
                });
        reg(GunnerFire.class, NetworkDirection.PLAY_TO_SERVER, Net::encGunnerFire, Net::decGunnerFire, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.level().getEntity(p.entity()) instanceof Launcher l) {
                        l.gunnerFire(sp);
                    }
                });
        reg(BlastFx.class, NetworkDirection.PLAY_TO_CLIENT, Net::encBlastFx, Net::decBlastFx, (p, ctx) -> com.harddayz.client.ClientBlasts.add(p));
        reg(FireOrder.class, NetworkDirection.PLAY_TO_SERVER, Net::encFireOrder, Net::decFireOrder, Net::onFireOrder);
        reg(DroneMove.class, NetworkDirection.PLAY_TO_SERVER, Net::encDroneMove, Net::decDroneMove, Net::onDroneMove);
        reg(DroneMark.class, NetworkDirection.PLAY_TO_SERVER, Net::encDroneMark, Net::decDroneMark, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.level().getEntity(p.entity()) instanceof Drone d) {
                        d.pilotMark(sp, new BlockPos(p.x(), p.y(), p.z()));
                    }
                });
        reg(MatchEnd.class, NetworkDirection.PLAY_TO_CLIENT, Net::encMatchEnd, Net::decMatchEnd, (p, ctx) -> com.harddayz.client.ClientAccess.matchEnd(p.winner(), p.rows()));
        reg(LogEdit.class, NetworkDirection.PLAY_TO_SERVER, Net::encLogEdit, Net::decLogEdit, (p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null) {
                        var stack = sp.getItemInHand(p.offhand() ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
                        if (stack.is(com.harddayz.registry.ModItems.TARGET_LOG.get())) {
                            com.harddayz.item.TargetLogItem.set(stack, p.pts());
                        }
                    }
                });
        reg(DroneAction.class, NetworkDirection.PLAY_TO_SERVER, Net::encDroneAction, Net::decDroneAction, Net::onDroneAction);
    }

    private static void onFireOrder(FireOrder p, NetworkEvent.Context ctx) {
        ServerPlayer sp = ctx.getSender();
        if (sp != null && sp.level().getEntity(p.entity()) instanceof Launcher l && l.distanceTo(sp) < 10) {
            BlockPos t = new BlockPos(p.x(), p.y(), p.z());
            l.setSlot(p.slot(), t);
            if (p.fire()) {
                l.order(sp, t);
            }
        }
    }

    private static void onDroneMove(DroneMove p, NetworkEvent.Context ctx) {
        ServerPlayer sp = ctx.getSender();
        if (sp != null && sp.level().getEntity(p.entity()) instanceof Drone d) {
            d.pilotMove(sp, new Vec3(p.x(), p.y(), p.z()), p.yaw(), p.pitch());
        }
    }

    private static void onDroneAction(DroneAction p, NetworkEvent.Context ctx) {
        ServerPlayer sp = ctx.getSender();
        if (sp != null && sp.level().getEntity(p.entity()) instanceof Drone d && d.pilotId() == sp.getId()) {
            if (p.action() == DroneAction.FIRE) {
                d.pilotFire(sp);
            } else if (p.action() == DroneAction.ENGINE) {
                d.toggleEngine(sp);
            } else {
                d.release();
            }
        }
    }

    // ------------------------------------------------------------------ кодеки

    private static List<Integer> readInts(FriendlyByteBuf b) {
        int n = readCount(b, com.harddayz.item.TargetLogItem.MAX * 3);
        if (n % 3 != 0) {
            throw new io.netty.handler.codec.DecoderException("Incomplete target coordinates");
        }
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(b.readVarInt());
        }
        return out;
    }

    private static int readCount(FriendlyByteBuf b, int max) {
        int count = b.readVarInt();
        if (count < 0 || count > max) {
            throw new io.netty.handler.codec.DecoderException("Invalid list size: " + count);
        }
        return count;
    }

    private static void encBlastFx(BlastFx m, FriendlyByteBuf b) {
        b.writeDouble(m.x());
        b.writeDouble(m.y());
        b.writeDouble(m.z());
        b.writeFloat(m.power());
    }

    private static BlastFx decBlastFx(FriendlyByteBuf b) {
        return new BlastFx(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat());
    }

    private static void encFireOrder(FireOrder m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity());
        b.writeVarInt(m.x());
        b.writeVarInt(m.y());
        b.writeVarInt(m.z());
        b.writeBoolean(m.fire());
        b.writeVarInt(m.slot());
    }

    private static FireOrder decFireOrder(FriendlyByteBuf b) {
        return new FireOrder(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt());
    }

    private static void encDroneMove(DroneMove m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity());
        b.writeDouble(m.x());
        b.writeDouble(m.y());
        b.writeDouble(m.z());
        b.writeFloat(m.yaw());
        b.writeFloat(m.pitch());
    }

    private static DroneMove decDroneMove(FriendlyByteBuf b) {
        return new DroneMove(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat());
    }

    private static void encDroneAction(DroneAction m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity());
        b.writeVarInt(m.action());
    }

    private static DroneAction decDroneAction(FriendlyByteBuf b) {
        return new DroneAction(b.readVarInt(), b.readVarInt());
    }

    private static void encLogEdit(LogEdit m, FriendlyByteBuf b) {
        b.writeBoolean(m.offhand());
        b.writeVarInt(m.pts().size()); for (int v : m.pts()) { b.writeVarInt(v); }
    }

    private static LogEdit decLogEdit(FriendlyByteBuf b) {
        return new LogEdit(b.readBoolean(), readInts(b));
    }

    private static void encMatchEnd(MatchEnd m, FriendlyByteBuf b) {
        b.writeUtf(m.winner());
        writeList(b, m.rows());
    }

    private static MatchEnd decMatchEnd(FriendlyByteBuf b) {
        return new MatchEnd(b.readUtf(), readList(b, 4096));
    }

    private static void encDroneMark(DroneMark m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity());
        b.writeVarInt(m.x());
        b.writeVarInt(m.y());
        b.writeVarInt(m.z());
    }

    private static DroneMark decDroneMark(FriendlyByteBuf b) {
        return new DroneMark(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    private static void encTracer(Tracer m, FriendlyByteBuf b) {
        b.writeDouble(m.x1());
        b.writeDouble(m.y1());
        b.writeDouble(m.z1());
        b.writeDouble(m.x2());
        b.writeDouble(m.y2());
        b.writeDouble(m.z2());
        b.writeBoolean(m.block());
    }

    private static Tracer decTracer(FriendlyByteBuf b) {
        return new Tracer(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean());
    }

    private static void encGunShot(GunShot m, FriendlyByteBuf b) {
        b.writeBoolean(m.aiming());
    }

    private static GunShot decGunShot(FriendlyByteBuf b) {
        return new GunShot(b.readBoolean());
    }

    private static void encUnderFire(UnderFire m, FriendlyByteBuf b) {
    }

    private static UnderFire decUnderFire(FriendlyByteBuf b) {
        return new UnderFire();
    }

    private static void encLogMark(LogMark m, FriendlyByteBuf b) {
        b.writeVarInt(m.x());
        b.writeVarInt(m.y());
        b.writeVarInt(m.z());
    }

    private static LogMark decLogMark(FriendlyByteBuf b) {
        return new LogMark(b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    private static void encLives(Lives m, FriendlyByteBuf b) {
        writeList(b, m.rows());
    }

    private static Lives decLives(FriendlyByteBuf b) {
        return new Lives(readList(b, 4096));
    }

    private static void encHeliFire(HeliFire m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity());
        b.writeVarInt(m.weapon());
    }

    private static HeliFire decHeliFire(FriendlyByteBuf b) {
        return new HeliFire(b.readVarInt(), b.readVarInt());
    }

    private static void encGunnerFire(GunnerFire m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity());
    }

    private static GunnerFire decGunnerFire(FriendlyByteBuf b) {
        return new GunnerFire(b.readVarInt());
    }

    private static void encShopState(ShopState m, FriendlyByteBuf b) {
        b.writeUtf(m.faction());
        b.writeVarInt(m.budget());
        b.writeBoolean(m.commander());
        writeList(b, m.orders());
        b.writeBoolean(m.open());
    }

    private static ShopState decShopState(FriendlyByteBuf b) {
        return new ShopState(b.readUtf(), b.readVarInt(), b.readBoolean(), readList(b, 4096), b.readBoolean());
    }

    private static void encShopBuy(ShopBuy m, FriendlyByteBuf b) {
        b.writeVarInt(m.index());
    }

    private static ShopBuy decShopBuy(FriendlyByteBuf b) {
        return new ShopBuy(b.readVarInt());
    }

    private static void encRadarRequest(RadarRequest m, FriendlyByteBuf b) {
        b.writeBoolean(m.open());
    }

    private static RadarRequest decRadarRequest(FriendlyByteBuf b) {
        return new RadarRequest(b.readBoolean());
    }

    private static void encRadarState(RadarState m, FriendlyByteBuf b) {
        b.writeVarInt(m.radars());
        writeList(b, m.contacts());
        b.writeBoolean(m.open());
    }

    private static RadarState decRadarState(FriendlyByteBuf b) {
        return new RadarState(b.readVarInt(), readList(b, 4096), b.readBoolean());
    }

    private static void encAlert(Alert m, FriendlyByteBuf b) {
        b.writeUtf(m.text());
    }

    private static Alert decAlert(FriendlyByteBuf b) {
        return new Alert(b.readUtf());
    }

    // ------------------------------------------------------------------ отправка

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
