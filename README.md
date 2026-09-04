# Stock API

API de gestion de stock (produits, catégories, recettes, utilisateurs) développée avec **Java 25** et **Spring Boot 4**.

## Stack technique

- **Java** 25
- **Spring Boot** 4.0.7 (Web MVC, Data JPA, Validation, Security, Actuator)
- **PostgreSQL** 17
- **Flyway** pour les migrations de base de données
- **SpringDoc OpenAPI** pour la documentation de l'API (Swagger UI)
- **Testcontainers** pour les tests d'intégration
- **Docker / Docker Compose** pour l'exécution en conteneurs

## Prérequis

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (ou moteur Docker + Compose v2)
- JDK 25 (uniquement si vous souhaitez lancer le projet sans Docker)
- Le wrapper Maven `mvnw` est fourni, aucune installation de Maven n'est nécessaire

## Configuration

La configuration se fait via un fichier `.env` à la racine du projet (non versionné, voir `.gitignore`).
Un fichier d'exemple `.env.example` liste les variables attendues :

| Variable                 | Description                                                        | Exemple           |
|---------------------------|---------------------------------------------------------------------|--------------------|
| `SPRING_PROFILES_ACTIVE`  | Profil Spring actif (`dev`, `test`, `prod`)                          | `dev`              |
| `SERVER_PORT`             | Port d'écoute de l'API                                               | `8080`             |
| `DB_HOST`                 | Hôte de la base PostgreSQL                                           | `localhost`        |
| `DB_PORT`                 | Port exposé sur l'hôte pour PostgreSQL                               | `5432`             |
| `DB_NAME`                 | Nom de la base de données                                            | `stock_api`        |
| `DB_USER`                 | Utilisateur PostgreSQL                                               | `postgres`         |
| `DB_PASSWORD`             | Mot de passe PostgreSQL                                              | *(à définir)*      |
| `PGADMIN_EMAIL`           | Identifiant de connexion à pgAdmin                                   | `admin@stockshop.fr` |
| `PGADMIN_PASSWORD`        | Mot de passe de connexion à pgAdmin                                  | *(à définir)*      |
| `PGADMIN_PORT`            | Port exposé pour l'interface pgAdmin                                 | `5050`             |
| `SMTP_HOST`               | Hôte du serveur SMTP (vide = Mailpit en local, voir plus bas)        | `sandbox.smtp.mailtrap.io` |
| `SMTP_PORT`               | Port du serveur SMTP                                                 | `2525`             |
| `SMTP_USER`               | Utilisateur SMTP                                                     | *(à définir)*      |
| `SMTP_PASSWORD`           | Mot de passe SMTP                                                    | *(à définir)*      |
| `SMTP_AUTH`               | Active l'authentification SMTP                                      | `true`             |
| `SMTP_STARTTLS`           | Active STARTTLS pour la connexion SMTP                               | `true`             |
| `MAIL_FROM`               | Adresse expéditrice des emails envoyés par l'application             | `no-reply@stockshop.fr` |
| `OAUTH2_MOBILE_REDIRECT_URI` | Deep link de l'app mobile `stock-mobile` (callback OAuth2 Google) | `stockshop://oauth2/callback` |

> ⚠️ Ne jamais commiter le fichier `.env` réel. Dupliquez `.env.example` en `.env` et complétez les valeurs avant de démarrer le projet.

```powershell
Copy-Item .env.example .env
```

### Utiliser Mailtrap (ou un autre service SMTP externe) en développement

Par défaut, le profil `dev` pointe vers **Mailpit** (voir docker-compose.yaml), qui ne nécessite aucune
authentification. Pour utiliser un service externe comme **Mailtrap Sandbox**, renseignez dans `.env` :

```dotenv
SMTP_HOST=sandbox.smtp.mailtrap.io
SMTP_PORT=2525
SMTP_USER=<votre_user_mailtrap>
SMTP_PASSWORD=<votre_password_mailtrap>
SMTP_AUTH=true
SMTP_STARTTLS=true
```

Puis redémarrez le conteneur `api` (`docker compose up -d --build api`) ou l'application locale.
Les emails envoyés par l'application (confirmation de compte, réinitialisation de mot de passe...)
apparaîtront alors dans votre boîte de réception Mailtrap au lieu de Mailpit.

## Lancer le projet avec Docker

Le `docker-compose.yaml` démarre trois services :

- **`db`** : PostgreSQL 17 (données persistées dans un volume Docker)
- **`api`** : l'application Spring Boot, buildée à partir du `Dockerfile`
- **`pgadmin`** : interface web d'administration de la base

### Démarrer la stack complète

```powershell
docker compose up -d --build
```

### Voir l'état des conteneurs

```powershell
docker compose ps
```

### Suivre les logs

```powershell
docker compose logs -f api
docker compose logs -f db
```

### Arrêter les conteneurs (en conservant les données)

```powershell
docker compose stop
```

### Arrêter et supprimer les conteneurs (en conservant les volumes/données)

```powershell
docker compose down
```

### Tout supprimer, y compris les données persistées

```powershell
docker compose down -v
```

### Reconstruire l'image de l'API après une modification du code

```powershell
docker compose up -d --build api
```

### Construire l'image Docker seule (sans Compose)

```powershell
docker build -t stock-api .
```

## Accès aux services (en local via Docker)

| Service      | URL / Adresse                                    |
|--------------|-----------------------------------------------------|
| API          | http://localhost:8080                              |
| Swagger UI   | http://localhost:8080/swagger-ui.html              |
| OpenAPI JSON | http://localhost:8080/v3/api-docs                  |
| Actuator     | http://localhost:8080/actuator/health              |
| pgAdmin      | http://localhost:5050                              |
| PostgreSQL   | `localhost:${DB_PORT}` (accès via un client SQL)   |

## Lancer le projet sans Docker (développement local)

Nécessite une instance PostgreSQL locale et un JDK 25 installé.

```powershell
./mvnw spring-boot:run
```

Le profil actif par défaut est `dev` (voir `application-dev.yml`), qui lit les mêmes variables d'environnement que celles définies dans `.env`.

## Tests

Les tests d'intégration utilisent **Testcontainers** : Docker Desktop doit être démarré pour les exécuter.

```powershell
./mvnw test
```

## Profils Spring disponibles

| Profil  | Fichier                     | Usage                                              |
|---------|------------------------------|-----------------------------------------------------|
| `dev`   | `application-dev.yml`         | Développement local (logs SQL, valeurs par défaut)  |
| `test`  | `application-test.yml`        | Tests automatisés (Testcontainers, Flyway strict)   |
| `prod`  | `application-prod.yml`        | Production (aucune valeur par défaut, logs réduits) |

## Base de données & migrations Flyway

Le schéma est entièrement versionné via **Flyway** (`src/main/resources/db/migration`) et appliqué automatiquement au démarrage de l'application. En cas d'échec d'une migration, le démarrage est bloqué et l'erreur SQL exacte est loguée.

| Migration                                | Contenu                                                        |
|-------------------------------------------|-----------------------------------------------------------------|
| `V1__init_schema.sql`                     | Création de toutes les tables métier (users, produits, stock, recettes, listes de courses, unités de quantité, OAuth, sessions, push tokens...), contraintes, index. |
| `V2__seed_quantity_data.sql`               | Données de seed des référentiels `quantity_types` / `quantity_units` (poids, liquide, unité). |
| `V3__create_refresh_tokens_table.sql`      | Table technique pour la gestion des jetons de rafraîchissement JWT. |
| `V4__add_user_preferred_locale.sql`        | Colonne `preferred_locale` sur `users` (langue utilisée pour les emails et messages traduits). |
| `V5__drop_refresh_tokens_table.sql`        | Suppression de `refresh_tokens`, remplacée par `user_sessions` (jeton opaque haché). |
| `V6__create_oauth_link_contexts_table.sql` | Table des propositions de liaison de compte OAuth2 ↔ compte local en attente. |
| `V7__convert_push_tokens_platform_to_varchar.sql` | Remplace l'ENUM natif `push_platform` par un `VARCHAR` + `CHECK`. |
| `V8__create_oauth_exchange_codes_table.sql` | Table des codes d'échange à usage unique remis à l'app mobile après le callback OAuth2. |

Principales tables : `users`, `oauth_accounts`, `oauth_link_contexts`, `oauth_link_decisions`, `oauth_exchange_codes`, `user_sessions`, `push_tokens`, `quantity_types`, `quantity_units`, `categories`, `products`, `stock_items`, `shopping_list_items`, `recipes`, `recipe_ingredients`.

Identifiants en `UUID`, timestamps en `TIMESTAMPTZ`, suppression en cascade documentée par table (voir commentaires dans les scripts SQL).

## Endpoints

La liste détaillée des routes applicatives n'est pas publiée dans ce README.
Pour la configuration OAuth2 Google, voir la section ci-dessous.

## Authentification OAuth2 Google

L'application permet de se connecter avec un compte Google (`spring-boot-starter-oauth2-client`,
authorization code flow géré côté serveur). Un compte créé via Google est activé d'office
(`is_active=TRUE`, email vérifié par Google) et n'a pas de mot de passe (`password_hash=NULL`) ; le
lien avec le fournisseur est stocké dans `oauth_accounts`.

### Configuration Google Cloud Console

1. Aller sur [Google Cloud Console](https://console.cloud.google.com/), créer (ou sélectionner) un
   projet.
2. **APIs et services > Écran de consentement OAuth** : configurer le type (Externe pour les tests),
   le nom de l'application, l'email de support, puis ajouter les scopes `openid`, `email`, `profile`.
3. **APIs et services > Identifiants > Créer des identifiants > ID client OAuth** :
   - Type d'application : **Application Web** (le flux authorization code reste géré côté serveur,
     même si le client final est l'app mobile `stock-mobile`).
   - **Origines JavaScript autorisées** : non nécessaire (aucun frontend web), laisser vide.
   - **URI de redirection autorisés** : URL publique de l'API suivie de
     `/api/auth/oauth2/callback`, par exemple :
     - Local : `http://localhost:8080/api/auth/oauth2/callback`
     - Production : `https://api.mon-domaine.fr/api/auth/oauth2/callback`
4. Récupérer l'**ID client** et le **secret client** générés, puis les renseigner dans `.env` :
   ```
   GOOGLE_CLIENT_ID=...
   GOOGLE_CLIENT_SECRET=...
   ```
5. Redémarrer l'application. Depuis l'app mobile (WebView/navigateur système), `GET
   /api/auth/oauth2/google` redirige vers l'écran de consentement Google ; après acceptation,
   Google redirige vers `/api/auth/oauth2/callback`, qui **redirige à son tour** vers le deep link
   de l'app mobile (`OAUTH2_MOBILE_REDIRECT_URI`, ex. `stockshop://oauth2/callback`) avec un code
   d'échange à usage unique (`?code=...`), ou un paramètre d'erreur (`?error=oauth2_failed`) en cas
   d'échec.
6. Côté app, échanger ce code contre le résultat de connexion via `GET
   /api/auth/oauth2/exchange?code=...`, qui répond avec `{ accessToken, refreshToken, user }` (ou
   `{ status: "LINK_REQUIRED", linkContext }` si l'email correspond à un compte local existant, à
   résoudre via `POST /api/auth/oauth2/link-decision`). Le code expire rapidement
   (`OAUTH2_EXCHANGE_CODE_EXPIRATION`, 2 minutes par défaut) et n'est utilisable qu'une seule fois.

> ⚠️ **Spécificités mobile (React Native/Expo, app `stock-mobile`)** : `localhost` ne fonctionne
> pas depuis un appareil/émulateur mobile (voir section Docker/local plus bas pour les adresses
> équivalentes), et Google exige un `redirect_uri` en **HTTPS** sauf pour `localhost`/`127.0.0.1`
> (un tunnel type ngrok/Cloudflare Tunnel est nécessaire pour tester depuis un téléphone physique en
> dev). Le deep link `OAUTH2_MOBILE_REDIRECT_URI` doit correspondre au scheme déclaré côté app
> (`app.json` > `expo.scheme`).

### CORS

Aucune configuration CORS n'est appliquée (`SecurityConfig`) : l'API est consommée exclusivement
par l'app mobile native `stock-mobile`, jamais par un navigateur, et aucun usage web n'est prévu. Le
CORS étant une protection appliquée uniquement par les navigateurs (absente des clients HTTP
natifs), la laisser ouverte n'apporterait aucune fonctionnalité tout en élargissant inutilement la
surface d'attaque côté web.

## Internationalisation (i18n)

Tous les textes affichés à l'utilisateur (messages de validation, erreurs API, emails) sont
traduits dynamiquement, sans aucun texte en dur dans le code :

- **Fichiers de traduction** : `src/main/resources/i18n/messages_xx.properties` (un fichier par
  langue ; `messages.properties`, sans suffixe, sert de repli par défaut).
- **Résolution de la langue** (voir `RequestLocaleFilter`), par ordre de priorité :
  1. Langue enregistrée sur le compte (`users.preferred_locale`), si l'utilisateur est authentifié.
  2. En-tête HTTP `Accept-Language`, pour les requêtes anonymes (inscription, connexion...).
  3. Langue par défaut de l'application (`fr`).
- **Langue à l'inscription** : capturée automatiquement depuis `Accept-Language` et stockée sur le
  compte ; modifiable ensuite via `PATCH /api/users/me/locale`.
- **Emails** : toujours envoyés dans la langue enregistrée du destinataire (`users.preferred_locale`),
  indépendamment de la requête HTTP qui déclenche l'envoi (les emails sont asynchrones).

### Ajouter une nouvelle langue

Aucune modification de code, ni migration de base de données, n'est nécessaire :

1. Créer `src/main/resources/i18n/messages_xx.properties` (`xx` = code ISO 639-1, ex. `es`, `de`)
   en traduisant toutes les clés de `messages_fr.properties`.
2. Ajouter le code à la propriété `app.i18n.supported-locales` dans `application.yml` (ou la
   variable d'environnement `SUPPORTED_LOCALES`), ex. `fr,en,es`.

> ⚠️ Les fichiers `.properties` doivent rester en ASCII pur : utiliser des échappements Unicode
> (`\u00e9` pour `é`, etc.) plutôt que des caractères accentués littéraux, afin d'éviter tout risque
> de corruption d'encodage selon l'éditeur/l'OS utilisé pour les éditer.

## Contribuer

Ce projet impose une convention de commit stricte (`[REF-JIRA]type: message`), vérifiée automatiquement via des hooks Git (Husky + Commitlint) et une invite interactive (Commitizen). Voir [CONTRIBUTING.md](./CONTRIBUTING.md) pour la mise en place et l'utilisation.

## Structure du projet

Le projet suit une organisation par domaine métier, chaque module (`category`, `product`, `recipe`, `stock`, `user`) contenant ses propres couches `controller`, `dto`, `entity`, `mapper`, `repository` et `service`.

```
src/main/java/fr/stockshop/stock_api/
├── category/
├── common/
├── configuration/
├── exception/
├── product/
├── recipe/
├── security/
├── stock/
└── user/
```

