package com.travel.global.config;

import com.travel.cafe.CafeCandidateService;
import com.travel.flight.dto.FlightCandidate;
import com.travel.global.cache.BoundedTtlCache;
import com.travel.global.cache.ResilientCache;
import com.travel.routing.dto.DrivingRouteResult;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.weather.WeatherCondition;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

@Configuration
@EnableCaching
public class RedisConfig {
    @Bean
    public CacheManager cacheManager(ObjectProvider<RedisConnectionFactory> connections, JsonMapper mapper,
                                      Environment env, MeterRegistry meters) {
        String provider = env.getProperty("app.cache.provider", "simple");
        if (!Set.of("redis", "simple").contains(provider)) throw new IllegalArgumentException("CACHE_PROVIDER must be redis or simple");
        String namespace = env.getProperty("app.cache.namespace", "travel:local:cache:v3");
        if (namespace.isBlank()) throw new IllegalArgumentException("CACHE_NAMESPACE must not be blank");
        var types = mapper.getTypeFactory();
        Map<String, JavaType> valueTypes = Map.of(
                "weatherShort", types.constructMapType(HashMap.class, LocalDate.class, WeatherCondition.class),
                "weatherMid", types.constructMapType(HashMap.class, LocalDate.class, WeatherCondition.class),
                "flightSchedule", types.constructCollectionType(ArrayList.class, FlightCandidate.class),
                "kakaoDrivingRoute", types.constructType(DrivingRouteResult.class),
                "cafeCandidates", types.constructType(CafeCandidateService.CafeCandidatePool.class),
                "tripPlanAttractionCandidates", types.constructCollectionType(ArrayList.class, TripPlanCandidatePool.AttractionCandidate.class),
                "tripPlanRestaurantCandidates", types.constructCollectionType(ArrayList.class, TripPlanCandidatePool.RestaurantCandidate.class),
                "tripPlanCafeCandidates", types.constructCollectionType(ArrayList.class, TripPlanCandidatePool.CafeCandidate.class));
        Map<String, Duration> defaults = Map.of(
                "weatherShort", Duration.ofHours(2), "weatherMid", Duration.ofHours(6),
                "flightSchedule", Duration.ofHours(12), "kakaoDrivingRoute", Duration.ofMinutes(15),
                "cafeCandidates", Duration.ofMinutes(30), "tripPlanAttractionCandidates", Duration.ofMinutes(30),
                "tripPlanRestaurantCandidates", Duration.ofMinutes(30), "tripPlanCafeCandidates", Duration.ofMinutes(30));
        Map<String, Duration> ttls = new HashMap<>();
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        valueTypes.forEach((name, type) -> {
            Duration ttl = env.getProperty("app.cache.ttl." + name, Duration.class, defaults.get(name));
            if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Invalid TTL for " + name);
            ttls.put(name, ttl);
            configs.put(name, RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl)
                    .computePrefixWith(cacheName -> namespace + ":" + cacheName + "::")
                    .disableCachingNullValues()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                            new JacksonJsonRedisSerializer<Object>(mapper, type))));
        });
        RedisCacheManager shared = null;
        if ("redis".equals(provider)) {
            shared = RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(
                            connections.getObject(), BatchStrategies.scan(500)))
                    .withInitialCacheConfigurations(configs).disableCreateOnMissingCache().build();
            shared.initializeCaches();
        }
        int maxEntries = env.getProperty("app.cache.local-max-entries", Integer.class, 256);
        List<Cache> caches = new ArrayList<>();
        for (String name : valueTypes.keySet()) {
            Duration ttl = ttls.get(name);
            Duration localTtl = shared == null || ttl.compareTo(Duration.ofSeconds(60)) < 0 ? ttl : Duration.ofSeconds(60);
            var local = new BoundedTtlCache(name, localTtl, maxEntries, Clock.systemUTC());
            caches.add(new ResilientCache(shared == null ? null : shared.getCache(name), local, meters));
        }
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(caches);
        return manager;
    }
}
