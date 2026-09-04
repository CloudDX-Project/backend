package com.travel.accommodation.repository;

import com.travel.accommodation.data.AccommodationEnrichmentData;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

/**
 * accommodation_enrichment는 Python 수집기가 관리하는 테이블이므로
 * JPA Entity 대신 읽기 전용 SQL repository로 접근한다.
 */
@Repository
public class AccommodationEnrichmentRepository {

    private static final String FIND_BY_IDS_AND_PROVIDER = """
            SELECT
                accommodation_id,
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
            WHERE provider = :provider
              AND accommodation_id IN (:accommodationIds)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccommodationEnrichmentRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AccommodationEnrichmentData> findByAccommodationIdInAndProvider(
            Collection<Long> accommodationIds,
            String provider
    ) {
        if (accommodationIds == null || accommodationIds.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accommodationIds", accommodationIds)
                .addValue("provider", provider);

        return jdbcTemplate.query(
                FIND_BY_IDS_AND_PROVIDER,
                params,
                ROW_MAPPER
        );
    }

    private static final RowMapper<AccommodationEnrichmentData> ROW_MAPPER =
            (rs, rowNum) -> new AccommodationEnrichmentData(
                    nullableLong(rs, "accommodation_id"),
                    rs.getString("provider"),
                    rs.getString("provider_id"),
                    rs.getString("provider_name"),
                    rs.getString("provider_url"),
                    nullableDouble(rs, "rating"),
                    nullableDouble(rs, "rating_scale"),
                    nullableInteger(rs, "review_count"),
                    nullableInteger(rs, "visitor_review_count"),
                    nullableInteger(rs, "blog_review_count"),
                    nullableLong(rs, "price"),
                    nullableLong(rs, "price_avg"),
                    rs.getString("price_text"),
                    rs.getString("provider_address"),
                    rs.getString("representative_image_url"),
                    nullableDouble(rs, "provider_latitude"),
                    nullableDouble(rs, "provider_longitude"),
                    rs.getString("check_in_time"),
                    rs.getString("check_out_time"),
                    nullableInteger(rs, "star_count"),
                    rs.getString("description"),
                    rs.getString("phone_number"),
                    rs.getString("scrape_status"),
                    nullableDateTime(rs, "collected_at"),
                    nullableDateTime(rs, "updated_at")
            );

    private static Integer nullableInteger(
            ResultSet rs,
            String column
    ) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static Long nullableLong(
            ResultSet rs,
            String column
    ) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }

    private static Double nullableDouble(
            ResultSet rs,
            String column
    ) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.doubleValue();
    }

    private static java.time.LocalDateTime nullableDateTime(
            ResultSet rs,
            String column
    ) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
