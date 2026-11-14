package com.chatsystem.shared;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureSmokeTest {
    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    @Test
    void baselineRunsOnJava17() {
        assertEquals(17, Runtime.version().feature(), "The frontend baseline requires Java 17");
    }

    @Test
    void allSevenAdminSourceScreensArePreserved() throws IOException {
        assertEquals(7, sourceScreenCount("admin"));
    }

    @Test
    void allTwentyOneUserSourceScreensArePreserved() throws IOException {
        assertEquals(21, sourceScreenCount("user"));
    }

    @Test
    void approvedDesignIsAvailable() {
        assertTrue(Files.isRegularFile(REPOSITORY_ROOT.resolve(
                "docs/superpowers/specs/2026-11-14-interactive-javafx-frontends-design.md")),
                "The approved frontend design must be preserved");
    }

    @Test
    void actionableControlInventoryIsAvailable() {
        assertTrue(Files.isRegularFile(REPOSITORY_ROOT.resolve("docs/UI_CONTROL_INVENTORY.md")),
                "Every actionable control must have an inventory classification");
    }

    private static long sourceScreenCount(String app) throws IOException {
        Path screens = REPOSITORY_ROOT.resolve("docs/UI_design/Materials/views").resolve(app);
        try (var paths = Files.walk(screens)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".fxml"))
                    .count();
        }
    }

    private static Path findRepositoryRoot() {
        try {
            // Compiled tests stay inside this checkout even when an IDE uses another working directory.
            Path classes = Path.of(ArchitectureSmokeTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            for (Path start : new Path[]{classes, Path.of(System.getProperty("user.dir"))}) {
                for (Path candidate = start.toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
                    if (Files.isRegularFile(candidate.resolve("Frontend/settings.gradle"))
                            && Files.isDirectory(candidate.resolve("docs/UI_design/Materials"))) {
                        return candidate;
                    }
                }
            }
            throw new IllegalStateException("Cannot locate the frontend repository from test classes or working directory");
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Cannot resolve the compiled test location", exception);
        }
    }
}
