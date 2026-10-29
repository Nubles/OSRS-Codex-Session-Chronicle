package com.codex.sessionchronicle.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the game's own messages for the things a session should remember. It takes the message
 * with its colour tags already removed and answers with what it found, or null.
 */
public final class ChatParser
{
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (?:completed |subdued )?(.+?) (?:kill |success |completion )?count is: ?([\\d,]+)\\b");
	/** Skilling minigames that announce their total in words of their own, by the line they count on. */
	private static final Pattern RIFTS = Pattern.compile("^Amount of rifts you have closed: ?([\\d,]+)");
	private static final Pattern CONTRACTS = Pattern.compile("^You have completed ([\\d,]+) contracts? with a total of");
	private static final Pattern COLLECTION = Pattern.compile("^New item added to your collection log: (.+?)\\.?$");
	private static final Pattern QUEST = Pattern.compile("^Congratulations,? you've completed a quest: (.+?)\\.?$");
	private static final Pattern DIARY = Pattern.compile(
		"^Congratulations! You have completed all of the (\\w+) tasks in the (.+?) area\\b");
	private static final Pattern TASK = Pattern.compile(
		"^Congratulations, you've completed an? (\\w+) combat task: (.+?)(?: \\(\\d+ points?\\))?\\.?$");
	private static final Pattern CLUE = Pattern.compile("^You have completed ([\\d,]+) (\\w+) Treasure Trails?\\.?$");
	private static final Pattern BEST = Pattern.compile("^(.*?)(?:duration|time)(?: was)?: ?([\\d:.]+) ?\\(new personal best\\)");
	private static final String[] PET_LINES = {
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed",
	};

	/** A kill count the game announced. */
	public static final class KillCount
	{
		private final String name;
		private final int count;

		KillCount(String name, int count)
		{
			this.name = name;
			this.count = count;
		}

		public String getName()
		{
			return name;
		}

		public int getCount()
		{
			return count;
		}
	}

	private ChatParser()
	{
	}

	public static KillCount killCount(String message)
	{
		Matcher m = KILL_COUNT.matcher(message);
		String name;
		String count;
		if (m.find())
		{
			name = m.group(1);
			count = m.group(2);
		}
		else if ((m = RIFTS.matcher(message)).find())
		{
			name = "Guardians of the Rift";
			count = m.group(1);
		}
		else if ((m = CONTRACTS.matcher(message)).find())
		{
			name = "Mahogany Homes";
			count = m.group(1);
		}
		else
		{
			return null;
		}
		try
		{
			return new KillCount(name, Integer.parseInt(count.replace(",", "")));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * A moment the message tells of, or null.
	 *
	 * @param recentBoss the boss whose kill count was just announced, to name a personal best
	 */
	public static Moment moment(String message, long now, String recentBoss)
	{
		for (String line : PET_LINES)
		{
			if (message.startsWith(line))
			{
				boolean duplicate = line.contains("would have been");
				return new Moment(Moment.Kind.PET, now, duplicate ? "A pet, again" : "A pet followed you home",
					duplicate ? "You already had this one" : "The rarest drop of all", 0, 0);
			}
		}
		Matcher m = COLLECTION.matcher(message);
		if (m.find())
		{
			return new Moment(Moment.Kind.COLLECTION, now, m.group(1), "New collection log slot", 0, 0);
		}
		m = QUEST.matcher(message);
		if (m.find())
		{
			return new Moment(Moment.Kind.QUEST, now, m.group(1), "Quest complete", 0, 0);
		}
		m = DIARY.matcher(message);
		if (m.find())
		{
			return new Moment(Moment.Kind.DIARY, now, m.group(2) + " " + m.group(1) + " diary", "Achievement diary complete", 0, 0);
		}
		m = TASK.matcher(message);
		if (m.find())
		{
			return new Moment(Moment.Kind.TASK, now, m.group(2), capital(m.group(1)) + " combat task", 0, 0);
		}
		m = CLUE.matcher(message);
		if (m.find())
		{
			return new Moment(Moment.Kind.CLUE, now, capital(m.group(2)) + " clue scroll", "Treasure Trail number " + m.group(1), 0, 0);
		}
		m = BEST.matcher(message);
		if (m.find())
		{
			String what = m.group(1).replaceAll("(?i)\\b(fight|challenge|total|completion|overall)\\b", "").trim();
			if (what.isEmpty())
			{
				what = recentBoss == null ? "Personal best" : recentBoss;
			}
			return new Moment(Moment.Kind.BEST, now, what + " in " + m.group(2), "New personal best", 0, 0);
		}
		return null;
	}

	private static String capital(String word)
	{
		return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
	}
}
