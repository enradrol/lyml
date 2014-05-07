package de.bhurling.lyml;

public class Main {

    public static void main(String[] args) throws Exception {
        YmlParser parser = new YmlParser(args);
        parser.fetchFromLocaleApp();
        parser.createResources();
    }
}
