#!/usr/bin/env python3
"""Generate an idempotent Oracle seed file for the 2026/27 top five leagues.

Uses ESPN's public scoreboard/team endpoints as a data snapshot. It has no
runtime dependency and writes SQL only; run the resulting file in SQL Developer
while connected as the Oracle schema owner.
"""

from __future__ import annotations

import concurrent.futures
import datetime as dt
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "database/seed-oracle-2026-27.sql"
SOURCE_DIR = ROOT / "target/seed-source-2026-27"
BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
LEAGUES = {
    "eng.1": ("Premier League", "England", dt.date(2026, 8, 1), dt.date(2027, 6, 1)),
    "esp.1": ("LaLiga", "Spain", dt.date(2026, 8, 1), dt.date(2027, 6, 1)),
    "ita.1": ("Serie A", "Italy", dt.date(2026, 8, 1), dt.date(2027, 6, 1)),
    "fra.1": ("Ligue 1", "France", dt.date(2026, 8, 1), dt.date(2027, 6, 1)),
    "ger.1": ("Bundesliga", "Germany", dt.date(2026, 8, 1), dt.date(2027, 6, 1)),
}
SEASON = "2026/27"


def get_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": "Touchline-seed/1.0"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                return json.load(response)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            if attempt == 4:
                raise
            time.sleep(0.5 * (attempt + 1))


def fetch_json(url: str, cache_path: Path):
    if cache_path.exists():
        return json.loads(cache_path.read_text(encoding="utf-8"))
    value = get_json(url)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    cache_path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
    return value


def sql_text(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def sql_num(value):
    return "NULL" if value in (None, "") else str(value)


def parse_date(value: str | None):
    if not value:
        return None
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00")).date()


def main():
    SOURCE_DIR.mkdir(parents=True, exist_ok=True)
    team_rows = []
    roster_rows = []
    for code, (league_name, country, _start, _end) in LEAGUES.items():
        teams_json = fetch_json(f"{BASE}/{code}/teams?limit=500", SOURCE_DIR / f"{code}-teams.json")
        teams = teams_json["sports"][0]["leagues"][0]["teams"]
        for row in teams:
            team = row["team"]
            team_rows.append({"code": code, "league": league_name, "country": country,
                              "id": team["id"], "name": team["displayName"]})
            roster = fetch_json(f"{BASE}/{code}/teams/{team['id']}/roster?season=2026",
                                SOURCE_DIR / f"{code}-roster-{team['id']}.json")
            for player in roster.get("athletes", []):
                if player.get("status", {}).get("type") not in (None, "active"):
                    continue
                position = player.get("position") or {}
                roster_rows.append({
                    "team": team["displayName"], "name": player.get("fullName"),
                    "birth": parse_date(player.get("dateOfBirth")),
                    "nationality": player.get("citizenship") or player.get("birthPlace", {}).get("country"),
                    "position": position.get("displayName"),
                # ESPN reports height in inches; Oracle schema stores cm.
                "height": round(float(player["height"]) * 2.54, 2) if player.get("height") else None,
                    "number": player.get("jersey"),
                })

    # ESPN accepts month values in `dates`; this includes all dates and
    # weekday reschedules without making one request per calendar day.
    months = []
    for code in LEAGUES:
        current = dt.date(2026, 8, 1)
        while current <= dt.date(2027, 6, 1):
            months.append((code, current.strftime("%Y%m")))
            current = (current.replace(day=28) + dt.timedelta(days=4)).replace(day=1)

    def fetch_month(item):
        code, month_string = item
        path = SOURCE_DIR / f"scoreboard-{code}-{month_string}.json"
        url = f"{BASE}/{code}/scoreboard?season=2026&dates={month_string}&limit=100"
        return code, month_string, fetch_json(url, path)

    fixture_map = {}
    venue_map = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        futures = [pool.submit(fetch_month, item) for item in months]
        for index, future in enumerate(concurrent.futures.as_completed(futures), start=1):
            code, month_string, payload = future.result()
            for event in payload.get("events", []):
                competitions = event.get("competitions") or []
                if not competitions:
                    continue
                competition = competitions[0]
                competitors = competition.get("competitors") or []
                home = next((c for c in competitors if c.get("homeAway") == "home"), None)
                away = next((c for c in competitors if c.get("homeAway") == "away"), None)
                if not home or not away:
                    continue
                h_id, a_id = str(home["team"]["id"]), str(away["team"]["id"])
                event_key = str(event.get("id") or competition.get("id"))
                venue = competition.get("venue") or {}
                if venue.get("fullName"):
                    venue_map[h_id] = {
                        "name": venue["fullName"],
                        "location": (venue.get("address") or {}).get("city"),
                        "capacity": venue.get("capacity"),
                    }
                status_type = (event.get("status") or {}).get("type") or (competition.get("status") or {}).get("type") or {}
                status_state = status_type.get("state")
                description = (status_type.get("description") or "").lower()
                if "postpon" in description:
                    status = "Postponed"
                elif "cancel" in description or "abandon" in description:
                    status = "Cancelled"
                elif status_state == "in":
                    status = "Live"
                elif status_state == "post":
                    status = "Completed"
                else:
                    status = "Scheduled"
                try:
                    kick = dt.datetime.fromisoformat((competition.get("date") or event["date"]).replace("Z", "+00:00"))
                except (ValueError, KeyError):
                    continue
                fixture_map[event_key] = {
                    "code": code, "home_id": h_id, "away_id": a_id,
                    "date": kick.astimezone(dt.timezone.utc).replace(tzinfo=None),
                    "status": status,
                    "home_goals": home.get("score") if status in ("Completed", "Live") else 0,
                    "away_goals": away.get("score") if status in ("Completed", "Live") else 0,
                }
            if index % 100 == 0:
                print(f"Fetched {index}/{len(months)} scoreboard months", flush=True)

    team_by_id = {t["id"]: t for t in team_rows}
    team_by_name = {t["name"]: t for t in team_rows}
    season_bounds = {}
    for match in fixture_map.values():
        code = match["code"]
        existing = season_bounds.get(code)
        if existing is None:
            season_bounds[code] = [match["date"].date(), match["date"].date()]
        else:
            existing[0] = min(existing[0], match["date"].date())
            existing[1] = max(existing[1], match["date"].date())
    # Produce only venues associated with this season's current home fixtures.
    venue_rows = []
    for team in team_rows:
        venue = venue_map.get(team["id"], {})
        venue_rows.append({"team": team["name"], "name": venue.get("name") or (team["name"] + " Stadium"),
                           "location": venue.get("location") or team["country"],
                           "capacity": venue.get("capacity")})
    venue_by_team = {v["team"]: v["name"] for v in venue_rows}

    lines = [
        "-- Touchline 2026/27 snapshot seed. Generated from ESPN's public soccer API.",
        "-- Idempotent for leagues, seasons, teams, venues, players and fixtures.",
        "-- Run after schema-oracle.sql while connected as the oracle application user.",
        "-- Fixture timestamps are stored as UTC in Oracle TIMESTAMP (no timezone).",
        "SET FEEDBACK OFF",
        "SET DEFINE OFF",
        "",
    ]

    for country in sorted({value[1] for value in LEAGUES.values()}):
        lines.append(f"MERGE INTO countries c USING (SELECT {sql_text(country)} name FROM dual) x ON (UPPER(c.name)=UPPER(x.name)) WHEN NOT MATCHED THEN INSERT (name) VALUES (x.name);")
    lines.append("")
    for _code, (league, country, _start, _end) in LEAGUES.items():
        lines.append(f"MERGE INTO leagues l USING (SELECT {sql_text(league)} name, country_id FROM countries WHERE UPPER(name)=UPPER({sql_text(country)})) x ON (UPPER(l.name)=UPPER(x.name)) WHEN MATCHED THEN UPDATE SET l.country_id=x.country_id WHEN NOT MATCHED THEN INSERT (name,country_id) VALUES (x.name,x.country_id);")
    lines.append("")
    for code, (league, country, configured_start, configured_end) in LEAGUES.items():
        start, end = season_bounds.get(code, [configured_start, configured_end])
        lines.append(f"MERGE INTO seasons se USING (SELECT league_id FROM leagues WHERE UPPER(name)=UPPER({sql_text(league)}) AND country_id=(SELECT country_id FROM countries WHERE UPPER(name)=UPPER({sql_text(country)}))) x ON (se.league_id=x.league_id AND se.label={sql_text(SEASON)}) WHEN MATCHED THEN UPDATE SET se.start_date=TO_DATE('{start:%Y-%m-%d}','YYYY-MM-DD'), se.end_date=TO_DATE('{end:%Y-%m-%d}','YYYY-MM-DD') WHEN NOT MATCHED THEN INSERT (league_id,label,start_date,end_date) VALUES (x.league_id,{sql_text(SEASON)},TO_DATE('{start:%Y-%m-%d}','YYYY-MM-DD'),TO_DATE('{end:%Y-%m-%d}','YYYY-MM-DD'));")
    lines.append("")
    for venue in venue_rows:
        lines.append(f"MERGE INTO stadiums st USING (SELECT {sql_text(venue['name'])} name, {sql_text(venue['location'])} location, {sql_num(venue['capacity'])} capacity FROM dual) x ON (UPPER(st.name)=UPPER(x.name)) WHEN MATCHED THEN UPDATE SET st.location=x.location, st.capacity=COALESCE(x.capacity,st.capacity) WHEN NOT MATCHED THEN INSERT (name,location,capacity) VALUES (x.name,x.location,x.capacity);")
    lines.append("")
    for team in team_rows:
        league, country = team["league"], team["country"]
        stadium_name = venue_by_team[team["name"]]
        lines.append(f"MERGE INTO teams t USING (SELECT {sql_text(team['name'])} name, (SELECT l.league_id FROM leagues l JOIN countries c ON c.country_id=l.country_id WHERE UPPER(l.name)=UPPER({sql_text(league)}) AND UPPER(c.name)=UPPER({sql_text(country)})) league_id, (SELECT stadium_id FROM stadiums WHERE UPPER(name)=UPPER({sql_text(stadium_name)})) stadium_id FROM dual) x ON (UPPER(t.name)=UPPER(x.name)) WHEN MATCHED THEN UPDATE SET t.league_id=x.league_id, t.stadium_id=x.stadium_id WHEN NOT MATCHED THEN INSERT (name,league_id,stadium_id) VALUES (x.name,x.league_id,x.stadium_id);")
    lines.append("")
    for player in roster_rows:
        team = team_by_name.get(player["team"])
        if not team or not player["name"]:
            continue
        team_name = team["name"]
        birth = f"TO_DATE('{player['birth']:%Y-%m-%d}','YYYY-MM-DD')" if player["birth"] else "NULL"
        height = player["height"]
        number = str(player["number"]) if str(player["number"] or "").isdigit() else "NULL"
        lines.append(f"MERGE INTO players p USING (SELECT team_id, {sql_text(player['name'])} name FROM teams WHERE UPPER(name)=UPPER({sql_text(team_name)})) x ON (p.team_id=x.team_id AND UPPER(p.name)=UPPER(x.name)) WHEN MATCHED THEN UPDATE SET p.date_of_birth={birth}, p.nationality={sql_text(player['nationality'])}, p.position={sql_text(player['position'])}, p.height_cm={sql_num(height)}, p.jersey_no={number} WHEN NOT MATCHED THEN INSERT (team_id,name,date_of_birth,nationality,position,height_cm,jersey_no) VALUES (x.team_id,x.name,{birth},{sql_text(player['nationality'])},{sql_text(player['position'])},{sql_num(height)},{number});")
    lines.append("")
    for event_id, match in sorted(fixture_map.items(), key=lambda pair: pair[1]["date"]):
        home = team_by_id.get(match["home_id"])
        away = team_by_id.get(match["away_id"])
        if not home or not away:
            continue
        league, country = home["league"], home["country"]
        date_value = match["date"].strftime("%Y-%m-%d %H:%M:%S")
        home_name, away_name = home["name"], away["name"]
        stadium_name = venue_by_team[home_name]
        hg, ag = sql_num(match["home_goals"]), sql_num(match["away_goals"])
        lines.append(f"MERGE INTO matches m USING (SELECT (SELECT season_id FROM seasons se JOIN leagues l ON l.league_id=se.league_id JOIN countries c ON c.country_id=l.country_id WHERE UPPER(l.name)=UPPER({sql_text(league)}) AND UPPER(c.name)=UPPER({sql_text(country)}) AND se.label={sql_text(SEASON)}) season_id, (SELECT team_id FROM teams WHERE UPPER(name)=UPPER({sql_text(home_name)})) home_id, (SELECT team_id FROM teams WHERE UPPER(name)=UPPER({sql_text(away_name)})) away_id, (SELECT stadium_id FROM stadiums WHERE UPPER(name)=UPPER({sql_text(stadium_name)})) stadium_id, TO_TIMESTAMP('{date_value}','YYYY-MM-DD HH24:MI:SS') match_date FROM dual) x ON (m.season_id=x.season_id AND m.match_date=x.match_date AND m.home_team_id=x.home_id AND m.away_team_id=x.away_id) WHEN MATCHED THEN UPDATE SET m.stadium_id=x.stadium_id, m.status={sql_text(match['status'])}, m.home_goals={hg}, m.away_goals={ag} WHEN NOT MATCHED THEN INSERT (season_id,stadium_id,home_team_id,away_team_id,match_date,status,home_goals,away_goals) VALUES (x.season_id,x.stadium_id,x.home_id,x.away_id,x.match_date,{sql_text(match['status'])},{hg},{ag});")

    lines.extend([
        "",
        "-- Ensure each club has a standings row, then derive the table from completed matches.",
        "MERGE INTO standings st USING (SELECT se.season_id,t.team_id FROM seasons se JOIN leagues l ON l.league_id=se.league_id JOIN teams t ON t.league_id=l.league_id WHERE se.label='2026/27' AND l.name IN ('Premier League','LaLiga','Serie A','Ligue 1','Bundesliga')) x ON (st.season_id=x.season_id AND st.team_id=x.team_id) WHEN NOT MATCHED THEN INSERT (season_id,team_id) VALUES (x.season_id,x.team_id);",
        "MERGE INTO standings st USING (SELECT season_id,team_id,SUM(played) played,SUM(won) won,SUM(drawn) drawn,SUM(lost) lost,SUM(gf) gf,SUM(ga) ga,SUM(points) points FROM (SELECT season_id,home_team_id team_id,1 played,CASE WHEN home_goals>away_goals THEN 1 ELSE 0 END won,CASE WHEN home_goals=away_goals THEN 1 ELSE 0 END drawn,CASE WHEN home_goals<away_goals THEN 1 ELSE 0 END lost,home_goals gf,away_goals ga,CASE WHEN home_goals>away_goals THEN 3 WHEN home_goals=away_goals THEN 1 ELSE 0 END points FROM matches WHERE status='Completed' UNION ALL SELECT season_id,away_team_id,1,CASE WHEN away_goals>home_goals THEN 1 ELSE 0 END,CASE WHEN home_goals=away_goals THEN 1 ELSE 0 END,CASE WHEN away_goals<home_goals THEN 1 ELSE 0 END,away_goals,home_goals,CASE WHEN away_goals>home_goals THEN 3 WHEN home_goals=away_goals THEN 1 ELSE 0 END FROM matches WHERE status='Completed') GROUP BY season_id,team_id) x ON (st.season_id=x.season_id AND st.team_id=x.team_id) WHEN MATCHED THEN UPDATE SET st.played=x.played,st.won=x.won,st.drawn=x.drawn,st.lost=x.lost,st.goals_for=x.gf,st.goals_against=x.ga,st.points=x.points;",
        "COMMIT;",
        "",
    ])
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUTPUT}")
    print(f"clubs={len(team_rows)} players={len(roster_rows)} fixtures={len(fixture_map)} venues={len(venue_rows)}")


if __name__ == "__main__":
    main()
