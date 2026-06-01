package net.arm.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class AreaCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("kotaradius")
                .then(literal("add")
                    .executes(context -> addItem(context.getSource(), 3, null))
                    .then(argument("radius", IntegerArgumentType.integer(1, 32))
                        .executes(context -> {
                            int radius = IntegerArgumentType.getInteger(context, "radius");
                            return addItem(context.getSource(), radius, null);
                        })
                        .then(argument("height", IntegerArgumentType.integer(1, 32))
                            .executes(context -> {
                                int radius = IntegerArgumentType.getInteger(context, "radius");
                                int height = IntegerArgumentType.getInteger(context, "height");
                                return addItem(context.getSource(), radius, null);
                            })
                            .then(argument("item", StringArgumentType.greedyString())
                                .suggests(AreaCommand::suggestItems)
                                .executes(context -> {
                                    String itemIdStr = StringArgumentType.getString(context, "item");
                                    Identifier itemId = Identifier.tryParse(itemIdStr);
                                    if (itemId == null || !Registries.ITEM.containsId(itemId)) {
                                        context.getSource().sendError(Text.literal("§cНеверный ID предмета: " + itemIdStr));
                                        return -1;
                                    }
                                    Item item = Registries.ITEM.get(itemId);
                                    int radius = context.getArgument("radius", Integer.class);

                                    return addItem(context.getSource(), radius, item);
                                })
                            )
                        )
                    )
                )
                .then(literal("remove")
                    .executes(context -> {
                        return removeItemFromHand(context);
                    })
                    .then(argument("item", StringArgumentType.greedyString())
                        .suggests(AreaCommand::suggestConfiguredItems)
                        .executes(context -> {
                            String itemIdStr = StringArgumentType.getString(context, "item");
                            Identifier itemId = Identifier.tryParse(itemIdStr);
                            if (itemId == null || !Registries.ITEM.containsId(itemId)) {
                                context.getSource().sendError(Text.literal("§cНеверный ID предмета: " + itemIdStr));
                                return -1;
                            }
                            Item item = Registries.ITEM.get(itemId);
                            return removeItem(context.getSource(), item);
                        })
                    )
                )
            );
        });
    }

    private static int removeItemFromHand(CommandContext<FabricClientCommandSource> context) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        ItemStack stack = player.getMainHandStack();

        if (stack.isEmpty()) {
            context.getSource().sendError(Text.literal("§cДержите предмет в руке, чтобы удалить его из списка."));
            return -1;
        }

        Item item = stack.getItem();
        Identifier itemId = Registries.ITEM.getId(item);
        OutlineConfig config = OutlineConfig.getInstance();

        if (config.getEntryFor(item) == null) {
            context.getSource().sendError(Text.literal("§cПредмет §f" + itemId + " §cне найден в списке."));
            return -1;
        }

        config.removeEntry(item);
        context.getSource().sendFeedback(Text.literal("§aУдалён предмет: §f" + itemId));
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestItems(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return CompletableFuture.supplyAsync(() -> {
            Registries.ITEM.getIds().forEach(id -> {
                builder.suggest(id.toString());
            });
            return builder.build();
        });
    }

    private static CompletableFuture<Suggestions> suggestConfiguredItems(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return CompletableFuture.supplyAsync(() -> {
            OutlineConfig.getInstance().getEntries().keySet().forEach(builder::suggest);
            return builder.build();
        });
    }

    private static int addItem(FabricClientCommandSource source, int radius, Item item) {
        ClientPlayerEntity player = source.getPlayer();
        if (item == null) {
            ItemStack stack = player.getMainHandStack();
            if (stack.isEmpty()) {
                source.sendError(Text.literal("§cДержите предмет в руке или укажите его явно!"));
                return -1;
            }
            item = stack.getItem();
        }

        Identifier id = Registries.ITEM.getId(item);
        OutlineConfig config = OutlineConfig.getInstance();
        config.addEntry(item, radius);

        source.sendFeedback(Text.literal("§aДобавлен предмет: §f" + id + " §a(радиус=" + radius +  ")"));
        return 1;
    }

    private static int removeItem(FabricClientCommandSource source, Item item) {
        Identifier id = Registries.ITEM.getId(item);
        OutlineConfig config = OutlineConfig.getInstance();
        config.removeEntry(item);
        source.sendFeedback(Text.literal("§aУдалён предмет: §f" + id));
        return 1;
    }
}