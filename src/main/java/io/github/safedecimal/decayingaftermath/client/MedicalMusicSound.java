package io.github.safedecimal.decayingaftermath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Exclusive owned CU music cues; respects Minecraft's music slider, unlike procedural UI sounds. */
public final class MedicalMusicSound extends AbstractTickableSoundInstance {
    private double ageSeconds;
    private boolean observedPlaying;
    private MedicalMusicSound(String name,boolean loop) {
        super(SoundEvent.createVariableRangeEvent(MedicalClient.id(name)),SoundSource.MUSIC,RandomSource.create());
        looping=loop;relative=true;attenuation=Attenuation.NONE;delay=0;
        volume=loop?0:.8f;pitch=1;
    }
    public static MedicalMusicSound start(String name,boolean loop) {
        return start(name,loop,loop?0:.8f);
    }
    public static MedicalMusicSound start(String name,boolean loop,float initialVolume) {
        var sound=new MedicalMusicSound(name,loop);
        sound.volume=Float.isFinite(initialVolume)?Math.clamp(initialVolume,0,1):0;
        Minecraft.getInstance().getSoundManager().play(sound);return sound;
    }
    public void fadeTo(float target,double seconds) {
        float amount=(float)Math.max(0,seconds*.5);
        volume+=Math.copySign(Math.min(Math.abs(target-volume),amount),target-volume);
    }
    public void close() {stop();Minecraft.getInstance().getSoundManager().stop(this);}
    /** Observe completion, never replay a nonlooping cue after natural EOF or a stopped channel. */
    public boolean finished(double seconds) {
        var manager=Minecraft.getInstance().getSoundManager();
        ageSeconds+=Double.isFinite(seconds)?Math.clamp(seconds,0,.25):0;
        if(manager.isActive(this)) {observedPlaying=true;return false;}
        return isStopped()||observedPlaying||ageSeconds>=5;
    }
    @Override public void tick() { }
    @Override public boolean canStartSilent() {return true;}
}
