package com.travel.accommodation.repository;

import com.travel.accommodation.data.AccommodationEnrichmentData;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * accommodation_enrichment는 Python 수집기가 관리하는 테이블이므로
 * JPA Entity 대신 읽기 전용 JDBC Repository로 접근한다.
 */
@Repository
public class AccommodationEnrichmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT
                accommodation_id,
                province,
                city,
                town,
                provider,
                provider_id,
                provider_name,
                provider_url,
                rating,
                rating_scale,
                review_count,
                visitor_review_count,
                blog_review_count,
                price,
                price_avg,
                price_text,
                provider_address,
                representative_image_url,
                provider_latitude,
                provider_longitude,
                check_in_time,
                check_out_time,
                star_count,
                description,
                phone_number,
                scrape_status,
                collected_at,
                updated_at
            FROM accommodation_enrichment
            """;

    /**
     * 읍면동을 입력하지 않은 경우.
     *
     * 예:
     * 제주특별자치도 + 제주시 전체 숙소 조회
     */
    private static final String FIND_BY_CITY =
            SELECT_COLUMNS + """
            WHERE provider = :provider
              AND province = :province
              AND city = :city
            ORDER BY provider_name
            """;

    /**
     * 읍면동까지 입력한 경우.
     *
     * 예:
     * 제주특별자치도 + 제주시 + 애월읍
     */
    private static final String FIND_BY_TOWN =
            SELECT_COLUMNS + """
            WHERE provider = :provider
              AND province = :province
              AND city = :city
              AND town = :town
            ORDER BY provider_name
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccommodationEnrichmentRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AccommodationEnrichmentData> findByRegionAndProvider(
            String province,
            String city,
            String town,
            String provider
    ) {

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("province", province)
                        .addValue("city", city)
                        .addValue("provider", provider);

        /*
         * town이 존재하면 읍면동까지 검색.
         */
        if (town != null && !town.isBlank()) {

            params.addValue("town", town);

            return jdbcTemplate.query(
                    FIND_BY_TOWN,
                    params,
                    ROW_MAPPER
            );
        }

        /*
         * town이 null 또는 빈 문자열이면
         * 시 단위 전체 검색.
         */
        return jdbcTemplate.query(
                FIND_BY_CITY,
                params,
                ROW_MAPPER
        );
    }

    private static final RowMapper<AccommodationEnrichmentData> ROW_MAPPER =
            (rs, rowNum) ->
                    new AccommodationEnrichmentData(

                            nullableLong(
                                    rs,
                                    "accommodation_id"
                            ),

                            rs.getString(
                                    "province"
                            ),

                            rs.getString(
                                    "city"
                            ),

                            rs.getString(
                                    "town"
                            ),

                            rs.getString(
                                    "provider"
                            ),

                            rs.getString(
                                    "provider_id"
                            ),

                            rs.getString(
                                    "provider_name"
                            ),

                            rs.getString(
                                    "provider_url"
                            ),

                            nullableDouble(
                                    rs,
                                    "rating"
                            ),

                            nullableDouble(
                                    rs,
                                    "rating_scale"
                            ),

                            nullableInteger(
                                    rs,
                                    "review_count"
                            ),

                            nullableInteger(
                                    rs,
                                    "visitor_review_count"
                            ),

                            nullableInteger(
                                    rs,
                                    "blog_review_count"
                            ),

                            nullableLong(
                                    rs,
                                    "price"
                            ),

                            nullableLong(
                                    rs,
                                    "price_avg"
                            ),

                            rs.getString(
                                    "price_text"
                            ),

                            rs.getString(
                                    "provider_address"
                            ),

                            rs.getString(
                                    "representative_image_url"
                            ),

                            nullableDouble(
                                    rs,
                                    "provider_latitude"
                            ),

                            nullableDouble(
                                    rs,
                                    "provider_longitude"
                            ),

                            rs.getString(
                                    "check_in_time"
                            ),

                            rs.getString(
                                    "check_out_time"
                            ),

                            nullableInteger(
                                    rs,
                                    "star_count"
                            ),

                            rs.getString(
                                    "description"
                            ),

                            rs.getString(
                                    "phone_number"
                            ),

                            rs.getString(
                                    "scrape_status"
                            ),

                            nullableDateTime(
                                    rs,
                                    "collected_at"
                            ),

                            nullableDateTime(
                                    rs,
                                    "updated_at"
                            )
                    );

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

    private static java.time.LocalDateTime nullableDateTime(
            ResultSet rs,
            String column
    ) throws SQLException {

        Timestamp value =
                rs.getTimestamp(column);

        return value == null
                ? null
                : value.toLocalDateTime();
    }
}