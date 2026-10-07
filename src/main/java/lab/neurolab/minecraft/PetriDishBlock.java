package lab.neurolab.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.InteractionResult;
import com.mojang.serialization.MapCodec;

/** Compact experiment chair that docks one subject for inspection at the Neuro Monitor. */
public final class PetriDishBlock extends BaseEntityBlock {
    public static final MapCodec<PetriDishBlock> CODEC = simpleCodec(PetriDishBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape BASE_COLLISION = Shapes.box(1 / 16.0, 0, 1 / 16.0, 15 / 16.0, 3 / 16.0, 15 / 16.0);

    public PetriDishBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override public MapCodec<PetriDishBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        net.minecraft.core.Direction facing = state.getValue(FACING);
        return Shapes.or(BASE_COLLISION,
                facingBox(facing, 2, 7, 2, 14, 12, 14),
                facingBox(facing, 2, 9, 11, 14, 29, 16),
                facingBox(facing, 1, 9, 3, 5, 16, 11),
                facingBox(facing, 11, 9, 3, 15, 16, 11),
                facingBox(facing, 3, 27, 7, 13, 29, 10));
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                  CollisionContext context) {
        return BASE_COLLISION;
    }
    private static VoxelShape facingBox(net.minecraft.core.Direction facing,
                                       double x1, double y1, double z1, double x2, double y2, double z2) {
        double minX, maxX, minZ, maxZ;
        switch (facing) {
            case EAST -> { minX = 16 - z2; maxX = 16 - z1; minZ = x1; maxZ = x2; }
            case SOUTH -> { minX = 16 - x2; maxX = 16 - x1; minZ = 16 - z2; maxZ = 16 - z1; }
            case WEST -> { minX = z1; maxX = z2; minZ = 16 - x2; maxZ = 16 - x1; }
            default -> { minX = x1; maxX = x2; minZ = z1; maxZ = z2; }
        }
        return Shapes.box(minX / 16, y1 / 16, minZ / 16, maxX / 16, y2 / 16, maxZ / 16);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetriDishBlockEntity(pos, state);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos,
                                   BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof PetriDishBlockEntity chair
                && chair.subject() != null && level.getServer() != null
                && level.getServer().getLevel(level.dimension()) instanceof ServerLevel server
                && server.getEntity(chair.subject()) instanceof Mob mob) {
            chair.cancelProcedure(mob);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == NeuroLabEntities.PETRI_DISH_ENTITY.get()
                ? (world, pos, blockState, entity) -> ((PetriDishBlockEntity) entity).serverTick() : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PetriDishBlockEntity dish)
                || !(level instanceof ServerLevel server)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (dish.subject() != null && server.getEntity(dish.subject()) instanceof Mob previous) {
                dish.cancelProcedure(previous);
                BrainAttachmentService.endTrial(previous);
            }
            dish.setSubject(null);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Experiment stand cleared."), true);
            return InteractionResult.CONSUME;
        }

        Mob subject = dish.subject() == null ? null : server.getEntity(dish.subject()) instanceof Mob mob ? mob : null;
        if (subject == null) {
            subject = server.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(6), Mob::isAlive).stream()
                    .filter(mob -> mob.distanceToSqr(pos.getCenter()) <= 36)
                    .min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(pos.getCenter())))
                    .orElse(null);
            if (subject == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Bring a mob within 6 blocks of the experiment stand."), true);
                return InteractionResult.CONSUME;
            }
            if (!BrainAttachmentService.isAttached(subject)) {
                String failure = BrainAttachmentService.attachFailure(subject);
                if (failure != null) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(failure), true);
                    return InteractionResult.CONSUME;
                }
                if (!BrainAttachmentService.attach(subject)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Could not attach the connectome to this subject."), true);
                    return InteractionResult.CONSUME;
                }
            }
            dish.setSubject(subject.getUUID());
            String trialFailure = BrainAttachmentService.startTrial(subject, "neuro-stand", 1200);
            if (trialFailure != null) {
                dish.setSubject(null);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(trialFailure), true);
                return InteractionResult.CONSUME;
            }
            dish.startProcedure(subject);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Mechanical arm secured " + subject.getName().getString()
                            + " for a 6-second neural procedure; it will be released automatically."), true);
            return InteractionResult.CONSUME;
        }

        if (!subject.isAlive() || !BrainAttachmentService.isAttached(subject)) {
            dish.setSubject(null);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Subject is no longer available; the stand has been cleared."), true);
            return InteractionResult.CONSUME;
        }
        if (dish.procedureStartTick() != 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "The neural procedure is in progress; the subject will be released shortly."), true);
            return InteractionResult.CONSUME;
        }
        dish.startProcedure(subject);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "Mechanical arm started a 6-second neural procedure for " + subject.getName().getString() + "."), true);
        return InteractionResult.CONSUME;
    }
}
