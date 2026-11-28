# AGENTS.md : directives de collaboration (Hive)

Ce fichier s'adresse aux **agents IA** (Claude Code, Codex, Copilot…) **et aux humains** de l'équipe. Lisez-le en entier avant de modifier quoi que ce soit.

## 1. Contexte du projet

Hive est une plateforme d'actualités et de messagerie pour l'école, qui fonctionne **sans internet** sur le réseau local.

- **Client** : application Android en **Java** (package `com.example.hive`).
- **Serveur** : programme **Java** tournant sur un PC de l'école.
- **Communication** : sockets **TCP**, protocole défini dans `docs/protocol.md`.
- Seul le serveur peut avoir accès à internet (collecte du site de l'école). Le client n'en a jamais besoin.

Voir `README.md` pour les fonctionnalités et l'architecture.

## 2. Règle d'or : le contrat d'abord

`docs/protocol.md` est la **source de vérité** entre le serveur et l'app.

1. **Ne jamais deviner** le format d'un message : le lire dans le contrat.
2. **Ne jamais modifier le contrat dans une PR de fonctionnalité.** Une modification du contrat est une PR séparée, étiquetée `contract`.
3. Une PR `contract` doit mettre à jour **dans le même commit** : `docs/protocol.md`, les classes de `protocol/` et les exemples de `docs/examples/`.
4. Une PR `contract` est relue par **les deux** responsables (serveur et app) avant fusion.
5. Si le contrat est ambigu ou incomplet, **s'arrêter et poser la question**, ne pas improviser une variante locale.

## 3. Structure du dépôt et propriétaires

| Dossier | Contenu | Propriétaire |
|---|---|---|
| `server/` | Serveur TCP, base de données, collecteur du site | Personne A (serveur) |
| `app/` | Application Android | Personne B (app) |
| `protocol/` | Classes de messages partagées, encodage des trames | A et B (relecture croisée) |
| `docs/` | Protocole, architecture, maquettes, exemples | Tous ; `docs/protocol.md` en PR `contract` |
| `tools/mock-server/` | Faux serveur pour développer l'app | Personne A |
| `tools/fake-client/` | Faux client pour tester le serveur | Personne B |
| `app/…/ui`, `server/…/feed`, `server/…/admin` | Écrans, fil d'actualités, collecteur, administration | Personne C |

(Remplacer A, B, C par les prénoms dès la phase 0.)

**Règle** : on ne modifie pas le dossier d'un autre propriétaire sans l'avoir prévenu. Une demande se fait par issue ou message, pas par modification directe, sauf pour un correctif trivial validé en relecture.

## 4. Travailler en parallèle sans se bloquer

- **Développeur app** : développer contre `tools/mock-server` (qui renvoie les exemples de `docs/examples/`). Ne pas attendre le vrai serveur.
- **Développeur serveur** : tester avec `tools/fake-client`. Ne pas attendre l'app.
- Quand le contrat manque d'un message dont vous avez besoin : ouvrir une PR `contract` **d'abord**, puis implémenter.
- Toute fonctionnalité réseau est livrée avec **ses exemples JSON** et un test qui les valide.

## 5. Flux de travail Git

- Branche principale : `main`, **protégée** (pas de push direct).
- Branche de travail : `type/zone-description`, par exemple `feat/server-auth-login`, `fix/app-reconnect`, `contract/add-poll-messages`, `docs/readme-install`.
- Commits courts, à l'impératif, préfixés : `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `contract:`, `chore:`.
- Une PR = un sujet. Petite (idéalement moins de 400 lignes modifiées).
- Une PR a **au moins une relecture** avant fusion ; le propriétaire du dossier concerné relit.
- `main` doit toujours **compiler** (serveur et app).
- Rebase sur `main` avant fusion ; pas de fusion avec des conflits non résolus.

## 6. Qualité et conventions de code (Java)

- Java 17 côté serveur. Côté Android, respecter le `minSdk` défini dans `app/build.gradle` (à fixer en phase 0).
- Noms en anglais dans le code (classes, méthodes, variables). Messages d'interface et documentation en **français**.
- Une classe, une responsabilité. Pas de logique réseau dans les `Activity`/`Fragment` : passer par un `ConnectionManager` / dépôt de données.
- **Aucune opération réseau ou disque sur le thread principal** Android.
- Ne jamais bloquer le serveur sur un client lent : une file d'envoi par connexion, délais d'expiration sur les lectures.
- Gestion d'erreurs explicite : toute réponse `ok: false` est traitée côté app avec un message clair pour l'utilisateur.
- Journaux utiles, **sans données sensibles** (jamais de mot de passe, de jeton ni de contenu de message en clair dans les logs).
- Pas de code mort ni de `TODO` sans issue associée.

## 7. Tests et définition de « terminé »

Une tâche est terminée quand :

- [ ] Le code compile (serveur et app) et `main` n'est pas cassé.
- [ ] Les messages réseau concernés sont conformes à `docs/protocol.md` (tests de contrat).
- [ ] Les cas d'erreur du contrat sont gérés (au moins ceux listés au §8 du protocole concernant la fonctionnalité).
- [ ] La fonctionnalité a été **essayée de bout en bout** (vrai serveur + vrai téléphone, ou mock + vrai téléphone).
- [ ] La documentation à jour (README, protocole, exemples) si le comportement a changé.
- [ ] La PR a été relue.

Tests minimum : encodage/décodage des trames, authentification, envoi/réception de message, reconnexion, transfert de fichier avec coupure.

## 8. Sécurité (à respecter par tous)

- **Aucun secret dans Git** : pas de mot de passe, de clé ou de fichier `.env` réel. Fournir `*.example`.
- Les mots de passe sont **hachés** (bcrypt ou Argon2) ; jamais stockés ni journalisés en clair.
- Le serveur **ne fait jamais confiance au client** : vérifier le rôle et l'appartenance au groupe pour chaque requête.
- Valider toutes les entrées : tailles, types, longueurs, champs obligatoires.
- Fichiers reçus : renommés sur disque, type et taille vérifiés, jamais exécutés.
- Données personnelles des étudiants (liste importée) : hors du dépôt, accès limité, jamais envoyées à un service externe.
- Les messages privés ne sont lisibles ni par l'administration ni par le conseil, sauf règle explicite écrite dans la documentation.

## 9. Contraintes propres à Android

- Pas de push Firebase (pas d'internet) : la réception en arrière-plan repose sur un **service au premier plan** gardant la connexion TCP ouverte.
- Prévoir la reconnexion automatique et la gestion de l'optimisation batterie (écran d'aide pour l'utilisateur).
- Tester sur **plusieurs marques** de téléphones, pas seulement l'émulateur.
- Le cache local (Room) permet d'afficher l'historique sans connexion ; les messages écrits hors connexion sont mis en file d'attente.

## 10. Directives spécifiques aux agents IA

- **Lire** `README.md`, `docs/protocol.md` et ce fichier avant toute modification.
- **Rester dans le périmètre** de la tâche demandée ; ne pas refactorer ni renommer ailleurs sans demande.
- **Ne jamais modifier** `docs/protocol.md`, `protocol/` ni `docs/examples/` en dehors d'une tâche `contract` explicitement demandée.
- **Ne pas inventer** de messages, de champs ou de codes d'erreur : s'ils manquent, le signaler.
- Ne pas ajouter de dépendance sans le justifier (taille de l'APK, licence, nécessité). Préférer la bibliothèque standard.
- Proposer le plan **avant** un changement touchant plusieurs modules.
- Après modification, **compiler et lancer les tests** concernés, et indiquer ce qui a été vérifié.
- Dire clairement ce qui n'a **pas** pu être testé (par exemple : comportement sur un vrai téléphone).
- Ne jamais committer de données réelles d'étudiants ni de secrets.
- Rédiger les commentaires utiles et la documentation en français ; le code en anglais.

## 11. Communication dans l'équipe

- Un point rapide (15 min) deux fois par semaine : avancement, blocages, changements de contrat à prévoir.
- Les décisions techniques importantes sont notées dans `docs/decisions.md` (date, décision, raison).
- Les tâches vivent dans les **issues GitHub** (un tableau simple : À faire / En cours / En relecture / Terminé).
- En cas de désaccord sur le contrat : décision à trois, notée dans `docs/decisions.md`.

## 12. Commandes utiles

> À compléter au fil du développement.

```bash
./gradlew :server:run          # lancer le serveur
./gradlew :server:test         # tests serveur
./gradlew :app:assembleDebug   # compiler l'app
./gradlew :app:testDebugUnitTest
./gradlew :tools:mock-server:run
```
