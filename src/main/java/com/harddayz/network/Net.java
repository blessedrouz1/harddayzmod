package com.harddayz.network;

import com.harddayz.HardDayz;
import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Сетевые пакеты мода (канал Forge). */
public final class Net {
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(HardDayz.MODID, "main"))
            .networkProtocolVersion(1)
            .simpleChannel();

    private Net() {
    }

    /** Сервер → клиент: эффекты взрыва. */
    public record BlastFx(double x, double y, double z, float power) {
        public static final StreamCodec<ByteBuf, BlastFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, BlastFx::x, ByteBufCodecs.DOUBLE, BlastFx::y, ByteBufCodecs.DOUBLE, BlastFx::z,
                ByteBufCodecs.FLOAT, BlastFx::power, BlastFx::new);
    }

    /** Клиент → сервер: приказ пусковой из окна управления. */
    public record FireOrder(int entity, int x, int y, int z, boolean fire, int slot) {
        public static final StreamCodec<ByteBuf, FireOrder> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FireOrder::entity, ByteBufCodecs.VAR_INT, FireOrder::x, ByteBufCodecs.VAR_INT, FireOrder::y,
                ByteBufCodecs.VAR_INT, FireOrder::z, ByteBufCodecs.BOOL, FireOrder::fire, ByteBufCodecs.VAR_INT, FireOrder::slot, FireOrder::new);
    }

    /** Клиент пилота → сервер: позиция дрона. */
    public record DroneMove(int entity, double x, double y, double z, float yaw, float pitch) {
        public static final StreamCodec<ByteBuf, DroneMove> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DroneMove::entity, ByteBufCodecs.DOUBLE, DroneMove::x, ByteBufCodecs.DOUBLE, DroneMove::y,
                ByteBufCodecs.DOUBLE, DroneMove::z, ByteBufCodecs.FLOAT, DroneMove::yaw, ByteBufCodecs.FLOAT, DroneMove::pitch, DroneMove::new);
    }

    /** Клиент пилота → сервер: огонь / отпустить управление. */
    public record DroneAction(int entity, int action) {
        public static final int FIRE = 0;
        public static final int RELEASE = 1;
        public static final int ENGINE = 2;
        public static final StreamCodec<ByteBuf, DroneAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DroneAction::entity, ByteBufCodecs.VAR_INT, DroneAction::action, DroneAction::new);
    }

    /** Клиент → сервер: новый список точек журнала целей в руке (x, y, z подряд). */
    public record LogEdit(boolean offhand, java.util.List<Integer> pts) {
        public static final StreamCodec<ByteBuf, LogEdit> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, LogEdit::offhand,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(com.harddayz.item.TargetLogItem.MAX * 3)), LogEdit::pts, LogEdit::new);
    }

    /** Сервер → клиент: итоги матча. Строка фракции: id;имя;очки;убийства;смерти;уничтожил;потерял;потратил;рубежи;игроков. */
    public record MatchEnd(String winner, java.util.List<String> rows) {
        public static final StreamCodec<ByteBuf, MatchEnd> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, MatchEnd::winner,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), MatchEnd::rows, MatchEnd::new);
    }

    /** Клиент пилота → сервер: записать точку, куда смотрит камера разведдрона. */
    public record DroneMark(int entity, int x, int y, int z) {
        public static final StreamCodec<ByteBuf, DroneMark> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DroneMark::entity, ByteBufCodecs.VAR_INT, DroneMark::x, ByteBufCodecs.VAR_INT, DroneMark::y,
                ByteBufCodecs.VAR_INT, DroneMark::z, DroneMark::new);
    }

    /** Сервер → клиент: трассер пулемёта. */
    public record Tracer(double x1, double y1, double z1, double x2, double y2, double z2, boolean block) {
        public static final StreamCodec<ByteBuf, Tracer> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Tracer::x1, ByteBufCodecs.DOUBLE, Tracer::y1, ByteBufCodecs.DOUBLE, Tracer::z1,
                ByteBufCodecs.DOUBLE, Tracer::x2, ByteBufCodecs.DOUBLE, Tracer::y2, ByteBufCodecs.DOUBLE, Tracer::z2,
                ByteBufCodecs.BOOL, Tracer::block, Tracer::new);
    }

    /** Клиент → сервер: стрелок нажал ЛКМ. */
    /** Клиент → сервер: выстрел из ручного оружия в руке. */
    public record GunShot(boolean aiming) {
        public static final StreamCodec<ByteBuf, GunShot> CODEC = ByteBufCodecs.BOOL.map(GunShot::new, GunShot::aiming);
    }

    /** Клиент → сервер: подствольник (G). */
    public record UnderFire() {
        public static final StreamCodec<ByteBuf, UnderFire> CODEC = StreamCodec.unit(new UnderFire());
    }

    /** Клиент → сервер: точка дальномера в журнал целей. */
    public record LogMark(int x, int y, int z) {
        public static final StreamCodec<ByteBuf, LogMark> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, LogMark::x, ByteBufCodecs.VAR_INT,
                LogMark::y, ByteBufCodecs.VAR_INT, LogMark::z, LogMark::new);
    }

    /** Сервер → клиент: жизни команд в режиме «жизни» (строки «id;имя;жизни»), пусто — режим выключен. */
    public record Lives(java.util.List<String> rows) {
        public static final StreamCodec<ByteBuf, Lives> CODEC = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)).map(Lives::new, Lives::rows);
    }

    /** Клиент пилота → сервер: оружие вертолёта / удар. */
    public record HeliFire(int entity, int weapon) {
        public static final StreamCodec<ByteBuf, HeliFire> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, HeliFire::entity, ByteBufCodecs.VAR_INT,
                HeliFire::weapon, HeliFire::new);
    }

    public record GunnerFire(int entity) {
        public static final StreamCodec<ByteBuf, GunnerFire> CODEC = ByteBufCodecs.VAR_INT.map(GunnerFire::new, GunnerFire::entity);
    }

    /** Сервер → клиент: состояние снабжения фракции (открыть/обновить магазин). */
    public record ShopState(String faction, int budget, boolean commander, java.util.List<String> orders, boolean open) {
        public static final StreamCodec<ByteBuf, ShopState> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ShopState::faction, ByteBufCodecs.VAR_INT, ShopState::budget, ByteBufCodecs.BOOL, ShopState::commander,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ShopState::orders, ByteBufCodecs.BOOL, ShopState::open, ShopState::new);
    }

    /** Клиент → сервер: купить позицию каталога (-1 — просто обновить состояние). */
    public record ShopBuy(int index) {
        public static final StreamCodec<ByteBuf, ShopBuy> CODEC = ByteBufCodecs.VAR_INT.map(ShopBuy::new, ShopBuy::index);
    }

    /** Собрать состояние магазина для игрока. */
    public static ShopState shopState(ServerPlayer p, boolean open) {
        var f = com.harddayz.war.War.of(p.getUUID());
        java.util.List<String> orders = new java.util.ArrayList<>();
        if (f == null) {
            return new ShopState("", 0, false, orders, open);
        }
        long now = p.level().getServer().overworld().getGameTime();
        for (var o : f.orders) {
            orders.add(o.entry + ";" + Math.max(0, (o.due - now) / 20) + ";" + o.by);
        }
        return new ShopState(f.name, f.budget, com.harddayz.war.War.isCommander(p), orders, open);
    }

    /** Клиент → сервер: запросить картину радара (open — открыть экран). */
    public record RadarRequest(boolean open) {
        public static final StreamCodec<ByteBuf, RadarRequest> CODEC = ByteBufCodecs.BOOL.map(RadarRequest::new, RadarRequest::open);
    }

    /** Сервер → клиент: метки целей относительно игрока «тип;dx;dz;высота;возраст». */
    public record RadarState(int radars, java.util.List<String> contacts, boolean open) {
        public static final StreamCodec<ByteBuf, RadarState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RadarState::radars, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RadarState::contacts,
                ByteBufCodecs.BOOL, RadarState::open, RadarState::new);
    }

    /** Сервер → клиент: тревога на экране. */
    public record Alert(String text) {
        public static final StreamCodec<ByteBuf, Alert> CODEC = ByteBufCodecs.STRING_UTF8.map(Alert::new, Alert::text);
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
        CHANNEL.messageBuilder(RadarRequest.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(RadarRequest.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null) {
                        toPlayer(sp, radarState(sp, p.open()));
                    }
                })
                .add();
        CHANNEL.messageBuilder(RadarState.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(RadarState.CODEC.cast())
                .consumerMainThread((p, ctx) -> com.harddayz.client.RadarScreen.onState(p))
                .add();
        CHANNEL.messageBuilder(Alert.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(Alert.CODEC.cast())
                .consumerMainThread((p, ctx) -> com.harddayz.client.ClientSetup.alert(p.text()))
                .add();
        CHANNEL.messageBuilder(ShopState.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(ShopState.CODEC.cast())
                .consumerMainThread((p, ctx) -> com.harddayz.client.ShopScreen.onState(p))
                .add();
        CHANNEL.messageBuilder(ShopBuy.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(ShopBuy.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp == null) {
                        return;
                    }
                    if (p.index() >= 0) {
                        var err = com.harddayz.war.Supply.buy(sp, p.index());
                        if (err != null) {
                            sp.sendOverlayMessage(err.copy().withStyle(net.minecraft.ChatFormatting.RED));
                        }
                    }
                    toPlayer(sp, shopState(sp, false));
                })
                .add();
        CHANNEL.messageBuilder(Tracer.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(Tracer.CODEC.cast())
                .consumerMainThread((p, ctx) -> com.harddayz.client.ClientGunner.tracer(p))
                .add();
        CHANNEL.messageBuilder(UnderFire.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(UnderFire.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.getMainHandItem().getItem() instanceof com.harddayz.item.GunItem g) {
                        g.fireUnder(sp, sp.getMainHandItem());
                    }
                })
                .add();
        CHANNEL.messageBuilder(LogMark.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(LogMark.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
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
                            sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.point_saved", n, p.x(), p.y(), p.z())
                                    .withStyle(net.minecraft.ChatFormatting.GREEN));
                            return;
                        }
                    }
                    sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.no_log")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                })
                .add();
        CHANNEL.messageBuilder(Lives.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(Lives.CODEC.cast())
                .consumerMainThread((p, ctx) -> com.harddayz.client.ClientAccess.lives(p.rows()))
                .add();
        CHANNEL.messageBuilder(HeliFire.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(HeliFire.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.level().getEntity(p.entity()) instanceof Launcher l) {
                        l.heliFire(sp, p.weapon());
                    }
                })
                .add();
        CHANNEL.messageBuilder(GunShot.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(GunShot.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.getVehicle() == null && !com.harddayz.war.Medic.isDowned(sp)
                            && sp.getMainHandItem().getItem() instanceof com.harddayz.item.GunItem g) {
                        g.shoot(sp, sp.getMainHandItem(), p.aiming());
                    }
                })
                .add();
        CHANNEL.messageBuilder(GunnerFire.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(GunnerFire.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.level().getEntity(p.entity()) instanceof Launcher l) {
                        l.gunnerFire(sp);
                    }
                })
                .add();
        CHANNEL.messageBuilder(BlastFx.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(BlastFx.CODEC.cast())
                .consumerMainThread((p, ctx) -> com.harddayz.client.ClientBlasts.add(p))
                .add();
        CHANNEL.messageBuilder(FireOrder.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(FireOrder.CODEC.cast())
                .consumerMainThread(Net::onFireOrder)
                .add();
        CHANNEL.messageBuilder(DroneMove.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(DroneMove.CODEC.cast())
                .consumerMainThread(Net::onDroneMove)
                .add();
        CHANNEL.messageBuilder(DroneMark.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(DroneMark.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null && sp.level().getEntity(p.entity()) instanceof Drone d) {
                        d.pilotMark(sp, new BlockPos(p.x(), p.y(), p.z()));
                    }
                })
                .add();
        CHANNEL.messageBuilder(MatchEnd.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(MatchEnd.CODEC.cast())
                .consumerMainThread((p, ctx) -> com.harddayz.client.ClientAccess.matchEnd(p.winner(), p.rows()))
                .add();
        CHANNEL.messageBuilder(LogEdit.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(LogEdit.CODEC.cast())
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    if (sp != null) {
                        var stack = sp.getItemInHand(p.offhand() ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
                        if (stack.is(com.harddayz.registry.ModItems.TARGET_LOG.get())) {
                            com.harddayz.item.TargetLogItem.set(stack, p.pts());
                        }
                    }
                })
                .add();
        CHANNEL.messageBuilder(DroneAction.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(DroneAction.CODEC.cast())
                .consumerMainThread(Net::onDroneAction)
                .add();
    }

    private static void onFireOrder(FireOrder p, CustomPayloadEvent.Context ctx) {
        ServerPlayer sp = ctx.getSender();
        if (sp != null && sp.level().getEntity(p.entity()) instanceof Launcher l && l.distanceTo(sp) < 10) {
            BlockPos t = new BlockPos(p.x(), p.y(), p.z());
            l.setSlot(p.slot(), t);
            if (p.fire()) {
                l.order(sp, t);
            }
        }
    }

    private static void onDroneMove(DroneMove p, CustomPayloadEvent.Context ctx) {
        ServerPlayer sp = ctx.getSender();
        if (sp != null && sp.level().getEntity(p.entity()) instanceof Drone d) {
            d.pilotMove(sp, new Vec3(p.x(), p.y(), p.z()), p.yaw(), p.pitch());
        }
    }

    private static void onDroneAction(DroneAction p, CustomPayloadEvent.Context ctx) {
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

    // ------------------------------------------------------------------ отправка

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(msg, PacketDistributor.PLAYER.with(player));
    }

    public static void toServer(Object msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }
}
