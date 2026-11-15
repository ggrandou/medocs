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
- rappel à l'heure de la prise (actions « J'ai pris / Reporter / Sauter » dans la notification) ;
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
- **Rappel persistant** : `setOngoing(true)` (non balayable avant Android 14, qui laisse balayer) + `deleteIntent` (`ACTION_BALAYEE`, traité par `ActionReceiver`) qui réaffiche le rappel tant que l'occurrence n'a pas de ligne ou est « reportée », et que le planning existe encore. Testé par `NotificationPersistanteTest` (vraie base, appareil de test).
- **Rattrapage** (`Moteur.reafficherEnAttente`, à chaque exécution avec `traiter = true`) : toute occurrence d'aujourd'hui échue depuis moins de 2 h, sans réponse et sans notification affichée, est notifiée (planning créé/modifié après l'heure, notification effacée, alarme ratée). Déjà affichée : rien n'est refait. Vérifié sur le S23 (la notification apparaît dès la mise à jour de l'app).
- Alerte de stock faible : une notification par médicament et par jour (pref `alertes`), évaluée à chaque exécution du moteur.
- Les boutons de la notification (« J'ai pris » / Reporter / Sauter) passent par `ActionReceiver`, qui appelle le même `Repository` que l'UI.

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
- **TalkBack** : cartes regroupées en une phrase (`semantics(mergeDescendants = true)`) pour l'info, boutons gardés séparés et décrits en entier (« J'ai pris : <médicament>, <heure> »), titres en `heading()`, interrupteurs en `toggleable(role = Role.Switch)` sur toute la ligne, images décoratives sans description (le nom est lu à côté), icônes emoji de la barre de navigation en `clearAndSetSemantics {}`, erreurs en `liveRegion`. Ordre de l'écran « Aujourd'hui » : bandeau **critique** (notifications coupées), manquées, prises du jour, puis astuces.
- **Tests** (`androidTest/.../ui/AccessibiliteTest.kt`) : rendent les écrans à 100 % et 200 %, vérifient zones tactiles ≥ 48 dp et libellés sur l'arbre sémantique, et écrivent des captures dans le dossier externe de l'app (relecture visuelle). Un **test témoin** (composant volontairement fautif) DOIT échouer, sinon les autres ne prouvent rien. Piège vérifié : `enableAccessibilityChecks`/`tryPerformAccessibilityChecks` de Compose et le validateur ATF sur la fenêtre **ne détectaient rien** (composant de 16 dp sans libellé accepté) : ne pas s'y fier. Lancer avec `am instrument` (pas `connectedAndroidTest`, qui désinstalle l'app et emporte les captures) ; l'écran du S8 doit être allumé, sinon « No compose hierarchies found ».

## Scan du code-barres des boîtes

- Les boîtes françaises portent un **EAN-13 dont le numéro EST le CIP13**, et/ou un **Datamatrix GS1** (`01`+GTIN-14 = « 0 » + CIP13, `17` péremption AAMMJJ, `10` lot, `21` série, séparés par GS `\u001d`). `domain/CodeBoite.kt` les interprète (clé de contrôle GS1 vérifiée, identifiant `]d2` toléré, champs inconnus ignorés) ; `Bdpm.parCip13` retrouve la présentation exacte, donc le formulaire s'ouvre prérempli (nom, molécule, unité, quantité par boîte). Le lot et la péremption sont lus mais pas encore utilisés (piste : alerte de péremption).
- Lecture : **ZXing core** (pur Java, Apache 2.0) + **CameraX**, aucun service Google (ML Kit est propriétaire, refusé par F-Droid). Permission `CAMERA` demandée seulement au premier appui sur « Scanner », après une explication ; la caméra est rendue en quittant l'écran (vérifié avec `dumpsys media.camera`). Chemin de secours accessible : « Saisir le numéro à la place » et le champ de recherche accepte un CIP7/CIP13.
- **Pièges vérifiés** : (1) `PlanarYUVLuminanceSource` ne sait PAS pivoter, donc `TRY_HARDER` ne lit pas un 1D vertical : `scan/DecodeurCode.kt` redresse lui-même l'image (`imageInfo.rotationDegrees`) puis réessaie à 90° — sans cela un code tenu à l'horizontale en portrait n'était jamais lu ; (2) dès que `CAMERA` est déclarée, Android l'exige AUSSI pour `ACTION_IMAGE_CAPTURE` (SecurityException sinon) : `ChampPhoto` la demande avant la capture ; (3) CameraX expose `ListenableFuture` : Kotlin refuse de compiler sans l'interface, qui n'existe que dans Guava (`listenablefuture:1.0` est écrasé par une version vide) ; `ProcessCameraProvider.awaitInstance` (import `androidx.camera.lifecycle.awaitInstance`) évite d'en manipuler.
- **Tests** : `DecodeurCodeTest` (JVM) génère de vrais EAN-13 et Datamatrix avec ZXing et les relit (4 orientations, petit, inversé, rembourrage de ligne, bruit, rotation du capteur) ; `BdpmCodesTest` interroge la vraie base ; `AjoutScanTest` teste « code lu → formulaire » avec un faux scanner injecté (`ecranScan`). Non testable sans boîte réelle : la lecture caméra de bout en bout (seuil d'environ 5 px par module).

## Build de release

- `cd android && ./gradlew assembleRelease` : R8 + réduction des ressources, APK d'environ **4,6 Mo** (24 Mo en debug). Sortie : `app/build/outputs/apk/release/`, avec `mapping.txt` (dans `app/build/outputs/mapping/release/`) à conserver pour décoder les traces d'erreur.
- **Signature** : lue dans `android/keystore.properties` (ignoré par git : `storeFile=/chemin/cle.jks`, `storePassword`, `keyAlias`, `keyPassword`). Sans ce fichier, l'APK est **non signé** (`app-release-unsigned.apk`), ce que F-Droid attend (il signe avec sa propre clé). La clé se crée par `keytool -genkeypair -v -keystore ~/mes-medocs.jks -alias medocs -keyalg RSA -keysize 4096 -validity 10000` : elle appartient à l'utilisateur, ne jamais la générer pour lui ni la commiter ; la perdre interdit toute mise à jour d'une appli déjà publiée.
- Test local d'une release non signée : `zipalign -p 4`, puis `apksigner sign --ks ~/.android/debug.keystore` (mot de passe `android`) ; elle ne s'installe pas par-dessus une version debug (signatures différentes : désinstaller d'abord). Vérifié sur le S8 : démarrage, recherche BDPM, écran de scan et aperçu caméra sans erreur sous R8. Les lignes `ClassNotFoundException: android.text.Layout$TextInclusionStrategy` d'Android 9 sont un message d'information de Compose (classe protégée par un test de version), pas un défaut.
- Piège vérifié : toute tâche qui lit `build/generated/bdpm-assets` (fusion des assets, **lint**, empaquetage) doit dépendre de `copierBdpm`, sinon `assembleRelease` échoue au lint.

## Carrousel de prise en main (`ui/Onboarding.kt`)

- 5 pages (`HorizontalPager`) : présentation, premiers pas, conseils, **avertissement** (à accepter), **réglages** des autorisations avec leur état en direct (notifications, alarmes exactes — masquées avant Android 12 —, économie d'énergie ; la caméra n'est qu'expliquée, elle se demande à l'usage). L'état se met à jour au retour des réglages grâce au `onResume` de `MainActivity`.
- **Premier lancement** : `avertissement_version` et `onboarding_version` (prefs `ui`) sont lus de façon SYNCHRONE avant `setContent` (sinon l'écran principal clignote avant le carrousel). L'avertissement ne peut pas être contourné : pas de « Passer », « Suivant » remplacé par « J'ai compris et j'accepte » sur sa page, balayage au-delà bloqué. Il remplace l'ancienne boîte de dialogue bloquante ; le texte vit dans `TexteAvertissement`, partagé avec « À propos ».
- **Relancer** : menu « Plus » > « Revoir la prise en main » (`revoir = true` : bouton « Fermer », avertissement déjà accepté non redemandé). Incrémenter `VERSION_ONBOARDING` pour le réafficher à tous après une évolution majeure.
- La permission de notifications n'est plus demandée à froid au lancement : la page des réglages s'en charge (fenêtre système la 1re fois, puis réglages de l'app car Android refuse d'office la 2e demande ; sinon bandeau critique de l'écran « Aujourd'hui »).
- Tests : `OnboardingTest` (parcours, avertissement incontournable même au balayage, états des réglages, mode revoir, accessibilité à 100 % et 200 % avec captures). `VerifAccessibilite.kt` factorise le contrôle des zones tactiles/libellés. Dans un test, un élément en bas d'une page qui défile exige `performScrollTo()`.

## Esthétique : palette, mode sombre, icônes

- **Mode sombre** : suit le réglage de l'appareil (`AppTheme(sombre = isSystemInDarkTheme())`). Le thème de FENÊTRE suit aussi (`res/values/themes.xml` + `values-night/themes.xml`, fond `fond_fenetre` identique au fond de la palette) : sans cela, flash blanc au lancement en mode sombre et sélecteurs d'heure/date (`TimePickerDialog`, `DatePickerDialog`, thème de l'activité) restent clairs. Si le fond de `Palette.kt` change, changer aussi les deux `colors.xml`.
- **Palette** (`ui/Palette.kt`) : bleu-vert et ambre tirés de l'icône. Les rôles `surfaceContainer*` sont définis un à un, sinon Material les dérive de sa palette violette par défaut et les cartes prennent une teinte étrangère. `PaletteTest` vérifie les contrastes WCAG (4,5:1 texte, 3:1 contours) en clair et en sombre, et prouve son propre calcul (noir/blanc = 21, #767676/blanc ≈ 4,54).
- **Choix clair/sombre/système** (onglet Plus > Apparence, pref `theme` de `ui`) : `MainActivity.attachBaseContext` force le mode nuit de la configuration (donc Compose, thème de fenêtre `-night`, sélecteurs et barre d'état suivent) ; changer de mode appelle `recreate()`. Testé par `ModeApparenceTest`.
- `COULEURS_DYNAMIQUES = false` (`ui/Theme.kt`) : identité visuelle identique sur tous les téléphones. Passer à `true` pour reprendre les couleurs du fond d'écran (Material You, Android 12+).
- **Quatre onglets** : Aujourd'hui, Historique (calendrier, voir ci-dessous), Médicaments, Plus (numéros internes 0, 3, 1, 2 : `ONGLET_HISTORIQUE = 3`, les autres n'ont pas changé) (`ui/PlusScreen.kt` : liste groupée en cartes ; chaque entrée ouvre un ÉCRAN COMPLET `SousEcran` (flèche de retour) défini dans `PlusEcrans.kt`, `SauvegardeDialogs.kt` (`SauvegardeScreen`) et `AideRappels.kt` ; seules la confirmation d'import et les messages restent des dialogues). Le menu déroulant « Plus » et les anciennes boîtes de dialogue n'existent plus.
- **Icônes de la barre d'onglets** (`ui/Navigation.kt`) : vectoriels maison `ic_nav_*_actif/_inactif` (pleine pour l'onglet actif, contour sinon), teintés par `Icon` ; `contentDescription = null` car le libellé est lu. Plus d'emoji (ils s'affichaient différemment selon le fabricant et TalkBack les lisait).
- `EsthetiqueTest` rend l'application en clair et en sombre (captures pour relecture). Le contrôle des zones tactiles ignore les éléments coupés par la zone de défilement (leur taille visible est tronquée).
- Le S8 (Android 9) n'a pas de mode sombre système : le rendu sombre se vérifie par `AppTheme(sombre = true)` dans les tests, et le thème de fenêtre `-night` sur un téléphone récent (S23).

## Icônes de type de médicament (sans photo)

- `ui/VisuelMedicament.kt` : sa photo si elle existe, sinon une **tuile à l'icône de son type** (comprimé, gélule, liquide, gouttes, injection, sachet, tube, suppositoire, spray, boîte générique). La tuile est TOUJOURS dessinée et la photo la recouvre une fois chargée : la mise en page ne bouge jamais (avant, l'espace était vide sans photo). Décorative : le nom est lu à côté.
- `domain/TypeMedicament.kt` choisit le type d'après la **forme** de la base (« comprimé pelliculé », « solution buvable en gouttes »…), puis l'**unité de prise**, puis le **nom** (saisie manuelle). Règles ORDONNÉES : une poudre injectable est une injection avant d'être un sachet ; « gélule » contient « gel » (d'où `\bgel\b`). Couvre plus de 98 % de la base (207 spécialités sur 11 897 restent « boîte générique » : dispositifs, vernis, trousses radiopharmaceutiques…). Pour ajouter un type : un drawable `ic_med_*.xml` (48 dp, blanc, `fillAlpha` pour les parties secondaires), une valeur de l'enum, une règle, une couleur dans `couleursDe`.
- Les couleurs viennent des paires `*Container` / `on*Container` du thème (contrastes vérifiés par `PaletteTest`), donc lisibles en clair et en sombre. `IconesMedicamentsTest` dessine la planche des dix icônes dans les deux modes.

## Boutons d'action d'une prise

- Les trois actions (« J'ai pris », Reporter, Sauter) ont le **même style** (tonal, icône + verbe). Ne jamais mettre un bouton plein sur « Pris » : c'est un participe, donc un bouton plein se lit comme un ÉTAT déjà validé. L'état résultant (pris, sauté, reporté) s'affiche à part : icône dans la couleur principale + texte (« Pris à 16:02 »), carte grisée.
- **Couleur par statut** (`CarteOccurrence`) : pris = vert doux (#CDEBD3 clair, #1E4D2B sombre, défini dans `CarteOccurrence`, hors palette), en retard = orange, reporté = jaune, sautée ET manquée = rouge, à venir = `surfaceContainerLow` (couleurs définies dans `couleursStatut`). Toujours doublée du texte du statut (jamais la couleur seule). Les tuiles d'icône ont un liseré, sinon elles disparaissent sur une carte de même teinte.
- **Prise en main allégée** : une phrase par point, un dessin par page (`ui/Illustrations.kt`, Canvas aux couleurs du thème, muets pour TalkBack). La page d'avertissement du carrousel affiche une version COURTE (`TexteAvertissement(resume = true)`) ; le texte complet reste dans Plus > À propos.
- **États vides illustrés** : `IllustrationPage` (calendrier = rien de prévu, coche + confettis = journée terminée, flacon = aucun médicament), testés dans `AccessibiliteTest`.

## Onglet Historique (calendrier mensuel)

- `domain/Calendrier.kt` (pur, testé par `CalendrierTest`) : `statutsDuMois` donne un `StatutJour` par jour. **Passé : lu UNIQUEMENT dans le journal des prises** (vert = tout pris, rouge = rien de pris, orange = entre les deux ; sautée, manquée ou reportée sans suite comptent comme non prise ; point = aucune ligne ce jour-là). **Aujourd'hui** : calculé sur les plannings, même règle sur les prises échues ; tant qu'il en reste à venir et que tout l'échu est pris, la journée est « en cours » et se colore comme un jour futur. **Futur** : gris = prises couvertes par le stock, blanc = au-delà du stock (`joursSansStock` : simulation par médicament, TOUS les jours en manque, pas seulement le premier comme `projeterStock`).
- **Le passé vit dans le journal, pas dans les plannings** : chaque occurrence passée a sa ligne `prise` (prise, sautée, ou MANQUÉE écrite par le moteur 2 h après l'heure, y compris en rattrapage d'un téléphone éteint) avec sa date, son heure et sa dose. Donc modifier ou supprimer un planning ne change rien à l'historique (`ModificationPlanningTest`) : `Repository.enregistrerPlanning` modifie sur place et garde l'identifiant des heures inchangées (une prise déjà notée aujourd'hui reste liée ; une heure retirée détache ses prises, `planningHeureId = null`, qui restent comptées par `datePrevue`). **Garde-fou** : `AppViewModel.figerLePasse()` lance le moteur (`Moteur.executer`) AVANT toute modification ou suppression de planning, pour que les occurrences échues aient déjà leur ligne. Seule exception de lecture : `occurrencesSansLigneRecentes` (occurrence d'un jour passé de moins de 2 h sans ligne, ex. prise de 23 h 30 regardée à minuit, déduite du planning). Conséquence assumée : un jour où l'application ne suivait rien (planning créé avec une date de début passée) est un point, pas un jour « non pris ». Seule la suppression du médicament efface son historique (cascade).
- Toucher un jour (point compris) ouvre `AujourdhuiScreen` avec `quand = PASSE | FUTUR | AUJOURDHUI` et `onRetour` : mêmes cartes et mêmes boutons. **Passé** : cartes tirées des lignes du journal (`construireEtatPasse`), J'ai pris / Sauter / Annuler, pas de Reporter (le rappel serait reprogrammé dans l'heure), « Non pris » au lieu de « En retard »/« Manquée ». Corriger une ligne passée = `Repository.corrigerPrise` (change son statut ET le mouvement de stock, sans jamais supprimer la ligne) ; « Annuler » d'une ligne passée la remet en MANQUÉE (la supprimer effacerait l'occurrence de l'historique). La décision est horodatée à l'HEURE PRÉVUE du jour corrigé (`AppViewModel.instantDe`). **Futur** : lecture seule. Pas de prise hors planning rétroactive pour l'instant.
- `AppViewModel.mois(YearMonth)` et `jour(LocalDate)` ; le mois affiché et le jour ouvert vivent dans `MainActivity` (`rememberSaveable`), sinon ils se perdent à l'ouverture d'un jour. Pastilles : cases de 48 dp de haut (largeur = 1/7 de l'écran, marges latérales de 8 dp pour tenir ≥ 48 dp à 360 dp), couleurs reprises de `couleursStatut`, description TalkBack « 3 novembre 2026, tout pris », légende sous la grille.
- Tests : `CalendrierTest` (androidTest : navigation, TalkBack, écrans de jour, capture sombre), `CalendrierViewModelTest` (vrai ViewModel + vraie base de l'appareil, nettoie ses données : à lancer sur un appareil de test), cas d'accessibilité dans `AccessibiliteTest`.
