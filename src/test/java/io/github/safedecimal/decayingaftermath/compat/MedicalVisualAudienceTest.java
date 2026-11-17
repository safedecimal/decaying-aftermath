package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class MedicalVisualAudienceTest {
 @Test void ownerIsIncludedExactlyOnceEvenIfTrackerAlsoReturnsIt(){assertEquals(List.of("owner","remote"),MedicalVisualAudience.select(List.of("owner","remote","remote"),"owner",p->true));}
 @Test void clientsWithoutTheCosmeticCodecReceiveNothing(){assertTrue(MedicalVisualAudience.select(List.of("legacy"),"owner",p->false).isEmpty());}
 @Test void onlyTrackingAndSelfWithAdvertisedCodecAreSelected(){assertEquals(List.of("remote","owner"),MedicalVisualAudience.select(List.of("remote","legacy"),"owner",p->!p.equals("legacy")));}
}
