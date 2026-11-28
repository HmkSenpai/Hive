# 🐝 Hive

**Plateforme d'actualités et de messagerie pour l'école, 100 % réseau local (sans internet).**

Hive centralise les annonces officielles, les discussions de classe et le partage de fichiers sur le réseau interne de l'école. L'étudiant ouvre l'application sur son téléphone, se connecte avec son **matricule** et se retrouve automatiquement dans le groupe de sa salle.

> Projet d'école, développé par une équipe de 3. Client : application Android (Java). Serveur : programme Java tournant sur un PC de l'école. Communication : sockets TCP.

---

## Sommaire

1. [Objectifs](#objectifs)
2. [Fonctionnalités](#fonctionnalités)
3. [Architecture](#architecture)
4. [Protocole TCP](#protocole-tcp)
5. [Stack technique](#stack-technique)
6. [Structure du dépôt](#structure-du-dépôt)
7. [Installation et lancement](#installation-et-lancement)
8. [Rôles et permissions](#rôles-et-permissions)
9. [Sécurité](#sécurité)
10. [Contraintes Android à connaître](#contraintes-android-à-connaître)
11. [Feuille de route](#feuille-de-route)
12. [Équipe](#équipe)

---

## Objectifs

- Diffuser l'information officielle de l'école de façon fiable et rapide.
- Offrir une messagerie interne qui fonctionne **sans connexion internet**.
- Remplacer la dispersion actuelle (groupes WhatsApp multiples, affichages papier, infos ratées).
- Rester léger : une app Android simple et un serveur facile à lancer sur un PC.

## Fonctionnalités

### MVP (version 1)

| Domaine | Détail |
|---|---|
| **Authentification** | Connexion par matricule + mot de passe (activation au premier login). |
| **Groupes de salle automatiques** | À partir de la liste officielle (matricule → filière/salle), l'utilisateur est ajouté au groupe de sa salle ; les groupes sont créés automatiquement. |
| **Messagerie** | Discussions privées, groupes de salle, groupes créés par les utilisateurs. |
| **Pièces jointes** | Envoi de fichiers (images, PDF, documents), taille limitée et configurable. |
| **Messages vocaux** | Enregistrement et lecture intégrés. |
| **Fil d'actualités** | Publié par l'administration ou le conseil des étudiants ; ciblable (école, filière, salle). |
| **Flux du site de l'école** | Récupération périodique des articles du site officiel par le serveur, mis en cache (lecture possible sans internet). |
| **Notifications** | Notifications locales + compteurs de non lus. |

### Idées d'extension

- Annonces **épinglées** et **urgentes** (alerte forte).
- **Accusés de lecture** sur les annonces (statistiques pour l'administration).
- **Canaux par matière** avec dépôt de supports de cours et d'anciens sujets.
- **Sondages** (élection de délégués, choix de dates).
- **Emploi du temps** et calendrier d'événements avec rappels.
- **Boîte à suggestions** anonyme vers le conseil des étudiants.
- **Objets trouvés / perdus**.
- **Recherche** dans les messages et fichiers.
- **Modération** : signalement, masquage, journal d'audit.
- **Panneau d'administration** : import CSV des étudiants, rôles, statistiques, sauvegarde.

## Architecture

```
┌──────────────────────┐                              ┌───────────────────────────────┐
│ Application Android  │      Wi-Fi de l'école        │        Serveur Hive (Java)    │
│       (Java)         │ ◄──────── TCP ─────────────► │     tourne sur un PC de l'école│
│                      │   connexion persistante      │                               │
│  Cache local (Room)  │                              │  Gestion des connexions TCP   │
│  Service de fond     │                              │  Base de données (SQLite)     │
└──────────────────────┘                              │  Stockage fichiers / vocaux   │
                                                      │  Collecteur du site de l'école│
                                                      └───────────────┬───────────────┘
                                                                      │ (si internet dispo,
                                                                      │  uniquement le serveur)
                                                                      ▼
                                                              Site web de l'école
```

Points clés :

- **Un serveur central** sur le réseau de l'école ; les téléphones ne parlent qu'à lui.
- Chaque téléphone garde une **connexion TCP persistante** : le serveur pousse les messages et annonces en temps réel.
- **Flux du site de l'école** : seul le serveur a besoin d'internet. Il récupère les articles à intervalle régulier et les met en cache. Sans internet, les derniers articles restent disponibles.
- **Découverte du serveur** : diffusion UDP sur le réseau local (ou NSD/mDNS), avec saisie manuelle de l'adresse IP en secours.

## Protocole TCP

Le protocole est **défini et documenté en premier** (dossier `docs/protocol.md`), car il est le contrat entre le serveur et l'app.

**Trame** : `[longueur : 4 octets][type : 1 octet][charge utile]`

| Type | Usage |
|---|---|
| `JSON` | Commandes et événements (login, message, annonce, création de groupe…). |
| `FILE_CHUNK` | Morceaux binaires de fichier ou de vocal (ex. 32 Ko), avec identifiant de transfert. |
| `PING / PONG` | Maintien de la connexion et détection de coupure. |

Exemple de message JSON :

```json
{ "type": "SEND_MESSAGE", "id": "c-1042", "groupId": 12, "kind": "TEXT", "body": "Cours à 10h salle B2" }
```

Principes :

- Chaque requête porte un `id` ; le serveur répond avec le même `id` (accusé de réception).
- Les messages envoyés hors connexion sont mis en file d'attente côté app et renvoyés à la reconnexion (sans doublon grâce à l'`id`).
- Les fichiers sont envoyés en morceaux ; le transfert peut reprendre après une coupure.
- À la reconnexion, l'app envoie le dernier identifiant reçu et le serveur renvoie ce qui manque.
- Version du protocole dans le message de connexion, pour refuser proprement les anciennes apps.

## Stack technique

| Couche | Choix |
|---|---|
| App Android | **Java**, Android Studio (package `com.example.hive`), Room pour le cache local |
| Serveur | **Java 17+**, sockets TCP (`java.net` ou NIO/Netty), Gradle ou Maven |
| JSON | Gson ou Jackson |
| Base de données | **SQLite** (JDBC) pour démarrer |
| Fichiers / vocaux | Disque du serveur, métadonnées en base |
| Vocaux | `MediaRecorder` (AAC / m4a) côté Android |
| Collecte du site | Tâche planifiée (RSS si disponible, sinon Jsoup) |
| Code partagé | Module Java `protocol` (classes de messages) utilisé par l'app et le serveur |

## Structure du dépôt

```
hive/
├── app/               # Application Android (Java)
├── server/            # Serveur Java (TCP, base de données, collecteur)
├── protocol/          # Classes de messages et constantes partagées
├── tools/             # mock-server (pour l'app) et fake-client (pour le serveur)
├── docs/              # protocol.md (contrat), exemples JSON, décisions, maquettes
├── AGENTS.md          # Directives de collaboration (humains et agents IA)
├── scripts/           # Import CSV, données de test, sauvegarde
└── README.md
```

## Installation et lancement

> À compléter au fil du développement.

### Serveur (PC de l'école)

Prérequis : Java 17+.

```bash
git clone <url-du-depot> hive && cd hive
./gradlew :server:run
# ou : java -jar server/build/libs/hive-server.jar
```

Configuration (`server/config.properties`) : port TCP, dossier de stockage, taille max des fichiers, URL du site de l'école.

Import des étudiants :

```bash
./gradlew :server:importStudents --args="data/etudiants.csv"
```

Format du CSV :

```csv
matricule,nom,prenom,filiere,niveau,salle
21A001,NGONO,Marie,ITT,L2,ITT2-IR
```

### Application Android

1. Ouvrir le projet dans Android Studio.
2. Lancer sur un téléphone ou un émulateur connecté au même réseau que le serveur.
3. Au premier lancement, l'app cherche le serveur ; sinon saisir l'adresse IP et le port.

## Rôles et permissions

| Rôle | Droits principaux |
|---|---|
| **Étudiant** | Messagerie, groupes, lecture du fil, fichiers et vocaux. |
| **Délégué** | Étudiant + publier dans le groupe de sa salle, épingler des messages. |
| **Conseil des étudiants** | Annonces ciblées, sondages, boîte à suggestions. |
| **Administration** | Annonces officielles/urgentes, gestion des utilisateurs et salles, modération. |
| **Super-admin (technique)** | Configuration serveur, import, sauvegardes, journaux. |

## Sécurité

- Mots de passe hachés (bcrypt ou Argon2), changement obligatoire au premier login.
- Option TLS (`SSLSocket`) avec certificat de l'école ; au minimum, aucun mot de passe en clair sur le réseau.
- Jetons de session, limitation des tentatives de connexion.
- Validation des fichiers (type, taille), noms de fichiers assainis.
- Journal d'audit des actions d'administration et de modération.
- Sauvegarde régulière de la base et du dossier de fichiers.

## Contraintes Android à connaître

- **Pas de notifications push classiques** (FCM nécessite internet). Hive utilise un **service au premier plan** qui garde la connexion TCP ouverte, avec une notification discrète permanente.
- Beaucoup de téléphones (Xiaomi, Tecno, Infinix, etc.) **tuent les services en arrière-plan** : prévoir un écran d'aide pour désactiver l'optimisation de batterie pour Hive.
- La connexion TCP doit **se rétablir automatiquement** après une coupure Wi-Fi ou un changement de réseau.
- Certains Wi-Fi isolent les appareils entre eux (« client isolation ») : le serveur doit être joignable depuis le réseau des étudiants. À tester tôt avec l'administration réseau.

## Feuille de route

- [ ] **Phase 0** : cadrage, maquettes, protocole TCP écrit, dépôt GitHub
- [ ] **Phase 1** : serveur (connexions, auth par matricule, import CSV, salles) + écran de login
- [ ] **Phase 2** : messagerie temps réel (privé, groupes, groupes de salle auto, reconnexion)
- [ ] **Phase 3** : fichiers et messages vocaux
- [ ] **Phase 4** : fil d'actualités + collecte du site de l'école
- [ ] **Phase 5** : administration, rôles, modération
- [ ] **Phase 6** : service de fond, notifications, recherche, mode hors ligne
- [ ] **Phase 7** : tests sur le vrai réseau, documentation, démo, soutenance

## Équipe

| Nom | Rôle |
|---|---|
| _À compléter_ | Serveur / protocole |
| _À compléter_ | Application Android (messagerie, service de fond) |
| _À compléter_ | UI/UX, fil d'actualités, collecteur, administration, documentation |
