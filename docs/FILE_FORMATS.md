# File formats

VocTrainer reads and writes two JSON formats (UTF-8):

| Format | Purpose | Read by | Written by |
|---|---|---|---|
| `voctrainer-import` | Vocabulary of a book, for "Vokabeln importieren" | the import | your own tools |
| `voctrainer-sync` | Complete backup of the learning state | "Sicherung wiederherstellen" | "Sicherung exportieren" |

How the app uses the data: [FEATURES.md](FEATURES.md).

## Compatibility rules

- Every file has a top-level `format` (string) and `version` (integer).
- Unknown fields are ignored, so a file may carry extra information.
- New information is added as **optional fields** within the same version; a missing optional field gets its
  default. Older files therefore stay readable, and older app versions can read newer files of the same version.
- A version higher than the app knows is rejected: „Die Datei stammt aus einer neueren App-Version. Bitte die
  App aktualisieren.“
- Current versions: import **2**, backup **5**. A lower version is rejected: „Diese Datei stammt aus einer zu
  alten App-Version und kann nicht gelesen werden.“
- A file of the wrong format is rejected with a hint to the other button.
- At most 10,000 entries and verbs together per file.
- Any error rejects the whole file; nothing is changed. Messages are German and name the entry, e.g.
  „Vokabel 3 („pet“): „unit“ fehlt“.

## Import format `voctrainer-import` (version 2)

### Top level

| Field | Type | Required | Meaning |
|---|---|---|---|
| `format` | string | yes | `"voctrainer-import"` |
| `version` | integer | yes | `2` |
| `bookId` | string | yes | Stable id of the book; files with the same id fill the same book |
| `bookTitle` | string | yes | Name shown in the app; a different title renames an existing book |
| `language` | string | no | Language of a new book, default `"en"` |
| `source`, `created` | string | no | Informational only |
| `entries` | array | no | Vocabulary entries (below) |
| `irregularVerbs` | array | no | Irregular verbs (below) |

### Vocabulary entry

| Field | Type | Required | Meaning |
|---|---|---|---|
| `english` | string | yes | The English entry as printed, e.g. `"(to) ask sb. for sth."` |
| `german` | array of strings | yes | German meanings, the first is the main meaning; none may be empty |
| `unit` | integer | yes | Unit number |
| `unitTitle` | string | no | Title of the unit |
| `part` | string | no | Part of the unit (e.g. `"A"`); missing = the unit's opening section ("Einstieg") |
| `page` | integer | no | Page number, at least 1 |
| `type` | string | no | `"verb"`, `"noun"`, `"phrase"` or `"other"`; any other value counts as missing (the app guesses) |
| `coreWord` | string | yes, unless `excluded` | The one word the learner types: a single word that occurs as a whole word in `english` (a final period may belong to it) |
| `extra` | string | no | Example sentence or note (stored, not shown in the quiz) |
| `excluded` | boolean | no | `true`: the entry is ignored (a new one is created ignored, an existing one becomes ignored); missing or `false`: an ignored existing entry becomes active again |
| `excludedReason` | string | no | Informational only |

Rules:
- The import file is the reference for existing entries: an entry with the same `english` and `unit` as an
  existing entry of the book is not created again but updated from the file. `german`, `unitTitle`, `part`,
  `page`, `extra`, `type` and `coreWord` replace the stored values (a missing or empty optional field clears
  the stored value; only an `excluded` entry without `coreWord` keeps the stored core word), and `excluded`
  decides whether it is ignored or active. The learning progress is never changed. If the file contains
  the same entry twice, the first one counts.
- The built-in example book is the exception: it only fills missing values.
- If entries without `coreWord` (and not `excluded`) exist, the whole file is rejected, naming up to five
  of them.
- The field `multipleChoiceOnly` is accepted and ignored.

### Irregular verb

| Field | Type | Required | Meaning |
|---|---|---|---|
| `infinitive` | string | yes | e.g. `"(to) be"` |
| `simplePast` | string | yes | e.g. `"was / were"` (double forms as one string) |
| `pastParticiple` | string | no | Third form |
| `german` | array of strings | yes | German meanings |
| `unit` | integer | yes | Unit number |
| `part` | string | no | Part |
| `page` | integer | no | Page, at least 1 |
| `germanAdded` | boolean | no | `true` if the German meaning was added because the book does not print it (default `false`) |

### Example

```json
{
  "format": "voctrainer-import",
  "version": 2,
  "bookId": "beispielbuch",
  "bookTitle": "Beispielbuch Englisch",
  "language": "en",
  "entries": [
    {
      "english": "pet", "german": ["Haustier"],
      "unit": 1, "unitTitle": "Our new pet", "part": null, "page": 150,
      "type": "noun", "coreWord": "pet", "extra": "Do you have a pet?"
    },
    {
      "english": "animal shelter", "german": ["Tierheim"],
      "unit": 1, "unitTitle": "Our new pet", "page": 150,
      "type": "noun", "coreWord": "shelter"
    },
    {
      "english": "(to) ask sb. for sth.", "german": ["jn. um etwas bitten"],
      "unit": 1, "part": "A", "page": 151, "type": "verb", "coreWord": "ask"
    },
    {
      "english": "Can we keep it?", "german": ["Können wir es behalten?"],
      "unit": 1, "page": 150, "type": "phrase", "coreWord": "keep"
    }
  ],
  "irregularVerbs": [
    {
      "infinitive": "(to) be", "simplePast": "was / were", "pastParticiple": null,
      "german": ["sein"], "unit": 1, "part": "A", "page": 151, "germanAdded": false
    }
  ]
}
```

## Backup format `voctrainer-sync` (version 5)

The format name is kept for compatibility (there is no device sync). A backup contains all
books except the built-in example book; restoring it replaces the whole learning state.

### Top level

| Field | Type | Required | Meaning |
|---|---|---|---|
| `format`, `version` | | yes | `"voctrainer-sync"`, `5` |
| `books` | array | yes (missing = empty, but every record needs a book) | Every book; exactly one becomes active (the first marked active, else the first) |
| `entries` | array | no | Vocabulary rows of all books |
| `irregularVerbs` | array | no | Verb rows of all books |
| `practiceDays` | array | no | Finished rounds per day and device |
| `pointsDays` | array | no | Points per day and device |
| `figures` | array | no | Avatars |
| `badges` | array | no | Earned badge tiers |
| `settings` | object | no | The learner's settings (below) |
| `appVariant` | string | no | `"release"` or `"debug"` (the test app); restoring a file of the other variant shows a warning |
| `appVersion` | string | no | Version name of the app that wrote the file, as shown in the settings (e.g. `"1.0.0"` or `"1.0.0+3 (abc1234)"`); information only, restoring does not depend on it |

Every row except a book carries `bookId`, which must name a book of the file. Timestamps are epoch
milliseconds; dates are ISO `yyyy-MM-dd`.

A backup also contains the import fields `bookId`, `bookTitle`, `language`, `source` and `created` at the top level
with the value `null`; they are ignored when restoring.

### Records

| Record | Required fields | Optional fields |
|---|---|---|
| Book | `id`, `title`, `language` | `isActive` (false), `quizFilter`, `verbQuizFilter` (saved selections, see below; invalid ones are dropped), `figureSlotStart` (0; first avatar slot of the current start, ≥ 0) |
| Vocabulary | `id` (unique), `english`, `german`, `leitnerLevel` (0–5), `updatedAt`, `isDeleted`, `bookId` | the import fields, `lastReviewedAt`, `coreWord`, `highestLevel` (0–5, missing = `leitnerLevel`), `firstTypedCorrectAt` |
| Irregular verb | `id` (unique), `infinitive`, `simplePast`, `german`, `unit`, `leitnerLevel`, `updatedAt`, `isDeleted`, `bookId` | `pastParticiple`, `part`, `page`, `germanAdded`, `lastReviewedAt` |
| Practice day | `date`, `deviceId`, `roundsCompleted` (≥ 0), `dailyGoal` (≥ 1), `updatedAt`, `bookId` | – |
| Points day | `date`, `deviceId`, `updatedAt`, `bookId` | `risePoints`, `dailyBonus`, `startPoints` (0; never negative) |
| Figure | `slot` (≥ 0, unique per book), `kind`, `pointsAtStart` (≥ 0), `stage` (0–10), `startedAt`, `updatedAt`, `bookId` | `finishedAt` |
| Badge | `series`, `tier` (1–4), `reachedAt`, `bookId` | – |

- `isDeleted: true` marks an ignored entry or verb.
- Practice and points days are unique per book, date and device.
- Figure `kind`: `dragon`, `monster`, `fighter`; badge `series`: `streak`, `sure_words`. Unknown kinds and
  series (from newer app versions) are skipped, not rejected.
- Entries without a usable core word are handled after the restore as described in
  [FEATURES.md](FEATURES.md#vocabulary).

### Saved selection (`quizFilter`, `verbQuizFilter`)

A JSON object stored as a string: `unit` (`"all"`, `"none"` = without unit, or the unit number as a string),
`parts` (array; `null` = words without a part), `pageFrom`, `pageTo` (optional integers). Example:
`"{\"unit\":\"1\",\"parts\":[null,\"A\"],\"pageFrom\":150,\"pageTo\":152}"`.

### Settings

Only read by „Sicherung wiederherstellen“. Every field is optional; an invalid value is skipped and the current
value stays.

| Field | Type | Values |
|---|---|---|
| `theme` | string | `gaming_dark`, `ocean`, `sunny` |
| `vibration`, `autoSpeak` | boolean | |
| `editButtonInQuiz` | boolean | only used by the test app |
| `quizMode` | string | `vocabulary`, `verbs` |
| `verbForms` | integer | „Verbformen“: `2` or `3`; missing keeps the current value, default 2 |
| `dailyGoal` | integer | 2–5 (1 is raised to 2) |
| `reminderEnabled` | boolean | |
| `reminderMinutes` | integer | minutes after midnight, 0–1439 |
| `tipsDone` | array of strings | keys of finished tips; unknown keys are ignored |

A file may also contain the fields `pointsIntroDone`, `pointsNotice`, `sureLevelIntroDone` and `sureLevelNotice`.
They are accepted and have no effect; the app does not write them. The fields `levelCapIntroDone`, `levelCapNotice`
and `levelCapCount` are written by the app and restored with the other settings; they have no visible effect.

### Example

```json
{
  "format": "voctrainer-sync",
  "version": 5,
  "books": [
    { "id": "my-book", "title": "Mein Englischbuch", "language": "en", "isActive": true,
      "quizFilter": "{\"unit\":\"1\",\"parts\":[],\"pageFrom\":null,\"pageTo\":null}",
      "verbQuizFilter": null, "figureSlotStart": 0 }
  ],
  "entries": [
    { "english": "pet", "german": ["Haustier"], "unit": 1, "unitTitle": "Our new pet", "part": null,
      "extra": "Do you have a pet?", "page": 150, "type": "noun",
      "id": "5d0c1f7e-0b0e-4a52-9a43-0f3e2c1b7a11", "leitnerLevel": 3, "updatedAt": 1767225600000,
      "isDeleted": false, "lastReviewedAt": 1767225600000, "coreWord": "pet",
      "highestLevel": 3, "firstTypedCorrectAt": null, "bookId": "my-book" }
  ],
  "irregularVerbs": [
    { "infinitive": "(to) be", "simplePast": "was / were", "pastParticiple": null, "german": ["sein"],
      "unit": 1, "part": "A", "page": 151, "germanAdded": false,
      "id": "a1b2c3d4-0000-4000-8000-000000000001", "leitnerLevel": 1, "updatedAt": 1767225600000,
      "isDeleted": false, "lastReviewedAt": 1767225600000, "bookId": "my-book" }
  ],
  "practiceDays": [
    { "date": "2026-01-01", "deviceId": "device-1", "roundsCompleted": 2, "dailyGoal": 2,
      "updatedAt": 1767225600000, "bookId": "my-book" }
  ],
  "pointsDays": [
    { "date": "2026-01-01", "deviceId": "device-1", "risePoints": 14, "dailyBonus": 11, "startPoints": 0,
      "updatedAt": 1767225600000, "bookId": "my-book" }
  ],
  "figures": [
    { "slot": 0, "kind": "dragon", "pointsAtStart": 0, "stage": 0, "startedAt": 1767225600000,
      "finishedAt": null, "updatedAt": 1767225600000, "bookId": "my-book" }
  ],
  "badges": [],
  "appVariant": "release",
  "appVersion": "1.0.0",
  "settings": { "theme": "ocean", "vibration": false, "autoSpeak": false, "quizMode": "vocabulary",
                "verbForms": 2, "dailyGoal": 2, "reminderEnabled": true, "reminderMinutes": 1020, "tipsDone": ["selection"] }
}
```
