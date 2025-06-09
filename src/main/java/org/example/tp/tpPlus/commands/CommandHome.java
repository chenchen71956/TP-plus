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
import org.example.tp.tpPlus.storage.DatabaseManager;
import org.example.tp.tpPlus.storage.DatabaseManager.HomeLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandHome {

    public static void register() {
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
    }

    private static SuggestionProvider<ServerCommandSource> suggestHomes() {
        return (context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            UUID playerUuid = player.getUuid();

            Map<String, HomeLocation> homes = DatabaseManager.getHomes(playerUuid);
            for (String home : homes.keySet()) {
                builder.suggest(home);
            }

            return builder.buildFuture();
        };
    }

    private static int executeSetHome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = (ServerWorld) player.getWorld();
        
        // 获取玩家的精确位置
        double exactX = player.getX();
        double exactY = player.getY();
        double exactZ = player.getZ();
        
        Identifier dimensionId = world.getRegistryKey().getValue();
        String homeName = StringArgumentType.getString(context, "name");

        // 保存玩家的原始精确坐标，不进行任何调整
        DatabaseManager.saveHome(player.getUuid(), homeName, exactX, exactY, exactZ, dimensionId.toString());
        
        source.sendFeedback(() -> Text.literal("家 '" + homeName + "' 已设置~"), false);
        return 1;
    }

    private static int executeHome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        UUID playerUuid = player.getUuid();
        String homeName = StringArgumentType.getString(context, "name");

        Map<String, HomeLocation> homes = DatabaseManager.getHomes(playerUuid);
        if (homes != null && homes.containsKey(homeName)) {
            HomeLocation home = homes.get(homeName);
            double x = home.getX();
            double y = home.getY();
            double z = home.getZ();
            String dimension = home.getDimension();
            RegistryKey<World> worldKey = RegistryKey.of(RegistryKey.ofRegistry(new Identifier("minecraft", "dimension")), Identifier.tryParse(dimension));
            ServerWorld world = player.getServer().getWorld(worldKey);
            if (world != null) {
                // 记录当前位置，以便使用/back命令返回
                CommandBack.recordPreviousLocation(player);
                
                // 使用原始精确坐标传送
                player.teleport(world, x, y, z, player.getYaw(), player.getPitch());
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

        if (DatabaseManager.deleteHome(playerUuid, homeName)) {
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

        Map<String, HomeLocation> homes = DatabaseManager.getHomes(playerUuid);
        if (homes != null && !homes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("你的家:"), false);
            homes.forEach((name, location) -> {
                double x = location.getX();
                double y = location.getY();
                double z = location.getZ();
                String dimension = location.getDimension();
                Text homeText = Text.literal(String.format("家 '%s': 坐标 (x: %.2f, y: %.2f, z: %.2f), 维度: %s", 
                        name, x, y, z, dimension))
                    .styled(style -> style.withColor(Formatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                            String.format("'%s': 坐标 (x: %.2f, y: %.2f, z: %.2f), 维度: %s", name, x, y, z, dimension)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点我发送坐标到聊天框"))));
                source.sendFeedback(() -> homeText, false);
            });
        } else {
            source.sendError(Text.literal("你还没有设置任何家捏~"));
        }

        return 1;
    }
} 