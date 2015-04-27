package de.bhurling.lyml;

public class Main {

    private static String USAGE =
            "\nusage: java -jar lyml.jar <api-token> [<api-token>...]\n";

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println(USAGE);
            System.exit(1);
        }
        String fileName = "truststore.jks"; // java does not trust the COMODO certificate for some reason
        System.setProperty("javax.net.ssl.trustStore", fileName); // so we use a custom keystore

        YmlParser parser = new YmlParser(args);
        parser.fetchFromLocaleApp();
        parser.createResources();
    }
}
