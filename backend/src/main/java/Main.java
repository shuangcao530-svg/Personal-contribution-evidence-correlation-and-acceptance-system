import cn.edu.contribution.ContributionApplication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * IDE-friendly entry point for starting the contribution evidence backend.
 * Configure the backend module as the run classpath and run this class.
 * DB_USER / DB_PASSWORD environment variables override the ignored local file
 * at .runtime/local-database.properties.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Properties local = loadLocalDatabaseProperties();
        List<String> launchArgs = new ArrayList<>(List.of(args));
        addDatabaseDefault(launchArgs, local, "DB_USER");
        addDatabaseDefault(launchArgs, local, "DB_PASSWORD");
        ContributionApplication.main(launchArgs.toArray(String[]::new));
    }

    private static void addDatabaseDefault(List<String> args, Properties local, String key) {
        if (hasEnvironmentValue(key) || hasArgument(args, key)) {
            return;
        }
        String value = local.getProperty(key);
        if (value != null && !value.isBlank()) {
            args.add("--" + key + "=" + value.trim());
        }
    }

    private static boolean hasEnvironmentValue(String key) {
        String value = System.getenv(key);
        return value != null && !value.isBlank();
    }

    private static boolean hasArgument(List<String> args, String key) {
        String prefix = "--" + key + "=";
        return args.stream().anyMatch(arg -> arg.startsWith(prefix));
    }

    private static Properties loadLocalDatabaseProperties() {
        Properties properties = new Properties();
        Path workingDirectory = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                workingDirectory.resolve(".runtime/local-database.properties"),
                workingDirectory.resolve("../.runtime/local-database.properties").normalize()
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try (InputStream input = Files.newInputStream(candidate)) {
                    properties.load(input);
                } catch (IOException exception) {
                    System.err.println("Could not read local database settings: " + candidate);
                }
                break;
            }
        }
        return properties;
    }
}
