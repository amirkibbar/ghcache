# GitHub Cache server

[![Build Status](https://travis-ci.org/amirkibbar/ghcache.svg?branch=master)](https://travis-ci.org/amirkibbar/ghcache)
[ ![Download](https://api.bintray.com/packages/amirk/maven/ghcache/images/download.svg) ](https://bintray.com/amirk/maven/ghcache/_latestVersion)

This is an exercise that shows a server that can cache the [GitHub API](https://developer.github.com/v3/). This is 
useful when clients would like to access GitHub's API in large volumes and you would want to prevent unnecessary burden 
on GitHub.

Caching reads also allows an opportunity to define custom views which save some processing time for downstream services.
As an example, this service implements the following custom views:

* /view/top/{N}/forks
* /view/top/{N}/last_updated
* /view/top/{N}/open_issues
* /view/top/{N}/stars
* /view/top/{N}/watchers

The service serves as a simple pass-through proxy to all GitHub API calls, except for pre-configured APIs for which the
service periodically caches the results and flattens pagination. 

The following APIs are cached:

* /
* /orgs/Netflix
* /orgs/Netflix/members
* /orgs/Netflix/repos

# Configuration

All configuration items can be passed as one of these 3 options (using my.property as an example):
* System properties: -Dmy.property=value
* Environment variable: MY_PROPERTY=value
* Program argument --my.property=value

## Ports

The cache server listens for http requests on port 7101. Use the property **server.port** to configure it. 

## Authentication

The access to the GitHub API requires a GitHub Personal Access Token. Generate a token with the **org:read** permissions
and pass this token to the server using the **GITHUB_API_TOKEN** environment variable, or by stating 
--github.api.token=<token> on the command line, or by passing **github.api.token** as a system property.

The format of the token should be `<username>:<personal token>`, for example:
`amirkibbar:12ab3cd4efa567b89cd0e1fa234b5fa56b7c8d90`

## Consul

By default the service assumes Consul is running on localhost:8500, you can customize this with the 
**github.consul-url** property. 

The service caches the GitHub responses into Consul, using a custom tree in the Consul key value store. You can 
customize the root of this tree, which by default is `github-cache`, using the **github.consul-k-v-root property**. 

## Cached URIs

You can change the default set of cached URIs. To do this copy the application.yml from src/main/resources and place it
next to the jar, for example in build/libs, then edit the **cached-uris** section.

## Repositories Views

By default the service fetches all the repositories in the /orgs/Netflix/repos (configurable with 
**github.repo-views-root**), and extracts some information from it for easier views of certain properties. These properties
can be accessed with special URLs mentioned above, for example /view/top/{n}/forks. The attributes collected can be 
configured, provided that they yield a number. For example, this is the default views configuration:

```yaml
github:
  repo-views-root: /orgs/Netflix/repos
  repo-views:
    - field: forks
    - field: updated_at
      path: last_updated
      converter: fromDate
    - field: open_issues
    - field: stargazers_count
      path: stars
    - field: watchers
```

Each item in the repo-views list represents a single view. A view must have a "field", and can optionally have a "path"
and a converter. Right now there are only 2 converters - from a number (basically a no-op converter) and from a date.

So, for example, to configure the /view/top/{n}/last_updated URI the view is defined with the field 'updated_at' - this
is the field returned by the GitHub API in the /orgs/:org/repos; the path is defined as "last_updated" - this is how it
appears in our desired API, and finally the converter is a "fromDate" converter, which converts an ISO8601 date into a
long representation.

# Building and Running

Clone the repository locally, then run `gradlew build`. This should produce the jar: build/libs/ghcache-0.0.1.jar. To 
run it use `java -jar ghcache-0.0.1.jar [options]`. 

Make sure Consul is running on localhost:8500 (the default configuration) or start it with the gradle script:
```bash
    gradlew startConsul
```

For example, to run it with the above GitHub token on port 8080:

```bash
    cd build/libs
    GITHUB_API_TOKEN=amirkibbar:12ab3cd4efa567b89cd0e1fa234b5fa56b7c8d90 java -jar ghcache-0.0.1.jar --server.port=8080
```

# Management API

The service provides a /healthcheck URI which returns 200 when the service is ready. The service also provides a 
/metrics URI which returns a set of useful metrics about the service.

In addition port 2005 (by default, configure with **management.shell.ssh.port**) listens to SSH sessions, this is considered an 
internal API to the service to debug it.

To connect to the SSH port find the password generated by the server in its startup procedure, this should appear in the
logs, for example:

```text
    Using default password for shell access: c31acd53-3e7d-4808-8df5-54740ca98151
```

Each server startup a new password is generated. Connect to the port with the user "user", use the password you found in the logs:

```bash
    ssh -p 2005 user@localhost
```

# Architecture

## Caches

The server caches all the information in the Consul key-value store under a single root (`github-cache` by default). In
addition to this cluster-wide cache each node has its own short-lived second level cache to prevent over-utilizing
Consul itself.

## Cluster

The server is designed to work in a cluster. The cluster design is of a symmetric cluster, meaning all nodes are
identical and have the same role. To facilitate this the cluster nodes must be time-synced, preferably up to the second,
using NTP or some other means to time-sync the nodes.

The cached data is stored in Consul - this way all members of the cluster can enjoy the cached results, even if they are
restarted. However, this implementation can be changed to a different mechanism is desired, by providing a different
implementation to the ResponseRepository interface.

## Notes and Limitations

Ideally the configuration would be stored in Consul and all the nodes in the cluster would read it from there. This can,
obviously be added to the service. However, right now the configuration is per-node, conflicting configuration could
have some unexpected results, particularly if you change the views and cached URIs.

Query params are not passed through to the underlying GitHub remote server, they are simply ignored.

All headers GitHub provides are passed back to the caller, however, the `Link` header is filtered because all responses
are flattened (de-paginated).

The service required Java 8. If you want to use the SSH connection, you must use a JDK rather than a JRE.