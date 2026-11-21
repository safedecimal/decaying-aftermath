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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Only a real server-side item interaction selects a patient. Packets never carry a target ID. */
public final class DefibNetwork {
    public static final int OPEN=0, PROGRESS=1, MISS=2, DONE=3, ABORT=4;
    private static final int LIMIT=600;
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
            if(!s.game.autoReady(s.actor.level().getGameTime()))return false;
            shock(s);return true;
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SESSIONS.remove(handler.getPlayer().getUUID()));
    }
    public static void open(ServerPlayer actor,ServerPlayer patient,InteractionHand hand,boolean automatic) {
        start(actor,patient,hand,-1,actor.getItemInHand(hand),automatic);
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
            MedicalNetwork.thought(actor,"decaying_aftermath.defib.not_needed");
            return;
        }
        var previous=SESSIONS.get(actor.getUUID());
        if(previous!=null && valid(previous))return;
        int layout=actor.getRandom().nextInt(4);
        var session=new Session(actor,patient,hand,slot,device,++nextId,automatic,layout,
                actor.level().getGameTime(),new DefibGame(automatic,layout,actor.level().getGameTime()));
        SESSIONS.put(actor.getUUID(),session);
        reply(session,OPEN);
    }
    private static boolean eligible(ServerPlayer actor,ServerPlayer target) {
        return actor.isAlive() && target.isAlive() && !target.isRemoved() && actor.level()==target.level()
                && (actor==target || actor.distanceToSqr(target)<=16 && actor.hasLineOfSight(target))
                && ServerMedicalSystem.get(target).canDefibrillate();
    }
    private static boolean valid(Session s) {
        long elapsed=s.actor.level().getGameTime()-s.started;
        return elapsed>=0 && elapsed<=LIMIT && !s.actor.isRemoved() && !s.actor.isSpectator()
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
        switch(s.game.input(packet.control(),actor.level().getGameTime())) {
            case MISS -> reply(s,MISS);
            case PROGRESS -> reply(s,PROGRESS);
            case SHOCK -> {
                SESSIONS.remove(actor.getUUID());
                shock(s);
            }
        }
    }
    private static void shock(Session s) {
        if(!ServerMedicalSystem.defibrillate(s.patient)) {reply(s,ABORT);return;}
        if(!s.actor.getAbilities().instabuild)
            s.device.hurtAndBreak(1,(ServerLevel)s.actor.level(),s.actor,ignored->{});
        if(s.slot>=0) {s.actor.inventoryMenu.getSlot(s.slot).setChanged();s.actor.inventoryMenu.broadcastChanges();}
        reply(s,DONE);
    }
    private static void reply(Session s,int result) {
        if(s.actor.isRemoved() || !ServerPlayNetworking.canSend(s.actor,State.TYPE))return;
        ServerPlayNetworking.send(s.actor,new State(s.id,result,s.automatic,s.layout,s.game.pads(),s.game.locks(),s.game.readyAt()));
    }
    private record Session(ServerPlayer actor,ServerPlayer patient,InteractionHand hand,int slot,ItemStack device,int id,
                           boolean automatic,int layout,long started,DefibGame game) {}

    public record State(int session,int result,boolean automatic,int layout,int pads,int locks,long readyAt) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(Identifier.fromNamespaceAndPath("decaying_aftermath","defib_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=CustomPacketPayload.codec(State::write,State::read);
        public Type<State> type(){return TYPE;}
        private void write(RegistryFriendlyByteBuf b){b.writeInt(session);b.writeByte(result);b.writeBoolean(automatic);b.writeByte(layout);b.writeByte(pads);b.writeByte(locks);b.writeLong(readyAt);}
        private static State read(RegistryFriendlyByteBuf b){return new State(b.readInt(),b.readUnsignedByte(),b.readBoolean(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readLong());}
    }
    /** Carries only an inventory slot of the sender; the patient is always the sender. */
    public record Apply(int slot) implements CustomPacketPayload {
        public static final Type<Apply> TYPE=new Type<>(Identifier.fromNamespaceAndPath("decaying_aftermath","defib_apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Apply> CODEC=CustomPacketPayload.codec(Apply::write,Apply::read);
        public Type<Apply> type(){return TYPE;}
        private void write(RegistryFriendlyByteBuf b){b.writeVarInt(slot);}
        private static Apply read(RegistryFriendlyByteBuf b){return new Apply(b.readVarInt());}
    }
    public record Input(int session,int control) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(Identifier.fromNamespaceAndPath("decaying_aftermath","defib_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=CustomPacketPayload.codec(Input::write,Input::read);
        public Type<Input> type(){return TYPE;}
        private void write(RegistryFriendlyByteBuf b){b.writeInt(session);b.writeByte(control);}
        private static Input read(RegistryFriendlyByteBuf b){return new Input(b.readInt(),b.readUnsignedByte());}
    }
}
