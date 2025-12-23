# CY Land - Framework d'Acteurs Distribues

Framework d'acteurs distribues inspire d'Akka, developpe avec Spring Boot pour la gestion d'un parc d'attractions.

Projet realise dans le cadre du module JEE, ING2 Groupe 1 Equipe 6 

---

## Table des matieres

1. [Presentation du Projet](#presentation-du-projet)
2. [Conformite aux Exigences](#conformite-aux-exigences)
3. [Architecture](#architecture)
4. [Prerequis](#prerequis)
5. [Installation et Lancement](#installation-et-lancement)
   - [macOS](#macos)
   - [Windows 10/11](#windows-1011)
6. [Guide de Test Complet](#guide-de-test-complet)
7. [Tests Automatises](#tests-automatises)
8. [API Reference](#api-reference)
9. [Concepts Spring Boot Utilises](#concepts-spring-boot-utilises)
10. [Structure du Projet](#structure-du-projet)
11. [References](#references)

---

## Presentation du Projet

Ce projet implemente un **framework d'acteurs distribues** inspire de la philosophie d'Akka, permettant de creer des applications concurrentes et distribuees basees sur le modele des acteurs.

L'application de demonstration est **CY Land**, un systeme de gestion de parc d'attractions comprenant :
- **Gate Service** : Gestion des portes d'entree et validation des tickets
- **Ride Service** : Gestion des attractions et files d'attente

---

## Conformite aux Exigences

Le tableau ci-dessous montre comment le projet repond a chaque exigence du cahier des charges :

| Exigence | Implementation | Localisation |
|----------|----------------|--------------|
| **Gestion des acteurs** (creation, destruction, blocage, deblocage) | ActorSystem avec methodes `actorOf()`, `stop()`, `block()`, `unblock()` | `actor-framework/src/main/java/com/music/actor/core/ActorSystem.java` |
| **Communication asynchrone (tell)** | Pattern fire-and-forget via `ActorRef.tell(message)` | `actor-framework/src/main/java/com/music/actor/core/ActorRef.java` |
| **Communication synchrone (ask)** | Pattern request-response via `ActorRef.ask(message, timeout)` retournant `CompletableFuture` | `actor-framework/src/main/java/com/music/actor/runtime/LocalActorRef.java` |
| **Communication intra-microservice** | Acteurs locaux communiquant via mailbox | `actor-framework/src/main/java/com/music/actor/runtime/LocalActorRef.java` |
| **Communication inter-microservices** | RemoteActorRef + Eureka discovery + WebClient | `actor-framework/src/main/java/com/music/actor/runtime/RemoteActorRef.java` |
| **Supervision et tolerance aux pannes** | OneForOneStrategy et AllForOneStrategy avec directives RESUME, RESTART, STOP, ESCALATE | `actor-framework/src/main/java/com/music/actor/supervision/` |
| **Scalabilite** | AutoScalingActorPool avec scale-up/down automatique | `actor-framework/src/main/java/com/music/actor/scalability/AutoScalingActorPool.java` |
| **Systeme de logs** | Logs structures JSON par jour avec trace complete | `actor-framework/src/main/java/com/music/actor/logging/DefaultActorLogger.java` |
| **Application differente du restaurant** | Parc d'attractions avec portes et attractions | `gate-service/` et `ride-service/` |
| **Tests unitaires et integration** | JUnit 5 + Spring Boot Test + MockMvc | `actor-framework/src/test/` et `gate-service/src/test/` |

### Fonctionnalites Supplementaires

| Fonctionnalite | Description |
|----------------|-------------|
| Virtual Threads (Java 21+) | Utilisation des threads virtuels pour une meilleure scalabilite |
| Snapshot/Restore | Persistance de l'etat des acteurs pour recuperation apres crash |
| Scheduling | Planification de messages avec `scheduleOnce()` et `schedulePeriodic()` |
| Messaging RabbitMQ | Communication evenementielle entre services via Spring Cloud Stream |

---

## Architecture

```
+---------------------------------------------------------------------+
|                       EUREKA SERVER (8761)                          |
|                    Service Discovery & Registry                     |
+---------------------------------------------------------------------+
                                  ^
                  +---------------+---------------+
                  |                               |
+----------------------------------+  +----------------------------------+
|      GATE SERVICE (8081)         |  |      RIDE SERVICE (8082)         |
|                                  |  |                                  |
|  +----------------------------+  |  |  +----------------------------+  |
|  |      Actor Framework       |  |  |  |      Actor Framework       |  |
|  |  +------+ +------+ +-----+ |  |  |  |  +----+ +----+ +----+      |  |
|  |  |  G1  | |  G2  | | VIP | |  |  |  |  | RC | | GR | | VR |      |  |
|  |  +------+ +------+ +-----+ |  |  |  |  +----+ +----+ +----+      |  |
|  |       Gate Actors          |  |  |  |      Ride Actors           |  |
|  +----------------------------+  |  |  +----------------------------+  |
|                                  |  |                                  |
|  +----------------------------+  |  |  +----------------------------+  |
|  |   Auto-Scaling Pool        |  |  |  |   Visitor Tracker          |  |
|  |   (2-10 scanner workers)   |  |  |  |   (inter-service comm)     |  |
|  +----------------------------+  |  |  +----------------------------+  |
+-----------------+----------------+  +-----------------+----------------+
                  |                                     |
                  +----------------+--------------------+
                                   v
                  +--------------------------------+
                  |        RABBITMQ (5672)         |
                  |       Message Broker           |
                  |    park.events exchange        |
                  +--------------------------------+
```

### Flux de Communication

1. **Visiteur scanne son ticket** -> Gate Service recoit la requete
2. **GateActor valide le ticket** -> Publie evenement sur RabbitMQ
3. **Ride Service consomme l'evenement** -> Met a jour le tracker de visiteurs
4. **Visiteur rejoint une file** -> RideActor gere la queue
5. **Cycle demarre** -> Passagers charges, evenement publie

---

## Prerequis

### Logiciels Requis

| Outil | Version Minimum | Verification |
|-------|-----------------|--------------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker Desktop | 24+ | `docker --version` |
| Git | 2.x | `git --version` |

### Installation des Prerequis

#### macOS

```bash
# Installer Homebrew si necessaire
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Installer Java 21
brew install openjdk@21

# Ajouter Java au PATH (ajouter dans ~/.zshrc ou ~/.bash_profile)
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"

# Installer Maven
brew install maven

# Installer Docker Desktop
brew install --cask docker
# Puis lancer Docker Desktop depuis Applications

# Verifier les installations
java -version
mvn -version
docker --version
```

#### Windows 10/11

**Option 1 : Installation manuelle**

1. **Java 21** :
   - Telecharger depuis https://adoptium.net/temurin/releases/?version=21
   - Executer l'installateur
   - Ajouter aux variables d'environnement :
     - `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot`
     - Ajouter `%JAVA_HOME%\bin` au `PATH`

2. **Maven** :
   - Telecharger depuis https://maven.apache.org/download.cgi
   - Extraire dans `C:\Program Files\Apache\maven`
   - Ajouter `C:\Program Files\Apache\maven\bin` au `PATH`

3. **Docker Desktop** :
   - Telecharger depuis https://www.docker.com/products/docker-desktop/
   - Activer WSL2 si demande
   - Redemarrer apres installation

**Option 2 : Via Chocolatey (PowerShell en administrateur)**

```powershell
# Installer Chocolatey
Set-ExecutionPolicy Bypass -Scope Process -Force
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Installer les outils
choco install temurin21 -y
choco install maven -y
choco install docker-desktop -y

# Redemarrer PowerShell puis verifier
java -version
mvn -version
docker --version
```

**Option 3 : Via Winget (Windows 11 / Windows 10 recent)**

```powershell
# Installer les outils
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
winget install Docker.DockerDesktop

# Redemarrer PowerShell puis verifier
java -version
mvn -version
docker --version
```

---

## Installation et Lancement

### Cloner le Projet

```bash
git clone <repository-url>
cd CY_Land
```

### macOS

#### Terminal 0 : Demarrer RabbitMQ

**IMPORTANT : Lancer Docker Desktop avant d'executer les commandes Docker.**

Ouvrez Docker Desktop depuis le dossier Applications et attendez que le daemon soit pret (icone stable dans la barre de menu). Sans cela, vous obtiendrez une erreur "Cannot connect to the Docker daemon".

```bash
# Demarrer RabbitMQ avec Docker
docker run -d --name park-rabbitmq  -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest rabbitmq:3-management

# Verifier que RabbitMQ est pret (attendre ~30 secondes)
docker logs park-rabbitmq 2>&1 | grep "started"
```

#### Terminal 1 : Compiler et Demarrer Eureka

```bash
# Compiler tout le projet
mvn clean install -DskipTests

# Demarrer Eureka Server
cd eureka-server
mvn spring-boot:run
```

Attendre le message : `EUREKA SERVER STARTED`

#### Terminal 2 : Demarrer Gate Service

```bash
cd gate-service
mvn spring-boot:run
```

Attendre le message : `GATE SERVICE READY`

#### Terminal 3 : Demarrer Ride Service

```bash
cd ride-service
mvn spring-boot:run
```

Attendre le message : `RIDE SERVICE READY`

### Windows 10/11

#### PowerShell/CMD 0 : Demarrer RabbitMQ

**IMPORTANT : Lancer Docker Desktop avant d'executer les commandes Docker.**

Ouvrez Docker Desktop depuis le menu Demarrer et attendez que l'application soit completement chargee (icone stable dans la barre des taches). Sans cela, vous obtiendrez une erreur "error during connect: ... Is the docker daemon running?".

```powershell
# Demarrer RabbitMQ
docker run -d --name park-rabbitmq -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest rabbitmq:3-management

# Verifier que RabbitMQ est pret (attendre ~30 secondes)
docker logs park-rabbitmq
```

#### PowerShell/CMD 1 : Compiler et Demarrer Eureka

```powershell
# Compiler tout le projet
mvn clean install -DskipTests

# Demarrer Eureka Server
cd eureka-server
mvn spring-boot:run
```

#### PowerShell/CMD 2 : Demarrer Gate Service

```powershell
cd gate-service
mvn spring-boot:run
```

#### PowerShell/CMD 3 : Demarrer Ride Service

```powershell
cd ride-service
mvn spring-boot:run
```

### Alternative : Docker Compose (macOS et Windows)

```bash
# Tout demarrer en une commande
docker compose up --build

# Arreter
docker compose down
```

### URLs des Services

| Service | URL | Description |
|---------|-----|-------------|
| Eureka Dashboard | http://localhost:8761 | Visualiser les services enregistres |
| Gate Service | http://localhost:8081 | API de controle d'acces |
| Ride Service | http://localhost:8082 | API de gestion des attractions |
| RabbitMQ Dashboard | http://localhost:15672 | Interface RabbitMQ (guest/guest) |

---

## Guide de Test Complet

### Scenario de Test Complet

Suivez ce scenario pour tester toutes les fonctionnalites :

#### 1. Verifier les Services

**macOS/Linux :**
```bash
# Verifier Eureka
curl http://localhost:8761/actuator/health

# Verifier Gate Service
curl http://localhost:8081/actuator/health

# Verifier Ride Service
curl http://localhost:8082/actuator/health
```

**Windows PowerShell :**
```powershell
# Verifier Eureka
Invoke-RestMethod http://localhost:8761/actuator/health

# Verifier Gate Service
Invoke-RestMethod http://localhost:8081/actuator/health

# Verifier Ride Service
Invoke-RestMethod http://localhost:8082/actuator/health
```

#### 2. Tester le Gate Service

**macOS/Linux :**
```bash
# Lister les portes
curl http://localhost:8081/gate

# Scan asynchrone (tell pattern - fire-and-forget)
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"

# Scan synchrone (ask pattern - attend la reponse)
curl -X POST "http://localhost:8081/gate/G1/scan-sync?ticketId=T002"

# Tester le rejet d'un ticket deja utilise
curl -X POST "http://localhost:8081/gate/G1/scan-sync?ticketId=T001"

# Bloquer une porte
curl -X POST "http://localhost:8081/gate/G2/block"

# Debloquer une porte
curl -X POST "http://localhost:8081/gate/G2/unblock"

# Creer une nouvelle porte
curl -X POST "http://localhost:8081/gate?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"

# Supprimer une porte
curl -X DELETE "http://localhost:8081/gate/G3"

# Metriques du pool de scanners
curl http://localhost:8081/gate/pool/metrics
```

**Windows PowerShell :**
```powershell
# Lister les portes
Invoke-RestMethod http://localhost:8081/gate

# Scan asynchrone
Invoke-RestMethod -Method Post "http://localhost:8081/gate/G1/scan?ticketId=T001"

# Scan synchrone
Invoke-RestMethod -Method Post "http://localhost:8081/gate/G1/scan-sync?ticketId=T002"

# Bloquer une porte
Invoke-RestMethod -Method Post "http://localhost:8081/gate/G2/block"

# Debloquer une porte
Invoke-RestMethod -Method Post "http://localhost:8081/gate/G2/unblock"

# Creer une nouvelle porte
Invoke-RestMethod -Method Post "http://localhost:8081/gate?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"

# Supprimer une porte
Invoke-RestMethod -Method Delete "http://localhost:8081/gate/G3"

# Metriques du pool
Invoke-RestMethod http://localhost:8081/gate/pool/metrics
```

#### 3. Tester le Ride Service

**macOS/Linux :**
```bash
# Lister les attractions
curl http://localhost:8082/rides

# Etat d'une attraction
curl http://localhost:8082/rides/rc/state

# Rejoindre la queue (simuler plusieurs visiteurs)
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V001"
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V002"
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V003"

# Verifier l'etat de la queue
curl http://localhost:8082/rides/rc/state

# Demarrer un cycle manuellement
curl -X POST "http://localhost:8082/rides/rc/start-cycle"

# Signaler une panne
curl -X POST "http://localhost:8082/rides/vr/report-fault?faultType=MECHANICAL&description=Panne%20moteur"

# Verifier que l'attraction est fermee
curl http://localhost:8082/rides/vr/state

# Reparer l'attraction
curl -X POST "http://localhost:8082/rides/vr/repair"

# Verifier que l'attraction est rouverte
curl http://localhost:8082/rides/vr/state
```

**Windows PowerShell :**
```powershell
# Lister les attractions
Invoke-RestMethod http://localhost:8082/rides

# Etat d'une attraction
Invoke-RestMethod http://localhost:8082/rides/rc/state

# Rejoindre la queue
Invoke-RestMethod -Method Post "http://localhost:8082/rides/rc/join?ticketId=V001"
Invoke-RestMethod -Method Post "http://localhost:8082/rides/rc/join?ticketId=V002"

# Demarrer un cycle
Invoke-RestMethod -Method Post "http://localhost:8082/rides/rc/start-cycle"

# Signaler une panne
Invoke-RestMethod -Method Post "http://localhost:8082/rides/vr/report-fault?faultType=MECHANICAL&description=Panne"

# Reparer
Invoke-RestMethod -Method Post "http://localhost:8082/rides/vr/repair"
```

#### 4. Tester l'API de Gestion des Acteurs

**macOS/Linux :**
```bash
# Lister tous les acteurs du Gate Service
curl http://localhost:8081/actors

# Details d'un acteur specifique
curl http://localhost:8081/actors/G1

# Metriques du systeme d'acteurs
curl http://localhost:8081/actors/system/metrics

# Bloquer un acteur via l'API generique
curl -X POST http://localhost:8081/actors/G1/block

# Debloquer un acteur
curl -X POST http://localhost:8081/actors/G1/unblock

# Lister les acteurs du Ride Service
curl http://localhost:8082/actors
```

**Windows PowerShell :**
```powershell
# Lister tous les acteurs
Invoke-RestMethod http://localhost:8081/actors

# Details d'un acteur
Invoke-RestMethod http://localhost:8081/actors/G1

# Metriques systeme
Invoke-RestMethod http://localhost:8081/actors/system/metrics
```

#### 5. Verifier les Logs

Les logs des acteurs sont ecrits dans des fichiers JSON :

**macOS/Linux :**
```bash
# Logs du Gate Service
cat logs/gate-service/actors/actors-$(date +%Y-%m-%d).jsonl | head -20

# Logs du Ride Service
cat logs/ride-service/actors/actors-$(date +%Y-%m-%d).jsonl | head -20
```

**Windows PowerShell :**
```powershell
# Logs du Gate Service
Get-Content logs\gate-service\actors\actors-*.jsonl | Select-Object -First 20

# Logs du Ride Service
Get-Content logs\ride-service\actors\actors-*.jsonl | Select-Object -First 20
```

#### 6. Verifier RabbitMQ

1. Ouvrir http://localhost:15672
2. Se connecter avec guest/guest
3. Aller dans "Queues" pour voir les messages en transit
4. Aller dans "Exchanges" pour voir `park.events`

---

## Tests Automatises

### Executer Tous les Tests

```bash
# Depuis la racine du projet
mvn test
```

### Executer les Tests par Module

```bash
# Tests du framework d'acteurs (8 tests)
cd actor-framework
mvn test

# Tests d'integration du Gate Service (8 tests)
cd gate-service
mvn test
```

### Resultats Attendus

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0  (actor-framework)
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0  (gate-service)
[INFO] BUILD SUCCESS
```

### Description des Tests

#### Tests du Framework (ActorFrameworkTest.java)

| Test | Description |
|------|-------------|
| `shouldCreateActor` | Verifie la creation d'un acteur |
| `shouldReturnSameActorIfExists` | Verifie l'unicite des acteurs |
| `shouldReceiveMessages` | Teste le pattern tell (asynchrone) |
| `shouldReceiveResponseWithAsk` | Teste le pattern ask (synchrone) |
| `shouldProcessMessagesInOrder` | Verifie l'ordre FIFO des messages |
| `shouldBlockAndUnblockActor` | Teste le blocage/deblocage |
| `shouldRestartActorAfterError` | Teste la supervision et redemarrage |
| `shouldStopActor` | Verifie l'arret propre d'un acteur |

#### Tests d'Integration (GateIntegrationTest.java)

| Test | Description |
|------|-------------|
| `scanShouldReturn202` | Verifie le scan asynchrone |
| `scanSyncShouldReturnResult` | Verifie le scan synchrone |
| `scanShouldPublishEvent` | Verifie la publication RabbitMQ |
| `shouldListGates` | Verifie le listing des portes |
| `shouldBlockGate` | Teste le blocage d'une porte |
| `shouldCreateAndDeleteGate` | Teste creation/suppression |
| `shouldListActors` | Verifie l'API de gestion |
| `shouldReturnSystemMetrics` | Verifie les metriques |

---

## API Reference

### Gate Service (port 8081)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/gate` | Liste toutes les portes |
| POST | `/gate` | Cree une nouvelle porte |
| DELETE | `/gate/{gateId}` | Supprime une porte |
| POST | `/gate/{gateId}/scan` | Scan asynchrone (tell) |
| POST | `/gate/{gateId}/scan-sync` | Scan synchrone (ask) |
| GET | `/gate/{gateId}/status` | Statut d'une porte |
| POST | `/gate/{gateId}/block` | Bloque une porte |
| POST | `/gate/{gateId}/unblock` | Debloque une porte |
| GET | `/gate/pool/metrics` | Metriques du pool de scanners |

### Ride Service (port 8082)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/rides` | Liste les attractions |
| GET | `/rides/{rideId}/state` | Etat d'une attraction |
| POST | `/rides/{rideId}/join` | Rejoindre la file d'attente |
| POST | `/rides/{rideId}/start-cycle` | Demarrer un cycle |
| POST | `/rides/{rideId}/report-fault` | Signaler une panne |
| POST | `/rides/{rideId}/repair` | Reparer une attraction |
| POST | `/rides/{rideId}/block` | Bloquer une attraction |
| POST | `/rides/{rideId}/unblock` | Debloquer une attraction |

### API de Gestion des Acteurs (tous les services)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/actors` | Liste tous les acteurs |
| GET | `/actors/{id}` | Details d'un acteur |
| POST | `/actors/{id}/block` | Bloquer un acteur |
| POST | `/actors/{id}/unblock` | Debloquer un acteur |
| DELETE | `/actors/{id}` | Supprimer un acteur |
| POST | `/actors/{id}/tell` | Envoyer un message (inter-service) |
| POST | `/actors/{id}/ask` | Requete synchrone (inter-service) |
| GET | `/actors/system/metrics` | Metriques du systeme |

---

## Concepts Spring Boot Utilises

### Annotations et Patterns

| Annotation/Pattern | Utilisation |
|-------------------|-------------|
| `@SpringBootApplication` | Point d'entree des microservices |
| `@EnableDiscoveryClient` | Enregistrement Eureka |
| `@EnableEurekaServer` | Serveur de decouverte |
| `@Component`, `@Service` | Injection de dependances |
| `@RestController`, `@RequestMapping` | API REST |
| `@PostConstruct`, `@PreDestroy` | Hooks de cycle de vie |
| `@Value` | Injection de configuration |
| `@Validated` | Validation des parametres |

### Spring Cloud

| Composant | Utilisation |
|-----------|-------------|
| Spring Cloud Netflix Eureka | Decouverte et enregistrement des services |
| Spring Cloud Stream | Messaging avec RabbitMQ |
| Spring Cloud LoadBalancer | Equilibrage de charge |
| WebClient | Communication HTTP reactive |

### Fonctionnalites Java 21+

| Fonctionnalite | Utilisation |
|----------------|-------------|
| Virtual Threads | `Executors.newThreadPerTaskExecutor()` pour les acteurs |
| Records | Messages immutables (`ScanTicket`, `GateStatus`, etc.) |
| Sealed Classes | Hierarchie de messages controlee |
| Pattern Matching | `switch` sur les types de messages dans `onReceive()` |
| CompletableFuture | Pattern ask asynchrone |

### Autres Patterns

| Pattern | Implementation |
|---------|----------------|
| Actor Model | Framework complet inspire d'Akka |
| Fire-and-Forget | Methode `tell()` |
| Request-Response | Methode `ask()` avec timeout |
| Supervision | Strategies OneForOne et AllForOne |
| Event Sourcing | Publication d'evenements via RabbitMQ |
| CQRS | Separation lecture/ecriture via evenements |

---

## Structure du Projet

```
cy-land/
|-- pom.xml                          # Parent POM (Maven multi-module)
|-- docker-compose.yml               # Orchestration Docker
|-- README.md                        # Ce fichier
|
|-- actor-framework/                 # MODULE 1 : Framework reutilisable
|   |-- pom.xml
|   +-- src/main/java/com/music/actor/
|       |-- core/                    # Interfaces de base
|       |   |-- Actor.java           # Interface principale des acteurs
|       |   |-- ActorRef.java        # Reference vers un acteur
|       |   |-- ActorContext.java    # Contexte d'execution
|       |   |-- ActorSystem.java     # Gestionnaire du systeme
|       |   |-- ActorFactory.java    # Factory pour creer des acteurs
|       |   |-- ActorState.java      # Etats possibles d'un acteur
|       |   +-- Message.java         # Interface des messages
|       |
|       |-- runtime/                 # Implementations
|       |   |-- ActorSystemImpl.java       # Implementation du systeme
|       |   |-- LocalActorRef.java         # Reference locale
|       |   |-- RemoteActorRef.java        # Reference distante
|       |   +-- ActorManagementController.java  # API REST de gestion
|       |
|       |-- supervision/             # Strategies de supervision
|       |   |-- SupervisorStrategy.java    # Interface
|       |   |-- SupervisorDirective.java   # RESUME, RESTART, STOP, ESCALATE
|       |   |-- OneForOneStrategy.java     # Un seul enfant affecte
|       |   +-- AllForOneStrategy.java     # Tous les enfants affectes
|       |
|       |-- scalability/             # Auto-scaling
|       |   |-- ActorPool.java             # Interface du pool
|       |   |-- ScalingConfig.java         # Configuration
|       |   +-- AutoScalingActorPool.java  # Implementation
|       |
|       +-- logging/                 # Logs structures
|           |-- ActorLogger.java           # Interface
|           +-- DefaultActorLogger.java    # Implementation JSON
|
|-- eureka-server/                   # MODULE 2 : Service Discovery
|   |-- pom.xml
|   +-- src/main/java/.../EurekaServerApplication.java
|
|-- gate-service/                    # MODULE 3 : Microservice Portes
|   |-- pom.xml
|   +-- src/
|       |-- main/java/com/music/gate/
|       |   |-- GateServiceApplication.java
|       |   |-- api/
|       |   |   |-- GateController.java    # Endpoints REST
|       |   |   +-- GateService.java       # Logique metier
|       |   |-- domain/
|       |   |   |-- GateActor.java         # Acteur porte
|       |   |   |-- GateMessages.java      # Messages
|       |   |   +-- GateType.java          # Types de portes
|       |   +-- messaging/
|       |       +-- GateEventPublisher.java  # Publication RabbitMQ
|       +-- test/java/com/music/gate/
|           +-- GateIntegrationTest.java   # Tests d'integration
|
+-- ride-service/                    # MODULE 4 : Microservice Attractions
    |-- pom.xml
    +-- src/main/java/com/music/ride/
        |-- RideServiceApplication.java
        |-- api/
        |   |-- RideController.java
        |   +-- RideService.java
        |-- domain/
        |   |-- RideActor.java
        |   +-- RideMessages.java
        +-- messaging/
            |-- RideEventPublisher.java
            +-- VisitorEventConsumer.java  # Consommation RabbitMQ
```

---

## Arret des Services

### macOS/Linux

```bash
# Arreter les services Spring Boot
# Dans chaque terminal, appuyer sur Ctrl+C

# Arreter RabbitMQ
docker stop park-rabbitmq
docker rm park-rabbitmq
```

### Windows

```powershell
# Arreter les services Spring Boot
# Dans chaque terminal, appuyer sur Ctrl+C

# Arreter RabbitMQ
docker stop park-rabbitmq
docker rm park-rabbitmq
```

---

## Depannage

### Docker Desktop non lance

Si vous obtenez l'erreur "Cannot connect to the Docker daemon" ou "error during connect: ... Is the docker daemon running?":

1. Ouvrez Docker Desktop depuis Applications (macOS) ou le menu Demarrer (Windows)
2. Attendez que l'icone Docker soit stable (sans animation)
3. Verifiez avec `docker info` que le daemon repond
4. Relancez la commande `docker run`

### RabbitMQ ne demarre pas

```bash
# Verifier si le port est deja utilise
# macOS/Linux
lsof -i :5672

# Windows
netstat -ano | findstr :5672

# Supprimer le conteneur existant
docker rm -f park-rabbitmq
```

### Eureka ne trouve pas les services

1. Verifier que Eureka est demarre en premier
2. Attendre 30 secondes apres le demarrage de chaque service
3. Verifier le dashboard : http://localhost:8761

### Erreur de compilation

```bash
# Nettoyer et recompiler
mvn clean install -DskipTests

# Si probleme de version Java
java -version  # Doit etre 21+
```

### Port deja utilise

```bash
# macOS/Linux : Trouver le processus
lsof -i :8081

# Windows : Trouver le processus
netstat -ano | findstr :8081

# Tuer le processus (remplacer PID par le numero)
# macOS/Linux
kill -9 <PID>

# Windows
taskkill /PID <PID> /F
```

---

## References

### Documentation Officielle

- [Akka Documentation](https://doc.akka.io/) - Inspiration pour le modele d'acteurs
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Cloud Netflix Eureka](https://spring.io/projects/spring-cloud-netflix)
- [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)

### Java 21+

- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
- [Pattern Matching for Switch (JEP 441)](https://openjdk.org/jeps/441)
- [Record Patterns (JEP 440)](https://openjdk.org/jeps/440)

### Articles et Tutoriels

- [Actor Model Explained](https://www.brianstorti.com/the-actor-model/)
- [Building Microservices with Spring Boot](https://spring.io/guides/gs/microservices)
- [Reactive Programming with WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

---

## Auteurs

Paul PITIOT
