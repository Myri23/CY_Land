#  Gate Service — Micro-service de contrôle d'accès

Micro-service Spring Boot démontrant l'utilisation d'un **modèle d'acteurs** pour gérer le contrôle d'accès aux portes d'un parc d'attractions, avec **persistance PostgreSQL** et publication d'événements sur **RabbitMQ**.

---

## 📋 Sommaire

1. [Fonctionnalités](#-fonctionnalités)
2. [Architecture](#-architecture)
3. [Prérequis](#-prérequis)
4. [Installation et Lancement](#-installation-et-lancement)
   - [Mac M1 2020](#-mac-m1-2020-terminal-vscode)
   - [Windows 10/11](#-windows-1011-terminal-vscode)
5. [Guide de Test Complet](#-guide-de-test-complet)
6. [Tests Automatisés](#-tests-automatisés)
7. [Structure du Projet](#-structure-du-projet)
8. [Troubleshooting](#-troubleshooting)

---

##  Fonctionnalités

###  Fonctionnalités de base (modèle d'acteurs)

| Fonctionnalité | Description | Endpoint |
|----------------|-------------|----------|
| **Scan de ticket** | Scanner un ticket à une porte (traitement asynchrone via acteur) | `POST /gate/{gateId}/scan?ticketId=XXX` |
| **Anti-doublon** | Un ticket ne peut entrer qu'une seule fois | Géré par l'acteur |
| **Publication événements** | Publie `VisitorEntered` sur RabbitMQ | Automatique après scan |
| **Status porte** | Vérifier qu'une porte est opérationnelle | `GET /gate/{gateId}/status` |

###  Nouvelles fonctionnalités (CRUD + PostgreSQL)

| Fonctionnalité | Description | Endpoint |
|----------------|-------------|----------|
| **Liste des portes** | Afficher toutes les portes configurées | `GET /admin/gates` |
| **Créer une porte** | Ajouter une nouvelle porte | `POST /admin/gates?gateId=...&name=...&type=...` |
| **Modifier une porte** | Changer le nom ou le type d'une porte | `PUT /admin/gates/{gateId}?name=...&type=...` |
| **Supprimer une porte** | Retirer une porte du système | `DELETE /admin/gates/{gateId}` |
| **Liste des tickets** | Afficher tous les tickets | `GET /admin/tickets` |
| **Créer un ticket** | Émettre un nouveau ticket | `POST /admin/tickets?ticketId=...&type=...&dateValidity=...&priceCents=...` |
| **Supprimer un ticket** | Annuler un ticket | `DELETE /admin/tickets/{ticketId}` |
| **Tickets valides aujourd'hui** | Liste des tickets utilisables aujourd'hui | `GET /admin/tickets/valid-today` |
| **Valider un ticket** | Vérifier si un ticket peut accéder à une porte | `GET /admin/tickets/{ticketId}/validate?gateType=...` |

### Types de portes disponibles
- `MAIN_GATE` — Entrée principale (tickets classiques et VIP)
- `VIP_GATE` — Entrée VIP (tickets VIP uniquement)
- `SERVICE_GATE` — Entrée de service
- `EMERGENCY_EXIT` — Sortie de secours

### Types de tickets disponibles
- `CLASSIC_TICKET` — Accès aux portes principales uniquement
- `VIP_TICKET` — Accès à toutes les portes

### Catégories d'âge
- `ADULT`, `YOUNG`, `CHILD`, `SENIOR`

---

##  Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         GATE SERVICE                                 │
│                                                                      │
│  ┌──────────────┐    ┌─────────────┐    ┌───────────────────────┐   │
│  │  REST API    │───▶│ GateService │───▶│    Actor Runtime      │   │
│  │              │    │             │    │  ┌─────┐ ┌─────┐      │   │
│  │ /gate/scan   │    └─────────────┘    │  │ G1  │ │ G2  │ ...  │   │
│  │ /admin/gates │                       │  │Actor│ │Actor│      │   │
│  │ /admin/ticket│                       │  └──┬──┘ └──┬──┘      │   │
│  └──────────────┘                       └─────┼───────┼─────────┘   │
│         │                                     │       │             │
│         │                              ┌──────▼───────▼──────┐      │
│         │                              │  GateEventPublisher │      │
│         │                              │ (Spring Cloud Stream)│      │
│         │                              └──────────┬──────────┘      │
│         │                                         │                 │
│  ┌──────▼──────────────────┐                      │                 │
│  │      PostgreSQL         │                      │                 │
│  │  ┌────────┐ ┌────────┐  │                      │                 │
│  │  │ Gates  │ │Tickets │  │                      │                 │
│  │  └────────┘ └────────┘  │                      │                 │
│  └─────────────────────────┘                      │                 │
└───────────────────────────────────────────────────┼─────────────────┘
                                                    │
                                                    ▼
                                       ┌────────────────────────┐
                                       │       RabbitMQ         │
                                       │  Exchange: park.events │
                                       └────────────────────────┘
```

---

##  Prérequis

| Outil | Version | Vérification |
|-------|---------|--------------|
| Java JDK | 21+ | `java -version` |
| Docker Desktop | Dernière | `docker --version` |
| Git | Dernière | `git --version` |
| VSCode | Dernière | — |

---

##  Installation et Lancement

###  Mac M1 2020 (Terminal VSCode)

#### Étape 1 : Ouvrir le terminal dans VSCode

1. Ouvrir VSCode
2. `Fichier` → `Ouvrir le dossier...` → Sélectionner le dossier `gate-service`
3. Ouvrir le terminal : `Terminal` → `Nouveau terminal` (ou `Ctrl+ù`)

#### Étape 2 : Vérifier Java 21

```bash
java -version
```

**Résultat attendu :**
```
openjdk version "21.0.x" 2024-xx-xx
OpenJDK Runtime Environment ...
```

**Si Java 21 n'est pas installé :**
```bash
# Installer Homebrew si nécessaire
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Installer Java 21
brew install openjdk@21

# Configurer JAVA_HOME
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Vérifier
java -version
```

#### Étape 3 : Lancer Docker Desktop

1. Ouvrir l'application **Docker Desktop**
2. Attendre que le statut soit "Running" (icône verte)

#### Étape 4 : Lancer les services (RabbitMQ + PostgreSQL)

```bash
# Dans le terminal VSCode, depuis le dossier gate-service
docker compose up -d

# Vérifier que les containers tournent
docker ps
```

**Résultat attendu :**
```
CONTAINER ID   IMAGE                   STATUS          PORTS
xxxx           rabbitmq:3-management   Up 10 seconds   5672->5672, 15672->15672
xxxx           postgres:16-alpine      Up 10 seconds   5432->5432
```

#### Étape 5 : Rendre le script Maven exécutable

```bash
chmod +x mvnw
```

#### Étape 6 : Lancer l'application

**Option A : Mode local (H2 en mémoire, recommandé pour commencer)**
```bash
./mvnw spring-boot:run
```

**Option B : Mode PostgreSQL (avec la base de données Docker)**
```bash
./mvnw spring-boot:run -Dspring.profiles.active=postgresql
```

**Résultat attendu :**
```
===========================================
  GATE SERVICE READY
  Portes actives: 3
  
  SCAN ENDPOINTS:
  - POST /gate/{gateId}/scan?ticketId=XXX
  - GET  /gate/{gateId}/status
  
  ADMIN ENDPOINTS:
  - GET/POST/DELETE /admin/gates
  - GET/POST/DELETE /admin/tickets
===========================================
```

---

###  Windows 10/11 (Terminal VSCode)

#### Étape 1 : Ouvrir le terminal dans VSCode

1. Ouvrir VSCode
2. `Fichier` → `Ouvrir le dossier...` → Sélectionner le dossier `gate-service`
3. Ouvrir le terminal : `Terminal` → `Nouveau terminal` (ou `Ctrl+ù`)
4. **Important** : S'assurer que le terminal est en **PowerShell** (pas CMD)
   - Cliquer sur la flèche à côté du `+` dans le terminal
   - Sélectionner `PowerShell`

#### Étape 2 : Vérifier Java 21

```powershell
java -version
```

**Résultat attendu :**
```
openjdk version "21.0.x" 2024-xx-xx
OpenJDK Runtime Environment ...
```

**Si Java 21 n'est pas installé :**

1. Télécharger depuis : https://adoptium.net/temurin/releases/?version=21
2. Installer le `.msi` pour Windows x64
3. **Redémarrer VSCode**
4. Vérifier avec `java -version`

**Configurer JAVA_HOME (si nécessaire) :**
```powershell
# Trouver le chemin Java
where java

# Définir JAVA_HOME (adapter le chemin)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot"
```

#### Étape 3 : Lancer Docker Desktop

1. Ouvrir l'application **Docker Desktop**
2. Attendre que le statut soit "Running" (icône verte en bas à gauche)

#### Étape 4 : Lancer les services (RabbitMQ + PostgreSQL)

```powershell
# Dans le terminal VSCode PowerShell, depuis le dossier gate-service
docker compose up -d

# Vérifier que les containers tournent
docker ps
```

**Résultat attendu :**
```
CONTAINER ID   IMAGE                   STATUS          PORTS
xxxx           rabbitmq:3-management   Up 10 seconds   5672->5672, 15672->15672
xxxx           postgres:16-alpine      Up 10 seconds   5432->5432
```

#### Étape 5 : Lancer l'application

**Option A : Mode local (H2 en mémoire, recommandé pour commencer)**
```powershell
.\mvnw.cmd spring-boot:run
```

**Option B : Mode PostgreSQL (avec la base de données Docker)**
```powershell
.\mvnw.cmd spring-boot:run -D"spring.profiles.active=postgresql"
```

**Résultat attendu :**
```
===========================================
  GATE SERVICE READY
  Portes actives: 3
  
  SCAN ENDPOINTS:
  - POST /gate/{gateId}/scan?ticketId=XXX
  - GET  /gate/{gateId}/status
  
  ADMIN ENDPOINTS:
  - GET/POST/DELETE /admin/gates
  - GET/POST/DELETE /admin/tickets
===========================================
```

---

##  Guide de Test Complet

> **Important** : Ouvrir un **nouveau terminal** dans VSCode pour tester (garder l'application qui tourne dans le premier terminal).

###  Tests sur Mac M1 (Terminal VSCode)

#### Test 1 : Vérifier que l'application tourne

```bash
curl http://localhost:8081/actuator/health
```

**Résultat attendu :**
```json
{"status":"UP"}
```

---

#### Test 2 : Lister les portes par défaut

```bash
curl http://localhost:8081/admin/gates
```

**Résultat attendu :**
```json
[
  {"id":1,"gateId":"G1","name":"Entrée Principale","type":"MAIN_GATE",...},
  {"id":2,"gateId":"G2","name":"Entrée Secondaire","type":"MAIN_GATE",...},
  {"id":3,"gateId":"VIP","name":"Entrée VIP","type":"VIP_GATE",...}
]
```

---

#### Test 3 : Créer une nouvelle porte

```bash
curl -X POST "http://localhost:8081/admin/gates?gateId=G3&name=Entrée%20Nord&type=MAIN_GATE"
```

**Résultat attendu :**
```json
{"status":"SUCCESS","message":"Gate G3 created successfully"}
```

---

#### Test 4 : Créer un ticket valide aujourd'hui

```bash
# Remplacer la date par aujourd'hui au format YYYY-MM-DD
curl -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=8500"
```

**Résultat attendu :**
```json
{"status":"SUCCESS","message":"Ticket created successfully","ticketId":"T001"}
```

---

#### Test 5 : Lister les tickets

```bash
curl http://localhost:8081/admin/tickets
```

**Résultat attendu :**
```json
[{"id":1,"ticketId":"T001","type":"CLASSIC_TICKET","ageCategory":"ADULT",...}]
```

---

#### Test 6 : Vérifier la validité d'un ticket

```bash
curl "http://localhost:8081/admin/tickets/T001/validate?gateType=MAIN_GATE"
```

**Résultat attendu :**
```json
{"valid":true,"reason":"","ticketType":"CLASSIC_TICKET"}
```

---

#### Test 7 : Scanner un ticket à une porte

```bash
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

**Résultat attendu :**
```json
{"status":"SCAN_ACCEPTED","gateId":"G1","ticketId":"T001"}
```

**Dans le terminal de l'application, vous verrez :**
```
[GATE G1] Ticket T001 ACCEPTÉ - Visiteur entré
[PUBLISH] VisitorEntered: ticket=T001, gate=G1
```

---

#### Test 8 : Tester l'anti-doublon (re-scanner le même ticket)

```bash
curl -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

**Résultat attendu :** La requête HTTP retourne toujours 202, mais dans les logs :
```
[GATE G1] Ticket T001 déjà scanné dans cette session - REJETÉ
```

---

#### Test 9 : Vérifier que le ticket est maintenant utilisé

```bash
curl "http://localhost:8081/admin/tickets/T001/validate?gateType=MAIN_GATE"
```

**Résultat attendu :**
```json
{"valid":false,"reason":"Ticket already used at G1","ticketType":"CLASSIC_TICKET"}
```

---

#### Test 10 : Créer un ticket VIP et accéder à la porte VIP

```bash
# Créer un ticket VIP
curl -X POST "http://localhost:8081/admin/tickets?ticketId=VIP001&type=VIP_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=15000"

# Vérifier qu'il peut accéder à VIP_GATE
curl "http://localhost:8081/admin/tickets/VIP001/validate?gateType=VIP_GATE"

# Scanner à la porte VIP
curl -X POST "http://localhost:8081/gate/VIP/scan?ticketId=VIP001"
```

---

#### Test 11 : Tester qu'un ticket classique ne peut pas accéder à VIP

```bash
# Créer un ticket classique
curl -X POST "http://localhost:8081/admin/tickets?ticketId=T002&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$(date +%Y-%m-%d)&priceCents=8500"

# Vérifier qu'il NE peut PAS accéder à VIP_GATE
curl "http://localhost:8081/admin/tickets/T002/validate?gateType=VIP_GATE"
```

**Résultat attendu :**
```json
{"valid":false,"reason":"Ticket type CLASSIC_TICKET cannot access VIP_GATE","ticketType":"CLASSIC_TICKET"}
```

---

#### Test 12 : Supprimer une porte

```bash
curl -X DELETE "http://localhost:8081/admin/gates/G3"
```

**Résultat attendu :**
```json
{"status":"SUCCESS","message":"Gate G3 deleted successfully"}
```

---

#### Test 13 : Observer les événements dans RabbitMQ

1. Ouvrir un navigateur : http://localhost:15672
2. Login : `guest` / `guest`
3. Aller dans `Queues and Streams`
4. Cliquer sur `park.events.analytics`
5. Section `Get messages` → Cliquer sur `Get Message(s)`

**Vous verrez les événements JSON :**
```json
{"ticketId":"T001","gateId":"G1","timestamp":"2025-01-15T10:30:00Z"}
```

---

###  Tests sur Windows 10/11 (PowerShell VSCode)

> **Note** : Sur Windows, utiliser `Invoke-RestMethod` ou `curl.exe`

#### Test 1 : Vérifier que l'application tourne

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

**ou avec curl.exe :**
```powershell
curl.exe http://localhost:8081/actuator/health
```

---

#### Test 2 : Lister les portes

```powershell
Invoke-RestMethod http://localhost:8081/admin/gates | ConvertTo-Json
```

**ou :**
```powershell
curl.exe http://localhost:8081/admin/gates
```

---

#### Test 3 : Créer une porte

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/admin/gates?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"
```

**ou :**
```powershell
curl.exe -X POST "http://localhost:8081/admin/gates?gateId=G3&name=Entree%20Nord&type=MAIN_GATE"
```

---

#### Test 4 : Créer un ticket valide aujourd'hui

```powershell
# Obtenir la date du jour
$today = Get-Date -Format "yyyy-MM-dd"

# Créer le ticket
Invoke-RestMethod -Method Post "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=$today&priceCents=8500"
```

**ou :**
```powershell
curl.exe -X POST "http://localhost:8081/admin/tickets?ticketId=T001&type=CLASSIC_TICKET&ageCategory=ADULT&dateValidity=2025-01-15&priceCents=8500"
```

---

#### Test 5 : Lister les tickets

```powershell
Invoke-RestMethod http://localhost:8081/admin/tickets | ConvertTo-Json
```

---

#### Test 6 : Valider un ticket

```powershell
Invoke-RestMethod "http://localhost:8081/admin/tickets/T001/validate?gateType=MAIN_GATE"
```

---

#### Test 7 : Scanner un ticket

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

**ou :**
```powershell
curl.exe -X POST "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

---

#### Test 8 : Scanner le même ticket (test anti-doublon)

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/gate/G1/scan?ticketId=T001"
```

**Regarder les logs dans le premier terminal — le ticket sera rejeté.**

---

#### Test 9 : Créer et tester un ticket VIP

```powershell
$today = Get-Date -Format "yyyy-MM-dd"

# Créer ticket VIP
Invoke-RestMethod -Method Post "http://localhost:8081/admin/tickets?ticketId=VIP001&type=VIP_TICKET&ageCategory=ADULT&dateValidity=$today&priceCents=15000"

# Valider pour VIP_GATE
Invoke-RestMethod "http://localhost:8081/admin/tickets/VIP001/validate?gateType=VIP_GATE"

# Scanner à la porte VIP
Invoke-RestMethod -Method Post "http://localhost:8081/gate/VIP/scan?ticketId=VIP001"
```

---

#### Test 10 : Supprimer une porte

```powershell
Invoke-RestMethod -Method Delete "http://localhost:8081/admin/gates/G3"
```

---

##  Tests Automatisés

### Lancer tous les tests unitaires et d'intégration

**Mac M1 :**
```bash
./mvnw test
```

**Windows :**
```powershell
.\mvnw.cmd test
```

### Description des tests

| Fichier | Type | Ce qu'il teste |
|---------|------|----------------|
| `GateActorTest.java` | Unitaire | Logique métier de l'acteur : acceptation ticket, anti-doublon, snapshot |
| `ActorRuntimeTest.java` | Unitaire | Framework d'acteurs : création, lookup, traitement FIFO des messages |
| `GateIntegrationTest.java` | Intégration | Flux complet HTTP → Acteur → Publication événement |

### Résultat attendu

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

##  Structure du Projet

```
gate-service/
├── src/main/java/com/park/
│   ├── GateServiceApplication.java       # Point d'entrée
│   │
│   ├── actor/                            #  FRAMEWORK D'ACTEURS
│   │   ├── core/                         # Interfaces
│   │   │   ├── Actor.java
│   │   │   ├── ActorRef.java
│   │   │   ├── ActorContext.java
│   │   │   ├── ActorFactory.java
│   │   │   └── Message.java
│   │   └── runtime/                      # Implémentation
│   │       ├── ActorRuntime.java         # Gestionnaire du cycle de vie
│   │       └── LocalActorRef.java        # Mailbox + thread virtuel
│   │
│   └── gate/                             #  DOMAINE MÉTIER
│       ├── api/                          # REST Controllers
│       │   ├── GateController.java       # POST /gate/{id}/scan
│       │   ├── GateAdminController.java  # CRUD /admin/gates
│       │   ├── TicketAdminController.java# CRUD /admin/tickets
│       │   └── GateService.java
│       ├── config/
│       │   └── GateConfiguration.java    # Initialisation au démarrage
│       ├── domain/
│       │   ├── GateActor.java            # Logique de la porte
│       │   ├── ScanTicket.java           # Message de scan
│       │   └── VisitorEntered.java       # Événement publié
│       ├── messaging/
│       │   └── GateEventPublisher.java   # Publication RabbitMQ
│       └── persistence/                  #  JPA / PostgreSQL
│           ├── GateEntity.java
│           ├── GateRepository.java
│           ├── GateType.java
│           ├── TicketEntity.java
│           ├── TicketRepository.java
│           ├── TicketType.java
│           └── TicketAgeCategory.java
│
├── src/main/resources/
│   └── application.yml                   # Config multi-profils
│
├── src/test/java/com/park/gate/
│   ├── GateActorTest.java
│   ├── ActorRuntimeTest.java
│   └── GateIntegrationTest.java
│
├── docker-compose.yml                    # RabbitMQ + PostgreSQL
├── pom.xml
├── mvnw                                  # Maven wrapper Unix
└── mvnw.cmd                              # Maven wrapper Windows
```

---

##  Troubleshooting

###  `zsh: permission denied: ./mvnw` (Mac)

```bash
chmod +x mvnw
```

###  `JAVA_HOME not found` (Windows)

1. Vérifier que Java 21 est installé : `java -version`
2. Si non, télécharger depuis https://adoptium.net/
3. Redémarrer VSCode après l'installation

###  `Connection refused` sur port 5672 ou 5432

```bash
# Vérifier que Docker tourne
docker ps

# Relancer les services
docker compose down
docker compose up -d
```

###  `Port 8081 already in use`

**Mac :**
```bash
lsof -i :8081
kill -9 <PID>
```

**Windows :**
```powershell
netstat -ano | findstr :8081
taskkill /PID <PID> /F
```

###  Les tests échouent avec `RabbitMQ connection refused`

Les tests utilisent un **TestBinder** qui simule RabbitMQ. Si les tests échouent, vérifier que vous n'avez pas de configuration qui force une connexion réelle.

###  `mvnw.cmd : File cannot be loaded` (Windows)

Exécuter dans PowerShell en mode Administrateur :
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

##  Ressources

- [Spring Cloud Stream](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)


