-- Touchline officials seed (Oracle / SQL Developer).
-- Run as the DBMS schema owner after schema-oracle.sql and the league/team seed.
-- Safe to rerun: referees match by name+nation; coaches by team+name.
-- Referee registry names are drawn from national top-flight/FIFA referee pools.
-- Coach entries are a verified 2026/27 starter snapshot for clubs listed below;
-- add rows to the source list for clubs whose coach is not included yet.
SET DEFINE OFF

MERGE INTO referees r
USING (
    SELECT 'Michael Oliver' name, 'England' nationality FROM dual UNION ALL
    SELECT 'Anthony Taylor', 'England' FROM dual UNION ALL
    SELECT 'Stuart Attwell', 'England' FROM dual UNION ALL
    SELECT 'Darren England', 'England' FROM dual UNION ALL
    SELECT 'Jarred Gillett', 'Australia' FROM dual UNION ALL
    SELECT 'Simon Hooper', 'England' FROM dual UNION ALL
    SELECT 'Chris Kavanagh', 'England' FROM dual UNION ALL
    SELECT 'Peter Bankes', 'England' FROM dual UNION ALL
    SELECT 'Craig Pawson', 'England' FROM dual UNION ALL
    SELECT 'Sam Barrott', 'England' FROM dual UNION ALL
    SELECT 'John Brooks', 'England' FROM dual UNION ALL
    SELECT 'Tom Bramall', 'England' FROM dual UNION ALL
    SELECT 'Rob Jones', 'England' FROM dual UNION ALL
    SELECT 'Andy Madley', 'England' FROM dual UNION ALL
    SELECT 'Alejandro Hernandez Hernandez', 'Spain' FROM dual UNION ALL
    SELECT 'Jesus Gil Manzano', 'Spain' FROM dual UNION ALL
    SELECT 'Jose Maria Sanchez Martinez', 'Spain' FROM dual UNION ALL
    SELECT 'Cesar Soto Grado', 'Spain' FROM dual UNION ALL
    SELECT 'Juan Martinez Munuera', 'Spain' FROM dual UNION ALL
    SELECT 'Isidro Diaz de Mera', 'Spain' FROM dual UNION ALL
    SELECT 'Guillermo Cuadra Fernandez', 'Spain' FROM dual UNION ALL
    SELECT 'Ricardo de Burgos Bengoetxea', 'Spain' FROM dual UNION ALL
    SELECT 'Marco Guida', 'Italy' FROM dual UNION ALL
    SELECT 'Davide Massa', 'Italy' FROM dual UNION ALL
    SELECT 'Simone Sozza', 'Italy' FROM dual UNION ALL
    SELECT 'Fabio Maresca', 'Italy' FROM dual UNION ALL
    SELECT 'Maurizio Mariani', 'Italy' FROM dual UNION ALL
    SELECT 'Daniele Doveri', 'Italy' FROM dual UNION ALL
    SELECT 'Michael Fabbri', 'Italy' FROM dual UNION ALL
    SELECT 'Luca Pairetto', 'Italy' FROM dual UNION ALL
    SELECT 'Francois Letexier', 'France' FROM dual UNION ALL
    SELECT 'Clement Turpin', 'France' FROM dual UNION ALL
    SELECT 'Benoit Bastien', 'France' FROM dual UNION ALL
    SELECT 'Willy Delajod', 'France' FROM dual UNION ALL
    SELECT 'Jerome Brisard', 'France' FROM dual UNION ALL
    SELECT 'Jeremie Pignard', 'France' FROM dual UNION ALL
    SELECT 'Eric Wattellier', 'France' FROM dual UNION ALL
    SELECT 'Mathieu Vernice', 'France' FROM dual UNION ALL
    SELECT 'Romain Lissorgue', 'France' FROM dual UNION ALL
    SELECT 'Bastien Dechepy', 'France' FROM dual UNION ALL
    SELECT 'Deniz Aytekin', 'Germany' FROM dual UNION ALL
    SELECT 'Florian Badstubner', 'Germany' FROM dual UNION ALL
    SELECT 'Benjamin Brand', 'Germany' FROM dual UNION ALL
    SELECT 'Robin Braun', 'Germany' FROM dual UNION ALL
    SELECT 'Bastian Dankert', 'Germany' FROM dual UNION ALL
    SELECT 'Christian Dingert', 'Germany' FROM dual UNION ALL
    SELECT 'Florian Exner', 'Germany' FROM dual UNION ALL
    SELECT 'Timo Gerach', 'Germany' FROM dual UNION ALL
    SELECT 'Robert Hartmann', 'Germany' FROM dual UNION ALL
    SELECT 'Patrick Ittrich', 'Germany' FROM dual UNION ALL
    SELECT 'Sven Jablonski', 'Germany' FROM dual UNION ALL
    SELECT 'Matthias Jollenbeck', 'Germany' FROM dual UNION ALL
    SELECT 'Harm Osmers', 'Germany' FROM dual UNION ALL
    SELECT 'Martin Petersen', 'Germany' FROM dual UNION ALL
    SELECT 'Tobias Reichel', 'Germany' FROM dual UNION ALL
    SELECT 'Daniel Schlager', 'Germany' FROM dual UNION ALL
    SELECT 'Robert Schroder', 'Germany' FROM dual UNION ALL
    SELECT 'Daniel Siebert', 'Germany' FROM dual UNION ALL
    SELECT 'Sascha Stegemann', 'Germany' FROM dual UNION ALL
    SELECT 'Tobias Stieler', 'Germany' FROM dual UNION ALL
    SELECT 'Soren Storks', 'Germany' FROM dual UNION ALL
    SELECT 'Tobias Welz', 'Germany' FROM dual UNION ALL
    SELECT 'Frank Willenborg', 'Germany' FROM dual UNION ALL
    SELECT 'Felix Zwayer', 'Germany' FROM dual
) x
ON (UPPER(r.name)=UPPER(x.name) AND UPPER(NVL(r.nationality,'?'))=UPPER(x.nationality))
WHEN NOT MATCHED THEN INSERT (name, nationality) VALUES (x.name, x.nationality);

MERGE INTO coaches c
USING (
    SELECT t.team_id, source.name, source.nationality
    FROM (
    SELECT 'AFC Bournemouth' team, 'Marco Rose' name, 'Germany' nationality FROM dual UNION ALL
    SELECT 'Arsenal', 'Mikel Arteta', 'Spain' FROM dual UNION ALL
    SELECT 'Aston Villa', 'Unai Emery', 'Spain' FROM dual UNION ALL
    SELECT 'Brentford', 'Keith Andrews', 'Ireland' FROM dual UNION ALL
    SELECT 'Brighton & Hove Albion', 'Fabian Hurzeler', 'Germany' FROM dual UNION ALL
    SELECT 'Chelsea', 'Xabi Alonso', 'Spain' FROM dual UNION ALL
    SELECT 'Coventry City', 'Frank Lampard', 'England' FROM dual UNION ALL
    SELECT 'Crystal Palace', 'Pierre Sage', 'France' FROM dual UNION ALL
    SELECT 'Everton', 'David Moyes', 'Scotland' FROM dual UNION ALL
    SELECT 'Fulham', 'Alvaro Arbeloa', 'Spain' FROM dual UNION ALL
    SELECT 'Hull City', 'Sergej Jakirovic', 'Bosnia and Herzegovina' FROM dual UNION ALL
    SELECT 'Ipswich Town', 'Gary O''Neil', 'England' FROM dual UNION ALL
    SELECT 'Leeds United', 'Daniel Farke', 'Germany' FROM dual UNION ALL
    SELECT 'Liverpool', 'Andoni Iraola', 'Spain' FROM dual UNION ALL
    SELECT 'Manchester City', 'Enzo Maresca', 'Italy' FROM dual UNION ALL
    SELECT 'Manchester United', 'Michael Carrick', 'England' FROM dual UNION ALL
    SELECT 'Newcastle United', 'Matthias Jaissle', 'Germany' FROM dual UNION ALL
    SELECT 'Nottingham Forest', 'Oliver Glasner', 'Austria' FROM dual UNION ALL
    SELECT 'Sunderland', 'Regis Le Bris', 'France' FROM dual UNION ALL
    SELECT 'Tottenham Hotspur', 'Roberto De Zerbi', 'Italy' FROM dual UNION ALL
    SELECT 'Bayern Munich', 'Vincent Kompany', 'Belgium' FROM dual UNION ALL
    SELECT 'Borussia Dortmund', 'Niko Kovac', 'Croatia' FROM dual UNION ALL
    SELECT 'Barcelona', 'Hansi Flick', 'Germany' FROM dual UNION ALL
    SELECT 'Atletico Madrid', 'Diego Simeone', 'Argentina' FROM dual UNION ALL
    SELECT 'Real Betis', 'Manuel Pellegrini', 'Chile' FROM dual UNION ALL
    SELECT 'Celta Vigo', 'Claudio Giraldez', 'Spain' FROM dual UNION ALL
    SELECT 'Getafe', 'Jose Bordalas', 'Spain' FROM dual UNION ALL
    SELECT 'Rayo Vallecano', 'Inigo Perez', 'Spain' FROM dual UNION ALL
    SELECT 'Sevilla', 'Luis Garcia Plaza', 'Spain' FROM dual UNION ALL
    SELECT 'Paris Saint-Germain', 'Luis Enrique', 'Spain' FROM dual UNION ALL
    SELECT 'Lyon', 'Paulo Fonseca', 'Portugal' FROM dual UNION ALL
    SELECT 'Brest', 'Eric Roy', 'France' FROM dual UNION ALL
    SELECT 'Angers', 'Alexandre Dujeux', 'France' FROM dual UNION ALL
    SELECT 'Napoli', 'Massimiliano Allegri', 'Italy' FROM dual UNION ALL
    SELECT 'Internazionale', 'Cristian Chivu', 'Romania' FROM dual UNION ALL
    SELECT 'Como', 'Cesc Fabregas', 'Spain' FROM dual UNION ALL
    SELECT 'Bologna', 'Vincenzo Italiano', 'Italy' FROM dual
) source
JOIN teams t ON UPPER(t.name)=UPPER(source.team)
) x
ON (c.team_id=x.team_id AND UPPER(c.name)=UPPER(x.name))
WHEN NOT MATCHED THEN INSERT (team_id, name, nationality) VALUES (x.team_id, x.name, x.nationality);

COMMIT;

-- Check what was added:
SELECT COUNT(*) AS referee_count FROM referees;
SELECT l.name AS league, COUNT(c.coach_id) AS coach_count
FROM coaches c JOIN teams t ON t.team_id=c.team_id
JOIN leagues l ON l.league_id=t.league_id
GROUP BY l.name ORDER BY l.name;
