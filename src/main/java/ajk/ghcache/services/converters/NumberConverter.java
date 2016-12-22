package ajk.ghcache.services.converters;

import com.fasterxml.jackson.databind.node.NumericNode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class NumberConverter implements Converter<Object, Long> {
    @Override
    public Long convert(Object source) {
        if (source instanceof NumericNode) {
            return ((NumericNode) source).asLong();
        }

        return 0L;
    }
}
