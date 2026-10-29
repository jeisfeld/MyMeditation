package de.jeisfeld.mymeditation.i18n;

import java.io.File;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalizationTest {
    private File resources() {
        File folder = new File("src/main/res");
        return folder.isDirectory() ? folder : new File("app/src/main/res");
    }
    private Map<String, Element> entries(String locale) throws Exception {
        Map<String, Element> result = new HashMap<>();
        File[] files = new File(resources(), locale).listFiles((folder, name) -> name.endsWith(".xml"));
        assertNotNull(files);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        for (File file : files) {
            NodeList children = factory.newDocumentBuilder().parse(file).getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element) {
                    Element element = (Element) children.item(i);
                    if (element.hasAttribute("name")) { assertNull("Duplicate resource", result.put(element.getAttribute("name"), element)); }
                }
            }
        }
        return result;
    }
    private List<String> placeholders(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("%(?:[0-9]+\\$)?[dsf]").matcher(text);
        while (matcher.find()) { result.add(matcher.group()); }
        Collections.sort(result);
        return result;
    }
    @Test public void everyTranslatableDefaultResourceHasGermanWithMatchingFormats() throws Exception {
        Map<String, Element> english = entries("values"), german = entries("values-de");
        for (Map.Entry<String, Element> resource : english.entrySet()) {
            Element source = resource.getValue();
            if (!(source.getTagName().equals("string") || source.getTagName().equals("string-array"))) { continue; }
            if (source.getAttribute("translatable").equals("false")) {
                assertFalse("Technical value must not be translated: " + resource.getKey(), german.containsKey(resource.getKey()));
                continue;
            }
            Element translated = german.get(resource.getKey());
            assertNotNull("Missing German: " + resource.getKey(), translated);
            assertEquals(resource.getKey(), placeholders(source.getTextContent()), placeholders(translated.getTextContent()));
            if (source.getTagName().equals("string-array")) {
                assertEquals(source.getElementsByTagName("item").getLength(), translated.getElementsByTagName("item").getLength());
            }
        }
    }
    @Test public void runtimeMessagesUseTheSameEnglishFallbackAsAndroidResources() throws Exception {
        Map<String, Element> english = entries("values");
        for (UiText message : UiText.values()) {
            Element value = english.get("message_" + message.name().toLowerCase(Locale.ROOT));
            assertNotNull(message.name(), value);
            assertEquals(message.name(), message.english, value.getTextContent());
        }
        assertTrue(UiText.SPEECH_HTTP.text(503).contains("503"));
        assertTrue(UiText.RETRY_EXHAUSTED.text("detail").contains("3 attempts"));
    }
}
