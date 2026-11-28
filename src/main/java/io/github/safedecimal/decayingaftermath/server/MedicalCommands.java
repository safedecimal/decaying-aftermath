package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Field reports/device removal plus explicit administrator-only full self-heal. */
public final class MedicalCommands {
    private static boolean registered;
    private MedicalCommands(){ }
    public static void register() {
        if(registered)return;registered=true;
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
            dispatcher.register(Commands.literal("heal")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context->{
                        var source=context.getSource();
                        // Re-check in the executor too: a conflicting third-party command tree must not weaken access.
                        if(!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source))
                            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("commands.generic.permission")).create();
                        var player=source.getPlayerOrException();
                        if(!player.isAlive())throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("abjection.heal.dead")).create();
                        ServerMedicalSystem.healFully(player);
                        source.sendSuccess(()->Component.translatable("abjection.heal.done"),false);return 1;
                    }));
            var root=Commands.literal("aftermath");
            root.then(Commands.literal("status").executes(context->{
                var player=context.getSource().getPlayerOrException();var body=ServerMedicalSystem.get(player).snapshot();var s=body.survival();
                context.getSource().sendSuccess(()->Component.translatable("abjection.report.vitals",
                        Math.round(body.blood()*100),Math.round(body.oxygen()*100),Math.round(s.hydration()*100),
                        Math.round(s.nutrition()*100),String.format(Locale.ROOT,"%.1f",s.temperature()),
                        Math.round(s.encumbrance()*100),Math.round(s.redCells()*100),Math.round(s.plasma()*100)),false);
                for(BodyPart part:BodyPart.values()) {
                    var p=body.part(part);
                    if(p.integrity()<.999f||p.infection()>0||p.fractured()||p.foreignBody())
                        context.getSource().sendSuccess(()->Component.translatable("abjection.report.part",
                                Component.translatable(part.translationKey()),Math.round(p.integrity()*100),
                                Math.round(p.bleeding()*100),Math.round(p.infection()*100),
                                Component.translatable(p.fractured()?"abjection.report.fractured":"abjection.report.no_fracture"),
                                Component.translatable(p.foreignBody()?"abjection.report.foreign":"abjection.report.no_foreign")),false);
                }
                return 1;
            }));
            var remove=Commands.literal("remove_tourniquet");
            for(BodyPart part:BodyPart.values())if(part.isLimb())remove.then(Commands.literal(part.name().toLowerCase(Locale.ROOT)).executes(context->{
                var player=context.getSource().getPlayerOrException();
                if(!MedicalNetwork.removeDevice(player,part,io.github.safedecimal.decayingaftermath.medical.TreatmentKind.TOURNIQUET))return 0;
                context.getSource().sendSuccess(()->Component.translatable("abjection.report.tourniquet_removed",
                        Component.translatable(part.translationKey())),false);return 1;
            }));
            var removeSplint=Commands.literal("remove_splint");
            for(BodyPart part:BodyPart.values())if(part.isLimb())removeSplint.then(Commands.literal(part.name().toLowerCase(Locale.ROOT)).executes(context->{
                var player=context.getSource().getPlayerOrException();
                if(!MedicalNetwork.removeDevice(player,part,io.github.safedecimal.decayingaftermath.medical.TreatmentKind.SPLINT))return 0;
                context.getSource().sendSuccess(()->Component.translatable("abjection.report.splint_removed",
                        Component.translatable(part.translationKey())),false);return 1;
            }));
            root.then(remove);root.then(removeSplint);dispatcher.register(root);
        });
    }
}
