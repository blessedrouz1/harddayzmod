package com.harddayz.war;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Инженерный блок позиции. Форма задаётся набором коробок для направления «север» (px 0..16) и поворачивается
 * так же, как модель в blockstate (y = 90/180/270), поэтому коллизия совпадает с тем, что видно.
 * NET — без коллизии (сеть), WIRE — колючка: замедляет и ранит.
 */
public class EngBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public enum Type {
        SOLID, NET, WIRE
    }

    private final VoxelShape[] shapes = new VoxelShape[4]; // север, восток, юг, запад
    private final Type type;

    public EngBlock(Properties properties, Type type, double x0, double y0, double z0, double x1, double y1, double z1) {
        this(properties, type, new double[][] {{x0, y0, z0, x1, y1, z1}});
    }

    public EngBlock(Properties properties, Type type, double[][] boxes) {
        super(properties);
        this.type = type;
        for (int r = 0; r < 4; r++) {
            VoxelShape s = Shapes.empty();
            for (double[] b : boxes) {
                s = Shapes.or(s, rotated(b, r));
            }
            shapes[r] = s.optimize();
        }
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Повернуть коробку на r×90° по часовой (вид сверху), как blockstate «y». */
    private static VoxelShape rotated(double[] b, int r) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (r) {
            case 1 -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case 2 -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case 3 -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation rot) {
        return s.setValue(FACING, rot.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes[switch (s.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return type == Type.SOLID ? getShape(s, level, pos, ctx) : Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos pos) {
        return type != Type.SOLID;
    }

    @Override
    public void entityInside(BlockState s, Level level, BlockPos pos, Entity e) {
        if (type != Type.WIRE) {
            return;
        }
        e.makeStuckInBlock(s, new Vec3(0.35, 0.3, 0.35));
        if (level instanceof ServerLevel sl && e instanceof LivingEntity le && le.tickCount % 15 == 0) {
            le.hurt(sl.damageSources().cactus(), 1.5f);
        }
    }
}
