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
import com.codex.sessionchronicle.ui.ItemIcons;
import com.codex.sessionchronicle.ui.Sigil;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
	/** Ticks between panel refreshes when only the clock moved. */
	private static final int REFRESH_TICKS = 5;
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
	private boolean dirty;
	private boolean unsaved;
	private int ticks;
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
		});
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			if (!chronicle.isLive())
			{
				pendingBegin = true;
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
		if (pendingBegin)
		{
			tryBegin(now);
		}
		if (!chronicle.isLive())
		{
			return;
		}
		chronicle.tick(now);
		unsaved = true;
		ticks++;
		if (ticks % SAVE_TICKS == 0)
		{
			saveCurrent();
		}
		if (dirty || ticks % REFRESH_TICKS == 0)
		{
			publish();
		}
	}

	/** Starts or picks up the session once the account and its stats are readable. */
	private void tryBegin(long now)
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null || client.getOverallExperience() <= 0)
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
			chronicle.xp(skill.getName(), client.getSkillExperience(skill), client.getRealSkillLevel(skill), now);
		}
		if (fresh && config.chatRecap() && !journal.isEmpty())
		{
			recap(journal.get(0));
		}
		saveCurrent();
		dirty = true;
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
		if (!chronicle.isLive())
		{
			return;
		}
		List<Moment> moments = chronicle.xp(event.getSkill().getName(), event.getXp(), event.getLevel(), System.currentTimeMillis());
		dirty = true;
		if (!moments.isEmpty())
		{
			unsaved = true;
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
		chronicle.loot(source == null ? null : Text.removeTags(source), drops, kills, config.valuableDrop(), System.currentTimeMillis());
		dirty = true;
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
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (chronicle.isLive() && event.getActor() == client.getLocalPlayer())
		{
			chronicle.death(System.currentTimeMillis(), "Back to the respawn point");
			dirty = true;
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
		unsaved = false;
		if (profile == null)
		{
			return;
		}
		Session current = chronicle.current();
		if (current == null)
		{
			configManager.unsetConfiguration(CodexSessionChronicleConfig.GROUP, profile, CURRENT_KEY);
		}
		else
		{
			configManager.setConfiguration(CodexSessionChronicleConfig.GROUP, profile, CURRENT_KEY, gson.toJson(current));
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
		if (current != null)
		{
			Session copy = gson.fromJson(gson.toJson(current), Session.class);
			live = Summary.of(copy, chronicle.isLive() ? Summary.State.LIVE : Summary.State.PAUSED);
			ticker.show(new SessionTicker.View(copy.getPlayedMs(), System.currentTimeMillis(), chronicle.isLive(), copy.totalXp(), copy.lootValue()));
		}
		else
		{
			ticker.show(null);
		}
		Journal.Records records = Journal.records(journal, current == null ? null : gson.fromJson(gson.toJson(current), Session.class),
			System.currentTimeMillis(), ZoneId.systemDefault());
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

	private void saveCard(BufferedImage card, Summary session)
	{
		String player = session.getPlayer();
		executor.execute(() ->
		{
			File folder = new File(RuneLite.SCREENSHOT_DIR, (player == null ? "" : player + File.separator) + "Session Chronicle");
			String base = "Session " + Format.file(session.getStartedAt());
			File file = new File(folder, base + ".png");
			for (int i = 1; file.exists(); i++)
			{
				file = new File(folder, base + " (" + i + ").png");
			}
			String note;
			try
			{
				if (!folder.isDirectory() && !folder.mkdirs())
				{
					throw new IOException("Could not make " + folder);
				}
				ImageIO.write(card, "png", file);
				note = "Saved as " + file.getName() + " in your RuneLite screenshots folder.";
			}
			catch (IOException e)
			{
				log.warn("Could not save the session card", e);
				note = "The card could not be saved: " + e.getMessage();
			}
			String shown = note;
			ChroniclePanel target = panel;
			if (target != null)
			{
				SwingUtilities.invokeLater(() -> target.cardNote(shown));
			}
		});
	}

	private void copyCard(BufferedImage card)
	{
		String note;
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransfer(card), null);
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
