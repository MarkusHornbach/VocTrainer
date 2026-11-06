# Architecture

Technical overview of VocTrainer. What the app does: [FEATURES.md](FEATURES.md). File formats:
[FILE_FORMATS.md](FILE_FORMATS.md). Debug build: [TEST_APP.md](TEST_APP.md).

## Tech stack

- Kotlin, Jetpack Compose with Material 3, a single activity (`MainActivity`)
- MVVM: Compose screens observe `StateFlow`s of Android ViewModels
- Room (SQLite) for all learning data, KSP for the Room compiler
- kotlinx.serialization for the JSON files
- WorkManager for the daily reminder
- Android text-to-speech for pronunciation (offline, no network)
- minSdk 26; Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)

## Packages (`io.github.markushornbach.voctrainer`)

| Package | Content |
|---|---|
| `data` | Room entities and DAOs, `VocTrainerDatabase` and its migrations, `VocabularyRepository` (single entry point for reads and writes, transactions), `VocabularyImporter` (decides what an import changes), `VocTrainerJson` (parsing and writing both file formats), the built-in example book, "Buch neu beginnen" |
| `learning` | Pure Kotlin rules without Android dependencies: Leitner levels and due dates, round selection, question and distractor generation, answer checking ("Auch richtig", "Fast richtig"), points, avatar stages, badges, streak, stars, round report. Unit-tested directly |
| `settings` | `AppSettings` (SharedPreferences: theme, sound, goal, reminder, tips, per-device values), the settings part of the backup, the tip catalogue, the codec of saved selections |
| `speech` | Text-to-speech wrapper and the text normalisation for speaking |
| `reminder` | Scheduling and the `CoroutineWorker` of the daily notification |
| `ui` | Compose screens, ViewModels (`QuizViewModel`, `VocabularyViewModel`, `VocabularyEditorViewModel`, `ManageProgressViewModel`, `FirstStartViewModel`, `TestToolsViewModel`), texts, info pages, tips; `ui.theme` holds the three colour themes |

Rules live in `learning` as constants (intervals, round size, thresholds), so screens, info pages and the
round report all read the same values.

The question screen has a compact layout for low windows. The one decision is `isCompactHeight` in
`ui/CompactHeight.kt` (threshold `COMPACT_HEIGHT_THRESHOLD_DP`, 760 dp: below it a Nexus 5X with 731 dp, above it
Pixel 8a and Pixel 10). It uses the height of the window, which stays the same when the keyboard opens, never the
keyboard inset or the answer state. `VocTrainerApp` provides it as `LocalCompactHeight`; only the top bar during a
question and the question screen read it, and every difference sits behind it.

## Data model

| Entity (table) | Key | Purpose |
|---|---|---|
| `Book` (`book`) | `id` | A vocabulary set: title, language, active flag, saved selections, start slot of the current avatar series |
| `Vocabulary` (`vocabulary`) | `id` (UUID) | One entry: texts, unit/part/page, word type, core word, level, highest level, review dates, `isDeleted` (ignored), `updatedAt` |
| `IrregularVerb` (`irregular_verb`) | `id` (UUID) | Verb forms, meanings, position in the book, level, review date |
| `PracticeDay` (`practice_day`) | `bookId`, `date`, `deviceId` | Finished rounds and the daily goal per day and device (streak) |
| `PointsDay` (`points_day`) | `bookId`, `date`, `deviceId` | Rise points and daily bonus per day and device |
| `Figure` (`figure`) | `bookId`, `slot` | One avatar: kind, points at its start, stage, start/finish time |
| `Badge` (`badge`) | `bookId`, `series`, `tier` | An earned badge tier |

Every row except `Book` carries a `bookId`. Per-day rows are per device, so backups of several devices can be
combined without double counting. Settings that belong to a vocabulary set are stored per book.

## Database migrations

- Schema version 13 is the base: `app/schemas/` holds its exported schema (`13.json`), and every later schema
  is exported and committed next to it; existing schema files are never changed.
- Every schema change bumps the database version and adds an `AutoMigration` (`13 → 14`, …); a manual
  `Migration` is used only for data transformations that an `AutoMigration` cannot express.
- No destructive migrations at all: learning progress must survive every update.
- An instrumented test (`DatabaseSchemaTest`) opens a new database with the app's own configuration
  (`VocTrainerDatabase.builder`) and checks it against the exported schema; every new migration gets a test
  against the exported schemas.

## Versions

- `versionName` comes from Git at build time: `git describe --tags --match "v[0-9]*" --long --dirty`, parsed by
  `buildSrc/src/main/kotlin/VersionName.kt`. Exactly on a tag `v1.2.0` it is `1.2.0`, on a release-candidate
  tag `v1.2.0-rc1` it is `1.2.0-rc1`; three commits later `1.2.0+3 (abc1234)` or `1.2.0-rc1+3 (abc1234)`;
  with uncommitted changes ` dirty` is appended; without any tag `0.0.0+<commits> (<hash>)`.
- `versionCode` follows from the version of the last tag (`VersionName.code`):
  `MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + S`, with `S = N` for `vX.Y.Z-rcN` and `S = 99` for
  `vX.Y.Z` (`1.0.0-rc2` → `1000002`, `1.0.0` → `1000099`, `1.2.3` → `1020399`). It increases with every
  release and is the same in every clone that has the tag. Builds after a tag (`+3`, `dirty`) keep the tag's
  code; without any tag the code is `1`. The build fails with a clear message if MINOR or PATCH is 100 or
  more, or N is 99 or more.
- Without Git the build still works (`unbekannt`, code 1).
- `BuildConfig.BUILD_TIME` holds the build time; the settings screen shows version and build time.
- `RELEASE_NOTES.md` in the repository root is copied into the assets of every variant at build time (task
  `copy<Variant>ReleaseNotes`); the build fails with a clear message if it is missing. `ui/ReleaseNotes.kt` holds
  its parser and the rule for opening the page by itself. The version last handled is stored in the `device`
  preferences, which no backup contains.
- Releases are annotated tags `vMAJOR.MINOR.PATCH` on `main`; release candidates are tagged `vMAJOR.MINOR.PATCH-rcN`.

## Build and test

```
./gradlew assembleDebug              # debug build (the test app)
./gradlew testDebugUnitTest          # unit tests (JVM)
./gradlew -p buildSrc test           # unit tests of the build logic (version name)
./gradlew connectedDebugAndroidTest  # instrumented tests on an emulator or device
./gradlew assembleRelease            # release build (signed if signing data is present)
```

Coverage (reports only, no minimum that fails the build) is collected only with the Gradle property
`-Pcoverage`. Without it the unit tests run without a coverage agent and the debug APK is not instrumented.

```
./gradlew -Pcoverage testDebugUnitTest koverHtmlReportDebug koverXmlReportDebug  # unit tests (Kover)
./gradlew -Pcoverage connectedDebugAndroidTest createDebugAndroidTestCoverageReport  # instrumented tests (JaCoCo)
./gradlew -Pcoverage jacocoCombinedReport  # both together, after the two runs above
```

- Kover writes `app/build/reports/kover/htmlDebug/` and `reportDebug.xml`; it uses the JaCoCo agent, so its
  results (`app/build/kover/bin-reports/*.exec`) can be merged with the instrumented ones.
- With `-Pcoverage` the debug build type has `enableAndroidTestCoverage`; the instrumented report lands in
  `app/build/reports/coverage/androidTest/debug/connected/`, the combined one in
  `app/build/reports/jacoco/jacocoCombinedReport/`. The JaCoCo version is set in the version catalog.

- Unit tests cover the rules in `learning`, parsing in `data` and the text builders in `ui`.
- Instrumented tests cover the database, migrations, import, backup/restore and the main UI flows. They use
  an in-memory database and their own settings, so they never touch the app's data. Some layout tests
  change system settings and run only on an emulator.
- Golden-file tests guard the learning behaviour and the backup format (`app/src/androidTest/assets/baseline/`):
  `CleanupBaselineScenarioTest` plays a fixed scenario over many simulated days through the repository with a
  fixed clock (`AppClock.fixedTimeForTest`) and device id and compares its backups with golden files;
  `V0140BackupRestoreTest` restores the oldest backup the app must read and exports it again unchanged.
  Golden files are never edited to make a test pass; each run logs the actual files under the logcat tag
  `Baseline`.
- The screenshots of the user manual (`docs/images/`) are taken by `ManualScreenshotTest` with the example
  book, the theme „Ozean“ in light mode and the look of the release app (the debug build switches its test
  markers off through `AppLook`, which exists in the `debug` and `release` source sets; the release one is
  always off). Run it with `am instrument` and pull `files/manual_screenshots` of the debug app, because
  `connectedDebugAndroidTest` uninstalls the app with its files afterwards.

## Signing

Release signing data is read from the gitignored `local.properties`:

| Key | Meaning |
|---|---|
| `release.storeFile` | path to the keystore |
| `release.storePassword` | keystore password |
| `release.keyAlias` | key alias |
| `release.keyPassword` | key password |

Without these values the release build stays unsigned, so the project builds on any machine.

## Build variants

- **release:** application id `io.github.markushornbach.voctrainer`, the app for learners.
- **debug:** application id suffix `.debug`, a separate app with its own data and extra test functions,
  see [TEST_APP.md](TEST_APP.md).

## Backup

- Manual backup and restore via one JSON file (`voctrainer-sync`, see [FILE_FORMATS.md](FILE_FORMATS.md)).
- Android Auto Backup is enabled (`backup_rules.xml`, `data_extraction_rules.xml`).
