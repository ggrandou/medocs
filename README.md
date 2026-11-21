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

## Installer les dépendances sans Android Studio

**En une commande** : `scripts/install-deps.sh` fait tout ce qui suit (JDK, outils en ligne de commande, licences, composants du SDK, `android/local.properties`). Il peut être relancé sans risque : ce qui est déjà installé n'est pas touché. `ANDROID_HOME` change le dossier du SDK (défaut : `~/Android/Sdk`), `SKIP_JDK=1` saute le JDK. Le détail manuel :

**JDK 21** : par le gestionnaire de paquets (sous Debian ou Ubuntu : `sudo apt install openjdk-21-jdk`).

**SDK Android** : il s'installe avec les outils en ligne de commande (`cmdline-tools`), qui fournissent `sdkmanager`.

1. Télécharger « Command line tools only » (`commandlinetools-linux-<n>_latest.zip`) sur <https://developer.android.com/studio#command-line-tools-only>.
2. L'extraire pour obtenir `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager` (le dossier `latest/` est obligatoire) :
   ```
   mkdir -p ~/Android/Sdk/cmdline-tools
   unzip commandlinetools-linux-*_latest.zip -d ~/Android/Sdk/cmdline-tools
   mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
   ```
3. Installer les composants et accepter les licences :
   ```
   export PATH=$PATH:~/Android/Sdk/cmdline-tools/latest/bin
   sdkmanager --licenses
   sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
   ```
4. Déclarer le chemin dans `android/local.properties` (voir ci-dessus).

Autres commandes utiles : `sdkmanager --list` (installé et disponible), `sdkmanager --update` (mise à jour). Si une plateforme ou des build-tools manquent, Gradle les télécharge de lui-même une fois les licences acceptées.

**Le reste** (Gradle, plugin Android, Kotlin, Compose et leurs bibliothèques) est téléchargé automatiquement au premier build et mis en cache dans `~/.gradle`.

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
