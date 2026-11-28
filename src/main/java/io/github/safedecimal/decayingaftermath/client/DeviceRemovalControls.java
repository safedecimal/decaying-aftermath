package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.medical.TreatmentKind;
import java.util.ArrayList;
import java.util.List;

/** Canvas-space controls, distinct from body, recipe and inventory hit boxes. */
public final class DeviceRemovalControls {
    private DeviceRemovalControls() { }
    public record Button(TreatmentKind kind,int x,int y,int width,int height) {
        public boolean contains(double px,double py) { return px>=x&&px<x+width&&py>=y&&py<y+height; }
        public String translationKey() { return kind==TreatmentKind.TOURNIQUET
                ?"abjection.device.remove_tourniquet":"abjection.device.remove_splint"; }
    }
    public static List<Button> buttons(BodySnapshot.PartSnapshot part) {
        var result=new ArrayList<Button>();
        if(part.tourniquet())result.add(new Button(TreatmentKind.TOURNIQUET,178,130,78,10));
        if(part.splinted())result.add(new Button(TreatmentKind.SPLINT,178,142,78,10));
        return List.copyOf(result);
    }
    public static TreatmentKind at(BodySnapshot.PartSnapshot part,double x,double y) {
        for(var button:buttons(part))if(button.contains(x,y))return button.kind();return null;
    }
}
