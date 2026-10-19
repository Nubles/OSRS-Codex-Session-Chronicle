# Codex Session Chronicle

**Every play session, written down for you.** Codex Session Chronicle quietly keeps the story of each session as you play: the experience every skill gained, the levels you reached, the loot that dropped, what you killed and the moments worth telling. When the session ends it goes into a journal, and any session can be saved as a gilt-framed card to share.

It is the fifth plugin in the **Codex** suite, after [Codex Navigator](https://github.com/Nubles/OSRS-Codex-Navigator), [Codex Guardian](https://github.com/Nubles/OSRS-Codex-Guardian), [Codex Log Insight](https://github.com/Nubles/OSRS-Codex-Log-Insight) and [Codex Gear Roadmap](https://github.com/Nubles/OSRS-Codex-Gear-Roadmap), and shares their look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/session.png" width="225" alt="Session">
  <img src="docs/screenshots/journal.png" width="225" alt="Journal">
  <img src="docs/screenshots/card-tab.png" width="225" alt="Card">
</p>

## What it does

- **Session.** A clock that fills through each hour you play, then the session at a glance: a headline ("99 Ranged!", "Draconic visage!", "24 Vorkath kills", "Mining at 192k/hr"), experience and loot with their hourly rates, levels gained and kills. Below that, every skill you trained with the levels it rose through, your loot laid out like bank slots, kills with the game's own kill count, and a timeline of the session's moments. Changing activity? **End session** files it in the journal and starts a fresh one straight away.
- **Goals.** Give the session a goal with one click: experience, loot, kills or play time. The offers come from your own journal (your usual session, your best, and a little past your best), or round numbers until you have a few sessions. A bar shows how far you are and when you should get there at the pace so far; reaching it becomes a moment, a chat line and a mark on the ticker.
- **Pace.** Your main skill's experience an hour, your main monster's kills an hour and your loot an hour, each set beside your earlier sessions of the same kind: a tick at your usual, a gilt diamond at your best, and a word on how you are doing ("12% above your usual", "Faster than your best"). Only sessions of 15 minutes or more count, so a short burst never sets the bar.
- **Personal records.** When a session ends, any record it set becomes one of its moments: your fastest Mining yet, your fastest Vorkath trip, your richest session, your most experience. The Journal lists your fastest rate for every skill and monster, and your longest streak of days played.
- **Moments.** Level ups and 99s, pets, new collection log slots, drops worth more than a value you choose, quests, achievement diaries, combat tasks, clue scrolls, personal bests and deaths (with what killed you), each with the time it happened. A big drop that is also a new log slot is one moment, not two.
- **Journal.** Every finished session, newest first, with your records (most experience, most loot), your hours this week, your streak of days played, and a chart of recent sessions by experience, loot or time. Click any session to open it again, and **Remove** one you would rather not keep.
- **Card.** The session as a picture: your name, the headline, totals, its goal and pace against your past, top skills, best loot, kills and highlights on a gilt-framed page, drawn at double scale so the pixel fonts stay crisp. Save it wherever you choose with RuneLite's file chooser, or copy it straight to the clipboard.
- **Ticker.** An optional slim plaque in the game view with the session's clock, experience and loot.

<p align="center">
  <img src="docs/screenshots/pace.png" width="225" alt="Goal and pace">
  <img src="docs/screenshots/records.png" width="225" alt="Personal records">
</p>

<p align="center">
  <img src="docs/screenshots/card.png" width="360" alt="A session card">
</p>

## How sessions work

A session begins when you log in. If you log out and come back within the break allowance (15 minutes by default), the same session carries on; stay away longer and it is filed in the journal, and the next login begins a new one. World hops never end a session. Play time only counts while you are logged in, and so does experience: anything you gain on another device during a break is left out, so the rates stay honest.

Experience is measured from your first reading of each skill in the session. Last Man Standing and the PvP Arena lend you stats for a while; those never count as progress. Loot comes from RuneLite's loot events, each item priced at the Grand Exchange price when it dropped. Monster kills are always seen; raids, chests and clue caskets come from RuneLite's Loot Tracker plugin, so keep it switched on to have them counted. Kills are counted from loot and from the game's kill count messages, whichever saw more, and the two are matched even when they spell a boss differently. Only real kills count as kills, toward the Kills total and a kills goal alike: monsters, and bosses, raids and chests the game keeps a kill count for. Bird nests, supply crates and clue caskets are listed apart under **Opened**.

When a new session begins, a line in the chat box recaps the one before. Both that and the ticker can be turned off in the settings, and the card can leave your name off.

## Rules posture

Session Chronicle only reads what the game already shows you: your experience, loot events, and the game's own chat messages. It does not click, change menus or send anything over the network. Sessions are saved per account in your RuneLite profile (also as the client closes), and a card is only written when you press Save and pick where it goes.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```
