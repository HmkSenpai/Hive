# 🐝 Hive

**Plateforme d'actualités et de messagerie pour l'école, 100 % réseau local (sans internet).**

Hive centralise les annonces officielles, les discussions de classe et le partage de fichiers sur le réseau interne de l'école. L'étudiant ouvre l'application Android, se connecte avec son **matricule** et se retrouve automatiquement dans le groupe de sa salle.

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
8. [Contraintes Android et réseau](#contraintes-android-et-réseau)
9. [Sécurité](#sécurité)
10. [Feuille de route](#feuille-de-route)
11. [Équipe](#équipe)
12. [Licence](#licence)

---

## Objectifs

- Diffuser l'information officielle de l'école de façon fiable et rapide.
- Offrir une messagerie interne qui fonctionne **sans connexion internet**.
- Éviter la dispersion actuelle (groupes WhatsApp multiples, affichages papier, infos ratées).
- Une appli mobile légère côté étudiants, un serveur simple à installer et à administrer.

## Fonctionnalités

### MVP (version 1)

| Domaine | Détail |
|---|---|
| **Authentification** | Connexion par matricule + mot de passe (activation au premier login). |
| **Groupes de salle automatiques** | À partir de la liste officielle (matricule → filière/salle), l'utilisateur est ajouté au groupe de sa salle ; les groupes sont créés automatiquement. |
| **Messagerie** | Discussions privées, groupes de salle, groupes créés par les utilisateurs. |
| **Pièces jointes** | Envoi de fichiers (images, PDF, documents) avec limite de taille configurable. |
| **Messages vocaux** | Enregistrement et lecture intégrés (format Opus ou AAC). |
| **Fil d'actualités** | Publié par l'administration ou le conseil des étudiants ; ciblable (toute l'école, une filière, une salle). |
| **Flux du site de l'école** | Récupération périodique des articles du site officiel par le serveur, mis en cache (lecture possible même sans internet). |
| **Notifications** | Notifications locales générées par l'app (sans passer par internet) + compteur de non lus. |

### Prévu / idées d'extension

- Annonces **épinglées** et **urgentes** (notification prioritaire).
- **Emploi du temps** et calendrier d'événements avec rappels.
- **Sondages** et votes (élections de délégués, choix d'activités).
- **Canaux par matière** (UE) avec dépôt de supports de cours et de sujets d'examens.
- **Accusés de lecture** sur les annonces (statistiques de portée pour l'administration).
- **Recherche** dans les messages, fichiers et actualités.
- **Objets trouvés / perdus**.
- **Boîte à suggestions** anonyme vers le conseil des étudiants.
- **Annuaire** (délégués, administration, services).
- Mode **hors ligne côté appli** (file d'attente d'envoi, resynchronisation).
- **Modération** : signalement, masquage, journal d'audit.
- **Interface web d'administration** servie par le serveur : import CSV des étudiants, rôles, statistiques, sauvegarde.

## Architecture

```
┌──────────────────────┐      Wi-Fi de l'école       ┌──────────────────────────────┐
│  Application Hive    │ ◄─────────────────────────► │        Serveur Hive          │
│  Android (Java)      │  HTTPS (REST) + WebSocket   │   (PC/serveur dans l'école)  │
└──────────────────────┘                             │                              │
                                                     │  API REST  ·  WebSocket      │
┌──────────────────────┐        HTTPS (LAN)          │  Base de données             │
│  Interface admin web │ ◄─────────────────────────► │  Stockage fichiers/vocaux    │
│  (navigateur du PC)  │                             │  Service de collecte (site)  │
└──────────────────────┘                             └──────────────┬───────────────┘
                                                                    │ (si internet dispo,
                                                                    │  uniquement le serveur)
                                                                    ▼
                                                            Site web de l'école
```

Points clés :

- **Un serveur central sur le réseau de l'école** ; les téléphones ne parlent qu'à lui.
- **Découverte automatique** du serveur sur le Wi-Fi (mDNS / NSD Android) avec saisie manuelle de l'adresse en secours.
- **Temps réel** par WebSocket (messages, présence, nouvelles annonces).
- **Flux du site de l'école** : seul le serveur a besoin d'internet. Il récupère les articles à intervalle régulier, les stocke et les sert aux appli. Sans internet, les derniers articles restent disponibles.
- **Pas de Firebase (FCM)** : il exige internet. Les notifications reposent sur la connexion WebSocket maintenue par l'appli.

## Stack technique

> Proposition de départ, à valider en équipe.

| Couche | Choix proposé | Alternative |
|---|---|---|
| Application mobile | **Android, Java**, vues XML + Material Components, RecyclerView | |
| Réseau (appli) | **OkHttp** (REST + WebSocket) + **Retrofit** + Gson/Moshi | |
| Stockage local (appli) | **Room** (SQLite) pour le cache hors ligne | |
| Audio (appli) | `MediaRecorder` (enregistrement), `MediaPlayer`/ExoPlayer (lecture) | |
| Serveur | **Java 17 + Spring Boot** (REST, WebSocket/STOMP, Security) | Node.js (Fastify), Python (FastAPI) |
| Base de données | **SQLite** ou **PostgreSQL** | H2 pour les tests |
| Fichiers / vocaux | Disque du serveur + métadonnées en base | MinIO |
| Collecte du site | Tâche planifiée Spring (`@Scheduled`), RSS si disponible, sinon Jsoup | |
| Interface admin | Pages web servies par le serveur (Thymeleaf ou HTML/JS simple) | |
| Déploiement serveur | JAR exécutable (+ service systemd / tâche Windows) | Docker |

Avantage du serveur en Java : même langage que l'appli, modèles de données (DTO) partageables, une seule compétence à maîtriser pour les trois.

## Structure du dépôt

```
hive/
├── server/            # Spring Boot : API, WebSocket, collecte, admin web
├── android/           # Projet Android Studio (Java)
├── shared/            # (optionnel) module Java des DTO partagés
├── docs/              # Architecture, contrat d'API, guide d'installation, maquettes
├── scripts/           # Import CSV, jeu de données de test, sauvegarde
└── README.md
```

## Installation et lancement

> À compléter au fil du développement.

### Prérequis

- JDK 17+, Maven ou Gradle
- Android Studio (SDK 24+ recommandé), un téléphone Android ou un émulateur
- Git

### Serveur

```bash
git clone <url-du-depot> hive && cd hive/server
cp src/main/resources/application-example.yml src/main/resources/application.yml   # port, secret, URL du site de l'école
./mvnw spring-boot:run
```

Import des étudiants (via l'interface admin ou le script) avec le CSV ci-dessous.

### Application Android

1. Ouvrir le dossier `android/` dans Android Studio.
2. Brancher un téléphone (débogage USB) ou lancer un émulateur.
3. Lancer `app`. Au premier démarrage, l'appli cherche le serveur sur le Wi-Fi ; sinon saisir son adresse (ex. `192.168.1.10:8443`).

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

## Contraintes Android et réseau

- **Wi-Fi sans internet** : Android peut abandonner un réseau qui ne donne pas accès à internet et basculer sur les données mobiles. Il faut lier le trafic de l'appli au réseau Wi-Fi (`ConnectivityManager.bindProcessToNetwork`) et/ou demander à l'utilisateur de garder la connexion.
- **Messages en arrière-plan** : un *foreground service* maintient le WebSocket ouvert (avec notification permanente). Prévoir la gestion de l'optimisation de batterie selon les marques.
- **HTTPS et certificat auto-signé** : Android bloque le HTTP en clair depuis la version 9. Utiliser TLS avec un certificat de l'école intégré à l'appli (`network_security_config.xml`).
- **Isolation des clients Wi-Fi** : si les bornes isolent les appareils entre eux, le serveur doit être sur le même réseau filaire/VLAN joignable par les téléphones. À tester très tôt.
- **Permissions** : micro (vocaux), notifications (Android 13+), accès aux fichiers via le sélecteur système.

## Sécurité

- Mots de passe hachés avec **BCrypt/Argon2** ; changement obligatoire au premier login.
- Communications chiffrées (**TLS**), jetons de session à durée limitée, limitation des tentatives de connexion.
- Validation et limitation des fichiers envoyés (type, taille), noms de fichiers assainis.
- Journal d'audit des actions d'administration et de modération.
- Sauvegarde régulière de la base et du dossier de fichiers.

## Feuille de route

- [ ] **Phase 0** : cadrage, maquettes, schéma de base, contrat d'API, dépôt Git
- [ ] **Phase 1** : serveur (auth matricule, utilisateurs, salles) + appli (écran de login, découverte du serveur)
- [ ] **Phase 2** : messagerie temps réel (privé, groupes, groupes de salle automatiques)
- [ ] **Phase 3** : fichiers et messages vocaux
- [ ] **Phase 4** : fil d'actualités + collecte du site de l'école
- [ ] **Phase 5** : interface web d'administration, rôles, modération
- [ ] **Phase 6** : notifications en arrière-plan, recherche, mode hors ligne
- [ ] **Phase 7** : tests sur le vrai réseau de l'école, packaging (APK + JAR), documentation, soutenance

## Équipe

| Nom | Rôle |
|---|---|
| _À compléter_ | Serveur / réseau |
| _À compléter_ | Application Android |
| _À compléter_ | UI/UX, collecte du site, interface admin, documentation |

## Licence

Usage academique
