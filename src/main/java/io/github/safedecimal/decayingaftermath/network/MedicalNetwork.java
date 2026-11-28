package io.github.safedecimal.decayingaftermath.network;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

/** Server-owned fractional supplies; one active unit is peeled without refilling its neighbours. */
public final class MedicalNetwork {
    public static final String FILL_KEY="abjection_fill";
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> LAST_CAVE_SOUND=new HashMap<>();
    private MedicalNetwork() {}
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(BodyPayload.TYPE,BodyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ThoughtPayload.TYPE,ThoughtPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MedicalActionPayload.TYPE,MedicalActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StandUpPayload.TYPE, StandUpPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CaveSoundPayload.TYPE, CaveSoundPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StandUpPayload.TYPE,
                (p,c)->c.server().execute(()->ServerMedicalSystem.tryStandUp(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(CaveSoundPayload.TYPE,
                (p,c)->c.server().execute(()->handleCaveSound(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(MedicalActionPayload.TYPE,(p,c)->c.server().execute(()->handle(c.player(),p)));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{SESSIONS.remove(h.getPlayer().getUUID());LAST_CAVE_SOUND.remove(h.getPlayer().getUUID());});
        ServerPlayerEvents.COPY_FROM.register((oldPlayer,newPlayer,alive)->SESSIONS.remove(oldPlayer.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                if (player.tickCount % 20 == 0) compactSupplies(player);
        });
        ServerMedicalSystem.setSyncHandler(MedicalNetwork::sync);
    }
    private static void handleCaveSound(ServerPlayer player) {
        if(!ServerMedicalSystem.canAct(player)||player.isCreative()||player.isSpectator()) return;
        long now=player.level().getGameTime();
        Long last=LAST_CAVE_SOUND.get(player.getUUID());
        if(last!=null&&now-last<160) return; // Rate-limit to at most once per 8 seconds
        LAST_CAVE_SOUND.put(player.getUUID(),now);
        ServerMedicalSystem.get(player).onCaveSound();
    }
    public static void sync(ServerPlayer player,BodySnapshot snapshot) {
        if(ServerPlayNetworking.canSend(player,BodyPayload.TYPE))
            ServerPlayNetworking.send(player,new BodyPayload(snapshot.withBloodGroup(BloodGroup.forPlayer(player.getUUID()))));
    }
    public static void cancelProcedures(ServerPlayer player) {SESSIONS.remove(player.getUUID());}
    public static void thought(ServerPlayer player,String message) {
        if(ServerPlayNetworking.canSend(player,ThoughtPayload.TYPE))ServerPlayNetworking.send(player,new ThoughtPayload(message));
    }
    public static float remainingFill(ItemStack stack) {
        if(stack==null||stack.isEmpty())return 0;
        if(MedicalItems.treatment(stack)==null)return 1;
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        float val=tag.contains(FILL_KEY)?tag.getFloatOr(FILL_KEY,1):tag.getFloatOr("decaying_aftermath_fill",1);
        return MedicalSupplyPolicy.fill(val);
    }
    private static void handle(ServerPlayer player,MedicalActionPayload request) {
        if(request.action()==MedicalActionPayload.END) {SESSIONS.remove(player.getUUID());return;}
        if(request.action()==MedicalActionPayload.REMOVE_TOURNIQUET||request.action()==MedicalActionPayload.REMOVE_SPLINT) {
            TreatmentKind device=request.action()==MedicalActionPayload.REMOVE_TOURNIQUET?TreatmentKind.TOURNIQUET:TreatmentKind.SPLINT;
            if(request.amount()!=0||request.kind()!=device.ordinal()
                    ||request.part()<0||request.part()>=BodyPart.values().length
                    ||player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty()
                    ||!ServerMedicalSystem.canAct(player))return;
            SESSIONS.remove(player.getUUID());
            BodyPart part=BodyPart.values()[request.part()];removeDevice(player,part,device);
            return;
        }
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
            if(stack.isEmpty()||MedicalItems.treatment(stack)!=kind||remainingFill(stack)<=MedicalSupplyPolicy.EMPTY)return;
            Session prior=SESSIONS.get(player.getUUID());
            if(prior!=null&&now-prior.start<40)return;
            SESSIONS.put(player.getUUID(),new Session(request.slot(),part,kind,stack.copy(),now,remainingFill(stack)));
            return;
        }
        Session session=SESSIONS.get(player.getUUID());
        if(session==null||session.slot!=request.slot()||session.part!=part||session.kind!=kind
                ||now-session.start<0||now-session.start>2400||session.signature.getCount()!=stack.getCount()
                ||!ItemStack.isSameItemSameComponents(session.signature,stack)) {SESSIONS.remove(player.getUUID());return;}
        BodyModel body=ServerMedicalSystem.get(player);
        if(request.action()==MedicalActionPayload.FINISH) {
            if(!MedicalSupplyPolicy.installation(kind)||now-session.start<40
                    ||session.spent<session.initialFill-MedicalSupplyPolicy.EMPTY)return;
            // A fitted device is installed, not a fractional pharmacological dose. A remainder
            // resumed after cancellation must still tighten the tourniquet below the 50% mark.
            if(!body.canTreat(part,kind)||!body.treat(part,kind,1)) {SESSIONS.remove(player.getUUID());return;}
            if(kind.reusable()&&!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1,(ServerLevel)player.level(),player,ignored->{});
                if(!stack.isEmpty())writeFill(stack,1);
                slot.setChanged();player.inventoryMenu.broadcastChanges();
            }
            SESSIONS.remove(player.getUUID());
            sync(player,body.snapshot());return;
        }
        if(request.action()!=MedicalActionPayload.APPLY||stack.isEmpty()||MedicalItems.treatment(stack)!=kind)return;
        boolean creative=player.getAbilities().instabuild;
        // Repair old one-item stacks and free medical-only clutter before looking for split space.
        // The source is excluded: its signature and the client's selected slot must remain stable.
        if(!creative && compactInventory(player.inventoryMenu,request.slot()))
            player.inventoryMenu.broadcastChanges();
        List<Transfer> transfers=creative||kind.reusable()?List.of():planUnused(player.inventoryMenu,request.slot(),stack);
        if(transfers==null) {
            if(!session.warnedSpace) {thought(player,"abjection.thought.supply_space");session.warnedSpace=true;}
            return;
        }
        boolean spray=kind==TreatmentKind.ANTISEPTIC;
        float available=creative&&spray?1:kind.reusable()?Math.max(0,1-session.spent):remainingFill(stack);
        if(spray && now<session.nextSpray) return;
        float grant=spray ? session.budget.grantSpray(now,request.amount(),available)
                : session.budget.grant(now,request.amount(),available);
        var spend=MedicalSupplyPolicy.spend(available,grant);
        if(spend.used()<=0)return;
        if(!creative&&!kind.reusable())separateUnused(slot,stack,transfers);
        session.spent+=spend.used();
        if(spray) session.nextSpray=now+MedicalSupplyPolicy.SPRAY_COOLDOWN;
        if(!MedicalSupplyPolicy.installation(kind)) {
            if(kind==TreatmentKind.HEALING_POTION||kind==TreatmentKind.REGENERATION_POTION||kind==TreatmentKind.POTION)
                applyPotion(player,body,part,stack,spend.used(),spend.exhausted());
            else {
                float treatment=MedicalSupplyPolicy.treatmentAmount(kind,spend.used());
                while(treatment>0) {float dose=Math.min(1,treatment);body.treat(part,kind,dose);treatment=Math.max(0,treatment-dose);}
            }
        }
        if(!creative&&!kind.reusable()) {
            if(spend.exhausted()) {
                boolean potion=stack.getItem() instanceof PotionItem;stack.shrink(1);
                if(potion)slot.set(new ItemStack(Items.GLASS_BOTTLE));
            } else {
                writeFill(stack,spend.remaining());
            }
        }
        session.signature=slot.getItem().copy();
        if(creative&&spray&&session.budget.delivered()>=1-MedicalSupplyPolicy.EMPTY)
            session.budget=new ProcedureBudget(now,MedicalSupplyPolicy.ticksPerFill(kind));
        if(spend.exhausted()&&!MedicalSupplyPolicy.installation(kind)) {
            if(creative)session.budget=new ProcedureBudget(now,MedicalSupplyPolicy.ticksPerFill(kind));
            else SESSIONS.remove(player.getUUID());
        }
        slot.setChanged();player.inventoryMenu.broadcastChanges();sync(player,body.snapshot());
    }
    /** Server-owned one-time removal. Return the installed device, or drop it if inventory is full. */
    public static boolean removeDevice(ServerPlayer player,BodyPart part,TreatmentKind device) {
        if(!ServerMedicalSystem.canAct(player))return false;
        BodyModel body=ServerMedicalSystem.get(player);
        boolean removed=device==TreatmentKind.TOURNIQUET?body.removeTourniquet(part)
                :device==TreatmentKind.SPLINT&&body.removeSplint(part);
        if(!removed)return false;
        if(!player.getAbilities().instabuild) {
            ItemStack returned=new ItemStack(device==TreatmentKind.TOURNIQUET?MedicalItems.TOURNIQUET:MedicalItems.SPLINT);
            if(!player.getInventory().add(returned))player.drop(returned,false,net.minecraft.util.Prediction.SERVER_ONLY);
        }
        ServerMedicalSystem.mirrorHealth(player);player.inventoryMenu.broadcastChanges();sync(player,body.snapshot());
        return true;
    }
    /** A counted partial stack contains equal-fill units, not one partial plus magically full units. */
    private static ItemStack unused(ItemStack stack,int count) {
        ItemStack remainder=stack.copyWithCount(count);
        writeFill(remainder,remainingFill(stack));
        return remainder;
    }
    private static void writeFill(ItemStack stack,float fill) {
        float value=MedicalSupplyPolicy.fill(fill);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag -> {
            tag.remove("decaying_aftermath_fill");
            if(value==1)tag.remove(FILL_KEY);else tag.putFloat(FILL_KEY,value);
        });
        if(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().isEmpty())
            stack.remove(DataComponents.CUSTOM_DATA);
        // remove(MAX_STACK_SIZE) is a deletion tombstone in Minecraft, NOT reset-to-default.
        // set(default) clears just this patch while retaining names and other native components.
        stack.set(DataComponents.MAX_STACK_SIZE,stack.getItem().getDefaultMaxStackSize());
    }
    /** Repair saved 0.3.8 fragments on the server. A held stack or foreign container is never edited. */
    public static boolean compactSupplies(ServerPlayer player) {
        if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())return false;
        Session session=SESSIONS.get(player.getUUID());
        if(session!=null&&player.level().getGameTime()-session.start>2400) {
            SESSIONS.remove(player.getUUID());session=null;
        }
        boolean changed=compactInventory(player.inventoryMenu,session==null?-1:session.slot);
        if(changed)player.inventoryMenu.broadcastChanges();
        return changed;
    }
    private static final class SupplyGroup {
        final ItemStack template;final List<Integer> slots=new ArrayList<>();double material;
        SupplyGroup(ItemStack template) {this.template=template;}
    }
    private static boolean compactInventory(InventoryMenu menu,int protectedSlot) {
        if(!menu.getCarried().isEmpty())return false;
        List<SupplyGroup> groups=new ArrayList<>();
        for(int i=9;i<Math.min(45,menu.slots.size());i++) {
            if(i==protectedSlot)continue;
            ItemStack stack=menu.getSlot(i).getItem();
            if(stack.isEmpty()||!MedicalSupplyPolicy.inventorySupply(MedicalItems.treatment(stack)))continue;
            ItemStack template=stack.copyWithCount(1);writeFill(template,1);
            SupplyGroup group=null;
            for(SupplyGroup candidate:groups)
                if(ItemStack.isSameItemSameComponents(candidate.template,template)) {group=candidate;break;}
            if(group==null) {group=new SupplyGroup(template);groups.add(group);}
            group.slots.add(i);group.material+=MedicalSupplyPolicy.material(stack.getCount(),remainingFill(stack));
        }
        boolean changed=false;
        for(SupplyGroup group:groups) {
            var packed=MedicalSupplyPolicy.repack(group.material);
            int capacity=Math.max(1,group.template.getItem().getDefaultMaxStackSize());
            long required=((long)packed.fullUnits()+capacity-1)/capacity+(packed.residual()>0?1:0);
            // A malformed oversized input must not spill, overwrite another item, or lose material.
            if(group.material>Integer.MAX_VALUE||required>group.slots.size())continue;
            int full=packed.fullUnits();boolean residual=packed.residual()>0;
            for(int index:group.slots) {
                ItemStack replacement=ItemStack.EMPTY;
                if(full>0) {int count=Math.min(full,capacity);replacement=group.template.copyWithCount(count);full-=count;}
                else if(residual) {replacement=group.template.copy();writeFill(replacement,packed.residual());residual=false;}
                Slot slot=menu.getSlot(index);ItemStack original=slot.getItem();
                if(original.getCount()!=replacement.getCount()||!ItemStack.isSameItemSameComponents(original,replacement)) {
                    slot.set(replacement);slot.setChanged();changed=true;
                }
            }
        }
        return changed;
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
        ItemStack signature;ProcedureBudget budget;float spent;boolean warnedSpace;long nextSpray;
        Session(int slot,BodyPart part,TreatmentKind kind,ItemStack signature,long start,float fill) {
            this.slot=slot;this.part=part;this.kind=kind;this.signature=signature;this.start=start;this.initialFill=fill;
            budget=new ProcedureBudget(start,MedicalSupplyPolicy.ticksPerFill(kind));
        }
    }
}
