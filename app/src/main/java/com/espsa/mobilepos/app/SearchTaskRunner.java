package com.espsa.mobilepos.app;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class SearchTaskRunner {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();

    public <T> void runLatest(SearchWork<T> work, SearchCallback<T> callback) {
        final int requestId = generation.incrementAndGet();
        executor.execute(() -> runInBackground(requestId, work, callback));
    }

    public void cancelPending() {
        generation.incrementAndGet();
    }

    public void shutdown() {
        generation.incrementAndGet();
        executor.shutdownNow();
    }

    private <T> void runInBackground(int requestId, SearchWork<T> work, SearchCallback<T> callback) {
        T result = null;
        RuntimeException error = null;
        try {
            result = work.run();
        } catch (RuntimeException ex) {
            error = ex;
        }
        final T finalResult = result;
        final RuntimeException finalError = error;
        mainHandler.post(() -> deliverIfLatest(requestId, finalResult, finalError, callback));
    }

    private <T> void deliverIfLatest(
            int requestId,
            T result,
            RuntimeException error,
            SearchCallback<T> callback
    ) {
        if (requestId != generation.get()) {
            return;
        }
        if (error != null) {
            callback.onError(error);
        } else {
            callback.onResult(result);
        }
    }

    public interface SearchWork<T> {
        T run();
    }

    public interface SearchCallback<T> {
        void onResult(T result);

        void onError(RuntimeException error);
    }
}
