# Features

A complete description of what VocTrainer does, for technically interested readers. The app UI is
German; labels are quoted exactly as shown, with a short English explanation. How the app is built:
[ARCHITECTURE.md](ARCHITECTURE.md). File formats: [FILE_FORMATS.md](FILE_FORMATS.md). Debug-only
functions: [TEST_APP.md](TEST_APP.md). Planned features: [ROADMAP.md](ROADMAP.md). How to use the app (German
user manual): [HANDBUCH.md](HANDBUCH.md).

Learner-facing texts never mention due dates ("fällig") or review intervals; the rules below are internal.

## Overview

- Learners practise English vocabulary from their own textbook: words are imported from a JSON file
  (one file per unit range or per book), then asked in rounds of 10, first with four suggested answers,
  from level 3 on by typing.
- A separate quiz trains the simple past of irregular verbs, optionally also the past participle.
- Rewards: points, a growing avatar, badges, a daily goal with a streak, and a daily reminder.
- Everything is stored on the device. There is no account and no server.

## First start

- Only on a fresh installation (not after an update and not after restoring a phone backup). Until it is
  finished, nothing else is shown (no help, settings or tips). If the app is closed halfway, the first start
  begins again from the welcome screen; a book loaded already stays.
- **Welcome screen:** „Willkommen bei VocTrainer!“, the main button **"Los geht's"** and below it
  **"Ohne Tipps starten"** (start without tips).
- **Book choice** „Mit welchem Buch lernst du?“: **"Mein Buch laden"** opens the file picker, imports the
  file and makes its book active (no further question). An invalid file shows „Diese Datei passt nicht.
  Wähle eine Vokabeldatei.“ The text link **"Keine Vokabeldatei? Beispielbuch ausprobieren"** activates the
  built-in example book, followed by a hint card that an own book can be loaded later (⚙️ → Vokabeln verwalten).
- **Avatar choice** (skipped if the book has an avatar), then the start screen; the first start is finished.
- "Ohne Tipps starten" marks all tips as done and shows once how to switch them on again.
- System back: welcome → leaves the app; book choice → welcome; example-book hint → book choice.

## Books

- Vocabulary is organised in **books**. Each book has its own vocabulary, irregular verbs, levels, points,
  avatars, badges, practice days and streak, and its own saved selections (vocabulary and verbs). Theme,
  sound, vibration, daily goal, reminder and quiz mode are shared by all books.
- Exactly one book is **active**; quiz, selection, „Dein Lernstand“, points, avatar, badges, streak and
  reminder show only the active book. Its title is shown below "VocTrainer" in the top bar.
- A book is created by importing its first file. Books cannot be deleted.
- **"Vokabeln verwalten"** (manage vocabulary) has three sections, each with a header and an ⓘ:
  - **"Bücher":** the book list ("120 Wörter, 35 gelernt", the active one marked „Aktiv“; a tap activates
    a book and changes no data) and **"Vokabeln importieren"**.
  - **"Sicherung":** **"Sicherung exportieren"** and **"Sicherung wiederherstellen"** (see Backup).
  - **"Lernstand zurücksetzen":** **"Buch neu beginnen"** (see below).
- **Example book "Beispielbuch Englisch":** built in (150 words, 36 irregular verbs, 35 of them with a past participle,
  units 1–3, self-made).
  It is the active book on a fresh installation; otherwise it is added inactive. It appears in the book list
  only while active. At every app start a missing example book is added again and words of a newer app
  version are completed; its progress, ignored words and changed core words stay. It is never written to a
  backup and starts fresh after every restore.
- **"Buch neu beginnen"** (start the active book from scratch):
  - Reset: all words (also ignored ones) and verbs of the book to level 0 (highest level, review date and
    first-typed date cleared), the book's points, badges, practice days and streak; the growing avatar is
    given up and the learner chooses avatar 1 again.
  - Kept: finished avatars („Meine Sammlung“; they do not count for the next avatar), the vocabulary,
    the saved selections, other books and all settings.
  - Disabled with „In diesem Buch gibt es noch nichts zurückzusetzen.“ when there is nothing to reset.
  - Flow: „Buch neu beginnen?“ (what is reset and what stays) → **"Sicherung speichern"** opens the backup
    save dialog → only after the backup was saved „Wirklich neu beginnen?“ → **"Endgültig neu beginnen"**.
    Cancelling at any step resets nothing. The example book has no backup step (one dialog).
  - One database transaction; the backup saved before brings the old state back completely.

## Vocabulary

- An entry has: English text, one or more German meanings (the first is the main meaning), unit, optional
  unit title, part and page, optional word type (`verb`, `noun`, `phrase`, `other`), optional extra
  information, and a **core word** („Wort zum Tippen“: the one word of the entry that is typed, e.g.
  "shelter" in "animal shelter").
- Missing word types are guessed in this order: "(to) …" is a verb; a sentence (ending in . ? !), an entry
  with "…" or with three or more words is a phrase („Redewendung“); a main German meaning starting with a
  capital letter (after an article or a bracket) is a noun; everything else is "other".
- German meanings are shown without gender suffixes ("Nachbar/-in" → "Nachbar"; also "/in", "*in", ":in",
  "(in)", "In"); stored text, import and backup keep the original.
- Extra information is stored but not shown in the quiz.
- **Every active entry has a core word.** Entries without one (e.g. from a backup) get it automatically
  if the English text has exactly one word (without brackets, placeholders such as "sb."/"sth." and "…");
  otherwise they are **ignored** and keep their level. This check runs at every start, after every import
  and after every restore (the message then names how many entries were ignored).
- Ignored ("deleted") entries are not asked, are kept with their level and are part of the backup. The
  release app cannot edit, add or delete single words; that exists only in the test app.

## Import

- File format `voctrainer-import`, see [FILE_FORMATS.md](FILE_FORMATS.md). Each file names its book
  (`bookId`, `bookTitle`); several files with the same `bookId` fill the same book.
- The whole file is checked first; on any error nothing is imported and a German message names the problem.
- A new book is created. If another book is active, the app asks „Neues Buch „…“ – jetzt zu diesem Buch
  wechseln?“ (**"Ja"** / **"Nein"**). The first book becomes active without a question.
- A file of an existing book is merged into it (also when inactive); a different `bookTitle` renames it.
- New entries are created; entries marked `excluded` are created as ignored.
- The file is the reference for entries that exist already (same English text in the same unit of the
  book; no new entry is created for them):

  | Stored value | After the import |
  |---|---|
  | German meanings, unit title, part, page, extra, word type | Taken from the file; a value missing or empty in the file clears the stored one |
  | Core word | Taken from the file; an `excluded` entry without one keeps the stored one |
  | Ignored or active | Ignored if the entry is `excluded`, otherwise active (also when it was ignored before) |
  | Level, review date, history, id | Never changed |

  An active entry without a usable core word is ignored afterwards (as after every import).
- The example book is not affected: at every start it only fills missing values and never changes
  meanings, core words or ignored entries.
- Irregular verbs are added or updated (missing page filled in).
- The result message counts imported entries (and how many of them are ignored), entries that existed
  already (and how many of them were changed, are ignored now or are active again) and added/updated verbs.
  After new words were imported into the active book, the selection card pulses a few times.

## Selection („Auswahl“) and start screen

- Two tiles **"Vokabeln"** / **"Verben"** choose the quiz mode (remembered).
- The start screen shows the streak bar, the selection summary (e.g. "Unit 1 · Einstieg, A · S. 150–152 ·
  45 Wörter" with a pencil), the button **"Los geht's"**, the card **„Dein Lernstand“** and (vocabulary mode)
  the avatar.
- Tapping the summary opens the selection screen. Changes are a draft: **"Auswahl übernehmen"** keeps them,
  **"Zurück"** closes at once if nothing changed, otherwise asks „Deine Auswahl hat sich geändert. Möchtest du
  sie übernehmen?“ (**"Übernehmen"** / **"Verwerfen"**). The kept selection is saved per book and mode.
- Filters, combined, each word must match all of them; each row starts with an **"Alle"** chip:
  - **Unit:** "Alle Units", each unit, "Ohne Unit" if needed. Changing the unit resets part and pages.
  - **Part** (single unit only): several parts; "Einstieg" = words without a part. Changing parts resets pages.
  - **Seiten** "von" – "bis": only pages with words of the chosen unit/parts are offered (the selection never
    becomes empty); empty "bis" = one page; with a page filter words without a page are left out.
- Phrases are always part of the selection. "Los geht's" is disabled when nothing matches; the quiz needs at
  least 4 words in the book („Dein Buch braucht mindestens 4 Wörter.“).
- **„Dein Lernstand“:** bar "X % gelernt" for exactly the selection (gelernt = level 4 or higher), plus one
  line about the next round: „Nächste Runde: N Wörter kannst du fertig lernen.“ (words that a correct typed
  answer today makes "gelernt"), else „Nächste Runde: N neue Wörter.“, else nothing. Before the first round
  of a book only „Starte deine erste Runde!“. The next round is picked ahead of time and the next start uses
  exactly these words unless words, selection, mode or day changed.
- Tapping the card shows the number of words per level 0–5 (0 „neu“, 1–3 „am Üben“, 4 „gelernt“,
  5 „gemeistert“) in coloured blocks by question type; levels 4–5 are framed as „Ziel“ (goal).
- A **scroll hint** (small down arrow) appears on every scrolling page while there is more below.

## Rounds

- A round has 10 words (fewer if the selection is smaller). Up to 3 places go to words that can become
  "gelernt" today (oldest review first); the rest in this order: due words (lowest level first, random within
  a level), new words (level 0, random), then words not due yet (lowest level first, oldest review first).
- Only the **first attempt** of a word counts. Wrong words come back at the end („Wiederholung“) until
  answered correctly; repetitions give no level and no points.
- **"X"** or back asks „Runde abbrechen?“ (**"Abbrechen"** / **"Weiterspielen"**); answers given keep their effect.
- **Question layout:** fixed header („Frage n von m“ / „Wiederholung“), the word at the top, the answer part
  at the bottom right above the keyboard. Nothing moves when an answer is given; all places for messages are
  reserved. Only the upper part scrolls if the screen is too small.
- **Compact question layout on small screens** (window height below 760 dp; decided by the window height alone, so
  it never changes with the keyboard or an answer; taller screens are not affected):

| Part | Compact layout |
|---|---|
| Top bar (app name, book) | hidden during a question |
| Header (X, „Frage n von m“) | one slim row; X and its dialog work as usual |
| Question text | smaller, same size in both answer types |
| Buttons and spacing | answers, „Prüfen“ and „Nochmal versuchen“ lower, less spacing and padding; the places for the result and „Nochmal versuchen“ stay reserved, only smaller |
| Verb questions | the asked form stands directly above the answers: „Simple Past?“ or, in step 2 of „3 Formen“, „Past Participle?“. It replaces the instruction line |
| Upper part | its bottom edge fades out while more content lies below it |
| Wrong typed answer | the upper part scrolls just far enough to show the correct word and the copy instruction; the German text stays reachable by scrolling |

- **Direction by level:** levels 0–1 English → German, from level 2 German → English (decided when the word
  is picked; a repetition keeps it).
- **With suggestions** („Mit Vorschlägen“): four answers. English → German: the main meaning and three wrong
  German meanings, preferably of the same word type, unit and part. German → English: all German meanings
  separated by ";" and four English entries as stored. A wrong answer never shares a meaning with the asked
  word; no answer appears twice.
- **Result banner** after every answer: **"Richtig!"** (green), **"Fast richtig"** (calm colour) or
  **"Leider falsch"** (red; the answer shakes, optional vibration). After a wrong suggested answer the
  solution is shown and **"Nochmal versuchen"** shows the same answers in a new order with „Finde die richtige
  Antwort.“ until the correct one is found. A correct answer moves on after about a second.

## Typing (levels 3–5)

- Every vocabulary entry on level 3, 4 or 5 is a typing question (also phrases; verbs never).
- The question shows the German meanings and a **frame**: the English entry with the core word as a gap
  ("(to) ask sb. for ____"); the gap fills live. Input field and **"Prüfen"** (check) sit above the keyboard;
  the keyboard opens by itself without suggestions, autocorrect or capitals.
- **"Mit Vorschlägen"** appears next to "Prüfen" after 3 s (level 3) or 5 s (levels 4–5) while the field is
  empty and turns the question into four English suggestions (no way back). On levels 4–5 the note „Mit
  Vorschlägen: Wort fällt auf ‚am Üben‘ zurück“ is shown.
- **Checking:** correct is the core word or the whole entry (with or without "to"), ignoring case, outer
  spaces and the apostrophe variant; a core word ending in a period also counts without it.
- **„Auch richtig!“:** another entry of the app that shares a German meaning (compared without brackets and
  placeholders) – „Auch richtig! Gesucht war: …“, next question after about 3 s.
- **„Fast richtig“:** the American spelling of the core word (rules, e.g. colour → color, centre → center,
  organise → organize, travelled → traveled) or one small typo (one letter wrong, missing or extra, or two
  neighbouring letters swapped; words of at least 4 letters), also of an "Auch richtig" entry. A variant that
  is itself a word of the learning material stays wrong. The reason („Kleiner Tippfehler“ / „Im Lehrbuch
  britisch“) and the correct word are shown; the learner copies it once.
- **Wrong:** „So geht's richtig: …“ and „Tippe das markierte Wort ab.“; the learner copies the core word once.
  Wrong words come back at the end as typing questions.

## Levels (Leitner system)

| Answer (first attempt) | Effect on the level |
|---|---|
| Wrong (any form) | −1 (not below 0) |
| Correct with suggestions | +1, at most to 3 for vocabulary (verbs up to 5) |
| Correct, rising to 1–3 | always (also when not due) |
| Correct, rising to 4 | only if the previous review was on an earlier calendar day |
| Correct, rising to 5 | only if the word is due |
| Typed correctly on 5 | stays 5 |
| "Mit Vorschlägen" instead of typing, correct | level becomes at most 3 |
| „Auch richtig“ / „Fast richtig“ | unchanged (counts as reviewed today) |

**Rest after a review** (calendar days, device time zone): level 0 always due, 1 → 1 day, 2 → 2, 3 → 4,
4 → 7, 5 → 14. Words without a review date are due. Each first attempt stores the review date; the date of
the first correctly typed first attempt is stored once per word (not shown, part of the backup).

## Result screen and „Details“

- 0–3 **stars** for the first attempts: each word earns 3 thirds when correct; where typing was possible
  "Mit Vorschlägen" and „Fast richtig“ earn 1 third, wrong 0. ≥ 90 % → 3, ≥ 70 % → 2, ≥ 40 % → 1 star. If
  reduced answers cost stars, a hint says that typing gives more stars.
- Headline, „Du hast X von Y Wörtern auf Anhieb gewusst.“, the words wrong at first, points, avatar growth
  and new badges; **"Noch eine Runde"** / **"Zum Start"**.
- „X aufgestiegen · Y zurückgefallen“ and the collapsible **"Details"**: every word of the round (first
  attempts, round order) with the new level ("↑ 4", "= 3", "↓ 2"), its points ("+4 P") and, where useful, a
  short reason (e.g. „Aufstieg ab morgen“, „mit Vorschlägen nur bis Level 3“, „fast richtig (Tippfehler)“,
  „Level bereits vorher erreicht“). Built from the same function that sets the level. Verb rounds: no points,
  sorted rises – unchanged – drops.

## Points

| Source | Points |
|---|---|
| A word reaches a level for the first time (above its highest level so far) | the level number (level 3 = 3) |
| Due word on level 5 typed correctly again | 5 |
| Daily goal reached (once per day) | 10 + streak days incl. today, at most 20 |
| Wrong, repetition, "Nochmal versuchen", "Mit Vorschlägen" instead of typing, „Auch/Fast richtig“, verbs | 0 |

- Every word stores its highest level; dropping and rising again earns nothing. Points are never lost; only
  "Buch neu beginnen" sets them to 0. Points are stored per day and device; the daily bonus counts once per day.
- Shown on the result screen („+12 Punkte“) and as total on „Mein Avatar“.

## Avatar

- Kinds **"Drache"**, **"Monster"** (start as an egg) and **"Kämpfer"** (starts as a recruit); 11 images per
  kind (stages 0–10). The choice is final; every book has its own avatar.
- A stage needs points since the avatar was started **and** learned words (active vocabulary on level 4–5 of
  the whole book, phrases included, verbs not):

| Stage | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Points | 30 | 120 | 300 | 550 | 900 | 1,300 | 1,800 | 2,500 | 3,300 | 4,300 |
| Learned words | – | – | 10 | 25 | 50 | 80 | 120 | 170 | 230 | 300 |

- **Each avatar needs its own words:** avatar k starts where avatar k−1 ended (avatar 2: stage 3 needs 310,
  stage 10 600 learned words). All avatars of a book together ask for at most 80 % of its active entries.
  Where less than a full avatar is left, words and points of that avatar shrink by the same factor (at least
  10 %). Finished avatars count; a given-up one and those before "Buch neu beginnen" do not. Requirements are
  recalculated after imports; a reached stage is never lowered.
- A new avatar can be chosen only if at least 150 more learned words are possible; until then the finished
  one stays with a short explanation.
- **Start screen** (vocabulary mode): the avatar, below it a pill with the bar to the next stage (a marker;
  a lock at the full bar when only learned words are missing) and one line such as „Stufe 4: noch 120 Punkte,
  5 gelernte Wörter“. It wiggles once after a stage rise. Tap → **„Mein Avatar“**: large image, stage, points,
  learned words, progress, „Meine Abzeichen“ and „Meine Sammlung“ (finished avatars).
- Stage 10 moves the avatar to the collection; the next one starts at stage 0. The stage is recalculated after
  every round, at app start and after a restore; only rises in a round are celebrated.

## Badges

| Series | Value | Bronze / Silber / Gold / Platin |
|---|---|---|
| **Serie** | longest streak in days | 3 / 7 / 30 / 100 |
| **Gelernte Wörter** | words on level 4–5 now (as for the avatar) | 10 / 50 / 150 / 500 |

- No points; an earned tier stays (only "Buch neu beginnen" deletes badges). Shown on „Mein Avatar“ and as
  „Neues Abzeichen: …“ on the result screen of a vocabulary round. Checked after rounds, at start, when the
  start screen or „Mein Avatar“ opens and after a restore.

## Daily goal, streak and reminder

- **Tagesziel:** 2–5 finished vocabulary rounds per day (default 2). Verb rounds and cancelled rounds do not
  count.
- **Streak bar:** „5 Tage“ and „Heute: 1 von 2 Runden“, a check mark when reached, a shield when the joker was
  used; without a streak „Starte heute deine Serie!“.
- **Streak:** consecutive days with the goal reached, ending today or yesterday. Rounds of all devices count
  together (via the backup); a day needs the highest goal set on any device that day.
- **Joker:** one missed day per calendar week (Monday–Sunday) is bridged automatically; it does not add a day.
- **Reminder** („Tägliche Erinnerung“, default on, 17:00): only if today's goal is missing, e.g. „Deine
  Serie: 5 Tage 🔥“ / „Heute kannst du 3 Wörter fertig lernen – los geht's!“; a tap opens the start screen.
  On Android 13+ the app asks for the notification permission; if denied, the reminder is switched off.

## Irregular verbs

- Stored separately (infinitive, simple past, optional past participle, German meanings, unit, part, page).
- **Verb quiz** (tile "Verben"), always with four suggestions. The setting **„Verbformen“** (page „Üben“)
  chooses how many forms are asked:

| „Verbformen“ | Question | Asked verbs |
|---|---|---|
| „2 Formen (go – went)“ (default) | infinitive → simple past, „Wie lautet das simple past?“ | all |
| „3 Formen (go – went – gone)“ | „Wie lauten die Formen?“: step 1 simple past, step 2 past participle | only verbs with a past participle |

- The correct form exactly as stored (also double forms such as "was / were" as one form, particles as stored).
- **„3 Formen“** in detail:

| Situation | What happens |
|---|---|
| Question shown | Two fixed lines „simple past ?“ and „past participle ?“ below the instruction; the current step is coloured |
| Step 1 right | The simple past appears in its line, then four new suggestions for the past participle |
| Step 1 wrong | Solution („(to) go – went“) and „Nochmal versuchen“ as usual; once found, step 2 follows anyway |
| Step 2 wrong | Solution with all three forms and „Nochmal versuchen“ |
| Both first answers right | Verb counts as right: +1 level |
| Either first answer wrong | Verb counts as wrong: −1 level, repeated at the end of the round with both steps |
| Step 2 right, step 1 wrong | „Das Simple Past war noch nicht richtig.“ (shown a little longer) |

- Only the first answer of each step counts; answers after „Nochmal versuchen“ and the repetition never change
  the level. Verbs without a past participle are left out of the selection, „Dein Lernstand“, the level counts
  and the rounds while „3 Formen“ is on; if no verb of the book has one, the start screen says so
  („Bei deinen Verben fehlt noch die dritte Form. …“).
- Switching „Verbformen“ keeps all verb levels (one level per verb for both modes).
- Wrong simple past forms are generated by fixed rules from the verb (regular "-ed" form, vowel changes, typical
  endings, if needed the simple past of another imported verb); real forms of other verbs and a block list are
  never used.
- Wrong past participles (step 2), in this order: the verb's own simple past if it differs from the participle
  (went for gone), the regular form (goed), "-en"/"-n" on the simple past (tooken) and on the infinitive (goen),
  "-ed" on the simple past (wented), vowel changes and "-t" endings. Never a form of another verb (participles
  included), never the block list; the correct answer appears exactly once, also when forms are equal
  (made – made, put – put – put).
- Same selection, filters, „Dein Lernstand“ and Leitner levels as vocabulary (verbs rise to 5 with suggestions);
  separate levels and a separate saved selection.
- Each round also contains up to 2 **regular verbs** from the vocabulary of the same selection (up to 12
  questions); they change no progress and are not counted in the selection. With „3 Formen“ they are asked in
  both steps; their past participle is the same "-ed" form (play – played – played).
- Verb rounds give no points and count for neither the daily goal, the streak nor the badges.

## Speech

- Offline text-to-speech, preferring a British voice, then American, then any English voice, slightly slower.
  "(to)" is read as "to", "sb."/"sth." as "somebody"/"something", "/" as "or"; "…" is skipped.
- English → German: the word is spoken when shown (speaker button „Vorsprechen“). German → English: nothing
  before the answer, the English entry after it. Verbs: infinitive first, simple past after the answer; with
  „3 Formen“ nothing after step 1 and all three forms in one go after step 2 („to go – went – gone“).
- Without an English voice nothing is spoken; the settings offer **"Stimme installieren"**.

## Info pages, help and tips

- An **ⓘ** opens an info page (short lines and tables; all numbers come from the rule constants). The help
  page **"Hilfe"** (the "?" in the top bar) lists them in three groups:

| Group | Pages |
|---|---|
| ÜBEN | „Vokabeln auswählen“ (verbs: „Verben auswählen“), „Was bedeuten die Level?“, „Verben üben“ |
| BELOHNUNGEN | „So bekommst du Punkte“, „So wächst dein Avatar“, „Abzeichen“, „Deine Serie“, „Zeichen in „Details““ |
| EINSTELLUNGEN | „Bücher und Sicherung“, „Buch neu beginnen“, „Tagesziel & Erinnerung“ |

- **Tips** („Tipp“): one-time spotlight hints (dimmed screen, the element stays bright, a card with
  **"Verstanden"**). Closing without "Verstanden" brings the tip back later. At most one tip per visit of the
  start screen, never over dialogs or the selection screen; the card always stays fully visible.
- The nine tips in order: „Welche Wörter möchtest du lernen?“ (selection), „Los geht's“ (start button,
  vocabulary mode), „Verben üben“ (mode tiles, first tap on "Verben"), „Selbst tippen“ ("Mit Vorschlägen" at
  the first typing question), „Wiederholung“ (first repetition), „Das hast du geschafft“ (first result),
  „Dein Lernstand“ followed directly by „Dein Avatar“, then „Jetzt kennst du alles Wichtige“ (the "?").
- **"💡 Tipps wieder zeigen"** on the help page shows all tips again. Tips are stored for the app, not per book.

## Settings

- The gear icon (hidden during a round) opens **"Einstellungen"** with four cards:
  - **"Vokabeln verwalten"** (see Books).
  - **"Farben, Ton & Vibration":** themes **"Gaming dunkel"** (always dark), **"Ozean"** (default) and
    **"Sonnig"** (both follow the system dark mode; green/red only for answers); **"Wörter automatisch
    vorsprechen"** (default off); **"Vibration bei falschen Antworten"** (default off, played as media vibration).
  - **"Üben":** **„Verbformen“** with „2 Formen (go – went)“ (default) and „3 Formen (go – went – gone)“
    (see Irregular verbs); the card shows „Verbformen: 2 Formen“ or „Verbformen: 3 Formen“.
  - **"Tagesziel & Erinnerung":** daily goal and reminder time.
- Version and build time at the bottom (e.g. „Version 1.0.0+3 (abc1234)“, „Erstellt am … · Debug“). Tapping this
  block opens **„Versionshinweise“** (release notes).
- **Fixed UI scale:** the app ignores the system font size and display size, so every screen fits.

### „Versionshinweise“ (release notes)

The page lists all versions from `RELEASE_NOTES.md`, which is bundled into the app when it is built, newest
first: heading „Version X.Y.Z“, date, bullet points. Release candidates appear like any other version. Without
readable notes the page says „Gerade gibt es hier nichts zu lesen.“

The page also opens by itself once. This is decided on the start screen, from the version stored for the page:

| Situation | Opens by itself | Stored version afterwards |
|---|---|---|
| Fresh installation (nothing stored, first start not finished) | no | running version |
| Same version as stored | no | unchanged |
| Other version with its own section, also a patch version | yes | running version |
| First update from a version without this page (nothing stored, first start finished) | yes, if it has its own section | running version |
| Other version without its own section (e.g. a local build) | no | running version |
| Other version stored while the first start is not finished | after the first start | running version |

It waits while a round, the selection screen or a tip is open. A tip that is due comes after the page is closed.
Back from the automatic page leads to the start screen, back from the settings entry to the settings. The stored
version is device state: it is in neither the backup file nor Android's backup.

## Backup and restore

- **"Sicherung exportieren"** writes all books except the example book with vocabulary (also ignored),
  verbs, progress, practice days, points, avatars, badges, saved selections and the learner's settings into
  one `voctrainer-sync` file at any location (e.g. cloud storage); the suggested name is
  `<yyMMdd>_voctrainer-sicherung.json`.
- **"Sicherung wiederherstellen"** replaces the complete learning state after a confirmation that lists the
  books of the file („Die Sicherung enthält: …“). Nothing changes before the confirmation or if the file is
  invalid. A backup of the other app variant (test app vs. release) shows a warning but is allowed.
- Settings in the backup are only read by „Sicherung wiederherstellen“. There is no device-to-device sync;
  moving to another phone works via export and restore.
- Android Auto Backup additionally saves the app data with the Google account.

## Data safety

- All data stays on the device; no account, no server, no network access for learning content.
- Database updates keep vocabulary and learning progress (see [ARCHITECTURE.md](ARCHITECTURE.md)).
- Files written by another app version stay readable within the same format version (see [FILE_FORMATS.md](FILE_FORMATS.md)).

## Test app

Debug builds are a separate app „VocTrainer Debug“ with extra tools; see [TEST_APP.md](TEST_APP.md).
