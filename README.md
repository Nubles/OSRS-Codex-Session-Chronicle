# Codex Session Chronicle

**Every play session, written down for you.** Codex Session Chronicle quietly keeps the story of each session as you play: the experience every skill gained, the levels you reached, the loot that dropped, what you killed and the moments worth telling. When the session ends it goes into a journal, and any session can be saved as a gilt-framed card to share.

It is the fifth plugin in the **Codex** suite, after [Codex Navigator](https://github.com/Nubles/OSRS-Codex-Navigator), [Codex Guardian](https://github.com/Nubles/OSRS-Codex-Guardian), [Codex Log Insight](https://github.com/Nubles/OSRS-Codex-Log-Insight) and [Codex Gear Roadmap](https://github.com/Nubles/OSRS-Codex-Gear-Roadmap), and shares their look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/session.png" width="225" alt="Session">
  <img src="docs/screenshots/journal.png" width="225" alt="Journal">
  <img src="docs/screenshots/card-tab.png" width="225" alt="Card">
</p>

## What it does

- **Session.** A clock that fills through each hour you play, then the session at a glance: a headline ("99 Ranged!", "24 Vorkath kills"), experience and loot with their hourly rates, levels gained and kills. Below that, every skill you trained with the levels it rose through, your loot laid out like bank slots, kills with the game's own kill count, and a timeline of the session's moments.
- **Moments.** Level ups and 99s, pets, new collection log slots, drops worth more than a value you choose, quests, achievement diaries, combat tasks, clue scrolls, personal bests and deaths, each with the time it happened.
- **Journal.** Every finished session, newest first, with your records (most experience, most loot), your hours this week, your streak of days played, and a chart of recent sessions by experience, loot or time. Click any session to open it again.
- **Card.** The session as a picture: your name, the headline, totals, top skills, best loot, kills and highlights on a gilt-framed page, drawn at double scale so the pixel fonts stay crisp. Save it wherever you choose with RuneLite's file chooser, or copy it straight to the clipboard.
- **Ticker.** An optional slim plaque in the game view with the session's clock, experience and loot.

<p align="center">
  <img src="docs/screenshots/card.png" width="360" alt="A session card">
</p>

## How sessions work

A session begins when you log in. If you log out and come back within the break allowance (15 minutes by default), the same session carries on; stay away longer and it is filed in the journal, and the next login begins a new one. World hops never end a session. Play time only counts while you are logged in.

Experience is measured from your first reading of each skill in the session. Loot comes from RuneLite's own loot events, so kills, raids, chests and clues are all counted, each item priced at the Grand Exchange price when it dropped. Kills are counted from loot and from the game's kill count messages, whichever saw more.

When a new session begins, a line in the chat box recaps the one before. Both that and the ticker can be turned off in the settings, and the card can leave your name off.

## Rules posture

Session Chronicle only reads what the game already shows you: your experience, loot events, and the game's own chat messages. It does not click, change menus or send anything over the network. Sessions are saved per account in your RuneLite profile, and a card is only written when you press Save and pick where it goes.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```
