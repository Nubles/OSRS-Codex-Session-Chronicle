package com.codex.sessionchronicle;

import com.codex.sessionchronicle.core.ChatParser;
import com.codex.sessionchronicle.core.Chronicle;
import com.codex.sessionchronicle.core.Goal;
import com.codex.sessionchronicle.core.Journal;
import com.codex.sessionchronicle.core.LedgerWorth;
import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.SlayerWatch;
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
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
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
	/** Codex Ledger's saved book, read only for its net worth; see LedgerWorth. */
	static final String LEDGER_GROUP = "codexledger";
	static final String LEDGER_KEY = "ledger";
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
	/** The slayer task id that stands for "a boss", whose name is then looked up from the boss id. */
	private static final int BOSS_TASK = 98;
	/** The slayer task modifier that changes how many kills it is for. */
	private static final int SLAYER_MODIFIER_AMOUNT = 2;

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
	/** When the slayer task's varps are read, and whether a reading is the first since a login or hop. */
	private final SlayerWatch slayer = new SlayerWatch();
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
				return skillIconManager.getSkillImage(Skill.valueOf(name.toUpperCase(Locale.ROOT)), true);
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

			@Override
			public void copyText(String text)
			{
				copyRecap(text);
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

			@Override
			public void setGoal(Goal.Kind kind, long target)
			{
				clientThread.invoke(() -> goal(new Goal(kind, target, System.currentTimeMillis())));
			}

			@Override
			public void clearGoal()
			{
				clientThread.invoke(() -> goal(null));
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
		if (state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			// The client takes its varps afresh in the new world: the task may read nothing for a few ticks.
			slayer.arrived();
		}
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
		if (LEDGER_GROUP.equals(event.getGroup()))
		{
			// Codex Ledger saved its book: the session's worth may have moved.
			if (LEDGER_KEY.equals(event.getKey()) && event.getProfile() != null && event.getProfile().equals(profile))
			{
				clientThread.invokeLater(this::readWorth);
			}
			return;
		}
		// The plugin's own saves of the session and journal land here too, about once a minute;
		// only the two settings that change what is drawn are worth acting on.
		if (!CodexSessionChronicleConfig.GROUP.equals(event.getGroup()) || event.getProfile() != null
			|| (!"showTicker".equals(event.getKey()) && !"nameOnCard".equals(event.getKey())))
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
		slayer.tick();
		if (slayer.isDue())
		{
			readSlayer(now);
		}
		Moment reached = chronicle.checkGoal(now);
		if (reached != null)
		{
			dirty = true;
			saveSoon = true;
			if (config.goalChat())
			{
				announceGoal(reached);
			}
		}
		Session current = chronicle.current();
		ticker.show(view(current, now, true));
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
	 * back afterwards. A world flagged Last Man Standing is an ordinary world otherwise (RuneLite's
	 * world hopper treats it as one), so only the in-game varbit counts for LMS. Neither is progress, so stats are ignored while swapped, and once they are
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
		slayer.arrived();
		readWorth();
		saveCurrent();
		dirty = true;
		publish();
	}

	/**
	 * Reads Codex Ledger's net worth for the session, when Ledger is installed and has seen the bank.
	 * A paused session reads it too: Ledger saves as the player logs out, after this session has paused,
	 * and that last figure is the one the session should be filed with.
	 */
	private void readWorth()
	{
		if (chronicle.current() == null || profile == null)
		{
			return;
		}
		String json = configManager.getRSProfileConfiguration(LEDGER_GROUP, LEDGER_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			if (chronicle.worth(gson.fromJson(json, LedgerWorth.class)))
			{
				dirty = true;
				if (!chronicle.isLive())
				{
					// Paused, so no tick will save it: keep the logout figure should the client close now.
					saveCurrent();
				}
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read Codex Ledger's worth", e);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (isBank(event.getGroupId()) && chronicle.isLive() && chronicle.bank(true) != null)
		{
			dirty = true;
			saveSoon = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (isBank(event.getGroupId()) && chronicle.isLive())
		{
			chronicle.bank(false);
		}
	}

	private static boolean isBank(int groupId)
	{
		return groupId == InterfaceID.BANKMAIN || groupId == InterfaceID.BANK_DEPOSITBOX;
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

	/** Sets or clears the live session's goal; a goal already met is told at the next tick. */
	private void goal(Goal goal)
	{
		if (chronicle.current() == null)
		{
			return;
		}
		chronicle.goal(goal);
		saveCurrent();
		publish();
	}

	private void announceGoal(Moment reached)
	{
		String line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Session Chronicle: ")
			.append(ChatColorType.HIGHLIGHT).append(reached.getTitle())
			.append(ChatColorType.NORMAL).append(", " + reached.getDetail().toLowerCase(Locale.ROOT) + ".")
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(line)
			.build());
	}

	/** What the ticker draws: the clock, experience and loot, and the goal's share when one is set. */
	private static SessionTicker.View view(Session s, long now, boolean live)
	{
		Goal goal = s.getGoal();
		int share = goal == null ? -1 : goal.isReached() ? 100
			: (int) Math.min(99, Goal.progress(s, goal.getKind()) * 100 / Math.max(1, goal.getTarget()));
		return new SessionTicker.View(s.getPlayedMs(), now, live, s.totalXp(), s.lootValue(), share);
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
	public void onVarbitChanged(VarbitChanged event)
	{
		int varp = event.getVarpId();
		if (varp == VarPlayerID.SLAYER_COUNT || varp == VarPlayerID.SLAYER_TARGET || varp == VarPlayerID.SLAYER_COUNT_ORIGINAL
			|| event.getVarbitId() == VarbitID.SLAYER_TARGET_BOSSID)
		{
			slayer.moved();
		}
	}

	/** Reads the slayer task as the game's slayer helper shows it, and tells the session. */
	private void readSlayer(long now)
	{
		int left = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		Boolean first = slayer.take(left, chronicle.hasSlayerTask());
		if (first == null)
		{
			// Just arrived in a world: the task's varps may not be back yet, so the task followed is not dropped at once.
			return;
		}
		boolean fresh = first;
		String name = left > 0 ? slayerTaskName() : null;
		if (left > 0 && name == null)
		{
			// The task's name could not be read: leave the session's task as it was rather than end it.
			return;
		}
		int total = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		if (client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID) == SLAYER_MODIFIER_AMOUNT)
		{
			int change = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
			total += client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1 ? -change : change;
		}
		Moment done = chronicle.slayer(name, left, total, fresh, now);
		dirty = true;
		if (done != null)
		{
			saveSoon = true;
		}
	}

	/** The current task's name from the game's slayer tables, as RuneLite's Slayer plugin reads it; null when unknown. */
	private String slayerTaskName()
	{
		try
		{
			int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
			int row;
			if (taskId == BOSS_TASK)
			{
				List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
					DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
				if (rows.isEmpty())
				{
					return null;
				}
				row = (Integer) client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
			}
			else
			{
				List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
				if (rows.isEmpty())
				{
					return null;
				}
				row = rows.get(0);
			}
			return (String) client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read the slayer task", e);
			return null;
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		loot(npc == null ? null : npc.getName(), event.getItems(), 1, false);
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
		loot(event.getName(), event.getItems(), type == LootRecordType.EVENT ? Math.max(1, event.getAmount()) : 0, true);
	}

	private void loot(String source, Collection<ItemStack> items, int kills, boolean opened)
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
		List<Moment> moments = chronicle.loot(source == null ? null : Text.removeTags(source), drops, kills, opened, config.valuableDrop(),
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
					// Sessions filed before the activity was kept get it now, for Codex Ledger to read.
					if (Journal.label(list))
					{
						storeJournal();
					}
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
		Journal.markRecords(closed, journal);
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
			// Each filed session's pace is set beside the sessions before it, as it stood then.
			list.add(Summary.of(s, Summary.State.ENDED, journal));
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
			.append(ChatColorType.NORMAL).append(records(last))
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(line)
			.build());
	}

	/** ", and a new record: Mining record: 212k xp/hr." or a plain full stop. */
	private static String records(Session last)
	{
		for (Moment m : last.getMoments())
		{
			if (m.getKind() == Moment.Kind.RECORD)
			{
				return ", and a new personal record: " + m.getTitle() + ".";
			}
		}
		return ".";
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
			live = Summary.of(copy, chronicle.isLive() ? Summary.State.LIVE : Summary.State.PAUSED, journal);
			ticker.show(view(copy, System.currentTimeMillis(), chronicle.isLive()));
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

	private void copyRecap(String text)
	{
		String note;
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			note = "Copied as text. Paste it into a chat or a post.";
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
