package com.chatsystem.admin;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import java.util.concurrent.*;

public abstract class JavaFxTestSupport {
    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException running) { Platform.runLater(ready::countDown); }
        if (!ready.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timed out");
    }
    protected static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
