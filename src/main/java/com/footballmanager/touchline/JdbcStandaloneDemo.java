package com.footballmanager.touchline;
import java.sql.DriverManager;
import java.util.Scanner;

public class JdbcStandaloneDemo {
    public static void main(String[] args) throws Exception {
        String password = System.getenv("DB_PASSWORD");
        if (password == null) throw new IllegalStateException("Set DB_PASSWORD first.");

        try (var input = new Scanner(System.in);
             var connection = DriverManager.getConnection(
                     "jdbc:oracle:thin:@//localhost:1521/XEPDB1",
                     System.getenv().getOrDefault("DB_USERNAME", "DBMS"), password)) {
            connection.setAutoCommit(true);

            System.out.print("matchId: ");
            long matchId = Long.parseLong(input.nextLine());
            System.out.print("playerId: ");
            long playerId = Long.parseLong(input.nextLine());
            System.out.print("teamId: ");
            long teamId = Long.parseLong(input.nextLine());
            System.out.print("minute: ");
            int minute = Integer.parseInt(input.nextLine());

            // Paste one jdbc.update(...) statement's SQL here; bind its values below.
            String sql = "INSERT INTO scorers(match_id, player_id, team_id, minute) VALUES (?, ?, ?, ?)";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, matchId);
                statement.setLong(2, playerId);
                statement.setLong(3, teamId);
                statement.setInt(4, minute);
                System.out.println("Rows changed: " + statement.executeUpdate());
            }
        }
    }
}
