package org.example.tp.tpPlus.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandHome {

    private static final File HOME_FILE = new File("home_locations.json");
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Map<String, HomeLocation>> homeData = new HashMap<>();

    public static void register() {
        loadHomes();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("sethome")
                .then(CommandManager.argument("name", StringArgumentType.word())
                .executes(CommandHome::executeSetHome)));

            dispatcher.register(CommandManager.literal("home")
                .then(CommandManager.argument("name", StringArgumentType.word())
                .suggests(suggestHomes())
                .executes(CommandHome::executeHome)));

            dispatcher.register(CommandManager.literal("delhome")
                .then(CommandManager.argument("name", StringArgumentType.word())
                .suggests(suggestHomes())
                .executes(CommandHome::executeDelHome)));

            dispatcher.register(CommandManager.literal("homelist")
                .executes(CommandHome::executeHomeList));
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            server.getPlayerManager().getPlayerList().forEach(CommandHome::checkAndUpdatePlayerData);
        });
    }

    private static void loadHomes() {
        if (HOME_FILE.exists()) {
            try (FileReader reader = new FileReader(HOME_FILE)) {
                Map<String, Map<String, HomeLocation>> data = GSON.fromJson(reader, new TypeToken<Map<String, Map<String, HomeLocation>>>(){}.getType());
                if (data != null) {
                    data.forEach((uuid, homes) -> homeData.put(UUID.fromString(uuid), homes));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveHomes() {
        try (FileWriter writer = new FileWriter(HOME_FILE)) {
            Map<String, Map<String, HomeLocation>> data = new HashMap<>();
            homeData.forEach((uuid, homes) -> data.put(uuid.toString(), homes));
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static SuggestionProvider<ServerCommandSource> suggestHomes() {
        return (context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            UUID playerUuid = player.getUuid();

            Map<String, HomeLocation> homes = homeData.getOrDefault(playerUuid, new HashMap<>());
            for (String home : homes.keySet()) {
                builder.suggest(home);
            }

            return builder.buildFuture();
        };
    }

    private static int executeSetHome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        BlockPos playerPos = player.getBlockPos();
        Identifier dimensionId = player.getWorld().getRegistryKey().getValue();
        String homeName = StringArgumentType.getString(context, "name");

        homeData.computeIfAbsent(player.getUuid(), k -> new HashMap<>())
                .put(homeName, new HomeLocation(playerPos, dimensionId.toString()));
        saveHomes();

        source.sendFeedback(() -> Text.literal("家 '" + homeName + "' 已设置~"), false);
        return 1;
    }

    private static int executeHome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        UUID playerUuid = player.getUuid();
        String homeName = StringArgumentType.getString(context, "name");

        Map<String, HomeLocation> homes = homeData.get(playerUuid);
        if (homes != null && homes.containsKey(homeName)) {
            HomeLocation home = homes.get(homeName);
            BlockPos pos = home.getPosition();
            String dimension = home.getDimension();
            RegistryKey<World> worldKey = RegistryKey.of(RegistryKey.ofRegistry(new Identifier("minecraft", "dimension")), Identifier.tryParse(dimension));
            ServerWorld world = player.getServer().getWorld(worldKey);
            if (world != null) {
                player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), player.getYaw(), player.getPitch());
                source.sendFeedback(() -> Text.literal("已传送到家 '" + homeName + "'！"), false);
            } else {
                source.sendError(Text.literal("无法找到家的维度捏~"));
            }
        } else {
            source.sendError(Text.literal("没有找到名为 '" + homeName + "' 的家捏~"));
        }

        return 1;
    }

    private static int executeDelHome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        UUID playerUuid = player.getUuid();
        String homeName = StringArgumentType.getString(context, "name");

        Map<String, HomeLocation> homes = homeData.get(playerUuid);
        if (homes != null && homes.remove(homeName) != null) {
            saveHomes();
            source.sendFeedback(() -> Text.literal("成功删除家 '" + homeName + "'！"), false);
        } else {
            source.sendError(Text.literal("没有找到名为 '" + homeName + "' 的家捏~"));
        }

        return 1;
    }

    private static int executeHomeList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        UUID playerUuid = player.getUuid();

        Map<String, HomeLocation> homes = homeData.get(playerUuid);
        if (homes != null && !homes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("你的家:"), false);
            homes.forEach((name, location) -> {
                BlockPos pos = location.getPosition();
                String dimension = location.getDimension();
                Text homeText = Text.literal(String.format("家 '%s': 坐标 (x: %d, y: %d, z: %d), 维度: %s", name, pos.getX(), pos.getY(), pos.getZ(), dimension))
                    .styled(style -> style.withColor(Formatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, String.format("'%s': 坐标 (x: %d, y: %d, z: %d), 维度: %s", name, pos.getX(), pos.getY(), pos.getZ(), dimension)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点我发送坐标到聊天框"))));
                source.sendFeedback(() -> homeText, false);
            });
        } else {
            source.sendError(Text.literal("你还没有设置任何家捏~"));
        }

        return 1;
    }

    private static void checkAndUpdatePlayerData(ServerPlayerEntity player) {
    }

    private static class HomeLocation {
        private final BlockPos position;
        private final String dimension;

        public HomeLocation(BlockPos position, String dimension) {
            this.position = position;
            this.dimension = dimension;
        }

        public BlockPos getPosition() {
            return position;
        }

        public String getDimension() {
            return dimension;
        }
    }
} 