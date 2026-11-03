package io.github.markushornbach.voctrainer.ui

/** The app name as in the top bar; the test app tells itself apart at a glance. */
fun appName(debug: Boolean): String = if (debug) "VocTrainer Debug" else "VocTrainer"

/** German texts of the first start: welcome, book choice and the hint after "Ohne Tipps starten". */
object FirstStartTexts {
    fun welcomeTitle(debug: Boolean): String = "Willkommen bei ${appName(debug)}!"

    const val WELCOME_LEARN = "Englisch-Wörter lernen"
    const val WELCOME_AVATAR = "Deinen Avatar wachsen lassen"
    const val START = "Los geht's"
    const val WITHOUT_TIPS = "Ohne Tipps starten"

    const val BOOK_TITLE = "Mit welchem Buch lernst du?"
    const val OWN_BOOK = "Mein Buch laden"

    /** The example book: the name of the help row, and the small text link on the book choice. */
    const val EXAMPLE_BOOK = "Beispielbuch ausprobieren"
    const val EXAMPLE_BOOK_LINK = "Keine Vokabeldatei? $EXAMPLE_BOOK"

    const val FILE_INVALID = "Diese Datei passt nicht. Wähle eine Vokabeldatei."

    /** After choosing the example book, before the avatar choice; the path as in the app (⚙️ = settings). */
    const val EXAMPLE_HINT = "Dein echtes Buch kannst du später laden: ⚙️ → Vokabeln verwalten"
    const val EXAMPLE_HINT_OK = "OK"

    const val TIPS_OFF_HINT = "Tipps kannst du jederzeit einschalten: ? → ${TipTexts.SHOW_AGAIN}"
    const val TIPS_OFF_OK = "OK"

    /** Test app only ("Erster Start simulieren"), below the avatar choice when the book has an avatar. */
    const val SIMULATED_CHOICE = "🧪 Test: Wahl wird nicht gespeichert"
}
