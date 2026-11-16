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

/** Real InventoryMenu slots, merely repositioned around the CU anatomy reference. */
public final class MedicalInventoryScreen extends AbstractContainerScreen<InventoryMenu> implements RecipeUpdateListener {
    private static final int CANVAS_W=220, CANVAS_H=250;
    private static final int[][] PART_BOXES={{99,21,23,23},{96,45,31,50},{127,48,54,15},
            {42,48,54,15},{115,96,16,54},{97,96,16,54}};
    private static final String[] PART_NAMES={"head","torso","left_arm","right_arm","left_leg","right_leg"};
    private static final int[][] PART_SOURCES={{46,44},{40,65},{99,27},{99,27},{31,105},{31,105}};
    private final int[][] originalPositions;
    private final CraftingRecipeBookComponent recipeBook;
    private float scale=1;
    private int originX,originY;
    private int pendingSlot=-1;
    private double pressX,pressY;
    private boolean moved;
    private ItemStack dragged=ItemStack.EMPTY;
    private BodyPart selected=BodyPart.TORSO;

    public MedicalInventoryScreen(Player player) {
        super(player.inventoryMenu,player.getInventory(),Component.literal("Medical inventory"),CANVAS_W,CANVAS_H);
        originalPositions=new int[menu.slots.size()][2];
        for(int i=0;i<menu.slots.size();i++) {
            originalPositions[i][0]=menu.slots.get(i).x;
            originalPositions[i][1]=menu.slots.get(i).y;
        }
        recipeBook=new CraftingRecipeBookComponent(menu);
    }

    @Override protected void init() {
        super.init(); leftPos=topPos=0;
        originX=Math.min(139,Math.max(0,width-80));
        scale=Math.min(1f,Math.min((width-originX-4f)/CANVAS_W,(height-8f)/CANVAS_H));
        scale=Math.max(0.15f,scale);
        originX+=(int)Math.max(0,(width-originX-CANVAS_W*scale)/2);
        originY=Math.max(4,(int)((height-CANVAS_H*scale)/2));
        for(int i=9;i<36;i++) position(i,28+(i-9)%9*18,164+(i-9)/9*18);
        // The HUD hotbar source has twenty-pixel spacing, unlike the inventory's eighteen-pixel grid.
        for(int i=36;i<45;i++) position(i,21+(i-36)*20,226);
        position(5,102,1); position(6,164,84); position(7,73,118);
        position(8,138,136); position(45,12,82);
        position(1,182,25); position(2,200,25); position(3,182,43);
        position(4,200,43); position(0,191,70);
        recipeBook.init(width,height,minecraft,true);
    }

    private void position(int index,int x,int y) {
        SlotPositionAccessor position=(SlotPositionAccessor)(Object)menu.slots.get(index);
        position.decayingAftermath$setX(x); position.decayingAftermath$setY(y);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        MedicalPanelRenderer.extract(graphics,MedicalPanelLayout.topLeft());
        BodySnapshot body=ClientMedicalState.snapshot();
        cuText(graphics,"BLOOD "+Math.round(body.blood()*100)+"%",7,34);
        cuText(graphics,"OXYGEN "+Math.round(body.oxygen()*100)+"%",7,46);
        cuText(graphics,"PAIN "+Math.round(body.pain()*100)+"%",7,58);
        cuText(graphics,"DRUG "+Math.round(body.drug()*100),7,70);
        graphics.pose().pushMatrix(); graphics.pose().translate(originX,originY); graphics.pose().scale(scale,scale);
        super.extractRenderState(graphics,(int)virtualX(mouseX),(int)virtualY(mouseY),partialTick);
        graphics.pose().popMatrix();
        if(pendingSlot>=0) {
            graphics.item(dragged,mouseX-8,mouseY-8);
            graphics.itemDecorations(font,dragged,mouseX-8,mouseY-8);
        }
        if(recipeBook.isVisible()) {
            recipeBook.extractRenderState(graphics,mouseX,mouseY,partialTick);
            recipeBook.extractTooltip(graphics,mouseX,mouseY,hoveredSlot);
        }
        // Screens render after HUD elements; keep the unconscious/Last Standing presentation above this screen too.
        if(body.mode()==BodySnapshot.Mode.UNCONSCIOUS||body.mode()==BodySnapshot.Mode.LAST_STANDING)
            MedicalClient.extractHud(graphics);
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
                18,223,182,22);
        BodySnapshot.PartSnapshot[] states=ClientMedicalState.snapshot().parts();
        for(int i=0;i<PART_BOXES.length;i++) {
            int[] b=PART_BOXES[i], src=PART_SOURCES[i];
            float health=states[i].integrity();
            int red=Math.round(225-145*health),green=Math.round(65+150*health);
            int tint=0xFF000000|red<<16|green<<8|70;
            bodyTexture(graphics,PART_NAMES[i],b,src,tint);
            int[] outlineSrc={src[0],i==0?45:src[1]};
            bodyTexture(graphics,PART_NAMES[i]+"_outline",b,outlineSrc,
                    selected.ordinal()==i?0xFF2BEE91:0xFFDADADA);
            if(states[i].bandaged()) overlayItem(graphics,MedicalItems.BANDAGE.getDefaultInstance(),b[0],b[1]+b[3]/2);
            if(states[i].splinted()) overlayItem(graphics,MedicalItems.SPLINT.getDefaultInstance(),b[0],b[1]+b[3]/2+5);
            if(states[i].tourniquet()) overlayItem(graphics,MedicalItems.TOURNIQUET.getDefaultInstance(),b[0],b[1]);
        }
        graphics.item(Items.KNOWLEDGE_BOOK.getDefaultInstance(),191,98);
        graphics.pose().popMatrix();
    }
    private static void overlayItem(GuiGraphicsExtractor graphics,ItemStack item,int x,int y) {
        graphics.pose().pushMatrix();graphics.pose().translate(x,y);graphics.pose().scale(0.6f,0.6f);
        graphics.item(item,0,0);graphics.pose().popMatrix();
    }
    private static void bodyTexture(GuiGraphicsExtractor graphics,String name,int[] b,int[] src,int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED,MedicalClient.id("textures/gui/body/"+name+".png"),
                b[0],b[1],0f,0f,b[2],b[3],src[0],src[1],src[0],src[1],color);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        graphics.text(font,"Crafting",177,12,0xFFFFFFFF,false);
        graphics.text(font,"Inventory / "+pretty(selected),28,153,0xFFFFFFFF,false);
        BodyPart hover=partAt(mouseX,mouseY);
        if(hover!=null) {
            BodySnapshot.PartSnapshot part=ClientMedicalState.snapshot().part(hover);
            String text=pretty(hover)+" "+Math.round(part.integrity()*100)+"%"
                    +(part.fractured()?" / fractured":"")+(part.bleeding()>0?" / bleeding":"");
            graphics.setTooltipForNextFrame(font,Component.literal(text),mouseX,mouseY);
        }
    }
    private static String pretty(BodyPart part) { return part.name().toLowerCase().replace('_',' '); }
    private static void cuText(GuiGraphicsExtractor graphics,String value,int x,int y) {
        for(char c:value.toCharArray()) {
            int glyph=MedicalPanelGlyphs.index(c);
            graphics.blit(RenderPipelines.GUI_TEXTURED,MedicalClient.id("textures/font/monitor.png"),
                    x,y,(glyph%16)*8f,(glyph/16)*10f,8,10,128,60,0xFF18B93B);
            x+=MedicalPanelGlyphs.advance(c);
        }
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
        for(int i=0;i<PART_BOXES.length;i++) {int[] b=PART_BOXES[i];if(x>=b[0]&&x<b[0]+b[2]&&y>=b[1]&&y<b[1]+b[3]) return BodyPart.values()[i];}
        return null;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.UNCONSCIOUS) return true;
        if(recipeBook.isVisible()&&recipeBook.mouseClicked(event,doubleClick)) return true;
        MouseButtonEvent e=virtual(event);
        if(e.x()>=189&&e.x()<211&&e.y()>=95&&e.y()<118) {recipeBook.toggleVisibility();return true;}
        BodyPart part=partAt(e.x(),e.y());
        if(part!=null&&menu.getCarried().isEmpty()) {selected=part;return true;}
        Slot slot=slotAt(e.x(),e.y());
        if(event.button()==0 && event.modifiers()==0 && menu.getCarried().isEmpty()
                &&slot!=null&&slot.index>=9&&slot.hasItem()&&MedicalItems.treatment(slot.getItem())!=null) {
            pendingSlot=slot.index;dragged=slot.getItem().copy();pressX=e.x();pressY=e.y();moved=false;return true;
        }
        return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(pendingSlot>=0) {MouseButtonEvent e=virtual(event);moved|=Math.hypot(e.x()-pressX,e.y()-pressY)>3;return true;}
        return super.mouseDragged(virtual(event),dx/scale,dy/scale);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if(pendingSlot>=0) {
            MouseButtonEvent e=virtual(event);int source=pendingSlot;pendingSlot=-1;
            BodyPart part=partAt(e.x(),e.y());TreatmentKind kind=MedicalItems.treatment(dragged);dragged=ItemStack.EMPTY;
            if(moved&&part!=null&&kind!=null) {
                if((kind==TreatmentKind.SPLINT||kind==TreatmentKind.TOURNIQUET)
                        &&(part==BodyPart.HEAD||part==BodyPart.TORSO)) {
                    ThoughtTyper.enqueue("This needs a limb.");return true;
                }
                selected=part; minecraft.gui.setScreen(new MedicalProcedureScreen(this,source,part,kind)); return true;
            }
            slotClicked(menu.getSlot(source),source,0,ContainerInput.PICKUP);
            if(moved) {Slot target=slotAt(e.x(),e.y());
                if(target!=null&&target.index!=source) slotClicked(target,target.index,0,ContainerInput.PICKUP);
                else if(hasClickedOutside(e.x(),e.y(),0,0)) slotClicked(null,-999,0,ContainerInput.PICKUP);
            }
            return true;
        }
        return super.mouseReleased(virtual(event));
    }
    @Override protected boolean hasClickedOutside(double x,double y,int left,int top) {
        return x<0||y<0||x>=CANVAS_W||y>=CANVAS_H;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        return super.mouseScrolled(virtualX(x),virtualY(y),horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.UNCONSCIOUS&&event.key()!=256) return true;
        return recipeBook.isVisible()&&recipeBook.keyPressed(event)||super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {return recipeBook.isVisible()&&recipeBook.charTyped(event);}
    @Override protected void slotClicked(Slot slot,int id,int button,ContainerInput input) {
        super.slotClicked(slot,id,button,input);recipeBook.slotClicked(slot);
    }
    @Override protected void containerTick() {super.containerTick();recipeBook.tick();}
    @Override public void recipesUpdated() {recipeBook.recipesUpdated();}
    @Override public void fillGhostRecipe(RecipeDisplay display) {recipeBook.fillGhostRecipe(display);}
    @Override public void removed() {
        for(int i=0;i<originalPositions.length;i++) position(i,originalPositions[i][0],originalPositions[i][1]);
        super.removed();
    }
}
