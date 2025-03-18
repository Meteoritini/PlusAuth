package ch.meteoritini.plusauth.mixins;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class LoginMixin {
    @Shadow @Nullable private GameProfile profile;

    @Shadow @Final private MinecraftServer server;

    @Shadow
    protected abstract GameProfile toOfflineProfile(GameProfile profile);

    @Shadow public abstract void disconnect(Text reason);

    @Inject(method = "acceptPlayer", at = @At("HEAD"), cancellable = true)
    protected void injectLogin(CallbackInfo ci) {
        assert profile != null;
        if(server.getPlayerManager().getPlayer(profile.getName()) != null) {
            disconnect(Text.literal("Already logged in").formatted(Formatting.RED));
            ci.cancel();
        }
    }
}
