# Touchline — Football League Manager

A multi-page football league management website built with Java, Spring Boot, Thymeleaf, and Oracle JDBC (OJDBC). It follows the entities in the supplied ER diagram and keeps the database schema in `src/main/resources/schema-oracle.sql`.

## Pages

- **Overview** — club, player, match, and league totals; recent fixtures, the table, and recent goal events.
- **Leagues & seasons** — create leagues and seasons. Creating a season adds standings rows for the league's existing clubs.
- **Clubs** — register clubs, assign league and home ground, and add stadiums.
- **Players** — maintain player profiles and club affiliations.
- **Fixtures & results** — schedule matches, save scores, and automatically recalculate season standings from completed matches.
- **Match reports** — record goals, cards, and substitutions.
- **Transfers** — record a player transfer and update the player's current club.
- **Standings** — show played, wins, draws, losses, goal difference, and points.

## 1. Create the Oracle tables

1. Open Oracle SQL Developer and connect as the application owner (`oracle`, service `XEPDB1`).
2. Open `src/main/resources/schema-oracle.sql` and run it as a script (F5). Run it once; it creates the tables and indexes but does not drop data or create the Oracle user.

The script defines countries, leagues, seasons, stadiums, teams, coaches, players, matches, standings, referees, match referees, lineups, scorers, cards, substitutions, and transfers.

## Load the 2026/27 leagues

`database/seed-oracle-2026-27.sql` is an idempotent Oracle seed for the five current leagues. It adds the league/country/season records, 96 clubs and home grounds, 2,911 current roster entries, all 1,752 league fixtures, scores available in the source snapshot, and standings recalculated from completed matches. It keeps earlier seasons and unrelated records. Open the file in SQL Developer while connected as `oracle`, then run it with **F5**.

The seed data snapshot is generated from ESPN's public soccer scoreboard and roster endpoints. To refresh it, run `python tools/generate_oracle_seed.py`; Python 3.10+ is sufficient. The generator caches the API responses under `target/seed-source-2026-27` and writes the SQL file. Fixture times are UTC stored in Oracle `TIMESTAMP` columns, which do not retain timezone metadata. Stadium capacities are left null when the source does not provide them.

The 2026/27 league sizes are 20 clubs each in LaLiga, Premier League and Serie A, and 18 clubs each in Ligue 1 and the Bundesliga.

## 2. Configure the connection

Use your Oracle user's password in the environment; the repository does not store credentials. In PowerShell, for the current terminal session:

```powershell
$env:DB_URL = 'jdbc:oracle:thin:@//localhost:1521/XEPDB1'
$env:DB_USERNAME = 'oracle'
$env:DB_PASSWORD = '<your Oracle user password>'
```

The URL and username default to `localhost:1521/XEPDB1` and `oracle`. Change them if your Oracle connection uses a different service or user. The password has no default. `.env.example` is a reference only; Spring Boot does not load it automatically.

## 3. Run the website

Requirements: Java 17 or newer and Maven 3.6.3 or newer.

```powershell
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Set `SERVER_PORT` to change the port.

Oracle JDBC is provided by the `com.oracle.database.jdbc:ojdbc11` Maven dependency. The app uses Spring JDBC with parameterized SQL; it does not initialize or migrate the database automatically.

## Schema notes

The provided ER diagram is handwritten, so this implementation uses a normalized schema with explicit foreign keys. Standings are kept per season and club. Saving a completed score recalculates that season's standings from completed match results (3 points for a win, 1 for a draw). Goal, card, and substitution event entry is available on the Match reports page. Lineups, referee assignments, and coach records are present in the schema for extension, but do not yet have dedicated management forms.
