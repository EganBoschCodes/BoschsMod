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

import java.util.ArrayList;
import java.util.LinkedList;

public class PaintBucket {
    public static int fill(CommandContext<ServerCommandSource> context) {

        BlockPalatte blockType = new BlockPalatte(StringArgumentType.getString(context, "block").split(",")[0]);
        float radius = FloatArgumentType.getFloat(context, "radius");


        final ServerCommandSource source = context.getSource();
        Vec3d sourcePos = source.getPosition();


        BlockPos initialPos = new BlockPos(sourcePos);

        LinkedList<Long> positionsToFill = new LinkedList<Long>();
        ArrayList<Long> marked = new ArrayList<Long>();
        positionsToFill.add(initialPos.asLong());

        ServerWorld world = source.getWorld();
        PlacementHandler ph = new PlacementHandler(world);
        BlockState initialState = world.getBlockState(initialPos);

        int iterations = 0;
        while (iterations < 10000000 && !positionsToFill.isEmpty()) {
            Long currentPosLong = positionsToFill.removeFirst();
            BlockPos currentPos = BlockPos.fromLong(currentPosLong);

            int dist2 = (initialPos.getX() - currentPos.getX()) * (initialPos.getX() - currentPos.getX())
                    +(initialPos.getY() - currentPos.getY()) * (initialPos.getY() - currentPos.getY())
                    +(initialPos.getZ() - currentPos.getZ()) * (initialPos.getZ() - currentPos.getZ());

            if (world.getBlockState(currentPos) != initialState || dist2 > radius * radius) {
                continue;
            }

            ph.placeBlock(currentPos, blockType.getBlock());

            if (!marked.contains(currentPos.add(1, 0, 0).asLong())) {
                marked.add(currentPos.add(1, 0, 0).asLong());
                positionsToFill.add(currentPos.add(1, 0, 0).asLong());
            }

            if (!marked.contains(currentPos.add(-1, 0, 0).asLong())) {
                marked.add(currentPos.add(-1, 0, 0).asLong());
                positionsToFill.add(currentPos.add(-1, 0, 0).asLong());
            }

            if (!marked.contains(currentPos.add(0, 1, 0).asLong())) {
                marked.add(currentPos.add(0, 1, 0).asLong());
                positionsToFill.add(currentPos.add(0, 1, 0).asLong());
            }

            if (!marked.contains(currentPos.add(0, -1, 0).asLong())) {
                marked.add(currentPos.add(0, -1, 0).asLong());
                positionsToFill.add(currentPos.add(0, -1, 0).asLong());
            }

            if (!marked.contains(currentPos.add(0, 0, 1).asLong())) {
                marked.add(currentPos.add(0, 0, 1).asLong());
                positionsToFill.add(currentPos.add(0, 0, 1).asLong());
            }

            if (!marked.contains(currentPos.add(0, 0, -1).asLong())) {
                marked.add(currentPos.add(0, 0, -1).asLong());
                positionsToFill.add(currentPos.add(0, 0, -1).asLong());
            }

            iterations++;
        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, PaintBucket::fill));

        return 1;
    }

}
