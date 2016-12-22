package ajk.ghcache.services;

import ajk.ghcache.config.GHCacheProperties;
import ajk.ghcache.config.GHCacheProperties.RepoView;
import ajk.ghcache.services.converters.DateConverter;
import ajk.ghcache.services.converters.NumberConverter;
import ajk.ghcache.services.repositories.GitHubRepoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ajk.ghcache.config.GHCacheProperties.RepoView.ToNumConverter.fromNumber;
import static org.apache.commons.logging.LogFactory.getLog;

@Service
public class ViewService {
    private Log log = getLog(getClass());

    @Autowired
    private GHCacheProperties props;

    @Autowired
    private CacheService cache;

    @Autowired
    private GitHubRepoRepository gitHubRepoRepository;

    @Autowired
    private ObjectMapper mapper;

    // if we'll need more converters we could put them in a map and look them up by the enum value, for now this is enough
    @Autowired
    private NumberConverter numberConverter;

    // if we'll need more converters we could put them in a map and look them up by the enum value, for now this is enough
    @Autowired
    private DateConverter dateConverter;

    private Pattern viewPattern = Pattern.compile("/view/top/(\\p{Digit}*)/([\\p{Alnum}\\p{Punct}]*)");

    public List<Object[]> getTopN(String request) {
        Matcher matcher = viewPattern.matcher(request);

        if (!matcher.matches()) {
            return null;
        }

        List<GitHubRepo> repos = gitHubRepoRepository.findAll();
        int n = Integer.parseInt(matcher.group(1));
        String path = matcher.group(2);

        repos.sort((o1, o2) -> {
            Long f1 = o1.getViewField().get(path);
            Long f2 = o2.getViewField().get(path);

            return f2 == null ? 1 : f2.compareTo(f1);
        });

        return
                repos.stream()
                        .limit(n)
                        .map(it -> {
                            Object[] result = new Object[2];
                            result[0] = it.getFullName();
                            result[1] = it.getViewField().get(path);
                            return result;
                        })
                        .collect(Collectors.toList());

    }

    public void cacheViews() {
        // to make sure that we're the only node in the cluster updating the views, we'll lock here
        String lock = gitHubRepoRepository.acquireLock();
        if (lock == null) {
            // someone beat us to the punch, no work left to do
            return;
        }

        try {
            CachedResponse cachedResponse = cache.fetch(props.getRepoViewsRoot(), false);

            // we have all the nodes in JSON format, we don't want to model the entire GitHub API, instead we just want to
            // store each repo as-is but store the required view fields separately so we could later sort by them
            String allRepos = cachedResponse.getContent();
            List<GitHubRepo> repos = new ArrayList<>();

            for (ObjectNode node : mapper.readValue(allRepos, ObjectNode[].class)) {
                GitHubRepo repo = new GitHubRepo();

                // store original value
                repo.setFullName(node.get("full_name").asText());

                for (RepoView view : props.getRepoViews()) {
                    Converter<Object, Long> converter = view.getConverter() == null || view.getConverter().equals(fromNumber) ? numberConverter : dateConverter;
                    String path = view.getPath() == null ? view.getField() : view.getPath();
                    repo.getViewField().put(path, converter.convert(node.get(view.getField())));
                }

                repos.add(repo);
            }

            gitHubRepoRepository.store(repos);
        } catch (Exception e) {
            log.error("couldn't cache views, " + e.getMessage(), e);
        } finally {
            // make sure we release the lock, if we don't then the session will expire anyway, but this would let others
            // reacquire the lock faster
            gitHubRepoRepository.releaseLock(lock);
        }
    }
}
