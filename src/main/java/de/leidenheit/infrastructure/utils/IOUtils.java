package de.leidenheit.infrastructure.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IOUtils {

    public static boolean isValidFileOrUrl(final String relativeOrAbsolutePath) {
        try {
            URI uri = new URI(relativeOrAbsolutePath);
            if (uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                return isValidUrl(uri.toURL());
            }

            Path path;
            if (!uri.isAbsolute()) {
                path = Paths
                        .get(retrieveWorkingDir())
                        .resolve(uri.getPath())
                        .normalize(); // removes . and ..
            } else {
                path = Paths.get(uri);
            }

            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidUrl(final URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return (responseCode == HttpURLConnection.HTTP_OK);
        } catch (IOException e) {
            return false;
        }
    }

    private static String retrieveWorkingDir() {
        return System.getProperty("user.dir");
    }

    private IOUtils() {}
}
