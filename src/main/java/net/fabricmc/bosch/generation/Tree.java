package net.fabricmc.bosch.generation;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.fabricmc.bosch.BoschMain;
import net.fabricmc.bosch.parsing.BlockPalatte;
import net.fabricmc.bosch.selection.PlacementHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Arrays;

import static net.fabricmc.bosch.BoschMain.LOGGER;

public class Tree {

    public static int generateTree(CommandContext<ServerCommandSource> context) {
        BlockPalatte logBlock = new BlockPalatte(StringArgumentType.getString(context, "log").split(",")[0]);
        BlockPalatte leafBlock = new BlockPalatte(StringArgumentType.getString(context, "leaf").split(",")[0]);
        BlockPalatte lightBlock = new BlockPalatte(StringArgumentType.getString(context, "light").split(",")[0]);

        float radius = FloatArgumentType.getFloat(context, "radius");
        float scale = FloatArgumentType.getFloat(context, "scale");
        float leafDensity = FloatArgumentType.getFloat(context, "leaf density");
        
        final ServerCommandSource source = context.getSource();

        BlockPos lockPos = BoschMain.LOCK.getOrDefault(source.getPlayer(), null);
        Vec3d sourcePos = lockPos != null ? new Vec3d(lockPos.getX(), lockPos.getY(), lockPos.getZ()): source.getPosition();;
        
        if(logBlock.getBlock() == null) {
            source.sendMessage(Text.literal("That is not a valid log block type!"));
            return 1;
        }

        if(leafBlock.getBlock() == null) {
            source.sendMessage(Text.literal("That is not a valid leaf block type!"));
            return 1;
        }

        if(lightBlock.getBlock() == null) {
            source.sendMessage(Text.literal("That is not a valid light block type!"));
            return 1;
        }

        LOGGER.info(logBlock.toString());
        LOGGER.info(leafBlock.toString());
        LOGGER.info(lightBlock.toString());

        ServerWorld world = source.getWorld();
        PlacementHandler ph = new PlacementHandler(world);

        ArrayList<Vec3d> branchEndpoints = generateBranch(ph, new Vec3d(Math.random() / 10 - 0.05, 1, Math.random() / 10 - 0.05), sourcePos, logBlock, radius, scale, 5);

        double minX = sourcePos.x, maxX = sourcePos.x, minY = sourcePos.y, maxY = sourcePos.y, minZ = sourcePos.z, maxZ = sourcePos.z;
        for (Vec3d endPoint : branchEndpoints) {
            minX = Math.min(minX, endPoint.x);
            maxX = Math.max(maxX, endPoint.x);
            minY = Math.min(minY, endPoint.y);
            maxY = Math.max(maxY, endPoint.y);
            minZ = Math.min(minZ, endPoint.z);
            maxZ = Math.max(maxZ, endPoint.z);
        }

        boolean firstBlockPlaced = false;
        DoublePerlinNoiseSampler.NoiseParameters parameters = new DoublePerlinNoiseSampler.NoiseParameters(0, new DoubleArrayList(Arrays.asList(2.0, 4.0)));
        DoublePerlinNoiseSampler noise = DoublePerlinNoiseSampler.create(Random.create(), parameters);

        for(double y = (int)maxY + radius * scale * 2; y >= minY - radius * scale * 2; y--){
            boolean blockChanged = false;
            for(double x = (int)minX - radius * scale * 2; x <= maxX + radius * scale * 2; x++) {
                for(double z = (int)minZ - radius * scale * 2; z <= maxZ + radius * scale * 2; z++) {
                    double val = 0;
                    for(Vec3d endPoint : branchEndpoints) {
                        val += 60 * leafDensity * Math.pow(scale, 2) / Math.pow(((x - endPoint.x) * (x - endPoint.x) + (y - endPoint.y) * (y - endPoint.y) + (z - endPoint.z) * (z - endPoint.z)), 1.3);
                    }

                    double noiseSample = noise.sample(x / 10 / scale , y / 10 / scale, z / 10 / scale) / 15;
                    val += noiseSample;

                    if(val > 1) {
                        blockChanged = true;
                        firstBlockPlaced = true;
                        if(Math.random() < Math.pow(0.05, 0.5/val) && !(((int)Math.abs(x)) % 7 + ((int)Math.abs(z))% 7 == 0) && world.getBlockState(new BlockPos(x, y, z)) == Blocks.AIR.getDefaultState()) {
                            BlockState blockState = val > 5 && Math.random() / val < 0.05 ? lightBlock.getBlock() : leafBlock.getBlock();
                            ph.placeBlock(new BlockPos(x, y, z), blockState);
                        }
                    }
                }
            }
            if(!blockChanged && firstBlockPlaced) {
                break;
            }
        }

        BoschMain.savePlacement(ph, source.getPlayer());
        BoschMain.COMMAND_HISTORY.put(source.getPlayer(), new BoschMain.CommandHistory(context, Tree::generateTree));

        return 1;
    }

    private static ArrayList<Vec3d> generateBranch(PlacementHandler ph, Vec3d heading, Vec3d position, BlockPalatte blockType, float thickness, float scale, int iterations) {
        if (iterations <= 0) {
            ArrayList<Vec3d> endPoint = new ArrayList<Vec3d>();
            endPoint.add(position.add(0, 3 * scale, 0));
            return endPoint;
        }

        heading.normalize();

        Vec3d orthonormalOne = heading.x == 0 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        orthonormalOne = orthonormalOne.subtract(heading.multiply(orthonormalOne.dotProduct(heading))).normalize();
        Vec3d orthonormalTwo = orthonormalOne.crossProduct(heading).normalize();

        double curveTheta = Math.random() * Math.PI * 2;

        float branchLength =  ((float)((1 + Math.sqrt(thickness + 5)) * (Math.pow(iterations, 1.3))) + 3) * scale;
        double curveStrength = (Math.random() / 100 + 0.07) / 5 / scale;
        double thetaEvolution = (Math.random() / 100 + 0.05) / scale;
        float decay = (0.3f + (float)Math.random() / 5);
        

        boolean middleFound = false;
        double lengthAlong = 0.5 + Math.random() / 4;
        Vec3d middleBranchPos = position;
        for(float step = 0; step <= branchLength; step += 0.5) {
            float effectiveThickness = thickness * (1 - step / branchLength * decay);
            float dTheta = 0.6f / effectiveThickness;
            position = position.add(heading.multiply(0.5));

            if(!middleFound && step > lengthAlong * branchLength) {
                middleBranchPos = position;
                middleFound = true;
            }
            for(float theta = 0; theta < 2 * Math.PI; theta += dTheta) {
                Vec3d blockPos = position.add(orthonormalOne.multiply(effectiveThickness * Math.cos(theta))).add(orthonormalTwo.multiply(effectiveThickness * Math.sin(theta)));
                ph.placeBlock(new BlockPos(blockPos), blockType.getBlock());
            }

            heading = heading.add(orthonormalOne.multiply(Math.cos(curveTheta) * curveStrength)).add(orthonormalTwo.multiply(Math.sin(curveTheta) * curveStrength)).add(0, 0.01 / scale, 0).normalize();
            orthonormalOne = heading.x == 0 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            orthonormalOne = orthonormalOne.subtract(heading.multiply(orthonormalOne.dotProduct(heading))).normalize();
            orthonormalTwo = orthonormalOne.crossProduct(heading).normalize();

            curveTheta += thetaEvolution;
        }

        double theta = Math.random() * 2 * Math.PI;
        
        ArrayList<Vec3d> endPoints = new ArrayList<Vec3d>();
        double numBranches = (iterations) / 2 + 1;
        for(int branch = 0; branch < numBranches; branch++) {
            double magShift = 0.7 + Math.random() / 5;

            ArrayList<Vec3d> branchEndings = generateBranch(ph, (iterations == 5 ? new Vec3d(0, 1, 0) : heading).multiply(Math.sqrt(1 - magShift * magShift)).add(orthonormalOne.multiply(magShift * Math.cos(theta))).add(orthonormalTwo.multiply(magShift * Math.sin(theta))), position.add(heading.multiply(-(double)(iterations * iterations)/5)), blockType, thickness * (1 - decay) * (1 - decay), scale, iterations - 1);
            endPoints.addAll(branchEndings);
            theta += (Math.random() - 0.5) * 3 / iterations + (2 * Math.PI / numBranches);
        }
        if(iterations > 2) {
            double magShift = 0.6 + Math.random() / 5;
            
            theta += (2 * Math.PI / numBranches / 2);

            ArrayList<Vec3d> branchEndings = generateBranch(ph, heading.multiply(Math.sqrt(1 - magShift * magShift)).add(orthonormalOne.multiply(magShift * Math.cos(theta))).add(orthonormalTwo.multiply(magShift * Math.sin(theta))), middleBranchPos.add(orthonormalOne.multiply(thickness * Math.cos(theta) * (1 - decay))).add(orthonormalTwo.multiply(thickness * Math.sin(theta) * (1 - decay))), blockType, thickness * (1 - decay) * (1 - decay) * (1 - decay) * (1 - decay), scale, iterations - 2);
            endPoints.addAll(branchEndings);
        }
        if(iterations == 5) {
            ArrayList<Vec3d> branchEndings = generateBranch(ph, heading, position, blockType, thickness * (1 - decay) * (1 - decay)* (1 - decay) * (1 - decay), scale, 3);
            endPoints.addAll(branchEndings);
        }
        return endPoints;
    }

}