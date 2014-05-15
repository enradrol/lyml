package de.bhurling.lyml;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class YmlParser {
    private static final String OPEN_RESOURCE_TAG = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n";
    private static final String CLOSE_RESOURCE_TAG = "</resources>\n";

    public HashMap<String, HashMap<String, String>> mTranslations = new HashMap<String, HashMap<String, String>>();

    private final String[] mApiKeys;

    private Object mDefaultLocale;

    public YmlParser(String... keys) {
        mApiKeys = keys;
    }

    public void parseAndStore(InputStream is) {
        Yaml yaml = new Yaml();

        HashMap<?, ?> map = (HashMap<?, ?>) yaml.load(is);

        for (Object locale : map.keySet()) {
            if (mDefaultLocale == null) {
                mDefaultLocale = locale;
            }
            HashMap<String, String> output = mTranslations.get(locale);
            if (output == null) {
                output = new HashMap<String, String>();
            }
            parseAndStore("", (HashMap<?, ?>) map.get(locale), output);
            mTranslations.put((String) locale, output);
        }
    }

    public void parseAndStore(String prefix, HashMap<?, ?> map, HashMap<String, String> output) {
        if (map == null || map.isEmpty())
            return;

        Iterator<?> iter = map.keySet().iterator();

        while (iter.hasNext()) {
            String nextKey = iter.next().toString();
            String nextPrefix = StringUtils.isEmpty(prefix) ?
                    nextKey : String.format("%s.%s", prefix, nextKey.toString());
            Object nextValue = map.get(nextKey);
            if (nextValue instanceof HashMap) {
                parseAndStore(nextPrefix, (HashMap<?, ?>) nextValue, output);
            } else if (!StringUtils.isEmpty((String) nextValue)) {
                // Keep existing translations
                if (!output.containsKey(nextPrefix)) {
                    output.put(nextPrefix, (String) nextValue);
                }
            }
        }
    }

    public void createResources() throws IOException {

        // Fetch a list of (alphabetically sorted) keys from the default locale's set of translations
        ArrayList<String> keys = new ArrayList<String>(mTranslations.get(mDefaultLocale.toString()).keySet());
        Collections.sort(keys);

        File outDir = new File("out");
        outDir.mkdirs();

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("out/strings-android.zip"));

        System.out.println("Creating Android resources.");

        // create the base path for our build flavor (e.g. "china/res")
        String baseOutPath = "src/main/res";

        for (String locale : mTranslations.keySet()) {

            String[] splittedLocale = locale.split("-");

            String languageCode = splittedLocale[0];
            String regionalCode = splittedLocale.length == 2 ? splittedLocale[1] : null;

            // append values-xx-rXX (but leave out xx if this is the default locale)
            String appendix = "";
            if (!mDefaultLocale.equals(locale)) {
                appendix = "-" + languageCode;

                if (regionalCode != null) {
                    appendix += "-r" + regionalCode.toUpperCase();
                }
            }
            String outPath = String.format("%s/values%s", baseOutPath, appendix);

            // append the filename (based on ZIPs filename)
            String nextOutEntry = String.format("%s/strings.xml", outPath);
            zos.putNextEntry(new ZipEntry(nextOutEntry));

            zos.write(OPEN_RESOURCE_TAG.getBytes("UTF-8"));

            for (String key : keys) {
                // find a value in the regional translations
                String value = getValue(key, locale);

                // create an android resource string and write to the output zip
                if (!StringUtils.isEmpty(value)) {
                    zos.write(createAndroidResource(key, value).getBytes("UTF-8"));
                }
            }

            zos.write(CLOSE_RESOURCE_TAG.getBytes("UTF-8"));
        }

        zos.close();

        zos = new ZipOutputStream(new FileOutputStream("out/strings-ios.zip"));

        System.out.println("Creating iOS resources.");

        for (String locale : mTranslations.keySet()) {

            // Create next zip entry (e.g. en-US.lproj/Localizable.strings)
            String nextOutEntry = String.format("%s.lproj/Localizable.strings", fixLocaleForIOS(locale));
            zos.putNextEntry(new ZipEntry(nextOutEntry));

            for (String key : keys) {

                // find a value in the translations for the complete locale
                String value = getValue(key, locale);

                // if none is found, find a value in the base language
                if (StringUtils.isEmpty(value)) {
                    value = getValue(key, locale.split("-")[0]);
                }

                // if still empty, use the default locale
                if (StringUtils.isEmpty(value)) {
                    value = getValue(key, mDefaultLocale.toString());
                }

                // create an ios resource string and write to the output zip
                if (!StringUtils.isEmpty(value)) {
                    zos.write(createLocalizableString(key, value).getBytes("UTF-8"));
                }
            }
        }

        // Create constant mapping
        zos.putNextEntry(new ZipEntry("StringDefinitions.h"));

        for (String key : keys) {
            zos.write(createIosDefinition(key).getBytes("UTF-8"));
        }

        zos.close();

        System.out.println("\nDone. Have a look into\n\n" + outDir.getAbsolutePath()
                + "\n\nto find your files. Have a nice day!\n\n");
    }

    public String fixLocaleForIOS(String locale) {
        return locale.replace("zh", "zh-Hans");
    }

    public String createLocalizableString(String key, String value) {
        return String.format("\"%s\"=\"%s\";\n", key.replace(".", "_"), fixValueForIOS(value));
    }

    public String createIosDefinition(String key) {
        return String.format("#define %s @\"%s\"\n", camelCase(key), key);
    }

    public String createAndroidResource(String key, String value) {
        String resourceLine = String.format(
                "    <string name=\"%s\">%s</string>\n",
                key.replace(".", "_"), fixValueForAndroid(value));

        return resourceLine;
    }

    private String getValue(String key, String languageCode) {
        HashMap<String, String> map = mTranslations.get(languageCode);
        if (map == null) {
            return null;
        }

        return map.get(key);
    }

    public String fixValueForAndroid(String nextValue) {
        return nextValue.replace("\r\n", "\\n").replace("\n\r", "\\n")
                .replace("\r", "\\n").replace("\n", "\\n")
                .replace("&", "&amp;")
                .replace("...", "…")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "\\'")
                .replace("%@", "%s")
                .replaceAll("%(\\d+\\$)@", "%$1s");
    }

    public String fixValueForIOS(String value) {
        String fixed = value.replace("%s", "%@");

        fixed = fixed.replaceAll("%(\\d+\\$)s", "%$1@").replace("\"", "\\\"");

        return fixed;
    }

    public String camelCase(String string) {
        return StringUtils.join(("k" + WordUtils.capitalizeFully(string
                .replace(".", " ").replace("_", " "))).split(" "), "");
    }

    public void fetchFromLocaleApp() {
        try {
            File outputDirectory = new File("out");
            outputDirectory.mkdirs();

            ZipOutputStream zos = new ZipOutputStream(
                    new FileOutputStream(new File(outputDirectory, "original-yml.zip")));

            for (String apiKey : mApiKeys) {
                System.out.println("Fetching translations for project " + apiKey);
                String url = String.format(
                        "http://api.localeapp.com/v1/projects/%s/translations/all.yml", apiKey);
                InputStream is = fetchTranslations(url);

                if (is != null) {
                    parseAndStore(is);
                }

                is = fetchTranslations(url);

                if (is != null) {
                    String nextOutEntry = String.format("%s.yml", apiKey);
                    zos.putNextEntry(new ZipEntry(nextOutEntry));

                    byte[] dataBlock = new byte[4096];
                    int count = is.read(dataBlock, 0, 4096);
                    while (count != -1) {
                        zos.write(dataBlock, 0, count);
                        count = is.read(dataBlock, 0, 4096);
                    }
                }

                System.out.println("Done.");
            }

            zos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private InputStream fetchTranslations(String url) {
        URL u;
        try {
            u = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) u.openConnection();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return connection.getInputStream();
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

}
