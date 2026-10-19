package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.SlayerTask;
import com.codex.sessionchronicle.core.Summary;
import java.util.List;

/**
 * A session as a few lines of plain text, for a clan chat, a forum post or a message where a
 * picture will not do. Short enough to paste in one go: the headline and totals, then the best
 * three of each list.
 */
public final class Recap
{
	/** Lines kept from each list: skills, drops, kills, moments. */
	static final int TOP = 3;
	private static final int COINS = 995;

	private Recap()
	{
	}

	/**
	 * @param showName whether the player's name opens the recap, as on the card
	 */
	public static String text(Summary s, boolean showName)
	{
		StringBuilder out = new StringBuilder();
		String who = showName && s.getPlayer() != null && !s.getPlayer().isEmpty() ? s.getPlayer() + "'s session" : "Session";
		out.append(who).append(", ").append(Format.day(s.getStartedAt())).append(", ")
			.append(Format.duration(s.getPlayedMs())).append(" played").append('\n');
		out.append(s.headline()).append('\n');

		StringBuilder totals = new StringBuilder();
		if (s.getTotalXp() > 0)
		{
			part(totals, Format.xp(s.getTotalXp()) + " xp" + per(s.xpPerHour()));
		}
		if (s.getLootValue() > 0)
		{
			part(totals, Format.coins(s.getLootValue()) + " loot" + per(s.lootPerHour()));
		}
		if (s.getLevels() > 0)
		{
			part(totals, s.getLevels() + (s.getLevels() == 1 ? " level" : " levels"));
		}
		if (s.getKills() > 0)
		{
			part(totals, s.getKills() + (s.getKills() == 1 ? " kill" : " kills"));
		}
		if (s.getDeaths() > 0)
		{
			part(totals, s.getDeaths() + (s.getDeaths() == 1 ? " death" : " deaths"));
		}
		if (totals.length() > 0)
		{
			out.append(totals).append('\n');
		}

		List<Session.SkillLine> skills = s.getSkills();
		if (!skills.isEmpty())
		{
			StringBuilder line = new StringBuilder("Skills: ");
			for (int i = 0; i < skills.size() && i < TOP; i++)
			{
				Session.SkillLine k = skills.get(i);
				line.append(i > 0 ? ", " : "").append(k.getSkill()).append(" ").append(Format.xp(k.gained()));
				if (k.getLevel() > k.getStartLevel())
				{
					line.append(" (").append(k.getStartLevel()).append(" to ").append(k.getLevel()).append(')');
				}
			}
			out.append(line).append('\n');
		}

		List<Session.Drop> drops = s.getDrops();
		if (!drops.isEmpty() && drops.get(0).value() > 0)
		{
			StringBuilder line = new StringBuilder("Best loot: ");
			int shown = 0;
			for (Session.Drop d : drops)
			{
				if (shown == TOP || d.value() <= 0)
				{
					break;
				}
				line.append(shown++ > 0 ? ", " : "").append(d.getName())
					.append(d.getQuantity() > 1 && d.getId() != COINS ? " x" + Format.stack(d.getQuantity()) : "")
					.append(" (").append(Format.coins(d.value())).append(')');
			}
			out.append(line).append('\n');
		}

		List<Session.KillLine> kills = s.getKillLines();
		if (!kills.isEmpty())
		{
			StringBuilder line = new StringBuilder("Kills: ");
			for (int i = 0; i < kills.size() && i < TOP; i++)
			{
				Session.KillLine k = kills.get(i);
				line.append(i > 0 ? ", " : "").append(k.getName()).append(" x").append(k.count());
				if (k.getKillCount() > 0)
				{
					line.append(" (kc ").append(k.getKillCount()).append(')');
				}
			}
			out.append(line).append('\n');
		}

		SlayerTask task = s.getSlayer();
		if (task != null && task.getTotal() > 0)
		{
			out.append("Slayer: ").append(task.getName()).append(", ").append(task.getLeft()).append(" of ")
				.append(task.getTotal()).append(" left").append('\n');
		}

		List<Moment> highlights = s.getHighlights();
		if (!highlights.isEmpty())
		{
			StringBuilder line = new StringBuilder("Moments: ");
			for (int i = 0; i < highlights.size() && i < TOP; i++)
			{
				line.append(i > 0 ? ", " : "").append(highlights.get(i).getTitle());
			}
			out.append(line).append('\n');
		}
		out.append("(Codex Session Chronicle)");
		return out.toString();
	}

	private static void part(StringBuilder line, String text)
	{
		line.append(line.length() > 0 ? ", " : "").append(text);
	}

	private static String per(long perHour)
	{
		String rate = Format.rate(perHour);
		return rate.isEmpty() ? "" : " (" + rate + ")";
	}
}
