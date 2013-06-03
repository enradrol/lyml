package de.bhurling.lyml;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.TransferHandler;

import org.yaml.snakeyaml.Yaml;

public class YmlTransferHandler extends TransferHandler {

	private static final String OPEN_RESOURCE_TAG = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n";
	private static final String CLOSE_RESOURCE_TAG = "</resources>\n";

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
			parse(clean(filename));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	private String clean(String filename) {
		return filename.replace("%20", " ").replace("file://", "").trim();
	}

	private void parse(String filename) throws IOException {
		ZipFile zipFile = new ZipFile(filename);

		String basename = basename(filename);

		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(
				String.format("%s-android.zip", basename)));

		for (ZipEntry entry : Collections.list(zipFile.entries())) {
			if (!entry.isDirectory()) {
				String locale = basename(entry.getName());

				String nextOutEntry = String.format("values-%s/strings.xml",
						locale);
				zos.putNextEntry(new ZipEntry(nextOutEntry));

				parse(zipFile.getInputStream(entry), locale, zos);

				zos.closeEntry();
			}
		}

		zos.close();

	}

	private String basename(String filename) {
		return filename.split("\\.(?=[^\\.]+$)")[0];
	}

	private void parse(InputStream is, String locale, ZipOutputStream zos)
			throws IOException {
		Yaml yaml = new Yaml();
		HashMap<?, ?> map = (HashMap<?, ?>) yaml.load(is);

		zos.write(OPEN_RESOURCE_TAG.getBytes());
		parse("", (HashMap<?, ?>) map.get(locale), zos);
		zos.write(CLOSE_RESOURCE_TAG.getBytes());
	}

	private void parse(String prefix, HashMap<?, ?> map, ZipOutputStream zos)
			throws IOException {
		Iterator<?> iter = map.keySet().iterator();

		while (iter.hasNext()) {
			String nextKey = iter.next().toString();
			String nextPrefix = String.format("%s_%s", prefix,
					nextKey.toString());
			Object nextValue = map.get(nextKey);
			if (nextValue instanceof HashMap) {
				parse(nextPrefix, (HashMap<?, ?>) nextValue, zos);
			} else {
				String resourceLine = String.format(
						"    <string name=\"%s\">%s</string>",
						nextPrefix.substring(1), nextValue);
				resourceLine = resourceLine.replace("\n", "\\n");
				zos.write(resourceLine.getBytes());
				zos.write("\n".getBytes());
			}
		}
	}

}
