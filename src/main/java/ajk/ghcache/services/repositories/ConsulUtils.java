package ajk.ghcache.services.repositories;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.http.HttpResponse;
import org.bouncycastle.util.encoders.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.apache.http.client.fluent.Executor.newInstance;
import static org.apache.http.client.fluent.Request.Get;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class ConsulUtils {
    @Autowired
    private ObjectMapper mapper;

    public String responseToJson(HttpResponse rawResponse) throws IOException {
        // do some error validation - if the value doesn't exist in Consul - return null
        if (rawResponse.getStatusLine().getStatusCode() == NOT_FOUND.value()) {
            // cache miss - this path isn't cached yet
            return null;
        }

        // convert from Consul response to our value
        ConsulResponse[] consulResponse = mapper.readValue(rawResponse.getEntity().getContent(), ConsulResponse[].class);
        // we know that there is a response, we'll make sure there's a single response by evicting stale ones and
        // by locking when inserting
        return new String(Base64.decode(consulResponse[0].getValue()));
    }

    @Data
    public static class ConsulResponse {
        @JsonProperty("LockIndex")
        private int lockIndex;

        @JsonProperty("Key")
        private String key;

        @JsonProperty("Flags")
        private int flags;

        @JsonProperty("Value")
        private String value;

        @JsonProperty("CreateIndex")
        private int createIndex;

        @JsonProperty("ModifyIndex")
        private int modifyIndex;
    }
}
