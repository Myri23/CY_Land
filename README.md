# CY Land - Framework d'Acteurs Distribués

Framework d'acteurs distribués inspiré d'Akka, développé avec Spring Boot pour la gestion d'un parc d'attractions.

## 📋 Table des matières

1. [Architecture](#architecture)
2. [Fonctionnalités du Framework](#fonctionnalités-du-framework)
3. [Prérequis](#prérequis)
4. [Installation et Lancement](#installation-et-lancement)
5. [Guide de Test](#guide-de-test)
6. [API Reference](#api-reference)
7. [Concepts Spring Boot Utilisés](#concepts-spring-boot-utilisés)
8. [Structure du Projet](#structure-du-projet)

---

## 🏗️ Architecture

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

## ⚡ Fonctionnalités du Framework

### 1. Gestion des Acteurs
- **Création dynamique** : `actorSystem.actorOf(id, factory)`
- **Destruction** : `actorSystem.stop(actorRef)`
- **Blocage/Déblocage** : `actorSystem.block(id)` / `actorSystem.unblock(id)`

### 2. Communication Asynchrone et Synchrone
- **Tell (fire-and-forget)** : `actorRef.tell(message)`
- **Ask (request-response)** : `actorRef.ask(message, timeout)` → `CompletableFuture<T>`

### 3. Communication Inter-Microservices
- Découverte via **Spring Cloud Eureka**
- `context.lookupRemote("service-name", "actor-id")` → `RemoteActorRef`
- Communication HTTP via **WebClient**

### 4. Supervision et Tolérance aux Pannes
- **OneForOneStrategy** : Redémarre uniquement l'acteur en erreur
- **AllForOneStrategy** : Redémarre tous les enfants si un échoue
- Directives : `RESUME`, `RESTART`, `STOP`, `ESCALATE`

### 5. Scalabilité (Auto-Scaling)
- Pool d'acteurs avec scaling automatique
- Configuration : min/max instances, seuils de charge
- Distribution round-robin des messages

### 6. Système de Logs
- Logs structurés en JSON (JSONL)
- Fichiers par jour : `logs/actors/actors-YYYY-MM-DD.jsonl`
- Événements : création, arrêt, messages, erreurs, redémarrages

---

## 📦 Prérequis

| Outil | Version | Vérification |
|-------|---------|--------------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | Latest | `docker --version` |

---

## 🚀 Installation et Lancement

### Option 1 : Lancement Local

```bash
# 1. Cloner le projet
git clone <repository-url>
cd cy-land

# 2. Démarrer RabbitMQ
docker compose up -d rabbitmq

# 3. Compiler le projet
mvn clean install -DskipTests

# 4. Démarrer Eureka Server (Terminal 1)
cd eureka-server
mvn spring-boot:run

# 5. Démarrer Gate Service (Terminal 2)
cd gate-service
mvn spring-boot:run

# 6. Démarrer Ride Service (Terminal 3)
cd ride-service
mvn spring-boot:run
```

### Option 2 : Docker Compose (tout en un)

```bash
# Construire et démarrer tous les services
docker compose up --build

# Arrêter
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

## 🧪 Guide de Test

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

# Débloquer une porte
curl -X POST "http://localhost:8081/gate/G1/unblock"

# Créer une porte
curl -X POST "http://localhost:8081/gate?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"

# Supprimer une porte
curl -X DELETE "http://localhost:8081/gate/G3"

# Métriques du pool de scanners
curl http://localhost:8081/gate/pool/metrics
```

### Tests Ride Service

```bash
# Lister les attractions
curl http://localhost:8082/rides

# État d'une attraction
curl http://localhost:8082/rides/rc/state

# Rejoindre la queue
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V001"
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V002"

# Démarrer un cycle
curl -X POST "http://localhost:8082/rides/rc/start-cycle"

# Signaler une panne
curl -X POST "http://localhost:8082/rides/vr/report-fault?faultType=MECHANICAL&description=Panne"

# Réparer une attraction
curl -X POST "http://localhost:8082/rides/vr/repair"
```

### Tests API de Gestion des Acteurs

```bash
# Lister tous les acteurs
curl http://localhost:8081/actors

# Détails d'un acteur
curl http://localhost:8081/actors/G1

# Bloquer un acteur
curl -X POST http://localhost:8081/actors/G1/block

# Débloquer un acteur
curl -X POST http://localhost:8081/actors/G1/unblock

# Supprimer un acteur
curl -X DELETE http://localhost:8081/actors/G1

# Métriques du système
curl http://localhost:8081/actors/system/metrics
```

### Exécuter les Tests Automatisés

```bash
# Tous les tests
mvn test

# Tests d'un module spécifique
cd actor-framework && mvn test
cd gate-service && mvn test
```

---

## 📚 API Reference

### Gate Service Endpoints

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/gate/{gateId}/scan` | Scan asynchrone (tell) |
| POST | `/gate/{gateId}/scan-sync` | Scan synchrone (ask) |
| GET | `/gate/{gateId}/status` | Statut d'une porte |
| POST | `/gate/{gateId}/block` | Bloquer une porte |
| POST | `/gate/{gateId}/unblock` | Débloquer une porte |
| POST | `/gate` | Créer une porte |
| DELETE | `/gate/{gateId}` | Supprimer une porte |
| GET | `/gate` | Lister les portes |
| GET | `/gate/pool/metrics` | Métriques du pool |

### Ride Service Endpoints

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/rides` | Lister les attractions |
| GET | `/rides/{rideId}/state` | État d'une attraction |
| POST | `/rides/{rideId}/join` | Rejoindre la queue |
| POST | `/rides/{rideId}/start-cycle` | Démarrer un cycle |
| POST | `/rides/{rideId}/report-fault` | Signaler une panne |
| POST | `/rides/{rideId}/repair` | Réparer |
| POST | `/rides/{rideId}/block` | Bloquer |
| POST | `/rides/{rideId}/unblock` | Débloquer |

### Actor Management Endpoints (tous les services)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/actors` | Lister les acteurs |
| GET | `/actors/{id}` | Détails d'un acteur |
| POST | `/actors/{id}/block` | Bloquer un acteur |
| POST | `/actors/{id}/unblock` | Débloquer un acteur |
| DELETE | `/actors/{id}` | Supprimer un acteur |
| POST | `/actors/{id}/tell` | Envoyer un message (inter-service) |
| POST | `/actors/{id}/ask` | Requête synchrone (inter-service) |
| GET | `/actors/system/metrics` | Métriques système |

---

## 🔧 Concepts Spring Boot Utilisés

### Annotations Principales
- `@SpringBootApplication` : Point d'entrée
- `@EnableDiscoveryClient` : Enregistrement Eureka
- `@EnableEurekaServer` : Serveur Eureka
- `@Component`, `@Service`, `@Repository` : Injection de dépendances
- `@RestController`, `@RequestMapping` : API REST
- `@PostConstruct`, `@PreDestroy` : Lifecycle hooks

### Spring Cloud
- **Eureka Client/Server** : Découverte de services
- **Spring Cloud Stream** : Messaging avec RabbitMQ
- **WebClient** : Communication HTTP réactive

### Fonctionnalités Avancées
- **Virtual Threads (Java 21)** : `Executors.newThreadPerTaskExecutor()`
- **Records** : Messages immutables
- **Sealed Classes** : Hiérarchie de messages contrôlée
- **Pattern Matching** : `switch` sur les types de messages
- **CompletableFuture** : Ask pattern asynchrone

---

## 📁 Structure du Projet

```
cy-land/
├── pom.xml                          # Parent POM
├── docker-compose.yml               # Orchestration Docker
│
├── actor-framework/                 # Module Framework (réutilisable)
│   ├── pom.xml
│   └── src/main/java/com/music/actor/
│       ├── core/                    # Interfaces de base
│       │   ├── Actor.java
│       │   ├── ActorRef.java
│       │   ├── ActorContext.java
│       │   ├── ActorSystem.java
│       │   └── Message.java
│       ├── runtime/                 # Implémentations
│       │   ├── ActorSystemImpl.java
│       │   ├── LocalActorRef.java
│       │   ├── RemoteActorRef.java
│       │   └── ActorManagementController.java
│       ├── supervision/             # Stratégies de supervision
│       │   ├── SupervisorStrategy.java
│       │   ├── OneForOneStrategy.java
│       │   └── AllForOneStrategy.java
│       ├── scalability/             # Auto-scaling
│       │   ├── ActorPool.java
│       │   ├── ScalingConfig.java
│       │   └── AutoScalingActorPool.java
│       └── logging/                 # Logs structurés
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

## 📖 Références

- [Akka Documentation](https://doc.akka.io/)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Cloud Netflix Eureka](https://spring.io/projects/spring-cloud-netflix)
- [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)

---

## 👥 Auteurs

Projet réalisé dans le cadre du module JEE, ING2, GI - Septembre 2025
