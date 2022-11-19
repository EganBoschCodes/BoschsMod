package net.fabricmc.bosch.selection;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class PlacementHandler {
    private HashMap<BlockPos, BlockState> shiftMap;
    private final ServerWorld world;

    public PlacementHandler (ServerWorld w) {
        shiftMap = new HashMap<BlockPos, BlockState>();
        world = w;
    }

    public void placeBlock(BlockPos position, BlockState block) {
        BlockState currentState = world.getBlockState(position);

        if(!shiftMap.containsKey(position)) shiftMap.put(position, currentState);
        
        world.setBlockState(position, block);
    }

    public void undo() {
        for (Map.Entry<BlockPos,BlockState> mapElement : shiftMap.entrySet()) {
            BlockPos position = mapElement.getKey();
            BlockState lastState = mapElement.getValue();
 
            world.setBlockState(position, lastState);
        }
    }

    private void undoOffset(int x, int y, int z) {
        for (Map.Entry<BlockPos,BlockState> mapElement : shiftMap.entrySet()) {
            BlockPos position = mapElement.getKey();
            BlockState lastState = mapElement.getValue();
 
            world.setBlockState(position.add(x, y, z), lastState);
        }
    }

    public void scoot(int x, int y, int z) {
        PlacementHandler scootPlacement = new PlacementHandler(world);
        HashMap<BlockPos, BlockState> newMap = new HashMap<BlockPos, BlockState>();

        for (Map.Entry<BlockPos,BlockState> mapElement : shiftMap.entrySet()) {
            BlockPos position = mapElement.getKey();
            BlockState lastState = mapElement.getValue();

            scootPlacement.placeBlock(position, lastState);
        }

        for (Map.Entry<BlockPos,BlockState> mapElement : shiftMap.entrySet()) {
            BlockPos position = mapElement.getKey();

            newMap.put(position.add(x, y, z), world.getBlockState(position.add(x, y, z)));
        }

        shiftMap = newMap;
        scootPlacement.undoOffset(x, y, z);
    }

    public HashMap<BlockPos, BlockState> getMask() {
        HashMap<BlockPos, BlockState> newMap = new HashMap<BlockPos, BlockState>();

        for (Map.Entry<BlockPos,BlockState> mapElement : shiftMap.entrySet()) {
            BlockPos position = mapElement.getKey();
            newMap.put(position, world.getBlockState(position));
        }

        return newMap;
    }
}
