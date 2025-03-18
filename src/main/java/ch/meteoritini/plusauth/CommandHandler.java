package ch.meteoritini.plusauth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class CommandHandler implements CommandRegistrationCallback {
    private static int putPassword(MinecraftServer server, UUID id, String password, String ip, String name, boolean force) {
        DataManager data = DataManager.getState(server);
        DataManager.PasswordData old = data.map.get(id);
        if(old != null && !force) return -1;

        if(ip != null && !force && Config.MAX_IP_ACCS != 0) {
            int found = 0;
            for(DataManager.PasswordData dat : data.map.values()) {
                if(dat.ip() != null && !dat.ip().isEmpty() && dat.ip().equals(ip)) {
                    found++;
                    if(found >= Config.MAX_IP_ACCS) return 3;
                }
            }
        }

        String oldIp = old == null?null:old.ip();
        try {
            String salt = String.valueOf(new Random().nextLong());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = Arrays.toString(digest.digest((password + salt).getBytes(StandardCharsets.UTF_8)));
            data.map.put(id, new DataManager.PasswordData(hash, salt, ip == null?oldIp:ip, name));
        } catch (NoSuchAlgorithmException e) {
            return 2;
        }
        return old == null?0:1;
    }
    private static int checkPassword(MinecraftServer server, UUID id, String password, String ip) {
        DataManager data = DataManager.getState(server);
        DataManager.PasswordData pwd = data.map.get(id);
        if(pwd == null) return -1;
        try {
            if(pwd.ip() == null || pwd.ip().isEmpty()) {
                data.map.put(id, new DataManager.PasswordData(pwd.hash(), pwd.salt(), ip, pwd.name()));
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = Arrays.toString(digest.digest((password + pwd.salt()).getBytes(StandardCharsets.UTF_8)));
            return hash.equals(pwd.hash())?0:1;
        } catch (NoSuchAlgorithmException e) {
            return 2;
        }
    }

    private int registerCommand(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if(player == null) {
            context.getSource().sendError(Text.literal("Only usable by players."));
            return -1;
        }
        String pw = StringArgumentType.getString(context, "password");
        String conf = StringArgumentType.getString(context, "confirm");
        if(!pw.equals(conf)) {
            context.getSource().sendError(Text.literal("Passwords do not match!"));
            return -1;
        }
        UUID id = player.getUuid();
        int res = putPassword(context.getSource().getServer(), id, pw, player.getIp(), player.getGameProfile().getName(), false);
        if(res == -1) {
            context.getSource().sendError(Text.literal("Account already exists!"));
            return -1;
        }
        if(res == 2) {
            context.getSource().sendError(Text.literal("An error occurred"));
            return -1;
        }
        if(res == 3) {
            context.getSource().sendError(Text.literal("Account limit reached"));
            return -1;
        }
        Plusauth.verify(context.getSource().getPlayer());
        context.getSource().sendFeedback(() -> Text.literal("Account created"), true);
        return 1;
    }
    private int loginCommand(CommandContext<ServerCommandSource> context) {
        if(context.getSource().getPlayer() == null) {
            context.getSource().sendError(Text.literal("Only usable by players."));
            return -1;
        }
        String pw = StringArgumentType.getString(context, "password");
        UUID id = context.getSource().getPlayer().getUuid();
        if(Plusauth.verified.contains(id)) {
            context.getSource().sendError(Text.literal("Already logged in"));
            return -1;
        }
        int res = checkPassword(context.getSource().getServer(), id, pw, context.getSource().getPlayer().getIp());
        if(res == -1) {
            context.getSource().sendError(Text.literal("Account doesn't exist"));
            return -1;
        }
        if(res == 2) {
            context.getSource().sendError(Text.literal("An error occurred"));
            return -1;
        }
        if(res == 1) {
            if(Config.KICK_ON_WRONG_PASSWORD) {
                context.getSource().getPlayer().networkHandler.disconnect(Text.literal("Wrong password").formatted(Formatting.RED));
                return -1;
            }
            context.getSource().sendError(Text.literal("Wrong password"));
            return -1;
        }
        Plusauth.verify(context.getSource().getPlayer());
        context.getSource().sendFeedback(() -> Text.literal("Login successful"), true);
        return 0;
    }

    private int changepw(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if(player == null) {
            context.getSource().sendError(Text.literal("Only usable by players."));
            return -1;
        }
        String oldPW = StringArgumentType.getString(context, "old");
        String newPW = StringArgumentType.getString(context, "new");
        if(!newPW.equals(StringArgumentType.getString(context, "confirm"))) {
            context.getSource().sendError(Text.literal("Passwords do not match!"));
            return -1;
        }
        MinecraftServer server = context.getSource().getServer();
        UUID id = context.getSource().getPlayer().getUuid();
        int res = checkPassword(server, id, oldPW, null);
        if(res == 2) {
            context.getSource().sendError(Text.literal("An error occurred"));
            return -1;
        }
        if(res != 0) {
            context.getSource().sendError(Text.literal("Wrong password"));
            return -1;
        }

        res = putPassword(server, id, newPW, null, player.getGameProfile().getName(), true);
        if(res == 2) {
            context.getSource().sendError(Text.literal("An error occurred"));
            return -1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Password updated"), true);
        return 1;
    }


    private int lsacc(CommandContext<ServerCommandSource> context) {
        DataManager data = DataManager.getState(context.getSource().getServer());
        StringBuilder message = new StringBuilder("Registered accounts:");
        for(DataManager.PasswordData dat : data.map.values()) {
            String ip = dat.ip();
            if(ip == null || ip.isEmpty()) ip = " Unknown";
            message.append('\n').append(dat.name()).append("    IP: ").append(ip);
        }
        context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
        return 0;
    }
    private int setip(CommandContext<ServerCommandSource> context) {
        String user = StringArgumentType.getString(context, "user");
        UUID id = null;
        DataManager data = DataManager.getState(context.getSource().getServer());
        try {
            id = UUID.fromString(user);
        } catch (Exception e) {
            for(UUID uid : data.map.keySet()) {
                if(data.map.get(uid).name().equals(user)) {
                    id = uid;
                    break;
                }
            }
        }
        DataManager.PasswordData dat = data.map.get(id);
        if(dat == null) {
            context.getSource().sendError(Text.literal("Account does not exist"));
            return -1;
        }
        data.map.put(id, new DataManager.PasswordData(dat.hash(), dat.salt(), StringArgumentType.getString(context, "ip"), dat.name()));
        UUID finalId = id;
        context.getSource().sendFeedback(() -> Text.literal("Updated IP for " + finalId), true);
        return 1;
    }
    private int delacc(CommandContext<ServerCommandSource> context) {
        String user = StringArgumentType.getString(context, "user");
        UUID id = null;
        DataManager data = DataManager.getState(context.getSource().getServer());
        try {
            id = UUID.fromString(user);
        } catch (Exception e) {
            for(UUID uid : data.map.keySet()) {
                if(data.map.get(uid).name().equals(user)) {
                    id = uid;
                    break;
                }
            }
        }

        if(data.map.remove(id) == null) {
            context.getSource().sendError(Text.literal("Account does not exist"));
            return -1;
        }
        if(Plusauth.verified.contains(id)) {
            ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(id);
            if(player != null) player.networkHandler.disconnect(Text.literal("Account deleted").formatted(Formatting.RED));
        }
        UUID finalId = id;
        context.getSource().sendFeedback(() -> Text.literal("Account " + finalId + " deleted"), true);
        return 1;
    }
    private int addacc(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "username");
        UUID id;
        if(context.getSource().getServer().isOnlineMode()) {
            id = Plusauth.onlineUUID(name);
        } else {
            id = Plusauth.offlineUUID(name);
        }
        if(id == null) {
            context.getSource().sendError(Text.literal("Unable to get UUID"));
            return -1;
        }
        String pw = StringArgumentType.getString(context, "password");
        int res = putPassword(context.getSource().getServer(), id, pw, null, name, false);
        if(res == -1) {
            context.getSource().sendError(Text.literal("Account already exists"));
            return -1;
        }
        if(res == 2) {
            context.getSource().sendError(Text.literal("An error occurred"));
            return -1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Account " + id + " (" + name + ") created"), true);
        return 1;
    }

    private int manverify(CommandContext<ServerCommandSource> context) {
        String user = StringArgumentType.getString(context, "user");
        UUID id = null;
        try {
            id = UUID.fromString(user);
        } catch (Exception ignored) {}
        ServerPlayerEntity player = id == null?context.getSource().getServer().getPlayerManager().getPlayer(user):context.getSource().getServer().getPlayerManager().getPlayer(id);
        if(player != null) {
            Plusauth.verify(player);
            context.getSource().sendFeedback(() -> player.getName().copy().append(" manually verified."), true);
            return 1;
        }
        context.getSource().sendError(Text.literal("Player not found"));
        return -1;
    }

    @Override
    public void register(CommandDispatcher<ServerCommandSource> commandDispatcher, CommandRegistryAccess commandRegistryAccess, CommandManager.RegistrationEnvironment registrationEnvironment) {
        commandDispatcher.register(literal("register").then(argument("password", StringArgumentType.string()).then(argument("confirm", StringArgumentType.string()).executes(this::registerCommand))));
        commandDispatcher.register(literal("login").then(argument("password", StringArgumentType.string()).executes(this::loginCommand)));
        commandDispatcher.register(literal("changepassword").then(argument("old", StringArgumentType.string()).then(argument("new", StringArgumentType.string()).then(argument("confirm", StringArgumentType.string()).executes(this::changepw)))));
        commandDispatcher.register(literal("plusauth").requires(src -> src.hasPermissionLevel(3))
                .then(literal("accounts").executes(this::lsacc))
                .then(literal("setip").then(argument("user", StringArgumentType.string()).then(argument("ip", StringArgumentType.string()).executes(this::setip))))
                .then(literal("create").then(argument("username", StringArgumentType.string()).then(argument("password", StringArgumentType.string()).executes(this::addacc))))
                .then(literal("delete").then(argument("user", StringArgumentType.string()).executes(this::delacc)))
                .then(literal("verify").then(argument("user", StringArgumentType.string()).executes(this::manverify)))
        );
    }
}
