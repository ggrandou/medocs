# Publier sur Google Play : marche à suivre

Dossier de préparation. Les textes sont dans `fiche-fr.md` et `confidentialite-fr.md`.

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
