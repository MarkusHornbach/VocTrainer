package io.github.markushornbach.voctrainer.learning

/**
 * Common irregular verbs (base form → simple past forms). Only used to recognise real English
 * forms when generating distractors; never shown. Past participles are deliberately missing,
 * because a participle ("swum") is a wanted distractor.
 */
object IrregularVerbList {
    private const val DATA =
        "arise arose;awake awoke;be was/were;bear bore;beat beat;become became;begin began;bend bent;" +
            "bet bet;bind bound;bite bit;bleed bled;blow blew;break broke;breed bred;bring brought;" +
            "build built;burn burnt/burned;buy bought;catch caught;choose chose;cling clung;come came;" +
            "cost cost;creep crept;cut cut;deal dealt;dig dug;do did;draw drew;dream dreamt/dreamed;" +
            "drink drank;drive drove;eat ate;fall fell;feed fed;feel felt;fight fought;find found;" +
            "flee fled;fling flung;fly flew;forbid forbade;forget forgot;forgive forgave;freeze froze;" +
            "get got;give gave;go went;grind ground;grow grew;hang hung/hanged;have had;hear heard;" +
            "hide hid;hit hit;hold held;hurt hurt;keep kept;kneel knelt;know knew;lay laid;lead led;" +
            "lean leant/leaned;leap leapt/leaped;learn learnt/learned;leave left;lend lent;let let;" +
            "lie lay;light lit/lighted;lose lost;make made;mean meant;meet met;pay paid;put put;quit quit;" +
            "read read;ride rode;ring rang;rise rose;run ran;say said;see saw;seek sought;sell sold;" +
            "send sent;set set;shake shook;shine shone;shoot shot;show showed;shrink shrank;shut shut;" +
            "sing sang;sink sank;sit sat;sleep slept;slide slid;smell smelt/smelled;speak spoke;" +
            "spell spelt/spelled;spend spent;spill spilt/spilled;spin spun;spit spat;split split;" +
            "spread spread;spring sprang;stand stood;steal stole;stick stuck;sting stung;stink stank;" +
            "strike struck;swear swore;sweep swept;swim swam;swing swung;take took;teach taught;" +
            "tear tore;tell told;think thought;throw threw;understand understood;wake woke;wear wore;" +
            "weep wept;win won;wind wound;write wrote"

    /** Base form → simple past forms. */
    val pastForms: Map<String, List<String>> = DATA.split(";").associate { entry ->
        val (base, past) = entry.trim().split(" ")
        base to past.split("/")
    }

    /** All base and simple past forms of the list. */
    val allForms: Set<String> = pastForms.flatMapTo(HashSet()) { (base, past) -> past + base }

    /**
     * Words the rules can produce that must never be shown (offensive or confusing).
     * "hore" is not a word, but in a learning app for students it reads too much like the swear word;
     * "staid" is an old past form of stay, "turnt" is slang, "coke" (vowel change of cook) is a drink brand and drug slang.
     */
    val forbidden: Set<String> = setOf("cum", "rent", "hore", "staid", "turnt", "coke")
}
