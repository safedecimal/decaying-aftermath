package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import java.util.function.Consumer;

/** Uses vanilla hold-to-consume, sound, stats and consumption; effects remain server-owned. */
public final class SurvivalConsumableItem extends Item {
    private final TreatmentKind treatment;
    private final float water,pathogens,caffeine;
    private final boolean bottle;
    private final String hint;
    public SurvivalConsumableItem(Properties properties,TreatmentKind treatment,float water,float pathogens,
            float caffeine,boolean bottle,String hint) {
        super(properties);this.treatment=treatment;this.water=water;this.pathogens=pathogens;
        this.caffeine=caffeine;this.bottle=bottle;this.hint=hint;
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer server && !ServerMedicalSystem.canAct(server))return InteractionResult.FAIL;
        return super.use(level,player,hand);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity) {
        int before=stack.getCount();
        if(entity instanceof ServerPlayer player) {
            if(!ServerMedicalSystem.canAct(player))return stack;
            BodyModel body=ServerMedicalSystem.get(player);
            if(treatment!=null)body.treat(BodyPart.TORSO,treatment,1);
            if(water>0)body.consume(0,water);
            if(pathogens>0)body.drinkContaminated(pathogens,0);
            if(caffeine>0)body.caffeinate(caffeine);
            ServerMedicalSystem.mirrorHealth(player);MedicalNetwork.sync(player,body.snapshot());
        }
        ItemStack result=super.finishUsingItem(stack,level,entity);
        if(entity instanceof ServerPlayer player && bottle && !player.getAbilities().instabuild && result.getCount()<before) {
            ItemStack empty=new ItemStack(Items.GLASS_BOTTLE);
            if(result.isEmpty())return empty;
            if(!player.getInventory().add(empty))player.drop(empty,false,net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return result;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,
            Consumer<Component> tooltip,TooltipFlag flag) {
        super.appendHoverText(stack,context,display,tooltip,flag);
        tooltip.accept(Component.translatable(hint));
    }
}
