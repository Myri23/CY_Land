package com.park.gate.api;

import com.park.actor.runtime.ActorRuntime;
import com.park.gate.domain.GateActor;
import com.park.gate.domain.ScanTicket;
import com.park.gate.messaging.GateEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Service applicatif pour les opérations sur les portes.
 * Fait le pont entre l'API REST et le système d'acteurs.
 */
@Service
public class GateService {
    
    private final ActorRuntime actorRuntime;
    private final GateEventPublisher eventPublisher;
    
    public GateService(ActorRuntime actorRuntime, GateEventPublisher eventPublisher) {
        this.actorRuntime = actorRuntime;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Traite un scan de ticket sur une porte.
     * Le traitement est asynchrone : le message est déposé dans la mailbox de l'acteur.
     *
     * @param gateId identifiant de la porte (ex: G1, G2)
     * @param ticketId identifiant du ticket scanné
     */
    public void handleScan(String gateId, String ticketId) {
        // Récupère ou crée l'acteur pour cette porte
        var gateRef = actorRuntime.getOrCreate(
                gateId,
                id -> new GateActor(id, eventPublisher)
        );
        
        // Envoi asynchrone du message
        gateRef.tell(new ScanTicket(ticketId));
    }
}
