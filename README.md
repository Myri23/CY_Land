# CY Land - Framework d'Acteurs Distribues

Framework d'acteurs distribues inspire d'Akka, developpe avec Spring Boot.

Projet realise dans le cadre du module JEE, ING2 Groupe 1 Equipe 6

---

## Presentation du Projet

Ce projet implemente un framework d'acteurs distribues inspire d'Akka. L'application de demonstration est CY Land, un systeme de gestion de parc d'attractions avec :

- Gate Service : Gestion des entrees, validation des tickets et reception des notifications
- Ride Service : Gestion des attractions, files d'attente et signalement des pannes

---

## Conformite au Cahier des Charges

Le tableau suivant montre comment notre projet repond aux exigences demandees :

| Exigence du sujet | Notre implementation | Fichiers concernes |
|-------------------|---------------------|-------------------|
| Gestion des acteurs (creation, destruction, blocage, deblocage) | Methodes `actorOf()`, `stop()`, `block()`, `unblock()` dans ActorSystem | `ActorSystem.java`, `ActorSystemImpl.java` |
| Communication asynchrone entre acteurs | Pattern tell (fire-and-forget) via `ActorRef.tell()` | `ActorRef.java`, `LocalActorRef.java` |
| Communication synchrone entre acteurs | Pattern ask avec `CompletableFuture` via `ActorRef.ask()` | `ActorRef.java`, `LocalActorRef.java` |
| Communication intra-microservice | Acteurs locaux avec mailbox | `LocalActorRef.java` |
| Communication inter-microservices | RemoteActorRef + Eureka + WebClient | `RemoteActorRef.java` |
| Supervision et tolerance aux pannes | Strategies OneForOne et AllForOne avec directives RESUME, RESTART, STOP, ESCALATE | `SupervisorStrategy.java`, `OneForOneStrategy.java` |
| Scalabilite des acteurs | AutoScalingActorPool avec scale-up/down automatique | `AutoScalingActorPool.java` |
| Systeme de logs | Logs JSON structures par jour | `DefaultActorLogger.java` |
| Application differente du restaurant | Parc d'attractions (portes + attractions) | `gate-service/`, `ride-service/` |
| Tests unitaires et integration | 42 tests avec JUnit 5, MockMvc, Awaitility | `src/test/java/` dans chaque module |

### Fonctionnalites supplementaires

| Fonctionnalite | Description |
|----------------|-------------|
| Virtual Threads | Utilisation des threads virtuels Java 21 |
| Resilience4j | Circuit Breaker et Retry pour la tolerance aux pannes |
| RabbitMQ | Communication par evenements entre services |
| Docker | Conteneurisation des services |
| Notification Handler | Acteur dedie pour la reception des notifications inter-services |

---

## Architecture

```
                    +------------------+
                    | Eureka Server    |
                    | (port 8761)      |
                    +--------+---------+
                             |
              +--------------+--------------+
              |                             |
    +---------+----------+       +----------+---------+
    | Gate Service       |       | Ride Service       |
    | (port 8081)        |       | (port 8082)        |
    |                    |       |                    |
    | Acteurs:           |       | Acteurs:           |
    | - G1, G2, VIP      |       | - rc, gr, vr       |
    | - Scanner Pool     |       | - visitor-tracker  |
    | - notification-    |       |                    |
    |   handler          |       |                    |
    +---------+----------+       +----------+---------+
              |                             |
              +-------------+---------------+
                            |
                   +--------+--------+
                   | RabbitMQ        |
                   | (port 5672)     |
                   +-----------------+
```

---

## Prerequis

| Outil | Version | Verification |
|-------|---------|--------------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | 24+ | `docker --version` |

---

## Installation et Lancement

### 1. Cloner le projet

```bash
git clone <url-du-repo>
cd CY_Land
```

### 2. Demarrer RabbitMQ

```bash
docker run -d --name park-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

Attendre 30 secondes que RabbitMQ demarre.

### 3. Compiler le projet

Terminal 0 - Compilation Projet
```bash
mvn clean install -DskipTests
```

### 4. Demarrer les services (3 terminaux)

Terminal 1 - Eureka :
```bash
cd eureka-server
mvn spring-boot:run
```

Terminal 2 - Gate Service :
```bash
cd gate-service
mvn spring-boot:run
```

Terminal 3 - Ride Service :
```bash
cd ride-service
mvn spring-boot:run
```

### URLs des services

| Service | URL |
|---------|-----|
| Eureka Dashboard | http://localhost:8761 |
| Gate Service | http://localhost:8081 |
| Ride Service | http://localhost:8082 |
| RabbitMQ | http://localhost:15672 (guest/guest) |

---

## Guide de Test

### Verifier que les services fonctionnent

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

### Tester le Gate Service

```bash
# Liste des portes
curl http://localhost:8081/gate

# Scan asynchrone (pattern tell) / Mac
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
ou Windows
curl.exe -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"

# Scan synchrone (pattern ask) / Mac
curl -X POST "http://localhost:8081/gate/G1/scan-sync?ticketId=T002"
ou Windows
curl.exe -X POST "http://localhost:8081/gate/G1/scan-sync?ticketId=T002"

# Bloquer une porte / Mac
curl -X POST "http://localhost:8081/gate/G2/block"
ou Windows

# Debloquer une porte / Mac
curl -X POST "http://localhost:8081/gate/G2/unblock"
ou Windows

```

### Tester le Ride Service

```bash
# Liste des attractions
curl http://localhost:8082/rides

# Etat d'une attraction
curl http://localhost:8082/rides/rc/state

# Rejoindre la file d'attente / Mac
curl -X POST "http://localhost:8082/rides/rc/join?ticketId=V001"
# ou Windows

# Demarrer un cycle / Mac
curl -X POST "http://localhost:8082/rides/rc/start-cycle"
# ou Windows
curl.exe -X POST "http://localhost:8082/rides/rc/start-cycle"

# Signaler une panne / Mac
curl -X POST "http://localhost:8082/rides/vr/report-fault?faultType=MECHANICAL&description=Test"
# ou Windows
curl.exe -X POST "http://localhost:8082/rides/vr/report-fault?faultType=MECHANICAL&description=Test" 

# Reparer / Mac
curl -X POST "http://localhost:8082/rides/vr/repair"
# ou Windows
curl.exe -X POST "http://localhost:8082/rides/vr/repair"

```

### Tester la communication inter-services

```bash
# Signaler une panne (le gate-service recoit la notification) / Mac
curl -X POST "http://localhost:8082/rides/rc/report-fault?faultType=SAFETY&description=Test"
# ou Windows
curl.exe -X POST "http://localhost:8082/rides/rc/report-fault?faultType=SAFETY&description=Test"

# Verifier les acteurs du gate-service (notification-handler present)
curl http://localhost:8081/actors

# Reparer l'attraction / Mac
curl -X POST "http://localhost:8082/rides/rc/repair"
# ou Windows
curl.exe -X POST "http://localhost:8082/rides/rc/report-fault?faultType=SAFETY&description=Test"

```

### Tester la gestion des acteurs

```bash
# Liste des acteurs
curl http://localhost:8081/actors

# Details d'un acteur
curl http://localhost:8081/actors/G1

# Details du notification-handler
curl http://localhost:8081/actors/notification-handler

# Metriques systeme
curl http://localhost:8081/actors/system/metrics
```

---

## Tests Automatises

### Lancer tous les tests

```bash
mvn test
```

### Tests par module

```bash
# Framework (8 tests)
cd actor-framework && mvn test

# Gate Service (18 tests)
cd gate-service && mvn test

# Ride Service (16 tests)
cd ride-service && mvn test
```

### Resultat attendu

```
Tests run: 42, Failures: 0, Errors: 0
BUILD SUCCESS
```

### Description des tests

Tests du framework (8 tests) :
- Creation d'acteur
- Pattern tell (asynchrone)
- Pattern ask (synchrone)
- Ordre FIFO des messages
- Blocage/deblocage
- Supervision et redemarrage
- Arret d'acteur

Tests Gate Service (18 tests) :
- Scan de tickets
- Publication d'evenements RabbitMQ
- Gestion des portes
- Tests de resilience
- Test de charge (50 scans)

Tests Ride Service (16 tests) :
- Liste des attractions
- File d'attente
- Cycles d'attraction
- Pannes et reparations
- Blocage/deblocage

---

## Collection Postman

Une collection Postman est disponible dans `postman/CY_Land_API_Collection.postman_collection.json`.

Pour l'utiliser :
1. Ouvrir Postman
2. File > Import
3. Selectionner le fichier JSON

La collection contient plus de 30 requetes organisees par service.

---

## API Reference

### Gate Service (port 8081)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | /gate | Liste les portes |
| POST | /gate/{id}/scan | Scan asynchrone |
| POST | /gate/{id}/scan-sync | Scan synchrone |
| POST | /gate/{id}/block | Bloquer |
| POST | /gate/{id}/unblock | Debloquer |
| GET | /gate/pool/metrics | Metriques du pool |

### Ride Service (port 8082)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | /rides | Liste les attractions |
| GET | /rides/{id}/state | Etat d'une attraction |
| POST | /rides/{id}/join | Rejoindre la file |
| POST | /rides/{id}/start-cycle | Demarrer un cycle |
| POST | /rides/{id}/report-fault | Signaler panne |
| POST | /rides/{id}/repair | Reparer |

### Gestion des acteurs (tous les services)

| Methode | Endpoint | Description |
|---------|----------|-------------|
| GET | /actors | Liste des acteurs |
| GET | /actors/{id} | Details d'un acteur |
| POST | /actors/{id}/block | Bloquer |
| POST | /actors/{id}/unblock | Debloquer |
| GET | /actors/system/metrics | Metriques |

---

## Concepts Spring Boot Utilises

| Concept | Utilisation |
|---------|-------------|
| @SpringBootApplication | Point d'entree des services |
| @RestController | API REST |
| @Service, @Component | Injection de dependances |
| @EnableDiscoveryClient | Enregistrement Eureka |
| Spring Cloud Stream | Messaging RabbitMQ |
| WebClient | Communication HTTP reactive |
| CompletableFuture | Programmation asynchrone |

---

## Structure du Projet

```
cy-land/
|-- pom.xml                     # POM parent
|-- docker-compose.yml
|-- README.md
|-- docs/
|   |-- ARCHITECTURE.md
|   +-- REFERENCES.md
|-- postman/
|   +-- CY_Land_API_Collection.postman_collection.json
|-- actor-framework/            # Framework reutilisable
|   +-- src/main/java/com/music/actor/
|       |-- core/               # Interfaces (Actor, ActorRef, Message...)
|       |-- runtime/            # Implementations
|       |-- supervision/        # Strategies de supervision
|       |-- scalability/        # Auto-scaling
|       |-- resilience/         # Configuration Resilience4j
|       +-- logging/            # Logs
|-- eureka-server/              # Service Discovery
|-- gate-service/               # Microservice Portes
|   +-- src/main/java/com/music/gate/
|       |-- api/                # Controllers et Services
|       |-- domain/             # Acteurs (GateActor, NotificationHandlerActor)
|       +-- messaging/          # Publishers RabbitMQ
+-- ride-service/               # Microservice Attractions
    +-- src/main/java/com/music/ride/
        |-- api/                # Controllers et Services
        |-- domain/             # Acteurs (RideActor)
        +-- messaging/          # Publishers et Consumers RabbitMQ
```

---

## Script de Nettoyage Complet

Terminal 0 - MacOS/Linux :

```bash
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
```

Terminal 0 - Windows PowerShell :

```powershell
# Nettoyage Maven + logs + fichiers generes
mvn clean
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue logs
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue */target
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue */logs
Get-ChildItem -Recurse -Filter "*.log" | Remove-Item -Force
```

---

## Arret des Services

```bash
# Ctrl+C dans chaque terminal

# Arreter RabbitMQ
docker stop park-rabbitmq
docker rm park-rabbitmq
```

---

## Depannage

### Port deja utilise

```bash
# Trouver le processus
lsof -i :8081

# Tuer le processus
kill -9 <PID>
```

### RabbitMQ ne demarre pas

```bash
docker rm -f park-rabbitmq
docker run -d --name park-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

### Erreur de compilation

```bash
mvn clean install -DskipTests
java -version  # Verifier Java 21+
```

---

## Auteurs

Equipe 6 - ING2 Groupe 1

- Paul Pitiot
- Thomas Rykaczewski
- Besma Saidi
- Myriam Saadi
- Ines Ribar

---

## References

Voir `docs/REFERENCES.md` pour la bibliographie complete.
