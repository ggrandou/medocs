# Publier sur Google Play : marche à suivre

Dossier de préparation. Les textes sont dans `fiche-fr.md` et `../docs/confidentialite.md` (publiée par GitHub Pages).

## 1. Compte développeur
- Compte Google Play Console (frais d'inscription uniques, vérification d'identité obligatoire).
- **Compte « personnel » créé récemment** : Google exige un **test fermé avec au moins 12 testeurs inscrits pendant 14 jours consécutifs** avant d'autoriser la production. Un compte « organisation » (numéro D-U-N-S) n'y est pas soumis. À vérifier dans la Play Console, ces règles évoluent.

## 2. Clé d'envoi (upload key) : à créer par toi
```sh
keytool -genkeypair -v -keystore ~/mes-medocs-upload.jks -alias medocs-upload -keyalg RSA -keysize 4096 -validity 10000
```
Crée ensuite `android/keystore.properties` (ignoré par git) :
```
storeFile=/chemin/absolu/mes-medocs-upload.jks
storePassword=...
keyAlias=medocs-upload
keyPassword=...
```
Sauvegarde le `.jks` et ses mots de passe ailleurs que sur cet ordinateur. Avec **Play App Signing** (à accepter à la création de l'appli), Google garde la clé de signature définitive : si tu perds la clé d'envoi, le support de Google peut la remplacer.

## 3. Construire le bundle
```sh
python3 -I scripts/build_bdpm.py --download      # base des médicaments
cd android && ./gradlew bundleRelease             # app/build/outputs/bundle/release/app-release.aab
```
À chaque nouvelle publication, **augmenter `versionCode`** (entier strictement croissant) dans `android/app/build.gradle.kts`. Conserver `app/build/outputs/mapping/release/mapping.txt` (Play Console : *Déobfuscation*).

## 4. Dans la Play Console
1. Créer l'appli : « Mes Médocs », français, application, gratuite.
2. Renseigner la fiche (`fiche-fr.md`), la politique de confidentialité (URL publique), la section « Sécurité des données », la déclaration « Applications de santé », la déclaration des alarmes exactes, la classification du contenu, le public cible.
3. Importer le `.aab` dans une **piste de test interne** d'abord (jusqu'à 100 testeurs, immédiat), puis **test fermé**, puis production.
4. Regarder le **rapport de pré-lancement** (Google teste l'appli sur des appareils réels) avant de promouvoir.

## 5. Points d'attention propres à cette appli
- Les **alarmes exactes** sont examinées par Google : la justification est prête dans `fiche-fr.md`.
- Ne **jamais** ajouter `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (restreinte par le Play Store) : l'aide intégrée guide vers les réglages du téléphone.
- L'application est classée « santé » : garder l'avertissement « simple aide-mémoire » visible et ne rien ajouter qui ressemble à un conseil médical (interactions, posologie…), sous peine de changer de catégorie réglementaire.

## 6. Visuels (`visuels/`)
- `icone-512.png` (icône 512×512) et `bandeau-1024x500.png` (image de présentation) sont générés depuis `icone.svg` et `bandeau.svg` : `rsvg-convert icone.svg -o icone-512.png`.
- `captures/clair/` et `captures/sombre/` : 4 captures de 1080×2160 (plus `clair/5-notifications.png`) (rapport 2:1, accepté par le Play Store) avec des **données fictives** (`androidTest/.../store/JeuDeDonneesStore.kt`, lancé avec `-e jeu_store 1` : il ne nettoie PAS, donc uniquement sur le S8 vierge). Barre d'état propre via le mode démo de SystemUI (`sysui_demo_allowed`), taille d'écran forcée par `wm size 1080x2160` (à remettre : `wm size reset`).
- Volet de notifications (`5-notifications.png`) : lancer `reseed2`-style avec `-e notifs_extra 1` (quatre rappels de l’appli remplissent le volet, ce qui repousse hors cadre les notifications des autres applications : USB, Google… qui donnent une localisation). Vérifier chaque capture avant de la publier.
