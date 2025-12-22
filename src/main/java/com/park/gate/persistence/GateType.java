package com.park.gate.persistence;

/**
 * Types de portes disponibles dans le parc.
 */
public enum GateType {
    MAIN_GATE,      // Entrée principale (tickets classiques et VIP)
    VIP_GATE,       // Entrée VIP (tickets VIP uniquement)
    SERVICE_GATE,   // Entrée de service
    EMERGENCY_EXIT  // Sortie de secours
}
