package com.codex.sessionchronicle.core;

/** Something worth remembering from a session: a level, a pet, a big drop, a new log slot. */
public class Moment
{
	/** What kind of moment it is; the order is how much it matters on a card. */
	public enum Kind
	{
		PET(120),
		MAX(100),
		COLLECTION(80),
		DROP(70),
		QUEST(60),
		DIARY(55),
		BEST(50),
		/** A personal record of the player's own, such as their fastest Mining session. */
		RECORD(45),
		TASK(40),
		/** The goal the player set for the session, reached. */
		GOAL(35),
		LEVEL(30),
		CLUE(20),
		DEATH(10);

		private final int weight;

		Kind(int weight)
		{
			this.weight = weight;
		}

		public int getWeight()
		{
			return weight;
		}
	}

	Kind kind;
	long at;
	String title;
	String detail;
	int itemId;
	long value;

	Moment()
	{
	}

	public Moment(Kind kind, long at, String title, String detail, int itemId, long value)
	{
		this.kind = kind;
		this.at = at;
		this.title = title;
		this.detail = detail;
		this.itemId = itemId;
		this.value = value;
	}

	public Kind getKind()
	{
		return kind == null ? Kind.LEVEL : kind;
	}

	public long getAt()
	{
		return at;
	}

	public String getTitle()
	{
		return title == null ? "" : title;
	}

	public String getDetail()
	{
		return detail;
	}

	public int getItemId()
	{
		return itemId;
	}

	public long getValue()
	{
		return value;
	}

	/** How much the moment matters when only a few fit: its kind, then a big drop's size. */
	double rank()
	{
		double bonus = value > 0 ? Math.min(19, Math.log10(value) * 3) : 0;
		return getKind().getWeight() + bonus;
	}
}
