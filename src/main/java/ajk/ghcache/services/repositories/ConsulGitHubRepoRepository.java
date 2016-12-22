package ajk.ghcache.services.repositories;

import ajk.ghcache.config.GHCacheProperties;
import ajk.ghcache.services.GitHubRepo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.commons.logging.Log;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.apache.http.client.fluent.Executor.newInstance;
import static org.apache.http.client.fluent.Request.Delete;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Put;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;

@Component
public class ConsulGitHubRepoRepository implements GitHubRepoRepository {
    private Log log = getLog(getClass());

    @Autowired
    private GHCacheProperties props;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ConsulUtils consulUtils;

    @Override
    public String acquireLock() {
        try {
            String session = newInstance().execute(
                    Put(props.getConsulUrl().toString() + "/v1/session/create")
                            .bodyString("{\"TTL\": \"120s\" }", APPLICATION_JSON))
                    .returnContent().asString();

            String id = mapper.readValue(session, ConsulSession.class).getId();

            String lock = newInstance().execute(
                    Put(props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + "/lock?acquire=" + id))
                    .returnContent().asString();

            if (lock != null && "true".equals(lock)) {
                return id;
            } else {
                return null;
            }
        } catch (Exception e) {
            log.warn("couldn't acquire lock, " + e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void releaseLock(String id) {
        if (id == null) {
            return;
        }

        try {
            // release lock
            newInstance().execute(
                    Put(props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + "/lock?release=" + id))
                    .discardContent();

            // destroy session
            newInstance().execute(
                    Put(props.getConsulUrl().toString() + "/v1/session/destroy/" + id))
                    .discardContent();
        } catch (Exception e) {
            log.warn("couldn't release lock, " + e.getMessage(), e);
        }
    }

    @Override
    public void store(List<GitHubRepo> repos) {
        if (repos.size() > 0) {
            // the assumption is that someone has already locked so it's safe to just overwrite the value

            // unfortunately the maximum size of values in Consul won't allow us to store all the repos at once, instead
            // we'll store them in a forced list structure. Before that we'll have to delete the value
            try {
                newInstance().execute(
                        Delete(props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + "/views?recurse"))
                        .discardContent();

                newInstance().execute(
                        Put(props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + "/views/count")
                                .bodyString("" + repos.size(), TEXT_PLAIN))
                        .discardContent();

                for (int i = 0; i < repos.size(); i++) {
                    newInstance().execute(
                            Put(props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + "/views/" + i)
                                    .bodyString(mapper.writeValueAsString(repos.get(i)), APPLICATION_JSON))
                            .discardContent();
                }
            } catch (Exception e) {
                log.warn("couldn't store " + repos.size() + " GitHub Repositories, " + e.getMessage(), e);
            }
        }
    }

    @Override
    @Cacheable(value = "repos", unless = "#result.size() == 0")
    public List<GitHubRepo> findAll() {
        try {
            HttpResponse rawResponse = newInstance().execute(
                    Get(props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + "/views/count"))
                    .returnResponse();
            String decoded = consulUtils.responseToJson(rawResponse);
            if (decoded == null) {
                return emptyList();
            }

            int count = Integer.parseInt(decoded);
            List<GitHubRepo> result = new ArrayList<>(count);
            for (int i = 0; i < count; ++i) {
                rawResponse = newInstance().execute(
                        Get(props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + "/views/" + i))
                        .returnResponse();
                decoded = consulUtils.responseToJson(rawResponse);
                result.add(mapper.readValue(decoded, GitHubRepo.class));
            }

            return result;
        } catch (Exception e) {
            return emptyList();
        }
    }

    @Data
    public static class ConsulSession {
        @JsonProperty("ID")
        private String id;
    }
}
