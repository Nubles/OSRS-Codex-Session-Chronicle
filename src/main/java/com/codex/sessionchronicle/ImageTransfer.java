package com.codex.sessionchronicle;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

/** Hands the card to the system clipboard as a picture. */
final class ImageTransfer implements Transferable
{
	private final BufferedImage image;

	ImageTransfer(BufferedImage image)
	{
		this.image = image;
	}

	@Override
	public DataFlavor[] getTransferDataFlavors()
	{
		return new DataFlavor[]{DataFlavor.imageFlavor};
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor)
	{
		return DataFlavor.imageFlavor.equals(flavor);
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
	{
		if (!isDataFlavorSupported(flavor))
		{
			throw new UnsupportedFlavorException(flavor);
		}
		return image;
	}
}
