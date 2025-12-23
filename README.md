# CY Land - Framework d'Acteurs Distribues

Framework d'acteurs distribues inspire d'Akka, developpe avec Spring Boot pour la gestion d'un parc d'attractions.

## Table des matieres

1. [Architecture](#architecture)
2. [Fonctionnalites du Framework](#fonctionnalites-du-framework)
3. [Prerequis](#prerequis)
4. [Installation et Lancement](#installation-et-lancement)
5. [Guide de Test](#guide-de-test)
6. [API Reference](#api-reference)
7. [Concepts Spring Boot Utilises](#concepts-spring-boot-utilises)
8. [Structure du Projet](#structure-du-projet)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         EUREKA SERVER (8761)                         │
│                     Service Discovery & Registry                     │
└─────────────────────────────────────────────────────────────────────┘
                                    ▲
                    ┌───────────────┴───────────────┐
                    │                               │
┌───────────────────▼───────────────┐  ┌───────────▼───────────────────┐
│      GATE SERVICE (8081)          │  │      RIDE SERVICE (8082)      │
│                                   │  │                               │
│  ┌─────────────────────────────┐  │  │  ┌─────────────────────────┐  │
│  │     Actor Framework         │  │  │  │     Actor Framework     │  │
│  │  ┌────┐ ┌────┐ ┌────┐      │  │  │  │  ┌────┐ ┌────┐ ┌────┐  │  │
│  │  │ G1 │ │ G2 │ │VIP │      │  │  │  │  │ RC │ │ GR │ │ VR │  │  │
│  │  └────┘ └────┘ └────┘      │  │  │  │  └────┘ └────┘ └────┘  │  │
│  │      Gate Actors            │  │  │  │     Ride Actors        │  │
│  └─────────────────────────────┘  │  │  └─────────────────────────┘  │
│                                   │  │                               │
│  ┌─────────────────────────────┐  │  │  ┌─────────────────────────┐  │
│  │   Auto-Scaling Pool         │  │  │  │   Visitor Tracker       │  │
│  │   (2-10 scanner workers)    │  │  │  │   (inter-service comm)  │  │
│  └─────────────────────────────┘  │  │  └─────────────────────────┘  │
└───────────────┬───────────────────┘  └───────────────┬───────────────┘
                │                                      │
                └──────────────┬───────────────────────┘
                               ▼
              ┌────────────────────────────────┐
              │         RABBITMQ (5672)        │
              │      Message Broker            │
              │   park.events exchange         │
              └────────────────────────────────┘
```

---

## Fonctionnalites du Framework

### 1. Gestion des Acteurs
- **Creation dynamique** : `actorSystem.actorOf(id, factory)`
- **Destruction** : `actorSystem.stop(actorRef)`
- **Blocage/Deblocage** : `actorSystem.block(id)` / `actorSystem.unblock(id)`

### 2. Communication Asynchrone et Synchrone
- **Tell (fire-and-forget)** : `actorRef.tell(message)`
- **Ask (request-response)** : `actorRef.ask(message, timeout)` → `CompletableFuture<T>`

### 3. Communication Inter-Microservices
- Decouverte via **Spring Cloud Eureka**
- `context.lookupRemote("service-name", "actor-id")` → `RemoteActorRef`
- Communication HTTP via **WebClient**

### 4. Supervision et Tolerance aux Pannes
- **OneForOneStrategy** : Redemarre uniquement l'acteur en erreur
- **AllForOneStrategy** : Redemarre tous les enfants si un echoue
- Directives : `RESUME`, `RESTART`, `STOP`, `ESCALATE`

### 5. Scalabilite (Auto-Scaling)
- Pool d'acteurs avec scaling automatique
- Configuration : min/max instances, seuils de charge
- Distribution round-robin des messages

### 6. Systeme de Logs
- Logs structures en JSON (JSONL)
- Fichiers par jour : `logs/actors/actors-YYYY-MM-DD.jsonl`
- Evenements : creation, arret, messages, erreurs, redemarrages

---

## Prerequis

| Outil | Version | Verification |
|-------|---------|--------------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | Latest | `docker --version` |

---

## Installation et Lancement

### Option 1 : Lancement Local

```bash
# 1. Cloner le projet
git clone <repository-url>
cd cy-land

# 2. Demarrer RabbitMQ
docker compose up -d rabbitmq

# 3. Compiler le projet
mvn clean install -DskipTests

# 4. Demarrer Eureka Server (Terminal 1)
cd eureka-server
mvn spring-boot:run

# 5. Demarrer Gate Service (Terminal 2)
cd gate-service
mvn spring-boot:run

# 6. Demarrer Ride Service (Terminal 3)
cd ride-service
mvn spring-boot:run
```

### Option 2 : Docker Compose (tout en un)

```bash
# Construire et demarrer tous les services
docker compose up --build

# Arreter
docker compose down
```

### URLs des Services

| Service | URL |
|---------|-----|
| Eureka Dashboard | http://localhost:8761 |
| Gate Service | http://localhost:8081 |
| Ride Service | http://localhost:8082 |
| RabbitMQ Dashboard | http://localhost:15672 (guest/guest) |

---

## Guide de Test

### Tests Gate Service

```bash
# Lister les portes
curl http://localhost:8081/gate

# Scan asynchrone (tell pattern)
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"

# Scan synchrone (ask pattern)
curl -X POST "http://localhost:8081/gate/G1/scan-sync?ticketId=T002"

# Bloquer une porte
curl -X POST "http://localhost:8081/gate/G1/block"

# Debloquer une porte
curl -X POST "http://localhost:8081/gate/G1/unblock"

# Creer une porte
curl -X POST "http://localhost:8081/gate?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"

# Supprimer une porte
curl -X DELETE "http://localhost:8081/gate/G3"

# Metriques du pool de scanners
curl http://localhost:8081/gate/pool/metrics
```

### Tests Ride Service

```bash
# Lister les attractions
curl http://localhost:8082/rides

# Etat d'une attraction
curl http://localhost:8082/rides/rc/state

# Rejoindre la queue
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V001"
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V002"

# Demarrer un cycle
curl -X POST "http://localhost:8082/rides/rc/start-cycle"

# Signaler une panne
curl -X POST "http://localhost:8082/rides/vr/report-fault?faultType=MECHANICAL&description=Panne"

# Reparer une attraction
curl -X POST "http://localhost:8082/rides/vr/repair"
```

### Tests API de Gestion des Acteurs

```bash
# Lister tous les acteurs
curl http://localhost:8081/actors

# Details d'un acteur
curl http://localhost:8081/actors/G1

# Bloquer un acteur
curl -X POST http://localhost:8081/actors/G1/block

# Debloquer un acteur
curl -X POST http://localhost:8081/actors/G1/unblock

# Supprimer un acteur
curl -X DELETE http://localhost:8081/actors/G1

# Metriques du systeme
curl http://localhost:8081/actors/system/metrics
```

### Executer les Tests Automatises

```bash
# Tous les tests
mvn test

# Tests d'un module specifique
cd actor-framework && mvn test
cd gate-service && mvn test
```

---

## API Reference

### Gate Service Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/gate/{gateId}/scan` | Scan asynchrone (tell) |
| POST | `/gate/{gateId}/scan-sync` | Scan synchrone (ask) |
| GET | `/gate/{gateId}/status` | Statut d'une porte |
| POST | `/gate/{gateId}/block` | Bloquer une porte |
| POST | `/gate/{gateId}/unblock` | Debloquer une porte |
| POST | `/gate` | Creer une porte |
| DELETE | `/gate/{gateId}` | Supprimer une porte |
| GET | `/gate` | Lister les portes |
| GET | `/gate/pool/metrics` | Metriques du pool |

### Ride Service Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/rides` | Lister les attractions |
| GET | `/rides/{rideId}/state` | Etat d'une attraction |
| POST | `/rides/{rideId}/join` | Rejoindre la queue |
| POST | `/rides/{rideId}/start-cycle` | Demarrer un cycle |
| POST | `/rides/{rideId}/report-fault` | Signaler une panne |
| POST | `/rides/{rideId}/repair` | Reparer |
| POST | `/rides/{rideId}/block` | Bloquer |
| POST | `/rides/{rideId}/unblock` | Debloquer |

### Actor Management Endpoints (tous les services)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actors` | Lister les acteurs |
| GET | `/actors/{id}` | Details d'un acteur |
| POST | `/actors/{id}/block` | Bloquer un acteur |
| POST | `/actors/{id}/unblock` | Debloquer un acteur |
| DELETE | `/actors/{id}` | Supprimer un acteur |
| POST | `/actors/{id}/tell` | Envoyer un message (inter-service) |
| POST | `/actors/{id}/ask` | Requete synchrone (inter-service) |
| GET | `/actors/system/metrics` | Metriques systeme |

---

## Concepts Spring Boot Utilises

### Annotations Principales
- `@SpringBootApplication` : Point d'entree
- `@EnableDiscoveryClient` : Enregistrement Eureka
- `@EnableEurekaServer` : Serveur Eureka
- `@Component`, `@Service`, `@Repository` : Injection de dependances
- `@RestController`, `@RequestMapping` : API REST
- `@PostConstruct`, `@PreDestroy` : Lifecycle hooks

### Spring Cloud
- **Eureka Client/Server** : Decouverte de services
- **Spring Cloud Stream** : Messaging avec RabbitMQ
- **WebClient** : Communication HTTP reactive

### Fonctionnalites Avancees
- **Virtual Threads (Java 21)** : `Executors.newThreadPerTaskExecutor()`
- **Records** : Messages immutables
- **Sealed Classes** : Hierarchie de messages controlee
- **Pattern Matching** : `switch` sur les types de messages
- **CompletableFuture** : Ask pattern asynchrone

---

## Structure du Projet

```
cy-land/
├── pom.xml                          # Parent POM
├── docker-compose.yml               # Orchestration Docker
│
├── actor-framework/                 # Module Framework (reutilisable)
│   ├── pom.xml
│   └── src/main/java/com/music/actor/
│       ├── core/                    # Interfaces de base
│       │   ├── Actor.java
│       │   ├── ActorRef.java
│       │   ├── ActorContext.java
│       │   ├── ActorSystem.java
│       │   └── Message.java
│       ├── runtime/                 # Implementations
│       │   ├── ActorSystemImpl.java
│       │   ├── LocalActorRef.java
│       │   ├── RemoteActorRef.java
│       │   └── ActorManagementController.java
│       ├── supervision/             # Strategies de supervision
│       │   ├── SupervisorStrategy.java
│       │   ├── OneForOneStrategy.java
│       │   └── AllForOneStrategy.java
│       ├── scalability/             # Auto-scaling
│       │   ├── ActorPool.java
│       │   ├── ScalingConfig.java
│       │   └── AutoScalingActorPool.java
│       └── logging/                 # Logs structures
│           ├── ActorLogger.java
│           └── DefaultActorLogger.java
│
├── eureka-server/                   # Service Discovery
│   ├── pom.xml
│   └── src/main/java/.../EurekaServerApplication.java
│
├── gate-service/                    # Microservice 1
│   ├── pom.xml
│   └── src/main/java/com/music/gate/
│       ├── api/                     # Controllers & Services
│       ├── domain/                  # Acteurs & Messages
│       └── messaging/               # RabbitMQ Publisher
│
└── ride-service/                    # Microservice 2
    ├── pom.xml
    └── src/main/java/com/music/ride/
        ├── api/                     # Controllers & Services
        ├── domain/                  # Acteurs & Messages
        └── messaging/               # RabbitMQ Publisher/Consumer
```

---

## References

- [Akka Documentation](https://doc.akka.io/)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Cloud Netflix Eureka](https://spring.io/projects/spring-cloud-netflix)
- [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)

---

## Auteurs

Projet realise dans le cadre du module JEE, ING2, GI - Septembre 2025

- Paul PITIOT
