package lab.neurolab.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import lab.neurolab.brain.ControlMode;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/** Stores the experiment subject identity with the placed dish. */
public final class PetriDishBlockEntity extends BlockEntity {
    private static final String SUBJECT_TAG = "SubjectUUID";
    private static final String PROCEDURE_TAG = "ProcedureStart";
    private static final String ORIGINAL_MODE_TAG = "OriginalMode";
    private static final String START_X_TAG = "StartX", START_Y_TAG = "StartY", START_Z_TAG = "StartZ";
    private static final int PROCEDURE_TICKS = 120;
    private UUID subject;
    private long procedureStartTick;
    private ControlMode originalMode = ControlMode.ASSISTED;
    private Vec3 pullOrigin = Vec3.ZERO;

    public PetriDishBlockEntity(BlockPos pos, BlockState state) {
        super(NeuroLabEntities.PETRI_DISH_ENTITY.get(), pos, state);
    }

    public UUID subject() { return subject; }
    public long procedureStartTick() { return procedureStartTick; }

    public void setSubject(UUID subject) {
        this.subject = subject;
        sync();
    }

    public void startProcedure(Mob mob) {
        if (!(level instanceof ServerLevel server)) return;
        subject = mob.getUUID();
        originalMode = BrainAttachmentService.modeOf(mob);
        pullOrigin = mob.position();
        procedureStartTick = server.getGameTime();
        BrainAttachmentService.setMode(mob, ControlMode.OBSERVE);
        mob.setNoAi(true);
        server.playSound(null, worldPosition, SoundEvents.COPPER_BULB_TURN_ON,
                SoundSource.BLOCKS, 0.9f, 0.72f);
        sync();
    }

    public void cancelProcedure(Mob mob) {
        if (procedureStartTick == 0) return;
        BrainAttachmentService.setMode(mob, originalMode);
        procedureStartTick = 0;
        sync();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || procedureStartTick == 0) return;
        Mob mob = subject == null || !(server.getEntity(subject) instanceof Mob found) ? null : found;
        long elapsed = server.getGameTime() - procedureStartTick;
        if (mob == null || !mob.isAlive() || elapsed >= PROCEDURE_TICKS) {
            if (mob != null && mob.isAlive()) BrainAttachmentService.setMode(mob, originalMode);
            procedureStartTick = 0;
            sync();
            return;
        }
        if (elapsed == 72) server.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE,
                SoundSource.BLOCKS, 0.72f, 0.68f);
        if (elapsed == 84) server.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.BLOCKS, 0.42f, 1.42f);
        if (elapsed == 112) server.playSound(null, worldPosition, SoundEvents.COPPER_BULB_TURN_OFF,
                SoundSource.BLOCKS, 0.8f, 0.82f);

        Vec3 seat = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.47,
                worldPosition.getZ() + 0.5);
        double progress = Math.max(0, Math.min(1, elapsed / 72.0));
        double eased = progress * progress * (3 - 2 * progress);
        mob.setPos(pullOrigin.lerp(seat, eased));
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0;

        if (elapsed >= 80 && elapsed <= 108 && elapsed % 4 == 0) {
            double headX = mob.getX();
            double headY = mob.getY() + mob.getBbHeight() * 0.88;
            double headZ = mob.getZ();
            for (int i = 0; i < 5; i++) {
                double t = i / 4.0;
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        headX + (0.5 - t) * 0.24, headY + t * 0.16, headZ + (t - 0.5) * 0.18,
                        1, 0.025, 0.035, 0.025, 0.015);
            }
            if (elapsed % 12 == 0) server.sendParticles(ParticleTypes.FLASH,
                    headX, headY + 0.12, headZ, 1, 0, 0, 0, 0);
            if (elapsed % 12 == 0) server.playSound(null, mob.blockPosition(),
                    SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.55f, 1.25f);
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (subject != null) tag.putUUID(SUBJECT_TAG, subject);
        if (procedureStartTick != 0) tag.putLong(PROCEDURE_TAG, procedureStartTick);
        tag.putString(ORIGINAL_MODE_TAG, originalMode.name());
        tag.putDouble(START_X_TAG, pullOrigin.x);
        tag.putDouble(START_Y_TAG, pullOrigin.y);
        tag.putDouble(START_Z_TAG, pullOrigin.z);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        subject = tag.hasUUID(SUBJECT_TAG) ? tag.getUUID(SUBJECT_TAG) : null;
        procedureStartTick = tag.getLong(PROCEDURE_TAG);
        try { originalMode = ControlMode.valueOf(tag.getString(ORIGINAL_MODE_TAG)); }
        catch (IllegalArgumentException ignored) { originalMode = ControlMode.ASSISTED; }
        pullOrigin = new Vec3(tag.getDouble(START_X_TAG), tag.getDouble(START_Y_TAG), tag.getDouble(START_Z_TAG));
    }

    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
