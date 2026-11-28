# Hive : contrat de protocole TCP (v1)

> **Source de vérité unique** entre le serveur et l'application Android.
> Toute modification passe par une PR étiquetée `contract` (voir `AGENTS.md`).
> Statut : **brouillon v1**, à valider en équipe avant la phase 1.

---

## 1. Conventions générales

| Élément | Règle |
|---|---|
| Transport | TCP, connexion persistante (une par appareil) |
| Port par défaut | `5050` (configurable côté serveur) |
| Encodage texte | UTF-8 |
| Entiers dans les trames | Big-endian |
| Dates | Millisecondes depuis l'epoch, UTC (`long`) |
| Identifiants | Entiers `long` attribués par le serveur ; `clientId` (chaîne) attribué par l'app pour dédupliquer |
| Noms de champs | `camelCase` |
| Version du protocole | `1`, envoyée dans `HELLO` |
| Taille max d'une trame JSON | 64 Ko |
| Taille d'un morceau de fichier | 32 Ko (valeur proposée par le serveur) |

## 2. Format des trames

```
┌───────────────┬───────────┬───────────────────────┐
│ longueur (4o) │ type (1o) │ charge utile (n octets)│
└───────────────┴───────────┴───────────────────────┘
```

`longueur` = taille de `type` + `charge utile` (donc ≥ 1).

| Type | Valeur | Contenu |
|---|---|---|
| `JSON` | `0x01` | Un objet JSON UTF-8 (enveloppe, voir §3) |
| `FILE_CHUNK` | `0x02` | `transferId` (4o) + `index` (4o) + données binaires |
| `PING` | `0x03` | Vide |
| `PONG` | `0x04` | Vide |

**Keepalive** : chaque côté envoie `PING` après 20 s sans trafic ; sans `PONG` sous 10 s, la connexion est considérée coupée. L'app se reconnecte avec un délai croissant (1 s, 2 s, 4 s… max 30 s).

## 3. Enveloppes JSON

### Requête (client → serveur)

```json
{ "v": 1, "type": "MESSAGE_SEND", "id": "c-1042", "payload": { } }
```

### Réponse (serveur → client)

Succès :

```json
{ "v": 1, "type": "MESSAGE_SEND", "replyTo": "c-1042", "ok": true, "payload": { } }
```

Erreur :

```json
{ "v": 1, "type": "MESSAGE_SEND", "replyTo": "c-1042", "ok": false,
  "error": { "code": "NOT_A_MEMBER", "message": "Vous n'appartenez pas à ce groupe." } }
```

### Événement (serveur → client, sans requête)

```json
{ "v": 1, "type": "EVT_MESSAGE", "seq": 8841, "payload": { } }
```

`seq` est un compteur **par utilisateur**, strictement croissant. Il sert à la resynchronisation (§7).

## 4. Modèles de données

### User

```json
{ "id": 7, "matricule": "21A001", "nom": "NGONO", "prenom": "Marie",
  "role": "STUDENT", "salleId": 3 }
```

`role` : `STUDENT`, `DELEGATE`, `COUNCIL`, `ADMIN`, `SUPERADMIN`.

### Group

```json
{ "id": 12, "name": "ITT2-IR", "kind": "ROOM", "createdBy": null,
  "memberCount": 38, "createdAt": 1767225600000 }
```

`kind` : `ROOM` (créé automatiquement), `USER` (créé par un utilisateur), `DIRECT` (discussion privée), `CHANNEL` (matière, réservé aux phases ultérieures).

### Attachment

```json
{ "fileId": 301, "name": "tp3.pdf", "mime": "application/pdf", "size": 482113, "durationMs": null }
```

Pour un vocal : `mime` = `audio/mp4` (AAC), `durationMs` renseigné.

### Message

```json
{ "id": 90211, "clientId": "c-1042", "groupId": 12, "senderId": 7,
  "kind": "TEXT", "body": "Cours à 10h en B2", "attachments": [],
  "sentAt": 1767225600000 }
```

`kind` : `TEXT`, `FILE`, `VOICE`.

### FeedPost

```json
{ "id": 55, "source": "INTERNAL", "title": "Rentrée", "body": "…",
  "authorId": 2, "authorLabel": "Administration", "target": { "scope": "ALL" },
  "pinned": false, "urgent": false, "publishedAt": 1767225600000,
  "url": null, "attachments": [] }
```

- `source` : `INTERNAL` (publié dans Hive) ou `SCHOOL_SITE` (collecté sur le site de l'école ; `url` renseigné).
- `target.scope` : `ALL`, `FILIERE`, `SALLE` (avec `target.id` pour les deux derniers).

## 5. Catalogue des messages

### 5.1 Connexion et authentification

| Type | Sens | Payload requête | Payload réponse |
|---|---|---|---|
| `HELLO` | C→S | `{ "protocol": 1, "appVersion": "0.1.0", "deviceId": "…" }` | `{ "serverVersion": "0.1.0", "chunkSize": 32768, "maxFileSize": 10485760, "serverTime": 1767225600000 }` |
| `AUTH_LOGIN` | C→S | `{ "matricule": "21A001", "password": "…" }` | `{ "user": User, "token": "…", "lastSeq": 8841 }` |
| `AUTH_ACTIVATE` | C→S | `{ "matricule": "21A001", "newPassword": "…" }` (premier login) | `{ "user": User, "token": "…", "lastSeq": 0 }` |
| `AUTH_RESUME` | C→S | `{ "token": "…", "lastSeq": 8800 }` | `{ "user": User, "missed": [Événement…] }` |
| `AUTH_LOGOUT` | C→S | `{}` | `{}` |

`HELLO` doit être le premier message. `AUTH_LOGIN` répond `ACTIVATION_REQUIRED` si le compte n'a jamais été activé ; l'app enchaîne alors avec `AUTH_ACTIVATE`.

### 5.2 Groupes

| Type | Sens | Payload requête | Payload réponse |
|---|---|---|---|
| `GROUP_LIST` | C→S | `{}` | `{ "groups": [Group…] }` |
| `GROUP_CREATE` | C→S | `{ "name": "Projet Réseaux", "memberIds": [8, 9] }` | `{ "group": Group }` |
| `GROUP_MEMBERS` | C→S | `{ "groupId": 12 }` | `{ "members": [User…] }` |
| `GROUP_ADD_MEMBER` | C→S | `{ "groupId": 12, "userId": 15 }` | `{}` |
| `GROUP_LEAVE` | C→S | `{ "groupId": 12 }` | `{}` |
| `DIRECT_OPEN` | C→S | `{ "userId": 15 }` | `{ "group": Group }` (créé s'il n'existe pas) |
| `USER_SEARCH` | C→S | `{ "query": "ngono" }` | `{ "users": [User…] }` |

Les groupes `ROOM` ne sont ni créés ni quittés par l'utilisateur : le serveur les gère à partir de la salle importée.

### 5.3 Messages

| Type | Sens | Payload requête | Payload réponse |
|---|---|---|---|
| `MESSAGE_SEND` | C→S | `{ "clientId": "c-1042", "groupId": 12, "kind": "TEXT", "body": "…", "attachmentIds": [] }` | `{ "message": Message }` |
| `MESSAGE_HISTORY` | C→S | `{ "groupId": 12, "beforeId": 90211, "limit": 50 }` (`beforeId` optionnel) | `{ "messages": [Message…], "hasMore": true }` |
| `MESSAGE_READ` | C→S | `{ "groupId": 12, "upToId": 90211 }` | `{}` |

**Idempotence** : si `MESSAGE_SEND` est reçu deux fois avec le même `clientId`, le serveur renvoie le message déjà créé sans le dupliquer.

### 5.4 Fil d'actualités

| Type | Sens | Payload requête | Payload réponse |
|---|---|---|---|
| `FEED_LIST` | C→S | `{ "beforeId": null, "limit": 20, "source": "ALL" }` (`source` : `ALL`, `INTERNAL`, `SCHOOL_SITE`) | `{ "posts": [FeedPost…], "hasMore": true }` |
| `FEED_PUBLISH` | C→S | `{ "title": "…", "body": "…", "target": { "scope": "ALL" }, "pinned": false, "urgent": false, "attachmentIds": [] }` | `{ "post": FeedPost }` |
| `FEED_DELETE` | C→S | `{ "postId": 55 }` | `{}` |
| `FEED_ACK` | C→S | `{ "postId": 55 }` (accusé de lecture) | `{}` |

`FEED_PUBLISH` et `FEED_DELETE` : rôles `COUNCIL`, `ADMIN`, `SUPERADMIN` uniquement.

### 5.5 Fichiers et vocaux

**Envoi**

1. `FILE_UPLOAD_INIT` : `{ "name": "tp3.pdf", "mime": "application/pdf", "size": 482113, "durationMs": null }` → `{ "transferId": 17, "chunkSize": 32768 }`
2. Le client envoie des trames `FILE_CHUNK` (`transferId`, `index` 0, 1, 2…, données).
3. `FILE_UPLOAD_END` : `{ "transferId": 17, "chunkCount": 15 }` → `{ "fileId": 301 }`
4. Le client référence ensuite `fileId` dans `MESSAGE_SEND` ou `FEED_PUBLISH` (`attachmentIds`).

**Reprise** : `FILE_UPLOAD_STATUS` `{ "transferId": 17 }` → `{ "nextIndex": 9 }`, puis le client reprend à cet index.

**Réception**

1. `FILE_DOWNLOAD_INIT` : `{ "fileId": 301, "fromIndex": 0 }` → `{ "transferId": 21, "size": 482113, "chunkCount": 15 }`
2. Le serveur envoie les trames `FILE_CHUNK`.
3. Le serveur termine par l'événement `EVT_FILE_DONE` `{ "transferId": 21 }`.

Règles : taille max définie dans la réponse `HELLO` (`maxFileSize`) ; types refusés : exécutables (`.exe`, `.apk`, `.bat`, `.sh`…) ; le serveur renomme les fichiers sur disque (jamais le nom d'origine).

### 5.6 Synchronisation

| Type | Sens | Payload requête | Payload réponse |
|---|---|---|---|
| `SYNC` | C→S | `{ "lastSeq": 8800 }` | `{ "events": [Événement…], "complete": true }` |

Si l'écart est trop grand (`complete: false`), le client refait `GROUP_LIST`, `FEED_LIST` et l'historique des groupes ouverts.

## 6. Événements serveur

| Type | Payload |
|---|---|
| `EVT_MESSAGE` | `{ "message": Message }` |
| `EVT_GROUP_ADDED` | `{ "group": Group }` (ajouté à un groupe, ex. groupe de salle à la première connexion) |
| `EVT_GROUP_UPDATED` | `{ "group": Group }` |
| `EVT_GROUP_REMOVED` | `{ "groupId": 12 }` |
| `EVT_FEED_POST` | `{ "post": FeedPost }` |
| `EVT_FEED_DELETED` | `{ "postId": 55 }` |
| `EVT_READ` | `{ "groupId": 12, "userId": 15, "upToId": 90211 }` |
| `EVT_FILE_DONE` | `{ "transferId": 21 }` |
| `EVT_FORCE_LOGOUT` | `{ "reason": "SESSION_REPLACED" }` |

## 7. Reconnexion et resynchronisation

1. Connexion TCP, `HELLO`.
2. `AUTH_RESUME` avec le jeton et le dernier `seq` reçu.
3. Le serveur renvoie les événements manqués dans `missed`.
4. L'app renvoie ensuite ses messages en attente (même `clientId`, donc pas de doublon).

Le jeton expire au bout de 30 jours d'inactivité. S'il est refusé (`TOKEN_INVALID`), l'app revient à l'écran de connexion.

## 8. Codes d'erreur

| Code | Signification |
|---|---|
| `PROTOCOL_UNSUPPORTED` | Version du protocole non prise en charge |
| `BAD_REQUEST` | JSON invalide ou champ manquant |
| `AUTH_REQUIRED` | Message envoyé avant authentification |
| `INVALID_CREDENTIALS` | Matricule ou mot de passe incorrect |
| `UNKNOWN_MATRICULE` | Matricule absent de la liste importée |
| `ACTIVATION_REQUIRED` | Compte jamais activé |
| `TOO_MANY_ATTEMPTS` | Trop de tentatives, réessayer plus tard |
| `TOKEN_INVALID` | Jeton expiré ou révoqué |
| `FORBIDDEN` | Rôle insuffisant |
| `NOT_A_MEMBER` | Pas membre du groupe |
| `NOT_FOUND` | Groupe, message, fichier ou utilisateur inexistant |
| `FILE_TOO_LARGE` | Dépasse `maxFileSize` |
| `FILE_TYPE_REJECTED` | Type de fichier interdit |
| `TRANSFER_UNKNOWN` | `transferId` inconnu ou expiré |
| `RATE_LIMITED` | Trop de requêtes |
| `INTERNAL` | Erreur serveur |

## 9. Travailler sans dépendre de l'autre côté

Pour que l'app et le serveur avancent en parallèle :

- **`protocol/`** : classes Java (types de messages, modèles, codes d'erreur) et utilitaires d'encodage/décodage des trames. Utilisé par les deux côtés.
- **`docs/examples/`** : un fichier JSON par message (requête, réponse, erreur) servant de référence et de données de test.
- **`tools/mock-server/`** : faux serveur renvoyant les exemples. Permet de développer l'app sans le vrai serveur.
- **`tools/fake-client/`** : petit client en ligne de commande qui envoie des requêtes. Permet de tester le serveur sans l'app.
- **Tests de contrat** : le serveur et l'app valident leurs messages contre les exemples.

## 10. Évolution du contrat

- Ajouter un champ optionnel ou un nouveau message : compatible, pas de changement de version.
- Renommer, supprimer ou changer le sens d'un champ : **changement cassant**, incrémenter `protocol` et prévoir la compatibilité.
- Toute modification passe par une PR `contract` relue par **les deux** responsables (serveur et app) ; les exemples et les classes de `protocol/` sont mis à jour dans la même PR.

## 11. Journal des changements

| Version | Date | Changement |
|---|---|---|
| 1 (brouillon) | 2026-11-28 | Première version |
