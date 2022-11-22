package net.fabricmc.bosch.generation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.bosch.BoschMain;
import net.fabricmc.bosch.parsing.BlockPalatte;
import net.fabricmc.bosch.parsing.Expression;
import net.fabricmc.bosch.parsing.Polynomial;
import net.fabricmc.bosch.selection.PlacementHandler;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.bosch.BoschMain.LOGGER;

public class Spline {
    public static Map<ServerPlayerEntity, List<BlockPos>> traces = new HashMap<>();

    public static int mark(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (!traces.containsKey(player)) traces.put(player, new ArrayList<>());
        if (player != null) traces.get(player).add(player.getBlockPos());

        return 1;
    }

    public static int clear(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        traces.remove(player);

        return 1;
    }

    public static int traceDefault (CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        if (traces.getOrDefault(source.getPlayer(), new ArrayList<>()).size() == 0) { source.sendMessage(Text.literal("Mark some points before calling this command!")); return 1; }

        BlockPalatte[] palatte = BlockPalatte.parse(StringArgumentType.getString(context, "block"));

        float accumulator = 0.0f;
        BlockPos lastPos = null;
        List<Float> xs = new ArrayList<>(), ys = new ArrayList<>(), zs = new ArrayList<>(), ts = new ArrayList<>();

        for(BlockPos blockPos : traces.get(source.getPlayer())) {
            xs.add((float)blockPos.getX());
            ys.add((float)blockPos.getY());
            zs.add((float)blockPos.getZ());
            if (lastPos != null) accumulator += (float)Math.sqrt(blockPos.getSquaredDistance(lastPos));
            lastPos = blockPos;
            ts.add(accumulator);
        }

        PlacementHandler ph = new PlacementHandler(source.getWorld());
        Polynomial xp = Polynomial.lagrange(ts, xs);
        Polynomial yp = Polynomial.lagrange(ts, ys);
        Polynomial zp = Polynomial.lagrange(ts, zs);

        float tfinal = ts.get(ts.size() - 1);
        for(float time = 0; time <= tfinal; time += 0.25f) {
            BlockPos pos = new BlockPos(xp.eval(time), yp.eval(time), zp.eval(time));
            ph.placeBlock(pos, palatte[0].getBlock());
        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, Spline::traceDefault));

        return 1;
    }

    public static int trace (CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        if (traces.getOrDefault(source.getPlayer(), new ArrayList<>()).size() == 0) { source.sendMessage(Text.literal("Mark some points before calling this command!")); return 1; }

        BlockPalatte[] palatte = BlockPalatte.parse(StringArgumentType.getString(context, "block"));

        String expr = StringArgumentType.getString(context, "equation");
        Map<String, Expression> expressions = Expression.parseAll(expr);

        float accumulator = 0.0f;
        BlockPos lastPos = null;
        List<Float> xs = new ArrayList<>(), ys = new ArrayList<>(), zs = new ArrayList<>(), ts = new ArrayList<>();

        for(BlockPos blockPos : traces.get(source.getPlayer())) {
            xs.add((float)blockPos.getX());
            ys.add((float)blockPos.getY());
            zs.add((float)blockPos.getZ());
            if (lastPos != null) accumulator += (float)Math.sqrt(blockPos.getSquaredDistance(lastPos));
            lastPos = blockPos;
            ts.add(accumulator);
        }

        PlacementHandler ph = new PlacementHandler(source.getWorld());
        Polynomial xp = Polynomial.lagrange(ts, xs);
        LOGGER.info(xp.toString());
        Polynomial dx = xp.derivative(1);
        LOGGER.info(dx.toString());
        Polynomial yp = Polynomial.lagrange(ts, ys);
        LOGGER.info(yp.toString());
        Polynomial dy = yp.derivative(1);
        LOGGER.info(dy.toString());
        Polynomial zp = Polynomial.lagrange(ts, zs);
        LOGGER.info(zp.toString());
        Polynomial dz = zp.derivative(1);
        LOGGER.info(dz.toString());

        float tfinal = ts.get(ts.size() - 1);
        float tstep = 0.25f;
        for(float time = tfinal * 0.01f; time <= tfinal * 0.99f; time += tstep) {
            Map<String, Float> variables = new HashMap<>();
            float x, y, z, dxdt, dydt, dzdt;

            variables.put("t", time/tfinal);
            variables.put("x", x = xp.eval(time));
            variables.put("y", y = yp.eval(time));
            variables.put("z", z = zp.eval(time));
            variables.put("dx", dxdt = dx.eval(time));
            variables.put("dy", dydt = dy.eval(time));
            variables.put("dz", dzdt = dz.eval(time));

            tstep = 0.2f/(float)Math.sqrt(dxdt * dxdt + dydt * dydt + dzdt * dzdt);

            Map<String, Expression.Maybe> vals = Expression.evaluateAll(expressions, variables);
            int blockType = clamp(vals.getOrDefault("block", Expression.Maybe.yes(0.0f)), palatte.length);

            Expression.Maybe r = vals.getOrDefault("r", Expression.Maybe.no(""));

            Vec3d pos = new Vec3d(x, y, z);
            if (!r.is || r.val == 0.0f) {
                ph.placeBlock(new BlockPos(pos), palatte[blockType].getBlock());
            }
            else {
                Vec3d heading = new Vec3d(dxdt, dydt, dzdt).normalize();

                Vec3d orthonormalOne = heading.x == 0 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
                orthonormalOne = orthonormalOne.subtract(heading.multiply(orthonormalOne.dotProduct(heading))).normalize();
                Vec3d orthonormalTwo = orthonormalOne.crossProduct(heading).normalize();

                for (float theta = 0; theta <= 2 * Math.PI; theta += 1 / r.val) {
                    Vec3d dr = orthonormalOne.multiply(r.val * Math.sin(theta)).add(orthonormalTwo.multiply(r.val * Math.cos(theta)));
                    ph.placeBlock(new BlockPos(pos.add(dr)), palatte[blockType].getBlock());
                }
            }

        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, Spline::trace));

        return 1;
    }

    private static int clamp(Expression.Maybe m, int max) {
        if (!m.is) return 0;
        int mval = (int)m.val;
        return mval < 0 ? 0 : Math.min(mval, max);
    }
}
