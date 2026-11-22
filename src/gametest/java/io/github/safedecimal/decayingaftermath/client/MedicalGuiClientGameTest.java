package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.server.ServerBodyOwner;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Runs in a separate generated singleplayer test world; never edits the user's instance. */
public final class MedicalGuiClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(c->c.player!=null&&c.level!=null);
            world.getServer().runOnServer(s->{
                var p=s.getPlayerList().getPlayers().getFirst();var b=new BodyModel();b.reduceOxygen(.6f);
                ((ServerBodyOwner)(Object)p).decayingAftermath$setBody(b);
            });
            context.waitFor(c->ClientMedicalState.received()&&ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.UNCONSCIOUS);
            context.waitTicks(3);copy(context.takeScreenshot("medical-circle-world"),"circle-world.png");
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            context.waitTicks(3);copy(context.takeScreenshot("medical-circle-hidden-hud"),"circle-hidden-hud.png");
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            world.getServer().runOnServer(s->{
                var p=s.getPlayerList().getPlayers().getFirst();var base=new BodyModel().snapshot();
                var b=BodyModel.restore(new BodySnapshot(.1f,1,1,0,0,0,BodySnapshot.Mode.UNCONSCIOUS,0,.1f,0,
                        base.parts(),base.survival()).withCardiacTicks(-800));
                ((ServerBodyOwner)(Object)p).decayingAftermath$setBody(b);
            });
            context.waitFor(c->ClientMedicalState.snapshot().cardiacArrest());
            context.waitTicks(10);
            MedicalMusicSound cue=context.computeOnClient(c->{try {
                var f=MedicalClient.class.getDeclaredField("dyingMusic");f.setAccessible(true);return (MedicalMusicSound)f.get(null);
            }catch(Exception e){throw new IllegalStateException(e);}});
            if(cue==null)throw new AssertionError("Predeath cue did not start");
            context.runOnClient(c->c.getSoundManager().stop(cue));
            context.waitTicks(70);
            context.runOnClient(c->{
                if(c.getSoundManager().isActive(cue))throw new AssertionError("Completed predeath cue restarted");
                try {var f=MedicalClient.class.getDeclaredField("scoreMusic");f.setAccessible(true);
                    var score=(MedicalMusicSound)f.get(null);
                    if(score==null||!c.getSoundManager().isActive(score))throw new AssertionError("Main critical score channel missing");
                    if(!score.getIdentifier().getPath().equals("dying_drone")||score.isLooping())
                        throw new AssertionError("Wrong or looping main score");
                    System.out.println("REAL_AUDIO: predeath_active=false; score=dying_drone; score_active=true; score_looping=false");
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
            copy(context.takeScreenshot("medical-circle-arrest"),"circle-arrest.png");
            System.out.println("REAL_CLIENT_GUI_AND_PREDEATH_NO_REPLAY_PASS");
        }
    }
    private static void copy(Path source,String name) {
        try {
            Path out=Path.of(System.getProperty("medical.evidence", "verification/client-gui"));Files.createDirectories(out);
            Files.copy(source,out.resolve(name),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
