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

Principales tables : `users`, `oauth_accounts`, `oauth_link_decisions`, `user_sessions`, `push_tokens`, `quantity_types`, `quantity_units`, `categories`, `products`, `stock_items`, `shopping_list_items`, `recipes`, `recipe_ingredients`, `refresh_tokens`.

Identifiants en `UUID`, timestamps en `TIMESTAMPTZ`, suppression en cascade documentée par table (voir commentaires dans les scripts SQL).

## Endpoints d'authentification

| Méthode | Endpoint              | Description                          |
|---------|------------------------|---------------------------------------|
| POST    | `/api/auth/register`   | Inscription (email + mot de passe)    |
| POST    | `/api/auth/login`      | Connexion, retourne access + refresh token |
| POST    | `/api/auth/refresh`    | Rotation du refresh token             |
| POST    | `/api/auth/logout`     | Révocation du refresh token           |

⚠️ La confirmation d'email et les comptes OAuth2 (colonnes déjà présentes en base) ne sont pas encore implémentés côté application.

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

