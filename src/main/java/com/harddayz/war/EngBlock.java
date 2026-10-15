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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Инженерный блок позиции: форма по направлению (задаётся для севера), без коллизии (маскировочная сеть),
 * колючая проволока — замедляет и ранит.
 */
public class EngBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public enum Type {
        SOLID, NET, WIRE
    }

    private final VoxelShape north;
    private final VoxelShape east;
    private final Type type;

    public EngBlock(Properties properties, Type type, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(properties);
        this.type = type;
        this.north = Block.box(x0, y0, z0, x1, y1, z1);
        this.east = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction d = s.getValue(FACING);
        return d.getAxis() == Direction.Axis.Z ? north : east;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return type == Type.SOLID ? getShape(s, level, pos, ctx) : Shapes.empty();
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
