package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Read-only field report and explicit device removal; no hidden heal/cheat command. */
public final class MedicalCommands {
    private static boolean registered;
    private MedicalCommands(){ }
    public static void register() {
        if(registered)return;registered=true;
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
            var root=Commands.literal("aftermath");
            root.then(Commands.literal("status").executes(context->{
                var player=context.getSource().getPlayerOrException();var body=ServerMedicalSystem.get(player).snapshot();var s=body.survival();
                context.getSource().sendSuccess(()->Component.translatable("decaying_aftermath.report.vitals",
                        Math.round(body.blood()*100),Math.round(body.oxygen()*100),Math.round(s.hydration()*100),
                        Math.round(s.nutrition()*100),String.format(Locale.ROOT,"%.1f",s.temperature()),
                        Math.round(s.encumbrance()*100),Math.round(s.redCells()*100),Math.round(s.plasma()*100)),false);
                for(BodyPart part:BodyPart.values()) {
                    var p=body.part(part);
                    if(p.integrity()<.999f||p.infection()>0||p.fractured()||p.foreignBody())
                        context.getSource().sendSuccess(()->Component.translatable("decaying_aftermath.report.part",
                                Component.translatable(part.translationKey()),Math.round(p.integrity()*100),
                                Math.round(p.bleeding()*100),Math.round(p.infection()*100),
                                Component.translatable(p.fractured()?"decaying_aftermath.report.fractured":"decaying_aftermath.report.no_fracture"),
                                Component.translatable(p.foreignBody()?"decaying_aftermath.report.foreign":"decaying_aftermath.report.no_foreign")),false);
                }
                return 1;
            }));
            var remove=Commands.literal("remove_tourniquet");
            for(BodyPart part:BodyPart.values())if(part.isLimb())remove.then(Commands.literal(part.name().toLowerCase(Locale.ROOT)).executes(context->{
                var player=context.getSource().getPlayerOrException();
                if(!MedicalNetwork.removeDevice(player,part,io.github.safedecimal.decayingaftermath.medical.TreatmentKind.TOURNIQUET))return 0;
                context.getSource().sendSuccess(()->Component.translatable("decaying_aftermath.report.tourniquet_removed",
                        Component.translatable(part.translationKey())),false);return 1;
            }));
            var removeSplint=Commands.literal("remove_splint");
            for(BodyPart part:BodyPart.values())if(part.isLimb())removeSplint.then(Commands.literal(part.name().toLowerCase(Locale.ROOT)).executes(context->{
                var player=context.getSource().getPlayerOrException();
                if(!MedicalNetwork.removeDevice(player,part,io.github.safedecimal.decayingaftermath.medical.TreatmentKind.SPLINT))return 0;
                context.getSource().sendSuccess(()->Component.translatable("decaying_aftermath.report.splint_removed",
                        Component.translatable(part.translationKey())),false);return 1;
            }));
            root.then(remove);root.then(removeSplint);dispatcher.register(root);
        });
    }
}
