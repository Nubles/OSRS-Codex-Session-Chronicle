package com.codex.sessionchronicle.ui;

/** What the Session and Journal tabs ask of the plugin; it answers on the client thread. */
public interface SessionActions
{
	/** Closes the session being lived and files it; a new one begins if the player is in the game. */
	void endSession();

	/** Takes the session that started at that moment out of the journal. */
	void removeSession(long startedAt);
}
