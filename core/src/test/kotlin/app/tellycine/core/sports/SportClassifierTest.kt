package app.tellycine.core.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sport in UK-style guides: which sport, live or not, and who's playing. */
class SportClassifierTest {
    private val c = SportClassifier()

    private fun classify(title: String, category: String? = null, description: String? = null, channel: String? = null, month: Int? = null, year: Int? = null) =
        c.classify(title, category, description, c.channel(channel), month, year)

    @Test
    fun aLiveMatchHasItsCompetitionAndSides() {
        val m = classify("Live Premier League: Arsenal v Chelsea", "Sport", channel = "Sky Sports Main Event HD")!!
        assertEquals(Sport.FOOTBALL, m.sport)
        assertEquals(AiringKind.LIVE, m.kind)
        assertEquals("Arsenal v Chelsea", m.title)
        assertEquals("Premier League", m.competition)
        assertEquals("Arsenal", m.home)
        assertEquals("Chelsea", m.away)
    }

    @Test
    fun kickOffTimesAndBracketsAreNotPartOfTheSides() {
        val m = classify("Live EFL: Leeds v Sunderland (KO 20:00)")!!
        assertEquals("Leeds v Sunderland", m.title)
        assertEquals("Sunderland", m.away)
        assertEquals(AiringKind.LIVE, m.kind)
        assertEquals("Brighton v Wolves", classify("Live Monday Night Football: Brighton v Wolves 20:00")!!.title)
    }

    @Test
    fun twoKnownTeamsMakeFootballWithoutTheWord() {
        val m = classify("England v Serbia", description = "Live coverage of the World Cup qualifier from Wembley.", channel = "ITV1")!!
        assertEquals(Sport.FOOTBALL, m.sport)
        assertEquals(AiringKind.LIVE, m.kind)
    }

    @Test
    fun highlightsMagazinesAndReplaysAreToldApart() {
        assertEquals(AiringKind.HIGHLIGHTS, classify("Match of the Day", "Sport", channel = "BBC One")!!.kind)
        assertEquals(AiringKind.HIGHLIGHTS, classify("Premier League Highlights", channel = "Sky Sports Premier League")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Football Focus", "Sport", channel = "BBC One")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Sky Sports News", "Sport", channel = "Sky Sports News")!!.kind)
        assertEquals(AiringKind.REPLAY, classify("Classic Premier League: Arsenal v Chelsea (2004)")!!.kind)
        assertEquals(AiringKind.REPLAY, classify("Premier League: Arsenal v Chelsea (R)")!!.kind)
        assertEquals(AiringKind.EVENT, classify("Premier League: Arsenal v Chelsea")!!.kind)
    }

    @Test
    fun formulaOneSessions() {
        val race = classify("Live Formula 1: Brazilian Grand Prix - Race", channel = "Sky Sports F1")!!
        assertEquals(Sport.F1, race.sport)
        assertEquals(AiringKind.LIVE, race.kind)
        assertEquals("Brazilian Grand Prix", race.title)
        assertEquals("Race", race.session)
        assertEquals("Qualifying", classify("F1: Brazilian GP Qualifying")!!.session)
        assertEquals("Practice 1", classify("Formula 1: Brazilian GP - FP1")!!.session)
        assertEquals("Race", classify("Formula 2: Feature Race")!!.session)
    }

    @Test
    fun grandPrixIsWhicheverSportTheRestSays() {
        assertEquals(Sport.MOTOGP, classify("MotoGP: Valencia Grand Prix")!!.sport)
        assertEquals(Sport.MOTOGP, classify("Valencia Grand Prix - Race", description = "Live coverage of the MotoGP race in Valencia.", channel = "TNT Sports 2")!!.sport)
        assertEquals(Sport.F1, classify("Brazilian Grand Prix", channel = "Sky Sports F1")!!.sport)
        assertEquals(Sport.DARTS, classify("World Grand Prix", channel = "Sky Sports Main Event", month = 10)!!.sport)
    }

    @Test
    fun namesTwoSportsShareGoByMonthOrChannel() {
        assertEquals(Sport.TENNIS, classify("US Open Tennis")!!.sport)
        assertEquals(Sport.GOLF, classify("Live US Open", channel = "Sky Sports Golf")!!.sport)
        assertEquals(Sport.TENNIS, classify("Live US Open", "Sport", month = 9)!!.sport)
        assertEquals(Sport.GOLF, classify("The Masters", "Sport", month = 4)!!.sport)
        assertEquals(Sport.SNOOKER, classify("The Masters", "Sport", month = 1)!!.sport)
        assertEquals(Sport.SNOOKER, classify("Live Snooker: Scottish Open")!!.sport)
        assertEquals(Sport.GOLF, classify("Live Golf: Genesis Scottish Open - Day 2")!!.sport)
        assertEquals("Day 2", classify("Live Golf: Genesis Scottish Open - Day 2")!!.session)
    }

    @Test
    fun individualSports() {
        val darts = classify("Grand Slam of Darts: Littler v Price")!!
        assertEquals(Sport.DARTS, darts.sport)
        assertEquals("Littler", darts.home)
        assertEquals(Sport.DARTS, classify("Live: Littler v Humphries", channel = "Sky Sports Arena")!!.sport)
        assertEquals(Sport.DARTS, classify("Premier League Darts: Night 12")!!.sport)
        assertEquals("Grand Slam of Darts", classify("Live Darts: Grand Slam of Darts")!!.title)

        val tennis = classify("Tennis: ATP Finals - Sinner v Zverev")!!
        assertEquals(Sport.TENNIS, tennis.sport)
        assertEquals("ATP Finals", tennis.competition)
        assertEquals("Sinner v Zverev", tennis.title)

        val snooker = classify("Live Snooker: Scottish Open")!!
        assertEquals("Scottish Open", snooker.title)
        assertEquals(AiringKind.LIVE, snooker.kind)
    }

    @Test
    fun boxingWithOrWithoutTheWord() {
        assertEquals(Sport.BOXING, classify("Live Boxing: Joshua v Dubois")!!.sport)
        assertEquals(Sport.BOXING, classify("Fury v Usyk: Heavyweight Title Fight")!!.sport)
        assertEquals(Sport.OTHER, classify("UFC Fight Night: Main Card", "Sport")!!.sport)
        assertEquals(Sport.FOOTBALL, classify("Boxing Day Football: Arsenal v Brentford")!!.sport)
    }

    @Test
    fun otherSportsAreOther() {
        val rugby = classify("Live Rugby Union: England v New Zealand", channel = "TNT Sports 1")!!
        assertEquals(Sport.OTHER, rugby.sport)
        assertEquals(AiringKind.LIVE, rugby.kind)
        assertEquals(Sport.OTHER, classify("Table Tennis: World Championships")!!.sport)
        assertEquals(Sport.OTHER, classify("Live NFL: Chiefs v Bills")!!.sport)
        assertEquals(Sport.OTHER, classify("Live: England v New Zealand", "Sport", description = "Autumn Nations Series rugby from Allianz Stadium.")!!.sport)
    }

    @Test
    fun aSportsChannelAloneNeedsMoreToBeAnEvent() {
        assertEquals(AiringKind.MAGAZINE, classify("Ted's Notebook", channel = "Sky Sports F1")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Good Morning Sports Fans", channel = "Sky Sports Main Event")!!.kind)
        val live = classify("Live Racing from Cheltenham", channel = "Sky Sports Racing")!!
        assertEquals(AiringKind.LIVE, live.kind)
    }

    @Test
    fun filmsAndShowsAreNotSport() {
        assertNull(classify("Bend It Like Beckham", "Film", description = "A football-mad girl..."))
        assertNull(classify("Man v Food", "Entertainment"))
        assertNull(classify("EastEnders", "Soap"))
        assertNull(classify("The One Show", channel = "BBC One"))
    }

    @Test
    fun candidatesAreWorthReadingTheDescriptionOf() {
        assertFalse(c.isCandidate("EastEnders", "Soap", SportClassifier.ChannelHint.NONE))
        assertTrue(c.isCandidate("Arsenal v Chelsea", null, SportClassifier.ChannelHint.NONE))
        assertTrue(c.isCandidate("Anything", null, c.channel("TNT Sports 3")))
        assertTrue(c.isCandidate("Anything", "Sports", SportClassifier.ChannelHint.NONE))
        assertTrue(c.isCandidate("Live Snooker", null, SportClassifier.ChannelHint.NONE))
    }

    @Test
    fun channelNames() {
        assertEquals(Sport.F1, c.channel("Sky Sports F1 HD").sport)
        assertEquals(Sport.GOLF, c.channel("UK: Sky Sports Golf FHD").sport)
        assertEquals(Sport.FOOTBALL, c.channel("LaLiga TV").sport)
        assertTrue(c.channel("TNT Sports 1").isSport)
        assertNull(c.channel("TNT Sports 1").sport)
        assertFalse(c.channel("BBC One HD").isSport)
    }

    // From a real UK guide (Sky, TNT Sports, Premier Sports, the BBC and ITV).

    @Test
    fun seasonsAndPastYearsAreReplays() {
        assertEquals(AiringKind.REPLAY, classify("EFL 26/27: West Brom v Millwall", "Sports, Football", channel = "Sky Sports Football HD", year = 2026)!!.kind)
        assertEquals(AiringKind.REPLAY, classify("Premier League Years 1999/00", "Sports, Football", year = 2026)!!.kind)
        assertEquals(AiringKind.REPLAY, classify("ICC Women's WC: ENG v SA 2017", "Sports, Cricket", year = 2026)!!.kind)
        assertEquals(AiringKind.REPLAY, classify("UFC Fight Flashback", channel = "TNT Sports 3 HD")!!.kind)
        assertEquals(AiringKind.HIGHLIGHTS, classify("PL: Ipswich v Bournemouth Hlts", "Sports, Football")!!.kind)
        // Not a date, nor this year.
        assertEquals(AiringKind.LIVE, classify("Live EFL 15/11: Leeds v Burnley", year = 2026)!!.kind)
        assertEquals(AiringKind.LIVE, classify("Live: Premier League 2026", year = 2026)!!.kind)
    }

    @Test
    fun theCategoryAloneNeedsMoreToBeAnEvent() {
        assertEquals(AiringKind.MAGAZINE, classify("Player Spotlight", "Sports, Football", channel = "MUTV HD")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Sacked in the Morning", "Sports, Football", channel = "BBC Scotland HD")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Dark Side of the Ring", null, "Wrestling documentary series.", channel = "ITV4 HD")!!.kind)
        // A club's channel shows its matches again.
        assertEquals(AiringKind.REPLAY, classify("Crystal Palace v Liverpool: The Match", "Sports, Football", channel = "LFCTV HD")!!.kind)
    }

    @Test
    fun newsChannelsAndShowsAreMagazines() {
        assertEquals(AiringKind.MAGAZINE, classify("Lunchtime Live", "Sports, Baseball", channel = "Sky Sports News HD")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Live: Sky Sports FC", "Sports, Football", channel = "Sky Sports Premier League HD")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Sports", "Sports", "Tennis: the latest from Turin.", channel = "FRANCE 24 HD")!!.kind)
        assertEquals(AiringKind.MAGAZINE, classify("Premier League Legends", channel = "TNT Sports 1 HD")!!.kind)
    }

    @Test
    fun liveMatchesTheirSidesInTheDescription() {
        val cup = classify("Live Emirates FA Cup", null, "Plymouth Parkway v Berkhamsted (Kick-off 7.30pm). Coverage of the first-round match from Manadon Sports Hub.", channel = "TNT Sports 1 HD")!!
        assertEquals(Sport.FOOTBALL, cup.sport)
        assertEquals(AiringKind.LIVE, cup.kind)
        assertEquals("Plymouth Parkway v Berkhamsted", cup.title)
        assertEquals("Emirates FA Cup", cup.competition)
        assertEquals("Sheffield United", classify("EFL Highlights", null, "Charlton Athletic v Sheffield United in the Championship.", channel = "Sky Sports Football HD")!!.away)
        assertEquals("Chelsea v Everton", classify("WSL", null, "Action from Chelsea v Everton.", channel = "Sky Sports Football HD")!!.title)
        val motd = classify("MOTD Live: Altrincham v Notts County", "Sport", channel = "BBC One HD")!!
        assertEquals(AiringKind.LIVE, motd.kind)
        assertEquals(Sport.FOOTBALL, motd.sport)
    }

    @Test
    fun otherFootballAndOtherLeagues() {
        assertEquals(Sport.FOOTBALL, classify("Women's Super League: London City v Birmingham City", "Sport")!!.sport)
        assertEquals(Sport.FOOTBALL, classify("NWSL", null, "San Diego Wave v Utah Royals in the first quarter-final.", channel = "TNT Sports 2 HD")!!.sport)
        assertEquals(Sport.OTHER, classify("Live Wheelchair RLWC26", null, "Coverage from the third semi-final (Kick-off TBA).", channel = "Premier Sports 2 HD")!!.sport)
        assertEquals(Sport.OTHER, classify("Good Morning Football", "Sports, American Football", channel = "Sky Sports NFL")!!.sport)
        val nfl = classify("Live: NFL Buccaneers @ Bears", "Sports, American Football", channel = "Sky Sports Main Event")!!
        assertEquals(Sport.OTHER, nfl.sport)
        assertEquals("Buccaneers", nfl.home)
    }

    @Test
    fun superscriptLetters() {
        val m = classify("Premier League: Arsenal v Chelsea ᴸᶦᵛᵉ")!!
        assertEquals(AiringKind.LIVE, m.kind)
        assertEquals("Chelsea", m.away)
        assertEquals(Sport.F1, c.channel("Sky Sports F1 ᴴᴰ").sport)
    }
}
