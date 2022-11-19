package net.fabricmc.bosch.generation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.bosch.BoschMain;
import net.fabricmc.bosch.parsing.BlockPalatte;
import net.fabricmc.bosch.parsing.Expression;
import net.fabricmc.bosch.selection.PlacementHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class Isometrics {
    public static int generate(CommandContext<ServerCommandSource> context) {

        final ServerCommandSource source = context.getSource();
        PlacementHandler ph = new PlacementHandler(source.getWorld());

        String[] palatteStrings = StringArgumentType.getString(context, "block").split(",");
        BlockPalatte[] palatte = new BlockPalatte[palatteStrings.length];
        for (int i = 0; i < palatteStrings.length; i++){
            palatte[i] = new BlockPalatte(palatteStrings[i]);
        }

        int boxSize = IntegerArgumentType.getInteger(context, "box size");

        String expr = StringArgumentType.getString(context, "equation");

        BlockPos playerPos = new BlockPos(source.getPosition());

        for(float x = -boxSize; x < boxSize; x++) {
            for(float y = -boxSize; y < boxSize; y++) {
                for(float z = -boxSize; z < boxSize; z++) {
                    Map<String, Float> pos = new HashMap<>();
                    pos.put("x", x);
                    pos.put("y", y);
                    pos.put("z", z);

                    Map<String, Expression.Maybe> output = Expression.extractAllValues(expr, pos);

                    if(!output.containsKey("place")) {
                        source.sendMessage(Text.literal("Specify the \"place\" variable in the expression to determine whether or not that block gets placed!"));
                        return 1;
                    }

                    for(Map.Entry<String, Expression.Maybe> entry : output.entrySet()) {
                        if(!entry.getValue().is) {
                            source.sendMessage(Text.literal("Error when parsing the value of \""+entry.getKey()+"\"!"));
                            return 1;
                        }
                    }

                    int palatteIndex = output.containsKey("block") && output.get("block").is ? (int)output.get("block").val : 0;

                    if(output.get("place").val != 0.0f) {
                        ph.placeBlock(playerPos.add(x, y, z), palatte[palatteIndex].getBlock());
                    }

                }
            }
        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, Isometrics::generate));

        return 1;
    }

}
