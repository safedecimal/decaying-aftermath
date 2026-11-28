package io.github.safedecimal.decayingaftermath.network;

import io.github.safedecimal.decayingaftermath.medical.DefibGame;
import io.github.safedecimal.decayingaftermath.medical.DefibrillatorItem;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Only a real server-side item interaction selects a patient. Packets never carry a target ID. */
public final class DefibNetwork {
    public static final int OPEN=0, PROGRESS=1, MISS=2, DONE=3, ABORT=4, NO_EFFECT=5, HARM=6;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static int nextId;
    private DefibNetwork() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE,State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Input.TYPE,Input.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Apply.TYPE,Apply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Apply.TYPE,(p,c)->c.server().execute(()->applyFromInventory(c.player(),p)));
        ServerPlayNetworking.registerGlobalReceiver(Input.TYPE,(p,c)->c.server().execute(()->input(c.player(),p)));
        ServerTickEvents.END_SERVER_TICK.register(server->SESSIONS.values().removeIf(s->{
            if(!valid(s)) {reply(s,ABORT);return true;}
            long now=s.actor.level().getGameTime();
            var body=ServerMedicalSystem.get(s.patient).snapshot();
            var action=s.game.tick(now,io.github.safedecimal.decayingaftermath.medical.CardiacRhythm.shockable(body.cardiacTicks()));
            int result=action==DefibGame.Result.SHOCK?shock(s):PROGRESS;
            persist(s);
            if(now%2==0||action==DefibGame.Result.SHOCK)reply(s,result);
            if(s.game.battery()<=0) {reply(s,ABORT);return true;}
            return false;
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SESSIONS.remove(handler.getPlayer().getUUID()));
    }
    public static void open(ServerPlayer actor,ServerPlayer patient,InteractionHand hand,boolean automatic) {
        start(actor,patient,hand,-1,actor.getItemInHand(hand),automatic);
    }
    public static void cancelFor(ServerPlayer player) {
        SESSIONS.values().removeIf(s->{
            if(s.actor!=player&&s.patient!=player)return false;
            reply(s,ABORT);return true;
        });
    }
    /** Inventory drag onto the torso: the device stays in its slot and the actor treats themselves. */
    private static void applyFromInventory(ServerPlayer actor,Apply packet) {
        int slot=packet.slot();
        if(slot<9 || slot>=actor.inventoryMenu.slots.size() || actor.containerMenu!=actor.inventoryMenu
                || !actor.inventoryMenu.getCarried().isEmpty())return;
        ItemStack stack=actor.inventoryMenu.getSlot(slot).getItem();
        if(!(stack.getItem() instanceof DefibrillatorItem item))return;
        start(actor,actor,InteractionHand.MAIN_HAND,slot,stack,item.automatic());
    }
    private static void start(ServerPlayer actor,ServerPlayer patient,InteractionHand hand,int slot,ItemStack device,boolean automatic) {
        if(!ServerPlayNetworking.canSend(actor,State.TYPE) || actor.isSpectator() || !ServerMedicalSystem.canAct(actor))return;
        if(!eligible(actor,patient)) {
            MedicalNetwork.thought(actor,"abjection.defib.not_needed");
            return;
        }
        var previous=SESSIONS.get(actor.getUUID());
        if(previous!=null && valid(previous))return;
        float battery=actor.getAbilities().instabuild?1:DefibrillatorItem.battery(device);
        if(battery<=(automatic?.02f:0)) {MedicalNetwork.thought(actor,"abjection.defib.empty_battery");return;}
        int seed=actor.getRandom().nextInt();
        var session=new Session(actor,patient,hand,slot,device,++nextId,automatic,
                actor.level().getGameTime(),new DefibGame(automatic,seed,actor.level().getGameTime(),battery));
        SESSIONS.put(actor.getUUID(),session);
        persist(session);
        reply(session,OPEN);
    }
    private static boolean eligible(ServerPlayer actor,ServerPlayer target) {
        return actor.isAlive() && target.isAlive() && !target.isRemoved() && actor.level()==target.level()
                && (actor==target || actor.distanceToSqr(target)<=16 && actor.hasLineOfSight(target))
                && ServerMedicalSystem.get(target).canDefibrillate();
    }
    private static boolean valid(Session s) {
        long elapsed=s.actor.level().getGameTime()-s.started;
        return elapsed>=0 && !s.actor.isRemoved() && !s.actor.isSpectator()
                && s.actor.containerMenu==s.actor.inventoryMenu && source(s)==s.device && !s.device.isEmpty()
                && ServerMedicalSystem.canAct(s.actor) && eligible(s.actor,s.patient);
    }
    private static ItemStack source(Session s) {
        return s.slot<0 ? s.actor.getItemInHand(s.hand) : s.actor.inventoryMenu.getSlot(s.slot).getItem();
    }
    private static void input(ServerPlayer actor,Input packet) {
        Session s=SESSIONS.get(actor.getUUID());
        if(s==null || s.actor!=actor || s.id!=packet.session())return;
        if(packet.control()==DefibGame.CANCEL) {SESSIONS.remove(actor.getUUID());return;}
        if(!valid(s)) {SESSIONS.remove(actor.getUUID());reply(s,ABORT);return;}
        int result=switch(s.game.input(packet.control(),packet.x(),packet.y(),actor.level().getGameTime())) {
            case MISS -> MISS;
            case PROGRESS -> PROGRESS;
            case SHOCK -> shock(s);
        };
        persist(s);reply(s,result);
    }
    private static int shock(Session s) {
        var result=ServerMedicalSystem.defibrillate(s.patient,s.automatic,s.game.lastShockCharge(),s.actor.getRandom().nextFloat());
        return switch(result) {
            case REJECTED -> ABORT;
            case BLOCKED,NO_CONVERSION -> NO_EFFECT;
            case CONVERTED -> DONE;
            case ARRHYTHMIA,ARREST -> HARM;
        };
    }
    private static void persist(Session s) {
        if(!s.actor.getAbilities().instabuild)DefibrillatorItem.battery(s.device,s.game.battery());
        if(s.slot>=0)s.actor.inventoryMenu.getSlot(s.slot).setChanged();
        s.actor.inventoryMenu.broadcastChanges();
    }
    private static void reply(Session s,int result) {
        if(s.actor.isRemoved() || !ServerPlayNetworking.canSend(s.actor,State.TYPE))return;
        var body=ServerMedicalSystem.get(s.patient).snapshot();float severity=DefibGame.fibrillation(body.cardiacTicks());
        boolean left=(s.actor.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^(s.hand==InteractionHand.OFF_HAND);
        ServerPlayNetworking.send(s.actor,new State(s.id,result,s.automatic,s.game.stage().ordinal(),s.game.held(),
                s.actor.level().getGameTime(),s.game.stageAt(),s.game.shocks(),s.game.currentCharge(),s.game.desiredCharge(),
                s.actor.getAbilities().instabuild?1:s.game.battery(),s.game.padX(0),s.game.padY(0),s.game.padX(1),s.game.padY(1),
                body.bpm(),body.oxygen(),body.cardiacTicks(),s.game.estimateLow(severity),s.game.estimateHigh(severity),left));
    }
    private record Session(ServerPlayer actor,ServerPlayer patient,InteractionHand hand,int slot,ItemStack device,int id,
                           boolean automatic,long started,DefibGame game) {}

    public record State(int session,int result,boolean automatic,int stage,int held,long serverTick,long stageAt,int shocks,
                        float currentCharge,float desiredCharge,float battery,float pad0X,float pad0Y,float pad1X,float pad1Y,
                        float bpm,float oxygen,int cardiacTicks,int estimateLow,int estimateHigh,boolean leftHand) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(Identifier.fromNamespaceAndPath("abjection","cu_defib_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=CustomPacketPayload.codec(State::write,State::read);
        public Type<State> type(){return TYPE;}
        private void write(RegistryFriendlyByteBuf b) {
            b.writeInt(session);b.writeByte(result);b.writeBoolean(automatic);b.writeByte(stage);b.writeByte(held);
            b.writeLong(serverTick);b.writeLong(stageAt);b.writeInt(shocks);b.writeFloat(currentCharge);b.writeFloat(desiredCharge);
            b.writeFloat(battery);b.writeFloat(pad0X);b.writeFloat(pad0Y);b.writeFloat(pad1X);b.writeFloat(pad1Y);
            b.writeFloat(bpm);b.writeFloat(oxygen);b.writeInt(cardiacTicks);b.writeInt(estimateLow);b.writeInt(estimateHigh);b.writeBoolean(leftHand);
        }
        private static State read(RegistryFriendlyByteBuf b) {
            return new State(b.readInt(),b.readUnsignedByte(),b.readBoolean(),b.readUnsignedByte(),b.readByte(),
                    b.readLong(),b.readLong(),b.readInt(),b.readFloat(),b.readFloat(),b.readFloat(),
                    b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readInt(),b.readInt(),b.readInt(),b.readBoolean());
        }
    }
    /** Carries only an inventory slot of the sender; the patient is always the sender. */
    public record Apply(int slot) implements CustomPacketPayload {
        public static final Type<Apply> TYPE=new Type<>(Identifier.fromNamespaceAndPath("abjection","defib_apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Apply> CODEC=CustomPacketPayload.codec(Apply::write,Apply::read);
        public Type<Apply> type(){return TYPE;}
        private void write(RegistryFriendlyByteBuf b){b.writeVarInt(slot);}
        private static Apply read(RegistryFriendlyByteBuf b){return new Apply(b.readVarInt());}
    }
    public record Input(int session,int control,float x,float y) implements CustomPacketPayload {
        public Input {
            if(control<0||control>DefibGame.SHOCK||!DefibGame.validPoint(x,y))throw new IllegalArgumentException("Invalid CU defibrillator input");
        }
        public static final Type<Input> TYPE=new Type<>(Identifier.fromNamespaceAndPath("abjection","cu_defib_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=CustomPacketPayload.codec(Input::write,Input::read);
        public Type<Input> type(){return TYPE;}
        private void write(RegistryFriendlyByteBuf b){b.writeInt(session);b.writeByte(control);b.writeFloat(x);b.writeFloat(y);}
        private static Input read(RegistryFriendlyByteBuf b){return new Input(b.readInt(),b.readUnsignedByte(),b.readFloat(),b.readFloat());}
    }
}
