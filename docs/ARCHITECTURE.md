# Diagrammes d'Architecture - CY Land Actor Framework

Ce document contient les diagrammes d'architecture du projet CY Land, un framework d'acteurs distribués inspiré d'Akka.

## Table des matières

1. [Architecture Globale](#1-architecture-globale)
2. [Modèle d'Acteurs](#2-modèle-dacteurs)
3. [Communication Inter-Services](#3-communication-inter-services)
4. [Cycle de Vie des Acteurs](#4-cycle-de-vie-des-acteurs)
5. [Stratégies de Supervision](#5-stratégies-de-supervision)
6. [Auto-Scaling Pool](#6-auto-scaling-pool)
7. [Flux de Données](#7-flux-de-données)
8. [Diagramme de Classes](#8-diagramme-de-classes)
9. [Tolérance aux Pannes avec Resilience4j](#9-tolérance-aux-pannes-avec-resilience4j)
10. [Tests d'Intégration Inter-Services](#10-tests-dintégration-inter-services)

---

## 1. Architecture Globale

```mermaid
graph TB
    subgraph "Client Layer"
        CLIENT[Client HTTP/REST]
        POSTMAN[Postman Collection]
    end
    
    subgraph "Service Discovery"
        EUREKA[Eureka Server<br/>:8761]
    end
    
    subgraph "Message Broker"
        RABBIT[RabbitMQ<br/>:5672/:15672]
        EXCHANGE[park.events<br/>Exchange]
    end
    
    subgraph "Gate Service :8081"
        GS_API[REST API<br/>GateController]
        GS_SERVICE[GateService]
        GS_ACTORS[Actor System]
        GS_RESILIENCE[Resilience4j<br/>Circuit Breaker + Retry]
        
        subgraph "Gate Actors"
            G1[GateActor G1]
            G2[GateActor G2]
            VIP[GateActor VIP]
        end
        
        subgraph "Scanner Pool"
            SP[AutoScalingActorPool]
            SW1[Scanner Worker 1]
            SW2[Scanner Worker 2]
            SWN[Scanner Worker N]
        end
    end
    
    subgraph "Ride Service :8082"
        RS_API[REST API<br/>RideController]
        RS_SERVICE[RideService]
        RS_ACTORS[Actor System]
        RS_RESILIENCE[Resilience4j<br/>Circuit Breaker + Retry]
        
        subgraph "Ride Actors"
            RC[RideActor RC<br/>RollerCoaster]
            GR[RideActor GR<br/>GrandeRoue]
            VR[RideActor VR<br/>SimulateurVR]
        end
        
        TRACKER[VisitorTracker<br/>Actor]
    end
    
    CLIENT --> GS_API
    CLIENT --> RS_API
    POSTMAN -.-> CLIENT
    
    GS_API --> GS_SERVICE
    GS_SERVICE --> GS_ACTORS
    GS_SERVICE --> GS_RESILIENCE
    GS_ACTORS --> G1
    GS_ACTORS --> G2
    GS_ACTORS --> VIP
    GS_ACTORS --> SP
    SP --> SW1
    SP --> SW2
    SP --> SWN
    
    RS_API --> RS_SERVICE
    RS_SERVICE --> RS_ACTORS
    RS_SERVICE --> RS_RESILIENCE
    RS_ACTORS --> RC
    RS_ACTORS --> GR
    RS_ACTORS --> VR
    RS_ACTORS --> TRACKER
    
    GS_ACTORS -.->|register| EUREKA
    RS_ACTORS -.->|register| EUREKA
    
    G1 -->|publish| EXCHANGE
    G2 -->|publish| EXCHANGE
    VIP -->|publish| EXCHANGE
    
    EXCHANGE -->|consume| TRACKER
    
    GS_ACTORS <-.->|lookup| EUREKA
    RS_ACTORS <-.->|lookup| EUREKA
    
    G1 -.->|RemoteActorRef<br/>+ Circuit Breaker| TRACKER
    
    style EUREKA fill:#f9f,stroke:#333
    style RABBIT fill:#ff9,stroke:#333
    style G1 fill:#9f9,stroke:#333
    style RC fill:#9ff,stroke:#333
    style GS_RESILIENCE fill:#fcc,stroke:#333
    style RS_RESILIENCE fill:#fcc,stroke:#333
```

---

## 2. Modèle d'Acteurs

```mermaid
graph LR
    subgraph "Actor Model"
        direction TB
        
        subgraph "Actor"
            STATE[État Interne<br/>Encapsulé]
            MAILBOX[Mailbox<br/>BlockingQueue]
            BEHAVIOR[Comportement<br/>onReceive]
        end
        
        MSG1[Message 1] --> MAILBOX
        MSG2[Message 2] --> MAILBOX
        MSG3[Message 3] --> MAILBOX
        
        MAILBOX -->|FIFO| BEHAVIOR
        BEHAVIOR --> STATE
    end
    
    subgraph "Communication Patterns"
        TELL[tell<br/>Fire & Forget]
        ASK[ask<br/>Request-Response]
        
        SENDER1[Sender] -->|tell| ACTOR1[Actor]
        SENDER2[Sender] -->|ask| ACTOR2[Actor]
        ACTOR2 -->|reply| FUTURE[CompletableFuture]
        FUTURE --> SENDER2
    end
    
    style STATE fill:#ffd,stroke:#333
    style MAILBOX fill:#dff,stroke:#333
    style BEHAVIOR fill:#dfd,stroke:#333
```

---

## 3. Communication Inter-Services

```mermaid
sequenceDiagram
    participant Client
    participant GateAPI as Gate API
    participant GateActor
    participant CircuitBreaker as Circuit Breaker
    participant Eureka
    participant RabbitMQ
    participant RideService
    participant VisitorTracker
    
    Client->>GateAPI: POST /gate/G1/scan?ticketId=T001
    GateAPI->>GateActor: tell(ScanTicket)
    
    activate GateActor
    GateActor->>GateActor: Validate ticket
    GateActor->>RabbitMQ: publish(VisitorEntered)
    
    GateActor->>Eureka: lookup(ride-service)
    Eureka-->>GateActor: ServiceInstance URL
    
    GateActor->>CircuitBreaker: Check state
    
    alt Circuit CLOSED
        CircuitBreaker-->>GateActor: Allow request
        GateActor->>RideService: POST /actors/visitor-tracker/tell
        RideService-->>GateActor: 202 Accepted
        CircuitBreaker->>CircuitBreaker: Record success
    else Circuit OPEN
        CircuitBreaker-->>GateActor: Reject (fail-fast)
        GateActor->>GateActor: Log warning, continue
    else Service unavailable
        GateActor->>RideService: POST /actors/visitor-tracker/tell
        RideService--xGateActor: Timeout/Error
        CircuitBreaker->>CircuitBreaker: Record failure
        Note over CircuitBreaker: After 5 failures: OPEN
    end
    
    deactivate GateActor
    
    RabbitMQ-->>VisitorTracker: consume(VisitorEntered)
    
    activate VisitorTracker
    VisitorTracker->>VisitorTracker: Track visitor
    deactivate VisitorTracker
    
    GateAPI-->>Client: 202 Accepted
```

---

## 4. Cycle de Vie des Acteurs

```mermaid
stateDiagram-v2
    [*] --> CREATED: actorOf()
    
    CREATED --> STARTING: start()
    STARTING --> RUNNING: preStart() success
    STARTING --> FAILED: preStart() error
    
    RUNNING --> BLOCKED: block()
    BLOCKED --> RUNNING: unblock()
    
    RUNNING --> RESTARTING: error + RESTART directive
    RESTARTING --> RUNNING: postRestart() success
    RESTARTING --> FAILED: max restarts exceeded
    
    RUNNING --> STOPPING: stop()
    BLOCKED --> STOPPING: stop()
    
    STOPPING --> STOPPED: postStop()
    FAILED --> [*]
    STOPPED --> [*]
    
    note right of RUNNING
        Processing messages
        from mailbox
    end note
    
    note right of BLOCKED
        Messages accumulate
        but not processed
    end note
    
    note right of RESTARTING
        preRestart() → postRestart()
        State can be preserved
    end note
```

---

## 5. Stratégies de Supervision

```mermaid
graph TB
    subgraph "OneForOneStrategy"
        direction TB
        PARENT1[Parent Actor]
        CHILD1A[Child A]
        CHILD1B[Child B ❌]
        CHILD1C[Child C]
        
        PARENT1 --> CHILD1A
        PARENT1 --> CHILD1B
        PARENT1 --> CHILD1C
        
        CHILD1B -->|error| DECISION1{Decide}
        DECISION1 -->|RESUME| RESUME1[Continue with state]
        DECISION1 -->|RESTART| RESTART1[Restart Child B only]
        DECISION1 -->|STOP| STOP1[Stop Child B]
        DECISION1 -->|ESCALATE| ESCALATE1[Escalate to grandparent]
    end
    
    subgraph "AllForOneStrategy"
        direction TB
        PARENT2[Parent Actor]
        CHILD2A[Child A]
        CHILD2B[Child B ❌]
        CHILD2C[Child C]
        
        PARENT2 --> CHILD2A
        PARENT2 --> CHILD2B
        PARENT2 --> CHILD2C
        
        CHILD2B -->|error| DECISION2{Decide}
        DECISION2 -->|RESTART| RESTART2[Restart ALL children]
    end
    
    style CHILD1B fill:#f99,stroke:#333
    style CHILD2B fill:#f99,stroke:#333
```

---

## 6. Auto-Scaling Pool

```mermaid
graph TB
    subgraph "AutoScalingActorPool"
        CONFIG[ScalingConfig<br/>min=2, max=10<br/>scaleUp=70%, scaleDown=20%]
        SCHEDULER[Scaling Scheduler<br/>check every 5s]
        METRICS[Pool Metrics<br/>+ Mailbox Size Counter]
        
        subgraph "Worker Pool"
            W1[Worker 1<br/>mailbox: N msgs]
            W2[Worker 2<br/>mailbox: N msgs]
            W3[Worker 3<br/>scaled up]
            WN[Worker N...]
        end
        
        RR[Round-Robin<br/>Distribution]
    end
    
    LOAD[Incoming Messages] --> RR
    RR --> W1
    RR --> W2
    RR --> W3
    RR --> WN
    
    SCHEDULER -->|check utilization| METRICS
    METRICS -->|>70%| SCALE_UP[Scale Up<br/>+1 worker]
    METRICS -->|<20%| SCALE_DOWN[Scale Down<br/>-1 worker]
    
    CONFIG --> SCHEDULER
    
    W1 -.->|mailboxSize| METRICS
    W2 -.->|mailboxSize| METRICS
    W3 -.->|mailboxSize| METRICS
    
    style CONFIG fill:#ffd,stroke:#333
    style METRICS fill:#dff,stroke:#333
```

---

## 7. Flux de Données

```mermaid
flowchart LR
    subgraph "Gate Service"
        SCAN[Scan Ticket] --> VALIDATE{Valid?}
        VALIDATE -->|Yes| ACCEPT[Accept Entry]
        VALIDATE -->|No| REJECT[Reject]
        
        ACCEPT --> SNAPSHOT[Save Snapshot]
        ACCEPT --> PUBLISH[Publish Event]
        ACCEPT --> NOTIFY[Notify Ride Service<br/>via Circuit Breaker]
    end
    
    subgraph "RabbitMQ"
        PUBLISH --> EXCHANGE[park.events]
        EXCHANGE --> QUEUE1[analytics queue]
        EXCHANGE --> QUEUE2[logging queue]
        EXCHANGE --> QUEUE3[ride-service queue]
    end
    
    subgraph "Ride Service"
        QUEUE3 --> CONSUME[Consume Event]
        CONSUME --> TRACKER[Visitor Tracker]
        
        JOIN[Join Queue] --> RIDE{Ride Open?}
        RIDE -->|Yes| QUEUE_ADD[Add to Queue]
        RIDE -->|No| REJECT2[Reject]
        
        QUEUE_ADD --> AUTO{Queue Full?}
        AUTO -->|Yes| START[Auto-Start Cycle]
        
        START --> LOAD[Load Passengers]
        LOAD --> RUN[Run Cycle]
        RUN --> FINISH[Finish Cycle]
        FINISH --> NEXT{More waiting?}
        NEXT -->|Yes| START
    end
    
    style VALIDATE fill:#ffd,stroke:#333
    style RIDE fill:#ffd,stroke:#333
    style AUTO fill:#ffd,stroke:#333
```

---

## 8. Diagramme de Classes

```mermaid
classDiagram
    class Actor {
        <<interface>>
        +onReceive(Message, ActorContext)
        +preStart(ActorContext)
        +postStop(ActorContext)
        +preRestart(Throwable, Message, ActorContext)
        +postRestart(Throwable, ActorContext)
    }
    
    class ActorRef {
        <<interface>>
        +id() String
        +path() String
        +tell(Message)
        +ask(Message, Duration) CompletableFuture~T~
        +isLocal() boolean
    }
    
    class ActorSystem {
        <<interface>>
        +name() String
        +actorOf(String, ActorFactory) ActorRef
        +findActor(String) Optional~ActorRef~
        +remoteActorOf(String, String) ActorRef
        +stop(ActorRef)
        +block(String)
        +unblock(String)
    }
    
    class ActorContext {
        <<interface>>
        +id() String
        +self() ActorRef
        +sender() Optional~ActorRef~
        +lookup(String) ActorRef
        +lookupRemote(String, String) ActorRef
        +createChild(String, ActorFactory) ActorRef
        +snapshot(Object)
        +restore(Class~T~) T
    }
    
    class LocalActorRef {
        -id: String
        -actor: Actor
        -mailbox: BlockingQueue~Envelope~
        -state: ActorState
        +tell(Message)
        +ask(Message, Duration) CompletableFuture
        +block()
        +unblock()
        +mailboxSize() int
    }
    
    class RemoteActorRef {
        -id: String
        -serviceName: String
        -serviceUrl: String
        -webClient: WebClient
        -circuitBreaker: CircuitBreaker
        -retry: Retry
        +tell(Message)
        +ask(Message, Duration) CompletableFuture
        +getCircuitBreakerState() State
        +getCircuitBreakerMetrics() Metrics
    }
    
    class SupervisorStrategy {
        <<interface>>
        +decide(ActorRef, Throwable, int) SupervisorDirective
        +maxRestarts() int
        +withinTimeRangeMs() long
        +isAllForOne() boolean
    }
    
    class OneForOneStrategy {
        -maxRestarts: int
        -withinTimeRangeMs: long
        -decider: Function
    }
    
    class AllForOneStrategy {
        -maxRestarts: int
        -withinTimeRangeMs: long
        -decider: Function
    }
    
    class AutoScalingActorPool {
        -poolName: String
        -actors: List~ActorRef~
        -config: ScalingConfig
        -pendingMessagesProvider: Function
        +getActor() ActorRef
        +scaleUp() ActorRef
        +scaleDown()
        +getMetrics() PoolMetrics
    }
    
    class ResilienceConfig {
        +circuitBreakerRegistry() CircuitBreakerRegistry
        +retryRegistry() RetryRegistry
        +gateServiceCircuitBreaker() CircuitBreaker
        +rideServiceCircuitBreaker() CircuitBreaker
        +remoteActorRetry() Retry
    }
    
    class GateActor {
        -gateId: String
        -scannedTickets: Set~String~
        -operational: boolean
        +onReceive(Message, ActorContext)
    }
    
    class RideActor {
        -rideId: String
        -queue: Deque~String~
        -capacity: int
        -closed: boolean
        +onReceive(Message, ActorContext)
    }
    
    Actor <|.. GateActor
    Actor <|.. RideActor
    ActorRef <|.. LocalActorRef
    ActorRef <|.. RemoteActorRef
    ActorContext <|.. LocalActorRef
    SupervisorStrategy <|.. OneForOneStrategy
    SupervisorStrategy <|.. AllForOneStrategy
    
    ActorSystem --> ActorRef
    ActorSystem --> SupervisorStrategy
    LocalActorRef --> Actor
    RemoteActorRef --> ResilienceConfig
    AutoScalingActorPool --> ActorRef
```

---

## 9. Tolérance aux Pannes avec Resilience4j

### 9.1 Architecture de Résilience

```mermaid
graph TB
    subgraph "Actor Framework - Resilience Layer"
        REMOTE_REF[RemoteActorRef]
        
        subgraph "Resilience4j Components"
            CB_REGISTRY[CircuitBreakerRegistry]
            RETRY_REGISTRY[RetryRegistry]
            
            CB_GATE[CircuitBreaker<br/>gate-service]
            CB_RIDE[CircuitBreaker<br/>ride-service]
            
            RETRY[Retry<br/>remote-actor]
        end
        
        WEBCLIENT[WebClient<br/>HTTP Reactive]
    end
    
    REMOTE_REF --> CB_REGISTRY
    REMOTE_REF --> RETRY_REGISTRY
    CB_REGISTRY --> CB_GATE
    CB_REGISTRY --> CB_RIDE
    RETRY_REGISTRY --> RETRY
    
    REMOTE_REF --> WEBCLIENT
    
    style CB_REGISTRY fill:#fcc,stroke:#333
    style RETRY_REGISTRY fill:#fcf,stroke:#333
```

### 9.2 États du Circuit Breaker

```mermaid
stateDiagram-v2
    [*] --> CLOSED: Initial state
    
    CLOSED --> OPEN: Failure rate >= 50%<br/>(min 5 calls)
    CLOSED --> CLOSED: Success / Failure < threshold
    
    OPEN --> HALF_OPEN: After 30 seconds
    OPEN --> OPEN: Requests rejected (fail-fast)
    
    HALF_OPEN --> CLOSED: 3 successful calls
    HALF_OPEN --> OPEN: Any failure
    
    note right of CLOSED
        Normal operation
        All requests allowed
        Failures counted
    end note
    
    note right of OPEN
        Service considered down
        Requests fail immediately
        No load on failing service
    end note
    
    note right of HALF_OPEN
        Testing recovery
        Limited requests allowed
        Deciding next state
    end note
```

### 9.3 Mécanisme de Retry avec Backoff Exponentiel

```mermaid
sequenceDiagram
    participant Actor as RemoteActorRef
    participant Retry as Retry Policy
    participant CB as Circuit Breaker
    participant Service as Remote Service
    
    Actor->>Retry: Execute request
    
    Retry->>CB: Check state
    CB-->>Retry: CLOSED (allow)
    
    Retry->>Service: Attempt 1
    Service--xRetry: Timeout/Error
    
    Note over Retry: Wait 500ms (backoff)
    
    Retry->>Service: Attempt 2
    Service--xRetry: Timeout/Error
    
    Note over Retry: Wait 1000ms (backoff x2)
    
    Retry->>Service: Attempt 3
    Service-->>Retry: Success!
    
    Retry-->>Actor: Response
    
    Note over CB: All attempts counted<br/>for circuit breaker
```

### 9.4 Configuration Resilience4j

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| **Circuit Breaker** | | |
| minimumNumberOfCalls | 5 | Appels minimum avant évaluation |
| failureRateThreshold | 50% | Taux d'échec pour ouverture |
| waitDurationInOpenState | 30s | Durée en état OPEN |
| permittedCallsInHalfOpen | 3 | Appels permis en HALF_OPEN |
| slidingWindowSize | 10 | Taille de la fenêtre glissante |
| **Retry** | | |
| maxAttempts | 3 | Nombre maximum de tentatives |
| initialInterval | 500ms | Délai initial entre tentatives |
| multiplier | 2.0 | Multiplicateur backoff exponentiel |
| Séquence | 500ms → 1s → 2s | Délais entre les tentatives |

### 9.5 API de Monitoring Resilience

```mermaid
graph LR
    subgraph "Endpoints /resilience"
        E1[GET /circuit-breakers]
        E2[GET /circuit-breakers/{name}]
        E3[GET /health]
        E4[GET /remote-actors]
    end
    
    subgraph "Réponses"
        R1[Liste des CBs<br/>+ états + métriques]
        R2[Détails CB<br/>succès/échecs/taux]
        R3[HEALTHY / RECOVERING<br/>/ DEGRADED]
        R4[Acteurs distants<br/>+ état CB associé]
    end
    
    E1 --> R1
    E2 --> R2
    E3 --> R3
    E4 --> R4
```

---

## 10. Tests d'Intégration Inter-Services

### 10.1 Architecture de Test

```mermaid
graph TB
    subgraph "Test Environment"
        TEST[InterServiceIntegrationTest<br/>JUnit 5 + Spring Boot Test]
        
        subgraph "Mock Components"
            MOCK_MVC[MockMvc<br/>HTTP Testing]
            TEST_BINDER[TestChannelBinder<br/>Mock RabbitMQ]
            OUTPUT[OutputDestination<br/>Capture Events]
            INPUT[InputDestination<br/>Inject Events]
        end
        
        subgraph "Real Components"
            ACTOR_SYSTEM[ActorSystem]
            GATE_SERVICE[GateService]
            ACTORS[Gate Actors]
        end
    end
    
    TEST --> MOCK_MVC
    TEST --> TEST_BINDER
    TEST_BINDER --> OUTPUT
    TEST_BINDER --> INPUT
    
    MOCK_MVC --> GATE_SERVICE
    GATE_SERVICE --> ACTOR_SYSTEM
    ACTOR_SYSTEM --> ACTORS
    
    ACTORS -.->|publish| OUTPUT
    INPUT -.->|consume| ACTORS
    
    style TEST fill:#9f9,stroke:#333
    style TEST_BINDER fill:#ff9,stroke:#333
```

### 10.2 Couverture des Tests Inter-Services

```mermaid
graph TB
    subgraph "Tests de Publication (4 tests)"
        T1[scanShouldPublishVisitorEnteredEvent]
        T2[rejectedTicketShouldPublishRejectionEvent]
        T3[multipleTicketsShouldGenerateSequentialEvents]
        T4[fullFlowScanToEventToTracking]
    end
    
    subgraph "Tests de Consommation (1 test)"
        T5[shouldConsumeExternalEvents]
    end
    
    subgraph "Tests de Résilience (2 tests)"
        T6[shouldContinueWhenRemoteServiceUnavailable]
        T7[blockingGateShouldNotAffectPendingEvents]
    end
    
    subgraph "Tests de Métriques (2 tests)"
        T8[systemMetricsShouldIncludeLocalActors]
        T9[scannerPoolMetricsShouldBeAvailable]
    end
    
    subgraph "Tests de Charge (1 test)"
        T10[shouldHandleHighLoadOfScans]
    end
    
    style T1 fill:#9f9,stroke:#333
    style T6 fill:#f99,stroke:#333
    style T10 fill:#99f,stroke:#333
```

### 10.3 Flux de Test Complet

```mermaid
sequenceDiagram
    participant Test as JUnit Test
    participant MockMvc
    participant GateService
    participant GateActor
    participant TestBinder as TestChannelBinder
    participant OutputDest as OutputDestination
    
    Test->>MockMvc: POST /gate/G1/scan?ticketId=T001
    MockMvc->>GateService: handleScan()
    GateService->>GateActor: tell(ScanTicket)
    
    activate GateActor
    GateActor->>GateActor: Validate ticket
    GateActor->>TestBinder: publish(VisitorEntered)
    TestBinder->>OutputDest: Store message
    deactivate GateActor
    
    MockMvc-->>Test: 202 Accepted
    
    Test->>Test: Awaitility.await()
    Test->>OutputDest: receive(timeout)
    OutputDest-->>Test: Message<byte[]>
    
    Test->>Test: Assert payload contains ticketId
    Test->>Test: Assert headers correct
```

### 10.4 Bibliothèques de Test Utilisées

| Bibliothèque | Version | Usage |
|--------------|---------|-------|
| JUnit 5 | 5.10+ | Framework de tests |
| Spring Boot Test | 3.4+ | Contexte Spring pour tests |
| MockMvc | - | Tests endpoints REST |
| AssertJ | 3.24+ | Assertions fluides |
| Awaitility | 4.2.0 | Tests asynchrones avec attente conditionnelle |
| Spring Cloud Stream Test Binder | - | Simulation RabbitMQ sans broker réel |

### 10.5 Scénarios de Test Détaillés

```mermaid
flowchart TB
    subgraph "Scénario 1: Publication d'événements"
        S1A[Scan ticket] --> S1B[Vérifier 202 Accepted]
        S1B --> S1C[Attendre événement]
        S1C --> S1D[Valider payload]
        S1D --> S1E[Valider headers]
    end
    
    subgraph "Scénario 2: Résilience"
        S2A[Eureka désactivé] --> S2B[Scan ticket]
        S2B --> S2C[Vérifier succès local]
        S2C --> S2D[Événement publié malgré tout]
    end
    
    subgraph "Scénario 3: Charge"
        S3A[50 tickets] --> S3B[Scans parallèles]
        S3B --> S3C[Tous acceptés]
        S3C --> S3D[Mesurer performance]
        S3D --> S3E[Service opérationnel]
    end
    
    style S1A fill:#9f9,stroke:#333
    style S2A fill:#f99,stroke:#333
    style S3A fill:#99f,stroke:#333
```

---

## Utilisation des Diagrammes

Ces diagrammes peuvent être rendus avec:

1. **GitHub/GitLab**: Support natif de Mermaid dans les fichiers Markdown
2. **VS Code**: Extension "Mermaid Preview"
3. **Mermaid Live Editor**: https://mermaid.live/
4. **Confluence**: Plugin Mermaid
5. **Notion**: Support natif

Pour générer des images PNG/SVG:
```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i ARCHITECTURE.md -o architecture.png
```

---

## Résumé des Composants

| Composant | Description | Localisation |
|-----------|-------------|--------------|
| ActorSystem | Gestion des acteurs locaux et distants | `actor-framework/core/` |
| LocalActorRef | Référence vers acteur local + mailbox | `actor-framework/runtime/` |
| RemoteActorRef | Référence vers acteur distant + Resilience4j | `actor-framework/runtime/` |
| ResilienceConfig | Configuration Circuit Breaker + Retry | `actor-framework/resilience/` |
| ResilienceController | API monitoring résilience | `actor-framework/runtime/` |
| AutoScalingActorPool | Pool avec scaling automatique | `actor-framework/scalability/` |
| GateActor | Acteur porte d'entrée | `gate-service/domain/` |
| RideActor | Acteur attraction | `ride-service/domain/` |
| InterServiceIntegrationTest | Tests inter-services | `gate-service/src/test/` |
