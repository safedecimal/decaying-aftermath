package io.github.safedecimal.decayingaftermath.network;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Server owns item consumption and partial syringe filling, never a client-supplied dose. */
public final class MedicalNetwork {
    public static final String FILL_KEY="decaying_aftermath_fill";
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private MedicalNetwork() {}
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(BodyPayload.TYPE,BodyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ThoughtPayload.TYPE,ThoughtPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MedicalActionPayload.TYPE,MedicalActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MedicalActionPayload.TYPE,(p,c)->c.server().execute(()->handle(c.player(),p)));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->SESSIONS.remove(h.getPlayer().getUUID()));
        ServerMedicalSystem.setSyncHandler(MedicalNetwork::sync);
    }
    public static void sync(ServerPlayer player,BodySnapshot snapshot) {
        if (ServerPlayNetworking.canSend(player,BodyPayload.TYPE)) ServerPlayNetworking.send(player,new BodyPayload(snapshot));
    }
    public static void thought(ServerPlayer player,String message) {
        if (ServerPlayNetworking.canSend(player,ThoughtPayload.TYPE)) ServerPlayNetworking.send(player,new ThoughtPayload(message));
    }
    public static float remainingFill(ItemStack stack) {
        if (!stack.is(MedicalItems.FENTANYL)) return 1;
        float fill=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getFloatOr(FILL_KEY,1);
        return Float.isFinite(fill)?Math.clamp(fill,0,1):0;
    }
    private static void handle(ServerPlayer player,MedicalActionPayload request) {
        if (request.action()==MedicalActionPayload.END) { SESSIONS.remove(player.getUUID());return; }
        if (!Float.isFinite(request.amount()) || request.amount()<0 || request.amount()>1
                || request.slot()<9 || request.slot()>=player.inventoryMenu.slots.size()
                || request.part()<0 || request.part()>=BodyPart.values().length
                || request.kind()<0 || request.kind()>=TreatmentKind.values().length
                || player.containerMenu!=player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()
                || !ServerMedicalSystem.canAct(player)) { SESSIONS.remove(player.getUUID());return; }
        var slot=player.inventoryMenu.getSlot(request.slot());
        ItemStack stack=slot.getItem();
        BodyPart part=BodyPart.values()[request.part()]; TreatmentKind kind=TreatmentKind.values()[request.kind()];
        if (stack.isEmpty() || MedicalItems.treatment(stack)!=kind || (kind.requiresLimb()&&!part.isLimb())) return;
        long now=player.level().getGameTime();
        if (request.action()==MedicalActionPayload.BEGIN) {
            Session prior=SESSIONS.get(player.getUUID());
            if (prior!=null && now-prior.start<40) return;
            SESSIONS.put(player.getUUID(),new Session(request.slot(),part,kind,stack.copy(),now,remainingFill(stack)));
            return;
        }
        Session session=SESSIONS.get(player.getUUID());
        if (request.action()!=MedicalActionPayload.APPLY || session==null || session.slot!=request.slot()
                || session.part!=part || session.kind!=kind || now-session.start>2400
                || !ItemStack.isSameItemSameComponents(session.signature,stack)) { SESSIONS.remove(player.getUUID());return; }
        BodyModel body=ServerMedicalSystem.get(player);
        if (kind==TreatmentKind.FENTANYL) {
            // Cumulative elapsed-time budget is frame-rate independent and caps forged floods.
            float amount=session.budget.grant(now,request.amount(),remainingFill(stack));
            if (amount<=0 || !body.treat(part,kind,amount)) return;
            float left=Math.max(0,remainingFill(stack)-amount);
            if (left<=0.0001f) { stack.shrink(1);SESSIONS.remove(player.getUUID()); }
            else CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putFloat(FILL_KEY,left));
            session.signature=stack.copy();
        } else {
            if (now-session.start<40 || request.amount()<0.99f) return;
            boolean applied;
            if (kind==TreatmentKind.HEALING_POTION || kind==TreatmentKind.REGENERATION_POTION || kind==TreatmentKind.POTION)
                applied=applyPotion(player,body,part,stack);
            else applied=body.treat(part,kind,1);
            SESSIONS.remove(player.getUUID());
            if (!applied) { thought(player,"That part doesn't need this.");return; }
            boolean potion=stack.getItem() instanceof PotionItem;
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (potion) {
                    ItemStack bottle=new ItemStack(Items.GLASS_BOTTLE);
                    if (stack.isEmpty()) slot.set(bottle);
                    else if (!player.getInventory().add(bottle)) player.drop(bottle,false,net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
        }
        slot.setChanged();player.inventoryMenu.broadcastChanges();sync(player,body.snapshot());
    }
    private static boolean applyPotion(ServerPlayer player,BodyModel body,BodyPart part,ItemStack stack) {
        PotionContents contents=stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
        boolean applied=false;
        var other=new ArrayList<net.minecraft.world.effect.MobEffectInstance>();
        for (var effect:contents.getAllEffects()) {
            if (effect.is(MobEffects.INSTANT_HEALTH)) applied|=body.treat(part,TreatmentKind.HEALING_POTION,Math.min(1,.5f*(effect.getAmplifier()+1)));
            else if (effect.is(MobEffects.REGENERATION)) applied|=body.treat(part,TreatmentKind.REGENERATION_POTION,Math.min(1,.5f*(effect.getAmplifier()+1)));
            else other.add(new net.minecraft.world.effect.MobEffectInstance(effect));
        }
        if (!other.isEmpty()) {
            new PotionContents(Optional.empty(),contents.customColor(),other,contents.customName()).applyToLivingEntity(player,
                    stack.getOrDefault(DataComponents.POTION_DURATION_SCALE,1f));
            applied=true;
        }
        return applied;
    }
    private static final class Session {
        final int slot; final BodyPart part; final TreatmentKind kind; final long start;
        ItemStack signature; final ProcedureBudget budget;
        Session(int slot,BodyPart part,TreatmentKind kind,ItemStack signature,long start,float fill) {
            this.slot=slot;this.part=part;this.kind=kind;this.signature=signature;this.start=start;
            this.budget=new ProcedureBudget(start);
        }
    }
}
