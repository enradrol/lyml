package de.bhurling.lyml;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class YmlParser {
    public HashMap<String, HashMap<String, String>> mTranslations = new HashMap<>();

    private final String[] mApiKeys;

    private Object mDefaultLocale;

    private File mOutputDirectory;

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
                output = new HashMap<>();
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
        ArrayList<String> keys = new ArrayList<>(mTranslations.get(mDefaultLocale.toString()).keySet());
        Collections.sort(keys);

        createAndroidResources(keys);
        createIosResources(keys);
        createWinPhoneResources(keys);
        createJavaResources(keys);

        System.out.println("\nDone. Have a look into\n\n" + mOutputDirectory.getAbsolutePath()
                + "\n\nto find your files. Have a nice day!\n\n");
    }


    private void createJavaResources(ArrayList<String> keys) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("out/strings-java.zip"));

        System.out.println("Creating Java resources.");

        for (String locale : mTranslations.keySet()) {

            String[] splittedLocale = locale.split("-");

            String languageCode = splittedLocale[0];
            String regionalCode = splittedLocale.length == 2 ? splittedLocale[1] : null;

            // append xx-XX (but leave out xx if this is the default locale)
            if (!mDefaultLocale.equals(locale)) {

                if (regionalCode != null) {
                    languageCode += "-" + regionalCode.toUpperCase();
                }
            }
            String outPath = String.format("%s", languageCode);

            // append the filename (based on ZIPs filename)
            String nextOutEntry = String.format("%s/language.properties", outPath);
            zos.putNextEntry(new ZipEntry(nextOutEntry));

            for (String key : keys) {
                // find a value in the regional translations
                String value = getValue(key, locale);

                // create an android resource string and write to the output zip
                if (!StringUtils.isEmpty(value)) {
                    zos.write(createJavaResource(key, value).getBytes("UTF-8"));
                }
            }
        }

        zos.close();

    }

    private void createAndroidResources(ArrayList<String> keys) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("out/strings-android.zip"));

        System.out.println("Creating Android resources.");

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
            String outPath = String.format("values%s", appendix);

            // append the filename (based on ZIPs filename)
            String nextOutEntry = String.format("%s/strings.xml", outPath);
            zos.putNextEntry(new ZipEntry(nextOutEntry));

            zos.write(AndroidResource.OPEN.getBytes("UTF-8"));

            for (String key : keys) {
                // find a value in the regional translations
                String value = getValue(key, locale);

                // create an android resource string and write to the output zip
                if (!StringUtils.isEmpty(value)) {
                    zos.write(createAndroidResource(key, value).getBytes("UTF-8"));
                }
            }

            zos.write(AndroidResource.CLOSE.getBytes("UTF-8"));
        }

        zos.close();
    }

    private void createIosResources(ArrayList<String> keys) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("out/strings-ios.zip"));

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
    }

    private void createWinPhoneResources(ArrayList<String> keys) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("out/strings-win.zip"));

        System.out.println("Creating Windows Phone resources.");

        for (String locale : mTranslations.keySet()) {
            String nextOutEntry = String.format("Strings/%s/Resources.resw", locale);
            zos.putNextEntry(new ZipEntry(nextOutEntry));
            zos.write(WinPhoneResource.OPEN.getBytes("UTF-8"));

            for (String key : keys) {

                String value = getValue(key, locale);

                if (!StringUtils.isEmpty(value)) {
                    zos.write(createWinPhoneResource(key, value).getBytes("UTF-8"));
                }
            }

            zos.write(WinPhoneResource.CLOSE.getBytes("UTF-8"));
        }

        zos.close();
    }

    public String createWinPhoneResource(String key, String value) {
        String resourceLine = String.format(
                "\t<data name=\"%s\">\n" +
                        "\t\t<value>%s</value>\n" +
                        "\t</data>\n",
                key.replace(".", "_"), fixValueForWinPhone(value));

        return resourceLine;
    }

    public String fixLocaleForIOS(String locale) {
        return locale.replace("zh", "zh-Hans");
    }

    public String createLocalizableString(String key, String value) {
        return String.format("\"%s\"=\"%s\";\n", fixKeyForIOS(key), fixValueForIOS(value));
    }

    public String createIosDefinition(String key) {
        return String.format("#define %s @\"%s\"\n", camelCase(key), fixKeyForIOS(key));
    }

    public String createAndroidResource(String key, String value) {
        String resourceLine = String.format(
                "    <string name=\"%s\">%s</string>\n",
                key.replace(".", "_"), fixValueForAndroid(value));

        return resourceLine;
    }

    public String createJavaResource(String key, String value) {
        return String.format("%s = %s\n", key, value);
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

    public String fixKeyForIOS(String key) {
        return key.replace(".", "_");
    }

    public String fixValueForIOS(String value) {
        String fixed = value.replace("%s", "%@");

        fixed = fixed.replaceAll("%(\\d+\\$)s", "%$1@").replace("\"", "\\\"");

        return fixed;
    }

    public String fixValueForWinPhone(String value) {
        String fixed = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("%s", "{0}");

        Pattern pattern = Pattern.compile("%((\\d+)\\$)s");
        Matcher matcher = pattern.matcher(fixed);
        while (matcher.find()) {
            int placeholderInt = Integer.parseInt(matcher.group(2)) - 1;
            fixed = fixed.replace(matcher.group(), "{" + placeholderInt + "}");
        }

        return fixed;
    }

    public String camelCase(String string) {
        return StringUtils.join(("k" + WordUtils.capitalizeFully(string
                .replace(".", " ").replace("_", " "))).split(" "), "");
    }

    public void fetchFromLocaleApp() {
        try {
            mOutputDirectory = new File("out");
            mOutputDirectory.mkdirs();

            ZipOutputStream zos = new ZipOutputStream(
                    new FileOutputStream(new File(mOutputDirectory, "original-yml.zip")));

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

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

}
