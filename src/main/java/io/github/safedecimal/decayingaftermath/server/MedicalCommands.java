package io.github.safedecimal.decayingaftermath.server;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import io.github.safedecimal.decayingaftermath.network.SelfHarmNetwork;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** /abjection command suite (heal, status, set stats/mental/parts, devices) plus /heal and /aftermath. */
public final class MedicalCommands {
    private static boolean registered;
    private MedicalCommands() { }

    public static void register() {
        if (registered) return;
        registered = true;
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(Commands.literal("heal")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> execHeal(context.getSource())));

            dispatcher.register(Commands.literal("awake")
                    .executes(context -> execAwake(context.getSource())));

            dispatcher.register(buildRoot("aftermath"));
            dispatcher.register(buildRoot("abjection"));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String rootName) {
        var root = Commands.literal(rootName);

        root.then(Commands.literal("heal")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> execHeal(context.getSource())));

        root.then(Commands.literal("status").executes(context -> execStatus(context.getSource())));
        root.then(Commands.literal("awake").executes(context -> execAwake(context.getSource())));

        root.then(Commands.literal("self_harm")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    var source = checkGamemaster(context.getSource());
                    var player = source.getPlayerOrException();
                    if (!SelfHarmNetwork.triggerNow(player)) {
                        source.sendFailure(Component.literal("Нужен острый предмет/оружие в инвентаре и сознательное состояние."));
                        return 0;
                    }
                    return 1;
                }));

        var removeTourniquet = Commands.literal("remove_tourniquet");
        for (BodyPart part : BodyPart.values()) {
            if (part.isLimb()) {
                removeTourniquet.then(Commands.literal(part.name().toLowerCase(Locale.ROOT)).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!MedicalNetwork.removeDevice(player, part, TreatmentKind.TOURNIQUET)) return 0;
                    context.getSource().sendSuccess(() -> Component.translatable("abjection.report.tourniquet_removed",
                            Component.translatable(part.translationKey())), false);
                    return 1;
                }));
            }
        }
        var removeSplint = Commands.literal("remove_splint");
        for (BodyPart part : BodyPart.values()) {
            if (part.isLimb()) {
                removeSplint.then(Commands.literal(part.name().toLowerCase(Locale.ROOT)).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!MedicalNetwork.removeDevice(player, part, TreatmentKind.SPLINT)) return 0;
                    context.getSource().sendSuccess(() -> Component.translatable("abjection.report.splint_removed",
                            Component.translatable(part.translationKey())), false);
                    return 1;
                }));
            }
        }
        root.then(removeTourniquet);
        root.then(removeSplint);
        root.then(buildSetBranch());
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetBranch() {
        var set = Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        set.then(Commands.literal("mood")
                .then(Commands.argument("percent", FloatArgumentType.floatArg(-100f, 100f))
                        .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                            float val = FloatArgumentType.getFloat(ctx, "percent");
                            b.saveState().survivalState().snapshot();
                            var m = mental(b);
                            m.adminSetMood(val / 100f);
                            return "Настроение: " + Math.round(val) + "%";
                        }))));

        var dep = Commands.literal("depression");
        for (DepressionType type : DepressionType.values()) {
            dep.then(Commands.literal(type.id())
                    .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                        mental(b).adminSetDepression(type);
                        return "Депрессия: " + type.id();
                    })));
        }
        set.then(dep);

        set.then(Commands.literal("ptsd")
                .then(Commands.argument("percent", FloatArgumentType.floatArg(0f, 100f))
                        .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                            float val = FloatArgumentType.getFloat(ctx, "percent");
                            mental(b).adminSetPtsd(val / 100f);
                            return "ПТСР: " + Math.round(val) + "%";
                        }))));

        set.then(Commands.literal("deaths")
                .then(Commands.argument("count", IntegerArgumentType.integer(0, 1_000_000))
                        .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                            int count = IntegerArgumentType.getInteger(ctx, "count");
                            mental(b).adminSetDeaths(count);
                            return "Память о смертях: " + count;
                        }))));

        set.then(percentStat("blood", (b, v) -> b.adminSetVitals(v, Float.NaN, Float.NaN, Float.NaN), "Кровь"));
        set.then(percentStat("oxygen", (b, v) -> b.adminSetVitals(Float.NaN, v, Float.NaN, Float.NaN), "Кислород"));
        set.then(percentStat("energy", (b, v) -> b.adminSetVitals(Float.NaN, Float.NaN, v, Float.NaN), "Энергия"));
        set.then(percentStat("brain", (b, v) -> b.adminSetVitals(Float.NaN, Float.NaN, Float.NaN, v), "Мозг"));
        set.then(percentStat("hydration", (b, v) -> survival(b).adminSetSurvival(v, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN), "Вода"));
        set.then(percentStat("nutrition", (b, v) -> survival(b).adminSetSurvival(Float.NaN, v, Float.NaN, Float.NaN, Float.NaN, Float.NaN), "Питание"));
        set.then(percentStat("fatigue", (b, v) -> survival(b).adminSetSurvival(Float.NaN, Float.NaN, Float.NaN, v, Float.NaN, Float.NaN), "Усталость"));

        set.then(Commands.literal("temperature")
                .then(Commands.argument("celsius", FloatArgumentType.floatArg(25f, 45f))
                        .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                            float temp = FloatArgumentType.getFloat(ctx, "celsius");
                            survival(b).adminSetSurvival(Float.NaN, Float.NaN, temp, Float.NaN, Float.NaN, Float.NaN);
                            return String.format(Locale.ROOT, "Температура: %.1f°C", temp);
                        }))));

        set.then(Commands.literal("toxin")
                .then(Commands.argument("percent", FloatArgumentType.floatArg(0f, 300f))
                        .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                            float val = FloatArgumentType.getFloat(ctx, "percent");
                            survival(b).adminSetSurvival(Float.NaN, Float.NaN, Float.NaN, Float.NaN, val / 100f, Float.NaN);
                            return "Токсины: " + Math.round(val) + "%";
                        }))));

        set.then(Commands.literal("radiation")
                .then(Commands.argument("gy", FloatArgumentType.floatArg(0f, 100f))
                        .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                            float val = FloatArgumentType.getFloat(ctx, "gy");
                            survival(b).adminSetSurvival(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, val);
                            return String.format(Locale.ROOT, "Радиация: %.2f Гр", val);
                        }))));

        var partCmd = Commands.literal("part");
        for (BodyPart part : BodyPart.values()) {
            var pNode = Commands.literal(part.name().toLowerCase(Locale.ROOT));
            for (String field : new String[]{"integrity", "pain", "bleeding", "infection", "burn"}) {
                pNode.then(Commands.literal(field)
                        .then(Commands.argument("percent", FloatArgumentType.floatArg(0f, 100f))
                                .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                                    float val = FloatArgumentType.getFloat(ctx, "percent");
                                    b.adminSetPart(part, field, val / 100f);
                                    return part.name() + " " + field + ": " + Math.round(val) + "%";
                                }))));
            }
            partCmd.then(pNode);
        }
        set.then(partCmd);

        var fracCmd = Commands.literal("fracture");
        for (BodyPart part : BodyPart.values()) {
            if (!part.isLimb()) continue;
            fracCmd.then(Commands.literal(part.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("state", BoolArgumentType.bool())
                            .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                                boolean v = BoolArgumentType.getBool(ctx, "state");
                                b.adminSetFracture(part, v);
                                return part.name() + " перелом: " + v;
                            }))));
        }
        set.then(fracCmd);

        var splintCmd = Commands.literal("splint");
        for (BodyPart part : BodyPart.values()) {
            if (!part.isLimb()) continue;
            splintCmd.then(Commands.literal(part.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("state", BoolArgumentType.bool())
                            .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                                boolean v = BoolArgumentType.getBool(ctx, "state");
                                b.adminSetSplint(part, v);
                                return part.name() + " шина: " + v;
                            }))));
        }
        set.then(splintCmd);

        var modeCmd = Commands.literal("mode");
        for (BodySnapshot.Mode m : new BodySnapshot.Mode[]{
                BodySnapshot.Mode.CONSCIOUS, BodySnapshot.Mode.UNCONSCIOUS,
                BodySnapshot.Mode.LAST_STANDING, BodySnapshot.Mode.DYING}) {
            modeCmd.then(Commands.literal(m.name().toLowerCase(Locale.ROOT))
                    .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                        b.adminSetMode(m);
                        return "Режим: " + m.name();
                    })));
        }
        set.then(modeCmd);

        return set;
    }

    private interface FloatApplier { void apply(BodyModel body, float fraction); }

    private static LiteralArgumentBuilder<CommandSourceStack> percentStat(String name, FloatApplier applier, String label) {
        return Commands.literal(name)
                .then(Commands.argument("percent", FloatArgumentType.floatArg(0f, 100f))
                        .executes(ctx -> updateBody(ctx.getSource(), (p, b) -> {
                            float val = FloatArgumentType.getFloat(ctx, "percent");
                            applier.apply(b, val / 100f);
                            return label + ": " + Math.round(val) + "%";
                        })));
    }

    private interface BodyMutation { String apply(ServerPlayer player, BodyModel body); }

    private static int updateBody(CommandSourceStack source, BodyMutation mutation) throws CommandSyntaxException {
        checkGamemaster(source);
        var player = source.getPlayerOrException();
        var body = ServerMedicalSystem.get(player);
        String summary = mutation.apply(player, body);
        ServerMedicalSystem.syncNow(player);
        source.sendSuccess(() -> Component.literal("Abjection: " + summary), false);
        return 1;
    }

    private static MentalHealthModel mental(BodyModel body) {
        return body.survival().mental();
    }

    private static SurvivalModel survival(BodyModel body) {
        return body.survival();
    }

    private static CommandSourceStack checkGamemaster(CommandSourceStack source) throws CommandSyntaxException {
        // Re-check in the executor too: a conflicting third-party command tree must not weaken access.
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.generic.permission")).create();
        }
        return source;
    }

    private static int execHeal(CommandSourceStack source) throws CommandSyntaxException {
        checkGamemaster(source);
        var player = source.getPlayerOrException();
        if (!player.isAlive()) {
            throw new SimpleCommandExceptionType(Component.translatable("abjection.heal.dead")).create();
        }
        ServerMedicalSystem.healFully(player);
        source.sendSuccess(() -> Component.translatable("abjection.heal.done"), false);
        return 1;
    }

    private static int execAwake(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!ServerMedicalSystem.wakeFromSleep(player)) {
            source.sendFailure(Component.translatable("abjection.sleep.awake_denied"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("abjection.sleep.awake_done"), false);
        return 1;
    }

    private static int execStatus(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var body = ServerMedicalSystem.get(player).snapshot();
        var s = body.survival();
        source.sendSuccess(() -> Component.translatable("abjection.report.vitals",
                Math.round(body.blood() * 100), Math.round(body.oxygen() * 100), Math.round(s.hydration() * 100),
                Math.round(s.nutrition() * 100), String.format(Locale.ROOT, "%.1f", s.temperature()),
                Math.round(s.encumbrance() * 100), Math.round(s.redCells() * 100), Math.round(s.plasma() * 100)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Психика: настроение %d%% | депрессия: %s | ПТСР: %d%% | смертей в памяти: %d",
                Math.round(MedicalPhysiology.mood(body) * 100), s.depression().id(),
                Math.round(s.ptsdLoad() * 100), s.rememberedDeaths())), false);
        for (BodyPart part : BodyPart.values()) {
            var p = body.part(part);
            if (p.integrity() < .999f || p.infection() > 0 || p.fractured() || p.foreignBody()) {
                source.sendSuccess(() -> Component.translatable("abjection.report.part",
                        Component.translatable(part.translationKey()), Math.round(p.integrity() * 100),
                        Math.round(p.bleeding() * 100), Math.round(p.infection() * 100),
                        Component.translatable(p.fractured() ? "abjection.report.fractured" : "abjection.report.no_fracture"),
                        Component.translatable(p.foreignBody() ? "abjection.report.foreign" : "abjection.report.no_foreign")), false);
            }
        }
        return 1;
    }
}
