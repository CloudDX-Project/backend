package com.travel.global.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingCacheConfigTest {
    @Test void localFallbackIsBounded() {
        var cache = new com.travel.global.cache.BoundedTtlCache(
                "kakaoDrivingRoute", Duration.ofMinutes(1), 2, Clock.systemUTC());

        cache.put("route-1", "first");
        cache.put("route-2", "second");
        cache.put("route-3", "third");

        assertThat(cache.get("route-1")).isNull();
        assertThat(cache.get("route-2", String.class)).isEqualTo("second");
        assertThat(cache.get("route-3", String.class)).isEqualTo("third");
    }
}
