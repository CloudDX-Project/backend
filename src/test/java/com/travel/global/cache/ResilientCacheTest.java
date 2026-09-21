package com.travel.global.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientCacheTest {
    @Test
    void usesSharedCacheAndKeepsLocalCopyOnWrite() {
        Cache shared = new ConcurrentMapCache("route");
        Cache local = new ConcurrentMapCache("route");
        ResilientCache cache = new ResilientCache(shared, local, new SimpleMeterRegistry());

        cache.put("key", "value");

        assertThat(cache.getName()).isEqualTo("route");
        assertThat(cache.getNativeCache()).isSameAs(shared.getNativeCache());
        assertThat(cache.get("key", String.class)).isEqualTo("value");
        assertThat(local.get("key", String.class)).isEqualTo("value");
    }

    @Test
    void fallsBackToLocalCacheWhenSharedCacheFails() {
        Cache shared = mock(Cache.class);
        Cache local = new ConcurrentMapCache("route");
        when(shared.getName()).thenReturn("route");
        when(shared.get("key")).thenThrow(new IllegalStateException("redis unavailable"));
        local.put("key", "fallback");
        ResilientCache cache = new ResilientCache(shared, local, new SimpleMeterRegistry());

        assertThat(cache.get("key", String.class)).isEqualTo("fallback");
        assertThat(cache.get("loaded", () -> "new-value")).isEqualTo("new-value");
        assertThat(local.get("loaded", String.class)).isEqualTo("new-value");
    }

    @Test
    void evictAndClearRemainAvailableWhenSharedCacheFails() {
        Cache shared = mock(Cache.class);
        Cache local = new ConcurrentMapCache("route");
        when(shared.getName()).thenReturn("route");
        doThrow(new IllegalStateException("redis unavailable")).when(shared).evict("key");
        doThrow(new IllegalStateException("redis unavailable")).when(shared).clear();
        local.put("key", "value");
        ResilientCache cache = new ResilientCache(shared, local, new SimpleMeterRegistry());

        cache.evict("key");
        assertThat(local.get("key")).isNull();
        cache.clear();
    }
}
