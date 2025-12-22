# CY_Land - Microservices du Parc d'Attractions

Application Spring Boot combinant deux microservices pour la gestion d'un parc d'attractions : le controle d'acces (**Gate Service**) et la gestion des attractions (**Ride Service**), utilisant un **modele d'acteurs** et **RabbitMQ**.

---

## Sommaire

1. [Fonctionnalites](#fonctionnalites)
2. [Architecture](#architecture)
3. [Prerequis](#prerequis)
4. [Installation et Lancement](#installation-et-lancement)
5. [Guide de Test Complet](#guide-de-test-complet)
6. [Tests Automatises](#tests-automatises)
7. [Structure du Projet](#structure-du-projet)
8. [Troubleshooting](#troubleshooting)

---

## Fonctionnalites

### Gate Service - Controle d'acces

| Fonctionnalite | Description | Endpoint |
|----------------|-------------|----------|
| **Scan de ticket** | Scanner un ticket a une porte | `POST /gate/{gateId}/scan?ticketId=XXX` |
| **Anti-doublon** | Un ticket ne peut entrer qu'une seule fois | Gere par l'acteur |
| **Publication evenements** | Publie `VisitorEntered` sur RabbitMQ | Automatique |
| **Status porte** | Verifier qu'une porte est operationnelle | `GET /gate/{gateId}/status` |
| **CRUD Portes** | Gestion des portes | `GET/POST/PUT/DELETE /admin/gates` |
| **CRUD Tickets** | Gestion des tickets | `GET/POST/DELETE /admin/tickets` |

### Ride Service - Gestion des attractions

| Fonctionnalite | Description | Endpoint |
|----------------|-------------|----------|
| **Liste attractions** | Voir toutes les attractions | `GET /rides` |
| **Etat attraction** | Queue, passagers en cycle, etc. | `GET /rides/{rideId}/state` |
| **Rejoindre queue** | Ajouter un visiteur a la file | `POST /rides/{rideId}/join?ticketId=XXX` |
| **Demarrer cycle** | Lancer manuellement un cycle | `POST /rides/{rideId}/start-cycle` |
| **Signaler panne** | Reporter un probleme | `POST /rides/{rideId}/report-fault` |

### Attractions disponibles

| ID | Nom | Capacite | Duree cycle |
|----|-----|----------|-------------|
| `rc` | RollerCoaster (Montagnes Russes) | 20 passagers | 60 secondes |
| `gr` | GrandeRoue | 12 passagers | 120 secondes |
| `vr` | SimulateurVR | 8 passagers | 180 secondes |

### Types de portes

- `MAIN_GATE` - Entree principale
- `VIP_GATE` - Entree VIP
- `SERVICE_GATE` - Entree de service
- `EMERGENCY_EXIT` - Sortie de secours

### Types de tickets

- `CLASSIC_TICKET` - Acces portes principales
- `VIP_TICKET` - Acces toutes portes

---

## Architecture

```
+-------------------------------------------------------------------------+
|                         CY_LAND PARK SERVICE                             |
|                                                                          |
|  +---------------------------+    +---------------------------+          |
|  |      GATE SERVICE         |    |      RIDE SERVICE         |          |
|  |                           |    |                           |          |
|  |  /gate/scan               |    |  /rides                   |          |
|  |  /admin/gates             |    |  /rides/{id}/join         |          |
|  |  /admin/tickets           |    |  /rides/{id}/start-cycle  |          |
|  +------------+--------------+    +------------+--------------+          |
|               |                                |                         |
|               v                                v                         |
|  +---------------------------+    +---------------------------+          |
|  |     Actor Runtime         |    |      RideService          |          |
|  |  +-------+ +-------+      |    |  +----+ +----+ +----+     |          |
|  |  |  G1   | |  G2   | ...  |    |  | RC | | GR | | VR |     |          |
|  |  | Actor | | Actor |      |    |  +----+ +----+ +----+     |          |
|  |  +-------+ +-------+      |    |     RideActors            |          |
|  +------------+--------------+    +---------------------------+          |
|               |                                                          |
|               v                                                          |
|  +---------------------------+                                           |
|  |    GateEventPublisher     |                                           |
|  |   (Spring Cloud Stream)   |                                           |
|  +------------+--------------+                                           |
|               |                                                          |
+---------------+----------------------------------------------------------+
                |
                v
   +------------------------+
   |       RabbitMQ         |
   |  Exchange: park.events |
   +------------------------+
```

---

## Prerequis

| Outil | Version | Verification |
|-------|---------|--------------|
| Java JDK | 21+ | `java -version` |
| Docker Desktop | Derniere | `docker --version` |
| Git | Derniere | `git --version` |
| VSCode | Derniere | - |

---

## Installation et Lancement

### Mac / Linux (Terminal VSCode)

#### Etape 1 : Ouvrir le terminal dans VSCode

1. Ouvrir VSCode
2. `Fichier` > `Ouvrir le dossier...` > Selectionner le dossier du projet
3. Ouvrir le terminal : `Terminal` > `Nouveau terminal`

#### Etape 2 : Verifier Java 21+

```bash
java -version
```

Si Java n'est pas installe, telecharger depuis https://adoptium.net/

#### Etape 3 : Lancer Docker Desktop

1. Ouvrir l'application Docker Desktop
2. Attendre que le statut soit "Running"

#### Etape 4 : Lancer RabbitMQ

```bash
docker compose up -d
docker ps
```

Resultat attendu : container `park-rabbitmq` en status "Up"

#### Etape 5 : Rendre le script Maven executable

```bash
chmod +x mvnw
```

#### Etape 6 : Lancer l'application

```bash
./mvnw spring-boot:run
```

---

### Windows 10/11 (Terminal VSCode PowerShell)

#### Etape 1 : Ouvrir le terminal dans VSCode

1. Ouvrir VSCode
2. `Fichier` > `Ouvrir le dossier...` > Selectionner le dossier du projet
3. Ouvrir le terminal : `Terminal` > `Nouveau terminal`
4. S'assurer que le terminal est en **PowerShell** (pas CMD)

#### Etape 2 : Verifier Java 21+

```powershell
java -version
```

Si Java n'est pas installe, telecharger depuis https://adoptium.net/

#### Etape 3 : Configurer JAVA_HOME (si necessaire)

```powershell
# Trouver le chemin Java
java -XshowSettings:properties -version 2>&1 | Select-String "java.home"

# Definir JAVA_HOME (adapter le chemin selon votre installation)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"

# Verifier
echo $env:JAVA_HOME
```

#### Etape 4 : Lancer Docker Desktop

1. Ouvrir l'application Docker Desktop
2. Attendre que le statut soit "Running"

#### Etape 5 : Lancer RabbitMQ

```powershell
docker compose up -d
docker ps
```

Resultat attendu : container `park-rabbitmq` en status "Up"

#### Etape 6 : Lancer l'application

```powershell
.\mvnw.cmd spring-boot:run
```

---

### Resultat attendu au demarrage

```
###############################################
#         CY_LAND PARK SERVICE READY          #
###############################################

  GATE SERVICE
  Portes actives: 3
  Endpoints:
    POST /gate/{gateId}/scan?ticketId=XXX
    GET  /gate/{gateId}/status
    GET/POST/PUT/DELETE /admin/gates
    GET/POST/DELETE /admin/tickets

  RIDE SERVICE
  Attractions actives: 3
  Endpoints:
    GET  /rides
    GET  /rides/{rideId}/state
    POST /rides/{rideId}/join?ticketId=XXX
    POST /rides/{rideId}/start-cycle
    POST /rides/{rideId}/report-fault

###############################################
```

---

## Guide de Test Complet

Ouvrir un **nouveau terminal** dans VSCode pour tester.

### Tests Gate Service - Mac / Linux

#### Test 1 : Verifier que l'application tourne

```bash
curl http://localhost:8081/actuator/health
```

#### Test 2 : Lister les portes

```bash
curl http://localhost:8081/admin/gates
```

#### Test 3 : Creer un ticket

```bash
curl -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=8500"
```

#### Test 4 : Scanner un ticket

```bash
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

#### Test 5 : Tester l'anti-doublon

```bash
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

---

### Tests Ride Service - Mac / Linux

#### Test 6 : Lister les attractions

```bash
curl http://localhost:8081/rides
```

Resultat attendu :
```json
["rc","gr","vr"]
```

#### Test 7 : Voir l'etat d'une attraction

```bash
curl http://localhost:8081/rides/rc/state
```

Resultat attendu :
```json
{"rideId":"RC","queueSize":0,"inCycle":[],"capacity":20,"cycleDurationSec":60,"closed":false}
```

#### Test 8 : Ajouter des visiteurs a la queue

```bash
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V001"
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V002"
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V003"
```

#### Test 9 : Verifier la queue

```bash
curl http://localhost:8081/rides/rc/state
```

Resultat attendu : `"queueSize":3`

#### Test 10 : Demarrer un cycle manuellement

```bash
curl -X POST "http://localhost:8081/rides/rc/start-cycle"
```

#### Test 11 : Signaler une panne

```bash
curl -X POST "http://localhost:8081/rides/gr/report-fault?faultType=MECHANICAL&description=Moteur%20en%20panne"
```

#### Test 12 : Verifier que l'attraction est fermee

```bash
curl http://localhost:8081/rides/gr/state
```

Resultat attendu : `"closed":true`

---

### Tests Gate Service - Windows (PowerShell)

#### Test 1 : Verifier que l'application tourne

```powershell
curl.exe http://localhost:8081/actuator/health
```

#### Test 2 : Lister les portes

```powershell
curl.exe http://localhost:8081/admin/gates
```

#### Test 3 : Creer un ticket

```powershell
$today = Get-Date -Format "yyyy-MM-dd"
curl.exe -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$today&priceCents=8500"
```

#### Test 4 : Scanner un ticket

```powershell
curl.exe -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

---

### Tests Ride Service - Windows (PowerShell)

#### Test 5 : Lister les attractions

```powershell
curl.exe http://localhost:8081/rides
```

#### Test 6 : Voir l'etat d'une attraction

```powershell
curl.exe http://localhost:8081/rides/rc/state
```

#### Test 7 : Ajouter des visiteurs a la queue

```powershell
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V001"
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V002"
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V003"
```

#### Test 8 : Demarrer un cycle

```powershell
curl.exe -X POST "http://localhost:8081/rides/rc/start-cycle"
```

#### Test 9 : Signaler une panne

```powershell
curl.exe -X POST "http://localhost:8081/rides/gr/report-fault?faultType=MECHANICAL&description=Moteur%20en%20panne"
```

---

## Tests Automatises

### Lancer tous les tests

Mac / Linux :
```bash
./mvnw test
```

Windows :
```powershell
.\mvnw.cmd test
```

### Description des tests

| Fichier | Type | Ce qu'il teste |
|---------|------|----------------|
| `GateActorTest.java` | Unitaire | Logique de l'acteur Gate |
| `ActorRuntimeTest.java` | Unitaire | Framework d'acteurs |
| `GateIntegrationTest.java` | Integration | Flux HTTP > Acteur > Event |

### Resultat attendu

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Structure du Projet

```
gate-service/
|-- src/main/java/com/park/
|   |-- GateServiceApplication.java
|   |
|   |-- actor/                            # FRAMEWORK D'ACTEURS
|   |   |-- core/
|   |   |   |-- Actor.java
|   |   |   |-- ActorRef.java
|   |   |   |-- ActorContext.java
|   |   |   |-- ActorFactory.java
|   |   |   +-- Message.java
|   |   +-- runtime/
|   |       |-- ActorRuntime.java
|   |       +-- LocalActorRef.java
|   |
|   |-- gate/                             # GATE SERVICE
|   |   |-- api/
|   |   |   |-- GateController.java
|   |   |   |-- GateAdminController.java
|   |   |   |-- TicketAdminController.java
|   |   |   |-- GateService.java
|   |   |   +-- GlobalExceptionHandler.java
|   |   |-- config/
|   |   |   +-- GateConfiguration.java
|   |   |-- domain/
|   |   |   |-- GateActor.java
|   |   |   |-- ScanTicket.java
|   |   |   +-- VisitorEntered.java
|   |   |-- messaging/
|   |   |   +-- GateEventPublisher.java
|   |   +-- persistence/
|   |       |-- GateEntity.java
|   |       |-- GateRepository.java
|   |       |-- GateType.java
|   |       |-- TicketEntity.java
|   |       |-- TicketRepository.java
|   |       |-- TicketType.java
|   |       +-- TicketAgeCategory.java
|   |
|   +-- ride/                             # RIDE SERVICE
|       |-- actor/
|       |   |-- RideActor.java
|       |   |-- RollerCoaster.java
|       |   |-- GrandeRoue.java
|       |   +-- SimulateurVR.java
|       |-- api/
|       |   +-- RideController.java
|       |-- messaging/
|       |   |-- in/
|       |   |   |-- JoinQueue.java
|       |   |   |-- StartCycle.java
|       |   |   |-- CycleTick.java
|       |   |   +-- ReportFault.java
|       |   +-- out/
|       |       |-- CycleStarted.java
|       |       |-- CycleFinished.java
|       |       +-- QueueUpdated.java
|       +-- service/
|           +-- RideService.java
|
|-- src/main/resources/
|   +-- application.yml
|
|-- src/test/java/com/park/gate/
|   |-- GateActorTest.java
|   |-- ActorRuntimeTest.java
|   +-- GateIntegrationTest.java
|
|-- docker-compose.yml
|-- pom.xml
|-- mvnw
+-- mvnw.cmd
```

---

## Troubleshooting

### `zsh: permission denied: ./mvnw` (Mac/Linux)

```bash
chmod +x mvnw
```

### `JAVA_HOME not found` (Windows)

1. Verifier que Java 21+ est installe : `java -version`
2. Trouver le chemin : `java -XshowSettings:properties -version 2>&1 | Select-String "java.home"`
3. Definir JAVA_HOME : `$env:JAVA_HOME = "C:\chemin\vers\jdk"`

### `Connection refused` sur port 5672

```bash
docker ps
docker compose down
docker compose up -d
```

### `Port 8081 already in use`

Mac/Linux :
```bash
lsof -i :8081
kill -9 <PID>
```

Windows :
```powershell
netstat -ano | findstr :8081
taskkill /PID <PID> /F
```

### `mvnw.cmd : File cannot be loaded` (Windows)

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Attraction non trouvee (404)

Verifiez que vous utilisez le bon ID en minuscules :
- `rc` pour RollerCoaster
- `gr` pour GrandeRoue  
- `vr` pour SimulateurVR

---

## Ressources

- Spring Cloud Stream : https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/
- RabbitMQ Tutorials : https://www.rabbitmq.com/tutorials
- Virtual Threads (JEP 444) : https://openjdk.org/jeps/444