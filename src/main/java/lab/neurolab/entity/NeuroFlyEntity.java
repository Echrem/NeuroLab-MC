package lab.neurolab.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public final class NeuroFlyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> PERCHED = SynchedEntityData.defineId(
            NeuroFlyEntity.class, EntityDataSerializers.BOOLEAN);
    public NeuroFlyEntity(EntityType<? extends NeuroFlyEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
        // Research subjects must survive the normal distance-based mob despawn check.
        setPersistenceRequired();
    }

    @Override protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PERCHED, false);
    }

    public void setPerched(boolean perched) { entityData.set(PERCHED, perched); }
    public boolean isPerched() { return entityData.get(PERCHED); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RandomStrollGoal(this, 0.8, 30));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override public void tick() {
        super.tick();
        setNoGravity(true);
        fallDistance = 0;
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }
}
