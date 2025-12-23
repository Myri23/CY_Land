# Architecture - CY Land

Document d'architecture du projet CY Land - Framework d'acteurs distribues.

---

## 1. Vue d'ensemble

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

Le systeme comprend :
- Eureka Server : Annuaire des services
- Gate Service : Gestion des entrees du parc et reception des notifications
- Ride Service : Gestion des attractions
- RabbitMQ : Messagerie entre services

---

## 2. Modele d'acteurs

```
    Message --> Mailbox --> Acteur --> Etat
```

Principe :
- Chaque acteur a sa propre mailbox
- Traitement d'un message a la fois
- Communication uniquement par messages
- Isolation complete de l'etat

---

## 3. Communication entre services

### Pattern Tell (asynchrone)

```
    Client --(POST /gate/G1/scan)--> Gate Service
    Gate Service --(tell(ScanTicket))--> GateActor
    Gate Service --(202 Accepted)--> Client
    GateActor --(publish(VisitorEntered))--> RabbitMQ
```

### Pattern Ask (synchrone)

```
    Client --(POST /gate/G1/scan-sync)--> Gate Service
    Gate Service --(ask(ScanTicket))--> GateActor
    GateActor --(ScanResult)--> Gate Service
    Gate Service --(200 OK)--> Client
```

### Communication inter-services (notifications)

```
    RideActor --(ReportFault)--> RideService
    RideService --(tell via RemoteActorRef)--> notification-handler (Gate Service)
    notification-handler --(broadcast)--> G1, G2, VIP
```

---

## 4. Cycle de vie des acteurs

```
    CREATED --> RUNNING --> BLOCKED --> RUNNING
                   |
                   v
              RESTARTING --> RUNNING
                   |
                   v
               STOPPED
```

Etats possibles :
- CREATED : Acteur cree mais pas encore demarre
- RUNNING : Acteur operationnel, traite les messages
- BLOCKED : Acteur suspendu, les messages s'accumulent
- RESTARTING : Acteur en cours de redemarrage apres une erreur
- STOPPED : Acteur arrete definitivement

---

## 5. Supervision

Quand un acteur echoue, le superviseur decide :

| Directive | Action |
|-----------|--------|
| RESUME | Continuer sans redemarrer |
| RESTART | Redemarrer l'acteur |
| STOP | Arreter l'acteur |
| ESCALATE | Remonter au parent |

Deux strategies :
- OneForOne : Seul l'acteur en erreur est affecte
- AllForOne : Tous les enfants sont affectes

---

## 6. Circuit Breaker (Resilience4j)

Protection des appels entre services :

```
    CLOSED --(5 echecs)--> OPEN --(30 secondes)--> HALF_OPEN
                                                       |
                                          +------------+------------+
                                          |                         |
                                       succes                     echec
                                          |                         |
                                          v                         v
                                       CLOSED                     OPEN
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
| notification-handler | Reception des notifications du Ride Service |

### Ride Service

| Acteur | Role |
|--------|------|
| rc | RollerCoaster (capacite 20, cycle 60s) |
| gr | Grande Roue (capacite 12, cycle 120s) |
| vr | Simulateur VR (capacite 8, cycle 180s) |
| visitor-tracker | Suivi des visiteurs dans le parc |

---

## 8. Notification Handler

L'acteur `notification-handler` est responsable de :

1. Recevoir les notifications du Ride Service :
   - Fermeture d'attraction (panne)
   - Reouverture d'attraction (reparation)

2. Maintenir l'etat des attractions :
   - Map des statuts (rideId -> status)
   - Timestamp de derniere mise a jour

3. Broadcaster aux portes :
   - Alertes de fermeture (RideClosedAlert)
   - Alertes de reouverture (RideReopenedAlert)

Flux de notification :

```
    RideActor (panne) --> RemoteActorRef --> notification-handler
    notification-handler --> G1 (RideClosedAlert)
    notification-handler --> G2 (RideClosedAlert)
    notification-handler --> VIP (RideClosedAlert)
```

---

## 9. Flux de donnees

### Entree d'un visiteur

```
    Scan ticket --> Validation --> Acceptation --> Publication evenement
                        |
                        v
                   Rejet si doublon
```

### Signalement de panne

```
    ReportFault --> Fermeture attraction --> Notification Gate Service
                                                      |
                                                      v
                                          Broadcast aux portes
```

### Reparation

```
    RepairComplete --> Reouverture attraction --> Notification Gate Service
                                                          |
                                                          v
                                              Broadcast aux portes
```

---

## 10. Tests

42 tests au total :
- 8 tests framework
- 18 tests Gate Service (dont 10 inter-services)
- 16 tests Ride Service

Tests inter-services :
- Publication d'evenements RabbitMQ
- Resilience quand service indisponible
- Test de charge (50 scans)
- Communication notification-handler

---

## 11. Technologies

| Technologie | Version | Utilisation |
|-------------|---------|-------------|
| Java | 21 | Virtual Threads, Pattern Matching |
| Spring Boot | 3.4 | Framework principal |
| Spring Cloud | 2024.0.0 | Eureka, Stream |
| RabbitMQ | 3.x | Messaging asynchrone |
| Resilience4j | 2.2.0 | Circuit Breaker, Retry |
| JUnit | 5 | Tests unitaires |
| Awaitility | 4.2.0 | Tests asynchrones |

---

## 12. Diagramme de sequence - Panne et notification

```
    Operateur          RideService       RideActor       notification-handler    GateActor
        |                   |               |                    |                   |
        |--POST /report-fault-->            |                    |                   |
        |                   |--tell-------->|                    |                   |
        |                   |               |--fermeture-------->|                   |
        |                   |               |                    |                   |
        |                   |               |   (via Eureka)     |                   |
        |                   |               |--RemoteActorRef--->|                   |
        |                   |               |                    |--RideClosedAlert->|
        |                   |               |                    |                   |
        |<--200 OK----------|               |                    |                   |
```

---

## 13. Configuration des acteurs

### GateActor

```java
// Supervision OneForOne
maxRestarts: 5
withinTimeRange: 60_000ms
```

### RideActor

```java
// Supervision AllForOne
maxRestarts: 3
withinTimeRange: 120_000ms
```

### Scanner Pool

```java
minInstances: 2
maxInstances: 10
scaleUpThreshold: 70%
scaleDownThreshold: 20%
cooldownPeriod: 30_000ms
```
