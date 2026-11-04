package com.codex.sessionchronicle.overlay;

import com.codex.sessionchronicle.core.Underwater;
import com.codex.sessionchronicle.core.UnderwaterCoach;
import com.codex.sessionchronicle.core.UnderwaterLog;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The dive plaque shows only a dive under way, and only when turned on. */
public class DivePlaqueTest
{
	@Test
	public void onlyADiveUnderWayWhenOn()
	{
		DivePlaque p = new DivePlaque();
		UnderwaterCoach ashore = UnderwaterCoach.of(new UnderwaterLog(), null, false, 10, 0, 70, 70, Underwater.Reward.MERFOLK_TRIDENT);
		UnderwaterCoach diving = UnderwaterCoach.of(new UnderwaterLog(), new UnderwaterLog.Dive(1L), true, 10, 0, 70, 70,
			Underwater.Reward.MERFOLK_TRIDENT);
		p.show(diving);
		assertNull(p.shown());
		p.setEnabled(true);
		assertNotNull(p.shown());
		p.show(ashore);
		assertNull(p.shown());
		BufferedImage image = new BufferedImage(300, 80, BufferedImage.TYPE_INT_ARGB);
		assertNull(p.render(image.createGraphics()));
		p.show(diving);
		Dimension d = p.render(image.createGraphics());
		assertTrue(d.width >= 128 && d.width <= 244);
	}

	@Test
	public void theRewardLine()
	{
		// 390 tears to go at the wiki's 200 an hour: 1h 57m.
		UnderwaterCoach c = UnderwaterCoach.of(new UnderwaterLog(), null, false, 10, 0, 70, 70, Underwater.Reward.MERFOLK_TRIDENT);
		assertEquals("Merfolk trident: 390 to go, 1h 57m", DivePlaque.goal(c));
		c = UnderwaterCoach.of(new UnderwaterLog(), null, false, 30, 0, 70, 70, Underwater.Reward.BOWL_OF_FISH);
		assertEquals("Bowl of fish: enough tears", DivePlaque.goal(c));
	}
}
