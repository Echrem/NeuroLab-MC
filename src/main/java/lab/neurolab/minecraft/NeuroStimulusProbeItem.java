package lab.neurolab.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Delivers a bounded sensory test pulse to the connectome attached to the targeted mob. */
public final class NeuroStimulusProbeItem extends Item {
    private final StimulusProbeChannel channel;

    public NeuroStimulusProbeItem(StimulusProbeChannel channel, Properties properties) {
        super(properties);
        this.channel = channel;
    }

    public StimulusProbeChannel channel() { return channel; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.neurolab.probe.use").withStyle(net.minecraft.ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.neurolab.probe.low").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.neurolab.probe." + channel.sense())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.sidedSuccess(true);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResult.CONSUME;
        if (!(target instanceof Mob mob)) {
            player.displayClientMessage(Component.translatable("message.neurolab.probe.mob_required"), true);
            return InteractionResult.CONSUME;
        }
        float strength = player.isShiftKeyDown() ? 0.25f : 0.75f;
        String failure = BrainAttachmentService.stimulate(mob, channel.sense(), strength, 40);
        if (failure != null) {
            player.displayClientMessage(Component.literal(failure), true);
            return InteractionResult.CONSUME;
        }
        player.getCooldowns().addCooldown(this, 10);
        if (mob.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    mob.getX(), mob.getEyeY() + 0.1, mob.getZ(), 8, 0.25, 0.15, 0.25, 0.01);
        }
        player.displayClientMessage(Component.translatable("message.neurolab.probe.applied_strength",
                getDescription(), mob.getName(), Math.round(strength * 100)), true);
        return InteractionResult.CONSUME;
    }
}
