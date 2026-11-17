package io.github.safedecimal.decayingaftermath.medical;

public enum TreatmentKind {
    BANDAGE, PRESSURE_DRESSING, SPLINT, TOURNIQUET, FENTANYL,
    HEALING_POTION, REGENERATION_POTION, POTION, ANESTHETIC, ANTISEPTIC;

    public boolean requiresLimb() { return this == SPLINT || this == TOURNIQUET; }
    public boolean injection() { return this == FENTANYL || this == ANESTHETIC; }
}
