package com.park.gate.api;

import com.park.actor.runtime.ActorRuntime;
import com.park.gate.domain.GateActor;
import com.park.gate.domain.ScanTicket;
import com.park.gate.messaging.GateEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class GateService {
    
    private final ActorRuntime actorRuntime;
    private final GateEventPublisher eventPublisher;
    
    public GateService(ActorRuntime actorRuntime, GateEventPublisher eventPublisher) {
        this.actorRuntime = actorRuntime;
        this.eventPublisher = eventPublisher;
    }
    
    public void handleScan(String gateId, String ticketId) {
        var gateRef = actorRuntime.getOrCreate(gateId, id -> new GateActor(id, eventPublisher));
        gateRef.tell(new ScanTicket(ticketId));
    }
}
