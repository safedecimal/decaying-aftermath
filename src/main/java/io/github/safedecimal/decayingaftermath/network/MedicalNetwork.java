package io.github.safedecimal.decayingaftermath.network;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Server-owned fractional supplies. Partial units are isolated so vanilla stack merging cannot duplicate fill. */
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
        if(ServerPlayNetworking.canSend(player,BodyPayload.TYPE))ServerPlayNetworking.send(player,new BodyPayload(snapshot));
    }
    public static void thought(ServerPlayer player,String message) {
        if(ServerPlayNetworking.canSend(player,ThoughtPayload.TYPE))ServerPlayNetworking.send(player,new ThoughtPayload(message));
    }
    public static float remainingFill(ItemStack stack) {
        if(stack==null||stack.isEmpty())return 0;
        if(MedicalItems.treatment(stack)==null)return 1;
        return MedicalSupplyPolicy.fill(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getFloatOr(FILL_KEY,1));
    }
    private static void handle(ServerPlayer player,MedicalActionPayload request) {
        if(request.action()==MedicalActionPayload.END) {SESSIONS.remove(player.getUUID());return;}
        if(!Float.isFinite(request.amount())||request.amount()<0||request.amount()>1
                ||request.slot()<9||request.slot()>=player.inventoryMenu.slots.size()
                ||request.part()<0||request.part()>=BodyPart.values().length
                ||request.kind()<0||request.kind()>=TreatmentKind.values().length
                ||player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty()
                ||!ServerMedicalSystem.canAct(player)) {SESSIONS.remove(player.getUUID());return;}
        Slot slot=player.inventoryMenu.getSlot(request.slot());ItemStack stack=slot.getItem();
        BodyPart part=BodyPart.values()[request.part()];TreatmentKind kind=TreatmentKind.values()[request.kind()];
        long now=player.level().getGameTime();
        if(request.action()==MedicalActionPayload.BEGIN) {
            if(stack.isEmpty()||MedicalItems.treatment(stack)!=kind||(kind.requiresLimb()&&!part.isLimb())||remainingFill(stack)<=MedicalSupplyPolicy.EMPTY)return;
            Session prior=SESSIONS.get(player.getUUID());
            if(prior!=null&&now-prior.start<40)return;
            SESSIONS.put(player.getUUID(),new Session(request.slot(),part,kind,stack.copy(),now,remainingFill(stack)));
            return;
        }
        Session session=SESSIONS.get(player.getUUID());
        if(session==null||session.slot!=request.slot()||session.part!=part||session.kind!=kind
                ||now-session.start>2400||session.signature.getCount()!=stack.getCount()
                ||!ItemStack.isSameItemSameComponents(session.signature,stack)) {SESSIONS.remove(player.getUUID());return;}
        BodyModel body=ServerMedicalSystem.get(player);
        if(request.action()==MedicalActionPayload.FINISH) {
            if(!MedicalSupplyPolicy.installation(kind)||now-session.start<40
                    ||session.spent<session.initialFill-MedicalSupplyPolicy.EMPTY)return;
            // A fitted device is installed, not a fractional pharmacological dose. A remainder
            // resumed after cancellation must still tighten the tourniquet below the 50% mark.
            body.treat(part,kind,1);SESSIONS.remove(player.getUUID());
            sync(player,body.snapshot());return;
        }
        if(request.action()!=MedicalActionPayload.APPLY||stack.isEmpty()||MedicalItems.treatment(stack)!=kind)return;
        boolean creative=player.getAbilities().instabuild;
        List<Transfer> transfers=creative?List.of():planUnused(player.inventoryMenu,request.slot(),stack);
        if(transfers==null) {
            if(!session.warnedSpace) {thought(player,"decaying_aftermath.thought.supply_space");session.warnedSpace=true;}
            return;
        }
        float grant=session.budget.grant(now,request.amount(),remainingFill(stack));
        var spend=MedicalSupplyPolicy.spend(remainingFill(stack),grant);
        if(spend.used()<=0)return;
        if(!creative)separateUnused(slot,stack,transfers);
        session.spent+=spend.used();
        if(!MedicalSupplyPolicy.installation(kind)) {
            if(kind==TreatmentKind.HEALING_POTION||kind==TreatmentKind.REGENERATION_POTION||kind==TreatmentKind.POTION)
                applyPotion(player,body,part,stack,spend.used(),spend.exhausted());
            else {
                float treatment=MedicalSupplyPolicy.treatmentAmount(kind,spend.used());
                while(treatment>0) {float dose=Math.min(1,treatment);body.treat(part,kind,dose);treatment=Math.max(0,treatment-dose);}
            }
        }
        if(!creative) {
            if(spend.exhausted()) {
                boolean potion=stack.getItem() instanceof PotionItem;stack.shrink(1);
                if(potion)slot.set(new ItemStack(Items.GLASS_BOTTLE));
            } else {
                CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putFloat(FILL_KEY,spend.remaining()));
                stack.set(DataComponents.MAX_STACK_SIZE,1);
            }
        }
        session.signature=slot.getItem().copy();
        if(spend.exhausted()&&!MedicalSupplyPolicy.installation(kind)) {
            if(creative)session.budget=new ProcedureBudget(now,MedicalSupplyPolicy.ticksPerFill(kind));
            else SESSIONS.remove(player.getUUID());
        }
        slot.setChanged();player.inventoryMenu.broadcastChanges();sync(player,body.snapshot());
    }
    /** Unopened units remain full. Do not assign a fractional tag to the whole stack. */
    private static ItemStack unused(ItemStack stack,int count) {
        ItemStack remainder=stack.copyWithCount(count);
        CustomData.update(DataComponents.CUSTOM_DATA,remainder,tag->tag.remove(FILL_KEY));
        if(remainder.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().isEmpty())remainder.remove(DataComponents.CUSTOM_DATA);
        remainder.remove(DataComponents.MAX_STACK_SIZE);
        return remainder;
    }
    private record Transfer(Slot slot,int count,ItemStack template) {}
    public static boolean canSeparateSupply(InventoryMenu menu,int sourceSlot,ItemStack stack) {
        return stack.getCount()<=1||planUnused(menu,sourceSlot,stack)!=null;
    }
    private static List<Transfer> planUnused(InventoryMenu menu,int sourceSlot,ItemStack stack) {
        if(stack.getCount()<=1)return List.of();
        ItemStack remainder=unused(stack,stack.getCount()-1);int left=remainder.getCount();
        List<Transfer> plan=new ArrayList<>();
        // Merge only unopened, component-identical units before taking an empty ordinary inventory slot.
        for(int pass=0;pass<2&&left>0;pass++)for(int i=9;i<45&&left>0;i++) {
            if(i==sourceSlot)continue;Slot target=menu.getSlot(i);ItemStack existing=target.getItem();
            boolean empty=existing.isEmpty();
            if((pass==0&&empty)||(pass==1&&!empty))continue;
            if(!empty&&!ItemStack.isSameItemSameComponents(existing,remainder))continue;
            int capacity=empty?remainder.getMaxStackSize():existing.getMaxStackSize()-existing.getCount();
            int amount=Math.min(left,Math.max(0,capacity));
            if(amount>0) {plan.add(new Transfer(target,amount,remainder));left-=amount;}
        }
        return left==0?plan:null;
    }
    private static void separateUnused(Slot source,ItemStack stack,List<Transfer> transfers) {
        if(stack.getCount()<=1)return;
        stack.setCount(1);
        for(Transfer move:transfers) {
            if(move.slot().getItem().isEmpty())move.slot().set(move.template().copyWithCount(move.count()));
            else move.slot().getItem().grow(move.count());
            move.slot().setChanged();
        }
        source.setChanged();
    }
    private static void applyPotion(ServerPlayer player,BodyModel body,BodyPart part,ItemStack stack,float amount,boolean depleted) {
        PotionContents contents=stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
        var other=new ArrayList<net.minecraft.world.effect.MobEffectInstance>();
        for(var effect:contents.getAllEffects()) {
            if(effect.is(MobEffects.INSTANT_HEALTH))body.treat(part,TreatmentKind.HEALING_POTION,Math.min(1,.5f*(effect.getAmplifier()+1)*amount));
            else if(effect.is(MobEffects.REGENERATION))body.treat(part,TreatmentKind.REGENERATION_POTION,Math.min(1,.5f*(effect.getAmplifier()+1)*amount));
            else other.add(new net.minecraft.world.effect.MobEffectInstance(effect));
        }
        // Non-medical vanilla effects are applied once when the bottle is exhausted, not once per packet.
        if(depleted&&!other.isEmpty())new PotionContents(Optional.empty(),contents.customColor(),other,contents.customName()).applyToLivingEntity(player,stack.getOrDefault(DataComponents.POTION_DURATION_SCALE,1f));
    }
    private static final class Session {
        final int slot;final BodyPart part;final TreatmentKind kind;final long start;final float initialFill;
        ItemStack signature;ProcedureBudget budget;float spent;boolean warnedSpace;
        Session(int slot,BodyPart part,TreatmentKind kind,ItemStack signature,long start,float fill) {
            this.slot=slot;this.part=part;this.kind=kind;this.signature=signature;this.start=start;this.initialFill=fill;
            budget=new ProcedureBudget(start,MedicalSupplyPolicy.ticksPerFill(kind));
        }
    }
}
