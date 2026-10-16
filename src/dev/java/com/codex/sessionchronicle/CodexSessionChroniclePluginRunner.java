package com.codex.sessionchronicle;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexSessionChroniclePluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexSessionChroniclePlugin.class);
		RuneLite.main(args);
	}
}
