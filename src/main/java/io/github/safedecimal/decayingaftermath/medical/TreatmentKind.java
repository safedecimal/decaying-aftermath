package io.github.safedecimal.decayingaftermath.medical;

public enum TreatmentKind {
    BANDAGE, PRESSURE_DRESSING, SPLINT, TOURNIQUET, FENTANYL,
    HEALING_POTION, REGENERATION_POTION, POTION, ANESTHETIC, ANTISEPTIC,
    ANTIBIOTICS, PAINKILLERS, NALOXONE, COAGULANT, COMBAT_STIMULANT,
    BLOOD_BAG, SALINE, ICE_PACK, CHEST_DRAIN, REMOVE_FOREIGN_BODY;

    public boolean requiresLimb() { return this == SPLINT || this == TOURNIQUET; }
    public boolean injection() {
        return this == FENTANYL || this == ANESTHETIC || this == NALOXONE
                || this == COMBAT_STIMULANT || this == BLOOD_BAG || this == SALINE;
    }
    public boolean oral() { return this == ANTIBIOTICS || this == PAINKILLERS || this == COAGULANT; }
    public boolean simpleTool() { return this == ICE_PACK || this == CHEST_DRAIN || this == REMOVE_FOREIGN_BODY; }
    public boolean reusable() { return this == REMOVE_FOREIGN_BODY; }
    public boolean requiresTorso() { return this == CHEST_DRAIN; }
}
