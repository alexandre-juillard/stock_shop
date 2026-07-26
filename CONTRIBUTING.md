# Contribuer au projet stock-api

## Convention de commit

Tous les commits doivent respecter le format suivant :

```
[REF-JIRA]type: message de commit
```

- **`REF-JIRA`** : référence du ticket Jira (ex: `STOCK-123`). Si aucun ticket n'existe, utilisez `NOTICKET`.
- **`type`** : un des types suivants
  - `feat` — nouvelle fonctionnalité
  - `fix` — correction de bug
  - `refactor` — refactorisation sans changement de comportement
  - `test` — ajout/modification de tests
  - `chore` — tâche technique (dépendances, configuration, outillage...)
  - `docs` — documentation uniquement
  - `style` — formatage/style de code
  - `perf` — amélioration de performance
  - `build` — build, dépendances, packaging
  - `ci` — intégration continue (workflows, pipelines)
  - `revert` — annulation d'un commit précédent
- **`message`** : description courte, à l'impératif, sans point final.

### Exemples valides

```
[STOCK-123]feat: ajoute l'endpoint de creation de produit
[STOCK-45]fix: corrige le calcul de quantite en stock
[NOTICKET]chore: met a jour les dependances Maven
```

### ✅ Créer un commit sans avoir à retenir le format

Une invite interactive assemble le message automatiquement :

```powershell
npm run commit
```

Elle vous demandera successivement :
1. la référence Jira,
2. le type de commit (liste à choix),
3. le message.

> Prérequis (une seule fois après avoir cloné le repo) :
> ```powershell
> npm install
> ```
> Cela installe l'outillage (Husky, Commitlint, Commitizen) et active automatiquement les hooks Git du projet.

### 🚫 Que se passe-t-il si le format n'est pas respecté ?

- Un hook **`commit-msg`** valide le message **au moment du `git commit`** : le commit est refusé immédiatement si le format est invalide.
- Un hook **`pre-push`** revérifie en filet de sécurité les commits sur le point d'être poussés (utile si un commit a été créé avec `--no-verify`).
- Une vérification est également effectuée en CI sur chaque Pull Request, en dernier rempart.

### Contourner exceptionnellement le hook

Si besoin (cas rarissime), vous pouvez forcer un commit avec `git commit --no-verify`, mais le filet de sécurité `pre-push`/CI restera actif.

