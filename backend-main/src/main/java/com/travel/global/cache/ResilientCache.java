package com.travel.global.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/** Catch cache failures only. A failed external API is NEVER retried by this wrapper. */
public class ResilientCache implements Cache {
    private static final Logger log = LoggerFactory.getLogger(ResilientCache.class);
    private final Cache shared;
    private final Cache local;
    private final Counter hits;
    private final Counter misses;
    private final Counter failures;
    private final Counter loads;
    private final Object[] locks = new Object[64];
    private final AtomicLong retrySharedAt = new AtomicLong();

    public ResilientCache(Cache shared, Cache local, MeterRegistry meters) {
        this.shared = shared;
        this.local = local;
        hits = meters.counter("travel.cache.requests", "cache", getName(), "result", "hit");
        misses = meters.counter("travel.cache.requests", "cache", getName(), "result", "miss");
        failures = meters.counter("travel.cache.errors", "cache", getName());
        loads = meters.counter("travel.cache.loads", "cache", getName());
        java.util.Arrays.setAll(locks, i -> new Object());
    }
    @Override public String getName() { return local.getName(); }
    @Override public Object getNativeCache() { return shared == null ? local.getNativeCache() : shared.getNativeCache(); }
    @Override public ValueWrapper get(Object key) {
        ValueWrapper hit;
        if (useShared()) {
            try { hit = shared.get(key); }
            catch (RuntimeException e) { unavailable(e); hit = local.get(key); }
        } else { hit = local.get(key); }
        if (hit == null) misses.increment(); else hits.increment();
        return hit;
    }
    @Override public <T> T get(Object key, Class<T> type) {
        var value = get(key);
        return value == null ? null : type.cast(value.get());
    }
    @Override public <T> T get(Object key, Callable<T> loader) {
        // Per-key striped synchronization within this JVM, not a distributed lock.
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            var cached = get(key);
            if (cached != null) return cast(cached.get());
            final T value;
            try { loads.increment(); value = loader.call(); }
            catch (Exception e) { throw new ValueRetrievalException(key, loader, e); }
            put(key, value);
            return value;
        }
    }
    @Override public void put(Object key, Object value) {
        if (value == null) return;
        local.put(key, value);
        if (useShared()) {
            try { shared.put(key, value); }
            catch (RuntimeException e) { unavailable(e); }
        }
    }
    @Override public ValueWrapper putIfAbsent(Object key, Object value) {
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            if (useShared()) {
                try {
                    ValueWrapper existing = shared.putIfAbsent(key, value);
                    if (existing == null) local.put(key, value);
                    return existing;
                } catch (RuntimeException e) { unavailable(e); }
            }
            return local.putIfAbsent(key, value);
        }
    }
    @Override public void evict(Object key) {
        local.evict(key);
        if (shared != null) {
            try { shared.evict(key); }
            catch (RuntimeException e) { unavailable(e); }
        }
    }
    @Override public void clear() {
        local.clear();
        if (shared != null) {
            try { shared.clear(); }
            catch (RuntimeException e) { unavailable(e); }
        }
    }
    private boolean useShared() { return shared != null && System.currentTimeMillis() >= retrySharedAt.get(); }
    private void unavailable(RuntimeException e) {
        failures.increment();
        long now = System.currentTimeMillis();
        long prior = retrySharedAt.getAndSet(now + 5000);
        if (prior <= now) log.warn("Cache {} unavailable; local TTL fallback for 5s ({})", getName(), e.getClass().getSimpleName());
    }
    @SuppressWarnings("unchecked") private static <T> T cast(Object value) { return (T) value; }
}
