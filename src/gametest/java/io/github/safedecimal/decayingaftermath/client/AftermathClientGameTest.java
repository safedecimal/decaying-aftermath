package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.server.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;

/** Real generated-world death, input, music-channel, shader and expiry regression. */
public final class AftermathClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(c->c.player!=null&&c.level!=null&&c.gui.screen()==null&&ClientMedicalState.received());
            context.waitTicks(60);
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();System.out.println("REAL_BEFORE_KILL: hp="+p.getHealth()+";mode="+ServerMedicalSystem.get(p).snapshot().mode()+";gameMode="+p.gameMode());});
            world.getServer().runCommand("kill @a");
            context.waitTicks(4);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();System.out.println("REAL_AFTER_KILL: hp="+p.getHealth()+";mode="+ServerMedicalSystem.get(p).snapshot().mode()+";gameMode="+p.gameMode()+";afterlife="+ServerAftermathSystem.active(p));});
            context.waitFor(c->AftermathClient.active()&&c.player.isSpectator());
            context.waitFor(c->AftermathClient.currentMusic().equals("afterlife_lobby_time"));
            context.waitTicks(60);
            context.runOnClient(c->{
                if(c.gui.screen() instanceof DeathScreen||AftermathClient.depression())throw new AssertionError("Ordinary death branch failed");
                if(AftermathClient.remainingSeconds()<330||AftermathClient.remainingSeconds()>360)throw new AssertionError("Not six-minute server timer");
                if(AftermathClient.corpseCount()!=1)throw new AssertionError("No corpse without optional ragdoll mod");
                System.out.println("REAL_ORDINARY_DEATH: vanilla_screen=false;spectator=true;timer=360s;corpse=present;music=Lobby Time");
            });
            Vec3 before=context.computeOnClient(c->c.player.position());
            Vec3 cameraBefore=context.computeOnClient(c->c.gameRenderer.mainCamera().position());
            float yawBefore=context.computeOnClient(c->c.player.getYRot());
            float pitchBefore=context.computeOnClient(c->c.player.getXRot());
            context.getInput().moveCursor(160,90);context.waitTicks(5);
            context.runOnClient(c->{if(Math.abs(c.player.getYRot()-yawBefore)>.001f||Math.abs(c.player.getXRot()-pitchBefore)>.001f)throw new AssertionError("Mouse rotated player camera input");});
            context.getInput().holdKey(InputConstants.KEY_W);context.getInput().holdKey(InputConstants.KEY_SPACE);
            context.waitTicks(20);
            context.getInput().releaseKey(InputConstants.KEY_W);context.getInput().releaseKey(InputConstants.KEY_SPACE);
            context.runOnClient(c->{
                if(c.player.position().distanceToSqr(before)>.0025||!c.player.input.keyPresses.equals(Input.EMPTY))throw new AssertionError("Spectator input moved the player");
                if(c.gameRenderer.mainCamera().position().distanceToSqr(cameraBefore)<.001)throw new AssertionError("Camera did not move automatically");
                System.out.println("REAL_INPUT: MOUSE_WASD_SPACE=blocked;player_position=unchanged;camera=automatic");
            });
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.waitTicks(5);
            context.runOnClient(c->{if(c.gui.screen()!=null&&c.gui.screen().isPauseScreen())throw new AssertionError("Death scene paused");});
            context.runOnClient(c->{
                var observer=new net.minecraft.client.player.RemotePlayer(c.level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Observer"));
                observer.setId(-7777);observer.setPos(c.player.getX()+1.5,c.player.getY(),c.player.getZ()+.4);
                observer.setOldPosAndRot();c.level.addEntity(observer);
            });context.waitTicks(5);
            copy(context.takeScreenshot("afterlife-normal"),"normal-death.png");
            context.getInput().pressKey(InputConstants.KEY_F1);context.waitTicks(3);
            copy(context.takeScreenshot("afterlife-normal-f1"),"normal-death-f1.png");
            context.getInput().pressKey(InputConstants.KEY_F1);
            for(String expected:new String[]{"afterlife_airport_lounge","afterlife_bossa_antigua","afterlife_leopard_print_elevator","afterlife_george_street_shuffle","afterlife_lobby_time"}) {
                context.runOnClient(c->{try {
                    var field=AftermathClient.class.getDeclaredField("music");field.setAccessible(true);
                    ((MedicalMusicSound)field.get(null)).close();
                }catch(Exception error){throw new IllegalStateException(error);}});
                context.waitFor(c->AftermathClient.currentMusic().equals(expected));context.waitTicks(8);
                System.out.println("REAL_PLAYLIST_CHANNEL: "+expected);
            }
            System.out.println("REAL_PLAYLIST: five_tracks_in_order;wraps_back_to_Lobby_Time=true");
            world.getServer().runOnServer(s->expire(s.getPlayerList().getPlayers().getFirst()));
            context.waitFor(c->!AftermathClient.active()&&!c.player.isSpectator());
            System.out.println("REAL_EXPIRED: spectator=false;camera_and_music_restored=true");
            context.waitTicks(35);
            world.getServer().runOnServer(s->{
                var player=s.getPlayerList().getPlayers().getFirst();var normal=new BodyModel().snapshot();var parts=normal.parts();
                for(BodyPart part:BodyPart.values())parts[part.ordinal()]=new BodySnapshot.PartSnapshot(1,0,.95f,false,false,false,false);
                var survival=new SurvivalSnapshot(.05f,.05f,37,1,.9f,1,1,0,0,0,0,0,1,0,.9f);
                var depressed=BodyModel.restore(new BodySnapshot(1,1,1,.95f,0,72,BodySnapshot.Mode.CONSCIOUS,1,.05f,1,parts,survival));
                ((ServerBodyOwner)(Object)player).decayingAftermath$setBody(depressed);
                float mood=MedicalPhysiology.mood(depressed.snapshot())*100;
                if(mood>=-75)throw new AssertionError("Low-mood fixture not below -75: "+mood);
                System.out.println("REAL_MOOD_BEFORE_SAME_KILL: "+mood);
                // Exercise the same native generic_kill death event directly, independent of respawn grace.
                var source=player.damageSources().genericKill();
                ServerMedicalSystem.applyDamage(player,source,1000);
                if(ServerMedicalSystem.get(player).mode()!=BodySnapshot.Mode.DEAD)throw new AssertionError("Severe-death fixture was rescued unexpectedly");
                player.die(source);
            });
            context.waitFor(c->AftermathClient.active()&&AftermathClient.depression()&&c.player.isSpectator());
            context.waitFor(c->AftermathClient.currentMusic().equals("afterlife_depression"));context.waitTicks(65);
            context.runOnClient(c->{
                if(c.gui.screen() instanceof DeathScreen)throw new AssertionError("Depression death opened vanilla screen");
                if(!c.gameRenderer.getRequestedPostEffects().contains(MedicalClient.id("afterlife_monochrome")))throw new AssertionError("Monochrome effect missing");
                if(AftermathClient.redactedPlayerCount()<1)throw new AssertionError("No other-player redaction fixture");
                System.out.println("REAL_LOW_MOOD_DEATH: same_kill_cause;depression=true;timer_hidden=true;Numb=bundled;monochrome=requested;players_redacted=true;drifting_thoughts=true");
            });
            copy(context.takeScreenshot("afterlife-depression"),"depression-death.png");
            world.getServer().runOnServer(s->expire(s.getPlayerList().getPlayers().getFirst()));
            context.waitFor(c->!AftermathClient.active()&&!c.player.isSpectator());
            System.out.println("REAL_AFTERMATH_ALL_PASS");
        }
    }
    private static void expire(net.minecraft.server.level.ServerPlayer player) {
        try {
            var field=ServerAftermathSystem.class.getDeclaredField("SESSIONS");field.setAccessible(true);
            Object session=((Map<?,?>)field.get(null)).get(player.getUUID());
            var remaining=session.getClass().getDeclaredField("remainingTicks");remaining.setAccessible(true);remaining.setInt(session,1);
        } catch(Exception error){throw new IllegalStateException(error);}
    }
    private static void copy(Path source,String name) {
        try {
            Path out=Path.of(System.getProperty("medical.evidence","verification/client-gui"));Files.createDirectories(out);
            Files.copy(source,out.resolve(name),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
