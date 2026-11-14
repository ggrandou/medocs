# Mes Médocs

Application Android de **rappel de prise de médicaments**, gratuite, sans publicité et **entièrement locale** : elle ne se connecte jamais à internet (aucune permission `INTERNET`).

> Un simple aide-mémoire. Mes Médocs ne donne aucun conseil médical, ne vérifie ni les doses ni les interactions, et ne remplace ni un médecin ni un pharmacien.

## Fonctions

- Rappels à l'heure de la prise (notification avec « Pris », « Reporter » et « Sauter »), y compris téléphone en veille ou redémarré
- Plannings souples : tous les jours, certains jours de la semaine, tous les N jours, plusieurs heures et doses par jour
- Gestion du stock, alerte quand il est faible (en jours de traitement restants) et prises hors planning
- Recherche d'un médicament par nom ou molécule dans la base publique française, ou saisie manuelle
- Photo de la boîte, prise par l'utilisateur
- Sauvegarde et restauration par un fichier chiffré (mot de passe) que l'utilisateur garde lui-même

## Confidentialité

Toutes les données restent sur le téléphone. Il n'y a ni compte, ni cloud, ni statistiques. La seule sortie de données est le fichier de sauvegarde que l'utilisateur exporte volontairement.

## Compiler

Prérequis : JDK 21, SDK Android (plateforme 36), Python 3.

```sh
python3 -I scripts/build_bdpm.py --download   # construit data/build/bdpm.sqlite (base des médicaments)
cd android && ./gradlew assembleDebug          # compile l'application
./gradlew testDebugUnitTest                    # tests sur PC
```

Voir `CLAUDE.md` pour l'architecture et les points d'attention (alarmes, compatibilité Android 8 à 17, sauvegarde).

## Licence

Ce programme est un logiciel libre : vous pouvez le redistribuer et le modifier selon les termes de la **GNU General Public License version 3** (ou, à votre choix, toute version ultérieure). Il est distribué sans aucune garantie. Voir le fichier [`LICENSE`](LICENSE).

Dépendances (Android Jetpack, Jetpack Compose, Room, Kotlin) : licence Apache 2.0, compatible avec la GPLv3.

## Données des médicaments

Base de données publique des médicaments (ANSM, HAS, Assurance Maladie), publiée sous [Licence Ouverte 2.0 (Etalab)](https://www.etalab.gouv.fr/licence-ouverte-open-licence/). Ces données peuvent être incomplètes ou dépassées : vérifiez toujours sur la boîte et l'ordonnance.
