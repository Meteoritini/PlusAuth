package ch.meteoritini.plusauth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static ch.meteoritini.plusauth.Plusauth.MOD_ID;
import static ch.meteoritini.plusauth.Plusauth.log;

public class Config {
    public static int MAX_IP_ACCS = 5, MAX_LOGIN_TIME = 60;
    public static boolean LIMIT_ACC_TO_IP = false, KICK_ON_WRONG_PASSWORD = false;

    public static void init() {
        new ConfigProperty("max-accs-per-ip", () -> Integer.toString(MAX_IP_ACCS), s -> MAX_IP_ACCS = Integer.parseInt(s)).register();
        new ConfigProperty("max-login-time", () -> Integer.toString(MAX_LOGIN_TIME), s -> MAX_LOGIN_TIME = Integer.parseInt(s)).register();
        new ConfigProperty("lock-acc-ip", () -> Boolean.toString(LIMIT_ACC_TO_IP), s -> LIMIT_ACC_TO_IP = Boolean.parseBoolean(s)).register();
        new ConfigProperty("kick-failed-login", () -> Boolean.toString(KICK_ON_WRONG_PASSWORD), s -> KICK_ON_WRONG_PASSWORD = Boolean.parseBoolean(s)).register();
        initCmd();
    }

    private static final String filename = MOD_ID + ".properties";
    private record ConfigProperty(String key, Supplier<String> get, Consumer<String> set) {
        private void register() {
            properties.add(this);
        }
    }
    private static final ArrayList<ConfigProperty> properties = new ArrayList<>();

    private static void parseConfigEntry(String entry) {
        if( !entry.isEmpty() && !entry.startsWith( "#" ) ) {
            String[] pair = entry.split("=", 2);
            if(pair.length == 2) {
                for(ConfigProperty property : properties) {
                    if(property.key().equals(pair[0])) {
                        property.set().accept(pair[1]);
                        return;
                    }
                }
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public static void write() throws IOException {
        File f = FabricLoader.getInstance().getConfigDir().resolve(filename).toFile();
        if(!f.exists()) {
            f.getParentFile().mkdirs();
            f.createNewFile();
        }
        StringBuilder sb = new StringBuilder();
        for(ConfigProperty p : properties) {
            if(!sb.isEmpty()) sb.append('\n');
            sb.append(p.key()).append('=').append(p.get().get());
        }
        PrintWriter writer = new PrintWriter(f, StandardCharsets.UTF_8);
        writer.write(sb.toString());
        writer.close();
    }

    public static void load() throws IOException {
        File f = FabricLoader.getInstance().getConfigDir().resolve(filename).toFile();
        if(!f.exists()) {
            f.getParentFile().mkdirs();
            f.createNewFile();
        }
        Scanner reader = new Scanner(f);
        try {
            while (reader.hasNextLine()) {
                parseConfigEntry(reader.nextLine());
            }
        } catch (Exception e) {
            log("Error reading config: " + e, 2);
        }
        reader.close();
        write();
    }

    private static String configCommand(String key, String val) {
        for(ConfigProperty p : properties) {
            if(p.key().equals(key)) {
                if(val == null) return p.get().get();
                p.set().accept(val);
                try {
                    write();
                } catch (IOException e) {
                    return null;
                }
                return "";
            }
        }
        return null;
    }

    private static int cmdSet(CommandContext<ServerCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        String val = StringArgumentType.getString(ctx, "value");
        if(configCommand(key, val) == null) {
            ctx.getSource().sendError(Text.literal("Key not found"));
            return -1;
        } else {
            ctx.getSource().sendFeedback(() -> Text.literal(MOD_ID + ": Config updated"), true);
        }
        return 1;
    }

    private static int cmdGet(CommandContext<ServerCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        String val = configCommand(key, null);
        if(val == null) {
            ctx.getSource().sendError(Text.literal("Key not found"));
            return -1;
        } else {
            ctx.getSource().sendFeedback(() -> Text.literal("Config property " + key + " has value " + val), false);
        }
        return 0;
    }

    public static void initCmd() {
        CommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
            commandDispatcher.register(CommandManager.literal(MOD_ID + ":config").requires(s -> s.hasPermissionLevel(4)).then(CommandManager.argument("key", StringArgumentType.string()).suggests(
                    (context, builder) -> {
                        for(ConfigProperty p : properties) {
                            if(CommandSource.shouldSuggest(builder.getRemaining(), p.key())) builder.suggest(p.key());
                        }
                        return builder.buildFuture();
                    }
            ).executes(Config::cmdGet).then(CommandManager.argument("value", StringArgumentType.string()).executes(Config::cmdSet))));
        });
    }
}

