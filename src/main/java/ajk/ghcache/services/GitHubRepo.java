package ajk.ghcache.services;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class GitHubRepo {
    private String fullName;
    Map<String, Long> viewField = new HashMap<>();
}
