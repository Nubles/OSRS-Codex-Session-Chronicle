package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Reading back what was saved in the profile when any of it may be missing, cut short, of the wrong
 * type or written by a later version. Nothing here throws: what cannot be read is left out, and only
 * that.
 */
public final class Saved
{
	/** More than any account is worth or any stack holds; a figure past it is damage. */
	static final long MAX_WORTH = 10_000_000_000_000L;

	private Saved()
	{
	}

	/** The saved text as JSON, or null when it is blank or not JSON. */
	static JsonElement tree(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, JsonElement.class);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** One saved object as a class, or null when it cannot be read as one. */
	static <T> T as(Gson gson, JsonElement e, Class<T> type)
	{
		if (e == null || !e.isJsonObject())
		{
			return null;
		}
		try
		{
			return gson.fromJson(e, type);
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	/**
	 * One saved object as a class, giving up only what cannot be read: a field of the wrong type is left
	 * at its default, and an entry of a list or map that cannot be read is left out on its own. Null
	 * when it is not an object at all.
	 */
	static <T> T mend(Gson gson, JsonElement e, Class<T> type)
	{
		T whole = as(gson, e, type);
		if (whole != null || e == null || !e.isJsonObject())
		{
			return whole;
		}
		JsonObject mended = new JsonObject();
		for (Map.Entry<String, JsonElement> field : e.getAsJsonObject().entrySet())
		{
			JsonElement kept = salvage(gson, type, field.getKey(), field.getValue());
			if (kept != null)
			{
				mended.add(field.getKey(), kept);
			}
		}
		return as(gson, mended, type);
	}

	private static boolean reads(Gson gson, Class<?> type, String key, JsonElement value)
	{
		JsonObject one = new JsonObject();
		one.add(key, value);
		return as(gson, one, type) != null;
	}

	private static JsonElement salvage(Gson gson, Class<?> type, String key, JsonElement value)
	{
		if (reads(gson, type, key, value))
		{
			return value;
		}
		if (value.isJsonArray())
		{
			JsonArray kept = new JsonArray();
			for (JsonElement entry : value.getAsJsonArray())
			{
				JsonArray one = new JsonArray();
				one.add(entry);
				if (reads(gson, type, key, one))
				{
					kept.add(entry);
				}
			}
			return reads(gson, type, key, kept) ? kept : null;
		}
		if (value.isJsonObject())
		{
			JsonObject kept = new JsonObject();
			for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet())
			{
				JsonObject one = new JsonObject();
				one.add(entry.getKey(), entry.getValue());
				if (reads(gson, type, key, one))
				{
					kept.add(entry.getKey(), entry.getValue());
				}
			}
			return reads(gson, type, key, kept) ? kept : null;
		}
		return null;
	}

	/** The longest damaged save set aside; one past it is only logged, so the profile never doubles in size for it. */
	static final int MAX_ASIDE = 1_000_000;

	/**
	 * Whether a save that could not be read whole should be set aside in place of the copy already
	 * there, which is kept to just one: yes when there is none, and otherwise only for a save at least
	 * as long, so a later stub (a journal begun again, then damaged) never replaces a fuller copy.
	 */
	public static boolean setAside(String kept, String damaged)
	{
		if (damaged == null || damaged.trim().isEmpty() || damaged.length() > MAX_ASIDE)
		{
			return false;
		}
		return kept == null || kept.isEmpty() || !kept.equals(damaged) && damaged.length() >= kept.length();
	}

	static long clamp(long n, long max)
	{
		return Math.max(0, Math.min(n, max));
	}
}
