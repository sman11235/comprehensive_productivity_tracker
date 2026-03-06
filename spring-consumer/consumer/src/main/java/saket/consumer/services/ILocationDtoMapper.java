package saket.consumer.services;

import saket.consumer.domain.LocationDTO;
import saket.consumer.domain.LocationLog;

public interface ILocationDtoMapper{
    LocationLog toEntity(LocationDTO dto);
}
