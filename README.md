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
6. [Guide de Test Complet](#guide-de-test-complet)
7. [Collection Postman](#collection-postman)
8. [Tests Automatises](#tests-automatises)
9. [API Reference](#api-reference)
10. [Concepts Spring Boot Utilises](#concepts-spring-boot-utilises)
11. [Structure du Projet](#structure-du-projet)
12. [Nettoyage du Projet](#nettoyage-du-projet)
13. [References](#references)

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
| Gestion des acteurs (creation, destruction, blocage, deblocage) | ActorSystem avec methodes `actorOf()`, `stop()`, `block()`, `unblock()` | `actor-framework/src/main/java/com/music/actor/core/ActorSystem.java` |
| Communication asynchrone (tell) | Pattern fire-and-forget via `ActorRef.tell(message)` | `actor-framework/src/main/java/com/music/actor/core/ActorRef.java` |
| Communication synchrone (ask) | Pattern request-response via `ActorRef.ask(message, timeout)` retournant `CompletableFuture` | `actor-framework/src/main/java/com/music/actor/runtime/LocalActorRef.java` |
| Communication intra-microservice | Acteurs locaux communiquant via mailbox | `actor-framework/src/main/java/com/music/actor/runtime/LocalActorRef.java` |
| Communication inter-microservices | RemoteActorRef + Eureka discovery + WebClient | `actor-framework/src/main/java/com/music/actor/runtime/RemoteActorRef.java` |
| Supervision et tolerance aux pannes | OneForOneStrategy et AllForOneStrategy avec directives RESUME, RESTART, STOP, ESCALATE | `actor-framework/src/main/java/com/music/actor/supervision/` |
| Scalabilite | AutoScalingActorPool avec scale-up/down automatique | `actor-framework/src/main/java/com/music/actor/scalability/AutoScalingActorPool.java` |
| Systeme de logs | Logs structures JSON par jour avec trace complete | `actor-framework/src/main/java/com/music/actor/logging/DefaultActorLogger.java` |
| Application differente du restaurant | Parc d'attractions avec portes et attractions | `gate-service/` et `ride-service/` |
| Tests unitaires et integration | JUnit 5 + Spring Boot Test + MockMvc | `actor-framework/src/test/`, `gate-service/src/test/`, `ride-service/src/test/` |
| Collection Postman | Collection complete avec scenarios de test | `postman/CY_Land_API_Collection.postman_collection.json` |
| Diagrammes d'architecture | Diagrammes Mermaid detailles | `docs/ARCHITECTURE.md` |
| References bibliographiques | Format IEEE academique | `docs/REFERENCES.md` |

### Fonctionnalites Supplementaires

| Fonctionnalite | Description |
|----------------|-------------|
| Virtual Threads (Java 21+) | Utilisation des threads virtuels pour une meilleure scalabilite |
| Snapshot/Restore | Persistance de l'etat des acteurs pour recuperation apres crash |
| Scheduling | Planification de messages avec `scheduleOnce()` et `schedulePeriodic()` |
| Messaging RabbitMQ | Communication evenementielle entre services via Spring Cloud Stream |
| Dockerfiles | Conteneurisation complete de tous les services |

---

## Architecture

Pour les diagrammes detailles (Mermaid), consultez `docs/ARCHITECTURE.md`.

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

1. Visiteur scanne son ticket -> Gate Service recoit la requete
2. GateActor valide le ticket -> Publie evenement sur RabbitMQ
3. Ride Service consomme l'evenement -> Met a jour le tracker de visiteurs
4. Visiteur rejoint une file -> RideActor gere la queue
5. Cycle demarre -> Passagers charges, evenement publie

---

## Prerequis

### Logiciels Requis

| Outil | Version Minimum | Verification |
|-------|-----------------|--------------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker Desktop | 24+ | `docker --version` |
| Git | 2+ | `git --version` |

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

# Verifier les installations
java -version
mvn -version
docker --version
```

#### Windows 10/11

Option 1 : Installation manuelle

1. Java 21 : Telecharger depuis https://adoptium.net/temurin/releases/?version=21
2. Maven : Telecharger depuis https://maven.apache.org/download.cgi
3. Docker Desktop : Telecharger depuis https://www.docker.com/products/docker-desktop/

Option 2 : Via Chocolatey (PowerShell en administrateur)

```powershell
choco install temurin21 -y
choco install maven -y
choco install docker-desktop -y
```

Option 3 : Via Winget

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
winget install Docker.DockerDesktop
```

---

## Installation et Lancement

### Cloner le Projet

```bash
git clone <repository-url>
cd CY_Land
```

### Demarrage Manuel (4 terminaux)

#### Terminal 0 : Demarrer RabbitMQ

IMPORTANT : Lancer Docker Desktop avant d'executer les commandes Docker.

```bash
docker run -d --name park-rabbitmq -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management

# Attendre ~30 secondes que RabbitMQ soit pret
docker logs park-rabbitmq 2>&1 | grep "started"
```

#### Terminal 1 : Compiler et Demarrer Eureka

```bash
mvn clean install -DskipTests
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

### Alternative : Docker Compose

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

### Verifier les Services

macOS/Linux :

```bash
curl http://localhost:8761/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

Windows PowerShell :

```powershell
Invoke-RestMethod http://localhost:8761/actuator/health
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8082/actuator/health
```

### Tester le Gate Service

```bash
# Lister les portes
curl http://localhost:8081/gate

# Scan asynchrone (tell pattern)
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"

# Scan synchrone (ask pattern)
curl -X POST "http://localhost:8081/gate/G1/scan-sync?ticketId=T002"

# Bloquer/debloquer une porte
curl -X POST "http://localhost:8081/gate/G2/block"
curl -X POST "http://localhost:8081/gate/G2/unblock"

# Creer/supprimer une porte
curl -X POST "http://localhost:8081/gate?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"
curl -X DELETE "http://localhost:8081/gate/G3"

# Metriques du pool
curl http://localhost:8081/gate/pool/metrics
```

### Tester le Ride Service

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
curl -X POST "http://localhost:8082/rides/vr/report-fault?faultType=MECHANICAL&description=Panne%20moteur"

# Reparer
curl -X POST "http://localhost:8082/rides/vr/repair"
```

### Tester l'API de Gestion des Acteurs

```bash
# Lister tous les acteurs
curl http://localhost:8081/actors
curl http://localhost:8082/actors

# Details d'un acteur
curl http://localhost:8081/actors/G1

# Metriques systeme
curl http://localhost:8081/actors/system/metrics
curl http://localhost:8082/actors/system/metrics
```

### Verifier les Logs

macOS/Linux :

```bash
cat logs/gate-service/actors/actors-$(date +%Y-%m-%d).jsonl | head -20
cat logs/ride-service/actors/actors-$(date +%Y-%m-%d).jsonl | head -20
```

Windows PowerShell :

```powershell
Get-Content logs\gate-service\actors\actors-*.jsonl | Select-Object -First 20
Get-Content logs\ride-service\actors\actors-*.jsonl | Select-Object -First 20
```

---

## Collection Postman

Une collection Postman complete est disponible dans `postman/CY_Land_API_Collection.postman_collection.json`.

### Importer la Collection

1. Ouvrir Postman
2. File -> Import
3. Selectionner le fichier `postman/CY_Land_API_Collection.postman_collection.json`

### Contenu de la Collection

La collection contient plus de 30 requetes organisees en categories :

| Categorie | Description |
|-----------|-------------|
| Health Checks | Verification de sante des services |
| Gate Service | Tous les endpoints du Gate Service |
| Ride Service | Tous les endpoints du Ride Service |
| Actor Management | API de gestion des acteurs |
| Eureka Discovery | API de decouverte de services |
| Scenarios de Test | 3 scenarios end-to-end complets |

### Scenarios de Test Inclus

1. Scenario 1 : Entree visiteur complet (scan -> rejoindre attraction -> verifier queue)
2. Scenario 2 : Gestion de panne (etat initial -> signaler panne -> verifier fermeture -> reparer -> verifier reouverture)
3. Scenario 3 : Blocage/Deblocage acteur

### Variables d'Environnement

La collection utilise des variables pre-configurees :

- `gate_url` : http://localhost:8081
- `ride_url` : http://localhost:8082
- `eureka_url` : http://localhost:8761

---

## Tests Automatises

### Executer Tous les Tests

```bash
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

# Tests d'integration du Ride Service (15 tests)
cd ride-service
mvn test
```

### Resultats Attendus

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0  (actor-framework)
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0  (gate-service)
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0  (ride-service)
[INFO] BUILD SUCCESS
```

### Description des Tests

#### Tests du Framework (ActorFrameworkTest.java)

| Test | Description |
|------|-------------|
| shouldCreateActor | Verifie la creation d'un acteur |
| shouldReturnSameActorIfExists | Verifie l'unicite des acteurs |
| shouldReceiveMessages | Teste le pattern tell (asynchrone) |
| shouldReceiveResponseWithAsk | Teste le pattern ask (synchrone) |
| shouldProcessMessagesInOrder | Verifie l'ordre FIFO des messages |
| shouldBlockAndUnblockActor | Teste le blocage/deblocage |
| shouldRestartActorAfterError | Teste la supervision et redemarrage |
| shouldStopActor | Verifie l'arret propre d'un acteur |

#### Tests d'Integration Gate Service (GateIntegrationTest.java)

| Test | Description |
|------|-------------|
| scanShouldReturn202 | Verifie le scan asynchrone |
| scanSyncShouldReturnResult | Verifie le scan synchrone |
| scanShouldPublishEvent | Verifie la publication RabbitMQ |
| shouldListGates | Verifie le listing des portes |
| shouldBlockGate | Teste le blocage d'une porte |
| shouldCreateAndDeleteGate | Teste creation/suppression |
| shouldListActors | Verifie l'API de gestion |
| shouldReturnSystemMetrics | Verifie les metriques |

#### Tests d'Integration Ride Service (RideIntegrationTest.java)

| Test | Description |
|------|-------------|
| shouldListRides | Liste les attractions |
| shouldGetRideState | Etat d'une attraction |
| shouldReturn404ForUnknownRide | 404 pour attraction inexistante |
| shouldJoinQueue | Rejoindre la file d'attente |
| shouldReturn404WhenJoiningUnknownRide | 404 pour join inexistant |
| shouldStartCycleWithPassengers | Demarrer un cycle |
| shouldReportFault | Signaler une panne |
| shouldRepairRide | Reparer une attraction |
| shouldBlockRide | Bloquer une attraction |
| shouldUnblockRide | Debloquer une attraction |
| shouldListActors | Liste des acteurs |
| shouldGetActorDetails | Details d'un acteur |
| shouldReturnSystemMetrics | Metriques systeme |
| shouldHandleInvalidFaultType | Type de panne invalide |
| shouldAcceptAllFaultTypes | Tous les types de pannes |

---

## API Reference

### Gate Service (port 8081)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | /gate | Liste toutes les portes |
| POST | /gate | Cree une nouvelle porte |
| DELETE | /gate/{gateId} | Supprime une porte |
| POST | /gate/{gateId}/scan | Scan asynchrone (tell) |
| POST | /gate/{gateId}/scan-sync | Scan synchrone (ask) |
| GET | /gate/{gateId}/status | Statut d'une porte |
| POST | /gate/{gateId}/block | Bloque une porte |
| POST | /gate/{gateId}/unblock | Debloque une porte |
| GET | /gate/pool/metrics | Metriques du pool de scanners |

### Ride Service (port 8082)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | /rides | Liste les attractions |
| GET | /rides/{rideId}/state | Etat d'une attraction |
| POST | /rides/{rideId}/join | Rejoindre la file d'attente |
| POST | /rides/{rideId}/start-cycle | Demarrer un cycle |
| POST | /rides/{rideId}/report-fault | Signaler une panne |
| POST | /rides/{rideId}/repair | Reparer une attraction |
| POST | /rides/{rideId}/block | Bloquer une attraction |
| POST | /rides/{rideId}/unblock | Debloquer une attraction |

### API de Gestion des Acteurs (tous les services)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | /actors | Liste tous les acteurs |
| GET | /actors/{id} | Details d'un acteur |
| POST | /actors/{id}/block | Bloquer un acteur |
| POST | /actors/{id}/unblock | Debloquer un acteur |
| DELETE | /actors/{id} | Supprimer un acteur |
| POST | /actors/{id}/tell | Envoyer un message (inter-service) |
| POST | /actors/{id}/ask | Requete synchrone (inter-service) |
| GET | /actors/system/metrics | Metriques du systeme |

---

## Concepts Spring Boot Utilises

### Annotations et Patterns

| Annotation/Pattern | Utilisation |
|-------------------|-------------|
| @SpringBootApplication | Point d'entree des microservices |
| @EnableDiscoveryClient | Enregistrement Eureka |
| @EnableEurekaServer | Serveur de decouverte |
| @Component, @Service | Injection de dependances |
| @RestController, @RequestMapping | API REST |
| @PostConstruct, @PreDestroy | Hooks de cycle de vie |
| @Value | Injection de configuration |
| @Validated | Validation des parametres |

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
| Virtual Threads | Executors.newThreadPerTaskExecutor() pour les acteurs |
| Records | Messages immutables (ScanTicket, GateStatus, etc.) |
| Sealed Classes | Hierarchie de messages controlee |
| Pattern Matching | switch sur les types de messages dans onReceive() |
| CompletableFuture | Pattern ask asynchrone |

### Autres Patterns

| Pattern | Implementation |
|---------|----------------|
| Actor Model | Framework complet inspire d'Akka |
| Fire-and-Forget | Methode tell() |
| Request-Response | Methode ask() avec timeout |
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
|-- docs/
|   |-- ARCHITECTURE.md              # Diagrammes Mermaid
|   +-- REFERENCES.md                # References bibliographiques IEEE
|
|-- postman/
|   +-- CY_Land_API_Collection.postman_collection.json
|
|-- actor-framework/                 # MODULE 1 : Framework reutilisable
|   |-- pom.xml
|   |-- Dockerfile
|   +-- src/main/java/com/music/actor/
|       |-- core/                    # Interfaces de base
|       |-- runtime/                 # Implementations
|       |-- supervision/             # Strategies de supervision
|       |-- scalability/             # Auto-scaling
|       +-- logging/                 # Logs structures
|
|-- eureka-server/                   # MODULE 2 : Service Discovery
|   |-- pom.xml
|   |-- Dockerfile
|   +-- src/
|
|-- gate-service/                    # MODULE 3 : Microservice Portes
|   |-- pom.xml
|   |-- Dockerfile
|   +-- src/
|       |-- main/java/com/music/gate/
|       +-- test/java/com/music/gate/
|
+-- ride-service/                    # MODULE 4 : Microservice Attractions
    |-- pom.xml
    |-- Dockerfile
    +-- src/
        |-- main/java/com/music/ride/
        +-- test/java/com/music/ride/
```

---

## Nettoyage du Projet

Lors de la compilation et de l'execution des microservices, plusieurs fichiers et dossiers sont generes. Utilisez les commandes suivantes pour nettoyer le projet.

### Nettoyage Complet (Recommande)

macOS/Linux :

```bash
# Nettoyage Maven + logs + fichiers generes
mvn clean && \
rm -rf logs/ && \
rm -rf */target/ && \
rm -rf **/target/ && \
rm -rf */logs/ && \
rm -rf **/logs/ && \
rm -rf *.log && \
rm -rf **/*.log
```

Windows PowerShell :

```powershell
# Nettoyage Maven + logs + fichiers generes
mvn clean
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue logs
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue */target
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue */logs
Get-ChildItem -Recurse -Filter "*.log" | Remove-Item -Force
```

### Nettoyage Maven Uniquement

```bash
mvn clean
```

### Nettoyage Docker

```bash
# Arreter et supprimer les conteneurs
docker compose down

# Supprimer le conteneur RabbitMQ
docker stop park-rabbitmq && docker rm park-rabbitmq

# Supprimer les images construites (optionnel)
docker rmi cy-land-gate-service cy-land-ride-service cy-land-eureka-server

# Nettoyage complet Docker (attention : supprime toutes les images/conteneurs non utilises)
docker system prune -a
```

### Script de Nettoyage Complet

Creez un fichier `clean.sh` (macOS/Linux) :

```bash
#!/bin/bash
echo "Nettoyage du projet CY Land..."

# Maven clean
mvn clean -q

# Supprimer les logs
rm -rf logs/
rm -rf */logs/
rm -rf **/logs/

# Supprimer les fichiers target restants
rm -rf */target/
rm -rf **/target/

# Supprimer les fichiers de log individuels
find . -name "*.log" -type f -delete
find . -name "*.jsonl" -type f -delete

# Docker cleanup (optionnel)
if command -v docker &> /dev/null; then
    docker stop park-rabbitmq 2>/dev/null
    docker rm park-rabbitmq 2>/dev/null
fi

echo "Nettoyage termine!"
```

Rendez-le executable et lancez-le :

```bash
chmod +x clean.sh
./clean.sh
```

---

## Arret des Services

### macOS/Linux

```bash
# Arreter les services Spring Boot (Ctrl+C dans chaque terminal)

# Arreter RabbitMQ
docker stop park-rabbitmq
docker rm park-rabbitmq
```

### Windows

```powershell
# Arreter les services Spring Boot (Ctrl+C dans chaque terminal)

# Arreter RabbitMQ
docker stop park-rabbitmq
docker rm park-rabbitmq
```

---

## Depannage

### Docker Desktop non lance

Si vous obtenez l'erreur "Cannot connect to the Docker daemon" :

1. Ouvrez Docker Desktop
2. Attendez que l'icone Docker soit stable
3. Verifiez avec `docker info`
4. Relancez la commande

### RabbitMQ ne demarre pas

```bash
# Verifier si le port est utilise
lsof -i :5672  # macOS/Linux
netstat -ano | findstr :5672  # Windows

# Supprimer le conteneur existant
docker rm -f park-rabbitmq
```

### Eureka ne trouve pas les services

1. Verifier que Eureka est demarre en premier
2. Attendre 30 secondes apres le demarrage de chaque service
3. Verifier le dashboard : http://localhost:8761

### Erreur de compilation

```bash
mvn clean install -DskipTests
java -version  # Doit etre 21+
```

### Port deja utilise

```bash
# Trouver le processus
lsof -i :8081  # macOS/Linux
netstat -ano | findstr :8081  # Windows

# Tuer le processus
kill -9 <PID>  # macOS/Linux
taskkill /PID <PID> /F  # Windows
```

---

## References

Pour les references bibliographiques completes au format IEEE, consultez `docs/REFERENCES.md`.

### Documentation Officielle

- Akka Documentation : https://doc.akka.io/
- Spring Boot Reference : https://docs.spring.io/spring-boot/docs/current/reference/html/
- Spring Cloud Netflix Eureka : https://spring.io/projects/spring-cloud-netflix
- Spring Cloud Stream : https://spring.io/projects/spring-cloud-stream
- RabbitMQ Tutorials : https://www.rabbitmq.com/tutorials

### Java 21+

- Virtual Threads (JEP 444) : https://openjdk.org/jeps/444
- Pattern Matching for Switch (JEP 441) : https://openjdk.org/jeps/441
- Record Patterns (JEP 440) : https://openjdk.org/jeps/440

### Articles et Tutoriels

- Actor Model Explained : https://www.brianstorti.com/the-actor-model/
- Building Microservices with Spring Boot : https://spring.io/guides/gs/microservices

---

## Auteurs

Paul PITIOT - ING2 Groupe 1 Equipe 6
