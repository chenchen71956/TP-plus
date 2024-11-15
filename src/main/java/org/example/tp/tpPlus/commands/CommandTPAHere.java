package org.example.tp.tpPlus.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandTPAHere {

    private static final Map<UUID, TeleportRequest> teleportRequests = new HashMap<>();
    private static final Map<UUID, Integer> requestCounts = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final int REQUEST_LIMIT = 2;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("tpahere")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        Collection<ServerPlayerEntity> players = context.getSource().getServer().getPlayerManager().getPlayerList();
                        for (ServerPlayerEntity player : players) {
                            builder.suggest(player.getEntityName());
                        }
                        return builder.buildFuture();
                    })
                    .executes(CommandTPAHere::executeTpahere)));

            dispatcher.register(CommandManager.literal("tpaccept")
                .then(CommandManager.literal("tpahere")
                .executes(context -> executeTpAccept(context, "tpahere"))));

            dispatcher.register(CommandManager.literal("tpdeny")
                .then(CommandManager.literal("tpahere")
                .executes(context -> executeTpDeny(context, "tpahere"))));
        });
    }

    private static int executeTpahere(CommandContext<ServerCommandSource> context) {
        String playerName = StringArgumentType.getString(context, "player");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity requester = source.getPlayer();
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);

        if (targetPlayer != null) {
            UUID targetUuid = targetPlayer.getUuid();
            BlockPos requesterPos = requester.getBlockPos();
            ServerWorld requesterWorld = (ServerWorld) requester.getWorld();

            int count = requestCounts.getOrDefault(requester.getUuid(), 0);
            if (count >= REQUEST_LIMIT) {
                source.sendError(Text.literal("不要再请求了啦！！夏夏在后台敲tp也很累的！！"));
                return 1;
            }

            requestCounts.put(requester.getUuid(), count + 1);
            teleportRequests.put(targetUuid, new TeleportRequest(requester.getUuid(), requesterWorld, requesterPos));

            System.out.println("TPAHere请求已发送: " + requester.getEntityName() + " -> " + targetPlayer.getEntityName());

            Text acceptText = Text.literal("[接受]").styled(style -> style
                .withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept tpahere")));

            Text denyText = Text.literal("[拒绝]").styled(style -> style
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny tpahere")));

            targetPlayer.sendMessage(Text.literal(requester.getEntityName() + " 想让你传送到他的位置")
                .append(acceptText)
                .append(" ")
                .append(denyText), false);

            source.sendFeedback(() -> Text.literal("传送请求已发送给 " + playerName), false);

            scheduler.schedule(() -> {
                if (teleportRequests.remove(targetUuid) != null) {
                    targetPlayer.sendMessage(Text.literal("来自" + requester.getEntityName() + "的去他那请求已过期"), false);
                    requester.sendMessage(Text.literal("你的传送请求已过期，试着等会再发送吧~"), false);
                    System.out.println("TPAHere请求已过期: " + requester.getEntityName() + " -> " + targetPlayer.getEntityName());
                }
                requestCounts.put(requester.getUuid(), requestCounts.get(requester.getUuid()) - 1);
            }, 90, TimeUnit.SECONDS);
        } else {
            source.sendError(Text.literal("未找到玩家: " + playerName));
        }

        return 1;
    }

    private static int executeTpAccept(CommandContext<ServerCommandSource> context, String type) {
        if (!"tpahere".equals(type)) {
            context.getSource().sendError(Text.literal("没有找到传送请求捏~"));
            return 1;
        }
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = source.getPlayer();
        UUID targetUuid = targetPlayer.getUuid();

        TeleportRequest request = teleportRequests.remove(targetUuid);
        if (request != null) {
            ServerPlayerEntity requester = source.getServer().getPlayerManager().getPlayer(request.getRequesterUuid());
            if (requester != null) {
                targetPlayer.teleport(request.getWorld(), request.getPosition().getX(), request.getPosition().getY(), request.getPosition().getZ(), targetPlayer.getYaw(), targetPlayer.getPitch());
                targetPlayer.sendMessage(Text.literal("已传送到 " + requester.getEntityName()), false);
                requester.sendMessage(Text.literal("你已接受来自 " + targetPlayer.getEntityName() + " 的传送请求"), false);
                System.out.println("TPAHere请求已接受: " + requester.getEntityName() + " -> " + targetPlayer.getEntityName());
            } else {
                source.sendError(Text.literal("请求者不在线喽~"));
            }
        } else {
            source.sendError(Text.literal("没有找到传送请求捏~"));
            System.out.println("TPAHere请求未找到: " + targetPlayer.getEntityName());
        }

        return 1;
    }

    private static int executeTpDeny(CommandContext<ServerCommandSource> context, String type) {
        if (!"tpahere".equals(type)) {
            context.getSource().sendError(Text.literal("没有找到传送请求捏~"));
            return 1;
        }
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = source.getPlayer();
        UUID targetUuid = targetPlayer.getUuid();

        TeleportRequest request = teleportRequests.remove(targetUuid);
        if (request != null) {
            ServerPlayerEntity requester = source.getServer().getPlayerManager().getPlayer(request.getRequesterUuid());
            if (requester != null) {
                requester.sendMessage(Text.literal(targetPlayer.getEntityName() + " 拒绝了你的传送请求"), false);
            }
            targetPlayer.sendMessage(Text.literal("你已拒绝传送请求"), false);
        } else {
            source.sendError(Text.literal("没有找到传送请求捏~"));
        }

        return 1;
    }

    private static class TeleportRequest {
        private final UUID requesterUuid;
        private final ServerWorld world;
        private final BlockPos position;

        public TeleportRequest(UUID requesterUuid, ServerWorld world, BlockPos position) {
            this.requesterUuid = requesterUuid;
            this.world = world;
            this.position = position;
        }

        public UUID getRequesterUuid() {
            return requesterUuid;
        }

        public ServerWorld getWorld() {
            return world;
        }

        public BlockPos getPosition() {
            return position;
        }
    }
} 