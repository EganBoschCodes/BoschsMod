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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.bosch.BoschMain.LOGGER;

public class Parametrics {
    public static int trace(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();

        PlacementHandler ph = new PlacementHandler(source.getWorld());
        BlockPalatte[] palatte = BlockPalatte.parse(StringArgumentType.getString(context, "block"));

        float t = FloatArgumentType.getFloat(context, "from");
        float tmax = FloatArgumentType.getFloat(context, "to");
        float tstep = FloatArgumentType.getFloat(context, "step");

        String expr = StringArgumentType.getString(context, "path");

        Map<String, Expression> expressions = Expression.parseAll(expr);
        ArrayList<Map<String, Expression.Maybe>> positions = new ArrayList<>();

        BlockPos playerPos = BoschMain.LOCK.containsKey(source.getPlayer()) ? BoschMain.LOCK.get(source.getPlayer()) : new BlockPos(source.getPosition());

        for(; t < tmax; t += tstep) {
            HashMap<String, Float> variables = new HashMap<>();
            variables.put("t", t);
            Map<String, Expression.Maybe> posVals = Expression.evaluateAll(expressions, variables);
            for(Map.Entry<String, Expression.Maybe> entry : posVals.entrySet()) {
                if(!entry.getValue().is) {
                    source.sendMessage(Text.literal("Error when parsing the value of \""+entry.getKey()+"\" ("+entry.getValue().err+")!"));
                    return 1;
                }
            }
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

            connectTheDots(ph, playerPos, palatte, Expression.clean(valsAtA), expressions.containsKey("block") ? expressions.get("block") : null, posA, posB, thicknessA, thicknessB, margin);
        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, Parametrics::trace));

        return 1;
    }

    private static void connectTheDots(PlacementHandler ph, BlockPos playerPos, BlockPalatte[] palatte, Map<String, Float> values, Expression blockExpr, Vec3d a, Vec3d b, float ra, float rb, float margin) {
        Vec3d heading = b.subtract(a).normalize();
        float length = (float)b.subtract(a).length();

        if (ra < 0 || rb < 0) return;

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
                Expression.Maybe blockEval = blockExpr != null ? blockExpr.evaluate(values) : Expression.Maybe.yes(0.0f);

                int palatteIndex = clamp(blockEval, palatte.length - 1);
                ph.placeBlock(bp.add(playerPos), palatte[palatteIndex].getBlock());
            }
        }
    }

    public static int multi(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();

        PlacementHandler ph = new PlacementHandler(source.getWorld());
        BlockPalatte[] palatte = BlockPalatte.parse(StringArgumentType.getString(context, "block"));

        String eq = StringArgumentType.getString(context, "equation");
        Map<String, Expression> expressions = Expression.parseAll(eq);
        MultiIterator iterator;
        try { iterator = new MultiIterator(eq); } catch (IOException e) {
            source.sendMessage(Text.literal("Format your parametrics like t=[tmin, tmax, step]!"));
            return 1;
        }

        do {
            Map<String, Float> variables = iterator.values();
            Map<String, Expression.Maybe> evaluatedVars = Expression.evaluateAll(expressions, variables);

            for(Map.Entry<String, Expression.Maybe> entry : evaluatedVars.entrySet()) {
                if(!entry.getValue().is) {
                    source.sendMessage(Text.literal("Error when parsing the value of \""+entry.getKey()+"\" ( "+entry.getValue().err+" )!"));
                    return 1;
                }
            }

            for(Map.Entry<String, Float> entry : variables.entrySet()) {
                evaluatedVars.put(entry.getKey(), Expression.Maybe.yes(entry.getValue()));
            }

            if (!evaluatedVars.containsKey("x") || !evaluatedVars.containsKey("y") || !evaluatedVars.containsKey("z")) {
                source.sendMessage(Text.literal("You must define the x, y, and z variables!"));
                return 1;
            }

            int blockType = clamp(evaluatedVars.getOrDefault("block", Expression.Maybe.yes(0.0f)), palatte.length);
            Expression.Maybe shouldPlace = evaluatedVars.getOrDefault("place", Expression.Maybe.no(""));

            if (shouldPlace.is && shouldPlace.val != 0.0f) {
                ph.placeBlock(new BlockPos(source.getPosition().add(evaluatedVars.get("x").val, evaluatedVars.get("y").val, evaluatedVars.get("z").val)), palatte[blockType].getBlock());
            }

        } while(!iterator.iterate());

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, Parametrics::multi));

        return 1;
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

    private static class MultiIterator {

        private final Parametric[] parametrics;
        public MultiIterator(String s) throws IOException {
            String[] split = s.split(";");
            List<Parametric> params = new ArrayList<>();
            for(String var : split) {
                String[] varSplit = var.split("=");

                if (varSplit[1].charAt(0) != '[') continue;
                params.add(new Parametric(varSplit[0], varSplit[1]));
            }

            parametrics = new Parametric[params.size()];
            for(int i = 0; i < params.size(); i++) {
                parametrics[i] = params.get(i);
            }
        }

        public boolean iterate() {
            int index = 0;
            while(index < parametrics.length && parametrics[index].iterate()) index++;

            return index == parametrics.length;
        }

        public Map<String, Float> values() {
            Map<String, Float> vals = new HashMap<>();
            for(Parametric p : parametrics) {
                vals.put(p.tag, p.value);
            }
            return vals;
        }

        private static class Parametric {
            public float value;
            public String tag;
            private final float minVal, maxVal, step;
            public Parametric (String t, String s) throws IOException {
                tag = t;
                s = s.substring(1, s.length() - 1);
                String[] sSplit = s.split(",");
                value = Float.parseFloat(sSplit[0]);
                minVal = Float.parseFloat(sSplit[0]);
                maxVal = Float.parseFloat(sSplit[1]);
                step = Float.parseFloat(sSplit[2]);
            }

            public boolean iterate() {
                value += step;
                if(value < maxVal) return false;

                value = minVal;
                return true;
            }
        }
    }
}
