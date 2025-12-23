# Architecture - CY Land

Document d'architecture du projet CY Land - Framework d'acteurs distribues.

## 1. Vue d'ensemble

```mermaid
graph TB
    CLIENT[Client REST] --> GATE[Gate Service :8081]
    CLIENT --> RIDE[Ride Service :8082]
    
    GATE --> EUREKA[Eureka Server :8761]
    RIDE --> EUREKA
    
    GATE --> RABBIT[RabbitMQ :5672]
    RABBIT --> RIDE
    
    GATE -.->|Circuit Breaker| RIDE
```

Le systeme comprend :
- **Eureka Server** : Annuaire des services
- **Gate Service** : Gestion des entrees du parc
- **Ride Service** : Gestion des attractions
- **RabbitMQ** : Messagerie entre services

---

## 2. Modele d'acteurs

```mermaid
graph LR
    MSG[Message] --> MAILBOX[Mailbox]
    MAILBOX --> ACTOR[Acteur]
    ACTOR --> STATE[Etat]
```

Principe :
- Chaque acteur a sa propre mailbox
- Traitement d'un message a la fois
- Communication uniquement par messages

---

## 3. Communication entre services

### Pattern Tell (asynchrone)

```mermaid
sequenceDiagram
    Client->>Gate: POST /gate/G1/scan
    Gate->>GateActor: tell(ScanTicket)
    Gate-->>Client: 202 Accepted
    GateActor->>RabbitMQ: publish(VisitorEntered)
```

### Pattern Ask (synchrone)

```mermaid
sequenceDiagram
    Client->>Gate: POST /gate/G1/scan-sync
    Gate->>GateActor: ask(ScanTicket)
    GateActor-->>Gate: ScanResult
    Gate-->>Client: 200 OK
```

---

## 4. Cycle de vie des acteurs

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> RUNNING: start()
    RUNNING --> BLOCKED: block()
    BLOCKED --> RUNNING: unblock()
    RUNNING --> RESTARTING: erreur
    RESTARTING --> RUNNING: succes
    RUNNING --> STOPPED: stop()
    STOPPED --> [*]
```

---

## 5. Supervision

Quand un acteur echoue, le superviseur decide :

| Directive | Action |
|-----------|--------|
| RESUME | Continuer |
| RESTART | Redemarrer l'acteur |
| STOP | Arreter l'acteur |
| ESCALATE | Remonter au parent |

Deux strategies :
- **OneForOne** : Seul l'acteur en erreur est affecte
- **AllForOne** : Tous les enfants sont affectes

---

## 6. Circuit Breaker (Resilience4j)

Protection des appels entre services :

```mermaid
stateDiagram-v2
    CLOSED --> OPEN: 5 echecs
    OPEN --> HALF_OPEN: 30 secondes
    HALF_OPEN --> CLOSED: succes
    HALF_OPEN --> OPEN: echec
```

| Etat | Comportement |
|------|--------------|
| CLOSED | Normal, appels autorises |
| OPEN | Echec immediat, pas d'appel |
| HALF_OPEN | Test de recuperation |

Configuration :
- Ouverture apres 5 echecs
- Attente de 30s avant test
- Retry : 3 tentatives (500ms, 1s, 2s)

---

## 7. Structure des acteurs

### Gate Service

| Acteur | Role |
|--------|------|
| G1, G2 | Portes principales |
| VIP | Porte VIP |
| Scanner Pool | Pool de scanners (2-10) |

### Ride Service

| Acteur | Role |
|--------|------|
| rc | RollerCoaster |
| gr | Grande Roue |
| vr | Simulateur VR |
| visitor-tracker | Suivi visiteurs |

---

## 8. Flux de donnees

```mermaid
flowchart LR
    SCAN[Scan ticket] --> VALID{Valide?}
    VALID -->|Oui| ACCEPT[Accepter]
    VALID -->|Non| REJECT[Refuser]
    ACCEPT --> PUBLISH[Publier evenement]
    PUBLISH --> RABBIT[RabbitMQ]
    RABBIT --> TRACKER[Visitor Tracker]
```

---

## 9. Tests

42 tests au total :
- 8 tests framework
- 18 tests Gate Service (dont 10 inter-services)
- 16 tests Ride Service

Tests inter-services :
- Publication d'evenements RabbitMQ
- Resilience quand service indisponible
- Test de charge (50 scans)

---

## 10. Technologies

- Java 21 (Virtual Threads)
- Spring Boot 3.4
- Spring Cloud (Eureka, Stream)
- RabbitMQ
- Resilience4j
- JUnit 5 + Awaitility
