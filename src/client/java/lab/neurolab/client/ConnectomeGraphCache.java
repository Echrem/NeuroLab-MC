package lab.neurolab.client;

import lab.neurolab.brain.ConnectomeData;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** Shares one asynchronously loaded anatomical graph between the dashboard and full-screen explorer. */
final class ConnectomeGraphCache {
    private static CompletableFuture<ConnectomeData> load;

    private ConnectomeGraphCache() {}

    static synchronized CompletableFuture<ConnectomeData> load() {
        if (load == null) load = CompletableFuture.supplyAsync(() -> {
            try {
                return ConnectomeData.readBundled();
            } catch (IOException e) {
                throw new IllegalStateException("Could not load bundled connectome", e);
            }
        });
        return load;
    }
}
