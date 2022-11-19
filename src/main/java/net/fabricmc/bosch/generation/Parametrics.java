package net.fabricmc.bosch.generation;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.bosch.BoschMain;
import net.fabricmc.bosch.parsing.BlockPalatte;
import net.fabricmc.bosch.parsing.Expression;
import net.fabricmc.bosch.selection.PlacementHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Parametrics {
    public static int trace(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();

        PlacementHandler ph = new PlacementHandler(source.getWorld());


        String[] palatteStrings = StringArgumentType.getString(context, "block").split(",");
        BlockPalatte[] palatte = new BlockPalatte[palatteStrings.length];
        for (int i = 0; i < palatteStrings.length; i++){
            palatte[i] = new BlockPalatte(palatteStrings[i]);
        }

        float t = FloatArgumentType.getFloat(context, "from");
        float tmax = FloatArgumentType.getFloat(context, "to");
        float tstep = FloatArgumentType.getFloat(context, "step");

        String expr = StringArgumentType.getString(context, "path");

        String blockExpr = Expression.extractExpr(expr, "block");

        ArrayList<Map<String, Expression.Maybe>> positions = new ArrayList<>();

        BlockPos playerPos = new BlockPos(source.getPosition());
        for(; t < tmax; t += tstep) {
            HashMap<String, Float> variables = new HashMap<>();
            variables.put("t", t);
            Map<String, Expression.Maybe> posVals = Expression.extractAllValues(expr, variables);
            posVals.put("t", Expression.Maybe.yes(t));
            positions.add(posVals);
        }

        for(int i = 0; i < positions.size() - 1; i++) {
            Map<String, Expression.Maybe> valsAtA = positions.get(i);
            Map<String, Expression.Maybe> valsAtB = positions.get(i + 1);
            Vec3d posA;
            Vec3d posB;

            try {
                posA = getPos(valsAtA);
                posB = getPos(valsAtB);
            }
            catch(Exception e) {
                source.sendMessage(Text.literal(e.toString()));
                return 1;
            }

            float thicknessA = valsAtA.containsKey("r") && valsAtA.get("r").is ? valsAtA.get("r").val : 1.0f;
            float thicknessB = valsAtB.containsKey("r") && valsAtB.get("r").is ? valsAtB.get("r").val : 1.0f;

            float margin = valsAtB.containsKey("margin") && valsAtB.get("margin").is ? valsAtB.get("margin").val : 0.0f;

            connectTheDots(ph, playerPos, palatte, Expression.clean(valsAtA), blockExpr, posA, posB, thicknessA, thicknessB, margin);
        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, Parametrics::trace));

        return 1;
    }

    private static void connectTheDots(PlacementHandler ph, BlockPos playerPos, BlockPalatte[] palatte, Map<String, Float> values, String blockExpr, Vec3d a, Vec3d b, float ra, float rb, float margin) {
        Vec3d heading = b.subtract(a).normalize();
        float length = (float)b.subtract(a).length();

        if(ra < 0 || rb < 0) return;

        Vec3d orthonormalOne = heading.x == 0 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        orthonormalOne = orthonormalOne.subtract(heading.multiply(orthonormalOne.dotProduct(heading))).normalize();
        Vec3d orthonormalTwo = orthonormalOne.crossProduct(heading).normalize();

        ArrayList<Vec3d> circleAround = new ArrayList<>();
        double dt = 0.5 / Math.max(ra + 0.001, rb + 0.001);
        for(double theta = 0; theta < 2 * Math.PI + dt; theta += dt) {
            circleAround.add(orthonormalOne.multiply(Math.sin(theta)).add(orthonormalTwo.multiply(Math.cos(theta))));
        }

        for(float path = -margin; path <= length + margin; path += 0.5) {
            Vec3d position = a.add(heading.multiply(path));

            for(Vec3d vec : circleAround) {
                BlockPos bp = new BlockPos(position.add(vec.multiply(ra + (rb - ra) * (path / length))));
                values.put("x", (float)bp.getX());
                values.put("y", (float)bp.getY());
                values.put("z", (float)bp.getZ());
                values.remove(blockExpr);

                int palatteIndex = blockExpr.length() > 0 ? clamp(Expression.parse(blockExpr, values), palatte.length - 1) : 0;
                ph.placeBlock(bp.add(playerPos), palatte[palatteIndex].getBlock());
            }
        }
    }

    private static int clamp(Expression.Maybe m, int max) {
        if (!m.is) return 0;
        int mval = (int)m.val;
        return mval < 0 ? 0 : Math.min(mval, max);
    }

    private static Vec3d getPos(Map<String, Expression.Maybe> vals) throws Exception {
        if (!vals.containsKey("x") || !vals.containsKey("y") || !vals.containsKey("z")) {
            throw new Exception("Not all positional variables were provided!");
        }
        Expression.Maybe x = vals.get("x");
        Expression.Maybe y = vals.get("y");
        Expression.Maybe z = vals.get("z");

        if (!x.is) throw new Exception("Error parsing \"x\" variable!");
        if (!y.is) throw new Exception("Error parsing \"y\" variable!");
        if (!z.is) throw new Exception("Error parsing \"z\" variable!");

        return new Vec3d(x.val, y.val, z.val);
    }
}
