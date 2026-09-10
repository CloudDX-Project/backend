package com.travel.restaurant.repository;

import com.travel.restaurant.data.RestaurantData;
import com.travel.restaurant.data.RestaurantMenuData;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RestaurantRepository {

    private static final String FIND_ALL_LOCATED_SQL =
            """
            SELECT
                id,
                kakao_place_id,
                restaurant_name,
                category,
                phone,
                address,
                road_address,
                latitude,
                longitude,
                place_url,
                rating,
                review_count,
                business_hours,
                summary,
                tags,
                facilities,
                last_scraped_at,
                last_scrape_status,
                created_at,
                updated_at
            FROM restaurants
            WHERE latitude IS NOT NULL
              AND longitude IS NOT NULL
            """;

    private static final String FIND_MENUS_SQL =
            """
            SELECT
                id,
                restaurant_id,
                menu_name,
                description,
                image_url,
                current_price,
                is_recommended,
                menu_tags,
                last_seen_at,
                created_at,
                updated_at
            FROM restaurant_menus
            WHERE restaurant_id IN (:restaurantIds)
              AND is_active = 1
            ORDER BY
                restaurant_id ASC,
                is_recommended DESC,
                id ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RestaurantRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
                jdbcTemplate;
    }

    public List<RestaurantData> findAllLocated() {

        return jdbcTemplate.query(
                FIND_ALL_LOCATED_SQL,
                Map.of(),
                RESTAURANT_ROW_MAPPER
        );
    }

    public Map<Long, List<RestaurantMenuData>>
    findMenusByRestaurantIds(
            Collection<Long> restaurantIds
    ) {

        if (restaurantIds == null
                || restaurantIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(
                                "restaurantIds",
                                restaurantIds
                        );

        List<RestaurantMenuData> menus =
                jdbcTemplate.query(
                        FIND_MENUS_SQL,
                        params,
                        MENU_ROW_MAPPER
                );

        Map<Long, List<RestaurantMenuData>> result =
                new HashMap<>();

        for (RestaurantMenuData menu : menus) {

            result
                    .computeIfAbsent(
                            menu.restaurantId(),
                            key ->
                                    new java.util.ArrayList<>()
                    )
                    .add(menu);
        }

        return result;
    }

    private static final RowMapper<RestaurantData>
            RESTAURANT_ROW_MAPPER =
            (rs, rowNum) ->
                    new RestaurantData(

                            nullableLong(
                                    rs,
                                    "id"
                            ),

                            rs.getString(
                                    "kakao_place_id"
                            ),

                            rs.getString(
                                    "restaurant_name"
                            ),

                            rs.getString(
                                    "category"
                            ),

                            rs.getString(
                                    "phone"
                            ),

                            rs.getString(
                                    "address"
                            ),

                            rs.getString(
                                    "road_address"
                            ),

                            nullableDouble(
                                    rs,
                                    "latitude"
                            ),

                            nullableDouble(
                                    rs,
                                    "longitude"
                            ),

                            rs.getString(
                                    "place_url"
                            ),

                            nullableDouble(
                                    rs,
                                    "rating"
                            ),

                            nullableInteger(
                                    rs,
                                    "review_count"
                            ),

                            rs.getString(
                                    "business_hours"
                            ),

                            rs.getString(
                                    "summary"
                            ),

                            rs.getString(
                                    "tags"
                            ),

                            rs.getString(
                                    "facilities"
                            ),

                            nullableDateTime(
                                    rs,
                                    "last_scraped_at"
                            ),

                            rs.getString(
                                    "last_scrape_status"
                            ),

                            nullableDateTime(
                                    rs,
                                    "created_at"
                            ),

                            nullableDateTime(
                                    rs,
                                    "updated_at"
                            )
                    );

    private static final RowMapper<RestaurantMenuData>
            MENU_ROW_MAPPER =
            (rs, rowNum) ->
                    new RestaurantMenuData(

                            nullableLong(
                                    rs,
                                    "id"
                            ),

                            nullableLong(
                                    rs,
                                    "restaurant_id"
                            ),

                            rs.getString(
                                    "menu_name"
                            ),

                            rs.getString(
                                    "description"
                            ),

                            rs.getString(
                                    "image_url"
                            ),

                            nullableInteger(
                                    rs,
                                    "current_price"
                            ),

                            rs.getBoolean(
                                    "is_recommended"
                            ),

                            rs.getString(
                                    "menu_tags"
                            ),

                            nullableDateTime(
                                    rs,
                                    "last_seen_at"
                            ),

                            nullableDateTime(
                                    rs,
                                    "created_at"
                            ),

                            nullableDateTime(
                                    rs,
                                    "updated_at"
                            )
                    );

    private static Long nullableLong(
            ResultSet rs,
            String column
    ) throws SQLException {

        Number value =
                (Number) rs.getObject(column);

        return value == null
                ? null
                : value.longValue();
    }

    private static Integer nullableInteger(
            ResultSet rs,
            String column
    ) throws SQLException {

        Number value =
                (Number) rs.getObject(column);

        return value == null
                ? null
                : value.intValue();
    }

    private static Double nullableDouble(
            ResultSet rs,
            String column
    ) throws SQLException {

        Number value =
                (Number) rs.getObject(column);

        return value == null
                ? null
                : value.doubleValue();
    }

    private static java.time.LocalDateTime
    nullableDateTime(
            ResultSet rs,
            String column
    ) throws SQLException {

        Timestamp timestamp =
                rs.getTimestamp(column);

        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}