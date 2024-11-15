package org.example.tp.tpPlus.commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandBack {

    private static final Map<UUID, PreviousLocation> previousLocations = new HashMap<>();
    private static final Map<UUID, Boolean> diedInVoid = new HashMap<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("back")
                .executes(CommandBack::executeBack));
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayerEntity player) {
                recordPreviousLocation(player);
                if (player.getBlockPos().getY() < player.getWorld().getBottomY()) {
                    diedInVoid.put(player.getUuid(), true);
                }
            }
        });
    }

    public static void recordPreviousLocation(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        ServerWorld world = (ServerWorld) player.getWorld();

        if (pos.getY() < world.getBottomY()) {
            pos = findSafePosition(world, pos);
        }

        previousLocations.put(player.getUuid(), new PreviousLocation(world, pos));
    }

    private static BlockPos findSafePosition(ServerWorld world, BlockPos pos) {
        int radius = 100;
        for (int y = pos.getY(); y < world.getTopY(); y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = new BlockPos(pos.getX() + x, y, pos.getZ() + z);
                    if (!world.getBlockState(checkPos).isAir()) {
                        return checkPos.up();
                    }
                }
            }
        }
        for (int y = pos.getY(); y > world.getBottomY(); y--) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = new BlockPos(pos.getX() + x, y, pos.getZ() + z);
                    if (!world.getBlockState(checkPos).isAir()) {
                        return checkPos.up();
                    }
                }
            }
        }
        return new BlockPos(pos.getX(), world.getBottomY(), pos.getZ());
    }

    private static int executeBack(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        UUID playerUuid = player.getUuid();

        PreviousLocation previousLocation = previousLocations.get(playerUuid);
        if (previousLocation != null) {
            player.teleport(previousLocation.getWorld(), previousLocation.getPosition().getX(), previousLocation.getPosition().getY(), previousLocation.getPosition().getZ(), player.getYaw(), player.getPitch());
            if (diedInVoid.getOrDefault(playerUuid, false)) {
                source.sendFeedback(() -> Text.literal("你死于虚空，夏夏看在了眼里把你传送到了最近的安全位置并提醒你：下次小心点~"), false);
                diedInVoid.remove(playerUuid);
            }
        } else {
            source.sendError(Text.literal("没有找到之前的位置！"));
        }

        return 1;
    }

    private static class PreviousLocation {
        private final ServerWorld world;
        private final BlockPos position;

        public PreviousLocation(ServerWorld world, BlockPos position) {
            this.world = world;
            this.position = position;
        }

        public ServerWorld getWorld() {
            return world;
        }

        public BlockPos getPosition() {
            return position;
        }
    }
} 