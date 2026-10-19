package com.codex.sessionchronicle;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CodexSessionChronicleConfig.GROUP)
public interface CodexSessionChronicleConfig extends Config
{
	String GROUP = "codexsessionchronicle";

	@ConfigSection(
		name = "Sessions",
		description = "When a session begins and ends, and what it remembers",
		position = 0
	)
	String sessions = "sessions";

	@ConfigSection(
		name = "Display",
		description = "What Session Chronicle shows in the game and on the card",
		position = 1
	)
	String display = "display";

	@Range(min = 1, max = 240)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "breakMinutes",
		name = "Break allowance",
		description = "Log back in within this long and the same session carries on; stay away longer and a new one begins",
		position = 0,
		section = sessions
	)
	default int breakMinutes()
	{
		return 15;
	}

	@Range(min = 5, max = 100)
	@ConfigItem(
		keyName = "journalSize",
		name = "Sessions kept",
		description = "How many finished sessions the journal keeps for each account",
		position = 1,
		section = sessions
	)
	default int journalSize()
	{
		return 40;
	}

	@ConfigItem(
		keyName = "valuableDrop",
		name = "Notable drop value",
		description = "A single drop worth at least this many coins becomes one of the session's moments",
		position = 2,
		section = sessions
	)
	default int valuableDrop()
	{
		return 500_000;
	}

	@ConfigItem(
		keyName = "showTicker",
		name = "Session ticker",
		description = "Show a small plaque in the game view with the session's time, experience and loot",
		position = 0,
		section = display
	)
	default boolean showTicker()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatRecap",
		name = "Recap last session",
		description = "When a new session begins, put a line in the chat box recapping the one before",
		position = 1,
		section = display
	)
	default boolean chatRecap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "goalChat",
		name = "Goal reached in chat",
		description = "Put a line in the chat box when the session reaches the goal you set for it",
		position = 3,
		section = display
	)
	default boolean goalChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nameOnCard",
		name = "Name on cards",
		description = "Write your character's name on the session and week cards and their text copies; turn off to share them without it",
		position = 4,
		section = display
	)
	default boolean nameOnCard()
	{
		return true;
	}
}
