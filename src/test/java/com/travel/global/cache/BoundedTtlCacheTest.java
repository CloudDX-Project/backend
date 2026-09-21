package com.travel.global.cache;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedTtlCacheTest {
    @Test
    void expiresEntriesAndEvictsLeastRecentlyUsedEntry() {
        MutableClock clock = new MutableClock();
        BoundedTtlCache cache = new BoundedTtlCache("route", Duration.ofSeconds(5), 2, clock);

        cache.put("a", "A");
        cache.put("b", "B");
        assertThat(cache.get("a", String.class)).isEqualTo("A");
        cache.put("c", "C");
        assertThat(cache.get("b")).isNull();

        clock.advance(Duration.ofSeconds(6));
        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("c")).isNull();
    }

    @Test
    void loadsOnceAndSupportsMutationOperations() {
        BoundedTtlCache cache = new BoundedTtlCache(
                "route", Duration.ofMinutes(1), 2, Clock.systemUTC());
        assertThat(cache.getName()).isEqualTo("route");
        assertThat(cache.getNativeCache()).isSameAs(cache);
        assertThat(cache.get("key", () -> "value")).isEqualTo("value");
        assertThat(cache.get("key", () -> "other")).isEqualTo("value");
        assertThat(cache.putIfAbsent("key", "other").get()).isEqualTo("value");
        cache.evict("key");
        assertThat(cache.get("key")).isNull();
        cache.put("key", "value");
        cache.clear();
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void rejectsInvalidBoundsAndWrapsLoaderFailure() {
        assertThatThrownBy(() -> new BoundedTtlCache(
                "bad", Duration.ZERO, 0, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        BoundedTtlCache cache = new BoundedTtlCache(
                "route", Duration.ofMinutes(1), 2, Clock.systemUTC());
        assertThatThrownBy(() -> cache.get("key", () -> {
            throw new IllegalStateException("failure");
        })).isInstanceOf(org.springframework.cache.Cache.ValueRetrievalException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-20T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
