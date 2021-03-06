#!/bin/bash

PORT=${1:-7101}
BASE_URL="http://localhost:$PORT"

for TOOL in bc curl jq wc awk sort uniq tr head tail; do
    if ! which $TOOL >/dev/null; then
        echo "ERROR: $TOOL is not available in the PATH"
        exit 1
    fi
done

PASS=0
FAIL=0
TOTAL=0

function describe() {
    echo -n "$1"; let TOTAL=$TOTAL+1
}

function pass() {
    echo "pass"; let PASS=$PASS+1
}

function fail() {
    echo "fail";  let FAIL=$FAIL+1
}

function report() {
    PCT=$(echo "scale=2; $PASS / $TOTAL * 100" |bc)
    echo "$PASS/$TOTAL ($PCT%) tests passed"
}

describe "test-01-01: healthcheck = "

ATTEMPTS=0
while true; do
    let ATTEMPTS=$ATTEMPTS+1
    RESPONSE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/healthcheck")
    if [[ $RESPONSE == "200" ]]; then
        let TIME=$ATTEMPTS*15
        echo -n "($TIME seconds) "; pass
        break
    else
        if [[ $ATTEMPTS -gt 24 ]]; then
            let TIME=$ATTEMPTS*15
            echo -n "($TIME seconds) "; fail
            break
        fi
        sleep 15
    fi
done

describe "test-02-01: / key count = "

COUNT=$(curl -s "$BASE_URL" |jq -r 'keys |.[]' |wc -l |awk '{print $1}')

if [[ $COUNT -eq 31 ]]; then
    pass
else
    fail
fi

describe "test-02-02: / repository_search_url value = "

VALUE=$(curl -s "$BASE_URL" |jq -r '.repository_search_url')

if [[ "$VALUE" == "https://api.github.com/search/repositories?q={query}{&page,per_page,sort,order}" ]]; then
    pass
else
    fail
fi

describe "test-02-03: / organization_repositories_url value = "

VALUE=$(curl -s "$BASE_URL" |jq -r '.organization_repositories_url')

if [[ "$VALUE" == "https://api.github.com/orgs/{org}/repos{?type,page,per_page,sort}" ]]; then
    pass
else
    fail
fi

describe "test-03-01: /orgs/Netflix key count = "

COUNT=$(curl -s "$BASE_URL/orgs/Netflix" |jq -r 'keys |.[]' |wc -l |awk '{print $1}')

if [[ $COUNT -eq 24 ]]; then
    pass
else
    fail
fi

describe "test-03-02: /orgs/Netflix avatar_url = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix" |jq -r '.avatar_url')

if [[ "$VALUE" == "https://avatars.githubusercontent.com/u/913567?v=3" ]]; then
    pass
else
    fail
fi

describe "test-03-03: /orgs/Netflix location = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix" |jq -r '.location')

if [[ "$VALUE" == "Los Gatos, California" ]]; then
    pass
else
    fail
fi

describe "test-04-01: /orgs/Netflix/members object count = "

COUNT=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '. |length')

if [[ $COUNT -gt 35 ]] && [[ $COUNT -lt 65 ]]; then
    pass
else
    fail
fi

describe "test-04-02: /orgs/Netflix/members login first alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.login' |tr '[:upper:]' '[:lower:]' |sort |head -1)

if [[ "$VALUE" == "aglover" ]]; then
    pass
else
    fail
fi

describe "test-04-03: /orgs/Netflix/members login first alpha case-sensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.login' |sort |head -1)

if [[ "$VALUE" == "NiteshKant" ]]; then
    pass
else
    fail
fi

describe "test-04-04: /orgs/Netflix/members login last alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.login' |tr '[:upper:]' '[:lower:]' |sort |tail -1)

if [[ "$VALUE" == "zethussuen" ]]; then
    pass
else
    fail
fi

describe "test-04-05: /orgs/Netflix/members id first = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.id' |sort -n |head -1)

if [[ "$VALUE" == "21094" ]]; then
    pass
else
    fail
fi

describe "test-04-06: /orgs/Netflix/members id last = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.id' |sort -n |tail -1)

if [[ "$VALUE" == "13667833" ]]; then
    pass
else
    fail
fi

describe "test-04-07: /users/aglover/orgs proxy = "

VALUE=$(curl -s "$BASE_URL/users/aglover/orgs" |jq -r '.[] |.login' |tr -d '\n')

if [[ "$VALUE" == "FoklNetflixhubot-scripts" ]]; then
    pass
else
    echo "[$VALUE]"
    fail
fi

describe "test-04-08: /users/zethussuen/orgs proxy = "

VALUE=$(curl -s "$BASE_URL/users/zethussuen/orgs" |jq -r '.[] |.login' |tr -d '\n')

if [[ "$VALUE" == "Netflix" ]]; then
    pass
else
    echo "[$VALUE]"
    fail
fi

describe "test-05-01: /orgs/Netflix/repos object count = "

COUNT=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '. |length')

if [[ $COUNT -gt 100 ]] && [[ $COUNT -lt 150 ]]; then
    pass
else
    fail
fi

describe "test-05-02: /orgs/Netflix/repos full_name first alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.full_name' |tr '[:upper:]' '[:lower:]' |sort |head -1)

if [[ "$VALUE" == "netflix/aegisthus" ]]; then
    pass
else
    fail
fi

describe "test-05-03: /orgs/Netflix/members full_name first alpha case-sensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.full_name' |sort |head -1)

if [[ "$VALUE" == "Netflix/AWSObjectMapper" ]]; then
    pass
else
    fail
fi

describe "test-05-04: /orgs/Netflix/members login last alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.full_name' |tr '[:upper:]' '[:lower:]' |sort |tail -1)

if [[ "$VALUE" == "netflix/zuul" ]]; then
    pass
else
    fail
fi

describe "test-05-05: /orgs/Netflix/repos id first = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.id' |sort -n |head -1)

if [[ "$VALUE" == "2044029" ]]; then
    pass
else
    fail
fi

describe "test-05-06: /orgs/Netflix/repos id last = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.id' |sort -n |tail -1)

if [[ "$VALUE" == "75882172" ]]; then
    pass
else
    fail
fi

describe "test-05-07: /orgs/Netflix/repos languages unique = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.language' |sort -u |tr -d '\n')

if [[ "$VALUE" == "CC#C++ClojureGoGroovyHTMLJavaJavaScriptNginxPythonRubyScalaShellnull" ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/forks = "

VALUE=$(curl -s "$BASE_URL/view/top/5/forks")

if [[ "$VALUE" == '[["Netflix/Hystrix", 1553], ["Netflix/SimianArmy", 663], ["Netflix/eureka", 614], ["Netflix/Cloud-Prize", 524], ["Netflix/zuul", 484]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/forks = "

VALUE=$(curl -s "$BASE_URL/view/top/10/forks")

if [[ "$VALUE" == '[["Netflix/Hystrix", 1553], ["Netflix/SimianArmy", 663], ["Netflix/eureka", 614], ["Netflix/Cloud-Prize", 524], ["Netflix/zuul", 484], ["Netflix/asgard", 425], ["Netflix/astyanax", 356], ["Netflix/curator", 349], ["Netflix/ice", 336], ["Netflix/falcor", 301]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/last_updated = "

VALUE=$(curl -s "$BASE_URL/view/top/5/last_updated")

if [[ "$VALUE" == '[["Netflix/genie", "2016-12-23T00:11:32Z"], ["Netflix/dyno", "2016-12-22T23:49:45Z"], ["Netflix/falcor", "2016-12-22T23:45:01Z"], ["Netflix/zuul", "2016-12-22T23:23:05Z"], ["Netflix/vizceral", "2016-12-22T23:19:22Z"]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/last_updated = "

VALUE=$(curl -s "$BASE_URL/view/top/10/last_updated")

if [[ "$VALUE" == '[["Netflix/genie", "2016-12-23T00:11:32Z"], ["Netflix/dyno", "2016-12-22T23:49:45Z"], ["Netflix/falcor", "2016-12-22T23:45:01Z"], ["Netflix/zuul", "2016-12-22T23:23:05Z"], ["Netflix/vizceral", "2016-12-22T23:19:22Z"], ["Netflix/archaius", "2016-12-22T22:46:18Z"], ["Netflix/conductor", "2016-12-22T22:28:52Z"], ["Netflix/hollow", "2016-12-22T22:23:51Z"], ["Netflix/dynomite", "2016-12-22T22:23:00Z"], ["Netflix/Hystrix", "2016-12-22T22:06:17Z"]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/open_issues = "

VALUE=$(curl -s "$BASE_URL/view/top/5/open_issues")

if [[ "$VALUE" == '[["Netflix/astyanax", 158], ["Netflix/ribbon", 110], ["Netflix/asgard", 105], ["Netflix/ice", 103], ["Netflix/falcor", 90]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/open_issues = "

VALUE=$(curl -s "$BASE_URL/view/top/10/open_issues")

if [[ "$VALUE" == '[["Netflix/astyanax", 158], ["Netflix/ribbon", 110], ["Netflix/asgard", 105], ["Netflix/ice", 103], ["Netflix/falcor", 90], ["Netflix/Hystrix", 54], ["Netflix/zuul", 49], ["Netflix/archaius", 47], ["Netflix/governator", 43], ["Netflix/security_monkey", 42]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/stars = "

VALUE=$(curl -s "$BASE_URL/view/top/5/stars")

if [[ "$VALUE" == '[["Netflix/Hystrix", 7970], ["Netflix/falcor", 7078], ["Netflix/SimianArmy", 4914], ["Netflix/eureka", 2661], ["Netflix/vector", 2136]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/stars = "

VALUE=$(curl -s "$BASE_URL/view/top/10/stars")

if [[ "$VALUE" == '[["Netflix/Hystrix", 7970], ["Netflix/falcor", 7078], ["Netflix/SimianArmy", 4914], ["Netflix/eureka", 2661], ["Netflix/vector", 2136], ["Netflix/asgard", 2133], ["Netflix/ice", 2061], ["Netflix/zuul", 1932], ["Netflix/Scumblr", 1872], ["Netflix/dynomite", 1729]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/watchers = "

VALUE=$(curl -s "$BASE_URL/view/top/5/watchers")

if [[ "$VALUE" == '[["Netflix/Hystrix", 7970], ["Netflix/falcor", 7078], ["Netflix/SimianArmy", 4914], ["Netflix/eureka", 2661], ["Netflix/vector", 2136]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/watchers = "

VALUE=$(curl -s "$BASE_URL/view/top/10/watchers")

if [[ "$VALUE" == '[["Netflix/Hystrix", 7970], ["Netflix/falcor", 7078], ["Netflix/SimianArmy", 4914], ["Netflix/eureka", 2661], ["Netflix/vector", 2136], ["Netflix/asgard", 2133], ["Netflix/ice", 2061], ["Netflix/zuul", 1932], ["Netflix/Scumblr", 1872], ["Netflix/dynomite", 1729]]' ]]; then
    pass
else
    fail
fi

report