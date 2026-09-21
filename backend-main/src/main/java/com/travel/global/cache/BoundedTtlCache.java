package com.travel.global.cache;

import org.springframework.cache.support.AbstractValueAdaptingCache;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/** Bounded local cache, also used as a short-lived Redis outage fallback. */
public class BoundedTtlCache extends AbstractValueAdaptingCache {
    private final String name;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Object, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Object[] locks = new Object[64];

    public BoundedTtlCache(String name, Duration ttl, int maxEntries, Clock clock) {
        super(false);
        if (ttl.isNegative() || ttl.isZero() || maxEntries < 1) throw new IllegalArgumentException("Invalid cache bounds");
        this.name = name;
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
        java.util.Arrays.setAll(locks, i -> new Object());
    }

    @Override public String getName() { return name; }
    @Override public Object getNativeCache() { return this; }
    @Override protected synchronized Object lookup(Object key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expiresAt() <= clock.millis()) {
            entries.remove(key);
            return null;
        }
        return entry.value();
    }
    @Override public synchronized void put(Object key, Object value) {
        if (value == null) return;
        entries.put(key, new Entry(value, clock.millis() + ttl.toMillis()));
        while (entries.size() > maxEntries) entries.remove(entries.keySet().iterator().next());
    }
    @Override public synchronized ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper found = get(key);
        if (found == null) put(key, value);
        return found;
    }
    @Override public <T> T get(Object key, Callable<T> loader) {
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            ValueWrapper hit = get(key);
            if (hit != null) return cast(hit.get());
            try {
                T loaded = loader.call();
                put(key, loaded);
                return loaded;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, loader, e);
            }
        }
    }
    @SuppressWarnings("unchecked") private static <T> T cast(Object value) { return (T) value; }
    @Override public synchronized void evict(Object key) { entries.remove(key); }
    @Override public synchronized void clear() { entries.clear(); }
    private record Entry(Object value, long expiresAt) {}
}
