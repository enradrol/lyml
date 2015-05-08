package de.bhurling.lyml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Main {

    private static String USAGE =
            "\nusage: java -jar lyml.jar <api-token> [<api-token>...]\n";

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println(USAGE);
            System.exit(1);
        }

        String fileName = "/truststore.jks"; // java does not trust the COMODO certificate for some reason

        InputStream in = Main.class.getResourceAsStream(fileName);

        final File tempFile = File.createTempFile("tmp", "jks");
        tempFile.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            while (len != -1) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
        }

        String path = tempFile.toString();
        System.setProperty("javax.net.ssl.trustStore", path); // so we use a custom keystore

        YmlParser parser = new YmlParser(args);
        parser.fetchFromLocaleApp();
        parser.createResources();
    }
}
