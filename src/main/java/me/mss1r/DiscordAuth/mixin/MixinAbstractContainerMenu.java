package me.mss1r.DiscordAuth.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Блокирует взаимодействие с инвентарём, если игрок в limbo
@Mixin(AbstractContainerMenu.class)
public class MixinAbstractContainerMenu {
    @Inject(
            method = "doClick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void discordauth$blockInventory(int slotId, int dragType, ClickType clickType, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (me.mss1r.DiscordAuth.auth.LimboManager.isInLimbo(serverPlayer.getUUID())) {
                ci.cancel();
            }
        }
    }
}
