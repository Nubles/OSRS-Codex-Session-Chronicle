package com.codex.sessionchronicle;

import com.codex.sessionchronicle.core.AgilityCoach;
import com.codex.sessionchronicle.core.AgilityLog;
import com.codex.sessionchronicle.core.Bench;
import com.codex.sessionchronicle.core.BenchLog;
import com.codex.sessionchronicle.core.Bosses;
import com.codex.sessionchronicle.core.ChatParser;
import com.codex.sessionchronicle.core.DiveWatch;
import com.codex.sessionchronicle.core.Chronicle;
import com.codex.sessionchronicle.core.Goal;
import com.codex.sessionchronicle.core.Graceful;
import com.codex.sessionchronicle.core.Journal;
import com.codex.sessionchronicle.core.LapClock;
import com.codex.sessionchronicle.core.LedgerWorth;
import com.codex.sessionchronicle.core.Midnight;
import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Rooftop;
import com.codex.sessionchronicle.core.Saved;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.SlayerWatch;
import com.codex.sessionchronicle.core.SupplyWatch;
import com.codex.sessionchronicle.core.Summary;
import com.codex.sessionchronicle.core.Underwater;
import com.codex.sessionchronicle.core.UnderwaterCoach;
import com.codex.sessionchronicle.core.UnderwaterLog;
import com.codex.sessionchronicle.core.Week;
import com.codex.sessionchronicle.overlay.BenchPlaque;
import com.codex.sessionchronicle.overlay.DivePlaque;
import com.codex.sessionchronicle.overlay.GhostPlaque;
import com.codex.sessionchronicle.overlay.LapPlaque;
import com.codex.sessionchronicle.overlay.SessionTicker;
import com.codex.sessionchronicle.ui.CardActions;
import com.codex.sessionchronicle.ui.ChroniclePanel;
import com.codex.sessionchronicle.ui.Format;
import com.codex.sessionchronicle.ui.IconWatch;
import com.codex.sessionchronicle.ui.Icons;
import com.codex.sessionchronicle.ui.SessionActions;
import com.codex.sessionchronicle.ui.ItemIcons;
import com.codex.sessionchronicle.ui.Sigil;
import com.google.gson.Gson;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.EnumComposition;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
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
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.EnumID;
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
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
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
	/** The rooftop coach's log of laps, marks and Graceful, per account; see AgilityLog. */
	static final String AGILITY_KEY = "agility";
	/** The account's make-X actions, their pace and the bank's supplies for them; see BenchLog. */
	static final String BENCH_KEY = "bench";
	/** The underwater log of dives, tears and hand-ins, per account; see UnderwaterLog. */
	static final String UNDERWATER_KEY = "underwater";
	/** Skills whose experience comes from fighting: an item used up beside it (a potion drunk) is no make-X action. */
	private static final Set<Skill> FIGHTING = java.util.EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED,
		Skill.MAGIC, Skill.HITPOINTS, Skill.PRAYER, Skill.SLAYER);
	/** Ticks between saves of the bench log while a batch runs, about half a minute. */
	private static final int BENCH_SAVE_TICKS = 50;
	/** Where a journal that could not be read at all is set aside, untouched, before it starts afresh. */
	static final String DAMAGED_KEY = "journalUnreadable";
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
	private SpriteManager spriteManager;

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
	/** The optional plaque racing the session against the best earlier one of the same activity. */
	private final GhostPlaque ghostPlaque = new GhostPlaque();
	/** The optional plaque with the rooftop lap under way against the best lap. */
	private final LapPlaque lapPlaque = new LapPlaque();
	/** Rooftop laps timed in ticks, obstacle by obstacle. */
	private final LapClock lapClock = new LapClock();
	/** The account's rooftop laps, marks of grace and Graceful pieces. */
	private AgilityLog agilityLog = new AgilityLog();
	/** A lap count message waiting for the end of its tick, so the last obstacle's experience is in the lap. */
	private Rooftop pendingLap;
	private int pendingLapTick;
	/** Agility experience last read, or -1 until read afresh after logging in or hopping. */
	private int agilityXp = -1;
	/** Marks of grace carried last read, or -1 until read afresh. */
	private int carriedMarks = -1;
	/** Whether the coach has something new for the panel, and whether its log needs saving. */
	private boolean agilityDirty;
	private boolean agilitySave;

	/** The optional plaque with the make-X batch under way. */
	private final BenchPlaque benchPlaque = new BenchPlaque();
	/** Make-X actions read from the pack tick by tick. */
	private final Bench bench = new Bench();
	/** The account's make-X recipes, their pace and the bank's supplies for them. */
	private BenchLog benchLog = new BenchLog();
	private boolean benchSave;
	private int benchSaveTick;
	/** The batch as last sent to the panel, by its key, so an unchanged one is not sent again. */
	private String benchSent;
	/** Each skill's experience last read, for which skilling skill gained on a tick. */
	private final Map<Skill, Integer> skillXp = new java.util.EnumMap<>(Skill.class);
	/** The skilling skill that gained experience last, and on which tick. */
	private String xpSkill;
	private int xpTick = -10;
	/** The tick food or a potion was last eaten or drunk: the pack's change then is no make-X action. */
	private int ateTick = -10;

	/** The optional plaque with the dive under way. */
	private final DivePlaque divePlaque = new DivePlaque();
	/** Dives at Fossil Island's Underwater Agility and Thieving, read tick by tick. */
	private final DiveWatch diveWatch = new DiveWatch();
	/** The account's dives, hand-ins and banked tears. */
	private UnderwaterLog underwaterLog = new UnderwaterLog();
	private boolean underwaterDirty;
	private boolean underwaterSave;

	/** Hands each item picture's load callback over only until it arrives. */
	private final IconWatch iconWatch = new IconWatch();
	private ChroniclePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> expiry;
	/** Wakes the panel at local midnight, so Today and the week roll over even while logged out. */
	private ScheduledFuture<?> midnight;
	/**
	 * Counts starts and stops. A midnight wake-up belongs to the start that set it and does nothing,
	 * not even set the next one, once the plugin has stopped or started again, so a wake-up already
	 * running at a stop cannot leave a second chain of them behind.
	 */
	private final java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
	/** The local day the panel was last given, as an epoch day, so a missed midnight is caught too. */
	private long publishedDay = Long.MIN_VALUE;
	/** Codex Ledger's saved worth as last read, for the week card's worth line; null without Ledger. */
	private LedgerWorth ledgerBook;

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
	/** Giants' Foundry reputation last read, or -1 until it is read afresh after logging in or hopping. */
	private int foundryPoints = -1;
	/** The game's count of Sailing port tasks completed last read, or -1 until it is read afresh after logging in or hopping. */
	private int portTasks = -1;
	/** Food, potions, runes and ammunition used, read from what is carried tick by tick. */
	private final SupplyWatch supplies = new SupplyWatch();
	/** Banks, shops, trades and the exchange open now: items leave through them without being used. */
	private final Set<Integer> busy = new HashSet<>();
	/** Items seen in the ammunition or weapon slot as a stack: fired or thrown when they go down. */
	private final Set<Integer> ammo = new HashSet<>();
	/** Whether an item is a rune or cannonball, by id, so names are looked up once. */
	private final Map<Integer, Boolean> spendable = new HashMap<>();
	private static final int[][] POUCH = {
		{VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
		{VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
		{VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
		{VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4},
		{VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_QUANTITY_5},
		{VarbitID.RUNE_POUCH_TYPE_6, VarbitID.RUNE_POUCH_QUANTITY_6},
	};
	private static final Set<Integer> BUSY = new HashSet<>(java.util.Arrays.asList(InterfaceID.BANKMAIN, InterfaceID.BANK_DEPOSITBOX,
		InterfaceID.SHOPMAIN, InterfaceID.TRADEMAIN, InterfaceID.TRADECONFIRM, InterfaceID.GE_OFFERS, InterfaceID.SEED_VAULT,
		InterfaceID.SEED_VAULT_DEPOSIT));
	/** Menu options that send items away unused: anything that leaves straight after them is not spent. */
	private static final Set<String> AWAY = new HashSet<>(java.util.Arrays.asList("drop", "destroy", "deposit", "deposit-all", "store",
		"empty", "fill", "give", "release", "use", "bank", "trade with", "offer", "sell"));

	@Provides
	CodexSessionChronicleConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexSessionChronicleConfig.class);
	}

	/**
	 * Each boss on the hiscores by {@link Bosses#key}, for its icon, so "Barrows" finds "Barrows
	 * Chests" and "The Nightmare" finds "Nightmare". Where two share a key (a raid and its hard mode)
	 * the plain one, listed first, keeps it.
	 */
	private static final Map<String, HiscoreSkill> BOSSES = new HashMap<>();

	static
	{
		for (HiscoreSkill s : HiscoreSkill.values())
		{
			if (s.getType() == HiscoreSkillType.BOSS && s.getSpriteId() >= 0)
			{
				BOSSES.putIfAbsent(Bosses.key(s.getName()), s);
			}
		}
	}

	/** Boss icons once loaded, shrunk to the size of a small skill icon. */
	private final Map<String, BufferedImage> bossIcons = new ConcurrentHashMap<>();
	/** Who waits for each boss icon still loading; one entry per row or card, however often they paint. */
	private final Map<String, Set<Runnable>> bossWaiting = new HashMap<>();

	/** A boss's hiscores icon; null for a name that is no boss and, the first time, while it loads. */
	private BufferedImage bossIcon(String name, Runnable onLoaded)
	{
		if (name == null)
		{
			return null;
		}
		String key = Bosses.key(name);
		HiscoreSkill boss = BOSSES.get(key);
		if (boss == null)
		{
			return null;
		}
		BufferedImage icon = bossIcons.get(key);
		if (icon != null)
		{
			return icon;
		}
		boolean ask;
		synchronized (bossWaiting)
		{
			Set<Runnable> waiting = bossWaiting.get(key);
			ask = waiting == null;
			if (ask)
			{
				waiting = new HashSet<>();
				bossWaiting.put(key, waiting);
			}
			if (onLoaded != null)
			{
				waiting.add(onLoaded);
			}
		}
		if (ask)
		{
			spriteManager.getSpriteAsync(boss.getSpriteId(), 0, sprite ->
			{
				// Boss icons are 25 by 25 at most, not all square: set on a square canvas first, as the
				// hiscores panel does, so none is squashed when shrunk to a small skill icon's size.
				bossIcons.put(key, ImageUtil.resizeImage(ImageUtil.resizeCanvas(sprite, 25, 25), 16, 16));
				Set<Runnable> waiting;
				synchronized (bossWaiting)
				{
					waiting = bossWaiting.remove(key);
				}
				if (waiting != null)
				{
					waiting.forEach(Runnable::run);
				}
			});
		}
		return null;
	}

	@Override
	protected void startUp()
	{
		ItemIcons items = (itemId, onLoaded) ->
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			// Only until it arrives: a loaded picture would run the callback again on every paint.
			iconWatch.watch(image, onLoaded, image::onLoaded);
			return image;
		};
		Icons icons = new Icons()
		{
			@Override
			public BufferedImage skill(String name)
			{
				try
				{
					return skillIconManager.getSkillImage(Skill.valueOf(name.toUpperCase(Locale.ROOT)), true);
				}
				catch (IllegalArgumentException e)
				{
					return null;
				}
			}

			@Override
			public BufferedImage boss(String name, Runnable onLoaded)
			{
				return bossIcon(name, onLoaded);
			}
		};
		panel = new ChroniclePanel(items, icons, new CardActions()
		{
			@Override
			public void save(BufferedImage card, String fileName)
			{
				saveCard(card, fileName);
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
		panel.setShowWorth(config.worthOnCard());
		navButton = NavigationButton.builder()
			.tooltip("Codex Session Chronicle")
			.icon(Sigil.icon(16))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		ticker.setEnabled(config.showTicker());
		overlayManager.add(ticker);
		ghostPlaque.setEnabled(config.ghostPace() && config.ghostPlaque());
		overlayManager.add(ghostPlaque);
		lapPlaque.setEnabled(config.agilityCoach() && config.lapPlaque());
		overlayManager.add(lapPlaque);
		benchPlaque.setEnabled(config.benchProgress() && config.benchPlaque());
		overlayManager.add(benchPlaque);
		divePlaque.setEnabled(config.underwaterLog() && config.divePlaque());
		overlayManager.add(divePlaque);
		expiry = executor.scheduleWithFixedDelay(() -> clientThread.invoke(this::checkExpiry), 30, 30, TimeUnit.SECONDS);
		scheduleMidnight(runs.incrementAndGet());
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
		runs.incrementAndGet();
		if (expiry != null)
		{
			expiry.cancel(false);
			expiry = null;
		}
		ScheduledFuture<?> wake = midnight;
		if (wake != null)
		{
			wake.cancel(false);
			midnight = null;
		}
		overlayManager.remove(ticker);
		overlayManager.remove(ghostPlaque);
		overlayManager.remove(lapPlaque);
		overlayManager.remove(benchPlaque);
		overlayManager.remove(divePlaque);
		clientToolbar.removeNavigation(navButton);
		clientThread.invokeLater(() ->
		{
			chronicle.pause(System.currentTimeMillis());
			// The dive under way is filed while the account's profile is still known.
			endDive(System.currentTimeMillis());
			saveCurrent();
			chronicle.restore(null);
			profile = null;
			journal = Collections.emptyList();
			pastSummaries = Collections.emptyList();
			storeAgility();
			agilityLog = new AgilityLog();
			resetAgility();
			storeBench();
			benchLog = new BenchLog();
			resetBench();
			underwaterLog = new UnderwaterLog();
			pendingBegin = false;
			snapshot = null;
			ledgerBook = null;
			publishedDay = Long.MIN_VALUE;
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
			foundryPoints = -1;
			portTasks = -1;
			supplies.reset();
			resetAgility();
			resetBench();
			endDive(System.currentTimeMillis());
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
			foundryPoints = -1;
			portTasks = -1;
			supplies.reset();
			resetAgility();
			storeBench();
			resetBench();
			endDive(System.currentTimeMillis());
			busy.clear();
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
		storeAgility();
		resetAgility();
		storeBench();
		resetBench();
		endDive(System.currentTimeMillis());
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
		// only the settings that change what is drawn are worth acting on.
		if (!CodexSessionChronicleConfig.GROUP.equals(event.getGroup()) || event.getProfile() != null
			|| (!"showTicker".equals(event.getKey()) && !"nameOnCard".equals(event.getKey()) && !"worthOnCard".equals(event.getKey())
			&& !"ghostPace".equals(event.getKey()) && !"ghostPlaque".equals(event.getKey())
			&& !"agilityCoach".equals(event.getKey()) && !"lapPlaque".equals(event.getKey())
			&& !"benchProgress".equals(event.getKey()) && !"benchPlaque".equals(event.getKey())
			&& !"underwaterLog".equals(event.getKey()) && !"underwaterGoal".equals(event.getKey()) && !"divePlaque".equals(event.getKey())))
		{
			return;
		}
		if ("underwaterLog".equals(event.getKey()) || "underwaterGoal".equals(event.getKey()) || "divePlaque".equals(event.getKey()))
		{
			divePlaque.setEnabled(config.underwaterLog() && config.divePlaque());
			clientThread.invokeLater(() ->
			{
				if (!config.underwaterLog())
				{
					endDive(System.currentTimeMillis());
					divePlaque.show(null);
				}
				publishUnderwater();
			});
			return;
		}
		if ("benchProgress".equals(event.getKey()) || "benchPlaque".equals(event.getKey()))
		{
			benchPlaque.setEnabled(config.benchProgress() && config.benchPlaque());
			clientThread.invokeLater(() ->
			{
				if (!config.benchProgress())
				{
					resetBench();
				}
			});
			return;
		}
		if ("agilityCoach".equals(event.getKey()) || "lapPlaque".equals(event.getKey()))
		{
			lapPlaque.setEnabled(config.agilityCoach() && config.lapPlaque());
			clientThread.invokeLater(() ->
			{
				if (!config.agilityCoach())
				{
					lapClock.reset();
					pendingLap = null;
				}
				publishAgility();
			});
			return;
		}
		if ("ghostPace".equals(event.getKey()) || "ghostPlaque".equals(event.getKey()))
		{
			ghostPlaque.setEnabled(config.ghostPace() && config.ghostPlaque());
			// The Session tab draws the race, or stops drawing it, at once.
			clientThread.invokeLater(this::publish);
			return;
		}
		ticker.setEnabled(config.showTicker());
		ChroniclePanel target = panel;
		if (target != null)
		{
			boolean showName = config.nameOnCard();
			boolean showWorth = config.worthOnCard();
			SwingUtilities.invokeLater(() ->
			{
				target.setShowName(showName);
				target.setShowWorth(showWorth);
			});
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
		tickAgility(now);
		tickBench(now);
		tickUnderwater(now);
		if (!chronicle.isLive())
		{
			return;
		}
		guard();
		chronicle.tick(now);
		ticks++;
		readFoundry();
		readPortTasks();
		readSupplies();
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
		LedgerWorth book = readLedgerBook();
		if (book == null)
		{
			return;
		}
		try
		{
			if (chronicle.current() != null && chronicle.worth(book))
			{
				dirty = true;
				if (!chronicle.isLive())
				{
					// Paused, so no tick will save it: keep the logout figure should the client close now.
					saveCurrent();
				}
			}
			if (!chronicle.isLive())
			{
				// Logged out, so no tick will show it: the week card's worth line has moved.
				publish();
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read Codex Ledger's worth", e);
		}
	}

	/** Reads Codex Ledger's saved book for this profile and keeps it for the week card; null without Ledger. */
	private LedgerWorth readLedgerBook()
	{
		ledgerBook = null;
		if (profile == null)
		{
			return null;
		}
		ledgerBook = LedgerWorth.read(gson, configManager.getRSProfileConfiguration(LEDGER_GROUP, LEDGER_KEY));
		return ledgerBook;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (BUSY.contains(event.getGroupId()))
		{
			busy.add(event.getGroupId());
		}
		if (isBank(event.getGroupId()) && chronicle.isLive() && chronicle.bank(true) != null)
		{
			dirty = true;
			saveSoon = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (busy.remove(event.getGroupId()))
		{
			// The bank's last items moved this tick; read afresh rather than count them as used.
			supplies.reset();
			bench.forget();
		}
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

	/**
	 * Sets the next wake-up for just after local midnight. The delay is worked out afresh each day from the
	 * zone's own calendar, so a day of 23 or 25 hours at a clock change still wakes at midnight.
	 */
	private synchronized void scheduleMidnight(int run)
	{
		if (runs.get() != run)
		{
			return;
		}
		long delay = Midnight.untilNext(System.currentTimeMillis(), ZoneId.systemDefault()) + Midnight.SLACK_MS;
		midnight = executor.schedule(() ->
		{
			if (runs.get() == run)
			{
				clientThread.invoke(this::publish);
				scheduleMidnight(run);
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	private void checkExpiry()
	{
		// A computer asleep through midnight wakes its timer late; the day is checked here too.
		if (publishedDay != Long.MIN_VALUE && Midnight.day(System.currentTimeMillis(), ZoneId.systemDefault()) != publishedDay)
		{
			publish();
		}
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
		if (event.getSkill() == Skill.AGILITY)
		{
			agilityXp(event.getXp());
		}
		Integer was = skillXp.put(event.getSkill(), event.getXp());
		if (was != null && event.getXp() > was && !FIGHTING.contains(event.getSkill()))
		{
			xpSkill = event.getSkill().getName();
			xpTick = client.getTickCount();
		}
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

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption() == null ? "" : Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
		if (option.equals("eat") || option.equals("drink"))
		{
			ateTick = client.getTickCount();
			supplies.ate(client.getTickCount());
		}
		else if (AWAY.contains(option))
		{
			supplies.quiet(client.getTickCount());
		}
		if (config.underwaterLog() && isObjectAction(event.getMenuAction()))
		{
			// Only which kind of object was searched is kept, to name the next search a clam or a chest.
			diveWatch.clicked(Underwater.Spot.of(event.getId()), client.getTickCount());
		}
	}

	private static boolean isObjectAction(MenuAction action)
	{
		return action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	/** What is carried (inventory, worn items, rune pouch), priced against the last tick's. */
	private void readSupplies()
	{
		Map<Integer, Integer> carried = new HashMap<>();
		add(carried, client.getItemContainer(InventoryID.INV));
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		add(carried, worn);
		if (worn != null)
		{
			for (EquipmentInventorySlot slot : new EquipmentInventorySlot[]{EquipmentInventorySlot.AMMO, EquipmentInventorySlot.WEAPON})
			{
				Item item = worn.getItem(slot.getSlotIdx());
				if (item != null && item.getId() > 0 && item.getQuantity() > 1)
				{
					ammo.add(item.getId());
				}
			}
		}
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int[] pouch : POUCH)
		{
			int type = client.getVarbitValue(pouch[0]);
			int quantity = client.getVarbitValue(pouch[1]);
			int id = type > 0 && runes != null ? runes.getIntValue(type) : -1;
			if (id > 0 && quantity > 0)
			{
				carried.merge(id, quantity, Integer::sum);
			}
		}
		long used = supplies.read(client.getTickCount(), carried, !busy.isEmpty(), this::spendable,
			id -> itemManager.getItemPrice(itemManager.canonicalize(id)));
		if (used > 0)
		{
			chronicle.used(used);
			dirty = true;
		}
	}

	private static void add(Map<Integer, Integer> carried, ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0 && item.getQuantity() > 0)
			{
				carried.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	/** Runes, cannonballs and fired or thrown ammunition are used up whenever they go down. */
	private boolean spendable(int id)
	{
		if (ammo.contains(id))
		{
			return true;
		}
		return spendable.computeIfAbsent(id, i ->
		{
			String name = itemManager.getItemComposition(i).getName().toLowerCase(Locale.ROOT);
			return name.endsWith(" rune") || name.contains("cannonball");
		});
	}

	/**
	 * The Giants' Foundry says nothing in chat when a sword is handed in, but its reputation only
	 * ever rises then (buying from the shop spends it), so each rise is one sword.
	 */
	private void readFoundry()
	{
		int points = client.getVarpValue(VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS);
		if (foundryPoints >= 0 && points > foundryPoints)
		{
			chronicle.round(Chronicle.GIANTS_FOUNDRY);
			dirty = true;
		}
		foundryPoints = points;
	}

	/**
	 * Sailing port tasks: the game keeps a count of those completed, so each rise of it is a task
	 * handed in, counted on its own line as a Wintertodt or a rooftop lap count is.
	 */
	private void readPortTasks()
	{
		int count = client.getVarpValue(VarPlayerID.PORT_TASKS_COMPLETED);
		if (Chronicle.portTasksDone(portTasks, count) > 0)
		{
			// Counted as the game counts it, from the first task of the rise, so the line shows the lifetime total too.
			chronicle.killCount(Chronicle.PORT_TASKS, portTasks + 1);
			chronicle.killCount(Chronicle.PORT_TASKS, count);
			dirty = true;
		}
		portTasks = count;
	}

	/** Reads the slayer task as the game's slayer helper shows it, and tells the session. */
	private void readSlayer(long now)
	{
		int left = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		String name = left > 0 ? slayerTaskName() : null;
		Boolean first = slayer.take(left, chronicle.hasSlayerTask(), left <= 0 || name != null);
		if (first == null)
		{
			// Just arrived in a world: the task's varps may not be back yet, so the task followed is not dropped at once.
			return;
		}
		boolean fresh = first;
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
		if (event.getType() == ChatMessageType.GAMEMESSAGE && config.agilityCoach())
		{
			ChatParser.KillCount lap = ChatParser.killCount(Text.removeTags(event.getMessage()).replace('\u00a0', ' ').trim());
			Rooftop course = lap == null ? null : Rooftop.byLapName(lap.getName());
			if (course != null)
			{
				if (pendingLap != null)
				{
					lapDone(pendingLap, pendingLapTick, System.currentTimeMillis());
				}
				// Filed at the next tick, once the tick's last experience drop is in.
				pendingLap = course;
				pendingLapTick = client.getTickCount();
			}
		}
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
			lapClock.reset();
			bench.forget();
			// What was lost waits at the grave; it is not spent.
			supplies.quiet(client.getTickCount() + 10);
			dirty = true;
			saveSoon = true;
		}
	}

	private void loadProfile()
	{
		profile = configManager.getRSProfileKey();
		agilityLog = profile == null ? new AgilityLog()
			: AgilityLog.read(gson, configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, AGILITY_KEY));
		agilityDirty = true;
		benchLog = profile == null ? new BenchLog()
			: BenchLog.read(gson, configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, BENCH_KEY));
		underwaterLog = profile == null ? new UnderwaterLog()
			: UnderwaterLog.read(gson, configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, UNDERWATER_KEY));
		underwaterDirty = true;
		journal = Collections.emptyList();
		pastSummaries = Collections.emptyList();
		chronicle.restore(null);
		if (profile == null)
		{
			return;
		}
		String saved = configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, JOURNAL_KEY);
		boolean[] lost = {false};
		List<Session> list = Journal.read(gson, saved, () -> lost[0] = true);
		if (lost[0] || list.isEmpty() && saved != null && !saved.trim().isEmpty() && !saved.trim().equals("[]"))
		{
			// Some or all of it could not be read, and the next session filed writes over it. The save is
			// set aside first as it was, one copy only, never over a fuller one (see Saved.setAside).
			String kept = configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, DAMAGED_KEY);
			if (Saved.setAside(kept, saved))
			{
				log.warn("Part of the saved journal could not be read; it is kept as it was as {}", DAMAGED_KEY);
				configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, profile, DAMAGED_KEY, saved);
			}
		}
		journal = list;
		// Sessions filed before the activity was kept get it now, for Codex Ledger to read.
		if (Journal.label(list))
		{
			storeJournal();
		}
		chronicle.restore(Session.read(gson, configManager.getRSProfileConfiguration(CodexSessionChronicleConfig.GROUP, CURRENT_KEY)));
		summarisePast();
		readLedgerBook();
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
			live = Summary.of(copy, chronicle.isLive() ? Summary.State.LIVE : Summary.State.PAUSED, journal, config.ghostPace());
			ghostPlaque.show(chronicle.isLive() ? live.getGhost() : null);
			ticker.show(view(copy, System.currentTimeMillis(), chronicle.isLive()));
		}
		else
		{
			ticker.show(null);
			ghostPlaque.show(null);
		}
		long now = System.currentTimeMillis();
		ZoneId zone = ZoneId.systemDefault();
		publishedDay = Midnight.day(now, zone);
		Journal.Records records = Journal.records(journal, copy, now, zone);
		Week week = Week.of(journal, copy, now, zone, ledgerBook);
		Player player = client.getLocalPlayer();
		String name = client.getGameState() == GameState.LOGGED_IN && player != null ? player.getName() : null;
		List<Summary> past = pastSummaries;
		ChroniclePanel target = panel;
		if (target != null)
		{
			Summary shown = live;
			SwingUtilities.invokeLater(() -> target.update(shown, past, records, week, name));
		}
		if (agilityDirty)
		{
			publishAgility();
		}
		if (underwaterDirty)
		{
			publishUnderwater();
		}
	}

	/** Forgets the lap under way and the readings that must be taken afresh: after a hop, a logout or a new account. */
	private void resetAgility()
	{
		lapClock.reset();
		pendingLap = null;
		agilityXp = -1;
		carriedMarks = -1;
		lapPlaque.show(null);
	}

	/** Agility experience moved: a gain inside a lap is an obstacle crossed. */
	private void agilityXp(int xp)
	{
		if (agilityXp >= 0 && xp > agilityXp && config.agilityCoach())
		{
			lapClock.xp(xp - agilityXp, client.getTickCount());
			agilityDirty |= lapClock.isRunning();
		}
		agilityXp = xp;
	}

	/** Each tick: files a lap told last tick, keeps the clock honest about where the player is, and reads the level. */
	private void tickAgility(long now)
	{
		if (!config.agilityCoach() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (pendingLap != null)
		{
			Rooftop done = pendingLap;
			pendingLap = null;
			lapDone(done, pendingLapTick, now);
		}
		int tick = client.getTickCount();
		Rooftop here = courseHere();
		lapClock.tick(here, tick);
		if (agilityLog.level(client.getRealSkillLevel(Skill.AGILITY)))
		{
			agilityDirty = true;
			agilitySave = true;
		}
		LapClock.Split split = lapClock.isRunning() ? lapClock.split(tick, agilityLog.get(lapClock.getCourse())) : null;
		lapPlaque.show(split);
		if (split != null && tick % 2 == 0)
		{
			// The Agility tab's clock moves while a lap runs; every other tick is smooth enough for a panel.
			agilityDirty = true;
		}
		if (agilitySave)
		{
			agilitySave = false;
			storeAgility();
		}
		if (agilityDirty)
		{
			publishAgility();
		}
	}

	/** The rooftop course whose map region the player stands in, or null. */
	private Rooftop courseHere()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		return Rooftop.byRegion(player.getWorldLocation().getRegionID());
	}

	/** A lap count message: the lap it ends is filed, and the next begins. */
	private void lapDone(Rooftop course, int tick, long now)
	{
		AgilityLog.Lap lap = lapClock.lapDone(course, tick);
		agilityDirty = true;
		if (lap == null)
		{
			return;
		}
		if (agilityLog.lap(lap, now) && chronicle.isLive())
		{
			chronicle.moment(new Moment(Moment.Kind.BEST, now, course.getShortName() + " lap in " + LapClock.time(lap.getTicks()),
				"New best rooftop lap", 0, 0));
			dirty = true;
			saveSoon = true;
		}
		agilitySave = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK && event.getItemContainer() != null && config.benchProgress() && profile != null)
		{
			Map<Integer, Integer> banked = new HashMap<>();
			add(banked, event.getItemContainer());
			benchSave |= bench.sawBank(banked, System.currentTimeMillis(), benchLog);
		}
		if (event.getContainerId() == InventoryID.BANK && event.getItemContainer() != null && config.underwaterLog() && profile != null)
		{
			int tears = 0;
			for (Item item : event.getItemContainer().getItems())
			{
				if (item != null && Underwater.isMermaidTear(item.getId()))
				{
					tears += item.getQuantity();
				}
			}
			if (underwaterLog.bankTears(tears))
			{
				underwaterSave = true;
				underwaterDirty = true;
			}
		}
		if (!config.agilityCoach())
		{
			return;
		}
		int id = event.getContainerId();
		ItemContainer container = event.getItemContainer();
		if (container == null || id != InventoryID.INV && id != InventoryID.WORN && id != InventoryID.BANK)
		{
			return;
		}
		List<Integer> ids = new ArrayList<>();
		int marks = 0;
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			ids.add(item.getId());
			if (item.getId() == ItemID.GRACE)
			{
				marks += item.getQuantity();
			}
		}
		Set<Graceful> seen = Graceful.among(ids);
		if (agilityLog.sawGraceful(seen))
		{
			agilityDirty = true;
			agilitySave = true;
		}
		if (id == InventoryID.BANK)
		{
			if (agilityLog.bankMarks(marks))
			{
				agilityDirty = true;
				agilitySave = true;
			}
			return;
		}
		if (id == InventoryID.INV)
		{
			// A mark more in the pack, with no bank or trade open, while on a course: one picked up.
			Rooftop on = lapClock.isRunning() ? lapClock.getCourse() : courseHere();
			if (carriedMarks >= 0 && marks > carriedMarks && busy.isEmpty() && on != null)
			{
				agilityLog.mark(on, marks - carriedMarks);
				agilitySave = true;
			}
			if (marks != carriedMarks)
			{
				agilityDirty = true;
			}
			carriedMarks = marks;
		}
	}

	/**
	 * Each tick: reads the pack for a make-X action, keeps the batch under way and sends it to the panel
	 * and the plaque. Nothing is sent to Codex Guardian and no alert is raised: its own idle rule covers that.
	 */
	private void tickBench(long now)
	{
		if (!config.benchProgress() || client.getGameState() != GameState.LOGGED_IN || profile == null)
		{
			return;
		}
		int tick = client.getTickCount();
		Map<Integer, Integer> pack = new HashMap<>();
		add(pack, client.getItemContainer(InventoryID.INV));
		boolean away = !busy.isEmpty() || tick - ateTick <= 1;
		String skill = tick - xpTick <= 1 ? xpSkill : null;
		if (bench.tick(tick, pack, away, skill, now, benchLog, id -> itemManager.getItemComposition(id).getName()))
		{
			benchSave = true;
		}
		Bench.Progress progress = bench.progress(tick, benchLog);
		benchPlaque.show(progress);
		if (benchSave && (tick - benchSaveTick >= BENCH_SAVE_TICKS || progress == null || !progress.isRunning()))
		{
			storeBench();
		}
		sendBench(progress);
	}

	private void sendBench(Bench.Progress progress)
	{
		String key = progress == null ? null : progress.key();
		if (java.util.Objects.equals(key, benchSent))
		{
			return;
		}
		benchSent = key;
		ChroniclePanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.updateBench(progress));
		}
	}

	/** Forgets the pack and the batch under way: after a hop, a logout, a new account or the setting turned off. */
	private void resetBench()
	{
		bench.reset();
		benchPlaque.show(null);
		skillXp.clear();
		xpSkill = null;
		sendBench(null);
	}

	private void storeBench()
	{
		benchSaveTick = client.getTickCount();
		if (benchSave && profile != null)
		{
			configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, profile, BENCH_KEY, gson.toJson(benchLog));
		}
		benchSave = false;
	}

	/**
	 * Each tick: reads the tears carried, Agility and Thieving experience and whether the player is
	 * underwater, files a dive or a hand-in to Mairin that ended, and sends the dive to the panel and
	 * the plaque. Nothing in the game view is marked.
	 */
	private void tickUnderwater(long now)
	{
		if (!config.underwaterLog() || client.getGameState() != GameState.LOGGED_IN || profile == null)
		{
			return;
		}
		int tick = client.getTickCount();
		int mermaid = 0;
		int glistening = 0;
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			mermaid = -1;
			glistening = -1;
		}
		else
		{
			for (Item item : inv.getItems())
			{
				if (item == null)
				{
					continue;
				}
				if (Underwater.isMermaidTear(item.getId()))
				{
					mermaid += item.getQuantity();
				}
				else if (Underwater.isGlisteningTear(item.getId()))
				{
					glistening += item.getQuantity();
				}
			}
		}
		boolean bank = busy.contains(InterfaceID.BANKMAIN) || busy.contains(InterfaceID.BANK_DEPOSITBOX);
		DiveWatch.Step step = diveWatch.tick(tick, now, new DiveWatch.Reading(isUnderwater(), busy.contains(InterfaceID.SHOPMAIN), bank,
			mermaid, glistening, client.getSkillExperience(Skill.AGILITY), client.getSkillExperience(Skill.THIEVING)));
		if (step.getHandIn() != null)
		{
			DiveWatch.HandIn h = step.getHandIn();
			underwaterLog.handIn(h.getTears(), h.getAgilityXp(), h.getThievingXp());
			underwaterSave = true;
		}
		if (step.getFiled() != null)
		{
			fileDive(step.getFiled(), now);
		}
		if (underwaterLog.levels(client.getRealSkillLevel(Skill.AGILITY), client.getRealSkillLevel(Skill.THIEVING)))
		{
			underwaterSave = true;
			underwaterDirty = true;
		}
		underwaterDirty |= step.isChanged() || diveWatch.isDiving() && tick % 5 == 0;
		if (diveWatch.isDiving())
		{
			divePlaque.show(coach(underwaterLog));
		}
		else
		{
			divePlaque.show(null);
		}
		if (underwaterSave && (!diveWatch.isDiving() || step.getHandIn() != null))
		{
			storeUnderwater();
		}
		if (underwaterDirty)
		{
			publishUnderwater();
		}
	}

	/** Whether the player is under the sea off Fossil Island: in its map regions, or with the oxygen bar up. */
	private boolean isUnderwater()
	{
		Player player = client.getLocalPlayer();
		if (player != null && Underwater.isRegion(player.getWorldLocation().getRegionID()))
		{
			return true;
		}
		Widget oxygen = client.getWidget(InterfaceID.FossilUnderwater.OXYGEN_LAYER);
		return oxygen != null && !oxygen.isHidden();
	}

	/** Ends the dive under way, if any, and files it: after a hop, a logout, a new account or the log turned off. */
	private void endDive(long now)
	{
		UnderwaterLog.Dive d = diveWatch.reset();
		divePlaque.show(null);
		if (d != null)
		{
			fileDive(d, now);
		}
		if (underwaterSave)
		{
			storeUnderwater();
		}
		underwaterDirty = true;
	}

	private void fileDive(UnderwaterLog.Dive d, long now)
	{
		if (underwaterLog.file(d) && chronicle.isLive())
		{
			chronicle.moment(new Moment(Moment.Kind.BEST, now, "Dive at " + Math.round(d.tearsPerHour()) + " tears an hour",
				"New best underwater dive", ItemID.FOSSIL_MERMAID_TEAR, 0));
			dirty = true;
			saveSoon = true;
		}
		underwaterSave = true;
		underwaterDirty = true;
	}

	private void storeUnderwater()
	{
		if (underwaterSave && profile != null)
		{
			configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, profile, UNDERWATER_KEY, gson.toJson(underwaterLog));
		}
		underwaterSave = false;
	}

	/** The underwater coach from a log, with the dive under way and what is carried now. */
	private UnderwaterCoach coach(UnderwaterLog log)
	{
		boolean in = client.getGameState() == GameState.LOGGED_IN;
		return UnderwaterCoach.of(log, diveWatch.current(), diveWatch.isUnderwater(), diveWatch.getMermaid(), diveWatch.getGlistening(),
			in ? client.getRealSkillLevel(Skill.AGILITY) : 0, in ? client.getRealSkillLevel(Skill.THIEVING) : 0, config.underwaterGoal());
	}

	/** Sends the panel the underwater log, worked out here from a copy, so Swing never reads what the client thread writes. */
	private void publishUnderwater()
	{
		underwaterDirty = false;
		ChroniclePanel target = panel;
		if (target == null)
		{
			return;
		}
		boolean off = !config.underwaterLog();
		UnderwaterCoach shown = off || profile == null ? null : coach(underwaterLog.copy(gson));
		SwingUtilities.invokeLater(() -> target.updateUnderwater(shown, off));
	}

	private void storeAgility()
	{
		if (profile != null)
		{
			configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, profile, AGILITY_KEY, gson.toJson(agilityLog));
		}
	}

	/** Sends the panel the rooftop coach, worked out here from a copy, so Swing never reads what the client thread writes. */
	private void publishAgility()
	{
		agilityDirty = false;
		ChroniclePanel target = panel;
		if (target == null)
		{
			return;
		}
		boolean off = !config.agilityCoach();
		AgilityCoach coach = null;
		if (!off && profile != null)
		{
			AgilityLog copy = agilityLog.copy(gson);
			int tick = client.getTickCount();
			LapClock.Split split = lapClock.isRunning() ? lapClock.split(tick, copy.get(lapClock.getCourse())) : null;
			AgilityCoach.Diaries diaries = new AgilityCoach.Diaries(
				client.getVarbitValue(VarbitID.DESERT_DIARY_HARD_COMPLETE) == 1,
				client.getVarbitValue(VarbitID.FREMENNIK_DIARY_HARD_COMPLETE) == 1,
				client.getVarbitValue(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE) == 1);
			coach = AgilityCoach.of(copy, copy.getLevel(), diaries, Math.max(0, carriedMarks), split);
		}
		AgilityCoach shown = coach;
		SwingUtilities.invokeLater(() -> target.updateAgility(shown, off));
	}

	/** Asks where to save the card with RuneLite's own file chooser, then writes it there; runs on the Swing thread. */
	private void saveCard(BufferedImage card, String fileName)
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save session card")
			.addExtensionFilter("PNG image", "png")
			.setDefaultExtension("png")
			.setFileName(fileName)
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
