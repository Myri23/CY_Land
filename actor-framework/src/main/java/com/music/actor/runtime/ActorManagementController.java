package com.music.actor.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.actor.core.ActorRef;
import com.music.actor.core.ActorState;
import com.music.actor.core.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * API REST pour la gestion et le monitoring des acteurs.
 * 
 * Endpoints :
 * - GET /actors : Liste tous les acteurs
 * - GET /actors/{id} : Info sur un acteur
 * - POST /actors/{id}/block : Bloque un acteur
 * - POST /actors/{id}/unblock : Débloque un acteur
 * - DELETE /actors/{id} : Supprime un acteur
 * - POST /actors/{id}/tell : Envoie un message (pour communication inter-services)
 * - POST /actors/{id}/ask : Envoie un message et attend une réponse
 */
@RestController
@RequestMapping("/actors")
public class ActorManagementController {
    
    private final ActorSystemImpl actorSystem;
    private final ObjectMapper objectMapper;
    
    public ActorManagementController(ActorSystemImpl actorSystem) {
        this.actorSystem = actorSystem;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Liste tous les acteurs du système.
     */
    @GetMapping
    public ResponseEntity<List<ActorInfo>> listActors() {
        List<ActorInfo> actors = actorSystem.getAllActors().stream()
                .map(this::toActorInfo)
                .toList();
        return ResponseEntity.ok(actors);
    }
    
    /**
     * Retourne les informations d'un acteur.
     */
    @GetMapping("/{actorId}")
    public ResponseEntity<ActorInfo> getActor(@PathVariable String actorId) {
        return actorSystem.findActor(actorId)
                .map(this::toActorInfo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Bloque un acteur (suspend le traitement des messages).
     */
    @PostMapping("/{actorId}/block")
    public ResponseEntity<Map<String, String>> blockActor(@PathVariable String actorId) {
        if (actorSystem.findActor(actorId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        actorSystem.block(actorId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Actor " + actorId + " blocked",
                "actorId", actorId
        ));
    }
    
    /**
     * Débloque un acteur.
     */
    @PostMapping("/{actorId}/unblock")
    public ResponseEntity<Map<String, String>> unblockActor(@PathVariable String actorId) {
        if (actorSystem.findActor(actorId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        actorSystem.unblock(actorId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Actor " + actorId + " unblocked",
                "actorId", actorId
        ));
    }
    
    /**
     * Supprime un acteur.
     */
    @DeleteMapping("/{actorId}")
    public ResponseEntity<Map<String, String>> deleteActor(@PathVariable String actorId) {
        return actorSystem.findActor(actorId)
                .map(actor -> {
                    actorSystem.stop(actor);
                    return ResponseEntity.ok(Map.of(
                            "status", "SUCCESS",
                            "message", "Actor " + actorId + " stopped",
                            "actorId", actorId
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Envoie un message à un acteur (pour communication inter-services).
     */
    @PostMapping("/{actorId}/tell")
    public ResponseEntity<Map<String, String>> tellActor(
            @PathVariable String actorId,
            @RequestBody RemoteActorRef.RemoteMessage remoteMessage) {
        
        return actorSystem.findActor(actorId)
                .map(actor -> {
                    try {
                        Message message = deserializeMessage(remoteMessage);
                        actor.tell(message);
                        return ResponseEntity.accepted().body(Map.of(
                                "status", "ACCEPTED",
                                "actorId", actorId,
                                "messageType", remoteMessage.messageType()
                        ));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "ERROR",
                                "message", e.getMessage()
                        ));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Envoie un message et attend une réponse (ask pattern inter-services).
     */
    @PostMapping("/{actorId}/ask")
    public ResponseEntity<RemoteActorRef.RemoteResponse> askActor(
            @PathVariable String actorId,
            @RequestBody RemoteActorRef.RemoteMessage remoteMessage,
            @RequestParam(defaultValue = "5000") long timeoutMs) {
        
        return actorSystem.findActor(actorId)
                .map(actor -> {
                    try {
                        Message message = deserializeMessage(remoteMessage);
                        CompletableFuture<Object> future = actor.ask(message);
                        Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                        
                        String resultJson = objectMapper.writeValueAsString(result);
                        return ResponseEntity.ok(new RemoteActorRef.RemoteResponse(
                                result.getClass().getName(), resultJson, null
                        ));
                    } catch (Exception e) {
                        return ResponseEntity.ok(new RemoteActorRef.RemoteResponse(
                                null, null, e.getMessage()
                        ));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Retourne les métriques du système d'acteurs.
     */
    @GetMapping("/system/metrics")
    public ResponseEntity<ActorSystemImpl.SystemMetrics> getSystemMetrics() {
        return ResponseEntity.ok(actorSystem.getMetrics());
    }
    
    private ActorInfo toActorInfo(ActorRef ref) {
        if (ref instanceof LocalActorRef localRef) {
            return new ActorInfo(
                    ref.id(),
                    ref.path(),
                    localRef.getState().name(),
                    localRef.isBlocked(),
                    localRef.mailboxSize(),
                    ref.isLocal()
            );
        }
        return new ActorInfo(ref.id(), ref.path(), "UNKNOWN", false, 0, ref.isLocal());
    }
    
    private Message deserializeMessage(RemoteActorRef.RemoteMessage remoteMessage) throws Exception {
        Class<?> messageClass = Class.forName(remoteMessage.messageType());
        return (Message) objectMapper.readValue(remoteMessage.payload(), messageClass);
    }
    
    /**
     * Information sur un acteur.
     */
    public record ActorInfo(
            String id,
            String path,
            String state,
            boolean blocked,
            int mailboxSize,
            boolean local
    ) {}
}
