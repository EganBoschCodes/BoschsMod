package net.fabricmc.bosch.generation;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.bosch.BoschMain;
import net.fabricmc.bosch.parsing.BlockPalatte;
import net.fabricmc.bosch.selection.PlacementHandler;
import net.minecraft.block.BlockState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PaintBucket {
    public static int fill(CommandContext<ServerCommandSource> context) {

        BlockPalatte blockType = new BlockPalatte(StringArgumentType.getString(context, "block").split(",")[0]);
        float radius = FloatArgumentType.getFloat(context, "iterations");

        final ServerCommandSource source = context.getSource();
        BlockPos initialPos = BoschMain.LOCK.containsKey(source.getPlayer()) ? BoschMain.LOCK.get(source.getPlayer()) : new BlockPos(source.getPosition());

        Map<BlockPos, Boolean> history = new HashMap<>();
        LinkedList<BlockPos> queue = new LinkedList<>();
        history.put(initialPos, true);
        queue.add(initialPos);

        ServerWorld world = source.getWorld();
        PlacementHandler ph = new PlacementHandler(world);
        BlockState initialState = world.getBlockState(initialPos);

        int iterations = 0;
        while (iterations < radius && !queue.isEmpty()) {
            int l = queue.size();
            for(int i = 0; i < l; i++) {
                BlockPos currentPos = queue.removeFirst();

                if (world.getBlockState(currentPos) != initialState) { history.put(currentPos, false); continue; }

                ph.placeBlock(currentPos, blockType.getBlock());

                addToSearch(currentPos.add(1, 0, 0), history, queue);
                addToSearch(currentPos.add(-1, 0, 0), history, queue);
                addToSearch(currentPos.add(0, 1, 0), history, queue);
                addToSearch(currentPos.add(0, -1, 0), history, queue);
                addToSearch(currentPos.add(0, 0, 1), history, queue);
                addToSearch(currentPos.add(0, 0, -1), history, queue);
            }

            iterations++;
        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, PaintBucket::fill));

        return 1;
    }

    private static void addToSearch(BlockPos b, Map<BlockPos, Boolean> history, LinkedList<BlockPos> queue) {
        if(history.containsKey(b)) return;

        queue.add(b);
        history.put(b, true);
    }

}
