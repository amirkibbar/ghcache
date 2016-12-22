package ajk.ghcache.services.repositories;

import ajk.ghcache.services.GitHubRepo;

import java.util.List;

public interface GitHubRepoRepository {
    void store(List<GitHubRepo> repos);

    List<GitHubRepo> findAll();

    String acquireLock();

    void releaseLock(String id);
}
