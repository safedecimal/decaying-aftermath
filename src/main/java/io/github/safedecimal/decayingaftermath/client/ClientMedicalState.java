package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

public final class ClientMedicalState {
    private static volatile BodySnapshot state=new BodyModel().snapshot();
    private static volatile boolean received;
    private ClientMedicalState() {}
    public static BodySnapshot snapshot() { return state; }
    public static boolean received() { return received; }
    public static void reset() { state=new BodyModel().snapshot();received=false; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BodyPayload.TYPE,(p,c)->c.client().execute(()->{state=p.snapshot();received=true;}));
        ClientPlayNetworking.registerGlobalReceiver(ThoughtPayload.TYPE,(p,c)->c.client().execute(()->{
            if(p.message().equals("abjection.heal.done"))MedicalClient.adminHealConfirmed();
            else ThoughtTyper.enqueue(Component.translatable(p.message()).getString());
        }));
    }
    public static void tryStandUp() {
        if (ClientPlayNetworking.canSend(StandUpPayload.TYPE)) ClientPlayNetworking.send(StandUpPayload.INSTANCE);
    }
    public static void reportCaveSound() {
        if (ClientPlayNetworking.canSend(CaveSoundPayload.TYPE)) ClientPlayNetworking.send(CaveSoundPayload.INSTANCE);
    }
    public static void startSleep() {
        if (ClientPlayNetworking.canSend(SleepPayload.TYPE)) ClientPlayNetworking.send(new SleepPayload(false));
    }
    public static void wakeFromSleep() {
        if (ClientPlayNetworking.canSend(SleepPayload.TYPE)) ClientPlayNetworking.send(new SleepPayload(true));
    }
    public static void beginProcedure(int slot,BodyPart part,TreatmentKind kind) { send(MedicalActionPayload.BEGIN,slot,part,kind,0); }
    public static void sendTreatment(int slot,BodyPart part,TreatmentKind kind,float amount) { send(MedicalActionPayload.APPLY,slot,part,kind,amount); }
    public static void finishProcedure(int slot,BodyPart part,TreatmentKind kind) { send(MedicalActionPayload.FINISH,slot,part,kind,0); }
    public static void removeTourniquet(BodyPart part){send(MedicalActionPayload.REMOVE_TOURNIQUET,0,part,TreatmentKind.TOURNIQUET,0);}
    public static void removeSplint(BodyPart part){send(MedicalActionPayload.REMOVE_SPLINT,0,part,TreatmentKind.SPLINT,0);}
    /** Self-defibrillation from an inventory slot dragged onto the torso. */
    public static void applyDefibrillator(int slot) {
        if (ClientPlayNetworking.canSend(DefibNetwork.Apply.TYPE)) ClientPlayNetworking.send(new DefibNetwork.Apply(slot));
    }
    public static void endProcedure() {
        if (ClientPlayNetworking.canSend(MedicalActionPayload.TYPE)) ClientPlayNetworking.send(new MedicalActionPayload(MedicalActionPayload.END,0,0,0,0));
    }
    private static void send(int action,int slot,BodyPart part,TreatmentKind kind,float amount) {
        if (ClientPlayNetworking.canSend(MedicalActionPayload.TYPE)) ClientPlayNetworking.send(new MedicalActionPayload(action,slot,part.ordinal(),kind.ordinal(),amount));
    }
}
