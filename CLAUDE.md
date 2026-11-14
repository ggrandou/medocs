# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## État du projet

Dépôt vide au démarrage (aucun code, aucune commande de build/test définie). Ce fichier consigne les décisions de conception déjà prises ; à compléter avec les vraies commandes dès que le code existe.

## Objectif

Application Android de **rappel de prise de médicaments**, d'abord personnelle, puis diffusée publiquement : gratuite, sans publicité, open source (GPLv3 ou AGPLv3 envisagée). Interface et documentation en français.

Périmètre volontairement restreint :
- rappel à l'heure de la prise (actions « Pris / Reporter / Sauter » dans la notification) ;
- gestion du stock et alerte de stock faible ;
- prises ponctuelles hors planning régulier.

**Hors périmètre, à ne pas ajouter** : conseil médical, analyse du type de médicament, interactions, dosage/posologie. Cela ferait basculer l'app vers la qualification de dispositif médical. Un avertissement clair doit être affiché dans l'app.

## Décisions structurantes

- **Plateforme** : Android uniquement, Kotlin + Jetpack Compose (Material 3). Flutter et iOS ont été écartés (pas d'iPhone/Mac disponible, et la fiabilité des alarmes prime).
- **100 % local, sans accès internet** : ne pas déclarer la permission `INTERNET`, ne pas ajouter de dépendance réseau, d'analytics, ni de Google Play Services (compatibilité F-Droid, argument de confiance sur les données de santé). Permissions prévues : `POST_NOTIFICATIONS`, alarmes exactes, caméra ou sélecteur d'image pour la photo de boîte.
- **Rappels** : `AlarmManager.setExactAndAllowWhileIdle`. Un `BroadcastReceiver` reprogramme les alarmes sur `BOOT_COMPLETED`, `TIMEZONE_CHANGED` et `TIME_SET`. C'est le principal risque technique : à valider d'abord sur un vrai téléphone (redémarrage, économie d'énergie, changement d'heure, constructeurs à gestion batterie agressive).
- **Deux bases séparées** : la base BDPM (en lecture seule, embarquée dans `assets/`, chargée via Room `createFromAsset`) et la base utilisateur. Cela permet de remplacer la BDPM à chaque release sans toucher aux données de l'utilisateur.
- **Images** : pas de scraping de photos de boîtes (droits d'auteur). Photo prise par l'utilisateur, stockée en local ; repli sur une icône selon la forme galénique.
- **Sauvegarde** : export/import d'un fichier chiffré choisi par l'utilisateur (pas de cloud).
- **Distribution** : F-Droid en priorité, puis Play Store.

## Commandes

- Construire la base BDPM : `python3 -I scripts/build_bdpm.py --download` (sans `--download`, réutilise `data/raw/`). Sortie : `data/build/bdpm.sqlite` (tables `specialite`, `substance`, `presentation`, `meta`). Bibliothèque standard uniquement.
- Tests du script : `python3 -I -m unittest discover -s scripts -v` ; un seul test : `python3 -I -m unittest discover -s scripts -k test_multiplicateur_en_tete`. Ils portent sur `parse_unites` (extraction du nombre d'unités depuis le libellé) ; ajouter un cas dans `scripts/test_build_bdpm.py` à chaque libellé BDPM mal interprété.

- Application Android (dossier `android/`, Gradle 8.13 + AGP 8.13.2 + Kotlin 2.2.10, SDK dans `~/Android/Sdk`, JDK 21) : tout se fait en ligne de commande, Android Studio n'est pas nécessaire. Depuis `android/` : `./gradlew assembleDebug` puis `adb install -r app/build/outputs/apk/debug/app-debug.apk`. `local.properties` (chemin du SDK) est ignoré par git : le recréer avec `sdk.dir=...` sur une autre machine.
- **Ordre de build** : la base BDPM est embarquée en assets depuis `data/build/bdpm.sqlite` (tâche Gradle `copierBdpm`, échoue avec un message clair si le fichier manque) : lancer `scripts/build_bdpm.py` avant `./gradlew assembleDebug`. Au premier lancement, `bdpm/Bdpm.kt` copie la base dans `filesDir` (recopiée à chaque mise à jour de l'app) et l'ouvre en lecture seule.
- **Versions figées** : seules les plateformes Android 34 et 36 sont installées et AGP 8.13.2 recommande `compileSdk` 36 ; les Compose récents exigent 37. Le BOM Compose est donc épinglé à `2025.12.00` : ne pas le monter sans installer la plateforme 37 et passer à un AGP compatible.
- Tests Kotlin : `./gradlew testDebugUnitTest` (JVM, logique de `domain/`, sans téléphone) ; `./gradlew connectedDebugAndroidTest` (sur l'appareil, base Room en mémoire). Attention : ce dernier **désinstalle l'application** à la fin (perte des autorisations et du journal du prototype) : réinstaller ensuite avec `adb install`.
- Appareil de test : Galaxy S23 Ultra, Android 17 (One UI, gestion batterie agressive à tester).
- `ProtoAlarmesActivity` est le **prototype d'alarmes** (Views classiques, accessible depuis le menu « Plus » de l'app) : il compare `setExactAndAllowWhileIdle` et `setAlarmClock`, journalise l'écart entre heure prévue et heure réelle, et reprogramme après redémarrage/changement d'heure.

## Données BDPM

Source : Base de données publique des médicaments (data.gouv.fr / base-donnees-publique.medicaments.gouv.fr), Licence Ouverte Etalab (mentionner la source).

- Fichiers texte séparés par tabulations. **L'encodage varie selon le fichier** (constaté : `CIS_CIP_bdpm` en UTF-8, `CIS_bdpm` et `CIS_COMPO_bdpm` en ISO-8859-1) : le script tente UTF-8 puis ISO-8859-1.
- Jointure sur le code CIS : `CIS_bdpm` (spécialités, forme, voie, statut), `CIS_COMPO_bdpm` (substances et dosage), `CIS_CIP_bdpm` (présentations, CIP13).
- Ne garder que les médicaments **commercialisés**. Écarter prix, remboursements, avis HAS, notices, ruptures.
- **Le nombre d'unités par boîte n'existe que dans le libellé texte** de la présentation (ex. « plaquette(s) thermoformée(s) PVC de 30 comprimé(s) »). Il est extrait par expressions régulières ; la valeur doit toujours rester modifiable par l'utilisateur (sirops, collyres, stylos injecteurs). Prévoir aussi une saisie manuelle complète pour les médicaments absents de la base.
- La base doit être produite par un script reproductible (téléchargement → filtrage → SQLite compacte), exécutable en CI pour générer les releases.

## Modèle de données (Room, `android/app/src/main/kotlin/org/medocs/rappel/`)

Tables : `medicament`, `planning` (règle de récurrence) → `planning_heure` (heure + dose), `prise` (journal), `stock_mouvement`. Décisions non évidentes :
- **Le stock n'est jamais stocké** : c'est la somme des `stock_mouvement.delta`. Un mouvement PRISE est lié à sa `prise` (cascade) : supprimer une prise, ou la passer en « sautée », restitue le stock sans dérive.
- **Les occurrences futures ne sont pas stockées** : elles se déduisent des plannings (`domain/Planification.kt`). Une ligne `prise` n'existe que quand l'utilisateur agit (prise, sautée, reportée) ou pour une prise ponctuelle (`planningHeureId == null`). Unicité d'une occurrence : `(planningHeureId, datePrevue)`.
- **Heures « murales » locales** (`LocalTime`, `LocalDate`) : « 8h00 » reste 8h00 après changement d'heure ou de fuseau ; les instants (epoch ms) ne servent que pour `horodatage` et `reporteJusqua`.
- Les infos BDPM sont **copiées** dans `medicament` (pas de clé étrangère vers la base BDPM, remplacée à chaque release). Saisie 100 % manuelle possible (`cip13 == null`).
- Alerte de stock faible : `projeterStock` simule le planning jour par jour jusqu'à la rupture, comparée à `seuilJours` ; `seuilUnites` couvre les médicaments sans planning. Doses décimales (0.5) pour les demi-comprimés.
- Les opérations multi-tables passent par `data/Repository.kt` (transactionnelles, idempotentes). Les schémas Room sont exportés dans `android/app/schemas/` et doivent être versionnés : toute modification d'entité exige d'incrémenter `version` et d'écrire une migration.

## Moteur de rappels (`rappels/`, `domain/Evenements.kt`)

- **Une seule alarme système est armée** à la fois (`Moteur.programmer`, requestCode fixe) : celle du prochain événement. Les événements (`Rappel`, `Echeance`) sont calculés par la fonction pure `evenementsEntre`/`prochainEvenement` à partir des plannings et du journal, pas stockés. Au déclenchement, `Moteur.executer` traite tout ce qui est dû depuis la dernière exécution (pref `derniere`), ce qui rattrape aussi un téléphone éteint, puis réarme.
- `Moteur.executer(traiter = false)` = simple recalage de l'alarme après une modification de données (appelé par le ViewModel après chaque action) : il ne notifie rien. `traiter = true` à l'ouverture de l'app, au déclenchement de l'alarme et sur `BOOT_COMPLETED`/`TIME_SET`/`TIMEZONE_CHANGED`/mise à jour/changement d'autorisation d'alarmes exactes.
- Une occurrence sans réponse **2 h** après son heure passe en « manquée » (`DELAI_MANQUE_MIN`) ; « Reporter » = 30 min (`REPORT_MIN`).
- Alarme exacte (`setExactAndAllowWhileIdle`) si l'utilisateur a accordé `SCHEDULE_EXACT_ALARM` (refusée par défaut depuis Android 14 et **remise à zéro à chaque réinstallation**) ; sinon repli sur `setAndAllowWhileIdle`, INEXACT (retards possibles). Piège vérifié : `setAlarmClock` exige aussi cette autorisation (SecurityException sinon) — ce n'est PAS un repli sans permission. Un bandeau de l'écran « Aujourd'hui » propose l'autorisation. `USE_EXACT_ALARM` (accordée d'office) serait possible pour une build F-Droid, mais la politique du Play Store la réserve aux apps de réveil/agenda.
- Alerte de stock faible : une notification par médicament et par jour (pref `alertes`), évaluée à chaque exécution du moteur.
- Les boutons de la notification (Pris / Reporter / Sauter) passent par `ActionReceiver`, qui appelle le même `Repository` que l'UI.
