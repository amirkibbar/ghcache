package ajk.ghcache.controllers;

import ajk.ghcache.services.CacheService;
import ajk.ghcache.services.CachedResponse;
import ajk.ghcache.services.ViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
public class GitHubController {
    @Autowired
    private CacheService cache;

    @Autowired
    private ViewService view;

    private String myHost;

    public GitHubController() throws UnknownHostException {
        // there should (99.9% sure) not going to be an exception here because we're fetching our own hostname
        myHost = InetAddress.getLocalHost().getHostName();
    }

    @DeleteMapping("/view")
    public void forceRefreshViews() {
        view.cacheViews();
    }

    @GetMapping(value = "/view/**", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Object[]>> view(HttpServletRequest request) {
        List<Object[]> result = view.getTopN(request.getRequestURI());

        if (result == null) {
            return new ResponseEntity<>(NOT_FOUND);
        } else {
            return new ResponseEntity<>(result, OK);
        }
    }

    @DeleteMapping("/")
    public void forceCacheEviction() {
        cache.rebuild();
    }

    @GetMapping("/**")
    public void cache(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean force = "true".equals(request.getParameter("force"));

        CachedResponse fetched = cache.fetch(request.getRequestURI(), force);

        if (fetched == null) {
            response.sendError(SC_BAD_GATEWAY, "unable to complete proxy request");
        } else {
            fetched.getHeaders().forEach(header -> response.addHeader(header.getName(), header.getValue()));
            // add our own headers
            response.setHeader("X-Forwarded-Host", myHost);
            response.getWriter().print(fetched.getContent());
            response.setStatus(fetched.getStatusLine().getStatusCode());
        }
    }
}
