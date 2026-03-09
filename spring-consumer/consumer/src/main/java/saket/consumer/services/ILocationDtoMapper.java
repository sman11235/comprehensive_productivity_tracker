package saket.consumer.services;

import saket.consumer.domain.LocationDTO;
import saket.consumer.domain.LocationLog;

/**
 * An interface that handles behavior related to mapping location dtos to the database entity counter parts.
 * @param dto the dto received.
 * @return a fully constructed DB entity. 
 */
public interface ILocationDtoMapper{
    LocationLog toEntity(LocationDTO dto);
}
