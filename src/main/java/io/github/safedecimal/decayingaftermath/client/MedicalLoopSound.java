package io.github.safedecimal.decayingaftermath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Owner-controlled AudioSource equivalent: one actual loop, mutable volume/pitch, explicit cleanup. */
public final class MedicalLoopSound extends AbstractTickableSoundInstance {
    private double inactiveSeconds;
    private MedicalLoopSound(String name, float volume, float pitch) {
        super(SoundEvent.createVariableRangeEvent(MedicalClient.id(name)), SoundSource.MASTER, RandomSource.create());
        looping = true; relative = true; attenuation = Attenuation.NONE; delay = 0;
        set(volume, pitch);
    }
    public static MedicalLoopSound start(String name, float volume, float pitch) {
        var sound = new MedicalLoopSound(name, volume, pitch);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }
    public void set(float volume, float pitch) {
        this.volume = Math.clamp(Float.isFinite(volume) ? volume : 0, 0, 1);
        this.pitch = Math.clamp(Float.isFinite(pitch) ? pitch : 1, .1f, 2);
    }
    public void fadeTo(float target, double seconds) {
        float amount = (float) Math.max(0, seconds * .5);
        set(volume + Math.copySign(Math.min(Math.abs(target - volume), amount), target - volume), pitch);
        var manager=Minecraft.getInstance().getSoundManager();
        inactiveSeconds=volume>.001f&&!isStopped()&&!manager.isActive(this)?inactiveSeconds+Math.min(.25,Math.max(0,seconds)):0;
        if(inactiveSeconds>=2) {manager.play(this);inactiveSeconds=0;}
    }
    public float volume() { return volume; }
    public void close() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
    @Override public void tick() { /* The owning screen/client updates volume and lifetime. */ }
    @Override public boolean canStartSilent() { return true; }
}
