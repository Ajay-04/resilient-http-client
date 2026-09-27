package com.ajay.resilient.demo;

import com.ajay.resilient.Transport;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * A {@link Transport} that replays a scripted sequence of outcomes per path.
 * Lets the demo exercise retries and the breaker with zero network I/O.
 */
final class ScriptedTransport implements Transport {

    /** Either an HTTP status or a thrown IOException. */
    sealed interface Outcome permits Outcome.Status, Outcome.Error {
        record Status(int code, String body) implements Outcome {}
        record Error(IOException e) implements Outcome {}
    }

    private final Map<String, Queue<Outcome>> scripts = new HashMap<>();
    private final Outcome defaultOutcome;

    ScriptedTransport(Outcome defaultOutcome) {
        this.defaultOutcome = defaultOutcome;
    }

    void script(String path, Outcome... outcomes) {
        Queue<Outcome> q = new ArrayDeque<>();
        for (Outcome o : outcomes) {
            q.add(o);
        }
        scripts.put(path, q);
    }

    @Override
    public HttpResponse<String> exchange(HttpRequest request) throws IOException {
        Queue<Outcome> q = scripts.get(request.uri().getPath());
        Outcome outcome = (q != null && !q.isEmpty()) ? q.poll() : defaultOutcome;
        if (outcome instanceof Outcome.Error err) {
            throw err.e();
        }
        var status = (Outcome.Status) outcome;
        return new SimpleHttpResponse(status.code(), status.body(), request);
    }
}
