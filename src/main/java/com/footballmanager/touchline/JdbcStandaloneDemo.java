package com.footballmanager.touchline;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.Scanner;

public class JdbcStandaloneDemo {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String url = "jdbc:oracle:thin:@localhost:1521/XEPDB1";
        String user = "DBMS";
        String password = "chandan";

        try {
            Connection con = DriverManager.getConnection(url, user, password);
            System.out.println("Oracle Connected Successfully!\n\nAvailable clubs:");

            Statement st = con.createStatement();
            ResultSet teams = st.executeQuery("SELECT team_id, name FROM teams ORDER BY name");
            while (teams.next()) {
                System.out.println(teams.getLong("team_id") + "  " + teams.getString("name"));
            }

            System.out.print("Enter player name: ");
            String name = sc.nextLine();
            System.out.print("Enter team ID: ");
            long teamId = Long.parseLong(sc.nextLine());
            System.out.print("Enter position: ");
            String position = sc.nextLine();
            System.out.print("Enter nationality: ");
            String nationality = sc.nextLine();
            System.out.print("Enter shirt number (or press Enter to leave blank): ");
            String numberInput = sc.nextLine().trim();

            String sql = "INSERT INTO players(name, team_id, position, nationality, jersey_no) "
                    + "VALUES (?, ?, ?, ?, ?)";
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"PLAYER_ID"});
            ps.setString(1, name);
            ps.setLong(2, teamId);
            ps.setString(3, position);
            ps.setString(4, nationality);
            if (numberInput.isEmpty()) {
                ps.setNull(5, Types.NUMERIC);
            } else {
                ps.setInt(5, Integer.parseInt(numberInput));
            }

            int rows = ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (rows == 1 && keys.next()) {
                System.out.println("Player inserted. player_id=" + keys.getLong(1));
            } else {
                System.out.println(rows + " row(s) inserted.");
            }

            keys.close();
            ps.close();
            teams.close();
            st.close();
            con.close();
        } catch (Exception e) {
            System.out.println("JDBC Error: " + e.getMessage());
        }
        sc.close();
    }
}
