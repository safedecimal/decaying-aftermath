package io.github.safedecimal.decayingaftermath.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Local, non-interactive fallback when an optional native death ragdoll is unavailable. */
public final class AftermathCorpse extends RemotePlayer {
    private final PlayerSkin skin;
    private final Direction orientation;
    public AftermathCorpse(ClientLevel level, GameProfile profile, PlayerSkin skin, Vec3 anchor, float yaw) {
        super(level,profile);
        this.skin=skin; orientation=Direction.fromYRot(yaw);
        setPos(anchor.x,anchor.y+.05,anchor.z); setYRot(yaw); yBodyRot=yaw; yHeadRot=yaw;
        setPose(Pose.SLEEPING); setHealth(0); setOldPosAndRot();
    }
    @Override public void tick() { }
    @Override public boolean isAlive() { return false; }
    @Override public boolean isSleeping() { return true; }
    @Override public Direction getBedOrientation() { return orientation; }
    @Override public GameType gameMode() { return GameType.SURVIVAL; }
    @Override public PlayerSkin getSkin() { return skin; }
    @Override public boolean shouldShowName() { return false; }
}
