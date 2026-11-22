package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.util.HashSet;
import java.util.Set;

/** One foreground score at a time. A finished cue is never rearmed by an unchanged condition. */
public final class MedicalScoreState {
    public record Action(String cue,boolean start,boolean stop,float volume) {}
    private final Set<String> played=new HashSet<>();
    private String selected;
    private int quietTicks;
    public Action advance(BodySnapshot body,boolean suppressed) {
        var levels=MedicalSituationSoundState.volumes(body);
        if(levels.silent()) {if(++quietTicks>=40)played.clear();} else quietTicks=0;
        String cue=suppressed?null:levels.dying()>0?"dying_drone":levels.pain()>0?"pain_drone"
                :levels.tired()>0?"tired_drone":levels.exhausted()>0?"exhausted_drone":null;
        boolean changed=!java.util.Objects.equals(cue,selected);
        boolean start=changed&&cue!=null&&played.add(cue),stop=changed&&selected!=null;
        selected=cue;
        float volume="exhausted_drone".equals(cue)||"tired_drone".equals(cue)?.12f:.7f;
        return new Action(cue,start,stop,volume);
    }
    public void reset() {played.clear();selected=null;quietTicks=0;}
}
