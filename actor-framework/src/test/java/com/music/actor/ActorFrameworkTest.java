package com.music.actor;

import com.music.actor.core.*;
import com.music.actor.logging.DefaultActorLogger;
import com.music.actor.runtime.ActorSystemImpl;
import com.music.actor.supervision.OneForOneStrategy;
import com.music.actor.supervision.SupervisorDirective;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires du framework d'acteurs.
 */
class ActorFrameworkTest {
    
    private ActorSystemImpl actorSystem;
    private DefaultActorLogger logger;
    
    @BeforeEach
    void setUp() {
        logger = new DefaultActorLogger("test-system", "logs/test");
        actorSystem = new ActorSystemImpl(
        "test-system", 
        logger, 
        Optional.empty(), 
        WebClient.builder(),
        null,  // CircuitBreakerRegistry - null pour utiliser les défauts
        null);   // RetryRegistry - null pour utiliser les défauts
    }
    
    @AfterEach
    void tearDown() {
        actorSystem.shutdown();
        logger.shutdown();
    }
    
    @Test
    @DisplayName("Création d'un acteur")
    void shouldCreateActor() {
        ActorRef ref = actorSystem.actorOf("test-actor", id -> new NoOpActor());
        
        assertThat(ref).isNotNull();
        assertThat(ref.id()).isEqualTo("test-actor");
        assertThat(ref.isLocal()).isTrue();
        assertThat(actorSystem.actorCount()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("Même acteur retourné si déjà créé")
    void shouldReturnSameActorIfExists() {
        ActorRef first = actorSystem.actorOf("same-actor", id -> new NoOpActor());
        ActorRef second = actorSystem.actorOf("same-actor", id -> new NoOpActor());
        
        assertThat(first).isSameAs(second);
        assertThat(actorSystem.actorCount()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("Tell - envoi asynchrone de messages")
    void shouldReceiveMessages() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger counter = new AtomicInteger(0);
        
        ActorRef ref = actorSystem.actorOf("counter", id -> new Actor() {
            @Override
            public void onReceive(Message message, ActorContext context) {
                counter.incrementAndGet();
                latch.countDown();
            }
        });
        
        ref.tell(new TestMessage("1"));
        ref.tell(new TestMessage("2"));
        ref.tell(new TestMessage("3"));
        
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        
        assertThat(completed).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Ask - envoi synchrone avec réponse")
    void shouldReceiveResponseWithAsk() throws Exception {
        ActorRef ref = actorSystem.actorOf("echo", id -> new EchoActor());
        
        String response = ref.<String>ask(new TestMessage("hello"), Duration.ofSeconds(5))
                .get(5, TimeUnit.SECONDS);
        
        assertThat(response).isEqualTo("Echo: hello");
    }
    
    @Test
    @DisplayName("Messages traités dans l'ordre FIFO")
    void shouldProcessMessagesInOrder() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        StringBuilder order = new StringBuilder();
        
        ActorRef ref = actorSystem.actorOf("ordered", id -> new Actor() {
            @Override
            public void onReceive(Message message, ActorContext context) {
                if (message instanceof OrderedMessage om) {
                    order.append(om.order());
                }
                latch.countDown();
            }
        });
        
        ref.tell(new OrderedMessage(1));
        ref.tell(new OrderedMessage(2));
        ref.tell(new OrderedMessage(3));
        
        latch.await(2, TimeUnit.SECONDS);
        
        assertThat(order.toString()).isEqualTo("123");
    }
    
    @Test
    @DisplayName("Blocage et déblocage d'un acteur")
    void shouldBlockAndUnblockActor() throws InterruptedException {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        ActorRef ref = actorSystem.actorOf("blockable", id -> new Actor() {
            @Override
            public void onReceive(Message message, ActorContext context) {
                processedCount.incrementAndGet();
            }
        });
        
        // Envoyer un message avant le blocage
        ref.tell(new TestMessage("before"));
        Thread.sleep(100);
        assertThat(processedCount.get()).isEqualTo(1);
        
        // Bloquer l'acteur
        actorSystem.block("blockable");
        assertThat(actorSystem.isBlocked("blockable")).isTrue();
        
        // Envoyer un message pendant le blocage
        ref.tell(new TestMessage("during"));
        Thread.sleep(100);
        assertThat(processedCount.get()).isEqualTo(1); // Pas traité
        
        // Débloquer l'acteur
        actorSystem.unblock("blockable");
        Thread.sleep(200);
        assertThat(processedCount.get()).isEqualTo(2); // Maintenant traité
    }
    
    @Test
    @DisplayName("Supervision - redémarrage après erreur")
    void shouldRestartActorAfterError() throws InterruptedException {
        AtomicInteger restartCount = new AtomicInteger(0);
        CountDownLatch errorLatch = new CountDownLatch(1);
        CountDownLatch recoveryLatch = new CountDownLatch(1);
        
        var strategy = OneForOneStrategy.builder()
                .maxRestarts(3)
                .withinTimeRange(60_000)
                .decider(cause -> SupervisorDirective.RESTART)
                .build();
        
        ActorRef ref = actorSystem.actorOf("supervised", id -> new Actor() {
            @Override
            public void onReceive(Message message, ActorContext context) {
                if (message instanceof ErrorMessage) {
                    errorLatch.countDown();
                    throw new RuntimeException("Simulated error");
                }
                if (message instanceof TestMessage) {
                    recoveryLatch.countDown();
                }
            }
            
            @Override
            public void postRestart(Throwable reason, ActorContext context) {
                restartCount.incrementAndGet();
                Actor.super.postRestart(reason, context);
            }
        }, strategy);
        
        // Provoquer une erreur
        ref.tell(new ErrorMessage());
        errorLatch.await(2, TimeUnit.SECONDS);
        
        // Attendre le redémarrage
        Thread.sleep(500);
        
        // Envoyer un message après la récupération
        ref.tell(new TestMessage("after"));
        boolean recovered = recoveryLatch.await(2, TimeUnit.SECONDS);
        
        assertThat(recovered).isTrue();
        assertThat(restartCount.get()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("Arrêt d'un acteur")
    void shouldStopActor() {
        ActorRef ref = actorSystem.actorOf("stoppable", id -> new NoOpActor());
        
        assertThat(actorSystem.actorCount()).isEqualTo(1);
        
        actorSystem.stop(ref);
        
        assertThat(actorSystem.findActor("stoppable")).isEmpty();
    }
    
    // ==================== Test Messages ====================
    
    record TestMessage(String content) implements Message {}
    record OrderedMessage(int order) implements Message {}
    record ErrorMessage() implements Message {}
    
    // ==================== Test Actors ====================
    
    static class NoOpActor implements Actor {
        @Override
        public void onReceive(Message message, ActorContext context) {
            // No operation
        }
    }
    
    static class EchoActor implements Actor {
        @Override
        public void onReceive(Message message, ActorContext context) {
            if (message instanceof TestMessage tm) {
                // Répondre si c'est une requête ask
                if (context instanceof com.music.actor.runtime.LocalActorRef.AskContext askContext) {
                    askContext.reply("Echo: " + tm.content());
                }
            }
        }
    }
}
