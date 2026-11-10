package app.tellycine.core.sports

import java.util.Locale

/**
 * A competition as the Sports Hub names it ("Premier League", "Champions League"), and where it's
 * played ("England", "Europe"), when that's a country or region. Tours and events in sports such as
 * tennis and golf have no [region].
 */
data class Competition(val name: String, val region: String?)

/**
 * The competitions the Sports Hub knows, UK first, for filtering a sport by country and league.
 * Guides name them in many ways ("EPL", "The Emirates FA Cup", "Futbol Ligue 1", "UEFA Champions
 * League en 60"): the longest name of one found in the text is the one it's about.
 */
object Competitions {
    private class Entry(val sport: Sport, val name: String, val region: String?, val women: Boolean, val keys: List<String>)

    // Before the competitions, which are read with them.
    private val DROPPED = Regex("['’‘`.]")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    private val entries = ArrayList<Entry>()

    private fun add(sport: Sport, region: String?, name: String, vararg aliases: String, women: Boolean = false) {
        entries += Entry(sport, name, region, women, (listOf(name) + aliases).map(::keyOf).distinct())
    }

    private fun football(region: String, name: String, vararg aliases: String, women: Boolean = false) =
        add(Sport.FOOTBALL, region, name, *aliases, women = women)

    init {
        football("England", "Premier League", "EPL", "English Premier League", "Barclays Premier League")
        football("England", "Championship", "EFL Championship", "Sky Bet Championship", "English Championship")
        football("England", "League One", "EFL League One", "Sky Bet League One")
        football("England", "League Two", "EFL League Two", "Sky Bet League Two")
        football("England", "FA Cup", "Emirates FA Cup", "The FA Cup", "English FA Cup")
        football("England", "EFL Cup", "Carabao Cup", "League Cup", "English League Cup")
        football("England", "EFL Trophy", "Vertu Trophy", "Bristol Street Motors Trophy", "Papa Johns Trophy")
        football("England", "EFL", "English Football League", "Sky Bet EFL")
        football("England", "National League", "Vanarama National League")
        football("England", "Community Shield", "FA Community Shield")
        football("England", "Women's Super League", "WSL", "Barclays WSL", "Barclays Women's Super League", "FA WSL", women = true)
        football("England", "Women's Championship", "WSL 2", "Barclays Women's Championship", women = true)
        football("Scotland", "Scottish Premiership", "SPFL", "SPFL Premiership", "Premiership", "William Hill Premiership", "cinch Premiership", "Scottish Premier League")
        football("Scotland", "Scottish Championship", "SPFL Championship", "William Hill Championship")
        football("Scotland", "Scottish League One", "SPFL League One")
        football("Scotland", "Scottish League Two", "SPFL League Two")
        football("Scotland", "Scottish Cup", "William Hill Scottish Cup")
        football("Scotland", "Scottish League Cup", "Premier Sports Cup", "Viaplay Cup")
        football("Wales", "Cymru Premier", "JD Cymru Premier", "Welsh Premier League")
        football("Wales", "Welsh Cup", "JD Welsh Cup")
        football("Northern Ireland", "NIFL Premiership", "Irish Premiership", "Sports Direct Premiership", "Danske Bank Premiership")
        football("Northern Ireland", "Irish Cup")
        football("Ireland", "League of Ireland", "SSE Airtricity League", "League of Ireland Premier Division", "LOI")
        football("Ireland", "FAI Cup")
        football("Europe", "Champions League", "UEFA Champions League", "UCL")
        football("Europe", "Europa League", "UEFA Europa League", "UEL")
        football("Europe", "Conference League", "UEFA Conference League", "Europa Conference League", "UECL")
        football("Europe", "Super Cup", "UEFA Super Cup")
        football("Europe", "Nations League", "UEFA Nations League")
        football("Europe", "Euros", "Euro", "UEFA Euro", "European Championship", "European Championships", "Euro 2028", "Women's Euro", "Women's Euros")
        football("Europe", "Euro Qualifiers", "European Qualifiers", "Euro Qualifying", "Euro 2028 Qualifiers")
        football("International", "World Cup", "FIFA World Cup", "World Cup 2026", "Women's World Cup")
        football("International", "World Cup Qualifiers", "FIFA World Cup Qualifiers", "World Cup Qualifying", "WC Qualifiers")
        football("International", "Club World Cup", "FIFA Club World Cup")
        football("International", "International Friendlies", "International Friendly", "Friendly International", "International Soccer Friendlies")
        football("Spain", "LaLiga", "La Liga", "LaLiga EA Sports", "Spanish La Liga", "LaLiga Santander", "Spanish Primera Division")
        football("Spain", "LaLiga 2", "La Liga 2", "LaLiga Hypermotion", "Segunda Division")
        football("Spain", "Copa del Rey")
        football("Italy", "Serie A", "Italian Serie A", "Serie A TIM", "Serie A Enilive")
        football("Italy", "Serie B", "Italian Serie B", "Serie BKT")
        football("Italy", "Coppa Italia")
        football("Germany", "Bundesliga", "German Bundesliga")
        football("Germany", "2. Bundesliga", "2 Bundesliga", "Bundesliga 2", "2nd Bundesliga")
        football("Germany", "DFB-Pokal", "DFB Pokal", "German Cup")
        football("France", "Ligue 1", "French Ligue 1", "Ligue 1 McDonald's", "Ligue 1 Uber Eats")
        football("France", "Ligue 2", "French Ligue 2")
        football("France", "Coupe de France")
        football("Netherlands", "Eredivisie", "Dutch Eredivisie")
        football("Portugal", "Primeira Liga", "Liga Portugal", "Liga Portugal Betclic", "Portuguese Primeira Liga", "Futbol Portugues Primeira Liga")
        football("Turkey", "Süper Lig", "Super Lig", "Turkish Super Lig", "Trendyol Super Lig")
        football("Saudi Arabia", "Saudi Pro League", "Roshn Saudi League", "Saudi Professional League")
        football("USA", "MLS", "Major League Soccer")
        football("USA", "NWSL", "National Women's Soccer League", women = true)
        football("USA", "US Open Cup", "U.S. Open Cup", "Lamar Hunt US Open Cup")
        football("USA", "USL Championship", "USL")
        football("North America", "Leagues Cup")
        football("North America", "CONCACAF Champions Cup", "Concacaf Champions Cup")
        football("Mexico", "Liga MX")
        football("Canada", "Canadian Premier League", "CPL")
        football("Canada", "Canadian Championship")
        football("Brazil", "Brasileirão", "Brasileirao", "Brazilian Serie A", "Campeonato Brasileiro")
        football("Argentina", "Liga Profesional", "Argentine Primera Division", "Argentine Liga Profesional")
        football("South America", "Copa Libertadores", "CONMEBOL Libertadores", "Libertadores")
        football("South America", "Copa Sudamericana", "CONMEBOL Sudamericana", "Sudamericana")
        football("South America", "Copa América", "Copa America")
        football("Australia", "A-League", "A League", "A Leagues", "Isuzu UTE A-League")
        football("Japan", "J1 League", "J League", "J.League")
        football("Asia", "AFC Champions League", "AFC Champions League Elite")

        add(Sport.TENNIS, null, "Australian Open")
        add(Sport.TENNIS, null, "French Open", "Roland Garros", "Roland-Garros")
        add(Sport.TENNIS, null, "Wimbledon")
        add(Sport.TENNIS, null, "US Open")
        add(Sport.TENNIS, null, "ATP Finals", "Nitto ATP Finals")
        add(Sport.TENNIS, null, "WTA Finals")
        add(Sport.TENNIS, null, "ATP Tour", "ATP", "ATP Masters 1000", "ATP 500", "ATP 250")
        add(Sport.TENNIS, null, "WTA Tour", "WTA", "WTA 1000", "WTA 500", "WTA 250")
        add(Sport.TENNIS, null, "Davis Cup")
        add(Sport.TENNIS, null, "Billie Jean King Cup", "BJK Cup")
        add(Sport.TENNIS, null, "United Cup")
        add(Sport.TENNIS, null, "Laver Cup")

        add(Sport.GOLF, null, "The Masters", "Masters", "US Masters")
        add(Sport.GOLF, null, "PGA Championship")
        add(Sport.GOLF, null, "US Open", "U.S. Open")
        add(Sport.GOLF, null, "The Open", "Open Championship", "The Open Championship")
        add(Sport.GOLF, null, "Ryder Cup")
        add(Sport.GOLF, null, "Solheim Cup")
        add(Sport.GOLF, null, "PGA Tour")
        add(Sport.GOLF, null, "DP World Tour", "European Tour")
        add(Sport.GOLF, null, "LIV Golf", "LIV")
        add(Sport.GOLF, null, "LPGA Tour", "LPGA")
        add(Sport.GOLF, null, "PGA Tour Champions", "Champions Tour")

        add(Sport.DARTS, null, "World Championship", "World Darts Championship", "PDC World Championship", "PDC World Darts Championship")
        add(Sport.DARTS, null, "Premier League", "Premier League Darts", "BetMGM Premier League")
        add(Sport.DARTS, null, "World Matchplay")
        add(Sport.DARTS, null, "Grand Slam of Darts", "Grand Slam")
        add(Sport.DARTS, null, "World Grand Prix")
        add(Sport.DARTS, null, "Players Championship", "Players Championship Finals")
        add(Sport.DARTS, null, "UK Open")
        add(Sport.DARTS, null, "European Championship")
        add(Sport.DARTS, null, "World Series", "World Series of Darts")
        add(Sport.DARTS, null, "World Cup of Darts", "World Cup")
        add(Sport.DARTS, null, "Masters")

        add(Sport.SNOOKER, null, "World Championship", "World Snooker Championship")
        add(Sport.SNOOKER, null, "UK Championship")
        add(Sport.SNOOKER, null, "The Masters", "Masters")
        add(Sport.SNOOKER, null, "Champion of Champions")
        add(Sport.SNOOKER, null, "Shoot Out", "Snooker Shoot Out")
        add(Sport.SNOOKER, null, "Welsh Open")
        add(Sport.SNOOKER, null, "Scottish Open")
        add(Sport.SNOOKER, null, "English Open")
        add(Sport.SNOOKER, null, "Northern Ireland Open")
        add(Sport.SNOOKER, null, "German Masters")
        add(Sport.SNOOKER, null, "World Grand Prix")
        add(Sport.SNOOKER, null, "Players Championship")
        add(Sport.SNOOKER, null, "Tour Championship")
        add(Sport.SNOOKER, null, "International Championship")
        add(Sport.SNOOKER, null, "British Open")
        add(Sport.SNOOKER, null, "Championship League")
        add(Sport.SNOOKER, null, "Shanghai Masters")
        add(Sport.SNOOKER, null, "Saudi Arabia Masters")

        add(Sport.OTHER, "USA", "NFL")
        add(Sport.OTHER, "USA", "NBA")
        add(Sport.OTHER, "USA", "NHL")
        add(Sport.OTHER, "USA", "MLB")
        add(Sport.OTHER, "USA", "WNBA", women = true)
        add(Sport.OTHER, "USA", "College Football", "NCAA Football", "NCAAF", "College Football Playoff")
        add(Sport.OTHER, "USA", "College Basketball", "NCAA Basketball", "NCAAB", "Men's College Basketball")
        add(Sport.OTHER, null, "UFC")
        add(Sport.OTHER, null, "Six Nations", "Guinness Six Nations", "6 Nations")
        add(Sport.OTHER, null, "Autumn Nations Series", "Quilter Nations Series", "Autumn Internationals")
        add(Sport.OTHER, "England", "Premiership Rugby", "Gallagher Premiership", "Gallagher PREM", "PREM Rugby")
        add(Sport.OTHER, null, "United Rugby Championship", "URC")
        add(Sport.OTHER, "Europe", "Champions Cup", "Investec Champions Cup", "European Champions Cup")
        add(Sport.OTHER, null, "Super League", "Betfred Super League")
        add(Sport.OTHER, null, "IPL", "Indian Premier League")
        add(Sport.OTHER, null, "The Hundred")
        add(Sport.OTHER, null, "Big Bash", "Big Bash League", "BBL")
        add(Sport.OTHER, null, "County Championship", "Rothesay County Championship")
    }

    /** Where competitions whose names start with one of these are played: "Austrian Bundesliga" is Austria's. */
    private val PLACES: List<Pair<String, String>> = listOf(
        "england" to "England", "english" to "England", "scotland" to "Scotland", "scottish" to "Scotland",
        "wales" to "Wales", "welsh" to "Wales", "northern ireland" to "Northern Ireland", "ireland" to "Ireland", "irish" to "Ireland",
        "uefa" to "Europe", "european" to "Europe", "europe" to "Europe", "fifa" to "International", "international" to "International",
        "spain" to "Spain", "spanish" to "Spain", "italy" to "Italy", "italian" to "Italy", "germany" to "Germany", "german" to "Germany",
        "france" to "France", "french" to "France", "netherlands" to "Netherlands", "dutch" to "Netherlands",
        "portugal" to "Portugal", "portuguese" to "Portugal", "portugues" to "Portugal", "turkey" to "Turkey", "turkish" to "Turkey",
        "saudi" to "Saudi Arabia", "usa" to "USA", "american" to "USA", "mexico" to "Mexico", "mexican" to "Mexico",
        "canada" to "Canada", "canadian" to "Canada", "brazil" to "Brazil", "brazilian" to "Brazil", "argentina" to "Argentina",
        "argentine" to "Argentina", "argentino" to "Argentina", "australia" to "Australia", "australian" to "Australia",
        "japan" to "Japan", "japanese" to "Japan", "belgium" to "Belgium", "belgian" to "Belgium", "greece" to "Greece", "greek" to "Greece",
        "austria" to "Austria", "austrian" to "Austria", "switzerland" to "Switzerland", "swiss" to "Switzerland",
        "denmark" to "Denmark", "danish" to "Denmark", "sweden" to "Sweden", "swedish" to "Sweden", "norway" to "Norway", "norwegian" to "Norway",
        "poland" to "Poland", "polish" to "Poland", "croatia" to "Croatia", "croatian" to "Croatia", "serbia" to "Serbia", "serbian" to "Serbia",
        "china" to "China", "chinese" to "China", "korea" to "South Korea", "korean" to "South Korea", "india" to "India", "indian" to "India",
        "egypt" to "Egypt", "egyptian" to "Egypt", "colombia" to "Colombia", "colombian" to "Colombia", "chile" to "Chile", "chilean" to "Chile",
        "uruguay" to "Uruguay", "peru" to "Peru", "peruvian" to "Peru", "qatar" to "Qatar", "uae" to "UAE",
        "ukraine" to "Ukraine", "ukrainian" to "Ukraine", "russia" to "Russia", "russian" to "Russia", "romania" to "Romania",
        "romanian" to "Romania", "hungary" to "Hungary", "hungarian" to "Hungary", "czech" to "Czechia", "slovak" to "Slovakia",
        "bulgaria" to "Bulgaria", "bulgarian" to "Bulgaria", "israel" to "Israel", "israeli" to "Israel", "finland" to "Finland",
        "finnish" to "Finland", "iceland" to "Iceland", "icelandic" to "Iceland", "cyprus" to "Cyprus", "cypriot" to "Cyprus",
        "slovenia" to "Slovenia", "slovenian" to "Slovenia", "bosnia" to "Bosnia", "bosnian" to "Bosnia", "malta" to "Malta",
        "maltese" to "Malta", "kazakhstan" to "Kazakhstan", "georgia" to "Georgia", "georgian" to "Georgia", "albania" to "Albania",
        "albanian" to "Albania", "morocco" to "Morocco", "moroccan" to "Morocco", "algeria" to "Algeria", "algerian" to "Algeria",
        "tunisia" to "Tunisia", "tunisian" to "Tunisia", "nigeria" to "Nigeria", "nigerian" to "Nigeria", "ghana" to "Ghana",
        "south africa" to "South Africa", "south african" to "South Africa", "iran" to "Iran", "iranian" to "Iran",
        "ecuador" to "Ecuador", "ecuadorian" to "Ecuador", "venezuela" to "Venezuela", "venezuelan" to "Venezuela",
        "paraguay" to "Paraguay", "paraguayan" to "Paraguay", "bolivia" to "Bolivia", "bolivian" to "Bolivia",
        "costa rica" to "Costa Rica", "honduras" to "Honduras", "jamaica" to "Jamaica", "thailand" to "Thailand", "thai" to "Thailand",
        "indonesia" to "Indonesia", "indonesian" to "Indonesia", "malaysia" to "Malaysia", "malaysian" to "Malaysia",
        "vietnam" to "Vietnam", "vietnamese" to "Vietnam", "new zealand" to "New Zealand",
    ).map { (word, place) -> " $word " to place }

    /** Regions shown first, in this order; the rest by how much sport is on in them. */
    val FIRST_REGIONS: List<String> = listOf("England", "Scotland", "Wales", "Northern Ireland", "Ireland", "Europe", "International")

    private val WOMEN = listOf(" women ", " womens ", " ladies ", " femenino ", " feminine ", " feminin ", " femminile ", " frauen ", " wsl ", " nwsl ")
    private val YOUTH = Regex(" (?:u|under) ?(\\d{2}) ")
    private val CONDENSED = Regex("\\s+(?:en|in)\\s+60\\s*$", RegexOption.IGNORE_CASE)

    /** The competition [text] names (a programme's competition or title, or an event channel's name), if it's one the hub knows. */
    fun find(text: String, sport: Sport): Competition? {
        val key = keyOf(text)
        var best: Entry? = null
        var length = 0
        for (e in entries) {
            if (e.sport != sport) continue
            for (k in e.keys) if (k.length > length && key.contains(k)) {
                best = e
                length = k.length
            }
        }
        val entry = best ?: return null
        // "Austrian Bundesliga" isn't the Bundesliga, nor "Ukrainian Premier League" the Premier League.
        val place = placeOf(key)
        if (place != null && entry.region != null && place != entry.region && !(entry.region == "Europe" && place == "International")) {
            return null
        }
        return Competition(named(entry.name, key, entry.women), entry.region)
    }

    /** Where [c] comes in the catalogue, which goes biggest first in each country; after them all, if it isn't there. */
    fun rank(c: Competition): Int {
        val name = c.name.removePrefix("Women's ").replace(YOUTH_SUFFIX, "")
        val i = entries.indexOfFirst { it.region == c.region && (it.name == name || it.name == c.name) }
        return if (i >= 0) i else entries.size
    }

    private val YOUTH_SUFFIX = Regex(" U-\\d{2}$")

    /**
     * [raw], a competition as a guide gives it, as the hub shows it: one it knows by its usual name;
     * one it doesn't, as it is, if where it's played is plain ("Austrian Bundesliga"). Otherwise null:
     * a guide's words before the sides aren't always a competition.
     */
    fun of(raw: String, sport: Sport): Competition? {
        find(raw, sport)?.let { return it }
        if (sport != Sport.FOOTBALL) return null
        val name = raw.replace(CONDENSED, "").trim()
        val place = placeOf(keyOf(name)) ?: return null
        return Competition(name, place)
    }

    /**
     * " lower case words ", without accents or punctuation; unlike [SportNames.key], with every
     * word: the AFC Champions League isn't the Champions League.
     */
    private fun keyOf(text: String): String {
        val plain = SportNames.plain(text).lowercase(Locale.ROOT).replace("&", " and ").replace(DROPPED, "")
        return " " + plain.split(NOT_WORD).filter { it.isNotEmpty() }.joinToString(" ") + " "
    }


    private fun placeOf(key: String): String? = PLACES.firstOrNull { key.contains(it.first) }?.second

    /** Women's and youth competitions under their own names: "Futbol Femenino UEFA Champions League" is the Women's Champions League. */
    private fun named(name: String, key: String, women: Boolean): String {
        var out = name
        if (!women && WOMEN.any { key.contains(it) } && !name.startsWith("Women's")) out = "Women's $out"
        YOUTH.find(key)?.let { out = "$out U-${it.groupValues[1]}" }
        return out
    }
}
