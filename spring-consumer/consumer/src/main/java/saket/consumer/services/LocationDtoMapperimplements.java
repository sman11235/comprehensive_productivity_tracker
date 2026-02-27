package saket.consumer.services;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;


import saket.consumer.domain.LocationDTO;
import saket.consumer.domain.LocationLog;

@Component
public class LocationDtoMapperimplements implements ILocationDtoMapper {

    @Override
    public LocationLog toEntity(LocationDTO dto) {
        if (dto == null) throw new IllegalArgumentException("dto is null");
        if (dto.loc() == null || dto.loc().coord() == null || dto.loc().coord().length < 2 || !dto.loc().type().equals("Point")) {
            throw new IllegalArgumentException("loc.coord must be [lon, lat] and of type Point");
        }

        double lon = dto.loc().coord()[0];
        double lat = dto.loc().coord()[1];

        Point coord = PointUtil.wgs84FromLatLon(lat, lon);

        return LocationLog.builder()
            .deviceId(dto.deviceId())
            .timestamp(dto.timestamp())
            .loc(coord)
            .build();
    }
}

