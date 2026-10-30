document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("form[data-submit-on-change]").forEach((form) => {
    const select = form.querySelector("select");
    select?.addEventListener("change", () => form.requestSubmit());
  });

  const playerLeagueFilter = document.querySelector("[data-filter-league]");
  const playerTeamFilter = document.querySelector("[data-filter-team]");
  function filterPlayerClubs() {
    if (!playerLeagueFilter || !playerTeamFilter) return;
    const leagueId = playerLeagueFilter.value;
    for (const option of playerTeamFilter.options) {
      if (!option.value) continue;
      const visible = !leagueId || option.dataset.leagueId === leagueId;
      option.hidden = !visible;
      option.disabled = !visible;
      if (!visible && option.selected) playerTeamFilter.value = "";
    }
  }
  playerLeagueFilter?.addEventListener("change", filterPlayerClubs);
  filterPlayerClubs();

  function filterPlayers(select, teamId) {
    if (!select) return;
    const playerOptions = [...select.options].filter((option) => option.value);
    if (!playerOptions.some((option) => option.dataset.teamId)) {
      select.disabled = false;
      return;
    }
    let hasOption = false;
    for (const option of select.options) {
      if (!option.value) continue;
      const visible = option.dataset.teamId === teamId;
      option.hidden = !visible;
      option.disabled = !visible;
      hasOption ||= visible;
      if (!visible && option.selected) select.value = "";
    }
    select.disabled = !teamId || !hasOption;
  }

  document.querySelectorAll("[data-filter-players]").forEach((teamSelect) => {
    const form = teamSelect.closest("form");
    const playerSelects =
      teamSelect.name === "teamId"
        ? [form?.querySelector('select[name="playerId"]')]
        : [
            form?.querySelector('select[name="playerOutId"]'),
            form?.querySelector('select[name="playerInId"]'),
          ];
    const update = () =>
      playerSelects.forEach((select) =>
        filterPlayers(select, teamSelect.value),
      );
    teamSelect.addEventListener("change", update);
    update();
  });

  const seasonSelect = document.querySelector("[data-season-select]");
  const destinationSelect = document.querySelector("[data-to-team-select]");
  const sourceLeagueSelect = document.querySelector(
    "[data-from-league-filter]",
  );
  const sourceTeamSelect = document.querySelector("[data-from-team-select]");
  const transferPlayerSelect = document.querySelector("[data-player-select]");

  function filterTeams(select, leagueId, enabled) {
    if (!select) return;
    const teamOptions = [...select.options].filter((option) => option.value);
    if (!teamOptions.some((option) => option.dataset.leagueId)) {
      for (const option of teamOptions) {
        option.hidden = false;
        option.disabled = false;
      }
      select.disabled = !enabled;
      return;
    }
    let hasTeam = false;
    for (const option of select.options) {
      if (!option.value) continue;
      const visible = enabled && option.dataset.leagueId === leagueId;
      option.hidden = !visible;
      option.disabled = !visible;
      hasTeam ||= visible;
      if (!visible && option.selected) select.value = "";
    }
    select.disabled = !enabled || !hasTeam;
  }

  function updateDestination() {
    const seasonOption = seasonSelect?.selectedOptions[0];
    const leagueId = seasonOption?.dataset.leagueId;
    const selected = Boolean(seasonSelect?.value);
    filterTeams(
      destinationSelect,
      leagueId,
      selected && (Boolean(leagueId) || !seasonOption?.dataset.leagueId),
    );
  }

  function updateSource() {
    const leagueId = sourceLeagueSelect?.value;
    if (!sourceTeamSelect) return;
    if (!leagueId) {
      for (const option of sourceTeamSelect.options) {
        if (!option.value) continue;
        option.hidden = false;
        option.disabled = false;
      }
      sourceTeamSelect.disabled = false;
      return;
    }
    filterTeams(sourceTeamSelect, leagueId, true);
  }

  seasonSelect?.addEventListener("change", updateDestination);
  sourceLeagueSelect?.addEventListener("change", updateSource);
  transferPlayerSelect?.addEventListener("change", () => {
    const playerOption = transferPlayerSelect.selectedOptions[0];
    const currentTeamId = playerOption?.dataset.currentTeamId;
    if (!sourceTeamSelect) return;
    if (!currentTeamId) {
      if (sourceLeagueSelect) sourceLeagueSelect.value = "";
      updateSource();
      sourceTeamSelect.value = "";
      return;
    }
    const currentTeam = [...sourceTeamSelect.options].find(
      (option) => option.value === currentTeamId,
    );
    if (currentTeam) {
      sourceLeagueSelect.value = currentTeam.dataset.leagueId || "";
      updateSource();
      sourceTeamSelect.value = currentTeamId;
    } else {
      if (sourceLeagueSelect) sourceLeagueSelect.value = "";
      updateSource();
      sourceTeamSelect.value = "";
    }
  });
  updateDestination();
  updateSource();
});
