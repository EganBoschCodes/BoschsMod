package net.fabricmc.bosch.selection;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static net.fabricmc.bosch.BoschMain.PLACEMENT_HANDLER;

public class Clipboard {
    private HashMap<BlockPos, BlockState> clipboard;
    
    public Clipboard(ServerWorld world, BlockPos C1, BlockPos C2, BlockPos playerPos) {
        clipboard = new HashMap<BlockPos, BlockState>();

        for(int x = Math.min(C1.getX(), C2.getX()); x <= Math.max(C1.getX(), C2.getX()); x++){
            for(int y = Math.min(C1.getY(), C2.getY()); y <= Math.max(C1.getY(), C2.getY()); y++){
                for(int z = Math.min(C1.getZ(), C2.getZ()); z <= Math.max(C1.getZ(), C2.getZ()); z++){
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState currentState = world.getBlockState(pos);
                    
                    if(!(currentState.getBlock().equals(Blocks.AIR))) {
                        clipboard.put(pos.add(playerPos.multiply(-1)), currentState);
                    }
                }
            }
        }
    }

    public Clipboard(PlacementHandler ph, BlockPos playerPos) {
        clipboard = new HashMap<BlockPos, BlockState>();

        HashMap<BlockPos, BlockState> phMask = ph.getMask();
        for (Map.Entry<BlockPos,BlockState> mapElement : phMask.entrySet()) {
            BlockPos position = mapElement.getKey();
            BlockState state = mapElement.getValue();

            clipboard.put(position.add(playerPos.multiply(-1)), state);
        }
    }
    /**
     * Rotates selection by {@code degree} degrees on {@code axis} axis. AxisRotation[axis] = {X, Y, Z}.
     */
    public int rotate(int degree, int axis) {
        if (degree % 90 != 0 || axis < 0 || axis > 2) return -1;

        int iterations = (degree / 90) % 4;

        HashMap<BlockPos, BlockState> rotatedClipboard = new HashMap<BlockPos, BlockState>();

        for (Map.Entry<BlockPos,BlockState> mapElement : clipboard.entrySet()) {
            BlockPos position = mapElement.getKey();
            BlockState lastState = mapElement.getValue();

            for(int i = 0; i < iterations; i++) {
                position = rotatePosition(position, axis);
            }
 
            rotatedClipboard.put(position, lastState);
        }

        clipboard = rotatedClipboard;
        return 0;
    }

    /**
     * Flips selection on {@code axis} axis. AxisRotation[axis] = {X, Y, Z}.
     */
    public int flip(int axis) {
        if (axis < 0 || axis > 2) return -1;

        HashMap<BlockPos, BlockState> flippedClipboard = new HashMap<BlockPos, BlockState>();

        for (Map.Entry<BlockPos,BlockState> mapElement : clipboard.entrySet()) {
            BlockPos position = mapElement.getKey();
            BlockState lastState = mapElement.getValue();

            if(axis == 0) {
                flippedClipboard.put(new BlockPos(-position.getX(), position.getY(), position.getZ()), lastState);
            }
            if(axis == 1) {
                flippedClipboard.put(new BlockPos(position.getX(), -position.getY(), position.getZ()), lastState);
            }
            if(axis == 2) {
                flippedClipboard.put(new BlockPos(position.getX(), position.getY(), -position.getZ()), lastState);
            }
        }

        clipboard = flippedClipboard;
        return 0;
    }

    private BlockPos rotatePosition(BlockPos pos, int axis) {
        if(axis == 0) {
            return new BlockPos(pos.getX(), pos.getZ(), -pos.getY());
        }
        else if(axis == 1) {
            return new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
        }
        else if(axis == 2) {
            return new BlockPos(pos.getY(), -pos.getX(), pos.getZ());
        }
        return pos;
    }

    public void paste(ServerWorld world, ServerPlayerEntity player, BlockPos playerPos) {
        PlacementHandler ph = new PlacementHandler(world);
        for (Map.Entry<BlockPos,BlockState> mapElement : clipboard.entrySet()) {
            BlockPos position = mapElement.getKey();
            BlockState lastState = mapElement.getValue();
 
            ph.placeBlock(position.add(playerPos), lastState);
        }
        if(!PLACEMENT_HANDLER.containsKey(player)) {
            PLACEMENT_HANDLER.put(player, new ArrayList<PlacementHandler>());
        }

        PLACEMENT_HANDLER.get(player).add(ph);
        if(PLACEMENT_HANDLER.get(player).size() > 20) {
            PLACEMENT_HANDLER.get(player).remove(0);
        }
    }
}
