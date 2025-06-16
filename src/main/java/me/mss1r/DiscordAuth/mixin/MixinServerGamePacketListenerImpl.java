package me.mss1r.DiscordAuth.mixin;

import me.mss1r.DiscordAuth.auth.LimboManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {

    // Движение
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void blockMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl)(Object)this).player;
        if (LimboManager.isInLimbo(player.getUUID())) {
            ci.cancel();
        }
    }

    // Использование предметов
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void blockUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl)(Object)this).player;
        if (LimboManager.isInLimbo(player.getUUID())) {
            ci.cancel();
        }
    }

    // Взаимодействие с блоком
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void blockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl)(Object)this).player;
        if (LimboManager.isInLimbo(player.getUUID())) {
            ci.cancel();
        }
    }

    // Чат
    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void blockChat(ServerboundChatPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl)(Object)this).player;
        if (LimboManager.isInLimbo(player.getUUID())) {
            ci.cancel();
        }
    }

    // Дроп предмета
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void blockDrop(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl)(Object)this).player;
        if (LimboManager.isInLimbo(player.getUUID())) {
            // Если это дроп, отменяем
            if (packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
                    || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
                ci.cancel();
            }
        }
    }
}
