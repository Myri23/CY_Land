# 🎢 Gate Service — Micro-service de contrôle d'accès

Un micro-service Spring Boot démontrant l'utilisation d'un **modèle d'acteurs** pour gérer le contrôle d'accès aux portes d'un parc d'attractions, avec publication d'événements sur **RabbitMQ**.

## 📋 Sommaire

- [Contexte du projet](#-contexte-du-projet)
- [Architecture](#-architecture)
- [Technologies](#-technologies)
- [Structure du code](#-structure-du-code)
- [Installation et lancement](#-installation-et-lancement)
- [Utilisation de l'API](#-utilisation-de-lapi)
- [Observer les événements](#-observer-les-événements-rabbitmq)
- [Tests](#-tests)
- [Concepts clés](#-concepts-clés)
- [Évolutions possibles](#-évolutions-possibles)

---

## 🎯 Contexte du projet

Ce projet s'inscrit dans une **formation aux concepts avancés de Spring** :

1. **Micro-services** : Architecture distribuée avec communication asynchrone
2. **Modèle d'acteurs** : Framework maison pour gérer la concurrence sans locks
3. **Messaging** : Publication d'événements via Spring Cloud Stream + RabbitMQ
4. **Résilience** : Base pour implémenter Circuit Breaker, Retry, etc.

### Cas d'usage

Un visiteur scanne son ticket à une porte du parc :
1. L'API REST reçoit la requête
2. Un message est envoyé à l'acteur de la porte concernée
3. L'acteur vérifie si le ticket a déjà été scanné (anti-doublon)
4. Si valide, un événement `VisitorEntered` est publié sur RabbitMQ
5. D'autres services peuvent consommer cet événement (analytics, notifications, etc.)

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        GATE SERVICE                              │
│  ┌──────────┐    ┌─────────────┐    ┌─────────────────────────┐ │
│  │   REST   │───▶│  GateService │───▶│     Actor Runtime       │ │
│  │   API    │    │             │    │  ┌───────┐ ┌───────┐   │ │
│  │ (8081)   │    └─────────────┘    │  │Gate G1│ │Gate G2│   │ │
│  └──────────┘                       │  │ Actor │ │ Actor │   │ │
│                                     │  └───┬───┘ └───┬───┘   │ │
│                                     └──────┼─────────┼───────┘ │
│                                            │         │         │
│                                     ┌──────▼─────────▼───────┐ │
│                                     │   GateEventPublisher   │ │
│                                     │   (Spring Cloud Stream)│ │
│                                     └───────────┬────────────┘ │
└─────────────────────────────────────────────────┼───────────────┘
                                                  │
                                                  ▼
                                     ┌────────────────────────┐
                                     │       RabbitMQ         │
                                     │  Exchange: park.events │
                                     │  RoutingKey: gate.*    │
                                     └────────────────────────┘
```

---

## 🛠 Technologies

| Technologie | Version | Usage |
|-------------|---------|-------|
| Java | 21 | Langage (Virtual Threads) |
| Spring Boot | 3.5.x | Framework applicatif |
| Spring Cloud Stream | 2024.0.0 | Abstraction messaging |
| RabbitMQ | 3.x | Broker de messages |
| JUnit 5 | 5.x | Tests unitaires |
| Testcontainers | 1.20.x | Tests d'intégration |
| Maven | 3.9.x | Build |

---

## 📁 Structure du code

```
src/main/java/com/park/
├── GateServiceApplication.java      # Point d'entrée
│
├── actor/                           # 🎭 FRAMEWORK D'ACTEURS
│   ├── core/                        # Interfaces publiques
│   │   ├── Actor.java              # Contrat d'un acteur
│   │   ├── ActorRef.java           # Référence vers un acteur
│   │   ├── ActorContext.java       # Contexte d'exécution
│   │   ├── ActorFactory.java       # Factory fonctionnelle
│   │   └── Message.java            # Interface marqueur
│   │
│   └── runtime/                     # Implémentation
│       ├── ActorRuntime.java       # Gestionnaire du cycle de vie
│       └── LocalActorRef.java      # Implémentation locale avec mailbox
│
└── gate/                            # 🚪 DOMAINE GATE
    ├── domain/                      # Logique métier
    │   ├── GateActor.java          # Acteur d'une porte
    │   ├── ScanTicket.java         # Message de scan
    │   └── VisitorEntered.java     # Événement publié
    │
    ├── api/                         # Couche API
    │   ├── GateController.java     # REST endpoints
    │   └── GateService.java        # Service applicatif
    │
    ├── config/                      # Configuration Spring
    │   └── GateConfiguration.java  # Initialisation des portes
    │
    └── messaging/                   # Publication événements
        └── GateEventPublisher.java # Pont vers RabbitMQ
```

---

## 🚀 Installation et lancement

### Prérequis

- **Java 21** (JDK)
- **Docker** (pour RabbitMQ)
- **Maven** (ou utiliser le wrapper `./mvnw`)

### 1. Lancer RabbitMQ

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

Console web : http://localhost:15672 (login: `guest` / `guest`)

### 2. Lancer l'application

```bash
# Avec Maven
./mvnw spring-boot:run

# Ou compiler puis exécuter
./mvnw clean package
java -jar target/gate-service-1.0.0-SNAPSHOT.jar
```

L'application démarre sur **http://localhost:8081**

### 3. Vérifier le démarrage

```bash
curl http://localhost:8081/actuator/health
```

---

## 📡 Utilisation de l'API

### Scanner un ticket

```bash
# Scanner le ticket T001 à la porte G1
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

**Réponse (202 Accepted)** :
```json
{
  "status": "SCAN_ACCEPTED",
  "gateId": "G1",
  "ticketId": "T001"
}
```

### Tester l'anti-doublon

```bash
# Premier scan : accepté
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"

# Deuxième scan du même ticket : accepté côté HTTP (202),
# mais l'acteur ne publie PAS de nouvel événement
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

### Status d'une porte

```bash
curl http://localhost:8081/gate/G1/status
```

---

## 🐰 Observer les événements (RabbitMQ)

1. Ouvrir la console RabbitMQ : http://localhost:15672
2. Aller dans **Queues and Streams**
3. Chercher la queue `park.events.analytics` (créée automatiquement)
4. Cliquer sur **Get Message(s)** pour voir les événements

**Format des événements** :
```json
{
  "ticketId": "T001",
  "gateId": "G1",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## ✅ Tests

### Lancer tous les tests

```bash
./mvnw test
```

### Tests disponibles

| Classe | Type | Description |
|--------|------|-------------|
| `GateActorTest` | Unitaire | Logique métier de l'acteur |
| `ActorRuntimeTest` | Unitaire | Framework d'acteurs |
| `GateIntegrationTest` | Intégration | API + messaging (TestBinder) |

### Couverture

```bash
./mvnw test jacoco:report
# Rapport dans target/site/jacoco/index.html
```

---

## 💡 Concepts clés

### Modèle d'acteurs

Un **acteur** est une entité qui :
- Possède un **état privé** (pas d'accès concurrent)
- Communique uniquement par **messages** (pas d'appels directs)
- Traite les messages **séquentiellement** (une mailbox par acteur)

```java
public interface Actor {
    void onReceive(Message message, ActorContext context);
}
```

**Avantages** :
- Pas de locks explicites
- Isolation naturelle de l'état
- Facilité de distribution (acteurs distants)

### Virtual Threads (Java 21)

Le runtime utilise les **Virtual Threads** pour un modèle "un thread par acteur" sans coût mémoire prohibitif :

```java
ExecutorService executor = Executors.newThreadPerTaskExecutor(
    Thread.ofVirtual().name("actor-", 0).factory()
);
```

### Spring Cloud Stream

Abstraction pour le messaging qui permet de changer de broker (RabbitMQ, Kafka, etc.) sans modifier le code applicatif :

```java
streamBridge.send("gateEvents-out-0", event);
```

---

## 🔮 Évolutions possibles

### Court terme

- [ ] **Persistance des snapshots** : Sauvegarder l'état des acteurs (Redis, PostgreSQL)
- [ ] **Acteurs distants** : Communication inter-services via RabbitMQ
- [ ] **Circuit Breaker** : Resilience4j sur les appels externes

### Moyen terme

- [ ] **Deuxième micro-service** : Service de comptage/analytics
- [ ] **Supervision** : Hiérarchie d'acteurs avec stratégies de reprise
- [ ] **Clustering** : Distribution des acteurs sur plusieurs instances

### Long terme

- [ ] **Event Sourcing** : Reconstituer l'état à partir des événements
- [ ] **CQRS** : Séparer lectures et écritures
- [ ] **Saga Pattern** : Transactions distribuées

---

## 📚 Ressources

- [Spring Cloud Stream Reference](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
- [Actor Model (Wikipedia)](https://en.wikipedia.org/wiki/Actor_model)
- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)

---

## 📄 Licence

Projet de formation — Usage libre pour apprentissage.
