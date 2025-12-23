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
    GS_ACTORS --> G1
    GS_ACTORS --> G2
    GS_ACTORS --> VIP
    GS_ACTORS --> SP
    SP --> SW1
    SP --> SW2
    SP --> SWN
    
    RS_API --> RS_SERVICE
    RS_SERVICE --> RS_ACTORS
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
    
    G1 -.->|RemoteActorRef| TRACKER
    
    style EUREKA fill:#f9f,stroke:#333
    style RABBIT fill:#ff9,stroke:#333
    style G1 fill:#9f9,stroke:#333
    style RC fill:#9ff,stroke:#333
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
    
    GateActor->>RideService: POST /actors/visitor-tracker/tell
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
        METRICS[Pool Metrics]
        
        subgraph "Worker Pool"
            W1[Worker 1]
            W2[Worker 2]
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
        ACCEPT --> NOTIFY[Notify Ride Service]
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
    }
    
    class RemoteActorRef {
        -id: String
        -serviceName: String
        -serviceUrl: String
        -webClient: WebClient
        +tell(Message)
        +ask(Message, Duration) CompletableFuture
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
        +getActor() ActorRef
        +scaleUp() ActorRef
        +scaleDown()
        +getMetrics() PoolMetrics
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
    AutoScalingActorPool --> ActorRef
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
