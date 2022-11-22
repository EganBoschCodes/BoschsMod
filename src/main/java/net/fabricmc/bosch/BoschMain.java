package net.fabricmc.bosch;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.bosch.generation.*;
import net.fabricmc.bosch.parsing.BlockPalatte;
import net.fabricmc.bosch.selection.Clipboard;
import net.fabricmc.bosch.selection.PlacementHandler;
import net.fabricmc.bosch.selection.SelectionCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import static net.minecraft.command.CommandSource.suggestMatching;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BoschMain implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("boschs-mod");

    public static HashMap<ServerPlayerEntity, BlockPos> MARK_1 = new HashMap<>();
    public static HashMap<ServerPlayerEntity, BlockPos> MARK_2 = new HashMap<>();
    public static HashMap<ServerPlayerEntity, Clipboard> CLIPBOARD = new HashMap<>();
    public static HashMap<ServerPlayerEntity, BlockPos> LOCK = new HashMap<>();
    public static HashMap<ServerPlayerEntity, CommandHistory> COMMAND_HISTORY = new HashMap<>();
    public static ArrayList<String> BLOCK_IDS;

    public static final HashMap<ServerPlayerEntity, ArrayList<PlacementHandler>> PLACEMENT_HANDLER = new HashMap<ServerPlayerEntity, ArrayList<PlacementHandler>>();

	@Override
	public void onInitialize() {
		LOGGER.info("Bosch's Mod Registered.");

        BLOCK_IDS = new ArrayList<String>();
        for (Identifier block : Registry.BLOCK.getIds()) {
            BLOCK_IDS.add(block.getPath());
        }

        initCommands();
	}

    public static void savePlacement(PlacementHandler ph, ServerPlayerEntity player) {
        if(!PLACEMENT_HANDLER.containsKey(player)) {
            PLACEMENT_HANDLER.put(player, new ArrayList<PlacementHandler>());
        }

        PLACEMENT_HANDLER.get(player).add(ph);
        if(PLACEMENT_HANDLER.get(player).size() > 20) {
            PLACEMENT_HANDLER.get(player).remove(0);
        }
    }

    public void initCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("paintbucket")
            .then(argument("block", StringArgumentType.string())
                .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"stone", "air"}), b))
                .then(argument("iterations", FloatArgumentType.floatArg(0.0f, 128.0f))
                    .executes(PaintBucket::fill)
        ))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("gentree")
            .then(argument("log", StringArgumentType.string())
                .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"spruce_wood", "oak_wood", "\"spruce_wood:25%dark_oak_wood\""}), b))
                .then(argument("leaf", StringArgumentType.string())
                    .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"oak_leaves", "\"50%white_stained_glass:50%white_stained_glass_pane:pink_stained_glass:pink_stained_glass_pane:20%end_rod\"", "spruce_leaves"}), b))
                    .then(argument("light", StringArgumentType.string())
                        .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"shroomlight", "pearlescent_froglight", "sea_lantern"}), b))
                        .then(argument("radius", FloatArgumentType.floatArg(0.0f, 32.0f))
                            .suggests((c, b) -> suggestMatching(java.util.Arrays.asList("8", "4", "16"), b))
                            .then(argument("scale", FloatArgumentType.floatArg(0.0f, 3.0f))
                                .suggests((c, b) -> suggestMatching(java.util.Arrays.asList("1", "0.5", "1.5"), b))
                                .then(argument("leaf density", FloatArgumentType.floatArg(0.0f, 3.0f))
                                    .suggests((c, b) -> suggestMatching(java.util.Arrays.asList("1", "0.85", "1.3"), b))
                                    .executes(Tree::generateTree)))))))
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("nah")
            .executes(SelectionCommands::undo)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("mark1")
            .executes(context -> {return SelectionCommands.mark(context, MARK_1);})
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("mark2")
            .executes(context -> {return SelectionCommands.mark(context, MARK_2);})
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("copysel")
            .executes(SelectionCommands::copy)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("pastesel")
            .executes(SelectionCommands::paste)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("flipsel")
            .then(argument("axis", StringArgumentType.string())
                .suggests((c, b) -> suggestMatching(java.util.Arrays.asList("x", "y", "z"), b))
                .executes(SelectionCommands::fill)
        )));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("rotsel")
            .then(argument("degree", IntegerArgumentType.integer(-360, 360))
                .suggests((c, b) -> suggestMatching(java.util.Arrays.asList("90", "180", "-90"), b))
                .then(argument("axis", StringArgumentType.string())
                    .suggests((c, b) -> suggestMatching(java.util.Arrays.asList("x", "y", "z"), b))
                    .executes(SelectionCommands::rotate)
        ))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("scoot")
                .then(argument("x", IntegerArgumentType.integer())
                    .suggests((c, b) -> suggestMatching(List.of("0"), b))
                    .then(argument("y", IntegerArgumentType.integer())
                        .suggests((c, b) -> suggestMatching(List.of("0"), b))
                        .then(argument("z", IntegerArgumentType.integer())
                                .suggests((c, b) -> suggestMatching(List.of("0"), b))
                                .executes(SelectionCommands::scoot)
        )))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("snip")
            .executes(SelectionCommands::snip)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("retry")
                .executes(SelectionCommands::redo)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("lock")
                .executes(SelectionCommands::lock)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("unlock")
                .executes(SelectionCommands::unlock)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("parametric")
            .then(argument("block", StringArgumentType.string())
                .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"\"diamond_block:25%glowstone\"", "stone", "diamond_block"}), b))
                .then(argument("from", FloatArgumentType.floatArg())
                    .then(argument("to", FloatArgumentType.floatArg())
                        .then(argument("step", FloatArgumentType.floatArg(0.0f))
                            .then(argument("path", StringArgumentType.greedyString())
                            .executes(Parametrics::trace)
        )))))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("isometric")
                .then(argument("block", StringArgumentType.string())
                    .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"stone", "diamond_block", "\"diamond_block:25%glowstone\""}), b))
                    .then(argument("box size", IntegerArgumentType.integer(0))
                        .then(argument("equation", StringArgumentType.greedyString())
                            .executes(Isometrics::generate)
        )))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("markpos")
                .executes(Spline::mark)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("clearpos")
                .executes(Spline::clear)
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("spline")
                .then(argument("block", StringArgumentType.string())
                    .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"stone", "diamond_block"}), b))
                    .executes(Spline::traceDefault)
                    .then(argument("equation", StringArgumentType.greedyString())
                            .executes(Spline::trace)
        ))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("multiparametric")
                .then(argument("block", StringArgumentType.string())
                        .suggests((c, b) -> suggestMatching(BlockPalatte.getSuggestions(c, new String[] {"stone", "diamond_block"}), b))
                        .then(argument("equation", StringArgumentType.greedyString())
                                .executes(Parametrics::multi)
                        ))));
    }

    public static class CommandHistory {
        public CommandContext<ServerCommandSource> context;
        public Function<CommandContext<ServerCommandSource>, Integer> func;

        public CommandHistory (CommandContext<ServerCommandSource> ctx, Function<CommandContext<ServerCommandSource>, Integer> f) {
            context = ctx;
            func = f;
        }

        public void rerun() {
            func.apply(context);
        }

    }

}
