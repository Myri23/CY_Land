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
9. [Ressources](#ressources)

---

## Fonctionnalites

### Gate Service - Controle d'acces

| Fonctionnalite | Description | Endpoint |
|----------------|-------------|----------|
| **Scan de ticket** | Scanner un ticket a une porte | `POST /gate/{gateId}/scan?ticketId=XXX` |
| **Anti-doublon** | Un ticket ne peut entrer qu'une seule fois | Gere par l'acteur |
| **Publication evenements** | Publie `VisitorEntered` sur RabbitMQ | Automatique |
| **Status porte** | Verifier qu'une porte est operationnelle | `GET /gate/{gateId}/status` |
| **Liste des portes** | Afficher toutes les portes | `GET /admin/gates` |
| **Creer une porte** | Ajouter une nouvelle porte | `POST /admin/gates` |
| **Modifier une porte** | Changer le nom ou le type | `PUT /admin/gates/{gateId}` |
| **Supprimer une porte** | Retirer une porte | `DELETE /admin/gates/{gateId}` |
| **Liste des tickets** | Afficher tous les tickets | `GET /admin/tickets` |
| **Creer un ticket** | Emettre un nouveau ticket | `POST /admin/tickets` |
| **Supprimer un ticket** | Annuler un ticket | `DELETE /admin/tickets/{ticketId}` |
| **Tickets valides** | Liste des tickets utilisables aujourd'hui | `GET /admin/tickets/valid-today` |
| **Valider un ticket** | Verifier si un ticket peut acceder a une porte | `GET /admin/tickets/{ticketId}/validate` |

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

- `MAIN_GATE` - Entree principale (tickets classiques et VIP)
- `VIP_GATE` - Entree VIP (tickets VIP uniquement)
- `SERVICE_GATE` - Entree de service
- `EMERGENCY_EXIT` - Sortie de secours

### Types de tickets

- `CLASSIC_TICKET` - Acces portes principales uniquement
- `VIP_TICKET` - Acces toutes portes

### Categories d'age

- `ADULT`, `YOUNG`, `CHILD`, `SENIOR`

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

Ouvrir un **nouveau terminal** dans VSCode pour tester (garder l'application qui tourne dans le premier terminal).

---

### Tests sur Mac / Linux

#### Test 1 : Verifier que l'application tourne

```bash
curl http://localhost:8081/actuator/health
```

Resultat attendu :
```json
{"status":"UP"}
```

---

### Tests Gate Service - Mac / Linux

#### Test 2 : Lister les portes par defaut

```bash
curl http://localhost:8081/admin/gates
```

Resultat attendu : liste de 3 portes (G1, G2, VIP)

#### Test 3 : Recuperer une porte specifique

```bash
curl http://localhost:8081/admin/gates/G1
```

#### Test 4 : Creer une nouvelle porte

```bash
curl -X POST "http://localhost:8081/admin/gates?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"
```

Resultat attendu :
```json
{"status":"SUCCESS","message":"Gate G3 created successfully"}
```

#### Test 5 : Modifier une porte

```bash
curl -X PUT "http://localhost:8081/admin/gates/G3?name=Entree%20Nord%20Modifiee&type=SERVICE_GATE"
```

#### Test 6 : Creer un ticket classique valide aujourd'hui

```bash
curl -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=8500"
```

Resultat attendu :
```json
{"status":"SUCCESS","message":"Ticket created successfully","ticketId":"T001"}
```

#### Test 7 : Creer un ticket VIP

```bash
curl -X POST "http://localhost:8081/admin/tickets?ticketId=VIP001&type=VIP_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=15000"
```

#### Test 8 : Lister tous les tickets

```bash
curl http://localhost:8081/admin/tickets
```

#### Test 9 : Recuperer un ticket specifique

```bash
curl http://localhost:8081/admin/tickets/T001
```

#### Test 10 : Lister les tickets valides aujourd'hui

```bash
curl http://localhost:8081/admin/tickets/valid-today
```

#### Test 11 : Valider un ticket classique pour MAIN_GATE

```bash
curl "http://localhost:8081/admin/tickets/T001/validate?gateType=MAIN_GATE"
```

Resultat attendu :
```json
{"valid":true,"reason":"","ticketType":"CLASSIC_TICKET"}
```

#### Test 12 : Verifier qu'un ticket classique NE peut PAS acceder a VIP_GATE

```bash
curl "http://localhost:8081/admin/tickets/T001/validate?gateType=VIP_GATE"
```

Resultat attendu :
```json
{"valid":false,"reason":"Ticket type CLASSIC_TICKET cannot access VIP_GATE","ticketType":"CLASSIC_TICKET"}
```

#### Test 13 : Verifier qu'un ticket VIP peut acceder a VIP_GATE

```bash
curl "http://localhost:8081/admin/tickets/VIP001/validate?gateType=VIP_GATE"
```

Resultat attendu :
```json
{"valid":true,"reason":"","ticketType":"VIP_TICKET"}
```

#### Test 14 : Verifier le status d'une porte

```bash
curl http://localhost:8081/gate/G1/status
```

Resultat attendu :
```
Gate G1 is operational
```

#### Test 15 : Scanner un ticket a une porte

```bash
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

Resultat attendu :
```json
{"status":"SCAN_ACCEPTED","gateId":"G1","ticketId":"T001"}
```

Dans le terminal de l'application :
```
[GATE G1] Ticket T001 ACCEPTE - Visiteur entre
[PUBLISH] VisitorEntered: ticket=T001, gate=G1
```

#### Test 16 : Tester l'anti-doublon (rescanner le meme ticket)

```bash
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

Dans les logs : `[GATE G1] Ticket T001 deja scanne - REJETE`

#### Test 17 : Scanner le ticket VIP a la porte VIP

```bash
curl -X POST "http://localhost:8081/gate/VIP/scan?ticketId=VIP001"
```

#### Test 18 : Supprimer un ticket

```bash
curl -X DELETE "http://localhost:8081/admin/tickets/T001"
```

#### Test 19 : Supprimer une porte

```bash
curl -X DELETE "http://localhost:8081/admin/gates/G3"
```

#### Test 20 : Observer les evenements dans RabbitMQ

1. Ouvrir un navigateur : http://localhost:15672
2. Login : `guest` / `guest`
3. Aller dans `Queues and Streams`
4. Cliquer sur `park.events.analytics`
5. Section `Get messages` > Cliquer sur `Get Message(s)`

---

### Tests Ride Service - Mac / Linux

#### Test 21 : Lister les attractions disponibles

```bash
curl http://localhost:8081/rides
```

Resultat attendu :
```json
["rc","gr","vr"]
```

#### Test 22 : Voir l'etat du RollerCoaster

```bash
curl http://localhost:8081/rides/rc/state
```

Resultat attendu :
```json
{"rideId":"RC","queueSize":0,"inCycle":[],"capacity":20,"cycleDurationSec":60,"closed":false}
```

#### Test 23 : Voir l'etat de la Grande Roue

```bash
curl http://localhost:8081/rides/gr/state
```

#### Test 24 : Voir l'etat du Simulateur VR

```bash
curl http://localhost:8081/rides/vr/state
```

#### Test 25 : Ajouter un visiteur a la queue du RollerCoaster

```bash
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V001"
```

Resultat attendu :
```json
{"status":"SUCCESS","message":"Ticket V001 ajoute a la queue de rc","queueSize":1,"inCycle":[]}
```

#### Test 26 : Ajouter plusieurs visiteurs a la queue

```bash
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V002"
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V003"
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V004"
curl -X POST "http://localhost:8081/rides/rc/join?ticketId=V005"
```

#### Test 27 : Verifier la taille de la queue

```bash
curl http://localhost:8081/rides/rc/state
```

Resultat attendu : `"queueSize":5`

#### Test 28 : Demarrer un cycle manuellement

```bash
curl -X POST "http://localhost:8081/rides/rc/start-cycle"
```

Resultat attendu :
```json
{"status":"SUCCESS","message":"Cycle demarre pour rc","queueSize":0,"inCycle":["V001","V002","V003","V004","V005"]}
```

#### Test 29 : Verifier que la queue est vide apres le cycle

```bash
curl http://localhost:8081/rides/rc/state
```

#### Test 30 : Tester le demarrage sans visiteurs (erreur attendue)

```bash
curl -X POST "http://localhost:8081/rides/rc/start-cycle"
```

Resultat attendu :
```json
{"status":"ERROR","message":"Aucun visiteur dans la queue de rc"}
```

#### Test 31 : Ajouter des visiteurs a la Grande Roue

```bash
curl -X POST "http://localhost:8081/rides/gr/join?ticketId=GR001"
curl -X POST "http://localhost:8081/rides/gr/join?ticketId=GR002"
curl -X POST "http://localhost:8081/rides/gr/join?ticketId=GR003"
```

#### Test 32 : Demarrer le cycle de la Grande Roue

```bash
curl -X POST "http://localhost:8081/rides/gr/start-cycle"
```

#### Test 33 : Signaler une panne sur le Simulateur VR

```bash
curl -X POST "http://localhost:8081/rides/vr/report-fault?faultType=MECHANICAL&description=Casque%20VR%20defectueux"
```

Resultat attendu :
```json
{"status":"SUCCESS","message":"Panne signalee pour vr: MECHANICAL"}
```

#### Test 34 : Verifier que le Simulateur VR est ferme

```bash
curl http://localhost:8081/rides/vr/state
```

Resultat attendu : `"closed":true`

#### Test 35 : Tester l'ajout a une attraction fermee (erreur attendue)

```bash
curl -X POST "http://localhost:8081/rides/vr/join?ticketId=VR001"
```

Resultat attendu :
```json
{"status":"ERROR","message":"Attraction vr est fermee"}
```

#### Test 36 : Tester une attraction inexistante (erreur attendue)

```bash
curl http://localhost:8081/rides/inexistant/state
```

Resultat attendu : HTTP 404

---

### Tests sur Windows (PowerShell)

#### Test 1 : Verifier que l'application tourne

```powershell
curl.exe http://localhost:8081/actuator/health
```

---

### Tests Gate Service - Windows (PowerShell)

#### Test 2 : Lister les portes par defaut

```powershell
curl.exe http://localhost:8081/admin/gates
```

#### Test 3 : Recuperer une porte specifique

```powershell
curl.exe http://localhost:8081/admin/gates/G1
```

#### Test 4 : Creer une nouvelle porte

```powershell
curl.exe -X POST "http://localhost:8081/admin/gates?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"
```

#### Test 5 : Modifier une porte

```powershell
curl.exe -X PUT "http://localhost:8081/admin/gates/G3?name=Entree%20Nord%20Modifiee&type=SERVICE_GATE"
```

#### Test 6 : Creer un ticket classique valide aujourd'hui

```powershell
$today = Get-Date -Format "yyyy-MM-dd"
curl.exe -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$today&priceCents=8500"
```

#### Test 7 : Creer un ticket VIP

```powershell
$today = Get-Date -Format "yyyy-MM-dd"
curl.exe -X POST "http://localhost:8081/admin/tickets?ticketId=VIP001&type=VIP_TICKET&ageCategory=ADULT&dateValidity=$today&priceCents=15000"
```

#### Test 8 : Lister tous les tickets

```powershell
curl.exe http://localhost:8081/admin/tickets
```

#### Test 9 : Recuperer un ticket specifique

```powershell
curl.exe http://localhost:8081/admin/tickets/T001
```

#### Test 10 : Lister les tickets valides aujourd'hui

```powershell
curl.exe http://localhost:8081/admin/tickets/valid-today
```

#### Test 11 : Valider un ticket classique pour MAIN_GATE

```powershell
curl.exe "http://localhost:8081/admin/tickets/T001/validate?gateType=MAIN_GATE"
```

#### Test 12 : Verifier qu'un ticket classique NE peut PAS acceder a VIP_GATE

```powershell
curl.exe "http://localhost:8081/admin/tickets/T001/validate?gateType=VIP_GATE"
```

#### Test 13 : Verifier qu'un ticket VIP peut acceder a VIP_GATE

```powershell
curl.exe "http://localhost:8081/admin/tickets/VIP001/validate?gateType=VIP_GATE"
```

#### Test 14 : Verifier le status d'une porte

```powershell
curl.exe http://localhost:8081/gate/G1/status
```

#### Test 15 : Scanner un ticket a une porte

```powershell
curl.exe -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

#### Test 16 : Tester l'anti-doublon (rescanner le meme ticket)

```powershell
curl.exe -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

#### Test 17 : Scanner le ticket VIP a la porte VIP

```powershell
curl.exe -X POST "http://localhost:8081/gate/VIP/scan?ticketId=VIP001"
```

#### Test 18 : Supprimer un ticket

```powershell
curl.exe -X DELETE "http://localhost:8081/admin/tickets/T001"
```

#### Test 19 : Supprimer une porte

```powershell
curl.exe -X DELETE "http://localhost:8081/admin/gates/G3"
```

---

### Tests Ride Service - Windows (PowerShell)

#### Test 20 : Lister les attractions disponibles

```powershell
curl.exe http://localhost:8081/rides
```

#### Test 21 : Voir l'etat du RollerCoaster

```powershell
curl.exe http://localhost:8081/rides/rc/state
```

#### Test 22 : Voir l'etat de la Grande Roue

```powershell
curl.exe http://localhost:8081/rides/gr/state
```

#### Test 23 : Voir l'etat du Simulateur VR

```powershell
curl.exe http://localhost:8081/rides/vr/state
```

#### Test 24 : Ajouter un visiteur a la queue du RollerCoaster

```powershell
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V001"
```

#### Test 25 : Ajouter plusieurs visiteurs a la queue

```powershell
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V002"
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V003"
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V004"
curl.exe -X POST "http://localhost:8081/rides/rc/join?ticketId=V005"
```

#### Test 26 : Verifier la taille de la queue

```powershell
curl.exe http://localhost:8081/rides/rc/state
```

#### Test 27 : Demarrer un cycle manuellement

```powershell
curl.exe -X POST "http://localhost:8081/rides/rc/start-cycle"
```

#### Test 28 : Verifier que la queue est vide apres le cycle

```powershell
curl.exe http://localhost:8081/rides/rc/state
```

#### Test 29 : Tester le demarrage sans visiteurs (erreur attendue)

```powershell
curl.exe -X POST "http://localhost:8081/rides/rc/start-cycle"
```

#### Test 30 : Ajouter des visiteurs a la Grande Roue

```powershell
curl.exe -X POST "http://localhost:8081/rides/gr/join?ticketId=GR001"
curl.exe -X POST "http://localhost:8081/rides/gr/join?ticketId=GR002"
curl.exe -X POST "http://localhost:8081/rides/gr/join?ticketId=GR003"
```

#### Test 31 : Demarrer le cycle de la Grande Roue

```powershell
curl.exe -X POST "http://localhost:8081/rides/gr/start-cycle"
```

#### Test 32 : Signaler une panne sur le Simulateur VR

```powershell
curl.exe -X POST "http://localhost:8081/rides/vr/report-fault?faultType=MECHANICAL&description=Casque%20VR%20defectueux"
```

#### Test 33 : Verifier que le Simulateur VR est ferme

```powershell
curl.exe http://localhost:8081/rides/vr/state
```

#### Test 34 : Tester l'ajout a une attraction fermee (erreur attendue)

```powershell
curl.exe -X POST "http://localhost:8081/rides/vr/join?ticketId=VR001"
```

#### Test 35 : Tester une attraction inexistante (erreur attendue)

```powershell
curl.exe http://localhost:8081/rides/inexistant/state
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
| `GateActorTest.java` | Unitaire | Logique metier de l'acteur Gate : acceptation ticket, anti-doublon, snapshot |
| `ActorRuntimeTest.java` | Unitaire | Framework d'acteurs : creation, lookup, traitement FIFO des messages |
| `GateIntegrationTest.java` | Integration | Flux complet HTTP > Acteur > Publication evenement |

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
# Verifier que Docker tourne
docker ps

# Relancer les services
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

Executer dans PowerShell en mode Administrateur :
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Attraction non trouvee (404)

Verifiez que vous utilisez le bon ID en minuscules :
- `rc` pour RollerCoaster
- `gr` pour GrandeRoue
- `vr` pour SimulateurVR

### Ticket invalide pour une porte

- `CLASSIC_TICKET` ne peut acceder qu'aux `MAIN_GATE`
- `VIP_TICKET` peut acceder a toutes les portes

---

## Ressources

- Spring Cloud Stream : https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/
- RabbitMQ Tutorials : https://www.rabbitmq.com/tutorials
- Virtual Threads (JEP 444) : https://openjdk.org/jeps/444
