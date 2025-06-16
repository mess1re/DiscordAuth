package me.mss1r.DiscordAuth.mixin;

import me.mss1r.DiscordAuth.auth.LimboManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;

@Mixin(PlayerList.class)
public class MixinPlayerList {
    @Inject(method = "broadcastAll*", at = @At("HEAD"))
    private void hideUnauthorizedFromTab(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        // Для каждого игрока — если не авторизован, убираем из списка (пример)
        Iterator<ClientboundPlayerInfoUpdatePacket.Entry> it = packet.entries().iterator();
        while (it.hasNext()) {
            ClientboundPlayerInfoUpdatePacket.Entry entry = it.next();
            ServerPlayer player = ((PlayerList)(Object)this).getPlayer(entry.profileId());
            if (player != null && LimboManager.isInLimbo(player.getUUID())) {
                it.remove();
            }
        }
    }
}
