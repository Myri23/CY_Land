# Gate Service - Micro-service de controle d'acces

Micro-service Spring Boot demontrant l'utilisation d'un **modele d'acteurs** pour gerer le controle d'acces aux portes d'un parc d'attractions, avec publication d'evenements sur **RabbitMQ**.

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

### Fonctionnalites de base (modele d'acteurs)

| Fonctionnalite | Description | Endpoint |
|----------------|-------------|----------|
| **Scan de ticket** | Scanner un ticket a une porte (traitement asynchrone via acteur) | `POST /gate/{gateId}/scan?ticketId=XXX` |
| **Anti-doublon** | Un ticket ne peut entrer qu'une seule fois | Gere par l'acteur |
| **Publication evenements** | Publie `VisitorEntered` sur RabbitMQ | Automatique apres scan |
| **Status porte** | Verifier qu'une porte est operationnelle | `GET /gate/{gateId}/status` |

### Fonctionnalites d'administration (CRUD)

| Fonctionnalite | Description | Endpoint |
|----------------|-------------|----------|
| **Liste des portes** | Afficher toutes les portes configurees | `GET /admin/gates` |
| **Creer une porte** | Ajouter une nouvelle porte | `POST /admin/gates?gateId=...&name=...&type=...` |
| **Modifier une porte** | Changer le nom ou le type d'une porte | `PUT /admin/gates/{gateId}?name=...&type=...` |
| **Supprimer une porte** | Retirer une porte du systeme | `DELETE /admin/gates/{gateId}` |
| **Liste des tickets** | Afficher tous les tickets | `GET /admin/tickets` |
| **Creer un ticket** | Emettre un nouveau ticket | `POST /admin/tickets?ticketId=...&type=...&dateValidity=...&priceCents=...` |
| **Supprimer un ticket** | Annuler un ticket | `DELETE /admin/tickets/{ticketId}` |
| **Tickets valides aujourd'hui** | Liste des tickets utilisables aujourd'hui | `GET /admin/tickets/valid-today` |
| **Valider un ticket** | Verifier si un ticket peut acceder a une porte | `GET /admin/tickets/{ticketId}/validate?gateType=...` |

### Types de portes disponibles

- `MAIN_GATE` - Entree principale (tickets classiques et VIP)
- `VIP_GATE` - Entree VIP (tickets VIP uniquement)
- `SERVICE_GATE` - Entree de service
- `EMERGENCY_EXIT` - Sortie de secours

### Types de tickets disponibles

- `CLASSIC_TICKET` - Acces aux portes principales uniquement
- `VIP_TICKET` - Acces a toutes les portes

### Categories d'age

- `ADULT`, `YOUNG`, `CHILD`, `SENIOR`

---

## Architecture

```
+---------------------------------------------------------------------+
|                         GATE SERVICE                                 |
|                                                                      |
|  +--------------+    +-------------+    +-----------------------+   |
|  |  REST API    |--->| GateService |--->|    Actor Runtime      |   |
|  |              |    |             |    |  +-----+ +-----+      |   |
|  | /gate/scan   |    +-------------+    |  | G1  | | G2  | ...  |   |
|  | /admin/gates |                       |  |Actor| |Actor|      |   |
|  | /admin/ticket|                       |  +--+--+ +--+--+      |   |
|  +--------------+                       +-----+-------+----------+   |
|         |                                     |       |             |
|         |                              +------v-------v------+      |
|         |                              |  GateEventPublisher |      |
|         |                              | (Spring Cloud Stream)|      |
|         |                              +----------+----------+      |
|         |                                         |                 |
|  +------v------------------+                      |                 |
|  |   In-Memory Storage     |                      |                 |
|  |  +--------+ +--------+  |                      |                 |
|  |  | Gates  | |Tickets |  |                      |                 |
|  |  +--------+ +--------+  |                      |                 |
|  +-------------------------+                      |                 |
+---------------------------------------------------+-----------------+
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
===========================================
  GATE SERVICE READY
  Portes actives: 3

  SCAN ENDPOINTS:
  - POST /gate/{gateId}/scan?ticketId=XXX
  - GET  /gate/{gateId}/status

  ADMIN ENDPOINTS:
  - GET/POST/PUT/DELETE /admin/gates
  - GET/POST/DELETE /admin/tickets
  - GET /admin/tickets/valid-today
  - GET /admin/tickets/{id}/validate?gateType=XXX
===========================================
```

---

## Guide de Test Complet

Ouvrir un **nouveau terminal** dans VSCode pour tester (garder l'application qui tourne dans le premier terminal).

### Tests sur Mac / Linux

#### Test 1 : Verifier que l'application tourne

```bash
curl http://localhost:8081/actuator/health
```

Resultat attendu :
```json
{"status":"UP"}
```

#### Test 2 : Lister les portes par defaut

```bash
curl http://localhost:8081/admin/gates
```

Resultat attendu : liste de 3 portes (G1, G2, VIP)

#### Test 3 : Creer une nouvelle porte

```bash
curl -X POST "http://localhost:8081/admin/gates?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"
```

Resultat attendu :
```json
{"status":"SUCCESS","message":"Gate G3 created successfully"}
```

#### Test 4 : Creer un ticket valide aujourd'hui

```bash
curl -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=8500"
```

Resultat attendu :
```json
{"status":"SUCCESS","message":"Ticket created successfully","ticketId":"T001"}
```

#### Test 5 : Lister les tickets

```bash
curl http://localhost:8081/admin/tickets
```

#### Test 6 : Verifier la validite d'un ticket

```bash
curl "http://localhost:8081/admin/tickets/T001/validate?gateType=MAIN_GATE"
```

Resultat attendu :
```json
{"valid":true,"reason":"","ticketType":"CLASSIC_TICKET"}
```

#### Test 7 : Scanner un ticket a une porte

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

#### Test 8 : Tester l'anti-doublon

```bash
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

Dans les logs : `[GATE G1] Ticket T001 deja scanne - REJETE`

#### Test 9 : Creer et tester un ticket VIP

```bash
# Creer ticket VIP
curl -X POST "http://localhost:8081/admin/tickets?ticketId=VIP001&type=VIP_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=15000"

# Valider pour VIP_GATE
curl "http://localhost:8081/admin/tickets/VIP001/validate?gateType=VIP_GATE"

# Scanner a la porte VIP
curl -X POST "http://localhost:8081/gate/VIP/scan?ticketId=VIP001"
```

#### Test 10 : Tester qu'un ticket classique ne peut pas acceder a VIP

```bash
# Creer un ticket classique
curl -X POST "http://localhost:8081/admin/tickets?ticketId=T002&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=8500"

# Verifier qu'il NE peut PAS acceder a VIP_GATE
curl "http://localhost:8081/admin/tickets/T002/validate?gateType=VIP_GATE"
```

Resultat attendu :
```json
{"valid":false,"reason":"Ticket type CLASSIC_TICKET cannot access VIP_GATE","ticketType":"CLASSIC_TICKET"}
```

#### Test 11 : Supprimer une porte

```bash
curl -X DELETE "http://localhost:8081/admin/gates/G3"
```

#### Test 12 : Observer les evenements dans RabbitMQ

1. Ouvrir un navigateur : http://localhost:15672
2. Login : `guest` / `guest`
3. Aller dans `Queues and Streams`
4. Cliquer sur `park.events.analytics`
5. Section `Get messages` > Cliquer sur `Get Message(s)`

---

### Tests sur Windows (PowerShell)

#### Test 1 : Verifier que l'application tourne

```powershell
curl.exe http://localhost:8081/actuator/health
```

#### Test 2 : Lister les portes

```powershell
curl.exe http://localhost:8081/admin/gates
```

#### Test 3 : Creer une porte

```powershell
curl.exe -X POST "http://localhost:8081/admin/gates?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"
```

#### Test 4 : Creer un ticket valide aujourd'hui

```powershell
$today = Get-Date -Format "yyyy-MM-dd"
curl.exe -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$today&priceCents=8500"
```

#### Test 5 : Lister les tickets

```powershell
curl.exe http://localhost:8081/admin/tickets
```

#### Test 6 : Valider un ticket

```powershell
curl.exe "http://localhost:8081/admin/tickets/T001/validate?gateType=MAIN_GATE"
```

#### Test 7 : Scanner un ticket

```powershell
curl.exe -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

#### Test 8 : Scanner le meme ticket (test anti-doublon)

```powershell
curl.exe -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

Regarder les logs dans le premier terminal - le ticket sera rejete.

#### Test 9 : Creer et tester un ticket VIP

```powershell
$today = Get-Date -Format "yyyy-MM-dd"

# Creer ticket VIP
curl.exe -X POST "http://localhost:8081/admin/tickets?ticketId=VIP001&type=VIP_TICKET&ageCategory=ADULT&dateValidity=$today&priceCents=15000"

# Valider pour VIP_GATE
curl.exe "http://localhost:8081/admin/tickets/VIP001/validate?gateType=VIP_GATE"

# Scanner a la porte VIP
curl.exe -X POST "http://localhost:8081/gate/VIP/scan?ticketId=VIP001"
```

#### Test 10 : Supprimer une porte

```powershell
curl.exe -X DELETE "http://localhost:8081/admin/gates/G3"
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
| `GateActorTest.java` | Unitaire | Logique metier de l'acteur : acceptation ticket, anti-doublon, snapshot |
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
|   |-- GateServiceApplication.java       # Point d'entree
|   |
|   |-- actor/                            # FRAMEWORK D'ACTEURS
|   |   |-- core/                         # Interfaces
|   |   |   |-- Actor.java
|   |   |   |-- ActorRef.java
|   |   |   |-- ActorContext.java
|   |   |   |-- ActorFactory.java
|   |   |   +-- Message.java
|   |   +-- runtime/                      # Implementation
|   |       |-- ActorRuntime.java         # Gestionnaire du cycle de vie
|   |       +-- LocalActorRef.java        # Mailbox + thread virtuel
|   |
|   +-- gate/                             # DOMAINE METIER
|       |-- api/                          # REST Controllers
|       |   |-- GateController.java       # POST /gate/{id}/scan
|       |   |-- GateAdminController.java  # CRUD /admin/gates
|       |   |-- TicketAdminController.java# CRUD /admin/tickets
|       |   |-- GateService.java
|       |   +-- GlobalExceptionHandler.java
|       |-- config/
|       |   +-- GateConfiguration.java    # Initialisation au demarrage
|       |-- domain/
|       |   |-- GateActor.java            # Logique de la porte
|       |   |-- ScanTicket.java           # Message de scan
|       |   +-- VisitorEntered.java       # Evenement publie
|       |-- messaging/
|       |   +-- GateEventPublisher.java   # Publication RabbitMQ
|       +-- persistence/                  # Stockage en memoire
|           |-- GateEntity.java
|           |-- GateRepository.java
|           |-- GateType.java
|           |-- TicketEntity.java
|           |-- TicketRepository.java
|           |-- TicketType.java
|           +-- TicketAgeCategory.java
|
|-- src/main/resources/
|   +-- application.yml                   # Configuration
|
|-- src/test/java/com/park/gate/
|   |-- GateActorTest.java
|   |-- ActorRuntimeTest.java
|   +-- GateIntegrationTest.java
|
|-- docker-compose.yml                    # RabbitMQ
|-- pom.xml
|-- mvnw                                  # Maven wrapper Unix
+-- mvnw.cmd                              # Maven wrapper Windows
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

---

## Ressources

- Spring Cloud Stream : https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/
- RabbitMQ Tutorials : https://www.rabbitmq.com/tutorials
- Virtual Threads (JEP 444) : https://openjdk.org/jeps/444
