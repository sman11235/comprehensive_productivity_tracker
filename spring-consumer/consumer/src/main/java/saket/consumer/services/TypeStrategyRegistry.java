package saket.consumer.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * A registery that maps kafka event type names to ITypeStratgy implementations.
 */
@Component
public class TypeStrategyRegistry {
    
    private final Map<String, ITypeStrategy> typeMap;

    public TypeStrategyRegistry(List<ITypeStrategy> types) {
        typeMap = new HashMap<>();
        for (ITypeStrategy i : types) {
            typeMap.put(i.getTopicType(), i);
        }
    }

    /**
     * A function that returns a ITopicStrategy to the corresponding event type.
     * @param type A string that represents the type of the kafka topic 
     * @return Optional: ITypeStrategy impl or null
     */
    public Optional<ITypeStrategy> find(String type) {
        return Optional.ofNullable(typeMap.get(type));
    }
}
