package com.footballmanager.touchline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FootballService {

    private final JdbcTemplate jdbc;

    public record LeagueRace(String league, String season, List<Map<String, Object>> rows) {}

    public FootballService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    public List<Map<String, Object>> teams() {
        return teams(null);
    }

    public List<Map<String, Object>> teams(Long leagueId) {
        String sql = """
            SELECT t.team_id AS \"id\", t.name AS \"name\", t.league_id AS \"leagueId\", l.name AS \"league\",
                   s.name AS \"stadium\", t.founded_year AS \"founded\",
                   (SELECT COUNT(*) FROM players p WHERE p.team_id = t.team_id) AS \"players\",
                   (SELECT LISTAGG(c.name, ', ') WITHIN GROUP (ORDER BY c.name)
                    FROM coaches c WHERE c.team_id=t.team_id) AS \"coaches\"
            FROM teams t LEFT JOIN leagues l ON l.league_id=t.league_id
            LEFT JOIN stadiums s ON s.stadium_id=t.stadium_id
            """ + (leagueId == null ? "" : " WHERE t.league_id=?") + " ORDER BY t.name";
        return leagueId == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, leagueId);
    }

    public List<Map<String, Object>> players(Long teamId, Long leagueId, String nationality, String position) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.player_id AS \"id\", p.name AS \"name\", p.position AS \"position\",
                   p.nationality AS \"nationality\", p.jersey_no AS \"number\", t.name AS \"team\",
                   t.team_id AS \"teamId\", t.league_id AS \"leagueId\", l.name AS \"league\",
                   (SELECT COUNT(*) FROM scorers sc WHERE sc.player_id=p.player_id) AS \"goals\"
            FROM players p LEFT JOIN teams t ON t.team_id=p.team_id
            LEFT JOIN leagues l ON l.league_id=t.league_id WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (teamId != null) {
            sql.append(" AND p.team_id=?");
            params.add(teamId);
        }
        if (leagueId != null) {
            sql.append(" AND t.league_id=?");
            params.add(leagueId);
        }
        if (nationality != null && !nationality.isBlank()) {
            sql.append(" AND p.nationality=?");
            params.add(nationality);
        }
        if (position != null && !position.isBlank()) {
            sql.append(" AND p.position=?");
            params.add(position);
        }
        sql.append(" ORDER BY p.name");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<String> playerNationalities() {
        return jdbc.queryForList("SELECT DISTINCT nationality FROM players WHERE nationality IS NOT NULL ORDER BY nationality", String.class);
    }

    public List<String> playerPositions() {
        return jdbc.queryForList("SELECT DISTINCT position FROM players WHERE position IS NOT NULL ORDER BY position", String.class);
    }

    public List<Map<String, Object>> matches() {
        return jdbc.queryForList("""
            SELECT m.match_id AS \"id\", m.match_date AS \"matchDate\", m.status AS \"status\",
                   m.home_goals AS \"homeGoals\", m.away_goals AS \"awayGoals\",
                   h.name AS \"home\", a.name AS \"away\", l.name AS \"league\", se.label AS \"season\",
                   (SELECT LISTAGG(r.name, ', ') WITHIN GROUP (ORDER BY r.name)
                    FROM match_refs mr JOIN referees r ON r.referee_id=mr.referee_id
                    WHERE mr.match_id=m.match_id) AS \"referees\"
            FROM matches m JOIN teams h ON h.team_id=m.home_team_id
            JOIN teams a ON a.team_id=m.away_team_id JOIN seasons se ON se.season_id=m.season_id
            JOIN leagues l ON l.league_id=se.league_id ORDER BY m.match_date DESC
            """);
    }

    public List<Map<String, Object>> fixtures(Long leagueId, boolean completed) {
        StringBuilder sql = new StringBuilder("""
            SELECT m.match_id AS "id", m.match_date AS "matchDate", m.status AS "status",
                   m.home_goals AS "homeGoals", m.away_goals AS "awayGoals",
                   h.name AS "home", a.name AS "away", l.name AS "league", l.league_id AS "leagueId",
                   se.label AS "season",
                   (SELECT LISTAGG(r.name, ', ') WITHIN GROUP (ORDER BY r.name)
                    FROM match_refs mr JOIN referees r ON r.referee_id=mr.referee_id
                    WHERE mr.match_id=m.match_id) AS "referees"
            FROM matches m JOIN teams h ON h.team_id=m.home_team_id
            JOIN teams a ON a.team_id=m.away_team_id JOIN seasons se ON se.season_id=m.season_id
            JOIN leagues l ON l.league_id=se.league_id
            WHERE
            """);
        sql.append(completed ? "m.status='Completed'" : "NVL(m.status,'Scheduled')<>'Completed'");
        List<Object> params = new ArrayList<>();
        if (leagueId != null) {
            sql.append(" AND l.league_id=?");
            params.add(leagueId);
        }
        sql.append(completed ? " ORDER BY m.match_date DESC" : " ORDER BY m.match_date ASC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> standings() {
        return standings(null);
    }

    public List<Map<String, Object>> standings(Long leagueId) {
        String sql = """
            SELECT RANK() OVER (PARTITION BY se.season_id ORDER BY st.points DESC,
                   (st.goals_for-st.goals_against) DESC, st.goals_for DESC) AS "rank",
                   t.name AS "team", st.played AS "played", st.won AS "won",
                   st.drawn AS "drawn", st.lost AS "lost", st.goals_for AS "gf",
                   st.goals_against AS "ga", st.goals_for-st.goals_against AS "gd",
                   st.points AS "points", se.label AS "season", l.name AS "league"
            FROM standings st JOIN teams t ON t.team_id=st.team_id
            JOIN seasons se ON se.season_id=st.season_id JOIN leagues l ON l.league_id=se.league_id
            """ + (leagueId == null ? "" : " WHERE l.league_id=?")
                + " ORDER BY se.season_id DESC, st.points DESC, (st.goals_for-st.goals_against) DESC";
        return leagueId == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, leagueId);
    }

    public List<Map<String, Object>> leagues() {
        return jdbc.queryForList("""
            SELECT l.league_id AS \"id\", l.name AS \"name\", c.name AS \"country\",
                   (SELECT COUNT(*) FROM teams t WHERE t.league_id=l.league_id) AS \"teams\",
                   (SELECT COUNT(*) FROM seasons s WHERE s.league_id=l.league_id) AS \"seasons\"
            FROM leagues l LEFT JOIN countries c ON c.country_id=l.country_id ORDER BY l.name
            """);
    }

    public List<Map<String, Object>> teamOptions() {
        return jdbc.queryForList("SELECT t.team_id AS \"id\", t.name AS \"name\", t.league_id AS \"leagueId\", l.name AS \"league\" FROM teams t LEFT JOIN leagues l ON l.league_id=t.league_id ORDER BY l.name,t.name");
    }

    public List<Map<String, Object>> refereeOptions() {
        return jdbc.queryForList("SELECT referee_id AS \"id\", name AS \"name\", nationality AS \"nationality\" FROM referees ORDER BY name");
    }

    public List<String> refereeNationalities() {
        return jdbc.queryForList("SELECT DISTINCT nationality FROM referees WHERE nationality IS NOT NULL ORDER BY nationality", String.class);
    }

    public List<Map<String, Object>> referees(Long leagueId, String nationality, String sort) {
        StringBuilder sql = new StringBuilder("""
            SELECT r.referee_id AS "id", r.name AS "name", r.nationality AS "nationality",
                   COUNT(DISTINCT mr.match_id) AS "matches",
                   LISTAGG(DISTINCT l.name, ', ') WITHIN GROUP (ORDER BY l.name) AS "leagues"
            FROM referees r
            LEFT JOIN match_refs mr ON mr.referee_id=r.referee_id
            LEFT JOIN matches m ON m.match_id=mr.match_id
            LEFT JOIN seasons se ON se.season_id=m.season_id
            LEFT JOIN leagues l ON l.league_id=se.league_id
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (leagueId != null) {
            sql.append(" AND se.league_id=?");
            params.add(leagueId);
        }
        if (nationality != null && !nationality.isBlank()) {
            sql.append(" AND r.nationality=?");
            params.add(nationality);
        }
        sql.append(" GROUP BY r.referee_id, r.name, r.nationality");
        String orderBy = switch (sort == null ? "name" : sort) {
            case "matches" -> "\"matches\" DESC, r.name";
            case "nationality" -> "r.nationality, r.name";
            case "league" -> "\"leagues\", r.name";
            default -> "r.name";
        };
        sql.append(" ORDER BY ").append(orderBy);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public void addReferee(String name, String nationality) {
        jdbc.update("INSERT INTO referees(name, nationality) VALUES (?, ?)", name, nationality);
    }

    public List<String> coachNationalities() {
        return jdbc.queryForList("SELECT DISTINCT nationality FROM coaches WHERE nationality IS NOT NULL ORDER BY nationality", String.class);
    }

    public List<Map<String, Object>> coaches(Long leagueId, String nationality, String sort) {
        StringBuilder sql = new StringBuilder("""
            SELECT c.coach_id AS "id", c.name AS "name", c.nationality AS "nationality",
                   t.name AS "team", l.name AS "league", t.team_id AS "teamId"
            FROM coaches c LEFT JOIN teams t ON t.team_id=c.team_id
            LEFT JOIN leagues l ON l.league_id=t.league_id WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (leagueId != null) { sql.append(" AND t.league_id=?"); params.add(leagueId); }
        if (nationality != null && !nationality.isBlank()) { sql.append(" AND c.nationality=?"); params.add(nationality); }
        String orderBy = switch (sort == null ? "name" : sort) {
            case "nationality" -> "c.nationality, c.name";
            case "league" -> "l.name, t.name, c.name";
            case "club" -> "t.name, c.name";
            default -> "c.name";
        };
        sql.append(" ORDER BY ").append(orderBy);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public void addCoach(String name, String nationality, Long teamId) {
        jdbc.update("INSERT INTO coaches(name, nationality, team_id) VALUES (?, ?, ?)", name, nationality, teamId);
    }

    public List<Map<String, Object>> playerOptions() {
        return jdbc.queryForList("SELECT player_id AS \"id\", name AS \"name\", team_id AS \"teamId\" FROM players ORDER BY name");
    }

    public List<Map<String, Object>> matchOptions(boolean completed) {
        String statusFilter = completed ? "m.status='Completed'" : "NVL(m.status,'Scheduled')<>'Completed'";
        String order = completed ? "DESC" : "ASC";
        return jdbc.queryForList("SELECT m.match_id AS \"id\", TO_CHAR(m.match_date, 'DD Mon YYYY') || ' · ' || h.name || ' vs ' || a.name AS \"name\", h.team_id AS \"homeTeamId\", a.team_id AS \"awayTeamId\" FROM matches m JOIN teams h ON h.team_id=m.home_team_id JOIN teams a ON a.team_id=m.away_team_id WHERE " + statusFilter + " ORDER BY m.match_date " + order + ", m.match_id " + order);
    }

    public Map<String, Object> matchReportContext(long matchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT m.match_id AS \"id\", m.status AS \"status\", TO_CHAR(m.match_date, 'DD Mon YYYY HH24:MI') AS \"date\", h.team_id AS \"homeTeamId\", h.name AS \"home\", a.team_id AS \"awayTeamId\", a.name AS \"away\" FROM matches m JOIN teams h ON h.team_id=m.home_team_id JOIN teams a ON a.team_id=m.away_team_id WHERE m.match_id=?", matchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> playersForMatch(long matchId) {
        return jdbc.queryForList("SELECT p.player_id AS \"id\", p.name AS \"name\", p.team_id AS \"teamId\", t.name AS \"team\" FROM players p JOIN teams t ON t.team_id=p.team_id WHERE p.team_id IN (SELECT home_team_id FROM matches WHERE match_id=? UNION SELECT away_team_id FROM matches WHERE match_id=?) ORDER BY t.name,p.name", matchId, matchId);
    }

    public List<Map<String, Object>> teamsForMatch(long matchId) {
        return jdbc.queryForList("SELECT t.team_id AS \"id\", t.name AS \"name\" FROM teams t WHERE t.team_id IN (SELECT home_team_id FROM matches WHERE match_id=? UNION SELECT away_team_id FROM matches WHERE match_id=?) ORDER BY t.name", matchId, matchId);
    }

    public List<Map<String, Object>> seasonOptions() {
        return jdbc.queryForList("SELECT se.season_id AS \"id\", se.league_id AS \"leagueId\", l.name || ' · ' || se.label AS \"name\" FROM seasons se JOIN leagues l ON l.league_id=se.league_id ORDER BY se.start_date DESC");
    }

    public List<Map<String, Object>> leagueOptions() {
        return jdbc.queryForList("SELECT league_id AS \"id\", name AS \"name\" FROM leagues ORDER BY name");
    }

    public List<Map<String, Object>> stadiumOptions() {
        return jdbc.queryForList("SELECT stadium_id AS \"id\", name AS \"name\" FROM stadiums ORDER BY name");
    }

    public List<Map<String, Object>> seasons() {
        return jdbc.queryForList("SELECT se.season_id AS \"id\", se.label AS \"label\", l.name AS \"league\", TO_CHAR(se.start_date, 'DD Mon YYYY') AS \"startDate\", TO_CHAR(se.end_date, 'DD Mon YYYY') AS \"endDate\" FROM seasons se JOIN leagues l ON l.league_id=se.league_id ORDER BY se.start_date DESC NULLS LAST");
    }

    public List<LeagueRace> dashboardLeagueRaces() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT league, season, "rank", team, played, gd, points FROM (
              SELECT l.name AS league, se.label AS season, t.name AS team,
                     st.played AS played, st.goals_for-st.goals_against AS gd,
                     st.points AS points,
                     ROW_NUMBER() OVER (PARTITION BY se.season_id ORDER BY st.points DESC,
                       (st.goals_for-st.goals_against) DESC, st.goals_for DESC, t.name) AS "rank",
                     DENSE_RANK() OVER (PARTITION BY l.league_id ORDER BY se.start_date DESC NULLS LAST, se.season_id DESC) AS season_rank
              FROM standings st JOIN teams t ON t.team_id=st.team_id
              JOIN seasons se ON se.season_id=st.season_id
              JOIN leagues l ON l.league_id=se.league_id
            ) latest WHERE season_rank=1 AND "rank"<=5
            ORDER BY league, "rank"
            """);
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        Map<String, String[]> labels = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String league = String.valueOf(row.get("league"));
            String season = String.valueOf(row.get("season"));
            String key = league + " · " + season;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            labels.putIfAbsent(key, new String[]{league, season});
        }
        List<LeagueRace> races = new ArrayList<>();
        grouped.forEach((key, value) -> races.add(new LeagueRace(labels.get(key)[0], labels.get(key)[1], value)));
        return races;
    }

    @Transactional
    public void addTeam(String name, Long league, Long stadium, Integer founded) {
        jdbc.update("INSERT INTO teams(name, league_id, stadium_id, founded_year) VALUES (?, ?, ?, ?)", name, league, stadium, founded);
        jdbc.update("INSERT INTO standings(season_id, team_id) SELECT season_id, (SELECT team_id FROM teams WHERE name=?) FROM seasons WHERE league_id=?", name, league);
    }

    public void deleteTeam(long id) {
        jdbc.update("DELETE FROM teams WHERE team_id=?", id);
    }

    public void addPlayer(String name, Long team, String position, String nationality, Integer number) {
        jdbc.update("INSERT INTO players(name, team_id, position, nationality, jersey_no) VALUES (?, ?, ?, ?, ?)", name, team, position, nationality, number);
    }

    public void deletePlayer(long id) {
        jdbc.update("DELETE FROM players WHERE player_id=?", id);
    }

    @Transactional
    public void addMatch(long season, long home, long away, long stadium, String date, Long refereeId) {
        KeyHolder keys = new GeneratedKeyHolder();
        PreparedStatementCreator insert = connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO matches(season_id, home_team_id, away_team_id, stadium_id, match_date) VALUES (?, ?, ?, ?, ?)",
                    new String[] {"MATCH_ID"});
            statement.setLong(1, season);
            statement.setLong(2, home);
            statement.setLong(3, away);
            statement.setLong(4, stadium);
            String timestamp = date.replace('T', ' ');
            if (timestamp.length() == 16) timestamp += ":00";
            statement.setTimestamp(5, Timestamp.valueOf(timestamp));
            return statement;
        };
        jdbc.update(insert, keys);
        if (refereeId != null) {
            jdbc.update("INSERT INTO match_refs(match_id, referee_id) VALUES (?, ?)", keys.getKey().longValue(), refereeId);
        }
    }

    public void addLeague(String name) {
        jdbc.update("INSERT INTO leagues(name) VALUES (?)", name);
    }

    @Transactional
    public void addSeason(long leagueId, String label, String startDate, String endDate) {
        jdbc.update("INSERT INTO seasons(league_id, label, start_date, end_date) VALUES (?, ?, TO_DATE(?, 'YYYY-MM-DD'), TO_DATE(?, 'YYYY-MM-DD'))",
                leagueId, label, startDate.isBlank() ? null : startDate, endDate.isBlank() ? null : endDate);
        jdbc.update("INSERT INTO standings(season_id, team_id) SELECT (SELECT season_id FROM seasons WHERE league_id=? AND label=?), team_id FROM teams WHERE league_id=?",
                leagueId, label, leagueId);
    }

    public void addStadium(String name, String location, Integer capacity) {
        jdbc.update("INSERT INTO stadiums(name, location, capacity) VALUES (?, ?, ?)", name, location, capacity);
    }

    @Transactional
    public void saveResult(long matchId, int homeGoals, int awayGoals) {
        jdbc.update("UPDATE matches SET home_goals=?, away_goals=?, status='Completed' WHERE match_id=?", homeGoals, awayGoals, matchId);
        Long seasonId = jdbc.queryForObject("SELECT season_id FROM matches WHERE match_id=?", Long.class, matchId);
        recalculateStandings(seasonId);
    }

    private void recalculateStandings(Long seasonId) {
        jdbc.update("UPDATE standings SET played=0, won=0, drawn=0, lost=0, goals_for=0, goals_against=0, points=0 WHERE season_id=?", seasonId);
        jdbc.update("""
            MERGE INTO standings st USING (
              SELECT team_id, SUM(played) played, SUM(won) won, SUM(drawn) drawn, SUM(lost) lost,
                     SUM(gf) gf, SUM(ga) ga, SUM(points) points
              FROM (
                SELECT home_team_id team_id, 1 played,
                       CASE WHEN home_goals>away_goals THEN 1 ELSE 0 END won,
                       CASE WHEN home_goals=away_goals THEN 1 ELSE 0 END drawn,
                       CASE WHEN home_goals<away_goals THEN 1 ELSE 0 END lost,
                       home_goals gf, away_goals ga,
                       CASE WHEN home_goals>away_goals THEN 3 WHEN home_goals=away_goals THEN 1 ELSE 0 END points
                FROM matches WHERE season_id=? AND status='Completed'
                UNION ALL
                SELECT away_team_id, 1,
                       CASE WHEN away_goals>home_goals THEN 1 ELSE 0 END,
                       CASE WHEN home_goals=away_goals THEN 1 ELSE 0 END,
                       CASE WHEN away_goals<home_goals THEN 1 ELSE 0 END,
                       away_goals, home_goals,
                       CASE WHEN away_goals>home_goals THEN 3 WHEN home_goals=away_goals THEN 1 ELSE 0 END
                FROM matches WHERE season_id=? AND status='Completed'
              ) GROUP BY team_id
            ) totals ON (st.season_id=? AND st.team_id=totals.team_id)
            WHEN MATCHED THEN UPDATE SET st.played=totals.played, st.won=totals.won,
              st.drawn=totals.drawn, st.lost=totals.lost, st.goals_for=totals.gf,
              st.goals_against=totals.ga, st.points=totals.points
            """, seasonId, seasonId, seasonId);
    }

    public List<Map<String, Object>> recentScorers() {
        return jdbc.queryForList("""
            SELECT p.name AS \"player\", t.name AS \"team\", sc.minute AS \"minute\",
                   m.match_date AS \"matchDate\", h.name AS \"home\", a.name AS \"away\"
            FROM scorers sc JOIN players p ON p.player_id=sc.player_id JOIN teams t ON t.team_id=sc.team_id
            JOIN matches m ON m.match_id=sc.match_id JOIN teams h ON h.team_id=m.home_team_id
            JOIN teams a ON a.team_id=m.away_team_id ORDER BY m.match_date DESC, sc.minute DESC FETCH FIRST 6 ROWS ONLY
            """);
    }

    public List<Map<String, Object>> recentCards() {
        return jdbc.queryForList("SELECT p.name AS \"player\", t.name AS \"team\", c.card_type AS \"type\", c.minute AS \"minute\", m.match_date AS \"matchDate\" FROM cards c JOIN players p ON p.player_id=c.player_id JOIN teams t ON t.team_id=p.team_id JOIN matches m ON m.match_id=c.match_id ORDER BY m.match_date DESC, c.minute DESC FETCH FIRST 30 ROWS ONLY");
    }

    public List<Map<String, Object>> transfers() {
        String transferTable = transferTableName();
        String dateColumn = transferDateColumn(transferTable);
        return jdbc.queryForList("SELECT tr.transfer_id AS \"id\", p.name AS \"player\", f.name AS \"fromTeam\", t.name AS \"toTeam\", tr.fee AS \"fee\", tr." + dateColumn + " AS \"transferDate\", se.label AS \"season\" FROM " + transferTable + " tr JOIN players p ON p.player_id=tr.player_id LEFT JOIN teams f ON f.team_id=tr.from_team_id JOIN teams t ON t.team_id=tr.to_team_id LEFT JOIN seasons se ON se.season_id=tr.season_id ORDER BY tr." + dateColumn + " DESC");
    }

    private String transferTableName() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM user_tables WHERE table_name IN ('TRANSFERS','TRANSFER')",
                String.class);
        if (tables.contains("TRANSFERS")) {
            return "transfers";
        }
        if (tables.contains("TRANSFER")) {
            return "transfer";
        }
        throw new IllegalStateException("Oracle table TRANSFER or TRANSFERS was not found for the connected user.");
    }

    private String transferDateColumn(String transferTable) {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM user_tab_columns WHERE table_name=UPPER(?)",
                String.class, transferTable);
        if (columns.contains("TRANSFER_DATE")) {
            return "transfer_date";
        }
        if (columns.contains("DATE")) {
            return "\"DATE\"";
        }
        if (columns.contains("transfer_date")) {
            return "\"transfer_date\"";
        }
        if (columns.contains("date")) {
            return "\"date\"";
        }
        throw new IllegalStateException("The Oracle transfer table needs a DATE or TRANSFER_DATE column.");
    }

    @Transactional
    public void addGoal(long matchId, long playerId, long teamId, int minute) {
        int valid = jdbc.queryForObject("SELECT COUNT(*) FROM matches m JOIN players p ON p.player_id=? WHERE m.match_id=? AND p.team_id=? AND (m.home_team_id=? OR m.away_team_id=?)", Integer.class, playerId, matchId, teamId, teamId, teamId);
        if (valid == 0) {
            throw new IllegalArgumentException("Choose a scorer and club from the selected match.");
        }
        jdbc.update("INSERT INTO scorers(match_id, player_id, team_id, minute) VALUES (?, ?, ?, ?)", matchId, playerId, teamId, minute);
        Map<String, Object> match = jdbc.queryForMap("SELECT season_id AS \"seasonId\", status AS \"status\" FROM matches WHERE match_id=?", matchId);
        jdbc.update("UPDATE matches SET home_goals=home_goals + CASE WHEN home_team_id=? THEN 1 ELSE 0 END, away_goals=away_goals + CASE WHEN away_team_id=? THEN 1 ELSE 0 END, status=CASE WHEN status='Scheduled' THEN 'Live' ELSE status END WHERE match_id=? AND (home_team_id=? OR away_team_id=?)", teamId, teamId, matchId, teamId, teamId);
        if ("Completed".equals(match.get("status"))) {
            recalculateStandings(((Number) match.get("seasonId")).longValue());
        }
    }

    public void addCard(long matchId, long playerId, String type, int minute) {
        assertPlayerInMatch(matchId, playerId);
        jdbc.update("INSERT INTO cards(match_id, player_id, card_type, minute) VALUES (?, ?, ?, ?)", matchId, playerId, type, minute);
    }

    public void addSubstitution(long matchId, long outId, long inId, int minute) {
        int valid = jdbc.queryForObject("SELECT COUNT(*) FROM matches m JOIN players p1 ON p1.player_id=? JOIN players p2 ON p2.player_id=? WHERE m.match_id=? AND p1.team_id=p2.team_id AND p1.team_id IN (m.home_team_id,m.away_team_id)", Integer.class, outId, inId, matchId);
        if (valid == 0) {
            throw new IllegalArgumentException("Both players in a substitution must belong to the same club in the selected match.");
        }
        jdbc.update("INSERT INTO substitutions(match_id, player_out_id, player_in_id, minute) VALUES (?, ?, ?, ?)", matchId, outId, inId, minute);
    }

    private void assertPlayerInMatch(long matchId, long playerId) {
        int valid = jdbc.queryForObject("SELECT COUNT(*) FROM matches m JOIN players p ON p.player_id=? WHERE m.match_id=? AND p.team_id IN (m.home_team_id,m.away_team_id)", Integer.class, playerId, matchId);
        if (valid == 0) {
            throw new IllegalArgumentException("Choose a player from one of the selected match clubs.");
        }
    }

    @Transactional
    public void addTransfer(long seasonId, long playerId, long toTeamId, String date, java.math.BigDecimal fee) {
        Map<String, Object> player = jdbc.queryForMap("SELECT team_id AS \"teamId\" FROM players WHERE player_id=?", playerId);
        Long currentTeamId = player.get("teamId") == null ? null : ((Number) player.get("teamId")).longValue();
        if (Objects.equals(currentTeamId, toTeamId)) {
            throw new IllegalArgumentException("Choose a destination club different from the player's current club.");
        }
        Long seasonLeagueId = jdbc.queryForObject("SELECT league_id FROM seasons WHERE season_id=?", Long.class, seasonId);
        Long destinationLeagueId = jdbc.queryForObject("SELECT league_id FROM teams WHERE team_id=?", Long.class, toTeamId);
        if (!Objects.equals(seasonLeagueId, destinationLeagueId)) {
            throw new IllegalArgumentException("Choose a destination club in the league selected for this season.");
        }
        String transferTable = transferTableName();
        String dateColumn = transferDateColumn(transferTable);
        jdbc.update("INSERT INTO " + transferTable + "(season_id, player_id, from_team_id, to_team_id, " + dateColumn + ", fee) VALUES (?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), ?)", seasonId, playerId, currentTeamId, toTeamId, date, fee);
        jdbc.update("UPDATE players SET team_id=? WHERE player_id=?", toTeamId, playerId);
    }
}
