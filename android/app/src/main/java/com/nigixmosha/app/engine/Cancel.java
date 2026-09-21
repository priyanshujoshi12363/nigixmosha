package com.nigixmosha.app.engine;

import java.util.HashSet;
import java.util.Set;

import okhttp3.Call;

public final class Cancel {
    public static final class Cancelled extends RuntimeException {
        public Cancelled() {
            super("Cancelled");
        }
    }

    private final Set<Call> calls = new HashSet<>();
    private final Set<Cancel> children = new HashSet<>();
    private volatile boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void check() {
        if (cancelled) throw new Cancelled();
    }

    public void cancel() {
        Set<Call> active;
        Set<Cancel> kids;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            active = new HashSet<>(calls);
            calls.clear();
            kids = new HashSet<>(children);
            children.clear();
            notifyAll();
        }
        for (Call c : active) c.cancel();
        for (Cancel k : kids) k.cancel();
    }

    public Cancel child() {
        Cancel kid = new Cancel();
        synchronized (this) {
            if (!cancelled) {
                children.add(kid);
                return kid;
            }
        }
        kid.cancel();
        return kid;
    }

    public synchronized void register(Call call) {
        if (cancelled) call.cancel();
        else calls.add(call);
    }

    public synchronized void unregister(Call call) {
        calls.remove(call);
    }

    public synchronized void sleep(long ms) {
        long end = System.currentTimeMillis() + ms;
        long left = ms;
        while (!cancelled && left > 0) {
            try {
                wait(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelled = true;
            }
            left = end - System.currentTimeMillis();
        }
        check();
    }
}
