# T'as pensé à ?

Application Android de **rappels récurrents avec confirmation** et de **suivi de stock**.

Elle demande si c'est fait (« fait / reporter / sauter »), surveille ce qui s'épuise et garde un historique en calendrier. Elle sert à tout ce qui se prend, se change ou se renouvelle régulièrement : médicaments, lentilles de contact, croquettes, filtres, vitamines, vermifuge des animaux…

- **Hors ligne et sans compte** : aucune donnée ne quitte le téléphone (pas d'accès à internet, pas d'analytique).
- **Gratuite, sans publicité, libre** : licence GNU GPL version 3 ou ultérieure (voir `LICENSE`).
- Interface en français.

> État : projet en cours de reconstruction. Pour l'instant, seul le squelette de l'interface existe (onglets Aujourd'hui, Historique, Rappels et Plus, avec Apparence et À propos), sans données.

## Prérequis

- **JDK 21** (le code est compilé pour Java 17).
- **SDK Android**, avec la plateforme `android-36` et les build-tools correspondants. Le chemin du SDK se déclare dans `android/local.properties` (fichier ignoré par git, à créer sur chaque machine) :
  ```
  sdk.dir=/chemin/vers/Android/Sdk
  ```
- `adb` (platform-tools) pour installer sur un appareil.

Rien d'autre à installer : Gradle (8.13) est téléchargé par le wrapper `gradlew`, ainsi que le plugin Android (8.13.2) et Kotlin (2.2.10). Android Studio n'est pas nécessaire.

## Construire

Toutes les commandes se lancent depuis le dossier `android/` :

| Commande | Effet |
|---|---|
| `./gradlew assembleDebug` | construit l'APK de debug : `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew installDebug` | construit et installe sur l'appareil connecté |
| `./gradlew testDebugUnitTest` | lance les tests unitaires (JVM, sans téléphone) |
| `./gradlew clean` | supprime les dossiers `build/` |
| `./gradlew tasks` | liste les tâches disponibles |
| `./gradlew --stop` | arrête le démon Gradle |

Le premier build télécharge les dépendances et prend plus de temps ; les suivants sont incrémentaux.

### Choisir l'appareil

Avec plusieurs appareils connectés, `installDebug` installe sur **tous**. Pour en viser un seul, donner son numéro de série (voir `adb devices`) :

```
ANDROID_SERIAL=<série> ./gradlew installDebug
```

`installDebug` n'ouvre pas l'application. Pour la lancer :

```
adb -s <série> shell monkey -p net.grandou.rappels -c android.intent.category.LAUNCHER 1
```

### Version de release

La signature et la réduction du code (R8) ne sont pas encore configurées : `./gradlew assembleRelease` produit pour l'instant un APK non signé et non optimisé. La clé de signature appartient à l'utilisateur et ne doit jamais être commitée (`keystore.properties`, `*.jks` et `*.keystore` sont ignorés par git).

## Versions figées

Les plateformes Android 34 et 36 sont celles installées ; AGP 8.13.2 recommande `compileSdk` 36. Le BOM Compose est donc épinglé à `2025.12.00` : les versions plus récentes exigent la plateforme 37 et un AGP plus récent. Ne pas le monter sans installer cette plateforme.

## Identité

- Nom affiché : **T'as pensé à ?** (`@string/app_name`)
- Identifiant du paquet : **`net.grandou.rappels`**
