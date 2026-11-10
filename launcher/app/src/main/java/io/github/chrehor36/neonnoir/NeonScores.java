package io.github.chrehor36.neonnoir;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores for your teams from ESPN's public scoreboard feed (unofficial, no account): the live game with its box
 * score, the last final, and the next game. Teams come from secrets.properties SCORES_TEAMS as sport/league/team.
 */
final class NeonScores {
    static final class Side {
        final String abbr, name, score;
        final List<String> lines;
        /** A tennis player's ranking or a college team's AP rank (0 when unranked), and full name (SCORES_PLAYERS). */
        int rank;
        String fullName = "";
        /** Won the finished game or match. */
        boolean winner;
        /** The season record, "6-2" (team sports; may be empty). */
        String record = "";

        Side(String abbr, String name, String score, List<String> lines) {
            this.abbr = abbr;
            this.name = name;
            this.score = score;
            this.lines = lines;
        }
    }

    static final class Game {
        final String id, state, detail;
        final long startMs;
        final Side away, home;
        String leaders = "";
        /** For the "around" row: "NFC", or the tournament and round for tennis. */
        String label = "";
        boolean tennis;
        /** A racing session or race (SCORES_RACING): sides are P1 and P2, or, for the next race, its name. */
        boolean racing;
        /** For racing: the session ("Race", "Qualifying", "Practice 2"). */
        String session = "";
        /** A followed player's match (SCORES_PLAYERS): only these take over the Home chip. */
        boolean followed;
        /** In a card, what leads the status line: the round ("Round 2"), the session ("Qualifying"), or a race. */
        String tag = "";
        /** A tennis match's tournament ("Paris Masters"). */
        String event = "";

        /** The better ranking of the two players (999 when neither is ranked): how "top" a tennis match is. */
        int bestRank() {
            int a = away.rank > 0 ? away.rank : 999, b = home.rank > 0 ? home.rank : 999;
            return Math.min(a, b);
        }

        Game(String id, String state, String detail, long startMs, Side away, Side home) {
            this.id = id;
            this.state = state;
            this.detail = detail;
            this.startMs = startMs;
            this.away = away;
            this.home = home;
        }

        boolean live() {
            return "in".equals(state);
        }

        boolean done() {
            return "post".equals(state);
        }
    }

    /** One team's picture: any of the three may be null. */
    static final class Team {
        final String key;
        Game live, last, next;
        /** "Celtics", "3-5", "4th in Atlantic Division" (any may be empty). */
        String name = "", record = "", standing = "";

        Team(String key) {
            this.key = key;
        }

        /**
         * In season: a game live, or one within 10 days either side. Ten days covers an NFL bye week and
         * baseball's All-Star break; the months between seasons don't qualify.
         */
        boolean inSeason(long now) {
            long window = 10L * 24 * 3600_000L;
            return live != null || (last != null && now - last.startMs < window)
                    || (next != null && next.startMs - now < window);
        }
    }

    private NeonScores() {
    }

    /** Background thread. Empty when no teams are set or nothing could be read. */
    static List<Team> load(Context ctx) {
        List<Team> out = new ArrayList<>();
        for (String key : NeonConfig.teams()) {
            Team t = team(ctx, key);
            if (t != null) out.add(t);
        }
        return out;
    }

    // ---- More's cards: one per conference (SCORES_GROUPS), tour (SCORES_PLAYERS) and series (SCORES_RACING)

    /** A card on More, titled with just the conference, tournament or race ("NFC", "Paris Masters", "São Paulo GP"). */
    static final class Card {
        final String title;
        final List<Game> games = new ArrayList<>();

        Card(String title) {
            this.title = title;
        }
    }

    /** Games per card. */
    static final int CARD_GAMES = 3;

    /**
     * The cards, in this order: each conference (your own teams in that league first, then its biggest games), a
     * league card for teams without a conference set (MLB in the summer, in season only), each tennis tour's main
     * tournament (the followed players first), each racing series. A card with nothing to show is left out.
     * Background thread.
     */
    static List<Card> cards(Context ctx, List<Team> teams) {
        List<Card> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<String> covered = new ArrayList<>();
        for (String key : NeonConfig.groups()) {
            String[] p = key.split("/");
            String league = p[0] + "/" + p[1];
            Card c = new Card(groupLabel(p));
            if (!covered.contains(league)) {
                covered.add(league);
                c.games.addAll(teamGames(teams, league, now));
            }
            // Your own teams in this league: their games come first (above); the scoreboard's copy of the same
            // game replaces it, since that one has both teams' records.
            List<String> mine = new ArrayList<>();
            for (String t : NeonConfig.teams()) {
                if (t.startsWith(league + "/")) mine.add(t.substring(t.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT));
            }
            for (Game g : groupGames(ctx, key)) {
                int at = indexOf(c.games, g);
                if (at >= 0) {
                    c.games.set(at, g);
                    continue;
                }
                if (mine.contains(g.away.abbr) || mine.contains(g.home.abbr)) continue;
                if (c.games.size() < CARD_GAMES) c.games.add(g);
            }
            if (!c.games.isEmpty()) out.add(c);
        }
        for (String key : NeonConfig.teams()) {
            String[] p = key.split("/");
            String league = p[0] + "/" + p[1];
            if (covered.contains(league)) continue;
            covered.add(league);
            Card c = new Card(p[1].toUpperCase(Locale.ROOT));
            c.games.addAll(teamGames(teams, league, now));
            if (!c.games.isEmpty()) out.add(c);
        }
        List<String> tours = new ArrayList<>();
        for (String key : NeonConfig.players()) {
            String[] p = key.split("/");
            String tour = p[0] + "/" + p[1];
            if (p[0].equals("tennis") && !tours.contains(tour)) tours.add(tour);
        }
        for (String tour : tours) {
            List<Game> games = new ArrayList<>();
            for (String key : NeonConfig.players()) {
                if (!key.startsWith(tour + "/")) continue;
                Game g = playerMatch(ctx, key);
                // A followed player's finished match stays for two days, not all week after they're out.
                if (g != null && !(g.done() && now - g.startMs > 2 * 24 * 3600_000L)) games.add(g);
            }
            List<Game> top = topMatches(ctx, tour, CARD_GAMES + games.size());
            for (Game g : top) {
                if (games.size() < CARD_GAMES && !contains(games, g)) games.add(g);
            }
            if (games.isEmpty()) continue;
            Card c = new Card(!top.isEmpty() ? top.get(0).event : games.get(0).event);
            for (Game g : games) {
                // A followed player at a smaller event that week: their tournament leads the line instead.
                if (!g.event.equals(c.title)) g.tag = g.event;
            }
            c.games.addAll(games.size() > CARD_GAMES ? games.subList(0, CARD_GAMES) : games);
            out.add(c);
        }
        for (String league : NeonConfig.racing()) {
            Card c = raceCard(ctx, league);
            if (c != null && !c.games.isEmpty()) out.add(c);
        }
        return out;
    }

    private static boolean contains(List<Game> games, Game g) {
        return indexOf(games, g) >= 0;
    }

    private static int indexOf(List<Game> games, Game g) {
        for (int i = 0; i < games.size(); i++) if (games.get(i).id.equals(g.id)) return i;
        return -1;
    }

    /**
     * Your teams' games in a league, in season: live ones, then the next (within 10 days), then the last (within
     * 8 days). Several teams (the Braves and the Cubs) share the card.
     */
    private static List<Game> teamGames(List<Team> teams, String league, long now) {
        List<Game> live = new ArrayList<>(), next = new ArrayList<>(), last = new ArrayList<>();
        long day = 24 * 3600_000L;
        for (Team t : teams == null ? new ArrayList<Team>() : teams) {
            if (!t.key.startsWith(league + "/")) continue;
            if (t.live != null) live.add(t.live);
            else if (t.next != null && t.next.startMs - now < 10 * day) next.add(t.next);
            if (t.last != null && now - t.last.startMs < 8 * day) last.add(t.last);
        }
        List<Game> out = new ArrayList<>(live);
        for (Game g : next) if (!contains(out, g)) out.add(g);
        for (Game g : last) if (!contains(out, g)) out.add(g);
        return out.size() > CARD_GAMES ? new ArrayList<>(out.subList(0, CARD_GAMES)) : out;
    }

    /** ESPN's group number for a conference name: nfc, afc; sec, acc, big-ten, big-12; or the number itself. */
    private static String groupId(String league, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (league.equals("nfl")) return n.equals("nfc") ? "7" : n.equals("afc") ? "8" : n;
        if (league.equals("college-football")) {
            switch (n) {
                case "acc": return "1";
                case "big-12": return "4";
                case "big-ten": return "5";
                case "sec": return "8";
                default: return n;
            }
        }
        return n;
    }

    /** The card's title: "NFC", "SEC", "BIG TEN"; a bare group number shows the league. */
    private static String groupLabel(String[] p) {
        return p[2].matches("\\d+") ? p[1].toUpperCase(Locale.ROOT) : p[2].replace('-', ' ').toUpperCase(Locale.ROOT);
    }

    /**
     * The racing series' card. During a race weekend: titled with the race, the live session, the next one and
     * the latest results. Otherwise titled with the next race: its start time, and the last race's top two for
     * three weeks after it.
     */
    private static Card raceCard(Context ctx, String league) {
        String body = NeonNet.text(ctx, "https://site.api.espn.com/apis/site/v2/sports/racing/" + league + "/scoreboard", null, 60_000L);
        if (body == null) return null;
        String series = league.toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis(), day = 24 * 3600_000L;
        try {
            JSONObject j = new JSONObject(body);
            JSONArray events = j.optJSONArray("events");
            String gp = "";
            Game lastRace = null;
            if (events != null && events.length() > 0) {
                JSONObject ev = events.getJSONObject(0);
                long start = parseTime(ev.optString("date", "")), end = parseTime(ev.optString("endDate", ""));
                gp = raceName(ev.optString("name", ""));
                JSONArray comps = ev.optJSONArray("competitions");
                List<Game> live = new ArrayList<>(), pre = new ArrayList<>(), done = new ArrayList<>();
                for (int i = 0; comps != null && i < comps.length(); i++) {
                    Game g = session(comps.getJSONObject(i), series, gp);
                    if (g == null) continue;
                    (g.live() ? live : g.done() ? done : pre).add(g);
                    if (g.session.equals("Race") && g.done()) lastRace = g;
                }
                if (now >= start && now <= end + 6 * 3600_000L && (!live.isEmpty() || !done.isEmpty())) {
                    pre.sort((a, b) -> Long.compare(a.startMs, b.startMs));
                    done.sort((a, b) -> Long.compare(b.startMs, a.startMs));
                    Card c = new Card(gp);
                    c.games.addAll(live);
                    if (!pre.isEmpty()) c.games.add(pre.get(0));
                    for (Game g : done) if (c.games.size() < CARD_GAMES) c.games.add(g);
                    if (c.games.size() > CARD_GAMES) c.games.subList(CARD_GAMES, c.games.size()).clear();
                    return c;
                }
                if (now <= end && !pre.isEmpty()) {
                    // The coming weekend: the next session, then the sprint, qualifying and the race, in order.
                    pre.sort((a, b) -> Long.compare(a.startMs, b.startMs));
                    List<Game> key = new ArrayList<>();
                    for (Game g : pre) {
                        if (g == pre.get(0) || g.session.equals("Sprint") || g.session.equals("Qualifying")
                                || g.session.equals("Race")) key.add(g);
                    }
                    Card c = new Card(gp);
                    c.games.addAll(key.size() > CARD_GAMES ? key.subList(key.size() - CARD_GAMES, key.size()) : key);
                    return c;
                }
            }
            if (lastRace != null && now - lastRace.startMs > 21 * day) lastRace = null;
            if (lastRace != null) lastRace.tag = gp;
            Game next = null;
            JSONArray leagues = j.optJSONArray("leagues");
            JSONArray cal = leagues == null || leagues.length() == 0 ? null : leagues.getJSONObject(0).optJSONArray("calendar");
            for (int i = 0; cal != null && i < cal.length(); i++) {
                JSONObject c = cal.getJSONObject(i);
                long end = parseTime(c.optString("endDate", ""));
                if (end <= now) continue;
                String name = raceName(c.optString("label", ""));
                next = new Game(series + "-" + c.optString("startDate"), "pre", "", raceStart(ctx, c, end),
                        new Side("", name, "", new ArrayList<>()), new Side("", "", "", new ArrayList<>()));
                next.racing = true;
                next.session = "Race";
                next.tag = "Race";
                break;
            }
            if (next == null && lastRace == null) return null;
            Card c = new Card(next != null ? next.away.name : gp);
            if (next != null) c.games.add(next);
            if (lastRace != null) c.games.add(lastRace);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Racing: SCORES_RACING (f1) ------------------------------------------------------------------------

    /** Each racing series' panel (see race()). Background thread. */
    static List<Game> races(Context ctx) {
        List<Game> out = new ArrayList<>();
        for (String league : NeonConfig.racing()) {
            Game g = race(ctx, league);
            if (g != null) out.add(g);
        }
        return out;
    }

    /**
     * During a race weekend: the live session, else the latest finished one (top two). For three days after
     * the race: its result. Otherwise the next race on the calendar, with its start time. Nothing between
     * seasons once the last result is three days old.
     */
    private static Game race(Context ctx, String league) {
        String base = "https://site.api.espn.com/apis/site/v2/sports/racing/" + league;
        String body = NeonNet.text(ctx, base + "/scoreboard", null, 60_000L);
        if (body == null) return null;
        String series = league.toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis(), day = 24 * 3600_000L;
        try {
            JSONObject j = new JSONObject(body);
            JSONArray events = j.optJSONArray("events");
            if (events != null && events.length() > 0) {
                JSONObject ev = events.getJSONObject(0);
                long start = parseTime(ev.optString("date", "")), end = parseTime(ev.optString("endDate", ""));
                String gp = raceName(ev.optString("name", ""));
                JSONArray comps = ev.optJSONArray("competitions");
                Game latest = null, race = null;
                for (int i = 0; comps != null && i < comps.length(); i++) {
                    Game g = session(comps.getJSONObject(i), series, gp);
                    if (g == null) continue;
                    if (g.live()) return g;
                    if (g.done() && (latest == null || g.startMs > latest.startMs)) latest = g;
                    if (g.session.equals("Race")) race = g;
                }
                if (latest != null && now >= start && now <= end + 6 * 3600_000L) return latest;
                if (race != null && race.done() && now - race.startMs < 3 * day) return race;
            }
            // Between weekends: the next race on the calendar.
            JSONArray leagues = j.optJSONArray("leagues");
            JSONArray cal = leagues == null || leagues.length() == 0 ? null : leagues.getJSONObject(0).optJSONArray("calendar");
            for (int i = 0; cal != null && i < cal.length(); i++) {
                JSONObject c = cal.getJSONObject(i);
                long end = parseTime(c.optString("endDate", ""));
                if (end <= now) continue;
                long raceStart = raceStart(ctx, c, end);
                String gp = raceName(c.optString("label", ""));
                Game g = new Game(series + "-" + c.optString("startDate"), "pre", "", raceStart,
                        new Side("", gp, "", new ArrayList<>()), new Side("", "", "", new ArrayList<>()));
                g.racing = true;
                g.session = "Race";
                g.label = series + " · Next race";
                return g;
            }
        } catch (Exception ignored) {
            // Nothing to show.
        }
        return null;
    }

    /** A session (practice, qualifying, sprint, race) with its top two; null before anyone has a position. */
    private static Game session(JSONObject comp, String series, String gp) {
        try {
            JSONObject typeObj = comp.optJSONObject("type");
            String abbr = typeObj == null ? "" : typeObj.optString("abbreviation", "");
            String name = abbr.matches("FP\\d") ? "Practice " + abbr.substring(2) : abbr.equals("Qual") ? "Qualifying"
                    : abbr.equals("SQ") ? "Sprint Qualifying" : abbr.isEmpty() ? "Session" : abbr;
            JSONObject type = comp.getJSONObject("status").getJSONObject("type");
            String state = type.optString("state", "pre");
            Side[] top = new Side[2];
            JSONArray cs = comp.optJSONArray("competitors");
            for (int i = 0; cs != null && i < cs.length(); i++) {
                JSONObject c = cs.getJSONObject(i);
                int order = c.optInt("order", 0);
                if (order < 1 || order > 2) continue;
                JSONObject a = c.optJSONObject("athlete");
                String who = a == null ? "" : a.optString("shortName", a.optString("displayName", ""));
                top[order - 1] = new Side("", who, "P" + order, new ArrayList<>());
            }
            if (!"pre".equals(state) && (top[0] == null || top[1] == null)) return null;
            if (top[0] == null) top[0] = new Side("", "", "", new ArrayList<>());
            if (top[1] == null) top[1] = new Side("", "", "", new ArrayList<>());
            Game g = new Game(comp.optString("id"), state, type.optString("shortDetail", ""),
                    parseTime(comp.optString("date", "")), top[0], top[1]);
            g.racing = true;
            g.session = name;
            g.label = series + " " + gp + " · " + name;
            g.tag = name;
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    /** The race's own start time from ESPN's event details (cached for hours); else a guess from the calendar. */
    private static long raceStart(Context ctx, JSONObject calEntry, long end) {
        try {
            JSONObject ref = calEntry.optJSONObject("event");
            String url = ref == null ? "" : ref.optString("$ref", "");
            if (!url.isEmpty()) {
                url = url.replace("http://", "https://").replace(".espn.pvt/", ".espn.com/");
                String body = NeonNet.text(ctx, url, null, 6 * 3600_000L);
                JSONArray comps = body == null ? null : new JSONObject(body).optJSONArray("competitions");
                for (int i = 0; comps != null && i < comps.length(); i++) {
                    JSONObject c = comps.getJSONObject(i);
                    JSONObject type = c.optJSONObject("type");
                    if (type != null && "Race".equals(type.optString("abbreviation"))) return parseTime(c.optString("date", ""));
                }
            }
        } catch (Exception ignored) {
            // Fall back below.
        }
        return end - 2 * 3600_000L;
    }

    /** "Qatar Airways Australian Grand Prix" -> "Australian GP": sponsors off the front, "GP" for short. */
    static String raceName(String full) {
        String s = full.replace("Grand Prix", "GP").trim();
        String[] sponsors = {"Qatar Airways", "Singapore Airlines", "Etihad Airways", "Gulf Air", "MSC Cruises",
                "Moët & Chandon", "Tag Heuer", "TAG Heuer", "Crypto.com", "Heineken", "Aramco", "Lenovo", "Pirelli",
                "Rolex", "STC", "AWS", "DHL", "Louis Vuitton", "Formula 1"};
        boolean cut = true;
        while (cut) {
            cut = false;
            for (String sp : sponsors) {
                if (s.startsWith(sp + " ")) {
                    s = s.substring(sp.length() + 1).trim();
                    cut = true;
                }
            }
        }
        return s;
    }

    /** The followed players' matches (SCORES_PLAYERS): each one's live match, else next, else latest. */
    static List<Game> playerMatches(Context ctx) {
        List<Game> out = new ArrayList<>();
        for (String key : NeonConfig.players()) {
            Game g = playerMatch(ctx, key);
            if (g != null) out.add(g);
        }
        return out;
    }

    /**
     * One conference's games this week, biggest first: live, then upcoming, then finals. "Biggest" is the two
     * teams' poll ranks where there are polls (college), else their records, else the start time.
     */
    private static List<Game> groupGames(Context ctx, String key) {
        List<Game> live = new ArrayList<>(), pre = new ArrayList<>(), post = new ArrayList<>();
        String[] p = key.split("/");
        String body = NeonNet.text(ctx, "https://site.api.espn.com/apis/site/v2/sports/" + p[0] + "/" + p[1]
                + "/scoreboard?groups=" + NeonNet.enc(groupId(p[1], p[2])), null, 60_000L);
        if (body == null) return live;
        try {
            JSONArray events = new JSONObject(body).optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                Game g = game(events.getJSONObject(i));
                if (g == null) continue;
                g.label = groupLabel(p);
                (g.live() ? live : g.done() ? post : pre).add(g);
            }
        } catch (Exception ignored) {
            // Whatever was read.
        }
        java.util.Comparator<Game> big = (a, b) -> {
            int r = Integer.compare(rankSum(a), rankSum(b));
            return r != 0 ? r : Double.compare(winPct(b.away) + winPct(b.home), winPct(a.away) + winPct(a.home));
        };
        live.sort(big);
        pre.sort(big.thenComparingLong(g -> g.startMs));
        post.sort(big.thenComparing((a, b) -> Long.compare(b.startMs, a.startMs)));
        List<Game> out = new ArrayList<>(live);
        out.addAll(pre);
        out.addAll(post);
        return out;
    }

    /** Both teams' poll ranks added up, an unranked team counting as 40: lower is a bigger game. */
    private static int rankSum(Game g) {
        return (g.away.rank > 0 ? g.away.rank : 40) + (g.home.rank > 0 ? g.home.rank : 40);
    }

    /** "6-2" or "4-2-1" as a share of games won (0 when unknown). */
    private static double winPct(Side s) {
        String[] n = s.record.split("-");
        try {
            double w = Double.parseDouble(n[0].trim()), total = 0;
            for (String x : n) total += Double.parseDouble(x.trim());
            return total > 0 ? w / total : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** This week's events on the tour (tennis/atp), or null. */
    private static JSONArray tennisEvents(Context ctx, String tour) {
        String body = NeonNet.text(ctx, "https://site.api.espn.com/apis/site/v2/sports/" + tour + "/scoreboard", null, 60_000L);
        if (body == null) return null;
        try {
            return new JSONObject(body).optJSONArray("events");
        } catch (Exception e) {
            return null;
        }
    }

    /** A tennis match from ESPN's competition, with set scores and rankings; null for a TBD pairing. */
    private static Game tennisGame(JSONObject comp, String tournament) {
        try {
            JSONArray cs = comp.optJSONArray("competitors");
            if (cs == null || cs.length() != 2) return null;
            Side[] sides = new Side[2];
            for (int s = 0; s < 2; s++) {
                JSONObject c = cs.getJSONObject(s);
                JSONObject a = c.optJSONObject("athlete");
                String full = a == null ? "" : a.optString("displayName", "");
                if (full.isEmpty() || full.equals("TBD")) return null;
                List<String> sets = new ArrayList<>();
                JSONArray ls = c.optJSONArray("linescores");
                for (int x = 0; ls != null && x < ls.length(); x++) sets.add(String.valueOf((int) ls.getJSONObject(x).optDouble("value", 0)));
                String shortName = a.optString("shortName", full);
                sides[s] = new Side(shortName, shortName, String.join("   ", sets), sets);
                JSONObject rank = c.optJSONObject("curatedRank");
                sides[s].rank = rank == null ? 0 : rank.optInt("current", 0);
                sides[s].fullName = full;
                sides[s].winner = c.optBoolean("winner", false);
            }
            JSONObject type = comp.getJSONObject("status").getJSONObject("type");
            Game g = new Game(comp.optString("id"), type.optString("state", "pre"), type.optString("shortDetail", ""),
                    parseTime(comp.optString("date", "")), sides[0], sides[1]);
            g.tennis = true;
            JSONObject round = comp.optJSONObject("round");
            g.label = tournament + (round == null ? "" : " · " + round.optString("displayName", ""));
            g.tag = round == null ? "" : round.optString("displayName", "");
            g.event = tournament;
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    private static String tournamentName(JSONObject ev) {
        return ev.optString("name", "").replaceFirst("^Rolex ", "");
    }

    /** The player's live match, else their next, else their latest, in this week's tournament(s). */
    private static Game playerMatch(Context ctx, String key) {
        String[] p = key.split("/");
        JSONArray events = tennisEvents(ctx, p[0] + "/" + p[1]);
        if (events == null) return null;
        String who = p[2].toLowerCase(Locale.ROOT);
        Game live = null, next = null, last = null;
        try {
            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                JSONArray groupings = ev.optJSONArray("groupings");
                for (int k = 0; groupings != null && k < groupings.length(); k++) {
                    JSONArray comps = groupings.getJSONObject(k).optJSONArray("competitions");
                    for (int c = 0; comps != null && c < comps.length(); c++) {
                        Game g = tennisGame(comps.getJSONObject(c), tournamentName(ev));
                        if (g == null || !(g.away.fullName.toLowerCase(Locale.ROOT).contains(who)
                                || g.home.fullName.toLowerCase(Locale.ROOT).contains(who))) continue;
                        g.followed = true;
                        if (g.live()) live = g;
                        else if (g.done()) { if (last == null || g.startMs > last.startMs) last = g; }
                        else if (next == null || g.startMs < next.startMs) next = g;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return live != null ? live : next != null ? next : last;
    }

    /**
     * The top singles matches of the tour's main tournament this week (a Grand Slam, else a Masters or the
     * Finals, else the biggest draw): live ones first, then today's, best-ranked player first. On a rest day,
     * the next day's order of play; after the final, the latest results.
     */
    private static List<Game> topMatches(Context ctx, String tour, int max) {
        List<Game> out = new ArrayList<>();
        JSONArray events = tennisEvents(ctx, tour);
        if (events == null) return out;
        try {
            JSONObject main = null;
            int bestScore = -1;
            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                String name = ev.optString("name", "");
                int size = 0;
                JSONArray groupings = ev.optJSONArray("groupings");
                for (int k = 0; groupings != null && k < groupings.length(); k++) {
                    JSONArray comps = groupings.getJSONObject(k).optJSONArray("competitions");
                    size += comps == null ? 0 : comps.length();
                }
                int level = ev.optBoolean("major") ? 3 : name.matches(".*(Masters|Finals|1000).*") ? 2 : 1;
                int score = level * 10_000 + size;
                if (score > bestScore) {
                    bestScore = score;
                    main = ev;
                }
            }
            if (main == null) return out;
            List<Game> live = new ArrayList<>(), today = new ArrayList<>(), later = new ArrayList<>(), done = new ArrayList<>();
            LocalDate day = LocalDate.now();
            JSONArray groupings = main.optJSONArray("groupings");
            for (int k = 0; groupings != null && k < groupings.length(); k++) {
                JSONArray comps = groupings.getJSONObject(k).optJSONArray("competitions");
                for (int c = 0; comps != null && c < comps.length(); c++) {
                    JSONObject comp = comps.getJSONObject(c);
                    JSONObject type = comp.optJSONObject("type");
                    JSONObject round = comp.optJSONObject("round");
                    if (type == null || !type.optString("slug", "").endsWith("singles")) continue;
                    if (round != null && round.optString("displayName", "").startsWith("Qualifying")) continue;
                    Game g = tennisGame(comp, tournamentName(main));
                    if (g == null) continue;
                    LocalDate d = Instant.ofEpochMilli(g.startMs).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (g.live()) live.add(g);
                    else if (d.equals(day)) today.add(g);
                    else if (!g.done() && d.isAfter(day)) later.add(g);
                    else if (g.done()) done.add(g);
                }
            }
            java.util.Comparator<Game> byRank = (a, b) -> Integer.compare(a.bestRank(), b.bestRank());
            live.sort(byRank);
            today.sort(byRank);
            out.addAll(live);
            out.addAll(today);
            if (out.isEmpty() && !later.isEmpty()) {
                // A rest day: the next day's order of play.
                later.sort((a, b) -> Long.compare(a.startMs, b.startMs));
                LocalDate next = Instant.ofEpochMilli(later.get(0).startMs).atZone(ZoneId.systemDefault()).toLocalDate();
                for (Game g : later) {
                    if (Instant.ofEpochMilli(g.startMs).atZone(ZoneId.systemDefault()).toLocalDate().equals(next)) out.add(g);
                }
                out.sort(byRank);
            }
            if (out.isEmpty()) {
                done.sort((a, b) -> Long.compare(b.startMs, a.startMs));
                out.addAll(done);
            }
        } catch (Exception ignored) {
            // Whatever was gathered.
        }
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /**
     * The team to feature: one with a live game; else the first in SCORES_TEAMS order that's in season (so the
     * Bears in the fall, the Braves or Cubs in the summer); else, between seasons, the one whose next game comes
     * soonest; else the first.
     */
    static Team featured(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return null;
        for (Team t : teams) if (t.live != null) return t;
        long now = System.currentTimeMillis();
        for (Team t : teams) if (t.inSeason(now)) return t;
        Team soonest = null;
        for (Team t : teams) {
            if (t.next != null && (soonest == null || t.next.startMs < soonest.next.startMs)) soonest = t;
        }
        return soonest != null ? soonest : teams.get(0);
    }

    private static Team team(Context ctx, String key) {
        String[] p = key.split("/");
        String base = "https://site.api.espn.com/apis/site/v2/sports/" + p[0] + "/" + p[1];
        Team t = new Team(key);
        try {
            String body = NeonNet.text(ctx, base + "/teams/" + p[2], null, 60_000L);
            if (body != null) {
                JSONObject team = new JSONObject(body).getJSONObject("team");
                t.name = team.optString("shortDisplayName", team.optString("name", ""));
                t.standing = team.optString("standingSummary", "");
                JSONObject rec = team.optJSONObject("record");
                JSONArray items = rec == null ? null : rec.optJSONArray("items");
                if (items != null && items.length() > 0) t.record = items.getJSONObject(0).optString("summary", "");
                JSONArray next = team.optJSONArray("nextEvent");
                if (next != null && next.length() > 0) {
                    Game g = game(next.getJSONObject(0));
                    if (g != null && g.live()) t.live = g;
                    else if (g != null && !g.done()) t.next = g;
                }
            }
            String sched = NeonNet.text(ctx, base + "/teams/" + p[2] + "/schedule", null, 10 * 60_000L);
            if (sched != null) {
                JSONArray events = new JSONObject(sched).optJSONArray("events");
                long now = System.currentTimeMillis();
                for (int i = 0; events != null && i < events.length(); i++) {
                    Game g = game(events.getJSONObject(i));
                    if (g == null) continue;
                    if (g.done() && (t.last == null || g.startMs > t.last.startMs)) t.last = g;
                    if (!g.done() && !g.live() && g.startMs > now && (t.next == null || g.startMs < t.next.startMs)
                            && (t.live == null || !g.id.equals(t.live.id))) {
                        t.next = g;
                    }
                }
            }
            // Line scores and leaders for the box score: the live game, or else the last final.
            Game box = t.live != null ? t.live : t.last;
            if (box != null) summary(ctx, base, box, t.live != null ? 30_000L : 6 * 3600_000L);
        } catch (Exception e) {
            // Keep whatever was read.
        }
        return t.live == null && t.last == null && t.next == null ? null : t;
    }

    private static void summary(Context ctx, String base, Game g, long maxAge) {
        String body = NeonNet.text(ctx, base + "/summary?event=" + g.id, null, maxAge);
        if (body == null) return;
        try {
            JSONObject j = new JSONObject(body);
            JSONArray comps = j.getJSONObject("header").getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors");
            for (int i = 0; i < comps.length(); i++) {
                JSONObject c = comps.getJSONObject(i);
                String abbr = c.getJSONObject("team").optString("abbreviation");
                List<String> lines = new ArrayList<>();
                JSONArray ls = c.optJSONArray("linescores");
                for (int k = 0; ls != null && k < ls.length(); k++) lines.add(ls.getJSONObject(k).optString("displayValue", "–"));
                Side s = abbr.equals(g.away.abbr) ? g.away : abbr.equals(g.home.abbr) ? g.home : null;
                if (s != null) {
                    s.lines.clear();
                    s.lines.addAll(lines);
                }
            }
            JSONArray leaders = j.optJSONArray("leaders");
            List<String> parts = new ArrayList<>();
            for (int i = 0; leaders != null && i < leaders.length() && parts.size() < 2; i++) {
                JSONArray cats = leaders.getJSONObject(i).optJSONArray("leaders");
                if (cats == null || cats.length() == 0) continue;
                JSONObject cat = cats.getJSONObject(0);
                JSONArray who = cat.optJSONArray("leaders");
                if (who == null || who.length() == 0) continue;
                JSONObject top = who.getJSONObject(0);
                JSONObject athlete = top.optJSONObject("athlete");
                String name = athlete == null ? "" : athlete.optString("shortName", athlete.optString("displayName", ""));
                if (name.isEmpty()) continue;
                String value = top.optString("displayValue", "");
                // Football values already say what they are ("17/34, 188 YDS, 2 TD"); bare numbers get a label.
                String label = value.matches("[\\d.]+") ? " " + abbreviate(cat.optString("name", "")) : "";
                if (value.length() > 22) value = value.substring(0, value.indexOf(',', 10) > 0 ? value.indexOf(',', 10) : 22);
                parts.add((name + " " + value + label).trim());
            }
            g.leaders = String.join(" · ", parts).trim();
        } catch (Exception ignored) {
            // No box score: the panel shows the totals only.
        }
    }

    private static String abbreviate(String stat) {
        switch (stat) {
            case "points": return "PTS";
            case "rebounds": return "REB";
            case "assists": return "AST";
            case "passingYards": return "PASS YDS";
            case "rushingYards": return "RUSH YDS";
            case "receivingYards": return "REC YDS";
            case "goals": return "G";
            default: return "";
        }
    }

    private static Game game(JSONObject event) {
        try {
            JSONObject comp = event.getJSONArray("competitions").getJSONObject(0);
            JSONObject type = comp.getJSONObject("status").getJSONObject("type");
            Side away = null, home = null;
            JSONArray cs = comp.getJSONArray("competitors");
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.getJSONObject(i);
                JSONObject team = c.getJSONObject("team");
                Object sc = c.opt("score");
                String score = sc instanceof JSONObject ? ((JSONObject) sc).optString("displayValue", "")
                        : sc == null || sc == JSONObject.NULL ? "" : String.valueOf(sc);
                Side s = new Side(team.optString("abbreviation", "?"), team.optString("shortDisplayName", team.optString("name", "")),
                        score, new ArrayList<>());
                s.winner = c.optBoolean("winner", false);
                JSONArray recs = c.optJSONArray("records");
                if (recs == null) recs = c.optJSONArray("record");
                if (recs != null && recs.length() > 0) {
                    JSONObject r = recs.getJSONObject(0);
                    s.record = r.optString("summary", r.optString("displayValue", ""));
                }
                // College polls: 1-25 is ranked, ESPN's 99 means unranked.
                JSONObject cr = c.optJSONObject("curatedRank");
                int rank = cr == null ? 0 : cr.optInt("current", 0);
                s.rank = rank >= 1 && rank <= 25 ? rank : 0;
                if ("home".equals(c.optString("homeAway"))) home = s;
                else away = s;
            }
            if (away == null || home == null) return null;
            Game g = new Game(event.optString("id"), type.optString("state", "pre"), type.optString("shortDetail", ""),
                    parseTime(event.optString("date", comp.optString("date", ""))), away, home);
            if (g.done() && !away.winner && !home.winner) {
                try {
                    int a = Integer.parseInt(away.score.trim()), h = Integer.parseInt(home.score.trim());
                    away.winner = a > h;
                    home.winner = h > a;
                } catch (NumberFormatException ignored) {
                    // No scores: nobody marked.
                }
            }
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseTime(String s) {
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]X")).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * "Tonight · 7:30 PM", "Tomorrow · …", "Yesterday · …", "Thu · 7:30 PM" in the coming week, "Last Thu · …" in
     * the past one, else "Nov 12 · 7:30 PM".
     * ESPN puts a not-yet-announced start (spring training, playoffs) at midnight: that shows the date alone.
     */
    static String when(long ms) {
        ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now(), day = t.toLocalDate();
        boolean timeKnown = t.getHour() != 0 || t.getMinute() != 0;
        String time = timeKnown ? " · " + t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) : "";
        if (day.equals(today)) return "Tonight" + time;
        if (day.equals(today.plusDays(1))) return "Tomorrow" + time;
        if (day.equals(today.minusDays(1))) return "Yesterday" + time;
        // Last Monday's final and next Monday's game mustn't both say "Mon".
        if (day.isAfter(today.minusDays(7)) && day.isBefore(today)) {
            return "Last " + t.format(DateTimeFormatter.ofPattern("EEE", Locale.US)) + time;
        }
        if (day.isAfter(today) && day.isBefore(today.plusDays(7))) {
            return t.format(DateTimeFormatter.ofPattern("EEE", Locale.US)) + time;
        }
        return t.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) + time;
    }

    /** "6-7 7-6 6-1" from the first side's view. */
    static String sets(Game g) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(g.away.lines.size(), g.home.lines.size()); i++) {
            parts.add(g.away.lines.get(i) + "-" + g.home.lines.get(i));
        }
        return String.join(" ", parts);
    }

    /** Chip line for a tennis match: "N. Djokovic v V. Vacherot · 6-7 7-6 6-1". */
    static String chipLine(Game g) {
        if (g.racing) {
            // "F1 São Paulo GP · Lap 34/71 · M. Verstappen leads"
            String gp = g.label.replaceFirst(" · [^·]*$", "");
            return gp + " · " + (g.detail.isEmpty() ? "Live" : g.detail) + (g.away.name.isEmpty() ? "" : " · " + g.away.name + " leads");
        }
        String s = sets(g);
        String names = g.away.name + " v " + g.home.name;
        if (g.live()) return names + " · " + (s.isEmpty() ? "Live" : s);
        if (g.done()) return names + " · Final " + s;
        return names + " · " + when(g.startMs);
    }

    /** One line for the Home chip, or null when there is nothing worth showing. */
    static String chipLine(Team t) {
        if (t == null) return null;
        // Written from your team's side, with full names: "Bears 21 – 14 Buccaneers", "Bears at Packers".
        String mine = t.key.substring(t.key.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT);
        if (t.live != null) return scoreLine(t.live, mine) + " · " + t.live.detail;
        long now = System.currentTimeMillis();
        if (t.last != null && now - t.last.startMs < 20 * 3600_000L) return scoreLine(t.last, mine) + " · Final";
        if (t.next != null) {
            Game g = t.next;
            boolean home = g.home.abbr.equalsIgnoreCase(mine);
            Side us = home ? g.home : g.away, them = home ? g.away : g.home;
            return us.name + (home ? " vs " : " at ") + them.name + " · " + when(g.startMs);
        }
        return null;
    }

    private static String scoreLine(Game g, String mine) {
        boolean home = g.home.abbr.equalsIgnoreCase(mine);
        Side us = home ? g.home : g.away, them = home ? g.away : g.home;
        return us.name + " " + us.score + " – " + them.score + " " + them.name;
    }
}
