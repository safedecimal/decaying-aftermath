package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.client.DeviceRemovalControls;
import io.github.safedecimal.decayingaftermath.client.MedicalInventoryLayout;
import io.github.safedecimal.decayingaftermath.network.MedicalActionPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodyPart.*;
import static io.github.safedecimal.decayingaftermath.medical.TreatmentKind.*;

class RemovableDevicesTest {
    private BodyEnvironment still() { return new BodyEnvironment(20,0,false,true,false,false,false,0,32,false,false); }
    @Test void splintCanBeRemovedBeforeHealingWithoutHealingOrLosingProgress() {
        var b=new BodyModel();b.damage(LEFT_LEG,10,DamageKind.BLUNT);b.treat(LEFT_LEG,SPLINT,1);
        for(int i=0;i<100;i++)b.tick(still());var progress=b.saveState().wounds().boneHealing()[LEFT_LEG.ordinal()];
        assertTrue(b.removeSplint(LEFT_LEG));assertFalse(b.snapshot().part(LEFT_LEG).splinted());
        assertTrue(b.snapshot().part(LEFT_LEG).fractured());assertEquals(progress,b.saveState().wounds().boneHealing()[LEFT_LEG.ordinal()]);
        assertFalse(b.removeSplint(LEFT_LEG));
    }
    @Test void healedSplintStaysInstalledUntilManualRemovalAndSurvivesSaving() {
        var b=new BodyModel();b.damage(LEFT_LEG,10,DamageKind.BLUNT);b.treat(LEFT_LEG,SPLINT,1);
        for(int i=0;i<BodyModel.BONE_HEAL_TICKS+100;i++)b.tick(still());
        assertFalse(b.snapshot().part(LEFT_LEG).fractured());assertTrue(b.snapshot().part(LEFT_LEG).splinted());
        var restored=BodyModel.restoreState(b.saveState());assertTrue(restored.removeSplint(LEFT_LEG));
        assertFalse(restored.snapshot().part(LEFT_LEG).splinted());assertFalse(restored.snapshot().part(LEFT_LEG).fractured());
    }
    @Test void bothDevicesCanBeRemovedSeparatelyAndCannotBeRemovedTwice() {
        var b=new BodyModel();b.damage(LEFT_LEG,10);b.treat(LEFT_LEG,SPLINT,1);b.treat(LEFT_LEG,TOURNIQUET,1);
        assertTrue(b.removeTourniquet(LEFT_LEG));assertTrue(b.snapshot().part(LEFT_LEG).splinted());
        assertFalse(b.removeTourniquet(LEFT_LEG));assertTrue(b.removeSplint(LEFT_LEG));assertFalse(b.removeSplint(LEFT_LEG));
        assertTrue(b.snapshot().part(LEFT_LEG).bleeding()>0);
    }
    @Test void selectedDeviceButtonsAreVisibleDistinctAndDoNotOverlapSlotsOrRecipeToggle() {
        var b=new BodyModel();b.treat(LEFT_LEG,SPLINT,1);b.treat(LEFT_LEG,TOURNIQUET,1);var part=b.snapshot().part(LEFT_LEG);
        var buttons=DeviceRemovalControls.buttons(part);assertEquals(2,buttons.size());
        for(var button:buttons) {
            assertEquals(button.kind(),DeviceRemovalControls.at(part,button.x()+3,button.y()+3));
            assertTrue(button.x()+button.width()<=MedicalInventoryLayout.WIDTH);assertTrue(button.y()+button.height()<153);
            assertTrue(button.y()>=MedicalInventoryLayout.RECIPE_Y+18);
            for(int i=0;i<46;i++) {
                var slot=MedicalInventoryLayout.slot(i);
                assertFalse(button.x()<slot.x()+16&&slot.x()<button.x()+button.width()
                        &&button.y()<slot.y()+16&&slot.y()<button.y()+button.height(),"slot "+i);
            }
        }
        assertTrue(DeviceRemovalControls.buttons(new BodyModel().snapshot().part(LEFT_LEG)).isEmpty());
        assertNull(DeviceRemovalControls.at(part,100,150));
    }
    @Test void removalActionsRetainExactDeviceKindPartAndZeroDoseInNativeCodec() {
        for(int action:new int[]{MedicalActionPayload.REMOVE_TOURNIQUET,MedicalActionPayload.REMOVE_SPLINT}) {
            var kind=action==MedicalActionPayload.REMOVE_SPLINT?SPLINT:TOURNIQUET;
            var expected=new MedicalActionPayload(action,0,LEFT_LEG.ordinal(),kind.ordinal(),0);
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try { MedicalActionPayload.CODEC.encode(buf,expected);assertEquals(expected,MedicalActionPayload.CODEC.decode(buf));assertEquals(buf.writerIndex(),buf.readerIndex()); }
            finally {buf.release();}
        }
        assertTrue(MedicalActionPayload.TYPE.id().toString().endsWith("medical_action_v4"));
    }
    @Test void serverRefundOccursOnlyAfterSuccessfulRemovalWithFullInventoryDropFallback() throws Exception {
        var j=Path.of(System.getProperty("user.dir"),"src/main/java/io/github/safedecimal/decayingaftermath");
        var network=Files.readString(j.resolve("network/MedicalNetwork.java"));
        assertTrue(network.contains("if(!removed)return false;"));
        assertTrue(network.indexOf("if(!removed)return false;")<network.indexOf("ItemStack returned=new ItemStack"));
        assertTrue(network.contains("if(!player.getInventory().add(returned))player.drop(returned,false"));
        assertTrue(network.contains("request.kind()!=device.ordinal()"));
        var screen=Files.readString(j.resolve("client/MedicalInventoryScreen.java"));
        assertTrue(screen.contains("DeviceRemovalControls.at("));assertTrue(screen.contains("ClientMedicalState.removeSplint("));
    }
}
