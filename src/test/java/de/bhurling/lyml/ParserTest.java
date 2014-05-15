package de.bhurling.lyml;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import org.junit.Test;

public class ParserTest {

    YmlParser mParser = new YmlParser();

    @Test
    public void testFixValueAndroid() {
        String value = "abc & 1";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo("abc &amp; 1");

        value = "<ABC>";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo("&lt;ABC&gt;");

        value = "fuck y'all";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo("fuck y\\'all");

        value = "...";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo("…");

        value = "linebreak \n linbreak";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo(
                "linebreak \\n linbreak");

        value = "linebreak \r linbreak";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo(
                "linebreak \\n linbreak");

        value = "linebreak \r\n linbreak";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo(
                "linebreak \\n linbreak");

        value = "linebreak \n\r linebreak \n linebreak";
        assertThat(mParser.fixValueForAndroid(value)).isEqualTo(
                "linebreak \\n linebreak \\n linebreak");

    }

    @Test
    public void testFixLocaleIOS() {
        assertThat(mParser.fixLocaleForIOS("zh")).isEqualTo("zh-Hans");
        assertThat(mParser.fixLocaleForIOS("de")).isEqualTo("de");
        assertThat(mParser.fixLocaleForIOS("en")).isEqualTo("en");
    }

    @Test
    public void testFixValueIOS() {
        String value = "some string %s";
        assertThat(mParser.fixValueForIOS(value)).isEqualTo("some string %@");

        value = "some some %1$s";
        assertThat(mParser.fixValueForIOS(value)).isEqualTo("some some %1$@");

        value = "some some %1$s and more %2$s la";
        assertThat(mParser.fixValueForIOS(value)).isEqualTo(
                "some some %1$@ and more %2$@ la");
        assertEquals("some some %1$@ and more %2$@ la", mParser.fixValueForIOS(value));

        value = "some text in \"quotes\" text";
        assertThat(mParser.fixValueForIOS(value)).isEqualTo(
                "some text in \\\"quotes\\\" text");
    }

    @Test
    public void testParseAndStore() {
        String yml = "{\"en\":{\"outer\":{\"inner\":\"english\"}}}";
        mParser.parseAndStore(new ByteArrayInputStream(yml.getBytes()));

        yml = "{\"de\":{\"outer\":{\"inner\":\"deutsch\"}}}";
        mParser.parseAndStore(new ByteArrayInputStream(yml.getBytes()));

        assertThat(mParser.mTranslations.size()).isEqualTo(2);
        assertThat(mParser.mTranslations.get("de")).isNotNull();
        assertThat(mParser.mTranslations.get("de").get("outer.inner"))
                .isEqualTo("deutsch");

        yml = "{\"en\":{\"outer\":{\"inner_2\":\"more english\"}}}";
        mParser.parseAndStore(new ByteArrayInputStream(yml.getBytes()));

        assertThat(mParser.mTranslations.get("en").get("outer.inner"))
                .isEqualTo("english");
        assertThat(mParser.mTranslations.get("en").get("outer.inner_2"))
                .isEqualTo("more english");
    }

    @Test
    public void testParseAndStoreRecursive() {
        HashMap<Object, Object> input = new HashMap<Object, Object>();
        input.put("key", "value");

        HashMap<String, String> output = new HashMap<String, String>();
        mParser.parseAndStore("", input, output);
        assertThat(output.get("key")).isEqualTo("value");
        output.clear();

        mParser.parseAndStore("prefix", input, output);
        assertThat(output.get("prefix.key")).isEqualTo("value");
        output.clear();

        HashMap<Object, Object> innerHashMap = new HashMap<Object, Object>();
        innerHashMap.put("inner_key", "value");
        input.put("key", innerHashMap);
        mParser.parseAndStore("prefix", input, output);
        assertThat(output.get("prefix.key.inner_key")).isEqualTo("value");
        output.clear();

        input.put("second_key", "second_value");
        mParser.parseAndStore("", input, output);
        assertThat(output.size()).isEqualTo(2);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateEntries() {
        HashMap<Object, Object> input = new HashMap<Object, Object>();
        input.put("key", "value");

        HashMap<String, String> output = new HashMap<String, String>();
        mParser.parseAndStore("", input, output);

        input = new HashMap<Object, Object>();
        input.put("key", "another Value");

        mParser.parseAndStore("", input, output);
    }

    @Test
    public void testCreateAndroidResourceString() {
        String resource = mParser.createAndroidResource("android.key",
                "value <>");
        assertThat(resource).isEqualTo(
                "    <string name=\"android_key\">value &lt;&gt;</string>\n");
    }

    @Test
    public void testCreateIOSResourceString() {
        String resource = mParser.createLocalizableString("ios.key",
                "some value");
        assertThat(resource).isEqualTo("\"ios_key\"=\"some value\";\n");
    }

    @Test
    public void testCamelCase() {
        String key = mParser.camelCase("ios.key");
        assertThat(key).isEqualTo("kIosKey");

        key = mParser.camelCase("ios.key.with_more");
        assertThat(key).isEqualTo("kIosKeyWithMore");
    }

    @Test
    public void testCreateIosDefinition() {
        String key = "ios.key.with_more";
        assertThat(mParser.createIosDefinition(key)).isEqualTo(
                "#define kIosKeyWithMore @\"ios.key.with_more\"\n");
    }

}
