# 🐝 Hive

**Plateforme d'actualités et de messagerie pour l'école, 100 % réseau local (sans internet).**

Hive centralise les annonces officielles, les discussions de classe et le partage de fichiers sur le réseau interne de l'école. L'étudiant se connecte avec son **matricule** et se retrouve automatiquement dans le groupe de sa salle.

> Projet d'école.

---

## Sommaire

1. [Objectifs](#objectifs)
2. [Fonctionnalités](#fonctionnalités)
3. [Architecture](#architecture)
4. [Stack technique](#stack-technique)
5. [Structure du dépôt](#structure-du-dépôt)
6. [Installation et lancement](#installation-et-lancement)
7. [Rôles et permissions](#rôles-et-permissions)
8. [Sécurité](#sécurité)
9. [Feuille de route](#feuille-de-route)
10. [Équipe](#équipe)
11. [Licence](#licence)

---

## Objectifs

- Diffuser l'information officielle de l'école de façon fiable et rapide.
- Offrir une messagerie interne qui fonctionne **sans connexion internet**.
- Éviter la dispersion actuelle (groupes WhatsApp multiples, affichages papier, infos ratées).
- Rester léger, installable sur les PC de l'école et facile à administrer.

## Fonctionnalités

### MVP (version 1)

| Domaine | Détail |
|---|---|
| **Authentification** | Connexion par matricule + mot de passe (activation au premier login). |
| **Groupes de salle automatiques** | À partir de la liste officielle (matricule → filière/salle), l'utilisateur est ajouté au groupe de sa salle ; les groupes sont créés automatiquement. |
| **Messagerie** | Discussions privées, groupes de salle, groupes créés par les utilisateurs. |
| **Pièces jointes** | Envoi de fichiers (images, PDF, documents) avec limite de taille configurable. |
| **Messages vocaux** | Enregistrement et lecture intégrés (format Opus). |
| **Fil d'actualités** | Publié par l'administration ou le conseil des étudiants ; ciblable (toute l'école, une filière, une salle). |
| **Flux du site de l'école** | Récupération périodique des articles du site officiel, mis en cache sur le serveur (lecture possible même sans internet). |
| **Notifications** | Notifications bureau + compteur de messages/annonces non lus. |

### Prévu / idées d'extension

- Annonces **épinglées** et **urgentes** (alerte plein écran).
- **Emploi du temps** et calendrier d'événements avec rappels.
- **Sondages** et votes (élections de délégués, choix d'activités).
- **Canaux par matière** (UE) avec dépôt de supports de cours et de sujets d'examens.
- **Accusés de lecture** sur les annonces (statistiques de portée pour l'administration).
- **Recherche** dans les messages, fichiers et actualités.
- **Objets trouvés / perdus**.
- **Boîte à suggestions** anonyme vers le conseil des étudiants.
- **Annuaire** (délégués, administration, services) et statut de présence.
- Mode **hors ligne côté client** (file d'attente d'envoi, resynchronisation).
- **Modération** : signalement, masquage, journal d'audit.
- Tableau de bord **admin** : import CSV des étudiants, gestion des rôles, statistiques, sauvegarde.

## Architecture

```
┌────────────────────┐        Wi-Fi / LAN école        ┌─────────────────────────────┐
│  Client Hive (PC)  │ ◄─────────────────────────────► │       Serveur Hive          │
│  application native│   HTTPS (REST) + WebSocket      │  (un PC/serveur dans l'école)│
└────────────────────┘                                 │                             │
                                                       │  API REST  ·  WebSocket     │
                                                       │  Base de données            │
                                                       │  Stockage fichiers/vocaux   │
                                                       │  Service de collecte (site) │
                                                       └──────────────┬──────────────┘
                                                                      │ (si internet dispo,
                                                                      │  uniquement le serveur)
                                                                      ▼
                                                              Site web de l'école
```

Points clés :

- **Un serveur central sur le réseau de l'école** ; les clients ne parlent qu'à lui.
- **Découverte automatique** du serveur sur le LAN (mDNS) avec saisie manuelle de l'adresse en secours.
- **Temps réel** par WebSocket (messages, présence, nouvelles annonces).
- **Flux du site de l'école** : seul le serveur a besoin d'internet. Il récupère les articles à intervalle régulier, les stocke, et les sert aux clients. Sans internet, les derniers articles restent disponibles.

## Stack technique

> Proposition de départ, à valider en équipe.

| Couche | Choix proposé | Alternative |
|---|---|---|
| Client natif PC | **Tauri 2** (Rust + interface web) | Electron, Qt, .NET (Avalonia) |
| Interface | **React + TypeScript** + Tailwind | Svelte |
| Serveur | **Node.js (Fastify) + TypeScript** | Go, Python (FastAPI) |
| Temps réel | WebSocket | Socket.IO |
| Base de données | **SQLite** (mode WAL) pour démarrer | PostgreSQL si la charge augmente |
| Fichiers / vocaux | Disque du serveur + métadonnées en base | MinIO |
| Collecte du site | Tâche planifiée (RSS si disponible, sinon scraping) | |
| Types partagés | Package TypeScript commun client/serveur | |

## Structure du dépôt

```
hive/
├── apps/
│   ├── server/        # API REST, WebSocket, tâches planifiées
│   ├── client/        # Application desktop (Tauri + React)
│   └── admin/         # (optionnel) interface d'administration
├── packages/
│   └── shared/        # Types, schémas de validation, constantes
├── docs/              # Architecture, API, guide d'installation, maquettes
├── scripts/           # Import CSV, seed de test, sauvegarde
└── README.md
```

## Installation et lancement

> À compléter au fil du développement.

### Prérequis

- Node.js 20+
- Rust (stable) et les prérequis Tauri pour votre OS
- Git

### Serveur

```bash
git clone <url-du-depot> hive && cd hive
npm install
cp apps/server/.env.example apps/server/.env   # configurer port, secret, URL du site de l'école
npm run db:migrate -w apps/server
npm run import:students -w apps/server -- ./data/etudiants.csv
npm run dev -w apps/server
```

### Client

```bash
npm run tauri dev -w apps/client
```

### Format du CSV d'import

```csv
matricule,nom,prenom,filiere,niveau,salle
21A001,NGONO,Marie,ITT,L2,ITT2-IR
```

## Rôles et permissions

| Rôle | Droits principaux |
|---|---|
| **Étudiant** | Messagerie, groupes, lecture du fil, envoi de fichiers/vocaux. |
| **Délégué** | Étudiant + publier dans le groupe de sa salle, épingler des messages. |
| **Conseil des étudiants** | Publier des annonces ciblées, sondages, gérer la boîte à suggestions. |
| **Administration** | Annonces officielles/urgentes, gestion des utilisateurs et salles, modération. |
| **Super-admin (technique)** | Configuration serveur, import, sauvegardes, journaux. |

## Sécurité

- Mots de passe hachés avec **Argon2** ; changement obligatoire au premier login.
- Communications chiffrées (**TLS**, certificat auto-signé de l'école installé sur les postes).
- Jetons de session à durée limitée, limitation du nombre de tentatives de connexion.
- Validation et limitation des fichiers envoyés (type, taille), noms de fichiers assainis.
- Journal d'audit des actions d'administration et de modération.
- Sauvegarde régulière de la base et du dossier de fichiers.

## Feuille de route

- [ ] **Phase 0**: cadrage, maquettes, choix techniques, dépôt Git
- [ ] **Phase 1**: serveur (auth matricule, utilisateurs, salles, base de données)
- [ ] **Phase 2**: messagerie temps réel (privé, groupes, groupes de salle automatiques)
- [ ] **Phase 3**: fichiers et messages vocaux
- [ ] **Phase 4**: fil d'actualités + collecte du site de l'école
- [ ] **Phase 5**: interface d'administration, rôles, modération
- [ ] **Phase 6**: notifications, recherche, mode hors ligne
- [ ] **Phase 7**: tests en conditions réelles sur le réseau de l'école, packaging, documentation, soutenance

## Équipe

| Nom | Rôle |
|---|---|
| _À compléter_ | Backend / réseau |
| _À compléter_ | Client desktop |
| _À compléter_ | UI/UX, collecte du site, administration, documentation |

## Licence

MIT.
