package com.travel.global.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RoutingCacheConfigTest {
    @Test void defaultSimpleCacheRegistersDrivingRouteCache() {
        assertThat(new RedisConfig().simpleCacheManager().getCache("kakaoDrivingRoute")).isNotNull();
    }
}
