package com.travel.attraction.repository;

import com.travel.attraction.data.TouristAttractionData;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class TouristAttractionRepository {

    private static final String PROVIDER = "VISIT_JEJU";
    private static final String CATEGORY_CODE = "c1";

    private static final String SELECT_COLUMNS = """
            SELECT
                id,
                provider,
                provider_id,
                name,
                category_code,
                category_name,
                region1_code,
                region1_name,
                region2_code,
                region2_name,
                address,
                road_address,
                postcode,
                latitude,
                longitude,
                tags,
                all_tags,
                introduction,
                phone_number,
                photo_id,
                representative_image_url,
                thumbnail_image_url,
                collected_at,
                updated_at
            FROM tourist_attraction
            """;
    private static final String FIND_ALL_RECOMMENDABLE =
            SELECT_COLUMNS + """
        WHERE provider = :provider
          AND category_code = :categoryCode
          AND latitude IS NOT NULL
          AND longitude IS NOT NULL
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TouristAttractionRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TouristAttractionData> search(
            String region1Name,
            String region2Name,
            String keyword,
            int limit
    ) {

        StringBuilder sql =
                new StringBuilder(SELECT_COLUMNS);

        sql.append("""
                WHERE provider = :provider
                  AND category_code = :categoryCode
                """);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("provider", PROVIDER)
                        .addValue("categoryCode", CATEGORY_CODE)
                        .addValue("limit", limit);

        /*
         * 제주시 / 서귀포시 / 섬 속의 섬
         */
        if (hasText(region1Name)) {

            sql.append("""
                      AND region1_name = :region1Name
                    """);

            params.addValue(
                    "region1Name",
                    region1Name.trim()
            );
        }

        /*
         * 애월 / 성산 / 표선 / 제주시내 등
         */
        if (hasText(region2Name)) {

            sql.append("""
                      AND region2_name = :region2Name
                    """);

            params.addValue(
                    "region2Name",
                    region2Name.trim()
            );
        }

        /*
         * 관광지 이름 / 태그 / 소개 / 주소 통합 검색
         */
        if (hasText(keyword)) {

            sql.append("""
                      AND (
                             name LIKE CONCAT('%', :keyword, '%')
                          OR tags LIKE CONCAT('%', :keyword, '%')
                          OR all_tags LIKE CONCAT('%', :keyword, '%')
                          OR introduction LIKE CONCAT('%', :keyword, '%')
                          OR address LIKE CONCAT('%', :keyword, '%')
                          OR road_address LIKE CONCAT('%', :keyword, '%')
                      )
                    """);

            params.addValue(
                    "keyword",
                    keyword.trim()
            );
        }

        /*
         * 이미지 있는 관광지를 우선 노출하고
         * 이후 이름순.
         */
        sql.append("""
                ORDER BY
                    CASE
                        WHEN representative_image_url IS NULL
                          OR representative_image_url = ''
                        THEN 1
                        ELSE 0
                    END,
                    name ASC
                LIMIT :limit
                """);

        return jdbcTemplate.query(
                sql.toString(),
                params,
                ROW_MAPPER
        );
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private static final RowMapper<TouristAttractionData>
            ROW_MAPPER =
            (rs, rowNum) ->
                    new TouristAttractionData(

                            nullableLong(
                                    rs,
                                    "id"
                            ),

                            rs.getString(
                                    "provider"
                            ),

                            rs.getString(
                                    "provider_id"
                            ),

                            rs.getString(
                                    "name"
                            ),

                            rs.getString(
                                    "category_code"
                            ),

                            rs.getString(
                                    "category_name"
                            ),

                            rs.getString(
                                    "region1_code"
                            ),

                            rs.getString(
                                    "region1_name"
                            ),

                            rs.getString(
                                    "region2_code"
                            ),

                            rs.getString(
                                    "region2_name"
                            ),

                            rs.getString(
                                    "address"
                            ),

                            rs.getString(
                                    "road_address"
                            ),

                            rs.getString(
                                    "postcode"
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
                                    "tags"
                            ),

                            rs.getString(
                                    "all_tags"
                            ),

                            rs.getString(
                                    "introduction"
                            ),

                            rs.getString(
                                    "phone_number"
                            ),

                            rs.getString(
                                    "photo_id"
                            ),

                            rs.getString(
                                    "representative_image_url"
                            ),

                            rs.getString(
                                    "thumbnail_image_url"
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
    public List<TouristAttractionData> findAllRecommendable() {

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(
                                "provider",
                                "VISIT_JEJU"
                        )
                        .addValue(
                                "categoryCode",
                                "c1"
                        );

        return jdbcTemplate.query(
                FIND_ALL_RECOMMENDABLE,
                params,
                ROW_MAPPER
        );
    }
}