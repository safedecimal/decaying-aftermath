package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.client.mixin.SlotPositionAccessor;
import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/** Real InventoryMenu slots around a front-view cubic Minecraft player using their skin. */
public final class MedicalInventoryScreen extends AbstractContainerScreen<InventoryMenu> implements RecipeUpdateListener {
    private static final int CANVAS_W=MedicalInventoryLayout.WIDTH, CANVAS_H=MedicalInventoryLayout.HEIGHT;
    private final int[][] originalPositions;
    private final CraftingRecipeBookComponent recipeBook;
    private float scale=1;
    private int originX,originY;
    private int pendingSlot=-1;
    private double pressX,pressY;
    private boolean moved;
    private boolean clickCarry;
    private boolean consumeMedicalRelease;
    private ItemStack dragged=ItemStack.EMPTY;
    private BodyPart selected=BodyPart.TORSO;

    public MedicalInventoryScreen(Player player) {
        super(player.inventoryMenu,player.getInventory(),Component.translatable("decaying_aftermath.inventory.title"),CANVAS_W,CANVAS_H);
        originalPositions=new int[menu.slots.size()][2];
        for(int i=0;i<menu.slots.size();i++) {
            originalPositions[i][0]=menu.slots.get(i).x;
            originalPositions[i][1]=menu.slots.get(i).y;
        }
        recipeBook=new CraftingRecipeBookComponent(menu);
    }

    @Override protected void init() {
        super.init(); leftPos=topPos=0;
        var panel=MedicalPanelLayout.forScreen(width,height);
        originX=Math.min(panel.x()+panel.width()+MedicalPanelLayout.GAP,Math.max(0,width-80));
        scale=Math.min(1f,Math.min((width-originX-4f)/CANVAS_W,(height-8f)/CANVAS_H));
        scale=Math.max(0.15f,scale);
        originX+=(int)Math.max(0,(width-originX-CANVAS_W*scale)/2);
        originY=Math.max(4,(int)((height-CANVAS_H*scale)/2));
        for(int i=0;i<menu.slots.size();i++) {
            var p=MedicalInventoryLayout.slot(i);position(i,p.x(),p.y());
        }
        recipeBook.init(width,height,minecraft,true);
    }

    private void position(int index,int x,int y) {
        SlotPositionAccessor position=(SlotPositionAccessor)(Object)menu.slots.get(index);
        position.decayingAftermath$setX(x); position.decayingAftermath$setY(y);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        validateMedicalCarry();
        var panel=MedicalPanelLayout.forScreen(width,height);
        MedicalPanelRenderer.extract(graphics,panel);
        BodySnapshot body=ClientMedicalState.snapshot();
        graphics.pose().pushMatrix(); graphics.pose().translate(originX,originY); graphics.pose().scale(scale,scale);
        super.extractRenderState(graphics,(int)virtualX(mouseX),(int)virtualY(mouseY),partialTick);
        graphics.pose().popMatrix();
        extractBodyTooltip(graphics,mouseX,mouseY);
        if(pendingSlot>=0) {
            graphics.item(dragged,mouseX-8,mouseY-8);
            MedicalSupplyOverlay.extract(graphics,dragged,mouseX-8,mouseY-8);
        }
        if(recipeBook.isVisible()) {
            recipeBook.extractRenderState(graphics,mouseX,mouseY,partialTick);
            recipeBook.extractTooltip(graphics,mouseX,mouseY,hoveredSlot);
        }
        // Screens render after HUD elements; keep the unconscious/Last Standing presentation above this screen too.
        if(body.mode()==BodySnapshot.Mode.UNCONSCIOUS||body.mode()==BodySnapshot.Mode.LAST_STANDING
                ||body.mode()==BodySnapshot.Mode.DYING)
            MedicalClient.extractHud(graphics);
        else MedicalClient.extractPain(graphics);
        MedicalPanelRenderer.extractTooltip(graphics,panel,mouseX,mouseY);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        extractTransparentBackground(graphics);
        // 26.3 extracts the background separately, BEFORE extractRenderState.
        // Use precisely the contents' canvas transform here too, not raw screen (0,0).
        graphics.pose().pushMatrix(); graphics.pose().translate(originX,originY); graphics.pose().scale(scale,scale);
        // Every slot background is a crop of Minecraft's original inventory texture.
        for(Slot slot:menu.slots) if(slot.index<36||slot.index>=45)
            graphics.blit(RenderPipelines.GUI_TEXTURED,INVENTORY_LOCATION,
                    slot.x-1,slot.y-1,7f,83f,18,18,256,256);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace("hud/hotbar"),
                MedicalInventoryLayout.HOTBAR_X,MedicalInventoryLayout.HOTBAR_Y,182,22);
        BodySnapshot.PartSnapshot[] states=ClientMedicalState.snapshot().parts();
        for(BodyPart part:BodyPart.values()) {
            int i=part.ordinal();
            var b=MinecraftBodyRenderer.box(part);
            float health=states[i].integrity();
            int channel=Math.round(150+105*health);
            int tint=0xFFFF0000|channel<<8|channel;
            MinecraftBodyRenderer.part(graphics,part,b.x(),b.y(),b.width(),b.height(),tint);
            MinecraftBodyRenderer.outline(graphics,b,selected==part?0xFF2BEE91:0xFFB8B8B8);
            MedicalStatusIcons.body(graphics,states[i],b);
        }
        graphics.item(Items.KNOWLEDGE_BOOK.getDefaultInstance(),MedicalInventoryLayout.RECIPE_X,MedicalInventoryLayout.RECIPE_Y);
        graphics.pose().popMatrix();
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        graphics.text(font,Component.translatable("container.crafting").getString(),215,19,0xFFFFFFFF,false);
        graphics.text(font,Component.translatable("decaying_aftermath.inventory.selected",Component.translatable("container.inventory"),pretty(selected)).getString(),30,153,0xFFFFFFFF,false);
    }
    private void extractBodyTooltip(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        BodyPart hover=partAt(virtualX(mouseX),virtualY(mouseY));
        if(hover!=null) {
            BodySnapshot.PartSnapshot part=ClientMedicalState.snapshot().part(hover);
            Component text=Component.translatable("decaying_aftermath.body_status",pretty(hover),
                    (part.fractured()?Component.translatable("decaying_aftermath.status.fractured").getString():"")
                    +(part.bleeding()>0?Component.translatable("decaying_aftermath.status.bleeding").getString():"")
                    +(part.infection()>=.25f?Component.translatable("decaying_aftermath.status.infection").getString():"")
                    +(part.anesthetized()?Component.translatable("decaying_aftermath.status.anesthetized").getString():"")
                    +(part.burn()>.01f?Component.translatable("decaying_aftermath.status.burn").getString():"")
                    +(part.coldInjury()?Component.translatable("decaying_aftermath.status.cold_injury").getString():"")
                    +(part.internalBleeding()>.001f?Component.translatable("decaying_aftermath.status.internal_bleeding").getString():"")
                    +(part.foreignBody()?Component.translatable("decaying_aftermath.status.foreign_body").getString():"")
                    +(part.tourniquet()?Component.translatable("decaying_aftermath.status.remove_tourniquet").getString():""));
            graphics.setTooltipForNextFrame(font,text,mouseX,mouseY);
        }
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        // Deferred vanilla tooltips require screen coordinates, not the transformed canvas pointer.
        if(pendingSlot<0) super.extractTooltip(graphics,originX+Math.round(mouseX*scale),originY+Math.round(mouseY*scale));
    }
    @Override protected void extractSlot(GuiGraphicsExtractor graphics,Slot slot,int mouseX,int mouseY) {
        // Only the cursor ghost is drawn; the server-owned source remains untouched until APPLY.
        if(slot.index==pendingSlot) return;
        if(MedicalItems.treatment(slot.getItem())!=null) {
            graphics.item(slot.getItem(),slot.x,slot.y);
            MedicalSupplyOverlay.extract(graphics,slot.getItem(),slot.x,slot.y);
        } else super.extractSlot(graphics,slot,mouseX,mouseY);
    }
    private static String pretty(BodyPart part) { return Component.translatable(part.translationKey()).getString(); }
    private static void cuText(GuiGraphicsExtractor graphics,String value,int x,int y) {
        MedicalPanelRenderer.text(graphics,value,x,y);
    }

    private double virtualX(double x) { return (x-originX)/scale; }
    private double virtualY(double y) { return (y-originY)/scale; }
    private MouseButtonEvent virtual(MouseButtonEvent event) {
        return new MouseButtonEvent(virtualX(event.x()),virtualY(event.y()),event.buttonInfo());
    }
    private Slot slotAt(double x,double y) {
        for(Slot slot:menu.slots) if(x>=slot.x-1&&x<slot.x+17&&y>=slot.y-1&&y<slot.y+17&&slot.isActive()) return slot;
        return null;
    }
    private BodyPart partAt(double x,double y) {
        return MinecraftBodyLayout.partAt(x,y,MinecraftBodyRenderer.slim());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        validateMedicalCarry();
        if(ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.UNCONSCIOUS) return true;
        if(recipeBook.isVisible()&&recipeBook.mouseClicked(event,doubleClick)) return true;
        MouseButtonEvent e=virtual(event);
        if(e.x()>=MedicalInventoryLayout.RECIPE_X-2&&e.x()<MedicalInventoryLayout.RECIPE_X+18
                &&e.y()>=MedicalInventoryLayout.RECIPE_Y-2&&e.y()<MedicalInventoryLayout.RECIPE_Y+18) {
            clearMedicalCarry();recipeBook.toggleVisibility();return true;
        }
        BodyPart part=partAt(e.x(),e.y());
        if(part!=null&&event.button()==3&&(event.modifiers()&1)!=0&&menu.getCarried().isEmpty()
                &&ClientMedicalState.snapshot().part(part).tourniquet()) {
            clearMedicalCarry();ClientMedicalState.removeTourniquet(part);return true;
        }
        if(clickCarry&&(event.button()==1||event.button()==3)&&event.modifiers()==0) {
            if(part!=null) return startProcedure(part);
            Slot target=slotAt(e.x(),e.y());
            if(target!=null||hasClickedOutside(e.x(),e.y(),0,0)) {
                transferMedicalCarry(target,event.button()==3?1:0);consumeMedicalRelease=true;return true;
            }
            return true;
        }
        if(clickCarry) clearMedicalCarry();
        if(part!=null&&menu.getCarried().isEmpty()) {selected=part;return true;}
        Slot slot=slotAt(e.x(),e.y());
        // MouseButtonEvent uses SDL button IDs in 26.3: primary=1, NOT GLFW's 0.
        if(event.button()==1 && event.modifiers()==0 && menu.getCarried().isEmpty()
                &&slot!=null&&slot.index>=9&&slot.hasItem()&&MedicalItems.treatment(slot.getItem())!=null) {
            pendingSlot=slot.index;dragged=slot.getItem().copy();pressX=e.x();pressY=e.y();moved=false;clickCarry=false;return true;
        }
        return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        validateMedicalCarry();
        if(pendingSlot>=0) {if(!clickCarry) {MouseButtonEvent e=virtual(event);moved|=Math.hypot(e.x()-pressX,e.y()-pressY)>3;}return true;}
        return super.mouseDragged(virtual(event),dx/scale,dy/scale);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if(consumeMedicalRelease) {consumeMedicalRelease=false;return true;}
        validateMedicalCarry();
        if(pendingSlot>=0) {
            if(clickCarry) return true;
            if(event.button()!=1) return true;
            MouseButtonEvent e=virtual(event);
            moved|=Math.hypot(e.x()-pressX,e.y()-pressY)>3;
            clickCarry=true;
            BodyPart part=partAt(e.x(),e.y());
            if(moved&&part!=null) return startProcedure(part);
            if(moved) {Slot target=slotAt(e.x(),e.y());
                if(target!=null||hasClickedOutside(e.x(),e.y(),0,0)) transferMedicalCarry(target,0);
            }
            return true;
        }
        return super.mouseReleased(virtual(event));
    }
    private void clearMedicalCarry() {pendingSlot=-1;dragged=ItemStack.EMPTY;clickCarry=false;moved=false;}
    private void validateMedicalCarry() {
        if(pendingSlot<0) return;
        ItemStack source=menu.getSlot(pendingSlot).getItem();
        if(!menu.getCarried().isEmpty()||source.isEmpty()||source.getCount()!=dragged.getCount()
                ||!ItemStack.isSameItemSameComponents(source,dragged)
                ||ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.UNCONSCIOUS) clearMedicalCarry();
    }
    private boolean startProcedure(BodyPart part) {
        TreatmentKind kind=MedicalItems.treatment(dragged);
        if(kind==null) {clearMedicalCarry();return true;}
        if((kind==TreatmentKind.SPLINT||kind==TreatmentKind.TOURNIQUET)
                &&(part==BodyPart.HEAD||part==BodyPart.TORSO)) {
            ThoughtTyper.enqueue(Component.translatable("decaying_aftermath.thought.needs_limb").getString());return true;
        }
        if(kind.requiresTorso()&&part!=BodyPart.TORSO) {
            ThoughtTyper.enqueue(Component.translatable("decaying_aftermath.thought.needs_torso").getString());return true;
        }
        if(!BodyModel.restore(ClientMedicalState.snapshot()).canTreat(part,kind)) {
            ThoughtTyper.enqueue(Component.translatable("decaying_aftermath.thought.not_needed").getString());return true;
        }
        int source=pendingSlot;selected=part;clearMedicalCarry();
        minecraft.gui.setScreen(new MedicalProcedureScreen(this,source,part,kind));return true;
    }
    private void transferMedicalCarry(Slot target,int button) {
        int source=pendingSlot;ItemStack selectedStack=dragged;clearMedicalCarry();
        if(target!=null&&target.index==source) return;
        // ContainerInput uses left=0/right=1, unlike SDL MouseButtonEvent's left=1/right=3.
        slotClicked(menu.getSlot(source),source,0,ContainerInput.PICKUP);
        slotClicked(target,target==null?-999:target.index,button,ContainerInput.PICKUP);
        if(!menu.getCarried().isEmpty()&&ItemStack.isSameItemSameComponents(menu.getCarried(),selectedStack)) {
            slotClicked(menu.getSlot(source),source,0,ContainerInput.PICKUP);
            if(menu.getCarried().isEmpty()&&menu.getSlot(source).hasItem()) {
                pendingSlot=source;dragged=menu.getSlot(source).getItem().copy();clickCarry=true;
            }
        }
    }
    @Override protected boolean hasClickedOutside(double x,double y,int left,int top) {
        return x<0||y<0||x>=CANVAS_W||y>=CANVAS_H;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        return super.mouseScrolled(virtualX(x),virtualY(y),horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(pendingSlot>=0) {clearMedicalCarry();if(event.key()==256) return true;}
        if(ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.UNCONSCIOUS&&event.key()!=256) return true;
        return recipeBook.isVisible()&&recipeBook.keyPressed(event)||super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {return recipeBook.isVisible()&&recipeBook.charTyped(event);}
    @Override protected void slotClicked(Slot slot,int id,int button,ContainerInput input) {
        super.slotClicked(slot,id,button,input);recipeBook.slotClicked(slot);
    }
    @Override protected void containerTick() {super.containerTick();validateMedicalCarry();recipeBook.tick();}
    @Override public void recipesUpdated() {recipeBook.recipesUpdated();}
    @Override public void fillGhostRecipe(RecipeDisplay display) {recipeBook.fillGhostRecipe(display);}
    @Override public void removed() {
        clearMedicalCarry();consumeMedicalRelease=false;
        for(int i=0;i<originalPositions.length;i++) position(i,originalPositions[i][0],originalPositions[i][1]);
        super.removed();
    }
}
