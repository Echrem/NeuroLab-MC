package lab.neurolab.minecraft;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Marks a repeatable one-minute interval in the subject's existing server telemetry log. */
public final class NeuroTrialRecorderItem extends Item {
    public NeuroTrialRecorderItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResult.CONSUME;
        if (!(target instanceof Mob mob)) {
            player.displayClientMessage(Component.translatable("message.neurolab.probe.mob_required"), true);
            return InteractionResult.CONSUME;
        }
        boolean stop = player.isShiftKeyDown();
        var log = TelemetryLog.status();
        if (!stop && (!log.running() || log.failed())) {
            player.displayClientMessage(Component.translatable("message.neurolab.recorder.unavailable"), true);
            return InteractionResult.CONSUME;
        }
        String label = "field-" + mob.level().getGameTime();
        String failure = stop ? BrainAttachmentService.endTrial(mob)
                : BrainAttachmentService.startTrial(mob, label, 1200);
        if (failure != null) {
            player.displayClientMessage(Component.literal(failure), true);
            return InteractionResult.CONSUME;
        }
        player.getCooldowns().addCooldown(this, 20);
        player.displayClientMessage(Component.translatable(stop
                ? "message.neurolab.recorder.stopped" : "message.neurolab.recorder.started", mob.getName(), label), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.neurolab.recorder.start").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.neurolab.recorder.stop").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.neurolab.recorder.file").withStyle(ChatFormatting.GRAY));
    }
}
