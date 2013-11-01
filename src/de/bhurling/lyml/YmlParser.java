package de.bhurling.lyml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

public class YmlParser {
	private static final String OPEN_RESOURCE_TAG = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n";
	private static final String CLOSE_RESOURCE_TAG = "</resources>\n";

	public static void parse(String filename) throws IOException {
		ZipFile zipFile = new ZipFile(filename);

		String basename = basename(filename);
		String path = path(filename);

		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(
				String.format("%s/%s-android.zip", path, basename)));

		for (ZipEntry entry : Collections.list(zipFile.entries())) {
			if (!entry.isDirectory()) {
				String locale = basename(entry.getName());

				String nextOutEntry = String.format("values-%s/%s.xml",
						StringUtils.join(locale.split("-"), "-r"),
						fixXmlName(basename));

				// values-en/ should be default and go into values/
				nextOutEntry = nextOutEntry.replace("-en/", "/");

				zos.putNextEntry(new ZipEntry(nextOutEntry));

				parse(zipFile.getInputStream(entry), locale, zos);

				zos.closeEntry();
			}
		}

		zos.close();

	}

	public static String basename(String filename) {
		int index = filename.lastIndexOf("/");

		return filename.substring(index + 1).split("\\.(?=[^\\.]+$)")[0];
	}

	public static String path(String filename) {
		int index = filename.lastIndexOf("/");

		if (index == -1) {
			return "";
		}

		return filename.substring(0, index);
	}

	public static void parse(InputStream is, String locale, ZipOutputStream zos)
			throws IOException {
		Yaml yaml = new Yaml();
		HashMap<?, ?> map = (HashMap<?, ?>) yaml.load(is);

		zos.write(OPEN_RESOURCE_TAG.getBytes());
		parse("", (HashMap<?, ?>) map.get(locale), zos);
		zos.write(CLOSE_RESOURCE_TAG.getBytes());
	}

	public static void parse(String prefix, HashMap<?, ?> map,
			ZipOutputStream zos) throws IOException {
		Iterator<?> iter = map.keySet().iterator();

		while (iter.hasNext()) {
			String nextKey = iter.next().toString();
			String nextPrefix = String.format("%s_%s", prefix,
					nextKey.toString());
			Object nextValue = map.get(nextKey);
			if (nextValue instanceof HashMap) {
				parse(nextPrefix, (HashMap<?, ?>) nextValue, zos);
			} else if (nextValue != null) {
				nextValue = fixValue(nextValue.toString());

				String resourceLine = String.format(
						"    <string name=\"%s\">%s</string>\n",
						nextPrefix.substring(1), nextValue);

				zos.write(resourceLine.getBytes());
			}
		}
	}

	public static String fixValue(String nextValue) {
		// TODO replace occurences of %x$@ by %x$s
		return nextValue.replace("\n", "\\n").replace("&", "&amp;")
				.replace("...", "…");
	}

	public static String fixXmlName(String filename) {
		String returnValue = filename.replaceAll("[\\d\\-]", "").trim()
				.toLowerCase().replace(" ", "_");

		return returnValue;
	}
}
