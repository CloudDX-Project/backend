package com.travel.global.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {

        /*
         * 중요:
         *
         * Spring Boot DevTools의 RestartClassLoader를
         * 사용하도록 명시한다.
         *
         * 기본 JdkSerializationRedisSerializer()를 사용하면
         * DevTools 환경에서 캐시 조회 시 ClassLoader 충돌이
         * 발생할 수 있다.
         */
        ClassLoader classLoader =
                RedisConfig.class.getClassLoader();

        JdkSerializationRedisSerializer valueSerializer =
                new JdkSerializationRedisSerializer(
                        classLoader
                );

        /*
         * Redis 기본 캐시 설정
         *
         * Key   -> String
         * Value -> Java Serialization
         */
        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration
                        .defaultCacheConfig()

                        /*
                         * Redis Key 직렬화
                         */
                        .serializeKeysWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(
                                                new StringRedisSerializer()
                                        )
                        )

                        /*
                         * Redis Value 직렬화
                         *
                         * Weather 결과:
                         * Map<LocalDate, WeatherCondition>
                         *
                         * 현재 구조에서는 Java 직렬화로
                         * 간단하게 처리
                         */
                        .serializeValuesWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(
                                                valueSerializer
                                        )
                        )

                        /*
                         * null 값은 Redis에 저장하지 않음
                         */
                        .disableCachingNullValues();


        /*
         * 캐시별 설정
         */
        Map<String, RedisCacheConfiguration> cacheConfigurations =
                new HashMap<>();


        /*
         * 기상청 단기예보
         *
         * TTL: 2시간
         */
        cacheConfigurations.put(
                "weatherShort",
                defaultConfig.entryTtl(
                        Duration.ofHours(2)
                )
        );


        /*
         * 기상청 중기예보
         *
         * TTL: 6시간
         */
        cacheConfigurations.put(
                "weatherMid",
                defaultConfig.entryTtl(
                        Duration.ofHours(6)
                )
        );

        cacheConfigurations.put(

                "flightSchedule",

                defaultConfig.entryTtl(
                        Duration.ofHours(12)
                )
        );


        /*
         * Redis CacheManager 생성
         */
        return RedisCacheManager
                .builder(connectionFactory)

                /*
                 * 별도 설정이 없는 캐시는
                 * 기본 1시간
                 */
                .cacheDefaults(
                        defaultConfig.entryTtl(
                                Duration.ofHours(1)
                        )
                )

                /*
                 * weatherShort / weatherMid
                 * 개별 TTL 적용
                 */
                .withInitialCacheConfigurations(
                        cacheConfigurations
                )

                .build();
    }
}