package ch.meteoritini.plusauth.mixins;

import ch.meteoritini.plusauth.Plusauth;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(value = ServerPlayNetworkHandler.class, priority = 0)
public class PacketMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onSpectatorTeleport", at = @At("HEAD"), cancellable = true)
    protected void injectSpectatorTeleport(SpectatorTeleportC2SPacket packet, CallbackInfo ci) {
        if (!Plusauth.verified.contains(player.getUuid())) {
            ci.cancel();
        }
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    protected void injectChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (!Plusauth.verified.contains(player.getUuid())) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    protected void injectPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (!Plusauth.verified.contains(player.getUuid())) {
            this.player.networkHandler.sendPacket(new PlayerPositionLookS2CPacket(0, 512, 0, player.getYaw(), player.getPitch(), Collections.emptySet(), 0));
            ci.cancel();

        }
    }
    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    protected void injectCommandExecution(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (!Plusauth.verified.contains(player.getUuid())) {
            String cmd = packet.command().split(" ")[0];
            if(cmd.equals("login") || cmd.equals("register")) return;
            ci.cancel();
        }
    }
    /*@Inject(method = "onChatCommandSigned", at = @At("HEAD"), cancellable = true)
    protected void injectChatCommandSigned(ChatCommandSignedC2SPacket packet, CallbackInfo ci) {
        if (!Plusauth.verified.contains(player.getUuid())) {
            ci.cancel();
        }
    }*/
    @Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
    protected void injectPlayerInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        if (!Plusauth.verified.contains(player.getUuid())) {
            ci.cancel();
        }
    }
    @Inject(method = "onClientCommand", at = @At("HEAD"), cancellable = true)
    protected void injectClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        if (!Plusauth.verified.contains(player.getUuid())) {
            ci.cancel();
        }
    }
}
