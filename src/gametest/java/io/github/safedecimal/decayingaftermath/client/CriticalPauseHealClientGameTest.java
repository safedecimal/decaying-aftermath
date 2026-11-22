package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.server.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.nio.file.*;

public final class CriticalPauseHealClientGameTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");context.waitFor(c->c.player!=null);
            world.getServer().runOnServer(s->{
                var p=s.getPlayerList().getPlayers().getFirst();var base=new BodyModel().snapshot();var parts=base.parts();
                parts[BodyPart.HEAD.ordinal()]=new BodySnapshot.PartSnapshot(.1f,0,0,false,false,false,false);
                ((ServerBodyOwner)(Object)p).decayingAftermath$setBody(BodyModel.restore(new BodySnapshot(.1f,1,1,0,0,72,BodySnapshot.Mode.UNCONSCIOUS,0,.4f,.3f,parts,base.survival())));
            });
            context.waitFor(c->ClientMedicalState.received()&&ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.UNCONSCIOUS);
            long before=world.getServer().computeOnServer(s->s.overworld().getGameTime());
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitTicks(5);
            context.runOnClient(c->{if(c.gui.screen() instanceof PauseScreen||c.isPaused())throw new AssertionError("Critical Esc paused the game");});
            copy(context.takeScreenshot("critical-esc-denied"),"esc-denied.png");context.waitTicks(20);
            long after=world.getServer().computeOnServer(s->s.overworld().getGameTime());if(after<=before)throw new AssertionError("Server time froze");
            context.getInput().pressKey(o->o.keyChat);context.waitForScreen(ChatScreen.class);
            copy(context.takeScreenshot("critical-chat-overlay"),"chat-overlay.png");
            world.getServer().runOnServer(s->{
                var p=s.getPlayerList().getPlayers().getFirst();var dispatcher=s.getCommands().getDispatcher();
                var denied=p.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
                if(dispatcher.getRoot().getChild("heal").getRequirement().test(denied))throw new AssertionError("Non-admin can discover /heal");
                try {dispatcher.execute("heal",denied);throw new AssertionError("Non-admin executed /heal");}
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
                var dirty=ServerMedicalSystem.get(p);dirty.damage(BodyPart.LEFT_ARM,8,DamageKind.BURN);
                dirty.treat(BodyPart.LEFT_ARM,TreatmentKind.FENTANYL,1);dirty.poison(1);dirty.irradiate(2);
                p.addEffect(new MobEffectInstance(MobEffects.POISON,600));p.setRemainingFireTicks(200);p.setTicksFrozen(100);
                p.getFoodData().setFoodLevel(5);p.getFoodData().setSaturation(0);
                try {if(dispatcher.execute("heal",p.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER))!=1)throw new AssertionError("Admin heal failed");}
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new AssertionError(e);}
                var b=ServerMedicalSystem.get(p).snapshot();
                if(b.mode()!=BodySnapshot.Mode.CONSCIOUS||b.blood()!=1||b.oxygen()!=1||b.brainHealth()!=1||b.energy()!=1||b.drug()!=0||b.pain()!=0||b.survival().toxin()!=0||b.survival().radiationGy()!=0)throw new AssertionError("Medical state not clean");
                for(var part:b.parts())if(part.integrity()!=1||part.bleeding()!=0||part.infection()!=0||part.burn()!=0||part.fractured()||part.foreignBody()||part.tourniquet())throw new AssertionError("Wound state not clean");
                if(p.getHealth()!=p.getMaxHealth()||p.hasEffect(MobEffects.POISON)||p.isOnFire()||p.getTicksFrozen()!=0||p.getFoodData().getFoodLevel()!=20)throw new AssertionError("Vanilla state not clean");
                System.out.println("REAL_ADMIN_HEAL: permission0_denied; op2_self_heal=clean_full_health");
            });
            context.waitFor(c->ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.CONSCIOUS&&ClientMedicalState.snapshot().drug()==0);
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitTicks(3);
            context.runOnClient(c->c.pauseGame(true));context.waitForScreen(PauseScreen.class);
            copy(context.takeScreenshot("healthy-pause-allowed"),"healthy-pause.png");
            System.out.println("REAL_CRITICAL_ESC_CHAT_HEAL_PASS; server_ticks_advanced="+(after-before));
        }
    }
    private static void copy(Path source,String name) {
        try {Path out=Path.of("verification/pause-heal");Files.createDirectories(out);Files.copy(source,out.resolve(name),StandardCopyOption.REPLACE_EXISTING);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
