package app.tellycine.core.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Events providers put in their event channels' names. */
class EventChannelsTest {
    private val uk = ZoneId.of("Europe/London")

    /** Saturday 14 November 2026, 10:00 in the UK (GMT). */
    private val now = ZonedDateTime.of(2026, 11, 14, 10, 0, 0, 0, uk).toInstant().toEpochMilli()

    private fun at(day: Int, hour: Int, minute: Int = 0, zone: ZoneId = uk) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun aMatchWithAKickOffTime() {
        val e = EventChannels.parse("EPL 03: Arsenal v Chelsea 15:00", now)!!
        assertEquals(Sport.FOOTBALL, e.sport)
        assertEquals("Arsenal v Chelsea", e.title)
        assertEquals("Arsenal", e.home)
        assertEquals("Chelsea", e.away)
        assertEquals(at(14, 15), e.startMs)
    }

    @Test
    fun slotsTimeZonesAndDays() {
        assertEquals(at(14, 20), EventChannels.parse("UCL 04: Arsenal v Inter @ 20:00", now)!!.startMs)
        val us = EventChannels.parse("USA Soccer01: Liverpool vs Arsenal @ 12:30pm ET", now)!!
        assertEquals("Liverpool v Arsenal", us.title)
        assertEquals(at(14, 12, 30, ZoneId.of("America/New_York")), us.startMs)
        val boxing = EventChannels.parse("PPV 1 | Joshua vs Dubois | Sat 22:00 UK", now)!!
        assertEquals(Sport.BOXING, boxing.sport)
        assertEquals(at(14, 22), boxing.startMs)
        assertEquals(at(15, 16, 30), EventChannels.parse("LIVE EVENT 5 - Man City v Spurs 15/11 16:30", now)!!.startMs)
        assertEquals("Man City v Spurs", EventChannels.parse("EVENT 5 Man City v Spurs 16:30", now)!!.title)
    }

    @Test
    fun aTimeLongGoneIsTomorrows() {
        val late = ZonedDateTime.of(2026, 11, 14, 23, 0, 0, 0, uk).toInstant().toEpochMilli()
        assertEquals(at(14, 21), EventChannels.parse("EPL 01: Leeds v Burnley 21:00", late)!!.startMs)
        assertEquals(at(15, 10), EventChannels.parse("EPL 01: Leeds v Burnley 10:00", late)!!.startMs)
    }

    @Test
    fun eventsWithoutSides() {
        val f1 = EventChannels.parse("F1 TV: Brazilian GP - Race 17:00", now)!!
        assertEquals(Sport.F1, f1.sport)
        assertEquals("Brazilian GP · Race", f1.title)
        assertNull(f1.home)
    }

    @Test
    fun withoutATimeItsStillAnEvent() {
        val e = EventChannels.parse("EPL 05: Arsenal v Chelsea", now)!!
        assertNull(e.startMs)
        assertEquals("Arsenal v Chelsea", e.title)
    }

    @Test
    fun ordinaryChannelsAndEmptySlotsAreNot() {
        assertNull(EventChannels.parse("EPL 02 : No Event Streaming", now))
        assertNull(EventChannels.parse("Sky Sports Premier League HD", now))
        assertNull(EventChannels.parse("BBC One", now))
        assertNull(EventChannels.parse("PPV 3 | ---", now))
    }

    // As an IPTVBoss-style provider names them.

    @Test
    fun londonTimesAndCompetitionsBeforeTheSides() {
        val e = EventChannels.parse("Sky Sports + 05: EFL EFL Trophy Barnet v L Orient @ 10 Nov 06:45 PM London", now)!!
        assertEquals(Sport.FOOTBALL, e.sport)
        assertEquals("Barnet v L Orient", e.title)
        assertEquals("EFL Trophy", e.competition)
        assertEquals(at(10, 18, 45), e.startMs)
        val tennis = EventChannels.parse("Sky Sports + 01: ATP Tour ATP Tennis A. Michelsen v J. Cerundolo @ 10 Nov 11:55 AM London", now)!!
        assertEquals(Sport.TENNIS, tennis.sport)
        assertEquals("A. Michelsen v J. Cerundolo", tennis.title)
    }

    @Test
    fun americanDatesAndAmericanFootball() {
        val soccer = EventChannels.parse("ESPN UNLTD 037: Soccer: Bucknell vs. American (11.10 12:00 PM ET)", now)!!
        assertEquals(Sport.FOOTBALL, soccer.sport)
        assertEquals("Bucknell v American", soccer.title)
        assertEquals(at(10, 12, 0, ZoneId.of("America/New_York")), soccer.startMs)
        assertEquals(Sport.OTHER, EventChannels.parse("ESPN UNLTD 169: Football: Kent State vs. Bowling Green (11.10 07:00 PM ET)", now)!!.sport)
        assertEquals(Sport.OTHER, EventChannels.parse("TSN+ 08: NCAA Football: Ohio vs. Miami (OH) @ 10 Nov 08:00 PM ET", now)!!.sport)
        assertEquals(Sport.OTHER, EventChannels.parse("(UK) Dazn  01: Basketball Champions League: Sabah BC vs Trabzonspor Nov 10 11:00 AM", now)!!.sport)
        val nba = EventChannels.parse("NBA LP 05 | Cavaliers @ Hornets (Mobile View (In-Arena)) (2026-11-10 00:00:00)", now)!!
        assertEquals("Cavaliers v Hornets", nba.title)
    }

    @Test
    fun notEvents() {
        assertNull(EventChannels.parse("(UK) Dazn  12: No Event Scheduled", now))
        assertNull(EventChannels.parse("Sky Sports + 28: NO EVENT", now))
        assertNull(EventChannels.parse("ESPN UNLTD 062: Football: Iowa State Football Press Conference (11.10 12:55 PM ET)", now))
    }
}
