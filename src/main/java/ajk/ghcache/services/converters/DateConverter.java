package ajk.ghcache.services.converters;

import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;

@Component
public class DateConverter implements Converter<Object, Long> {
    @Override
    public Long convert(Object source) {
        if (!(source instanceof TextNode)) {
            return 0L;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").parse(((TextNode) source).asText()).getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }
}
