package com.chatsystem.user;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Runs real FXML/control checks on the JavaFX application thread. */
public abstract class JavaFxTestSupport {
    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready::countDown);
        }
        if (!ready.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timed out");
    }

    protected static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    protected static void onFx(Runnable action) throws Exception {
        onFx(() -> { action.run(); return null; });
    }
}
