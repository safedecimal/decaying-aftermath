package io.github.safedecimal.decayingaftermath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Exclusive owned CU music cues; respects Minecraft's music slider, unlike procedural UI sounds. */
public final class MedicalMusicSound extends AbstractTickableSoundInstance {
    private MedicalMusicSound(String name,boolean loop) {
        super(SoundEvent.createVariableRangeEvent(MedicalClient.id(name)),SoundSource.MUSIC,RandomSource.create());
        looping=loop;relative=true;attenuation=Attenuation.NONE;delay=0;
        volume=loop?0:.8f;pitch=1;
    }
    public static MedicalMusicSound start(String name,boolean loop) {
        var sound=new MedicalMusicSound(name,loop);Minecraft.getInstance().getSoundManager().play(sound);return sound;
    }
    public void fadeTo(float target,double seconds) {
        float amount=(float)Math.max(0,seconds*.5);
        volume+=Math.copySign(Math.min(Math.abs(target-volume),amount),target-volume);
    }
    public void close() {stop();Minecraft.getInstance().getSoundManager().stop(this);}
    @Override public void tick() { }
    @Override public boolean canStartSilent() {return true;}
}
