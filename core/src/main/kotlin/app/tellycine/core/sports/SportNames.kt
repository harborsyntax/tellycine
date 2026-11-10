package app.tellycine.core.sports

import java.text.Normalizer
import java.util.Locale

/** Names compared as the Sports Hub compares them. */
object SportNames {
    private val marks = Regex("\\p{M}+")
    private val apostrophes = Regex("['’‘`]")
    private val other = Regex("[^\\p{L}\\p{N}]+")

    /** Words that only say what kind of club it is: "Arsenal FC" is Arsenal. */
    private val clubWords = setOf("fc", "afc", "cf", "sc")

    /**
     * [name] in lower case without accents, punctuation or club words, with single spaces:
     * "Atlético Madrid" and "Atletico Madrid" are both "atletico madrid", "Man Utd" is "man united".
     */
    fun key(name: String): String {
        val plain = plain(name)
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace("ß", "ss")
        val words = other.replace(apostrophes.replace(plain, "").replace(".", ""), " ").trim().split(' ').filter { it.isNotEmpty() }
        return words
            .map { if (it == "utd") "united" else it }
            .let { list -> if (list.size > 1) list.filterNot { it in clubWords } else list }
            .joinToString(" ")
    }

    /**
     * [name] with look-alike letters as plain ones and without accents: "ᴴᴰ" is "HD", "ᴸᶦᵛᵉ" is
     * "Live", "Atlético" is "Atletico".
     */
    fun plain(name: String): String {
        if (name.all { it.code < 128 }) return name
        val decomposed = marks.replace(Normalizer.normalize(name, Normalizer.Form.NFKD), "")
        // Small capitals, which decomposing leaves as they are ("ᶦ" is "ɪ").
        return if (decomposed.none { it in SMALL_CAPITALS }) decomposed else decomposed.map { SMALL_CAPITALS[it] ?: it }.joinToString("")
    }

    private val SMALL_CAPITALS: Map<Char, Char> = mapOf(
        'ᴀ' to 'a', 'ʙ' to 'b', 'ᴄ' to 'c', 'ᴅ' to 'd', 'ᴇ' to 'e', 'ꜰ' to 'f', 'ɢ' to 'g', 'ʜ' to 'h', 'ɪ' to 'i', 'ᴊ' to 'j',
        'ᴋ' to 'k', 'ʟ' to 'l', 'ᴍ' to 'm', 'ɴ' to 'n', 'ᴏ' to 'o', 'ᴘ' to 'p', 'ʀ' to 'r', 'ꜱ' to 's', 'ᴛ' to 't', 'ᴜ' to 'u',
        'ᴠ' to 'v', 'ᴡ' to 'w', 'ʏ' to 'y', 'ᴢ' to 'z',
    )

    /** Up to three letters for a side without a code of its own: "Luke Littler" is "LL", "Inter" is "INT". */
    fun monogram(name: String): String {
        val words = name.split(' ', '-').filter { w -> w.any { it.isLetter() } }
        return when {
            words.isEmpty() -> name.take(2).uppercase(Locale.ROOT)
            words.size == 1 -> words[0].filter { it.isLetter() }.take(3).uppercase(Locale.ROOT)
            else -> words.take(3).mapNotNull { w -> w.firstOrNull { it.isLetter() } }.joinToString("").uppercase(Locale.ROOT)
        }
    }
}

/**
 * A team or a player the Sports Hub knows: for suggestions while choosing what to follow, to know
 * "Man Utd" is Manchester United, and for a [code] and [colour] to show it by (no crests: those
 * belong to the clubs).
 */
data class SportFigure(
    val name: String,
    val sport: Sport,
    /** Three letters it's known by, for a team. */
    val code: String? = null,
    /** Its main colour, as 0xRRGGBB. */
    val colour: Int? = null,
    /** Other names it goes by in guides. */
    val aliases: List<String> = emptyList(),
) {
    val keys: List<String> = (listOf(name) + aliases).map(SportNames::key).distinct()

    /**
     * Teams and drivers in motor racing take part in every race, so following one follows its
     * sport's races.
     */
    val followsWholeSport: Boolean get() = sport == Sport.F1 || sport == Sport.MOTOGP
}

/** The teams and players the Sports Hub knows, UK first. */
object SportCatalogue {
    private fun club(name: String, code: String, colour: Int, vararg aliases: String) =
        SportFigure(name, Sport.FOOTBALL, code, colour, aliases.toList())

    private fun player(sport: Sport, name: String, vararg aliases: String) = SportFigure(name, sport, aliases = aliases.toList())

    private fun team(sport: Sport, name: String, code: String, colour: Int, vararg aliases: String) =
        SportFigure(name, sport, code, colour, aliases.toList())

    val figures: List<SportFigure> = listOf(
        // Football: the Premier League, the EFL and Scotland first, then Europe and national sides.
        club("Arsenal", "ARS", 0xDB0007, "Arsenal Women"),
        club("Aston Villa", "AVL", 0x670E36, "Villa"),
        club("Bournemouth", "BOU", 0xDA291C, "AFC Bournemouth"),
        club("Brentford", "BRE", 0xE30613),
        club("Brighton & Hove Albion", "BHA", 0x0057B8, "Brighton", "Brighton and Hove Albion"),
        club("Burnley", "BUR", 0x6C1D45),
        club("Chelsea", "CHE", 0x034694, "Chelsea Women"),
        club("Crystal Palace", "CRY", 0x1B458F, "Palace"),
        club("Everton", "EVE", 0x003399),
        club("Fulham", "FUL", 0xF5F5F5),
        club("Leeds United", "LEE", 0xF5F5F5, "Leeds"),
        club("Liverpool", "LIV", 0xC8102E, "Liverpool Women"),
        club("Manchester City", "MCI", 0x6CABDD, "Man City", "Man. City", "Manchester City Women"),
        club("Manchester United", "MUN", 0xDA291C, "Man United", "Man Utd", "Manchester Utd", "Man. Utd"),
        club("Newcastle United", "NEW", 0x241F20, "Newcastle", "Newcastle Utd"),
        club("Nottingham Forest", "NFO", 0xDD0000, "Nottm Forest", "Nott'm Forest", "Notts Forest"),
        club("Sunderland", "SUN", 0xEB172B),
        club("Tottenham Hotspur", "TOT", 0x132257, "Tottenham", "Spurs"),
        club("West Ham United", "WHU", 0x7A263A, "West Ham", "West Ham Utd"),
        club("Wolverhampton Wanderers", "WOL", 0xFDB913, "Wolves", "Wolverhampton"),
        club("Ipswich Town", "IPS", 0x0044A9, "Ipswich"),
        club("Leicester City", "LEI", 0x003090, "Leicester"),
        club("Southampton", "SOU", 0xD71920),
        club("Sheffield United", "SHU", 0xEE2737, "Sheff Utd", "Sheffield Utd"),
        club("Sheffield Wednesday", "SHW", 0x0E2C8E, "Sheff Wed"),
        club("Middlesbrough", "MID", 0xE21A23, "Boro"),
        club("Norwich City", "NOR", 0x00A650, "Norwich"),
        club("Coventry City", "COV", 0x59CBE8, "Coventry"),
        club("West Bromwich Albion", "WBA", 0x122F67, "West Brom"),
        club("Watford", "WAT", 0xFBEE23),
        club("Stoke City", "STK", 0xE03A3E, "Stoke"),
        club("Hull City", "HUL", 0xF18A01, "Hull"),
        club("Derby County", "DER", 0xF5F5F5, "Derby"),
        club("Queens Park Rangers", "QPR", 0x1D5BA4, "QPR"),
        club("Millwall", "MIL", 0x001D5E),
        club("Blackburn Rovers", "BLB", 0x009EE0, "Blackburn"),
        club("Preston North End", "PNE", 0xF5F5F5, "Preston"),
        club("Bristol City", "BRC", 0xE21C38),
        club("Swansea City", "SWA", 0xF5F5F5, "Swansea"),
        club("Cardiff City", "CAR", 0x0070B5, "Cardiff"),
        club("Plymouth Argyle", "PLY", 0x00573F, "Plymouth"),
        club("Portsmouth", "POR", 0x001489, "Pompey"),
        club("Oxford United", "OXF", 0xFFDD00, "Oxford Utd"),
        club("Luton Town", "LUT", 0xF78F1E, "Luton"),
        club("Wrexham", "WRX", 0xD71920),
        club("Birmingham City", "BIR", 0x0000FF, "Birmingham"),
        club("Charlton Athletic", "CHA", 0xD4021D, "Charlton"),
        club("Celtic", "CEL", 0x018749),
        club("Rangers", "RAN", 0x1B458F, "Glasgow Rangers"),
        club("Aberdeen", "ABE", 0xE2001A),
        club("Heart of Midlothian", "HEA", 0x8B0000, "Hearts"),
        club("Hibernian", "HIB", 0x005000, "Hibs"),
        club("Real Madrid", "RMA", 0xF5F5F5),
        club("Barcelona", "BAR", 0xA50044, "FC Barcelona", "Barca", "Barça"),
        club("Atletico Madrid", "ATM", 0xCB3524, "Atlético Madrid", "Atletico", "Atlético de Madrid"),
        club("Bayern Munich", "BAY", 0xDC052D, "Bayern", "Bayern München", "Bayern Munchen", "FC Bayern"),
        club("Borussia Dortmund", "BVB", 0xFDE100, "Dortmund"),
        club("Bayer Leverkusen", "LEV", 0xE32221, "Leverkusen"),
        club("Paris Saint-Germain", "PSG", 0x004170, "PSG", "Paris St-Germain", "Paris SG"),
        club("Marseille", "OM", 0x2FAEE0, "Olympique de Marseille"),
        club("Juventus", "JUV", 0xF5F5F5, "Juve"),
        club("Inter", "INT", 0x0068A8, "Inter Milan", "Internazionale"),
        club("AC Milan", "ACM", 0xFB090B, "Milan"),
        club("Napoli", "NAP", 0x12A0D7),
        club("Roma", "ROM", 0x8E1F2F, "AS Roma"),
        club("Ajax", "AJA", 0xD2122E),
        club("PSV", "PSV", 0xED1C24, "PSV Eindhoven"),
        club("Benfica", "BEN", 0xE83030),
        club("Porto", "FCP", 0x003893, "FC Porto"),
        club("Sporting CP", "SCP", 0x00805F, "Sporting Lisbon", "Sporting"),
        club("Galatasaray", "GAL", 0xA90432),
        club("England", "ENG", 0xF5F5F5, "England Women", "Lionesses"),
        club("Scotland", "SCO", 0x0065BF),
        club("Wales", "WAL", 0xC8102E),
        club("Northern Ireland", "NIR", 0x00843D),
        club("Republic of Ireland", "IRL", 0x169B62, "Ireland", "Rep. of Ireland", "Rep of Ireland"),
        club("France", "FRA", 0x002395),
        club("Germany", "GER", 0x1F1F1F),
        club("Spain", "ESP", 0xAA151B),
        club("Italy", "ITA", 0x0066CC),
        club("Netherlands", "NED", 0xFF6200, "Holland"),
        club("Portugal", "POR", 0xE42518),
        club("Belgium", "BEL", 0xE30613),
        club("Croatia", "CRO", 0xFF0000),
        club("Serbia", "SRB", 0xC6363C),
        club("Denmark", "DEN", 0xC60C30),
        club("Brazil", "BRA", 0xFFDF00),
        club("Argentina", "ARG", 0x75AADB),
        club("USA", "USA", 0x002868, "United States"),

        // Formula 1: the teams, then the drivers.
        team(Sport.F1, "McLaren", "McL", 0xFF8000),
        team(Sport.F1, "Ferrari", "FER", 0xDC0000, "Scuderia Ferrari"),
        team(Sport.F1, "Mercedes", "MER", 0x00D2BE),
        team(Sport.F1, "Red Bull", "RBR", 0x3671C6, "Red Bull Racing", "Oracle Red Bull Racing"),
        team(Sport.F1, "Aston Martin", "AMR", 0x229971),
        team(Sport.F1, "Williams", "WIL", 0x1868DB),
        team(Sport.F1, "Alpine", "ALP", 0x0093CC),
        team(Sport.F1, "Haas", "HAA", 0xB6BABD),
        team(Sport.F1, "Racing Bulls", "RB", 0x6692FF, "Visa Cash App RB"),
        team(Sport.F1, "Audi", "AUD", 0xBB0A30, "Sauber"),
        team(Sport.F1, "Cadillac", "CAD", 0x2B2B2B),
        player(Sport.F1, "Lando Norris", "Norris"),
        player(Sport.F1, "Oscar Piastri", "Piastri"),
        player(Sport.F1, "Max Verstappen", "Verstappen"),
        player(Sport.F1, "Lewis Hamilton", "Hamilton"),
        player(Sport.F1, "Charles Leclerc", "Leclerc"),
        player(Sport.F1, "George Russell", "Russell"),
        player(Sport.F1, "Kimi Antonelli", "Andrea Kimi Antonelli", "Antonelli"),
        player(Sport.F1, "Fernando Alonso", "Alonso"),
        player(Sport.F1, "Lance Stroll", "Stroll"),
        player(Sport.F1, "Alex Albon", "Alexander Albon", "Albon"),
        player(Sport.F1, "Carlos Sainz", "Sainz"),
        player(Sport.F1, "Pierre Gasly", "Gasly"),
        player(Sport.F1, "Franco Colapinto", "Colapinto"),
        player(Sport.F1, "Esteban Ocon", "Ocon"),
        player(Sport.F1, "Oliver Bearman", "Ollie Bearman", "Bearman"),
        player(Sport.F1, "Liam Lawson", "Lawson"),
        player(Sport.F1, "Isack Hadjar", "Hadjar"),
        player(Sport.F1, "Arvid Lindblad", "Lindblad"),
        player(Sport.F1, "Nico Hulkenberg", "Nico Hülkenberg", "Hulkenberg"),
        player(Sport.F1, "Gabriel Bortoleto", "Bortoleto"),
        player(Sport.F1, "Sergio Perez", "Sergio Pérez", "Perez"),
        player(Sport.F1, "Valtteri Bottas", "Bottas"),

        // MotoGP.
        team(Sport.MOTOGP, "Ducati", "DUC", 0xCC0000, "Ducati Lenovo"),
        team(Sport.MOTOGP, "Aprilia", "APR", 0x41BFFF),
        team(Sport.MOTOGP, "KTM", "KTM", 0xFF6600, "Red Bull KTM"),
        team(Sport.MOTOGP, "Yamaha", "YAM", 0x0A2CC4, "Monster Energy Yamaha"),
        team(Sport.MOTOGP, "Honda", "HON", 0xE40521, "Repsol Honda", "Honda HRC"),
        player(Sport.MOTOGP, "Marc Marquez", "Marc Márquez"),
        player(Sport.MOTOGP, "Alex Marquez", "Álex Márquez"),
        player(Sport.MOTOGP, "Francesco Bagnaia", "Pecco Bagnaia", "Bagnaia"),
        player(Sport.MOTOGP, "Marco Bezzecchi", "Bezzecchi"),
        player(Sport.MOTOGP, "Jorge Martin", "Jorge Martín"),
        player(Sport.MOTOGP, "Pedro Acosta", "Acosta"),
        player(Sport.MOTOGP, "Fabio Di Giannantonio", "Di Giannantonio"),
        player(Sport.MOTOGP, "Fabio Quartararo", "Quartararo"),
        player(Sport.MOTOGP, "Brad Binder", "Binder"),
        player(Sport.MOTOGP, "Fermin Aldeguer", "Fermín Aldeguer", "Aldeguer"),
        player(Sport.MOTOGP, "Toprak Razgatlioglu", "Razgatlioglu"),
        player(Sport.MOTOGP, "Johann Zarco", "Zarco"),
        player(Sport.MOTOGP, "Jack Miller"),

        // Golf.
        player(Sport.GOLF, "Rory McIlroy", "McIlroy"),
        player(Sport.GOLF, "Scottie Scheffler", "Scheffler"),
        player(Sport.GOLF, "Tommy Fleetwood", "Fleetwood"),
        player(Sport.GOLF, "Jon Rahm", "Rahm"),
        player(Sport.GOLF, "Xander Schauffele", "Schauffele"),
        player(Sport.GOLF, "Matt Fitzpatrick", "Fitzpatrick"),
        player(Sport.GOLF, "Justin Rose"),
        player(Sport.GOLF, "Shane Lowry", "Lowry"),
        player(Sport.GOLF, "Tyrrell Hatton", "Hatton"),
        player(Sport.GOLF, "Viktor Hovland", "Hovland"),
        player(Sport.GOLF, "Ludvig Aberg", "Ludvig Åberg", "Aberg"),
        player(Sport.GOLF, "Robert MacIntyre", "Bob MacIntyre", "MacIntyre"),
        player(Sport.GOLF, "Collin Morikawa", "Morikawa"),
        player(Sport.GOLF, "Bryson DeChambeau", "DeChambeau"),
        player(Sport.GOLF, "Brooks Koepka", "Koepka"),
        player(Sport.GOLF, "Aaron Rai"),
        player(Sport.GOLF, "Tiger Woods"),
        player(Sport.GOLF, "Nelly Korda", "Korda"),
        player(Sport.GOLF, "Charley Hull"),
        player(Sport.GOLF, "Lottie Woad", "Woad"),

        // Tennis.
        player(Sport.TENNIS, "Jannik Sinner", "Sinner"),
        player(Sport.TENNIS, "Carlos Alcaraz", "Alcaraz"),
        player(Sport.TENNIS, "Novak Djokovic", "Djokovic"),
        player(Sport.TENNIS, "Alexander Zverev", "Zverev"),
        player(Sport.TENNIS, "Taylor Fritz", "Fritz"),
        player(Sport.TENNIS, "Jack Draper", "Draper"),
        player(Sport.TENNIS, "Ben Shelton", "Shelton"),
        player(Sport.TENNIS, "Lorenzo Musetti", "Musetti"),
        player(Sport.TENNIS, "Alex de Minaur", "De Minaur"),
        player(Sport.TENNIS, "Daniil Medvedev", "Medvedev"),
        player(Sport.TENNIS, "Felix Auger-Aliassime", "Auger-Aliassime"),
        player(Sport.TENNIS, "Jakub Mensik", "Mensik"),
        player(Sport.TENNIS, "Joao Fonseca", "João Fonseca", "Fonseca"),
        player(Sport.TENNIS, "Cameron Norrie", "Norrie"),
        player(Sport.TENNIS, "Emma Raducanu", "Raducanu"),
        player(Sport.TENNIS, "Aryna Sabalenka", "Sabalenka"),
        player(Sport.TENNIS, "Iga Swiatek", "Iga Świątek", "Swiatek"),
        player(Sport.TENNIS, "Coco Gauff", "Gauff"),
        player(Sport.TENNIS, "Elena Rybakina", "Rybakina"),
        player(Sport.TENNIS, "Jessica Pegula", "Pegula"),
        player(Sport.TENNIS, "Amanda Anisimova", "Anisimova"),
        player(Sport.TENNIS, "Mirra Andreeva", "Andreeva"),
        player(Sport.TENNIS, "Jasmine Paolini", "Paolini"),
        player(Sport.TENNIS, "Katie Boulter", "Boulter"),
        player(Sport.TENNIS, "Sonay Kartal", "Kartal"),

        // Snooker.
        player(Sport.SNOOKER, "Ronnie O'Sullivan", "O'Sullivan"),
        player(Sport.SNOOKER, "Judd Trump", "Trump"),
        player(Sport.SNOOKER, "Mark Selby", "Selby"),
        player(Sport.SNOOKER, "Neil Robertson", "Robertson"),
        player(Sport.SNOOKER, "John Higgins", "Higgins"),
        player(Sport.SNOOKER, "Mark Williams"),
        player(Sport.SNOOKER, "Kyren Wilson"),
        player(Sport.SNOOKER, "Zhao Xintong"),
        player(Sport.SNOOKER, "Mark Allen"),
        player(Sport.SNOOKER, "Shaun Murphy"),
        player(Sport.SNOOKER, "Luca Brecel", "Brecel"),
        player(Sport.SNOOKER, "Ding Junhui"),
        player(Sport.SNOOKER, "Barry Hawkins", "Hawkins"),
        player(Sport.SNOOKER, "Ali Carter"),
        player(Sport.SNOOKER, "Si Jiahui"),
        player(Sport.SNOOKER, "Wu Yize"),
        player(Sport.SNOOKER, "Jack Lisowski", "Lisowski"),
        player(Sport.SNOOKER, "Chris Wakelin", "Wakelin"),
        player(Sport.SNOOKER, "Stuart Bingham", "Bingham"),

        // Darts.
        player(Sport.DARTS, "Luke Littler", "Littler"),
        player(Sport.DARTS, "Luke Humphries", "Humphries"),
        player(Sport.DARTS, "Michael van Gerwen", "Van Gerwen", "MVG"),
        player(Sport.DARTS, "Gerwyn Price"),
        player(Sport.DARTS, "Michael Smith"),
        player(Sport.DARTS, "Peter Wright"),
        player(Sport.DARTS, "Nathan Aspinall", "Aspinall"),
        player(Sport.DARTS, "Stephen Bunting", "Bunting"),
        player(Sport.DARTS, "Rob Cross"),
        player(Sport.DARTS, "Josh Rock"),
        player(Sport.DARTS, "Gian van Veen", "Van Veen"),
        player(Sport.DARTS, "Damon Heta", "Heta"),
        player(Sport.DARTS, "Jonny Clayton", "Clayton"),
        player(Sport.DARTS, "Danny Noppert", "Noppert"),
        player(Sport.DARTS, "Chris Dobey", "Dobey"),
        player(Sport.DARTS, "James Wade"),
        player(Sport.DARTS, "Gary Anderson"),
        player(Sport.DARTS, "Dimitri Van den Bergh", "Van den Bergh"),
        player(Sport.DARTS, "Martin Schindler", "Schindler"),
        player(Sport.DARTS, "Wessel Nijman", "Nijman"),
        player(Sport.DARTS, "Beau Greaves", "Greaves"),
        player(Sport.DARTS, "Fallon Sherrock", "Sherrock"),

        // Boxing.
        player(Sport.BOXING, "Anthony Joshua", "Joshua", "AJ"),
        player(Sport.BOXING, "Tyson Fury", "Fury"),
        player(Sport.BOXING, "Oleksandr Usyk", "Usyk"),
        player(Sport.BOXING, "Daniel Dubois", "Dubois"),
        player(Sport.BOXING, "Joseph Parker"),
        player(Sport.BOXING, "Fabio Wardley", "Wardley"),
        player(Sport.BOXING, "Moses Itauma", "Itauma"),
        player(Sport.BOXING, "Canelo Alvarez", "Canelo Álvarez", "Canelo"),
        player(Sport.BOXING, "Terence Crawford", "Crawford"),
        player(Sport.BOXING, "Naoya Inoue", "Inoue"),
        player(Sport.BOXING, "Chris Eubank Jr", "Eubank Jr", "Eubank"),
        player(Sport.BOXING, "Conor Benn", "Benn"),
        player(Sport.BOXING, "Katie Taylor"),
        player(Sport.BOXING, "Natasha Jonas"),
        player(Sport.BOXING, "Chantelle Cameron"),
        player(Sport.BOXING, "Dalton Smith"),
        player(Sport.BOXING, "Jai Opetaia", "Opetaia"),
        player(Sport.BOXING, "Dmitry Bivol", "Bivol"),
        player(Sport.BOXING, "Artur Beterbiev", "Beterbiev"),
        player(Sport.BOXING, "Jake Paul"),
        player(Sport.BOXING, "Shakur Stevenson"),
        player(Sport.BOXING, "Derek Chisora", "Chisora"),
    )

    private val byKey: Map<String, SportFigure> = buildMap {
        for (figure in figures) for (key in figure.keys) putIfAbsent(key, figure)
    }

    /** The team or player [name] is, if it's one the Sports Hub knows. */
    fun find(name: String): SportFigure? = byKey[SportNames.key(name)]

    private val bySportAndKey: Map<Pair<Sport, String>, SportFigure> = buildMap {
        for (figure in figures) for (key in figure.keys) putIfAbsent(figure.sport to key, figure)
    }

    /** The team or player [name] is, among those of [sport]. */
    fun find(name: String, sport: Sport): SportFigure? = bySportAndKey[sport to SportNames.key(name)]

    /** Teams and players whose name starts with what's typed, or has a word that does, best first. */
    fun suggest(typed: String, limit: Int = 8): List<SportFigure> {
        val key = SportNames.key(typed)
        if (key.isEmpty()) return emptyList()
        val starts = figures.filter { f -> f.keys.any { it.startsWith(key) } }
        val words = figures.filter { f -> f !in starts && f.keys.any { k -> k.split(' ').any { it.startsWith(key) } } }
        return (starts + words).take(limit)
    }
}
