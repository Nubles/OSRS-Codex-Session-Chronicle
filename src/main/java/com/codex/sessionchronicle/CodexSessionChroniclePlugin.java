package com.codex.sessionchronicle;

import com.codex.sessionchronicle.core.ChatParser;
import com.codex.sessionchronicle.core.Chronicle;
import com.codex.sessionchronicle.core.Journal;
import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import com.codex.sessionchronicle.overlay.SessionTicker;
import com.codex.sessionchronicle.ui.CardActions;
import com.codex.sessionchronicle.ui.ChroniclePanel;
import com.codex.sessionchronicle.ui.Format;
import com.codex.sessionchronicle.ui.Icons;
import com.codex.sessionchronicle.ui.SessionActions;
import com.codex.sessionchronicle.ui.ItemIcons;
import com.codex.sessionchronicle.ui.Sigil;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Codex Session Chronicle",
	description = "An automatic recap of every play session: experience, loot, kills and moments, saved to a journal and shareable as a card",
	tags = {"session", "recap", "summary", "journal", "xp", "loot", "kills", "share", "card", "codex"}
)
public class CodexSessionChroniclePlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CodexSessionChroniclePlugin.class);
	private static final String CURRENT_KEY = "current";
	private static final String JOURNAL_KEY = "journal";
	private static final Type JOURNAL_TYPE = new TypeToken<List<Session>>()
	{
	}.getType();
	/** Ticks between panel refreshes when only the clock and the experience moved. */
	private static final int REFRESH_TICKS = 5;
	/** Ticks to wait after logging in, so the stats the game sends at login are all in. */
	private static final int LOGIN_TICKS = 2;
	/** Ticks to keep ignoring stats after a minigame hands the player's own stats back. */
	private static final int GUARD_TICKS = 3;
	/** Ticks between saves of the session being lived, about a minute. */
	private static final int SAVE_TICKS = 100;
	/** How long a kill count stays fresh enough to name the personal best that follows it. */
	private static final long RECENT_KC_MS = 10_000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private CodexSessionChronicleConfig config;

	private final Chronicle chronicle = new Chronicle();
	private final SessionTicker ticker = new SessionTicker();

	private ChroniclePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> expiry;

	private String profile;
	private List<Session> journal = Collections.emptyList();
	private List<Summary> pastSummaries = Collections.emptyList();
	private boolean pendingBegin;
	private int loginTicks;
	private boolean quietBegin;
	private boolean dirty;
	private boolean saveSoon;
	private int guardTicks;
	private int ticks;
	/** The session being lived as last saved or shown, so it can be written as the client closes. */
	private volatile String snapshot;
	private volatile String snapshotProfile;
	private String recentKc;
	private long recentKcAt;

	@Provides
	CodexSessionChronicleConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexSessionChronicleConfig.class);
	}

	@Override
	protected void startUp()
	{
		ItemIcons items = (itemId, onLoaded) ->
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			image.onLoaded(onLoaded);
			return image;
		};
		Icons icons = name ->
		{
			try
			{
				return skillIconManager.getSkillImage(Skill.valueOf(name.toUpperCase()), true);
			}
			catch (IllegalArgumentException e)
			{
				return null;
			}
		};
		panel = new ChroniclePanel(items, icons, new CardActions()
		{
			@Override
			public void save(BufferedImage card, Summary session)
			{
				saveCard(card, session);
			}

			@Override
			public void copy(BufferedImage card)
			{
				copyCard(card);
			}
		}, new SessionActions()
		{
			@Override
			public void endSession()
			{
				clientThread.invoke(CodexSessionChroniclePlugin.this::endNow);
			}

			@Override
			public void removeSession(long startedAt)
			{
				clientThread.invoke(() -> forget(startedAt));
			}
		});
		panel.setShowName(config.nameOnCard());
		navButton = NavigationButton.builder()
			.tooltip("Codex Session Chronicle")
			.icon(Sigil.icon(16))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		ticker.setEnabled(config.showTicker());
		overlayManager.add(ticker);
		expiry = executor.scheduleWithFixedDelay(() -> clientThread.invoke(this::checkExpiry), 30, 30, TimeUnit.SECONDS);
		clientThread.invokeLater(() ->
		{
			loadProfile();
			pendingBegin = client.getGameState() == GameState.LOGGED_IN;
			loginTicks = LOGIN_TICKS;
			publish();
		});
	}

	@Override
	protected void shutDown()
	{
		if (expiry != null)
		{
			expiry.cancel(false);
			expiry = null;
		}
		overlayManager.remove(ticker);
		clientToolbar.removeNavigation(navButton);
		clientThread.invokeLater(() ->
		{
			chronicle.pause(System.currentTimeMillis());
			saveCurrent();
			chronicle.restore(null);
			profile = null;
			journal = Collections.emptyList();
			pastSummaries = Collections.emptyList();
			pendingBegin = false;
			snapshot = null;
		});
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			if (!chronicle.isLive() && !pendingBegin)
			{
				pendingBegin = true;
				loginTicks = 0;
			}
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			pendingBegin = false;
			if (chronicle.isLive())
			{
				chronicle.pause(System.currentTimeMillis());
				saveCurrent();
				dirty = true;
				publish();
			}
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		chronicle.pause(System.currentTimeMillis());
		saveCurrent();
		loadProfile();
		pendingBegin = client.getGameState() == GameState.LOGGED_IN;
		publish();
	}

	/** The client is closing: write the session as it last stood, since game ticks have stopped. */
	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		String json = snapshot;
		String key = snapshotProfile;
		if (json != null && key != null)
		{
			configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, key, CURRENT_KEY, json);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CodexSessionChronicleConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		ticker.setEnabled(config.showTicker());
		ChroniclePanel target = panel;
		if (target != null)
		{
			boolean showName = config.nameOnCard();
			SwingUtilities.invokeLater(() -> target.setShowName(showName));
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		long now = System.currentTimeMillis();
		if (pendingBegin && ++loginTicks > LOGIN_TICKS)
		{
			tryBegin(now);
		}
		if (!chronicle.isLive())
		{
			return;
		}
		guard();
		chronicle.tick(now);
		ticks++;
		Session current = chronicle.current();
		ticker.show(new SessionTicker.View(current.getPlayedMs(), now, true, current.totalXp(), current.lootValue()));
		if (saveSoon || ticks % SAVE_TICKS == 0)
		{
			saveCurrent();
		}
		if (dirty || ticks % REFRESH_TICKS == 0)
		{
			publish();
		}
	}

	/**
	 * Last Man Standing and the PvP Arena swap the player's stats for fixed ones and give them
	 * back afterwards. Neither is progress, so stats are ignored while swapped, and once they are
	 * back the session picks up from whatever the game now says.
	 */
	private void guard()
	{
		if (statsSwapped())
		{
			guardTicks = GUARD_TICKS;
			return;
		}
		if (guardTicks > 0 && --guardTicks == 0)
		{
			for (Skill skill : Skill.values())
			{
				chronicle.resume(skill.getName(), client.getSkillExperience(skill), client.getRealSkillLevel(skill));
			}
			dirty = true;
		}
	}

	private boolean statsSwapped()
	{
		return client.getVarbitValue(VarbitID.BR_INGAME) != 0
			|| client.getWorldType().contains(WorldType.LAST_MAN_STANDING)
			|| client.getWorldType().contains(WorldType.PVP_ARENA);
	}

	/** Starts or picks up the session once the account and its stats are readable. */
	private void tryBegin(long now)
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null || client.getOverallExperience() <= 0 || statsSwapped())
		{
			return;
		}
		if (profile == null || !profile.equals(configManager.getRSProfileKey()))
		{
			loadProfile();
			if (profile == null)
			{
				return;
			}
		}
		pendingBegin = false;
		Session closed = chronicle.begin(now, player.getName(), config.breakMinutes() * 60_000L);
		if (closed != null)
		{
			file(closed);
		}
		boolean fresh = chronicle.current().getStartedAt() == now;
		for (Skill skill : Skill.values())
		{
			if (fresh)
			{
				chronicle.xp(skill.getName(), client.getSkillExperience(skill), client.getRealSkillLevel(skill), now);
			}
			else
			{
				chronicle.resume(skill.getName(), client.getSkillExperience(skill), client.getRealSkillLevel(skill));
			}
		}
		if (fresh && !quietBegin && config.chatRecap() && !journal.isEmpty())
		{
			recap(journal.get(0));
		}
		quietBegin = false;
		saveCurrent();
		dirty = true;
		publish();
	}

	/** The player asked to close the session now; a new one begins at once if they are in the game. */
	private void endNow()
	{
		if (chronicle.current() == null)
		{
			return;
		}
		chronicle.pause(System.currentTimeMillis());
		Session closed = chronicle.close();
		if (closed != null)
		{
			file(closed);
		}
		saveCurrent();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			pendingBegin = true;
			loginTicks = LOGIN_TICKS;
			quietBegin = true;
		}
		publish();
	}

	/** Takes one session out of the journal. */
	private void forget(long startedAt)
	{
		journal = Journal.remove(journal, startedAt);
		summarisePast();
		storeJournal();
		publish();
	}

	private void checkExpiry()
	{
		Session closed = chronicle.expire(System.currentTimeMillis(), config.breakMinutes() * 60_000L);
		if (closed != null)
		{
			file(closed);
			saveCurrent();
			publish();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!chronicle.isLive() || guardTicks > 0 || statsSwapped())
		{
			return;
		}
		List<Moment> moments = chronicle.xp(event.getSkill().getName(), event.getXp(), event.getLevel(), System.currentTimeMillis());
		if (!moments.isEmpty())
		{
			// A level is worth showing and keeping straight away; plain experience waits for the next refresh.
			dirty = true;
			saveSoon = true;
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		loot(npc == null ? null : npc.getName(), event.getItems(), 1);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// Kills arrive through NpcLootReceived on their own, and player kills are left out.
		LootRecordType type = event.getType();
		if (type == LootRecordType.NPC || type == LootRecordType.PLAYER)
		{
			return;
		}
		loot(event.getName(), event.getItems(), type == LootRecordType.EVENT ? Math.max(1, event.getAmount()) : 0);
	}

	private void loot(String source, Collection<ItemStack> items, int kills)
	{
		if (!chronicle.isLive())
		{
			return;
		}
		List<Session.Drop> drops = new ArrayList<>();
		for (ItemStack stack : items)
		{
			int id = itemManager.canonicalize(stack.getId());
			ItemComposition item = itemManager.getItemComposition(id);
			drops.add(new Session.Drop(id, item.getName(), stack.getQuantity(), itemManager.getItemPrice(id)));
		}
		List<Moment> moments = chronicle.loot(source == null ? null : Text.removeTags(source), drops, kills, config.valuableDrop(),
			System.currentTimeMillis());
		dirty = true;
		saveSoon |= !moments.isEmpty();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!chronicle.isLive() || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}
		String message = Text.removeTags(event.getMessage()).replace(' ', ' ').trim();
		long now = System.currentTimeMillis();
		ChatParser.KillCount kc = ChatParser.killCount(message);
		if (kc != null)
		{
			chronicle.killCount(kc.getName(), kc.getCount());
			recentKc = kc.getName();
			recentKcAt = now;
			dirty = true;
			return;
		}
		Moment moment = ChatParser.moment(message, now, now - recentKcAt <= RECENT_KC_MS ? recentKc : null);
		if (moment != null)
		{
			chronicle.moment(moment);
			dirty = true;
			saveSoon = true;
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player player = client.getLocalPlayer();
		if (chronicle.isLive() && player != null && event.getActor() == player && !statsSwapped())
		{
			Actor foe = player.getInteracting();
			String killer = foe instanceof NPC && foe.getName() != null ? Text.removeTags(foe.getName()) : null;
			chronicle.death(System.currentTimeMillis(), killer);
			dirty = true;
			saveSoon = true;
		}
	}

	private void loadProfile()
	{
		profile = configManager.getRSProfileKey();
		journal = Collections.emptyList();
		pastSummaries = Collections.emptyList();
		chronicle.restore(null);
		if (profile == null)
		{
			return;
		}
		try
		{
			String saved = configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, JOURNAL_KEY);
			if (saved != null && !saved.isEmpty())
			{
				List<Session> list = gson.fromJson(saved, JOURNAL_TYPE);
				if (list != null)
				{
					list.removeIf(s -> s == null);
					journal = list;
				}
			}
			String current = configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, CURRENT_KEY);
			if (current != null && !current.isEmpty())
			{
				chronicle.restore(gson.fromJson(current, Session.class));
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Could not read the saved chronicle, starting it afresh", e);
		}
		summarisePast();
		// A session left open when the client closed may have ended long ago.
		Session closed = chronicle.expire(System.currentTimeMillis(), config.breakMinutes() * 60_000L);
		if (closed != null)
		{
			file(closed);
			saveCurrent();
		}
	}

	private void file(Session closed)
	{
		journal = Journal.file(journal, closed, config.journalSize());
		summarisePast();
		storeJournal();
	}

	private void storeJournal()
	{
		if (profile != null)
		{
			configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, profile, JOURNAL_KEY, gson.toJson(journal, JOURNAL_TYPE));
		}
	}

	private void summarisePast()
	{
		List<Summary> list = new ArrayList<>();
		for (Session s : journal)
		{
			list.add(Summary.of(s, Summary.State.ENDED));
		}
		pastSummaries = Collections.unmodifiableList(list);
	}

	private void saveCurrent()
	{
		saveSoon = false;
		Session current = chronicle.current();
		snapshot = current == null ? null : gson.toJson(current);
		snapshotProfile = profile;
		if (profile == null)
		{
			return;
		}
		if (current == null)
		{
			configManager.unsetConfiguration(CodexSessionChronicleConfig.GROUP, profile, CURRENT_KEY);
		}
		else
		{
			configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, profile, CURRENT_KEY, snapshot);
		}
	}

	private void recap(Session last)
	{
		String line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Session Chronicle: ")
			.append(ChatColorType.NORMAL).append("a new session begins. Last time, " + Format.duration(last.getPlayedMs()) + " played, ")
			.append(ChatColorType.HIGHLIGHT).append(Format.xp(last.totalXp()) + " xp")
			.append(ChatColorType.NORMAL).append(" and ")
			.append(ChatColorType.HIGHLIGHT).append(Format.coins(last.lootValue()) + " loot")
			.append(ChatColorType.NORMAL).append(".")
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(line)
			.build());
	}

	/** Sends the panel a copy of the chronicle, so Swing never reads what the client thread is writing. */
	private void publish()
	{
		dirty = false;
		Session current = chronicle.current();
		Summary live = null;
		Session copy = null;
		if (current != null)
		{
			String json = gson.toJson(current);
			snapshot = json;
			snapshotProfile = profile;
			copy = gson.fromJson(json, Session.class);
			live = Summary.of(copy, chronicle.isLive() ? Summary.State.LIVE : Summary.State.PAUSED);
			ticker.show(new SessionTicker.View(copy.getPlayedMs(), System.currentTimeMillis(), chronicle.isLive(), copy.totalXp(), copy.lootValue()));
		}
		else
		{
			ticker.show(null);
		}
		Journal.Records records = Journal.records(journal, copy, System.currentTimeMillis(), ZoneId.systemDefault());
		Player player = client.getLocalPlayer();
		String name = client.getGameState() == GameState.LOGGED_IN && player != null ? player.getName() : null;
		List<Summary> past = pastSummaries;
		ChroniclePanel target = panel;
		if (target != null)
		{
			Summary shown = live;
			SwingUtilities.invokeLater(() -> target.update(shown, past, records, name));
		}
	}

	/** Asks where to save the card with RuneLite's own file chooser, then writes it there; runs on the Swing thread. */
	private void saveCard(BufferedImage card, Summary session)
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save session card")
			.addExtensionFilter("PNG image", "png")
			.setDefaultExtension("png")
			.setFileName("Session " + Format.file(session.getStartedAt()) + ".png")
			.showDialog(panel);
		if (chosen == null || chosen.isEmpty())
		{
			return;
		}
		Filepath target = chosen.get(0);
		executor.execute(() ->
		{
			String note;
			try (OutputStream out = target.openOutputStream())
			{
				ImageIO.write(card, "png", out);
				note = "Saved as " + target.getFileName() + ".";
			}
			catch (IOException e)
			{
				log.warn("Could not save the session card", e);
				note = "The card could not be saved: " + e.getMessage();
			}
			String shown = note;
			ChroniclePanel view = panel;
			if (view != null)
			{
				SwingUtilities.invokeLater(() -> view.cardNote(shown));
			}
		});
	}

	private void copyCard(BufferedImage card)
	{
		String note;
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransfer(ImageTransfer.opaque(card)), null);
			note = "Copied. Paste it anywhere you share pictures.";
		}
		catch (IllegalStateException e)
		{
			note = "The clipboard is busy, try again in a moment.";
		}
		ChroniclePanel target = panel;
		if (target != null)
		{
			target.cardNote(note);
		}
	}
}
