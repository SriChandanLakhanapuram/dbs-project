package com.footballmanager.touchline;

import org.springframework.stereotype.Controller;
import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class WebController {
    private static final Logger log = LoggerFactory.getLogger(WebController.class);
    private final FootballService football;

    public WebController(FootballService football) { this.football = football; }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("page", "dashboard");
        model.addAttribute("teamCount", football.count("teams"));
        model.addAttribute("playerCount", football.count("players"));
        model.addAttribute("matchCount", football.count("matches"));
        model.addAttribute("leagueCount", football.count("leagues"));
        model.addAttribute("leagueRaces", football.dashboardLeagueRaces());
        model.addAttribute("matches", football.matches().stream().limit(5).toList());
        model.addAttribute("scorers", football.recentScorers());
        return "dashboard";
    }

    @GetMapping("/teams")
    public String teams(@RequestParam(required = false) Long leagueId, Model model) {
        model.addAttribute("page", "teams"); model.addAttribute("teams", football.teams(leagueId));
        model.addAttribute("leagues", football.leagueOptions()); model.addAttribute("stadiums", football.stadiumOptions());
        model.addAttribute("selectedLeagueId", leagueId);
        return "teams";
    }

    @PostMapping("/stadiums")
    public String addStadium(@RequestParam String name, @RequestParam(required = false) String location,
                             @RequestParam(required = false) Integer capacity, RedirectAttributes flash) {
        football.addStadium(name.trim(), location == null ? null : location.trim(), capacity);
        flash.addFlashAttribute("notice", "Stadium added."); return "redirect:/teams#add-stadium";
    }

    @PostMapping("/teams")
    public String addTeam(@RequestParam String name, @RequestParam Long leagueId,
                          @RequestParam(required = false) Long stadiumId,
                          @RequestParam(required = false) Integer foundedYear, RedirectAttributes flash) {
        football.addTeam(name.trim(), leagueId, stadiumId, foundedYear);
        flash.addFlashAttribute("notice", "Club added and its season table entries created.");
        return "redirect:/teams";
    }

    @GetMapping("/players")
    public String players(@RequestParam(required = false) Long teamId,
                          @RequestParam(required = false) Long leagueId,
                          @RequestParam(required = false) String nationality,
                          @RequestParam(required = false) String position, Model model) {
        model.addAttribute("page", "players");
        model.addAttribute("players", football.players(teamId, leagueId, nationality, position));
        model.addAttribute("teams", football.teamOptions()); model.addAttribute("leagues", football.leagueOptions());
        model.addAttribute("nationalities", football.playerNationalities()); model.addAttribute("positions", football.playerPositions());
        model.addAttribute("filterTeamId", teamId); model.addAttribute("filterLeagueId", leagueId);
        model.addAttribute("filterNationality", nationality); model.addAttribute("filterPosition", position);
        return "players";
    }

    @PostMapping("/players")
    public String addPlayer(@RequestParam String name, @RequestParam(required = false) Long teamId,
                            @RequestParam(required = false) String position,
                            @RequestParam(required = false) String nationality,
                            @RequestParam(required = false) Integer jerseyNo, RedirectAttributes flash) {
        football.addPlayer(name.trim(), teamId, position, nationality, jerseyNo);
        flash.addFlashAttribute("notice", "Player added to the squad."); return "redirect:/players";
    }

    @GetMapping("/matches")
    public String matches(@RequestParam(required = false) Long leagueId,
                          @RequestParam(defaultValue = "upcoming") String tab, Model model) {
        boolean completedTab = "completed".equalsIgnoreCase(tab);
        model.addAttribute("page", "matches");
        model.addAttribute("matches", football.fixtures(leagueId, completedTab));
        model.addAttribute("upcomingCount", football.fixtures(leagueId, false).size());
        model.addAttribute("completedCount", football.fixtures(leagueId, true).size());
        model.addAttribute("currentTab", completedTab ? "completed" : "upcoming");
        model.addAttribute("selectedLeagueId", leagueId); model.addAttribute("leagues", football.leagueOptions());
        model.addAttribute("teams", football.teamOptions()); model.addAttribute("seasons", football.seasonOptions());
        model.addAttribute("stadiums", football.stadiumOptions()); return "matches";
    }

    @PostMapping("/matches")
    public String addMatch(@RequestParam Long seasonId, @RequestParam Long homeTeamId,
                           @RequestParam Long awayTeamId, @RequestParam Long stadiumId,
                           @RequestParam String matchDate, RedirectAttributes flash) {
        if (homeTeamId.equals(awayTeamId)) {
            flash.addFlashAttribute("error", "Choose two different clubs for a fixture.");
            return "redirect:/matches";
        }
        football.addMatch(seasonId, homeTeamId, awayTeamId, stadiumId, matchDate);
        flash.addFlashAttribute("notice", "Fixture added to the schedule."); return "redirect:/matches";
    }

    @PostMapping("/matches/result")
    public String saveResult(@RequestParam Long matchId, @RequestParam Integer homeGoals,
                             @RequestParam Integer awayGoals,
                             @RequestParam(required = false) Long leagueId, RedirectAttributes flash) {
        football.saveResult(matchId, homeGoals, awayGoals);
        flash.addFlashAttribute("notice", "Result saved and the season standings recalculated.");
        return "redirect:/matches?tab=completed" + (leagueId == null ? "" : "&leagueId=" + leagueId);
    }

    @GetMapping("/standings")
    public String standings(@RequestParam(required = false) Long leagueId, Model model) {
        model.addAttribute("page", "standings"); model.addAttribute("standings", football.standings(leagueId));
        model.addAttribute("leagues", football.leagueOptions()); model.addAttribute("selectedLeagueId", leagueId);
        return "standings";
    }

    @GetMapping("/leagues")
    public String leagues(Model model) {
        model.addAttribute("page", "leagues"); model.addAttribute("leagues", football.leagues());
        model.addAttribute("seasons", football.seasons()); return "leagues";
    }

    @PostMapping("/leagues")
    public String addLeague(@RequestParam String name, RedirectAttributes flash) {
        football.addLeague(name.trim()); flash.addFlashAttribute("notice", "League added."); return "redirect:/leagues";
    }

    @PostMapping("/seasons")
    public String addSeason(@RequestParam Long leagueId, @RequestParam String label,
                            @RequestParam(defaultValue = "") String startDate,
                            @RequestParam(defaultValue = "") String endDate, RedirectAttributes flash) {
        football.addSeason(leagueId, label.trim(), startDate, endDate);
        flash.addFlashAttribute("notice", "Season created with a table row for every club in the league.");
        return "redirect:/leagues";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) Long matchId,
                          @RequestParam(defaultValue = "upcoming") String tab, Model model) {
        boolean completedTab = "completed".equalsIgnoreCase(tab);
        model.addAttribute("page", "reports"); model.addAttribute("matches", football.matchOptions(completedTab));
        model.addAttribute("currentTab", completedTab ? "completed" : "upcoming");
        Map<String, Object> selectedMatch = matchId == null ? null : football.matchReportContext(matchId);
        if (selectedMatch != null && completedTab != "Completed".equals(selectedMatch.get("status"))) selectedMatch = null;
        model.addAttribute("selectedMatch", selectedMatch);
        model.addAttribute("players", selectedMatch == null ? java.util.List.of() : football.playersForMatch(matchId));
        model.addAttribute("matchTeams", selectedMatch == null ? java.util.List.of() : football.teamsForMatch(matchId));
        model.addAttribute("teams", football.teamOptions());
        model.addAttribute("scorers", football.recentScorers()); model.addAttribute("cards", football.recentCards());
        return "reports";
    }

    @PostMapping("/reports/goal")
    public String addGoal(@RequestParam Long matchId, @RequestParam Long playerId,
                          @RequestParam Long teamId, @RequestParam Integer minute,
                          @RequestParam(defaultValue = "upcoming") String tab, RedirectAttributes flash) {
        try { football.addGoal(matchId, playerId, teamId, minute); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return reportRedirect(matchId, tab); }
        flash.addFlashAttribute("notice", "Goal event added and the match score updated."); return reportRedirect(matchId, tab);
    }

    @PostMapping("/reports/card")
    public String addCard(@RequestParam Long matchId, @RequestParam Long playerId,
                          @RequestParam String cardType, @RequestParam Integer minute,
                          @RequestParam(defaultValue = "upcoming") String tab, RedirectAttributes flash) {
        try { football.addCard(matchId, playerId, cardType, minute); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return reportRedirect(matchId, tab); }
        flash.addFlashAttribute("notice", "Card event added to the match report."); return reportRedirect(matchId, tab);
    }

    @PostMapping("/reports/substitution")
    public String addSubstitution(@RequestParam Long matchId, @RequestParam Long playerOutId,
                                  @RequestParam Long playerInId, @RequestParam Integer minute,
                                  @RequestParam(defaultValue = "upcoming") String tab, RedirectAttributes flash) {
        try { football.addSubstitution(matchId, playerOutId, playerInId, minute); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return reportRedirect(matchId, tab); }
        flash.addFlashAttribute("notice", "Substitution added to the match report."); return reportRedirect(matchId, tab);
    }

    private String reportRedirect(long matchId, String tab) { return "redirect:/reports?tab=" + tab + "&matchId=" + matchId + "#add-event"; }

    @GetMapping("/transfers")
    public String transfers(Model model) {
        model.addAttribute("page", "transfers"); model.addAttribute("transfers", football.transfers());
        model.addAttribute("players", football.playerOptions()); model.addAttribute("teams", football.teamOptions());
        model.addAttribute("seasons", football.seasonOptions()); model.addAttribute("leagues", football.leagueOptions()); return "transfers";
    }

    @PostMapping("/transfers")
    public String addTransfer(@RequestParam Long seasonId, @RequestParam Long playerId,
                              @RequestParam Long toTeamId,
                              @RequestParam String transferDate, @RequestParam(defaultValue = "0") BigDecimal fee,
                              RedirectAttributes flash) {
        try { football.addTransfer(seasonId, playerId, toTeamId, transferDate, fee); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return "redirect:/transfers#record-transfer"; }
        catch (DataAccessException ex) {
            log.error("Oracle rejected a transfer record", ex);
            flash.addFlashAttribute("error", "Oracle could not save this transfer. Check the server console for the database error.");
            return "redirect:/transfers#record-transfer";
        }
        flash.addFlashAttribute("notice", "Transfer recorded and the player's current club updated.");
        return "redirect:/transfers";
    }
}
