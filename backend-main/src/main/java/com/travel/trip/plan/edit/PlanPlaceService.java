package com.travel.trip.plan.edit;

import com.travel.attraction.repository.TouristAttractionRepository;
import com.travel.cafe.repository.CafeRepository;
import com.travel.restaurant.repository.RestaurantRepository;
import com.travel.trip.plan.type.TripPlanItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanPlaceService {
    private static final String ATTRACTION_SEARCH_SQL = """
            SELECT id, name, category_name AS category, latitude, longitude, address
            FROM tourist_attraction
            WHERE latitude BETWEEN -90 AND 90
              AND longitude BETWEEN -180 AND 180
              AND LOCATE(:keyword, name) > 0
            ORDER BY name, id
            LIMIT 20
            """;

    private static final String RESTAURANT_SEARCH_SQL = """
            SELECT id, restaurant_name AS name, category, latitude, longitude, address
            FROM restaurants
            WHERE latitude BETWEEN -90 AND 90
              AND longitude BETWEEN -180 AND 180
              AND LOCATE(:keyword, restaurant_name) > 0
            ORDER BY restaurant_name, id
            LIMIT 20
            """;

    private static final String CAFE_SEARCH_SQL = """
            SELECT id, cafe_name AS name, category, latitude, longitude, address
            FROM cafes
            WHERE latitude BETWEEN -90 AND 90
              AND longitude BETWEEN -180 AND 180
              AND LOCATE(:keyword, cafe_name) > 0
            ORDER BY cafe_name, id
            LIMIT 20
            """;

    private final TouristAttractionRepository attractions;
    private final RestaurantRepository restaurants;
    private final CafeRepository cafes;
    private final NamedParameterJdbcTemplate jdbc;

    public PlanPlace resolve(PlanEditRequest.Place selection) {
        PlanPlace place = switch (selection.type()) {
            case ATTRACTION -> attractions.findById(selection.placeId()).map(p ->
                    new PlanPlace(p.id(), selection.type(), p.name(), p.categoryName(),
                            p.latitude(), p.longitude(), p.address())).orElse(null);
            case RESTAURANT -> {
                var p = restaurants.findById(selection.placeId());
                yield p == null ? null : new PlanPlace(p.id(), selection.type(), p.restaurantName(),
                        p.category(), p.latitude(), p.longitude(), p.address());
            }
            case CAFE -> {
                var p = cafes.findById(selection.placeId());
                yield p == null ? null : new PlanPlace(p.id(), selection.type(), p.cafeName(),
                        p.category(), p.latitude(), p.longitude(), p.address());
            }
            default -> throw PlanEditException.invalid("관광지·식당·카페만 추가하거나 교체할 수 있습니다.");
        };
        if (place == null || !validCoordinates(place.latitude(), place.longitude())) {
            throw PlanEditException.invalid("선택한 장소가 없거나 유효한 좌표가 없습니다.");
        }
        return place;
    }

    /** Bounded DB-only search. No Bedrock, geocoding or Kakao calls. */
    public List<PlanPlace> search(TripPlanItemType type, String query) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() < 2 || keyword.length() > 80) {
            throw PlanEditException.invalid("장소 이름은 2~80자로 검색해주세요.");
        }
        String sql = switch (type) {
            case ATTRACTION -> ATTRACTION_SEARCH_SQL;
            case RESTAURANT -> RESTAURANT_SEARCH_SQL;
            case CAFE -> CAFE_SEARCH_SQL;
            default -> throw PlanEditException.invalid("관광지·식당·카페만 검색할 수 있습니다.");
        };
        return jdbc.query(sql,
                Map.of("keyword", keyword), (rs, row) -> new PlanPlace(rs.getLong("id"), type,
                        rs.getString("name"), rs.getString("category"), rs.getDouble("latitude"),
                        rs.getDouble("longitude"), rs.getString("address")));
    }

    static boolean validCoordinates(Double lat, Double lon) {
        return lat != null && lon != null && Double.isFinite(lat) && Double.isFinite(lon)
                && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }
}
