# AGENTS.md

What to know about the app before touching it. The app UI, the end-user docs and the conversations with the maintainer are in **French** (with proper accents); commits and this file are in English. French UI strings are quoted as-is.

## The app in short

**T'as pensé à ?** ("Did you remember?"): an Android app for recurring reminders with confirmation ("done / postpone / skip"), stock tracking with a running-out alert, and a calendar history. First use case: medication. Other cited uses: contact lenses, pet food, filters, vitamins, pet dewormer.

- Promise: reminders that ask whether it's done, and that watch what is running out.
- Offline, no account, no analytics: no data leaves the phone.
- Free, no ads, **GPLv3 or later**.
- Package id: **`net.grandou.rappels`**. It becomes final at first publication: Android would treat another id as a different app, with no data transfer. Never change it afterwards.

### Why this name and positioning

The previous version ("Mes Médocs", `net.grandou.medocs`) was rejected by Google Play because it was declared in the "Medical" category (reserved for organization accounts; the developer account is individual and can't be converted). The function did not change; only the presentation became generic. Chosen Play category: **Tools**.

**Name**: "T'as pensé à ?" (`@string/app_name`): memorable, warm, it describes the service (it is the question the reminder asks) and contains nothing medical. Play title (30 characters max, sober punctuation): **"T'as pensé à ? Rappels"**, the word "Rappels" providing the keyword. Rejected: "J'ai pensé à", "Pas d'oubli", "Pense-bête", "Nœud au mouchoir", "Rappel & Stock". Known reservation: the informal "tu" suits an older audience less, yet they are a natural target; the descriptions compensate by staying neutral.

**Target and pitch**: people who take medication and want a reliable reminder with confirmation and stock tracking. What sets the app apart: no data leaves the phone, persistent reminder, history calendar, encrypted backup chosen by the user. Medication is named explicitly in the descriptions (that is how people will find the app), without being featured in the title.

**Honesty rule**: the app keeps a search in the public drug database (BDPM), a box scanner and a medical warning. The listing and the texts must not hide this: a mismatch between the listing and the content falls under Play's "misleading metadata" policy (risk of account suspension, far worse than a rejection).

### Out of scope, do not add

Medical advice, analysis of the type of drug, interactions, dosage or posology: this would push the app into medical-device qualification. A warning must remain displayed in the app (onboarding and "À propos"); it is temporarily removed from "À propos" and will come back.

## Current state

Project **being rebuilt** on `main`. Only the UI skeleton exists, with no data at all:

- 4 bottom tabs: **Aujourd'hui**, **Historique**, **Rappels**, **Plus** ("Rappels" is the chosen name for the former "Médicaments" tab: the list of what is tracked).
- **Rappels** works end to end and stays generic (no assumption on what is tracked): list (items running low first, in amber), item detail (next reminder, stock card with add / correct, rhythms with pause switch), item form (name, free unit, stock tracking, lot size, alert thresholds), schedule form (4 rhythm kinds, times with quantities, period, live summary). Navigation is a text route in `ui/Root.kt`; data comes from `AppViewModel`.
- **Aujourd'hui** works: today's planned occurrences in sections (À faire, Manqués, À venir, Terminé) with Fait / Reporter (30 min) / Ignorer / Annuler, a progress summary and an amber card for items running low. An unanswered occurrence stays "En retard" (orange) all day and only becomes "Manqué" the next day; the alarm engine writes the `MISSED` rows at day change. A floating "+ Prise ponctuelle" button records a one-off ("extra") intake (item, quantity, time; `Repository.recordOneOff`, rows with no schedule time and no scheduled date): listed under "Hors planning", cancellable, consuming stock like a dose.
- **Historique** works: month calendar in a frame (swipe or arrows to change month). Each day is a ring with one slice per planned reminder, colored by its state (status tones; neutral for upcoming), then one blue slice per one-off intake. Dashed ring = upcoming day the stock will not cover (`lacksStock`, from `ItemLine.coverage`). Today is circled, the selected day is a filled disc inside the ring. Below: the day's list and a "Modifier ce jour" screen with the same cards and answers as "Aujourd'hui" (shared in `ui/EntryCards.kt`; "Reporter" only for today) plus adding a one-off on a past day. `buildDay` (pure, in `ui/TodayModel`) gives the state of any date: on a past day an unanswered occurrence counts as missed; the history is derived from the current schedules, so editing a rhythm changes the past planning.
- **Alarm engine** (`reminders/`, pure part in `engine/AlarmPlanner`): `ReminderEngine.sync` is idempotent and driven by the database state, not by events. It (0) deletes the `MISSED` rows of today and later (written while the clock was set forward, then back), (1) writes `MISSED` for past occurrences left unanswered (window: since the last run, 31 days max), (2) posts or removes the notification of every occurrence waiting for an answer, (3) raises one low-stock alert per item per day, (4) arms the single alarm (next occurrence, end of a postponement, or midnight). Called by the alarm, by `ReminderReceiver` (boot, `TIME_SET`, `TIMEZONE_CHANGED`, app update, exact-alarm permission change), after every data change (`AppViewModel.write`, restore) and in `MainActivity.onResume`. Notification buttons Fait / Reporter / Ignorer go through `NotificationActionReceiver`, same `Repository` as the screens. **Persistent reminders** (setting in "Plus", on by default, `Settings.persistentReminders`): ongoing notification put back by `deleteIntent` + sync if swiped away; off: ordinary notification, shown once (`notified` set in the `engine` prefs) and dismissed for good when swiped. Validated on the Galaxy S8 (Android 9, API 28): reboot (alarm re-armed and notifications reposted, but only about 2 minutes after boot, `BOOT_COMPLETED` being late on this slow phone), and a real alarm firing in forced deep Doze with the process dead and the screen off (fired on time, ongoing notification with 3 actions). Observed, unexplained: in Doze, the alarm armed right after one that just fired was pushed from midnight to 00:03:13 (back to 00:00:00 outside Doze). Also validated on the S8 (clock and time zone set from adb with `service call alarm`): time zone change (Paris, London, Tokyo: alarm re-armed at the same wall-clock time), clock jumps (alarm fires at 08:30 after a jump to 08:29:30; jump to the next day marks the day's occurrences `MISSED`, though the stale notification took about a minute to disappear) and battery saver (`settings put global low_power 1`: alarm fires on time). Not yet validated: vendor battery management (Samsung "deep sleeping apps", a manual setting), and everything on the Android 17 phone.
- Pure, JVM-tested logic: `engine/RecurrenceEngine` (occurrences, next occurrence), `engine/StockCalculator` (coverage, stock level), `ui/TodayModel` (state of today's occurrences), `ui/Format` (French display).
- **Plus**: Sauvegarder and Restaurer (encrypted file chosen by the user; restore previews the content, asks confirmation, and also reads v1 "Mes Médocs" files, see `docs/schema.md`; code in `data/Backup*.kt`, `ui/Backup*.kt`), Apparence (system / light / dark, stored in `SharedPreferences`), Autorisations and À propos (placeholder text).
- Adaptive icon, monochrome variant (Android 13+ themed icons), notification icon.
- Graphic charter and dark theme, with a contrast test.

Everything else (BDPM, scan, photo capture, onboarding) remains to be done. The **complete v1** is on the `v1_medocs` branch: use it as an example, without copying everything verbatim (`git show v1_medocs:<path>`). Its `CLAUDE.md` details its decisions.

## Repository layout

```
android/                  Gradle project (app module), gradlew wrapper
  app/src/main/kotlin/net/grandou/rappels/   code (MainActivity, Settings, ui/)
  app/src/main/res/                          icons, themes, strings
  app/src/test/                              JVM tests (PaletteTest)
docs/schema.md            user database schema (tables, recurrence types); keep it in sync with the Room entities
scripts/install-deps.sh   installs JDK 21 and the Android SDK without Android Studio
README.md                 description and build commands
```

Local files never committed: `android/local.properties` (SDK path), `android/keystore.properties`, `*.jks`, `*.keystore`, `build/` directories.

## Build

Details in `README.md`. The essentials, from `android/`:

- `./gradlew assembleDebug`: debug APK; `./gradlew testDebugUnitTest`: tests; `./gradlew clean`.
- **`installDebug` installs on every connected device.** To target one: `ANDROID_SERIAL=<serial> ./gradlew installDebug`. It does not launch the app.
- Pinned versions: Gradle 8.13, AGP 8.13.2, Kotlin 2.2.10, JDK 21 (code compiled for Java 17), `compileSdk` and `targetSdk` 36, `minSdk` 26. The Compose BOM is pinned to `2025.12.00`: newer versions require platform 37 (not installed) and a newer AGP.
- `./gradlew assembleRelease` currently produces an unsigned, unoptimized APK (signing and R8 not configured yet). The signing key belongs to the user: never generate it, never commit it.

## Graphic charter

Single source of colors: the icon, i.e. five colors defined in `ui/Palette.kt` (`IconColors`). Every UI color is one of these five or a derived shade (darker or lighter, same family) when contrast requires it; no other brand hue.

| Name | Value | In the icon |
|---|---|---|
| Blue | `#1F6F8B` | background |
| Amber | `#FFB300` | box text, badge ring |
| White | `#FFFFFF` | front face, bubble |
| Top | `#D9E8EE` | top of the box |
| Side | `#B9D2DC` | side of the box |

### Roles

| Role | Light | Dark | Use |
|---|---|---|---|
| Primary | Blue | `#7FC3DD` | buttons, active tab, section titles |
| Primary container | Top | Blue | icon badges, tab indicator |
| Secondary container | Side | `#2F4753` | emphasized neutral elements |
| Accent (tertiary) | `#825500` (dark amber) | Amber | stock alerts, things to look at |
| Accent container | Amber | `#633F00` | alert badge, "to do" badge |
| Background, surface | `#FBFCFF` | `#141D21` | backgrounds |
| Error | `#BA1A1A` | `#FFB4AB` | errors, deletion |

Text on Blue is white (5.67:1); on Top, Side or the light secondary container, `#0C2F3D` (about 11:1); on Amber, `#1A1C1E` (9.5:1). The `surfaceContainer*` roles are defined one by one in `Palette` (otherwise Material derives them from its purple default).

### Dark mode

Surfaces are the icon blue, heavily darkened (same hue, about 200°), never a neutral grey nor pure black; accents are a lightened blue and the icon's amber as is. Same hierarchy as light: blue = structure, amber = attention.

- Surfaces: background `#141D21`, dim `#0F171B`, bright `#323C40`; containers from lowest to highest `#0E1519`, `#192226` (cards), `#1D272B`, `#283236`, `#333D41`; text `#E1E6E9`.
- Surface variant `#3A4850` (text `#BFC8CE`), outline `#899298`, dividers `#3A4850`.
- Primary `#7FC3DD` (text `#0C2F3D`); primary container Blue (white text); secondary `#B5C9D7`; secondary container `#2F4753` (Side text); accent Amber (text `#1A1C1E`); accent container `#633F00` (text `#FFDDB3`).
- Error `#FFB4AB` (text `#690005`), error container `#93000A` (text `#FFDAD6`); inverse surface `#E1E6E9` (text `#2B3338`).
- Elevation is read through surface lightness, not shadows. The window background in `values-night/colors.xml` equals the palette background (no light flash at launch).

### Status tones (documented exception)

Cards carry a semantic status tone, outside the icon's five colors (`Palette.statusLight` / `statusDark`, `ui/StatusStyle.kt`). They are the only non-icon hues allowed, they never serve as brand colors, and the status is always written and paired with an icon.

| Tone | Light (bg / text) | Dark (bg / text) | Use |
|---|---|---|---|
| done (green) | `#CDEBD3` / `#00210C` | `#1E4D2B` / `#CDEBD3` | "Aujourd'hui" card: done |
| snoozed (yellow) | `#FFF3A0` / `#221B00` | `#5E4B00` / `#FFF3A0` | postponed |
| late (orange) | `#FFCF99` / `#2E1500` | `#703B00` / `#FFE0C2` | waiting for an answer, time passed |
| danger (red) | `#FFB8B0` / `#410002` | `#93000A` / `#FFDAD6` | skipped, missed; "Rappels" card out of stock |

"Rappels" cards by stock: OK = neutral (`surfaceContainerLow`), low = amber (accent container), out = red (danger). Contrast of every tone is at least 7:1, checked by `PaletteTest`.

### Rules

- **Amber is never a text color on a light background** (1.79:1 on white): use it as a fill with dark text, or as text on a dark background. Forbidden as text: Amber on White (1.79), Amber on Blue (3.16), Blue on Side (3.59).
- Amber flags what needs attention (low stock); blue structures the interface. Do not swap them. Out of stock is red (status tone `danger`).
- No dynamic colors (Material You): the identity follows the icon on every phone.
- Information is never conveyed by color alone: always paired with an icon or text.
- WCAG AA contrast (4.5:1) of both modes is checked by `PaletteTest`: rerun it after any change to `Palette.kt`.
- Typography: system font and Material 3 scale, no bundled font. Circular 40 dp icon badges, 48 dp minimum touch targets, sizes in `sp` (respect the user's font size).

### Icon

A white **elongated** box with volume, two amber text lines, and in the foreground, straddling the bottom right, a check mark in a white bubble ringed with amber. 108 × 108 viewport, background `#1F6F8B`:

| Element | Geometry and color |
|---|---|
| Front face | rectangle x 21–71, y 34–66, white |
| Top | parallelogram (21,34) (27,28) (77,28) (71,34), Top |
| Side | parallelogram (71,34) (77,28) (77,60) (71,66), Side |
| Text | title x 27, y 40, 26 × 6; line x 27, y 51, 22 × 3; amber. Two lines only: a third one was removed |
| Ring, bubble | circles centered (70,66), radii 17 (amber) and 13.5 (white) |
| Check | blue polygon centered on the badge |

The whole drawing is **scaled to 85 %** around the center to fit inside Android's round or squircle mask (farthest point from the center: 38.6 before scaling, 32.8 after, for a safe zone of radius 33).

- **Monochrome** (Android 13+): a single color, top at 65 % opacity and side at 45 %, text cut out of the front face, **transparent gap of radius 17** around the badge (a `clip-path` whose hole is drawn in the opposite winding), check cut out of the disc.
- **Notification** (24 dp): same drawing cropped to the 21–87 zone, not scaled. The two text lines tell the box apart from a plain rectangle: keep them.
- Files: `ic_launcher_foreground`, `ic_launcher_monochrome`, `ic_notification`.
- Rejected: v1's two-tone pill ("too medical"; it may serve as a drug-type icon inside the app), the thought bubble with a check (evokes neither stock nor intake), the tall box with text (reads as a document).
- Open: real rendering on a physical phone (round launcher, themed mode, small notification), badge spacing and top/side tint variants.

## Technical decisions to carry over from v1

- **Platform**: Android only, Kotlin + Jetpack Compose (Material 3). Flutter and iOS were dropped: alarm reliability comes first.
- **100 % local**: do not declare the `INTERNET` permission, do not add network dependencies, analytics or Google Play Services (F-Droid compatibility, trust around health data).
- **Reminders**: `AlarmManager.setExactAndAllowWhileIdle`, a single alarm armed at a time (the next one). A `BroadcastReceiver` reschedules on `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET`. Main technical risk: validate on a real phone (reboot, battery saver, clock changes, aggressive vendor battery management).
- **Persistent reminder**: non-dismissable notification (`setOngoing`) that reappears until the occurrence gets an answer. An occurrence without answer stays late all day and becomes "missed" the next day; "Reporter" (postpone) = 30 min.
- **Two separate databases**: the BDPM database (read-only, embedded in assets, replaceable at each release) and the user database (Room). BDPM info is **copied** into the user's entry, with no foreign key. Fully manual entry always possible.
- **Stock is never stored**: it is the sum of movements. A cancelled or skipped intake gives stock back with no drift. Future occurrences are derived from schedules, not stored.
- **Local "wall-clock" times** (`LocalTime`, `LocalDate`): 8:00 stays 8:00 after a clock or time zone change.
- **Backup**: a single encrypted file chosen by the user (PBKDF2 → AES-256-GCM) through the system file picker, with no storage permission and no cloud. Import replaces everything, in one transaction. v1 files stay restorable: keep `BackupJson`'s v1 reader and the `MEDOCS1` magic working. After a restore, the alarm engine (to come) must rearm the next alarm.
- **Images**: no box photos fetched from the internet (copyright); a photo taken by the user, or an icon based on the dosage form.
- **BDPM**: the public drug database (data.gouv.fr, Licence Ouverte Etalab, credit the source), marketed drugs only, generated by a reproducible script. The number of units per box only exists in the label text: extracted with regular expressions and always editable.
- **Vocabulary**: "Médicament" stays the data-model word for drugs; opening up to other uses does not require renaming it at this stage.

### Known pitfalls (lessons from v1)

- The exact-alarm permission is denied by default since Android 14 and is **reset on every reinstall**. `setAlarmClock` needs the same permission (it is not a permission-free fallback); without it, fall back to `setAndAllowWhileIdle` (inexact). `USE_EXACT_ALARM` is reserved by Google Play for alarm clock and calendar apps.
- Test every new API call on an old device: `canScheduleExactAlarms` (Android 12+) and the `POST_NOTIFICATIONS` check (Android 13+) crashed API 28.
- `connectedDebugAndroidTest` **uninstalls the app** at the end (losing permissions and the phone's data) and must be run with `ANDROID_SERIAL` on a dedicated test device.
- Any modified Room entity requires bumping the database version and writing a migration; exported schemas are versioned, and `docs/schema.md` must be updated.

## Distribution

- **Google Play: main channel**, with a generic listing. v1's rejection came from the "Medical" category: the new listing is a **new listing** (an existing listing's package id cannot be changed).
- **F-Droid: later** (no medical category nor organization account, natural audience for an app without internet). F-Droid signs builds itself: an unsigned release suits it.
- Signed APK published on GitHub as a complement, with the user's key.

### Play listing

| Item | Choice |
|---|---|
| Title | T'as pensé à ? Rappels (22 characters) |
| Category | **Tools**, never "Medical". "Health & Fitness" would have given more visibility but was not chosen |
| Short description (80 characters) | Draft: "Rappels de prise avec confirmation et suivi du stock : médicaments, lentilles, croquettes…", to be adjusted to the length |
| Full description | Names medication explicitly, states the app is not a medical device, gives no advice and collects no data |
| Declarations | If the "health apps" form appears, fill it in honestly: the app is not a medical device |
| Individual account | A recent account probably requires a closed test (about 12 testers for 14 days) before production: check in the console |

If a rejection were about the nature of the app (health features), accept it rather than work around it. The listing texts and the privacy policy (GitHub Pages page) from v1 (`play/`, `docs/` on `v1_medocs`) are to be rewritten with the new wording.

## Working conventions

- **Commits**: in English, in the `topic: subject` format followed by bullet points, **no trailers** (no `Co-Authored-By` or other). Example topics: `app`, `build`, `docs`. Commit only when asked.
- Before changing the palette or the icon, reread the "Graphic charter" section above; afterwards run `testDebugUnitTest`.
- Displayed texts (À propos, warning, listing) are rephrased around "reminders and stock" without ever removing the medical warning.

## Test devices

- Test on **physical phones**, not an emulator: one recent (Android 17, API 37) and one old (Android 9, API 28, close to `minSdk` 26) to catch API-level differences.
- With several devices connected, **always target explicitly** (`adb -s <serial>` or `ANDROID_SERIAL`).
- Before installing or driving a device (tapping, screen capture, uninstalling), make sure it is a test device and not a phone holding personal data; ask when in doubt. Restore any setting you changed.

## Reference

`v1_medocs` branch: the complete previous app (data, reminder engine, BDPM, scan, backup, Play listing, detailed `CLAUDE.md`).

## Planned next steps

1. Finish validating the reminder engine on real phones (reboot, Doze, clock, time zone and battery saver already done on the S8; remaining: vendor battery management, Android 17 phone), then check the "Historique" screen and one-off intakes on the phones.
2. Full rewording of the texts (onboarding, "À propos", warning, privacy policy).
3. Play listing and privacy policy; create the new listing, closed test if required. F-Droid submission later.
