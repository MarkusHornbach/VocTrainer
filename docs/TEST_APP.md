# Test app „VocTrainer Debug“

Debug builds are a separate app for trying features. None of the functions below exist in release builds.
What the app does in general: [FEATURES.md](FEATURES.md).

## Separate app

- Application id `io.github.markushornbach.voctrainer.debug`, name and start-screen title „VocTrainer Debug“, green
  launcher icon. It has its own data and can be installed next to the release app without touching its
  learning progress.
- The daily reminder is off by default; suggested backup file names start with `debug_`; backups record
  `appVariant: "debug"`.
- The test app also reads its own backups with negative rise points (from „Punkte setzen“); the release app
  rejects them.
- Admin mode is always on: learning levels can be raised by hand (release builds can only lower them).

## Test marker

Everything that exists only in the test app looks the same: a fixed **test orange** (in every theme), the
prefix **🧪** before a button, switch or menu card, and a slim bar **„🧪 Nur Test-App“** under the title of
„Testwerkzeuge“, „Vokabeln editieren“ and „Vokabel bearbeiten“. The edit pencil in questions, the correct-answer
dot and the date marker on the start screen are orange too.

## Extra functions in "Vokabeln verwalten"

- **„Vokabeln editieren“:** a filterable list of all words (search in English and German, unit, part, pages,
  level 0–5, word type, „Ignorierte anzeigen“ for ignored words with **„Wiederherstellen“**). Unit/part/pages
  start with the current selection; the filters do not change it.
- **„Vokabel bearbeiten“:** English, German (meanings separated by ";"), unit, part, page, word type
  („Automatisch“ or fixed), extra, „Wort zum Tippen“ and level; **"Speichern"**, **"Abbrechen"**,
  **"Löschen"** (with „Rückgängig“). A duplicate (same English text in the same unit) cannot be saved;
  unsaved changes ask „Änderungen verwerfen?“. An ignored entry without a core word is restored only with a
  tapped word.
- **„Vokabel hinzufügen“:** add a word by hand (unit required); the core word is tapped from chips of the
  English text (a single word is chosen automatically).
- Switch **„Bearbeiten-Knopf in der Abfrage“** (default off, part of the backup): a pencil on every vocabulary
  question opens the edit page of that word; the round continues afterwards with the current data of the word.
- The example book is always listed as „Beispielbuch Englisch (Test)“.
- **„Buch neu beginnen“** offers **„🧪 Ohne Sicherung“** to skip the backup step.
- „Mein Avatar“ has **„🧪 +100 Punkte“**.

## „Testwerkzeuge“ (settings menu)

Each action confirms with a snackbar. At the top a read-only **„Anzeige“** line (system and app font scale
and density) and the **date bar**; below it collapsible groups with „Alle aufklappen“ / „Alle zuklappen“.

| Function | What it does |
|---|---|
| Date bar **„+1“**, **„+7“**, **„+14“**, **„Echtes Datum“** | The app acts as if the shifted day were today (due dates, calendar-day rules, daily goal, streak, reminder); answers, points and practice days are stored with that date, `updatedAt` keeps the real time. A marker „Test: <date>“ appears on the start screen; the shift survives a restart |
| **„Lernlevel der Auswahl setzen“** (0–5, toggle „Vokabeln“ / „Verben“) | Sets all words or verbs of the saved selection to that level and makes them due; for vocabulary also the highest level, so points can be tried again |
| **„Auswahl jetzt fällig machen“** | Clears the review date of the current mode's selection (levels stay) |
| **„Nächste Runde anzeigen“** | Lists the words of the prepared next round with level and reason ("kann gelernt werden", "Wiederholung", "neu", "aufgefüllt (vorzeitig)") |
| **„Stufe setzen“** (0–10) | Sets the current avatar's stage ignoring the requirements (10 finishes it) |
| **„Punkte setzen“** | Makes the point total equal the value by correcting today's rise points of this device (may become negative); the stage is recomputed |
| **„Avatar fertigstellen“**, **„Avatar zurücksetzen“** | Finish the current avatar; delete all avatars of the book (choice appears again) |
| **„Avatar-Modell“** | The numbers behind the current avatar: avatar number, finished/given-up avatars, start slot, cap, scale factor, next requirements |
| **„Abzeichen“**, **„Abzeichen jetzt prüfen“**, **„Abzeichen zurücksetzen“** | Show value and earned tiers per series; run the check; delete all badges (the next check awards them again silently) |
| **„Zweites Testbuch anlegen“** | Creates the inactive book „Testbuch“ with 10 invented words and 2 verbs |
| **„Alle Vokabeln entfernen“** | Removes words and verbs of the active book |
| **„Tipp sofort zeigen“** | Shows a tip at its real place with the example book (demo rounds and an invented result store nothing) |
| Switch **„Ein Tipp pro Besuch“** | Default on; off removes the one-tip-per-visit limit |
| **„Erster Start simulieren“** | Shows welcome, book choice and avatar choice again without deleting data |
| **„Alle Tipps zurücksetzen“** | Like „Tipps wieder zeigen“ |
| **„Alle Info-Seiten“** | Opens every info page with the current data |
| Switch **„Richtige Antwort markieren“** | A small dot on the correct option before answering |
| **„Prüfung testen“** | Type an answer for any entry and see what the checker makes of it („Richtig“, „Auch richtig“, „Fast richtig – …“, „Falsch – …“ with the reason); changes nothing |
