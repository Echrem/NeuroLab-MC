package lab.neurolab.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Attaches the bundled connectome to the mob directly targeted by the player. */
public final class NeuroMindImprinterItem extends Item {
    public NeuroMindImprinterItem(Properties properties) { super(properties); }

    public static InteractionResult applyTo(ItemStack stack, Player player, LivingEntity target) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(target instanceof Mob mob) || !(player instanceof ServerPlayer)) {
            player.displayClientMessage(Component.translatable("message.neurolab.attach.mob_required"), true);
            return InteractionResult.CONSUME;
        }
        if (BrainAttachmentService.isAttached(mob)) {
            player.displayClientMessage(Component.translatable("message.neurolab.attach.already", mob.getName()), true);
            return InteractionResult.CONSUME;
        }
        String failure = BrainAttachmentService.attachFailure(mob);
        if (failure != null || !BrainAttachmentService.attach(mob)) {
            player.displayClientMessage(Component.literal(failure == null
                    ? "Could not attach the connectome to this mob." : failure), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.neurolab.attach.success", mob.getName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        return applyTo(stack, player, target);
    }
}
