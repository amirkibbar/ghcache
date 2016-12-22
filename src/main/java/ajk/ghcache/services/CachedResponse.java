package ajk.ghcache.services;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;

@Data
@NoArgsConstructor
public class CachedResponse {
    private List<CachedHeader> headers = new ArrayList<>();
    private CachedStatusLine statusLine;
    private String content;
    private long validUntil;

    public CachedResponse(HttpResponse response) throws IOException {
        // store the headers, but filter out the original "transfer-encoding", we'll let our application server decide
        // about its own encoding. Also filter out "link" because we're flattening the pagination anyway
        headers = of(response.getAllHeaders())
                .filter(header -> !header.getName().equalsIgnoreCase("transfer-encoding"))
                .filter(header -> !header.getName().equalsIgnoreCase("link"))
                .map(CachedHeader::new)
                .collect(toList());

        statusLine = new CachedStatusLine(response.getStatusLine());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);

        content = new String(out.toByteArray(), Charset.forName("UTF-8"));
    }

    @Data
    @NoArgsConstructor
    public static class CachedHeader {
        private String name;
        private String value;

        public CachedHeader(Header header) {
            name = header.getName();
            value = header.getValue();
        }
    }

    @Data
    @NoArgsConstructor
    public static class CachedStatusLine {
        private int statusCode;
        private String reasonPhrase;

        public CachedStatusLine(StatusLine line) {
            statusCode = line.getStatusCode();
            reasonPhrase = line.getReasonPhrase();
        }
    }
}
