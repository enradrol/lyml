package de.bhurling.lyml;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import javax.swing.TransferHandler;

public class YmlTransferHandler extends TransferHandler {

	@Override
	public boolean canImport(TransferHandler.TransferSupport info) {
		// Check for String flavor
		if (!info.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			return false;
		}
		return true;
	}

	@Override
	public boolean importData(TransferHandler.TransferSupport info) {
		if (!info.isDrop()) {
			return false;
		}

		try {
			// Get the string that is being dropped.
			Transferable t = info.getTransferable();
			String filename = (String) t
					.getTransferData(DataFlavor.stringFlavor);
			YmlParser.parse(clean(filename));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	private String clean(String filename) {
		return filename.replace("%20", " ").replace("file://", "").trim();
	}



}
