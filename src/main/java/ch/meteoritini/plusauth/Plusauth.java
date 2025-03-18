package ch.meteoritini.plusauth;

import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Plusauth implements ModInitializer {
    public static void log(String s, int level) {
        switch (level) {
            case 0 -> logger.info(s);
            case 1 -> logger.warn(s);
            case 2 -> logger.error(s);
        }
    }
    public static void log(String s) {
        log(s, 0);
    }

    public record PlayerData(Vec3d pos, Vec3d v, List<ItemStack> inv, GameMode gm) {}
    public static final Map<UUID, PlayerData> tempData = new HashMap<>();
    public static final List<UUID> verified = new ArrayList<>();
    public static final List<UUID> savable = new ArrayList<>();
    public static final Map<ServerPlayerEntity, Long> unverified = new HashMap<>();
    public static final String MOD_ID = "plusauth";
    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(new CommandHandler());
        EventHandler handler = new EventHandler();
        ServerPlayConnectionEvents.INIT.register(handler);
        ServerPlayConnectionEvents.DISCONNECT.register(handler);
        ServerLifecycleEvents.SERVER_STOPPING.register(handler);
        ServerTickEvents.END_SERVER_TICK.register(handler);
        Config.init();
        try {
            Config.load();
        } catch (IOException e) {
            log("Error loading config", 2);
        }
    }

    public static void resetInventory(ServerPlayerEntity player, PlayerData dat) {
        for(int i = 0; i < dat.inv().size(); i++) {
            player.getInventory().setStack(i, dat.inv().get(i));
        }
        unverified.remove(player);
        player.changeGameMode(dat.gm());
    }

    public static void verify(ServerPlayerEntity player) {
        PlayerData dat = tempData.remove(player.getUuid());
        if(!verified.contains(player.getUuid())) verified.add(player.getUuid());
        if(!savable.contains(player.getUuid())) savable.add(player.getUuid());
        player.sendMessage(Text.empty(), true);
        player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
        resetInventory(player, dat);
        player.requestTeleport(dat.pos().x, dat.pos().y, dat.pos().z);
        player.setVelocity(dat.v());
    }

    public static UUID offlineUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
    public static UUID onlineUUID(String name) {
        try {
            URL urlUUID = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            String sID = JsonParser.parseReader(new InputStreamReader(urlUUID.openConnection().getInputStream())).getAsJsonObject().get("id").getAsString();
            BigInteger bi1 = new BigInteger(sID.substring(0, 16), 16);
            BigInteger bi2 = new BigInteger(sID.substring(16, 32), 16);
            return new UUID(bi1.longValue(), bi2.longValue());
        } catch (IllegalArgumentException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
