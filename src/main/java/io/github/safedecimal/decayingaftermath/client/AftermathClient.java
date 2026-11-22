package io.github.safedecimal.decayingaftermath.client;

import com.mojang.authlib.GameProfile;
import io.github.safedecimal.decayingaftermath.compat.ClientMedicalRagdolls;
import io.github.safedecimal.decayingaftermath.medical.AftermathPolicy;
import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.network.AftermathPayload;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Death presentation survives the temporary server respawn and never consumes user camera input. */
public final class AftermathClient {
    private static final List<String> TITLES=List.of("Lobby Time", "Airport Lounge", "Bossa Antigua",
            "Leopard Print Elevator", "George Street Shuffle");
    private static final Style TIMER_FONT=Style.EMPTY.withFont(new FontDescription.Resource(MedicalClient.id("aftermath_thin")));
    private static AftermathPayload state;
    private static boolean supported;
    private static long beganNanos, syncedNanos, musicNanos;
    private static UUID victim;
    private static CameraType previousCamera;
    private static AftermathCorpse corpse;
    private static Object corpseLevel;
    private static MedicalMusicSound music;
    private static int track;
    private static int recoveryTicks;
    private AftermathClient() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AftermathPayload.TYPE,
                (payload,context)->context.client().execute(()->receive(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(AftermathClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{reset(client);supported=false;});
    }
    private static void receive(AftermathPayload payload) {
        Minecraft client=Minecraft.getInstance();
        supported=true;
        if (!payload.active()) {boolean completed=state!=null;reset(client);if(completed)recoveryTicks=40;return;}
        if (!Double.isFinite(payload.x()) || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z())
                || !Float.isFinite(payload.yaw()) || payload.remainingTicks()<0 || payload.remainingTicks()>72000) return;
        if (state==null || state.token()!=payload.token()) {
            reset(client); beganNanos=System.nanoTime(); track=0;
            if (client.player!=null) victim=client.player.getUUID();
            previousCamera=client.options.getCameraType(); client.options.setCameraType(CameraType.FIRST_PERSON);
            client.getMusicManager().stopPlaying();
        }
        state=payload; syncedNanos=System.nanoTime();
        if (client.gui.screen() instanceof DeathScreen) client.gui.setScreen(null);
    }
    public static boolean active() {
        var client=Minecraft.getInstance(); return state!=null && client.player!=null && client.level!=null;
    }
    public static boolean depression() { return active() && state.depression(); }
    public static boolean locked() {
        var client=Minecraft.getInstance();
        return active();
    }
    public static boolean suppressDeathScreen() { return supported || active(); }
    public static double elapsedSeconds() { return Math.max(0,(System.nanoTime()-beganNanos)/1_000_000_000.0); }
    public static double remainingSeconds() {
        return state==null ? 0 : Math.max(0,state.remainingTicks()/20d-(System.nanoTime()-syncedNanos)/1_000_000_000.0);
    }
    public static String currentMusic() {
        return music==null ? "none" : music.getIdentifier().getPath();
    }
    public static int corpseCount() { return corpse==null ? 0 : 1; }
    public static int nativeCorpseCount() {
        return active() && victim!=null && ClientMedicalRagdolls.pinDeath(state.corpseId(),victim) ? 1 : 0;
    }
    public static long redactedPlayerCount() {
        if (!depression()) return 0;
        var client=Minecraft.getInstance();
        return client.level.players().stream().filter(player->player!=client.player && !(player instanceof AftermathCorpse)).count();
    }
    private static Vec3 anchor() {
        Vec3 nativeBody=victim==null ? null : ClientMedicalRagdolls.deathOrigin(state.corpseId(),victim,BodyPart.TORSO);
        return nativeBody!=null ? nativeBody : new Vec3(state.x(),state.y(),state.z());
    }
    private static void tick(Minecraft client) {
        if (client.player==null || client.level==null) { if (state!=null) reset(client); return; }
        if(recoveryTicks>0) {
            recoveryTicks--;
            if(!active()&&ClientMedicalState.received()&&!client.player.isAlive()
                    &&ClientMedicalState.snapshot().mode()!=io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode.DEAD)
                client.player.setHealth(Math.max(1,client.player.getMaxHealth()*ClientMedicalState.snapshot().condition()));
        }
        if (!active()) return;
        if (victim==null) victim=client.player.getUUID();
        // Camera ownership is distinct from its cinematic position. Respawn replaces LocalPlayer.
        client.setCameraEntity(client.player);
        client.player.setDeltaMovement(Vec3.ZERO); client.player.setSprinting(false);
        if (client.player.input!=null) client.player.input.keyPresses=Input.EMPTY;
        if (client.gui.screen()!=null && (client.gui.screen() instanceof DeathScreen || client.gui.screen().isPauseScreen()))
            client.gui.setScreen(null);
        maintainCorpse(client);
        long now=System.nanoTime(); double seconds=musicNanos==0 ? .05 : Math.clamp((now-musicNanos)/1_000_000_000.0,0,.25);
        musicNanos=now;
        if (music==null) music=MedicalMusicSound.start(state.depression() ? "afterlife_depression" : AftermathPolicy.PLAYLIST.get(track),
                state.depression(),.64f);
        if (music.finished(seconds)) {
            music.close(); music=null;
            if (!state.depression()) track=AftermathPolicy.nextTrack(track);
        }
    }
    private static void maintainCorpse(Minecraft client) {
        boolean nativeBody=ClientMedicalRagdolls.pinDeath(state.corpseId(),victim);
        if (corpseLevel!=client.level) { corpse=null; corpseLevel=client.level; }
        // A streamed native body may still be queued on the physics thread. Never show
        // a duplicate flat player while Ragdollified is present.
        if (nativeBody || io.github.safedecimal.decayingaftermath.compat.OptionalMedicalCompatibility.ragdollifiedSupported()) {
            removeCorpse(client); return;
        }
        if (corpse!=null && !corpse.isRemoved()) return;
        UUID profile=UUID.nameUUIDFromBytes((victim+"/afterlife/"+state.token()).getBytes(StandardCharsets.UTF_8));
        corpse=new AftermathCorpse(client.level,new GameProfile(profile,"Aftermath"),client.player.getSkin(),
                new Vec3(state.x(),state.y(),state.z()),state.yaw());
        int id=-20_000_000-(int)(state.token()&0xffff);
        while (client.level.getEntity(id)!=null) id--;
        corpse.setId(id); client.level.addEntity(corpse);
    }
    private static void removeCorpse(Minecraft client) {
        if (corpse!=null && corpseLevel==client.level && client.level!=null)
            client.level.removeEntity(corpse.getId(),Entity.RemovalReason.DISCARDED);
        corpse=null;
    }
    public static AftermathPolicy.Shot cameraShot(Minecraft client) {
        Vec3 anchor=anchor();
        var raw=AftermathPolicy.camera(new AftermathPolicy.Point(anchor.x,anchor.y,anchor.z),state.yaw(),elapsedSeconds());
        Vec3 desired=new Vec3(raw.position().x(),raw.position().y(),raw.position().z());
        Vec3 target=new Vec3(raw.target().x(),raw.target().y(),raw.target().z());
        // Keep the orbit in the world, not inside the nearest wall or ceiling.
        var hit=client.level.clip(new ClipContext(target,desired,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        if (hit.getType()!=HitResult.Type.MISS) desired=hit.getLocation().add(target.subtract(desired).normalize().scale(.18));
        Vec3 look=target.subtract(desired);
        return new AftermathPolicy.Shot(new AftermathPolicy.Point(desired.x,desired.y,desired.z),raw.target(),
                (float)Math.toDegrees(Math.atan2(-look.x,look.z)),
                (float)-Math.toDegrees(Math.atan2(look.y,Math.hypot(look.x,look.z))));
    }
    public static void extractOverlay(GuiGraphicsExtractor graphics) {
        if (!active()) return;
        var client=Minecraft.getInstance(); double seconds=elapsedSeconds();
        int width=graphics.guiWidth(),height=graphics.guiHeight();
        if (state.depression()) {
            extractRedactions(graphics,client);
            graphics.fill(0,0,width,height,0x200A0A0A);
            for (int lane=0;lane<5;lane++) {
                var thought=AftermathPolicy.thought(lane,seconds);
                graphics.pose().pushMatrix(); graphics.pose().translate((float)(thought.x()*width),(float)(thought.y()*height));
                graphics.pose().scale((float)thought.scale(),(float)thought.scale());
                graphics.text(client.font,Component.translatable("decaying_aftermath.afterlife.thought."+thought.index())
                        .withStyle(Style.EMPTY.withItalic(true)),0,0,alpha(thought.opacity(),0xC4C4C4),false);
                graphics.pose().popMatrix();
            }
        } else {
            double opacity=AftermathPolicy.timerOpacity(seconds);
            Component clock=Component.literal(AftermathPolicy.timerText(remainingSeconds())).withStyle(TIMER_FONT);
            float xScale=Math.min(9f,width*.58f/Math.max(1,client.font.width(clock)));
            float yScale=Math.min(xScale*.70f,height*.22f/9f);
            graphics.pose().pushMatrix(); graphics.pose().translate(width/2f,height/2f);
            graphics.pose().scale(xScale,yScale);
            graphics.text(client.font,clock,-client.font.width(clock)/2,-4,alpha(opacity,0xFFFFFF),false);
            graphics.pose().popMatrix();
            graphics.text(client.font,TITLES.get(track)+" · Kevin MacLeod",12,height-18,alpha(opacity*.48,0xFFFFFF),false);
        }
        // The world gently appears while the camera leaves the body; HUD is independent of F1/chat.
        double fade=Math.clamp(1-seconds/2.4,0,1);
        if (fade>0) graphics.fill(0,0,width,height,alpha(fade,0));
    }
    private static int alpha(double opacity,int rgb) {
        return ((int)Math.round(Math.clamp(opacity,0,1)*255)<<24)|rgb;
    }
    private static void extractRedactions(GuiGraphicsExtractor graphics,Minecraft client) {
        Camera camera=client.gameRenderer.mainCamera(); Matrix4f matrix=camera.getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 origin=camera.position();
        for (var player : client.level.players()) {
            if (player==client.player || player instanceof AftermathCorpse) continue;
            obscure(graphics,matrix,origin,player.getBoundingBox().inflate(.20,.35,.20),false);
        }
        Vec3 body=anchor();
        obscure(graphics,matrix,origin,new AABB(body.x-1.1,body.y-.05,body.z-1.1,
                body.x+1.1,body.y+.65,body.z+1.1),true);
    }
    private static void obscure(GuiGraphicsExtractor graphics,Matrix4f matrix,Vec3 origin,AABB box,boolean body) {
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for (int corner=0;corner<8;corner++) {
            var point=new Vector4f((float)(((corner&1)==0?box.minX:box.maxX)-origin.x),
                    (float)(((corner&2)==0?box.minY:box.maxY)-origin.y),
                    (float)(((corner&4)==0?box.minZ:box.maxZ)-origin.z),1).mul(matrix);
            if (point.w<=.05) continue;
            float x=(point.x/point.w+1)*graphics.guiWidth()/2f,y=(1-point.y/point.w)*graphics.guiHeight()/2f;
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
        }
        if (!Float.isFinite(minX) || maxX<0 || maxY<0 || minX>graphics.guiWidth() || minY>graphics.guiHeight()) return;
        int x=(int)Math.floor(Math.clamp(minX,-graphics.guiWidth(),graphics.guiWidth()));
        int y=(int)Math.floor(Math.clamp(minY,-graphics.guiHeight(),graphics.guiHeight()));
        int width=(int)Math.ceil(Math.clamp(maxX-minX,1,graphics.guiWidth()*2));
        int height=(int)Math.ceil(Math.clamp(maxY-minY,1,graphics.guiHeight()*2));
        graphics.blit(RenderPipelines.GUI_TEXTURED,MedicalClient.id("textures/gui/afterlife/"+(body?"body_smear":"figure_smear")+".png"),
                x,y,0f,0f,width,height,128,128,128,128,0xFFFFFFFF);
    }
    private static void reset(Minecraft client) {
        if (music!=null) music.close(); music=null;
        if (state!=null && victim!=null) {ClientMedicalRagdolls.unpinDeath(state.corpseId(),victim);ClientMedicalRagdolls.detachDeathCamera();}
        removeCorpse(client); corpseLevel=null;
        if (previousCamera!=null) client.options.setCameraType(previousCamera);
        if(client.player!=null) client.setCameraEntity(client.player);
        previousCamera=null;state=null;victim=null;beganNanos=syncedNanos=musicNanos=0;track=0;recoveryTicks=0;
    }
}
