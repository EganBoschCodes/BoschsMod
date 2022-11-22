package net.fabricmc.bosch.selection;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.bosch.BoschMain;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;

import static net.fabricmc.bosch.BoschMain.*;

public class SelectionCommands {
    public static int undo(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();

        if(!PLACEMENT_HANDLER.containsKey(source.getPlayer()) || PLACEMENT_HANDLER.get(source.getPlayer()).size() == 0) {
            source.sendMessage(Text.literal("Nothing to undo."));
            return 1;
        }

        ArrayList<PlacementHandler> playerPlacements = PLACEMENT_HANDLER.get(source.getPlayer());

        PlacementHandler undoPlacement = playerPlacements.get(playerPlacements.size() - 1);
        playerPlacements.remove(playerPlacements.size() - 1);

        undoPlacement.undo();

        return 1;
    }

    public static int mark(CommandContext<ServerCommandSource> context, HashMap<ServerPlayerEntity, BlockPos> MARK) {
        final ServerCommandSource source = context.getSource();
        Vec3d sourcePos = source.getPosition();
        BlockPos playerPos = new BlockPos(sourcePos);

        source.sendMessage(Text.literal("Set corner 1! ("+playerPos.getX()+", "+playerPos.getY()+", "+playerPos.getZ()+")"));
        MARK.put(source.getPlayer(), playerPos);

        return 1;
    }

    public static int copy(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        Vec3d sourcePos = source.getPosition();
        BlockPos playerPos = new BlockPos(sourcePos);
        ServerWorld world = source.getWorld();
        ServerPlayerEntity player = source.getPlayer();

        if(!MARK_1.containsKey(player) || !MARK_2.containsKey(player)) {
            source.sendMessage(Text.literal("Select two corners before copying!"));
            return 1;
        }

        long volume = Math.abs((MARK_1.get(player).getX() - MARK_2.get(player).getX()) * (MARK_1.get(player).getY() - MARK_2.get(player).getY()) * (MARK_1.get(player).getZ() - MARK_2.get(player).getZ()));
        if(volume > 20000000) {
            source.sendMessage(Text.literal("Selection too large (" + volume+" blocks, but maximum 20,000,000!)"));
            return 1;
        }

        source.sendMessage(Text.literal("Copied selection! (" + volume + " blocks)"));
        CLIPBOARD.put(source.getPlayer(), new Clipboard(world, MARK_1.get(player), MARK_2.get(player), playerPos));

        return 1;
    }

    public static int paste(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        Vec3d sourcePos = source.getPosition();
        BlockPos playerPos = new BlockPos(sourcePos);
        ServerWorld world = source.getWorld();

        if(!CLIPBOARD.containsKey(source.getPlayer()) || CLIPBOARD.get(source.getPlayer()) == null) {
            source.sendMessage(Text.literal("No selection copied!"));
        }

        source.sendMessage(Text.literal("Pasted selection!"));
        CLIPBOARD.get(source.getPlayer()).paste(world, source.getPlayer(), playerPos);

        return 1;
    }

    public static int fill(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        String axis = StringArgumentType.getString(context, "axis");
        int status = CLIPBOARD.get(source.getPlayer()).flip(axis.equals("x") ? 0 : axis.equals("y") ? 1 : axis.equals("z") ? 2 : -1);

        if(status < 0) {
            source.sendMessage(Text.literal("Invalid Axis!"));
        }

        source.sendMessage(Text.literal("Selection flipped along " + axis + "-axis!"));

        return 1;
    }

    public static int rotate(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        int rotation = IntegerArgumentType.getInteger(context, "degree");
        String axis = StringArgumentType.getString(context, "axis");

        int axisNum = axis.equals("x") ? 0 : axis.equals("y") ? 1 : axis.equals("z") ? 2 : -1;

        if(axisNum < 0) {
            source.sendMessage(Text.literal("Invalid Axis!"));
            return 1;
        }
        if(rotation % 90 != 0) {
            source.sendMessage(Text.literal("Invalid Rotation Degree!"));
            return 1;
        }

        CLIPBOARD.get(source.getPlayer()).rotate(rotation, axisNum);
        source.sendMessage(Text.literal("Selection rotated " + rotation + " degrees around the " + axis + "-axis!"));

        return 1;
    }

    public static int scoot(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");

        if(!PLACEMENT_HANDLER.containsKey(source.getPlayer())) {
            source.sendMessage(Text.literal("Nothing to scoot."));
            return 1;
        }

        ArrayList<PlacementHandler> playerPH = PLACEMENT_HANDLER.get(source.getPlayer());

        if(playerPH.size() == 0) {
            source.sendMessage(Text.literal("Nothing to scoot."));
            return 1;
        }

        PlacementHandler scootPlacement = playerPH.get(playerPH.size() - 1);
        scootPlacement.scoot(x, y, z);

        return 1;
    }

    public static int snip(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();

        if(!PLACEMENT_HANDLER.containsKey(source.getPlayer())) {
            source.sendMessage(Text.literal("Nothing to snip."));
            return 1;
        }

        ArrayList<PlacementHandler> playerPH = PLACEMENT_HANDLER.get(source.getPlayer());

        if(playerPH.size() == 0) {
            source.sendMessage(Text.literal("Nothing to snip."));
            return 1;
        }

        PlacementHandler snipPlacement = playerPH.get(playerPH.size() - 1);
        Clipboard clip = new Clipboard(snipPlacement, new BlockPos(source.getPosition()));
        CLIPBOARD.put(source.getPlayer(), clip);

        playerPH.remove(playerPH.size() - 1);
        snipPlacement.undo();

        return 1;
    }

    public static int redo(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();

        if(!COMMAND_HISTORY.containsKey(source.getPlayer())) {
            source.sendMessage(Text.literal("Nothing to retry."));
            return 1;
        }

        BoschMain.CommandHistory history = COMMAND_HISTORY.get(source.getPlayer());
        ArrayList<PlacementHandler> playerPH = PLACEMENT_HANDLER.get(source.getPlayer());

        playerPH.get(playerPH.size() - 1).undo();
        playerPH.remove(playerPH.size() - 1);

        history.rerun();

        return 1;
    }

    public static int lock(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        BoschMain.LOCK.put(source.getPlayer(), new BlockPos(source.getPosition()));

        return 1;
    }

    public static int unlock(CommandContext<ServerCommandSource> context) {
        final ServerCommandSource source = context.getSource();
        BoschMain.LOCK.remove(source.getPlayer());

        return 1;
    }
}
