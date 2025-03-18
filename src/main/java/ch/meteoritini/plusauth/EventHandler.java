package ch.meteoritini.plusauth;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

public class EventHandler implements ServerPlayConnectionEvents.Init, ServerPlayConnectionEvents.Disconnect, ServerLifecycleEvents.ServerStopping, ServerTickEvents.EndTick {
    @Override
    public void onPlayInit(ServerPlayNetworkHandler serverPlayNetworkHandler, MinecraftServer minecraftServer) {
        ServerPlayerEntity player = serverPlayNetworkHandler.getPlayer();
        if(Config.LIMIT_ACC_TO_IP) {
            DataManager.PasswordData data = DataManager.getState(minecraftServer).map.get(serverPlayNetworkHandler.getPlayer().getUuid());
            if(data != null && data.ip() != null && !data.ip().isEmpty()) {
                if(!data.ip().equals(serverPlayNetworkHandler.getPlayer().getIp())) {
                    serverPlayNetworkHandler.disconnect(Text.literal("This account is linked to a different IP.\nContact the server administrators if you believe this is in error.").formatted(Formatting.RED));
                    return;
                }
            }
        }
        Plusauth.savable.remove(serverPlayNetworkHandler.getPlayer().getUuid());
        List<ItemStack> inv = new ArrayList<>();
        for(int i = 0; i < player.getInventory().size(); i++) {
            inv.add(player.getInventory().getStack(i));
        }
        Plusauth.PlayerData dat = new Plusauth.PlayerData(player.getPos(), player.getVelocity(), inv, player.interactionManager.getGameMode());
        Plusauth.tempData.put(player.getUuid(), dat);
        player.setPos(0, 512, 0);
        player.setVelocity(0, 0, 0);
        player.getInventory().clear();
        player.changeGameMode(GameMode.SPECTATOR);
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, Integer.MAX_VALUE, 0));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Welcome!").formatted(Formatting.GREEN, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Use /login to login or /register to create an account")));
        Plusauth.unverified.put(player, System.currentTimeMillis());
    }

    @Override
    public void onPlayDisconnect(ServerPlayNetworkHandler serverPlayNetworkHandler, MinecraftServer minecraftServer) {
        ServerPlayerEntity player = serverPlayNetworkHandler.getPlayer();
        player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
        if(Plusauth.verified.remove(player.getUuid())) return;
        Plusauth.PlayerData dat = Plusauth.tempData.remove(player.getUuid());
        if(dat == null) return;
        Plusauth.resetInventory(player, dat);
        player.setPosition(dat.pos());
        player.setVelocity(dat.v());
    }

    @Override
    public void onServerStopping(MinecraftServer minecraftServer) {
        for(ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
            onPlayDisconnect(player.networkHandler, minecraftServer);
        }
    }

    int tick = 0;

    @Override
    public void onEndTick(MinecraftServer minecraftServer) {
        tick = (tick+1)%20;
        if(tick != 0) return;
        if(Config.MAX_LOGIN_TIME == 0) return;
        ArrayList<ServerPlayerEntity> kicked = new ArrayList<>();
        for(ServerPlayerEntity player : Plusauth.unverified.keySet()) {
            long timed = System.currentTimeMillis()-Plusauth.unverified.get(player);
            if(timed > Config.MAX_LOGIN_TIME*1000L) {
                kicked.add(player);
                continue;
            }
            player.sendMessage(Text.literal((Config.MAX_LOGIN_TIME-timed/1000) + " seconds remaining").formatted(Formatting.RED), true);
        }
        for(ServerPlayerEntity kick : kicked) {
            kick.networkHandler.disconnect(Text.literal("Time expired").formatted(Formatting.RED));
        }
    }
}
