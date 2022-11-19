package net.fabricmc.bosch.parsing;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.bosch.BoschMain;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BlockPalatte {

    private final BlockProbability[] blockProbabilities;

    public BlockPalatte (String input) {
        String[] splitString = input.split(":");
        blockProbabilities = Stream.of(splitString).map(BlockProbability::new).toArray(BlockProbability[]::new);
    }

    public BlockState getBlock() {
        float sumWeights = 0;
        for(BlockProbability bp : blockProbabilities) {
            sumWeights += bp.weight;
        }

        if(sumWeights == 0) {
            return null;
        }

        float choice = (float)(Math.random()) * sumWeights;

        for(BlockProbability bp : blockProbabilities) {
            if (choice > bp.weight) {
                choice -= bp.weight;
                continue;
            }
            return bp.getBlockState();
        }

        return Blocks.AIR.getDefaultState();
    }

    public String toString() {
        String[] bpStrings = Stream.of(blockProbabilities).map((BlockProbability bp) -> {return bp.toString();}).toArray(String[]::new);
        String stringRep = "{ ";

        for (int i = 0; i < bpStrings.length; i++) {
            stringRep += bpStrings[i];
            if(i < bpStrings.length - 1) {
                stringRep += ", ";
            }
        }

        stringRep += " }";
        return stringRep;
    }

    public static List<String> getSuggestions(CommandContext<ServerCommandSource> ctx, String[] defaults) {

        if(ctx.getInput().charAt(ctx.getInput().length() - 1) == ' ') {
            return java.util.Arrays.asList(defaults);
        }

        String[] commSplit = ctx.getInput().split(" ");
        String str = commSplit[commSplit.length - 1];

        int lastIndexColon = str.lastIndexOf(":");
        int lastIndexPercent = str.lastIndexOf("%");
        int lastIndexComma = str.lastIndexOf(",");

        int splitIndex = Math.max(Math.max(lastIndexColon, lastIndexPercent), lastIndexComma);
        String before = str.substring(0, splitIndex + 1);
        if(before.length() == 0 || before.charAt(0) != '\"') {
            before = "\"" + before;
        }
        ArrayList<String> suggestions = new ArrayList<String>();

        for(String blockId : BoschMain.BLOCK_IDS) {
            suggestions.add(before + blockId);
        }

        return suggestions;
    }

    private class BlockProbability {
        public float weight;
        private Block block;

        private boolean isLeaf;

        public BlockProbability(String input) {
            weight = 1;
            if(input.contains("%")) {
                String[] split = input.split("%");
                input = split[1];

                weight = Float.parseFloat(split[0]) / 100.0f;
            }
            block = Registry.BLOCK.get(new Identifier(input));

            isLeaf = input.contains("leaves");
        }

        public BlockState getBlockState() {
            return block == null ? null : isLeaf ? block.getDefaultState().with(LeavesBlock.PERSISTENT, true) : block.getDefaultState();
        }

        public String toString() {
            return "{ weight: " + weight + ", blockType: "+block.getName().getString()+" }";
        }
    }
}
