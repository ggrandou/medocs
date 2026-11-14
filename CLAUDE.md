# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## État du projet

Dépôt vide au démarrage (aucun code, aucune commande de build/test définie). Ce fichier consigne les décisions de conception déjà prises ; à compléter avec les vraies commandes dès que le code existe.

## Identité de l'application

- Nom affiché : **Mes Médocs** (`@string/app_name`). Identifiant du paquet : **`net.grandou.medocs`** (organisation grandou.net) : ne plus le changer une fois publié, Android le traiterait comme une autre application (données non transférées, hors sauvegarde chiffrée). Le prototype initial utilisait `org.medocs.rappel` : un appareil de test peut encore l'avoir installé en parallèle (rappels en double), le désinstaller.
- Icône adaptative (pilule bicolore sur fond `#1F6F8B`, avec variante monochrome) dans `res/mipmap-anydpi-v26/` et `res/drawable/ic_launcher_*.xml` ; icône de notification `ic_notification.xml`.

## Objectif

Application Android de **rappel de prise de médicaments**, d'abord personnelle, puis diffusée publiquement : gratuite, sans publicité, open source, **licence GPLv3 ou ultérieure** (fichier `LICENSE`, texte officiel de la FSF). Interface et documentation en français.

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

## Sauvegarde et aide à la fiabilité

- **Sauvegarde = un fichier chiffré choisi par l'utilisateur** (aucun réseau). `data/Sauvegarde.kt` sérialise toutes les tables en JSON (identifiants d'origine conservés, cohérence des clés étrangères vérifiée AVANT toute écriture) ; `data/Chiffrement.kt` le chiffre : PBKDF2-HMAC-SHA256 (210 000 itérations) → AES-256-GCM, format `MEDOCS1 | sel | IV | chiffré`. Un mauvais mot de passe et un fichier modifié sont indistinguables (`MotDePasseIncorrect`). Le mot de passe n'est jamais enregistré. L'import REMPLACE tout, dans une transaction (`Repository.remplacerPar`). Fichiers via le sélecteur système (`CreateDocument` / `OpenDocument`), sans permission de stockage. Toute nouvelle donnée à sauvegarder (ex. photos) doit entrer dans `Donnees` et dans `VERSION` du format.
- Les tests JVM de `Sauvegarde` utilisent la dépendance `org.json:json` : celle du SDK est un stub non exécutable en test local.
- **Aide « rappels fiables »** (`ui/AideRappels.kt`) : bandeau si l'optimisation de la batterie est active, avec « Ne plus afficher » ; l'application ne peut pas se mettre elle-même en liste blanche (c'est volontaire : `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` est restreinte par le Play Store).
- **minSdk 26** : tout appel d'API récente doit être gardé ou testé sur Android 9 (`canScheduleExactAlarms` et `checkSelfPermission(POST_NOTIFICATIONS)` ont déjà posé problème). Pour les notifications, utiliser `NotificationManager.areNotificationsEnabled()`.
- Réglage de test du délai « manquée » : pref `delai_manque_min` de `moteur.xml` (défaut 120), modifiable via `adb shell run-as` après `force-stop`.

## Photos des boîtes

- La photo est choisie par l'utilisateur (caméra via `TakePicture` + `FileProvider` sur `cache/captures/`, ou galerie via `PickVisualMedia`) : **aucune permission caméra ni stockage**. `photos/Photos.kt` la réduit (1024 px max), la redresse selon l'EXIF et la ré-encode en JPEG : le fichier final n'a **aucune métadonnée, GPS compris** (testé). Stockage : `filesDir/photos/<uuid>.jpg`, nom référencé par `Medicament.photoPath`.
- Les fichiers non référencés sont supprimés (`Photos.nettoyer`) au démarrage du ViewModel et après chaque ajout/modification/suppression/import : une photo abandonnée avant « Enregistrer » ne s'accumule pas.
- **Sauvegarde** : les photos sont dans `Donnees.photos` (nom → Base64), champ **facultatif** du JSON : les anciennes sauvegardes restent lisibles, donc `VERSION` n'a pas changé. Les noms viennent d'un fichier non fiable : `nomPhotoValide` (`[A-Za-z0-9_-]{1,64}.jpg`) est vérifié à la lecture ET à l'écriture (`Photos.fichier`), et le contenu est vérifié comme image avant d'être écrit, AVANT toute écriture en base.
- Piège vérifié : `BitmapFactory.decodeStream(..., inJustDecodeBounds = true)` renvoie toujours `null` ; ne jamais le chaîner à un `?: return null`.

## Accessibilité

- **Grandes polices** : tout doit rester utilisable à 200 % (testé sur le S8 avec `settings put system font_scale 2.0`, à remettre à 1.0 après). Règle : jamais de `Row` avec plusieurs boutons ou textes côte à côte, utiliser `FlowRow` (une `Row` comprime le dernier élément jusqu'à le faire retourner à la ligne lettre par lettre : bug réel trouvé sur le bandeau d'économie d'énergie) ; les champs de formulaire en pleine largeur, jamais `weight` dans une rangée de boutons.
- **TalkBack** : cartes regroupées en une phrase (`semantics(mergeDescendants = true)`) pour l'info, boutons gardés séparés et décrits en entier (« Pris : <médicament>, <heure> »), titres en `heading()`, interrupteurs en `toggleable(role = Role.Switch)` sur toute la ligne, images décoratives sans description (le nom est lu à côté), icônes emoji de la barre de navigation en `clearAndSetSemantics {}`, erreurs en `liveRegion`. Ordre de l'écran « Aujourd'hui » : bandeau **critique** (notifications coupées), manquées, prises du jour, puis astuces.
- **Tests** (`androidTest/.../ui/AccessibiliteTest.kt`) : rendent les écrans à 100 % et 200 %, vérifient zones tactiles ≥ 48 dp et libellés sur l'arbre sémantique, et écrivent des captures dans le dossier externe de l'app (relecture visuelle). Un **test témoin** (composant volontairement fautif) DOIT échouer, sinon les autres ne prouvent rien. Piège vérifié : `enableAccessibilityChecks`/`tryPerformAccessibilityChecks` de Compose et le validateur ATF sur la fenêtre **ne détectaient rien** (composant de 16 dp sans libellé accepté) : ne pas s'y fier. Lancer avec `am instrument` (pas `connectedAndroidTest`, qui désinstalle l'app et emporte les captures) ; l'écran du S8 doit être allumé, sinon « No compose hierarchies found ».

## Scan du code-barres des boîtes

- Les boîtes françaises portent un **EAN-13 dont le numéro EST le CIP13**, et/ou un **Datamatrix GS1** (`01`+GTIN-14 = « 0 » + CIP13, `17` péremption AAMMJJ, `10` lot, `21` série, séparés par GS `\u001d`). `domain/CodeBoite.kt` les interprète (clé de contrôle GS1 vérifiée, identifiant `]d2` toléré, champs inconnus ignorés) ; `Bdpm.parCip13` retrouve la présentation exacte, donc le formulaire s'ouvre prérempli (nom, molécule, unité, quantité par boîte). Le lot et la péremption sont lus mais pas encore utilisés (piste : alerte de péremption).
- Lecture : **ZXing core** (pur Java, Apache 2.0) + **CameraX**, aucun service Google (ML Kit est propriétaire, refusé par F-Droid). Permission `CAMERA` demandée seulement au premier appui sur « Scanner », après une explication ; la caméra est rendue en quittant l'écran (vérifié avec `dumpsys media.camera`). Chemin de secours accessible : « Saisir le numéro à la place » et le champ de recherche accepte un CIP7/CIP13.
- **Pièges vérifiés** : (1) `PlanarYUVLuminanceSource` ne sait PAS pivoter, donc `TRY_HARDER` ne lit pas un 1D vertical : `scan/DecodeurCode.kt` redresse lui-même l'image (`imageInfo.rotationDegrees`) puis réessaie à 90° — sans cela un code tenu à l'horizontale en portrait n'était jamais lu ; (2) dès que `CAMERA` est déclarée, Android l'exige AUSSI pour `ACTION_IMAGE_CAPTURE` (SecurityException sinon) : `ChampPhoto` la demande avant la capture ; (3) CameraX expose `ListenableFuture` : Kotlin refuse de compiler sans l'interface, qui n'existe que dans Guava (`listenablefuture:1.0` est écrasé par une version vide) ; `ProcessCameraProvider.awaitInstance` (import `androidx.camera.lifecycle.awaitInstance`) évite d'en manipuler.
- **Tests** : `DecodeurCodeTest` (JVM) génère de vrais EAN-13 et Datamatrix avec ZXing et les relit (4 orientations, petit, inversé, rembourrage de ligne, bruit, rotation du capteur) ; `BdpmCodesTest` interroge la vraie base ; `AjoutScanTest` teste « code lu → formulaire » avec un faux scanner injecté (`ecranScan`). Non testable sans boîte réelle : la lecture caméra de bout en bout (seuil d'environ 5 px par module).
