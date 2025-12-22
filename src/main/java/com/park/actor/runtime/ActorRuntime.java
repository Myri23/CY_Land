package com.park.actor.runtime;

import com.park.actor.core.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Runtime du système d'acteurs.
 * Gère le cycle de vie des acteurs : création, lookup, arrêt.
 * 
 * <p>Chaque acteur créé possède :
 * <ul>
 *   <li>Sa propre mailbox (file de messages)</li>
 *   <li>Son propre thread de traitement</li>
 * </ul>
 */
@Component
public class ActorRuntime {
    
    private final ConcurrentMap<String, LocalActorRef> actors = new ConcurrentHashMap<>();
    
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("actor-", 0).factory()
    );
    
    /**
     * Récupère ou crée un acteur.
     * Si l'acteur existe déjà, retourne la référence existante.
     * Sinon, crée l'acteur via la factory et démarre sa boucle de traitement.
     *
     * @param id identifiant unique de l'acteur
     * @param factory factory pour créer l'acteur si nécessaire
     * @return référence vers l'acteur
     */
    public ActorRef getOrCreate(String id, ActorFactory factory) {
        return actors.computeIfAbsent(id, key -> {
            Actor actor = factory.create(key);
            LocalActorRef ref = new LocalActorRef(key, actor, this);
            executor.submit(ref::processLoop);
            System.out.printf("[ACTOR] Created actor: %s%n", key);
            return ref;
        });
    }
    
    /**
     * Recherche un acteur par son identifiant.
     *
     * @param id identifiant de l'acteur
     * @return la référence ou null si non trouvé
     */
    public ActorRef find(String id) {
        return actors.get(id);
    }
    
    /**
     * Vérifie si un acteur existe.
     */
    public boolean exists(String id) {
        return actors.containsKey(id);
    }
    
    /**
     * Retourne le nombre d'acteurs actifs.
     */
    public int actorCount() {
        return actors.size();
    }
    
    /**
     * Arrêt propre du runtime.
     */
    @PreDestroy
    public void shutdown() {
        System.out.println("[ACTOR] Shutting down actor runtime...");
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[ACTOR] Timeout waiting for actors to stop");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        actors.clear();
        System.out.println("[ACTOR] Runtime stopped");
    }
}
