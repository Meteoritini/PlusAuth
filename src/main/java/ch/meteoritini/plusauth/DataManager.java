package ch.meteoritini.plusauth;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DataManager extends PersistentState {
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound pass = new NbtCompound();
        for(UUID key : map.keySet()) {
            PasswordData data = map.get(key);
            NbtCompound innerPass = new NbtCompound();
            innerPass.putString("hash", data.hash);
            innerPass.putString("salt", data.salt);
            innerPass.putString("name", data.name);
            innerPass.putString("ip", data.ip == null?"":data.ip);
            pass.put(key.toString(), innerPass);
        }
        nbt.put("passwords", pass);
        return nbt;
    }

    public record PasswordData(String hash, String salt, String ip, String name) {}

    public Map<UUID, PasswordData> map = new HashMap<>();

    public static DataManager createFromNbt(NbtCompound tag) {
        DataManager state = new DataManager();
        NbtCompound pass = tag.getCompound("passwords");
        for(String key : pass.getKeys()) {
            NbtCompound inner = pass.getCompound(key);
            state.map.put(UUID.fromString(key), new PasswordData(inner.getString("hash"), inner.getString("salt"), inner.getString("ip"), inner.getString("name")));
        }
        return state;
    }

    public static DataManager getState(MinecraftServer server) {
        PersistentStateManager persistentStateManager = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager();
        DataManager state = persistentStateManager.getOrCreate(DataManager::createFromNbt, DataManager::new, Plusauth.MOD_ID);
        state.markDirty();
        return state;
    }
}
