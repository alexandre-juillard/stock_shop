versionning :
Modèle MAJOR.MINOR.PATCH
PATCH -> 0.4.5 devient 0.4.6 avec Correction, Optimisation, Refactoring mineur
MINOR -> 0.4.0 devient 0.5.0 avec nouvelle feature
MAJOR -> 1.0.0 devient 2.0.0 avec breaking change important/version stable

convention nommage : camelCase pour les variables et fonctions, PascalCase pour les classes et interfaces, snake_case pour les tables et colonnes de la BDD. En français pour les variables et fonctions, anglais pour les classes et interfaces. Noms explicites, pas d’abréviation. Pas de texte en dur dans le code.
Les noms de variables et fonctions et classes doivent être explicites et en français.

nommage commit : [feature_name]fix/feat/refactor/test: blabla
branche git : main en version stable, develop en intégration. Tiré branche feature/name depuis develop, eventuellement fix/name en cas de bug.
merge request : faire des MR meme en solo pour avoir un suivi du code intégré

pipeline de déploiement CI/CD sur github qui va lancer les tests : automatiser compilation > lint > tests > build docker > publication image de build ?

tests : Lancement des tests auto dans le pipeline CI/CD.
unitaires : services / validation métier / calculs
integration : avec Testcontainer depuis Repository vers BDD (mock?)
couverture de tests convenu : 80% minimum

securité : bcrypt pour mot de passe, JWT, session avec expiration (sur mobile?), autorisation, politique CORS (car api + mobile)

documenter fonctionnel + technique : swagger pour le technique, documenter chaque feature pour avoir un suivi régulier en fonctionnel.

pas de secret en clair dans le code ni application.yaml

variable environnement dès que possible : DB_HOST,JWT_SECRET, PORT, LOG_LEVEL

appli full traduite avec i8n, aucun texte en dur

logging : définir plusieurs niveaux de logs dans le serveur : INFO > WARN > ERROR

gestion exception : avoir une réponse unique avec un controller qui définit un schéma pour renvoyer les exceptions

validation données : uniquement avec les annotations, rien dans les controller

migration BDD : avec Flyway, jamais modifié un script deja executé. Avoir plusieurs versions pour respecter un cycle de vie réaliste.

architecture package : plutot que controller/ service/ repository/, on adoptera une archi orientée fonctionnalité comme stock/controller /service/repository, user/controller /service …

respecter les bonnes pratiques en java : DTO en passant par un Mapper qui utilise MapStruct. Le code ne doit jamais changer entre les environnements en utilisant les profils spring (application.yaml, application-dev, application-test, application-prod). Nommage variables et fonctions en francais, explicite. Commenter les fonctions si besoin

docker : Dockerfile pour l’api, docker-compose pour le dev, volumes pour la bdd, un profil dev et un profil prod, des healthcheck et dépendances au démarrage.
