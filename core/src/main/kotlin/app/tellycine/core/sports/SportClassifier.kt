package app.tellycine.core.sports

import java.text.Normalizer
import java.util.Locale

/**
 * Finds sport in TV guides: which sport a programme is, whether it's live, a replay, highlights
 * or a magazine, and who's playing. It goes by the programme's title, category and description and
 * by the channel's name, with UK guides and channels in mind first.
 *
 * Guides say all this in many ways, so it's a matter of rules of thumb: words that only one sport
 * uses ("snooker", "Grand Slam of Darts"), then words that need the rest to say which sport
 * ("Grand Prix", "US Open"), then the channel's sport, then the guide's category.
 */
class SportClassifier {

    /**
     * What a channel's name says about its sport: one sport ("Sky Sports F1"), or sport in general;
     * whether it's a news channel ("Sky Sports News"), whose programmes are news unless they say
     * who's playing; and whether it's a club's own channel (MUTV), which shows its matches again.
     */
    data class ChannelHint(
        val sport: Sport?,
        val isSport: Boolean,
        val news: Boolean = false,
        val club: Boolean = false,
        /** Its name is a sport the hub doesn't name ("Sky Sports NFL"): its football isn't football. */
        val otherSport: Boolean = false,
    ) {
        companion object {
            val NONE = ChannelHint(null, false)
        }
    }

    /** What [name], a channel's name, says about the sport it shows. */
    fun channel(name: String?): ChannelHint {
        if (name.isNullOrBlank()) return ChannelHint.NONE
        val w = Words(name)
        val sport = when {
            w.hasAny(OTHER_IN_MAIN) -> null
            w.hasAny(CHANNEL_F1) -> Sport.F1
            w.has("motogp") || w.has("moto gp") -> Sport.MOTOGP
            w.has("golf") -> Sport.GOLF
            w.has("tennis") -> Sport.TENNIS
            w.has("snooker") -> Sport.SNOOKER
            w.has("darts") -> Sport.DARTS
            w.has("boxing") || w.has("boxnation") -> Sport.BOXING
            w.hasAny(CHANNEL_FOOTBALL) -> Sport.FOOTBALL
            else -> null
        }
        val isSport = sport != null || w.hasAny(CHANNEL_SPORT) || w.hasAny(OTHER_SPORT)
        return ChannelHint(sport, isSport, news = w.has("news"), club = w.hasAny(CLUB_CHANNELS), otherSport = sport == null && w.otherSport())
    }

    /**
     * Whether a programme could be sport, from what's cheap to read: its [title], [category] and
     * channel. Only these are worth reading the description of for [classify].
     */
    fun isCandidate(title: String, category: String?, channel: ChannelHint): Boolean {
        if (channel.isSport) return true
        val cat = category?.let(::Words)
        if (cat != null && (cat.hasAny(SPORTY_CATEGORY) || sportOf(cat) != null)) return true
        val w = Words(title)
        // Most programmes have none of the words sport starts with: a quick look rules them out.
        if (w.words.none { it in CANDIDATE_WORDS }) return false
        return sportOf(w) != null || w.hasAny(OTHER_SPORT) || w.has("v") || w.has("vs") || w.has("versus")
    }

    /**
     * What sport the programme is, if it's sport. [month] (1 to 12) settles names two sports
     * share, such as the US Open (golf in June, tennis in August and September); [year], when it's
     * on, tells a match from years ago ("ENG v SA 2017") for a replay.
     */
    fun classify(
        title: String,
        category: String?,
        description: String?,
        channel: ChannelHint,
        month: Int? = null,
        year: Int? = null,
    ): SportMatch? {
        val titleWords = Words(title)
        val categoryWords = category?.let(::Words)
        val descWords = description?.let { Words(firstSentences(it)) }
        val sporty = categoryWords?.hasAny(SPORTY_CATEGORY) == true
        // Films, dramas and the like aren't sport, whatever they're about.
        if (!sporty && categoryWords != null && categoryWords.hasAny(NOT_SPORT_CATEGORY) && sportOf(categoryWords) == null) return null

        val parts = TitleParts.of(title)
        val descSides = if (parts.home == null && description != null) TitleParts.knownSides(firstSentences(description)) else null
        val home = parts.home ?: descSides?.first
        val away = parts.away ?: descSides?.second

        // Which sport: the title, or who's playing, says for sure; the description, the category
        // or the channel only suggest it.
        val strong = sportOf(titleWords)
            ?: contextual(titleWords, descWords, channel, categoryWords, month)
            ?: Sport.OTHER.takeIf { titleWords.otherSport() }
            ?: footballOf(titleWords)?.let { if (channel.otherSport) Sport.OTHER else it }
            ?: sidesSport(home, away)
        val weak = descWords?.let { sportOf(it) ?: Sport.OTHER.takeIf { _ -> it.otherSport() } ?: footballOf(it) }
            ?: categoryWords?.let { sportOf(it) ?: footballOf(it) ?: if (it.hasAny(MOTOR_CATEGORY)) channel.sport?.takeIf { s -> s == Sport.F1 || s == Sport.MOTOGP } ?: Sport.OTHER else null }
            ?: channel.sport
            ?: Sport.OTHER.takeIf { channel.isSport || sporty }
        val sport = strong ?: weak ?: return null

        val kind = kindOf(title, titleWords, descWords, categoryWords, year)
        val session = sessionOf(parts.rest, sport)
        val finalKind = when {
            // A news channel's programmes are news, unless they say who's playing.
            channel.news && home == null -> AiringKind.MAGAZINE
            // Only the description, category or channel says it's sport: unless it says it's live,
            // or who's playing or which part of the event it is, it's more likely a magazine.
            strong == null && kind == AiringKind.EVENT && home == null && session == null -> AiringKind.MAGAZINE
            // A club's channel shows its matches again; live ones say so.
            channel.club && kind == AiringKind.EVENT -> AiringKind.REPLAY
            else -> kind
        }

        val competition = parts.competition(sport)
        val display = when {
            home != null && away != null -> "$home v $away"
            else -> parts.eventName(sport) ?: competition ?: parts.cleaned
        }
        return SportMatch(
            sport = sport,
            kind = finalKind,
            title = display,
            competition = competition?.takeUnless { it.equals(display, ignoreCase = true) },
            session = session,
            home = home,
            away = away,
        )
    }

    // ---- Sport ----

    /** A sport only one sport's words name, if the text has them. */
    private fun sportOf(w: Words): Sport? {
        if (w.hasAny(OTHER_IN_MAIN)) return null
        // Whichever sport is named first: "Snooker and darts round-up" is snooker.
        var best: Sport? = null
        var at = Int.MAX_VALUE
        for ((sport, words) in STRONG) {
            val i = w.firstIndexOf(words) ?: continue
            if (i < at) {
                at = i
                best = sport
            }
        }
        return best
    }

    private fun footballOf(w: Words): Sport? =
        Sport.FOOTBALL.takeIf { !w.otherSport() && w.hasAny(FOOTBALL) }

    /** Names more than one sport uses, settled by the rest of what's known. */
    private fun contextual(title: Words, desc: Words?, channel: ChannelHint, category: Words?, month: Int?): Sport? {
        val all = listOfNotNull(title, desc, category)
        fun any(words: Phrases) = all.any { it.hasAny(words) }
        if (title.otherSport()) return null
        val named = desc?.let { sportOf(it) }
        if (title.has("world grand prix")) {
            return named?.takeIf { it == Sport.DARTS || it == Sport.SNOOKER }
                ?: channel.sport?.takeIf { it == Sport.DARTS || it == Sport.SNOOKER }
                ?: if (month in 1..3) Sport.SNOOKER else Sport.DARTS
        }
        if (title.has("scottish open")) {
            return named?.takeIf { it == Sport.GOLF || it == Sport.SNOOKER }
                ?: channel.sport?.takeIf { it == Sport.GOLF || it == Sport.SNOOKER }
                ?: when (month) {
                    6, 7 -> Sport.GOLF
                    11, 12 -> Sport.SNOOKER
                    else -> null
                }
        }
        if (title.has("grand prix") || title.has("gp")) {
            return when {
                named == Sport.DARTS || named == Sport.SNOOKER -> named
                any(MOTOGP_WORDS) || channel.sport == Sport.MOTOGP -> Sport.MOTOGP
                named == Sport.F1 || channel.sport == Sport.F1 -> Sport.F1
                channel.sport == Sport.DARTS || channel.sport == Sport.SNOOKER -> channel.sport
                any(MOTOR_SESSIONS) || category?.hasAny(MOTOR_CATEGORY) == true -> Sport.F1
                else -> null
            }
        }
        if (title.has("us open")) {
            return named?.takeIf { it == Sport.GOLF || it == Sport.TENNIS }
                ?: channel.sport?.takeIf { it == Sport.GOLF || it == Sport.TENNIS }
                ?: when (month) {
                    6 -> Sport.GOLF
                    8, 9 -> Sport.TENNIS
                    else -> null
                }
        }
        if (title.has("masters")) {
            return named?.takeIf { it == Sport.GOLF || it == Sport.SNOOKER || it == Sport.DARTS || it == Sport.TENNIS }
                ?: channel.sport?.takeIf { it == Sport.GOLF || it == Sport.SNOOKER || it == Sport.DARTS || it == Sport.TENNIS }
                ?: when (month) {
                    4 -> Sport.GOLF
                    1 -> Sport.SNOOKER
                    else -> null
                }
        }
        for ((sport, words) in SHARED) {
            if (title.hasAny(words)) return named ?: channel.sport ?: sport
        }
        if (title.hasAny(BOXING_CONTEXT) && desc?.otherSport() != true) return Sport.BOXING
        return null
    }

    /** Football when both sides are teams the catalogue knows, or a sport's players when both are its. */
    private fun sidesSport(home: String?, away: String?): Sport? {
        if (home == null || away == null) return null
        val a = SportCatalogue.find(home) ?: return null
        val b = SportCatalogue.find(away) ?: return null
        return a.sport.takeIf { it == b.sport && !a.followsWholeSport }
    }

    // ---- Live, replay, highlights ----

    private fun kindOf(raw: String, title: Words, desc: Words?, category: Words?, year: Int?): AiringKind = when {
        // Match of the Day's live matches.
        title.has("motd live") || title.has("match of the day live") -> AiringKind.LIVE
        title.hasAny(REPLAY) || REPEAT_MARK.containsMatchIn(raw) || OLD_YEAR.containsMatchIn(raw) || isSeason(raw) || isPastYear(raw, year) ->
            AiringKind.REPLAY
        title.hasAny(HIGHLIGHTS) -> AiringKind.HIGHLIGHTS
        title.hasAny(MAGAZINE) || isGenericTitle(title) || category?.hasAny(MAGAZINE_CATEGORY) == true -> AiringKind.MAGAZINE
        title.has("live") || desc?.startsWith("live") == true || desc?.hasAny(LIVE_DESC) == true -> AiringKind.LIVE
        desc?.hasAny(REPLAY_DESC) == true -> AiringKind.REPLAY
        else -> AiringKind.EVENT
    }

    /** A season in the title, such as "26/27" or "1999/00": a match or show from the archive. */
    private fun isSeason(raw: String): Boolean = SEASON.findAll(raw).any { m ->
        val a = m.groupValues[1].takeLast(2).toInt()
        val b = m.groupValues[2].takeLast(2).toInt()
        (a + 1) % 100 == b
    }

    /** A year before [year] in the title: "ENG v SA 2017". */
    private fun isPastYear(raw: String, year: Int?): Boolean =
        year != null && YEAR.findAll(raw).any { it.value.toInt() < year }

    /** Only the word sport, or news: a sports news slot. */
    private fun isGenericTitle(title: Words): Boolean = title.words.all { it in GENERIC_TITLE_WORDS }

    // ---- Sessions ----

    private fun sessionOf(text: String, sport: Sport): String? {
        val pattern = if (sport == Sport.F1 || sport == Sport.MOTOGP) MOTOR_SESSION else ROUND_SESSION
        val found = pattern.find(text)?.value ?: return null
        val key = Words(found).text.trim()
        SESSION_NAMES[key]?.let { return it }
        return found.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            .replace("quarter final", "quarter-final").replace("semi final", "semi-final")
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /** A title in its parts: who's playing ("A v B"), and the rest. */
    private class TitleParts(
        /** The title without "Live" and repeat marks. */
        val cleaned: String,
        val segments: List<String>,
        val home: String?,
        val away: String?,
    ) {
        /** The title less the sides, for finding the session in. */
        val rest: String get() = segments.joinToString(" · ")

        /** The competition: the first part that isn't only the sport's name or a session. */
        fun competition(sport: Sport): String? = segments.firstOrNull { !isGeneric(it, sport) && !isSession(it) }?.let { strip(it, sport) }

        /** For an event without sides: its own name, after the competition when there's one. */
        fun eventName(sport: Sport): String? {
            val named = segments.filter { !isGeneric(it, sport) && !isSession(it) }
            return (if (named.size >= 2) named[1] else named.firstOrNull())?.let { strip(it, sport) }
        }

        companion object {
            fun of(title: String): TitleParts {
                val cleaned = clean(title)
                val parts = cleaned.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
                var home: String? = null
                var away: String? = null
                val rest = ArrayList<String>()
                for (part in parts) {
                    val sides = if (home == null) sidesOf(part) else null
                    if (sides != null) {
                        home = sides.first
                        away = sides.second
                    } else {
                        rest += part
                    }
                }
                return TitleParts(cleaned.ifEmpty { title.trim() }, rest, home, away)
            }

            /**
             * "A v B" in [text], a description: at its start ("Plymouth Parkway v Berkhamsted
             * (Kick-off 7.30pm). Coverage of…"), or elsewhere when both are teams or players the
             * catalogue knows.
             */
            fun knownSides(text: String): Pair<String, String>? {
                text.replace(LEADING_ACTION, "").split(SENTENCE_OR_SEPARATOR).forEachIndexed { index, part ->
                    val sides = sidesOf(part.trim()) ?: return@forEachIndexed
                    if (index == 0 || (SportCatalogue.find(sides.first) != null && SportCatalogue.find(sides.second) != null)) return sides
                }
                return null
            }

            private fun sidesOf(part: String): Pair<String, String>? {
                val m = SIDES.find(part) ?: return null
                val home = side(m.groupValues[1]) ?: return null
                val away = side(m.groupValues[2]) ?: return null
                return home to away
            }

            /** One side of "A v B": a name of a few words, without "Live" or a kick-off time. */
            private fun side(text: String): String? {
                val s = text.replace(TRAILING_BRACKETS, "").replace(LEADING_LIVE, "").replace(LEADING_LEAGUE, "").replace(TRAILING_TIME, "")
                    .replace(TRAILING_CONTEXT, "").trim(' ', ',', '.', '-', '–')
                val words = s.split(' ').filter { it.isNotEmpty() }
                if (words.isEmpty() || words.size > 5 || s.length > 40 || s.none { it.isLetter() }) return null
                return s
            }

            private fun clean(title: String): String = title
                .replace(REPEAT_MARK, " ")
                .replace(LIVE_BRACKETS, " ")
                .replace(LEADING_LIVE, "")
                .replace(TRAILING_LIVE, "")
                .replace(KICK_OFF, " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            private fun isGeneric(part: String, sport: Sport): Boolean {
                val w = Words(part).text.trim()
                return w.isEmpty() || w in GENERIC || w.removePrefix("live ").let { it in GENERIC || it == SportNames.key(sport.label) }
            }

            private fun isSession(part: String): Boolean = Words(part).text.trim().let { key ->
                key in SESSION_NAMES || MOTOR_SESSION.matches(part.trim()) || ROUND_SESSION.matches(part.trim())
            }

            /** [part] less a trailing sport name: "Premier League Football" is the Premier League. */
            private fun strip(part: String, sport: Sport): String {
                val s = part.replace(LEADING_LIVE, "").trim()
                for (suffix in TRAILING_SPORT_WORDS) {
                    if (s.length > suffix.length + 2 && s.endsWith(" $suffix", ignoreCase = true)) {
                        val rest = s.dropLast(suffix.length + 1).trim()
                        // Not when it's part of the name: the Grand Slam of Darts.
                        if (rest.substringAfterLast(' ').lowercase() !in JOINING_WORDS) return rest
                    }
                }
                return s.ifEmpty { sport.label }
            }
        }
    }

    /** Text as words in lower case, without accents or punctuation, for finding words and phrases in. */
    private class Words(raw: String) {
        val text: String = normalise(raw)

        /** Its words, for a quick look before looking for phrases. */
        val words: List<String> by lazy { text.trim().split(' ') }

        fun has(phrase: String): Boolean = text.contains(" $phrase ")
        fun hasAny(phrases: Phrases): Boolean = phrases.padded.any { text.contains(it) }
        fun startsWith(phrase: String): Boolean = text.startsWith(" $phrase ")

        /** Where the first of [phrases] is, if any is there. */
        fun firstIndexOf(phrases: Phrases): Int? {
            var first = -1
            for (p in phrases.padded) {
                val i = text.indexOf(p)
                if (i >= 0 && (first < 0 || i < first)) first = i
            }
            return first.takeIf { it >= 0 }
        }

        fun otherSport(): Boolean = hasAny(OTHER_IN_MAIN) || hasAny(OTHER_SPORT)

        companion object {
            /**
             * " lower case words with single spaces ": without accents, apostrophes or other
             * punctuation. Plain ASCII, as most guides are, skips the slower Unicode steps.
             */
            fun normalise(raw: String): String {
                val ascii = raw.all { it.code < 128 }
                val source = if (ascii) raw else MARKS.replace(Normalizer.normalize(raw, Normalizer.Form.NFD), "")
                val out = StringBuilder(source.length + 2).append(' ')
                var space = true
                for (c in source) {
                    when {
                        c.isLetterOrDigit() -> {
                            if (ascii) out.append(if (c in 'A'..'Z') c + 32 else c) else out.append(c.toString().lowercase(Locale.ROOT))
                            space = false
                        }
                        c == '\'' || c == '’' || c == '‘' || c == '`' -> Unit
                        !space -> {
                            out.append(' ')
                            space = true
                        }
                    }
                }
                if (!space) out.append(' ')
                // Boxing Day is a day for football, not boxing.
                return out.toString().replace(" boxing day ", " boxingday ")
            }
        }
    }

    /** Phrases to look for, ready to look for: with a space either side, and their first words. */
    private class Phrases(list: List<String>) {
        val padded: Array<String> = list.map { " $it " }.toTypedArray()
        val firstWords: Set<String> = list.map { it.substringBefore(' ') }.toSet()
    }

    private companion object {
        val MARKS = Regex("\\p{M}+")

        /** Between the parts of a title: "Live Premier League: Arsenal v Chelsea - Week 12". */
        val SEPARATORS = Regex("\\s*:\\s+|\\s+[-–—|/]\\s+|\\s*\\|\\s*")
        val SENTENCE_OR_SEPARATOR = Regex("[.!?](?:\\s+|$)|\\s*:\\s+|\\s+[-–—|]\\s+|,\\s+|\\s*\\(")

        /** "A v B", "A vs B", "A vs. B", "A versus B". */
        val SIDES = Regex("^(.+?)\\s+(?:v|vs\\.?|versus|@)\\s+(.+)$", RegexOption.IGNORE_CASE)

        /** "26/27", "1999/00", "2013-14". */
        val SEASON = Regex("(?<![\\d/])((?:19|20)?\\d{2})[/-]((?:19|20)?\\d{2})(?![\\d/])")
        val YEAR = Regex("(?<!\\d)(?:19[5-9]\\d|20\\d\\d)(?!\\d)")
        val GENERIC_TITLE_WORDS = setOf("sport", "sports", "news", "live", "today", "tonight", "update", "the")
        val CLUB_CHANNELS = Phrases(listOf("mutv", "lfctv", "lfc tv", "chelsea tv", "barca tv", "barca one", "real madrid tv", "rangers tv", "celtic tv", "city plus"))

        val MOTOGP_WORDS = Phrases(listOf("motogp", "moto gp", "moto2", "moto3"))

        val REPEAT_MARK = Regex("\\((?:r|rpt|repeat)\\)|\\[(?:r|rpt)]", RegexOption.IGNORE_CASE)
        val OLD_YEAR = Regex("\\((?:19|20)\\d\\d(?:/\\d\\d)?\\)")
        val LIVE_BRACKETS = Regex("\\(live\\)|\\[live]", RegexOption.IGNORE_CASE)
        val LEADING_LIVE = Regex("^\\s*live\\b\\s*[:!\\-–|]?\\s*", RegexOption.IGNORE_CASE)
        val TRAILING_LIVE = Regex("\\s*[-–:|]\\s*live\\s*$|\\s+live!?\\s*$", RegexOption.IGNORE_CASE)
        val KICK_OFF = Regex("\\((?:ko|k/o|kick[- ]?off)[^)]*\\)", RegexOption.IGNORE_CASE)
        /** A league before the first side, without a colon: "NFL Buccaneers @ Bears". */
        val LEADING_LEAGUE = Regex("^(?:NFL|NBA|NHL|MLB|MLS|WNBA|NRL|AFL|IPL|EPL)\\s+")

        /** How a description starts before naming the match: "Action from Chelsea v Everton". */
        val LEADING_ACTION = Regex(
            "^\\s*(?:live\\s+)?(?:extended\\s+)?(?:action|highlights|coverage|another chance to see|a chance to see|full match)\\s+(?:from|of)?\\s*",
            RegexOption.IGNORE_CASE,
        )

        /** Where or what a match is, after the side: "Sheffield United in the Championship". */
        val TRAILING_CONTEXT = Regex("\\s+(?:in|at|from|on|for|with|as|during)\\s+(?:the\\s+)?[a-z0-9].*$|\\s+(?:in|at|from)\\s+the\\s+.*$")
        val TRAILING_BRACKETS = Regex("\\s*[(\\[][^)\\]]*[)\\]]\\s*$")
        val TRAILING_TIME = Regex("\\s+(?:@\\s*)?\\d{1,2}[:.]\\d{2}\\s*(?:am|pm)?$|\\s+\\d{1,2}\\s*(?:am|pm)$", RegexOption.IGNORE_CASE)

        /** Sports with words of their own, and those words. */
        val STRONG: List<Pair<Sport, Phrases>> = listOf(
            Sport.SNOOKER to Phrases(listOf("snooker", "world snooker", "wst", "crucible", "uk championship", "shoot out", "champion of champions", "welsh open", "german masters", "english open", "northern ireland open")),
            Sport.DARTS to Phrases(listOf("darts", "pdc", "bdo", "wdf", "world matchplay", "grand slam of darts", "premier league darts", "world series of darts", "uk open darts", "alexandra palace")),
            Sport.MOTOGP to Phrases(listOf("motogp", "moto gp", "moto2", "moto3", "motoe")),
            Sport.F1 to Phrases(listOf("f1", "formula 1", "formula one", "formula 2", "formula 3", "f2", "f3", "f1 academy")),
            Sport.GOLF to Phrases(listOf("golf", "pga", "pga tour", "lpga", "dp world tour", "ryder cup", "solheim cup", "liv golf", "the open championship", "race to dubai")),
            Sport.TENNIS to Phrases(listOf("tennis", "atp", "wta", "wimbledon", "roland garros", "french open", "australian open", "davis cup", "billie jean king cup", "laver cup", "atp finals", "wta finals", "united cup")),
            Sport.BOXING to Phrases(listOf("boxing", "boxnation", "fight night", "queensberry", "matchroom boxing", "ring walks")),
        )

        /** Names more than one sport uses, with the likelier one for a UK guide. */
        val SHARED: List<Pair<Sport, Phrases>> = listOf(
            Sport.SNOOKER to Phrases(listOf("players championship", "tour championship", "british open")),
            Sport.GOLF to Phrases(listOf("the open", "open championship", "irish open", "dunhill links", "bmw pga")),
            Sport.TENNIS to Phrases(listOf("queens", "queens club", "next gen")),
        )

        val FOOTBALL = Phrases(listOf(
            "football", "soccer", "premier league", "efl", "sky bet championship", "efl championship", "league one", "league two",
            "fa cup", "carabao cup", "efl cup", "league cup", "efl trophy", "champions league", "europa league", "conference league",
            "nations league", "world cup qualifier", "world cup qualifiers", "world cup qualifying", "euro 2028", "euro qualifier",
            "euro qualifiers", "scottish premiership", "spfl", "scottish cup", "la liga", "laliga", "serie a", "bundesliga", "ligue 1",
            "eredivisie", "mls", "copa del rey", "copa america", "copa libertadores", "wsl", "womens super league", "saudi pro league",
            "community shield", "club world cup", "nwsl", "supercopa", "dfb pokal", "coppa italia", "primeira liga", "liga portugal",
            "match of the day", "motd", "uefa", "fifa", "monday night football", "super sunday",
        ))

        /** Sports the hub doesn't name whose names have one of its sports' words in. */
        val OTHER_IN_MAIN = Phrases(listOf(
            "table tennis", "beach tennis", "padel", "padel tennis", "kickboxing", "thai boxing", "muay thai", "bare knuckle",
            "american football", "gaelic football", "australian football", "aussie rules", "crazy golf", "disc golf", "footgolf",
            "formula e", "superbikes", "superbike", "worldsbk", "fifa 26", "efootball", "esports", "ufc", "mma", "bellator", "pfl",
        ))

        /** Sports the hub doesn't name: they're "other sport". */
        val OTHER_SPORT = Phrases(listOf(
            "rugby", "rugby union", "rugby league", "cricket", "nfl", "gaelic", "hurling", "ice hockey", "hockey", "nhl", "basketball",
            "nba", "wnba", "baseball", "mlb", "netball", "handball", "volleyball", "cycling", "tour de france", "athletics", "swimming",
            "horse racing", "racing from", "greyhound", "greyhounds", "ufc", "mma", "bellator", "pfl", "wrestling", "wwe", "aew",
            "triathlon", "rowing", "skiing", "snowboarding", "gymnastics", "squash", "badminton", "pickleball", "bowls",
            "pool", "nine ball", "mosconi cup", "motocross", "supercross", "indycar", "nascar", "rally", "wrc", "le mans", "wec",
            "btcc", "speedway", "lacrosse", "afl", "curling", "equestrian", "showjumping", "six nations", "premiership rugby",
            "challenge cup", "autumn nations", "test match", "the ashes", "t20", "odi", "ipl", "the hundred", "rlwc",
            "rugby league", "cyclocross", "nations championship",
            "olympics", "paralympics", "commonwealth games",
        ))

        /** Words that say who's boxing, without the word "boxing". */
        val BOXING_CONTEXT = Phrases(listOf(
            "heavyweight", "cruiserweight", "light heavyweight", "super middleweight", "middleweight", "super welterweight",
            "welterweight", "super lightweight", "lightweight", "super featherweight", "featherweight", "bantamweight", "flyweight",
            "undercard", "world title fight", "title fight", "riyadh season", "top rank", "golden boy",
        ))

        val CHANNEL_F1 = Phrases(listOf("sky sports f1", "f1", "f1 tv", "formula 1"))
        val CHANNEL_FOOTBALL = Phrases(listOf(
            "football", "sky sports football", "sky sports premier league", "premier league", "laliga tv", "la liga", "mutv",
            "lfc tv", "chelsea tv", "soccer", "efl", "spfl", "bundesliga", "serie a", "ligue 1", "mls",
        ))
        val CHANNEL_SPORT = Phrases(listOf(
            "sport", "sports", "sky sports", "tnt sports", "bt sport", "eurosport", "dazn", "espn", "bein", "bein sports",
            "premier sports", "viaplay sports", "setanta", "supersport", "arena sport", "sport tv", "sportdigital", "canal sport",
            "sky sport", "deportes", "sports plus", "racing tv", "fox sports", "tsn", "sportsnet", "astro supersport", "optus sport",
            "stan sport", "nbc sports", "cbs sports", "peacock",
        ))

        val SPORTY_CATEGORY = Phrases(listOf("sport", "sports", "sports event", "sport event", "sporting event", "sports events", "sports magazine", "sports magazines", "athletics", "team sports"))
        val NOT_SPORT_CATEGORY = Phrases(listOf(
            "film", "films", "movie", "movies", "drama", "comedy", "sitcom", "soap", "children", "childrens", "kids", "animation", "cartoon",
            "entertainment", "game show", "quiz", "reality", "lifestyle", "cookery", "cooking", "travel", "music", "religion", "religious",
            "news", "current affairs", "documentary", "documentaries", "talk show", "shopping",
        ))
        val MOTOR_CATEGORY = Phrases(listOf("motor sport", "motor sports", "motorsport", "motorsports", "motor racing", "formula 1", "formula one"))
        val MAGAZINE_CATEGORY = Phrases(listOf("sports magazine", "sports magazines", "sports talk", "sports news", "sports non event", "magazine"))

        val REPLAY = Phrases(listOf(
            "replay", "replays", "repeat", "rpt", "rerun", "re run", "classic", "classics", "encore", "rewind", "retro", "archive",
            "greatest", "best of", "vintage", "full match", "full race", "memorable", "iconic", "from the vault", "on this day",
            "flashback",
        ))
        val REPLAY_DESC = Phrases(listOf("another chance to see", "first shown", "originally shown", "from earlier", "earlier today"))
        val HIGHLIGHTS = Phrases(listOf(
            "highlights", "match of the day", "motd", "goals", "round up", "roundup", "review", "recap", "in 90", "extended",
            "catch up", "best bits", "the best of", "action replay", "hlts", "netbusters", "reload",
        ))
        val MAGAZINE = Phrases(listOf(
            "news", "show", "podcast", "preview", "previews", "build up", "buildup", "pre match", "prematch", "pre race", "post race",
            "post match", "talk", "talking", "debate", "reaction", "analysis", "countdown", "weekly", "magazine", "focus", "insider",
            "documentary", "uncovered", "story", "stories", "quiz", "fantasy", "soccer saturday", "final score", "results", "sportscene",
            "sports centre", "sportsday", "breakfast", "fanzone", "tips", "betting", "draw", "awards", "behind the", "notebook", "unlocked",
            "special report", "headlines", "bulletin", "update", "updates", "legends", "icons", "interview", "this week",
            "season so far", "spotlight", "a day in the life", "ref watch", "sky sports fc", "football night", "debrief", "lessons",
            "good morning", "golf academy", "the cut", "specials", "breakdown", "on fire", "on the fly",
        ))
        val LIVE_DESC = Phrases(listOf("live coverage", "live action", "coverage live", "shown live", "live from", "live on", "live as", "watch live"))

        val MOTOR_SESSIONS = Phrases(listOf("qualifying", "practice", "sprint", "race", "pole", "pit lane", "grid", "fp1", "fp2", "fp3"))
        val MOTOR_SESSION = Regex(
            "\\b(?:practice\\s*(?:1|2|3|one|two|three)|fp[123]|(?:first|second|third)\\s+practice|sprint\\s+(?:qualifying|shootout)|" +
                "sprint|qualifying|race|warm[- ]?up)\\b",
            RegexOption.IGNORE_CASE,
        )
        val ROUND_SESSION = Regex(
            "\\b(?:(?:first|second|third|fourth|final)\\s+round|round\\s+(?:\\d{1,2}|one|two|three|four)|day\\s+(?:\\d{1,2}|one|two|three|four|five|six|seven)|" +
                "last\\s+(?:16|32|64)|quarter[- ]?finals?|semi[- ]?finals?|final|main\\s+card|undercard|prelims)\\b",
            RegexOption.IGNORE_CASE,
        )
        val SESSION_NAMES = mapOf(
            "fp1" to "Practice 1", "fp2" to "Practice 2", "fp3" to "Practice 3",
            "practice 1" to "Practice 1", "practice 2" to "Practice 2", "practice 3" to "Practice 3",
            "practice one" to "Practice 1", "practice two" to "Practice 2", "practice three" to "Practice 3",
            "first practice" to "Practice 1", "second practice" to "Practice 2", "third practice" to "Practice 3",
            "sprint qualifying" to "Sprint Qualifying", "sprint shootout" to "Sprint Qualifying", "sprint" to "Sprint",
            "qualifying" to "Qualifying", "race" to "Race", "warm up" to "Warm-up", "warmup" to "Warm-up",
            "first round" to "Round 1", "second round" to "Round 2", "third round" to "Round 3", "fourth round" to "Round 4",
            "final round" to "Final round", "round one" to "Round 1", "round two" to "Round 2", "round three" to "Round 3",
            "round four" to "Round 4", "main card" to "Main card", "undercard" to "Undercard", "prelims" to "Prelims",
        )

        /** Parts of titles that only name the sport, so aren't the event's or the competition's name. */
        val GENERIC = setOf(
            "football", "soccer", "formula 1", "formula one", "f1", "motogp", "moto gp", "golf", "tennis", "snooker", "darts",
            "boxing", "international football", "sport", "sports", "motor sport", "motorsport", "super sunday", "monday night football",
            "live", "sky sports", "tnt sports", "world snooker", "pdc darts",
        )
        val TRAILING_SPORT_WORDS = listOf("Football", "Soccer", "Snooker", "Darts", "Golf", "Tennis", "Boxing")
        val JOINING_WORDS = setOf("of", "the", "and", "de", "in")

        fun firstSentences(description: String): String = description.take(240)

        /** The words any phrase that makes a programme worth a closer look starts with. */
        val CANDIDATE_WORDS: Set<String> by lazy {
            (STRONG.flatMap { it.second.firstWords } + FOOTBALL.firstWords + OTHER_IN_MAIN.firstWords + OTHER_SPORT.firstWords +
                listOf("v", "vs", "versus")).toSet()
        }
    }
}
