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
    echo "fail, expected $1 found $2";  let FAIL=$FAIL+1
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

if [[ $COUNT -eq 30 ]]; then
    pass
else
    fail 30 $COUNT
fi

describe "test-02-02: / repository_search_url value = "

VALUE=$(curl -s "$BASE_URL" |jq -r '.repository_search_url')

if [[ "$VALUE" == "https://api.github.com/search/repositories?q={query}{&page,per_page,sort,order}" ]]; then
    pass
else
    fail "https://api.github.com/search/repositories?q={query}{&page,per_page,sort,order}" "$VALUE"
fi

describe "test-02-03: / organization_repositories_url value = "

VALUE=$(curl -s "$BASE_URL" |jq -r '.organization_repositories_url')

if [[ "$VALUE" == "https://api.github.com/orgs/{org}/repos{?type,page,per_page,sort}" ]]; then
    pass
else
    fail "https://api.github.com/orgs/{org}/repos{?type,page,per_page,sort}" "$VALUE"
fi

describe "test-03-01: /orgs/Netflix key count = "

COUNT=$(curl -s "$BASE_URL/orgs/Netflix" |jq -r 'keys |.[]' |wc -l |awk '{print $1}')

if [[ $COUNT -eq 24 ]]; then
    pass
else
    fail 24 $COUNT
fi

describe "test-03-02: /orgs/Netflix avatar_url = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix" |jq -r '.avatar_url')

if [[ "$VALUE" == "https://avatars.githubusercontent.com/u/913567?v=3" ]]; then
    pass
else
    fail "https://avatars.githubusercontent.com/u/913567?v=3" "$VALUE"
fi

describe "test-03-03: /orgs/Netflix location = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix" |jq -r '.location')

if [[ "$VALUE" == "Los Gatos, California" ]]; then
    pass
else
    fail "Los Gatos, California" "$VALUE"
fi

describe "test-04-01: /orgs/Netflix/members object count = "

COUNT=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '. |length')

if [[ $COUNT -gt 47 ]] && [[ $COUNT -lt 57 ]]; then
    pass
else
    fail "47-57" $COUNT
fi

describe "test-04-02: /orgs/Netflix/members login first alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.login' |tr '[:upper:]' '[:lower:]' |sort |head -1)

if [[ "$VALUE" == "aglover" ]]; then
    pass
else
    fail "aglover" "$VALUE"
fi

describe "test-04-03: /orgs/Netflix/members login first alpha case-sensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.login' |sort |head -1)

if [[ "$VALUE" == "ScottMansfield" ]]; then
    pass
else
    fail "ScottMansfield" "$VALUE"
fi

describe "test-04-04: /orgs/Netflix/members login last alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.login' |tr '[:upper:]' '[:lower:]' |sort |tail -1)

if [[ "$VALUE" == "zethussuen" ]]; then
    pass
else
    fail "zethussuen" "$VALUE"
fi

describe "test-04-05: /orgs/Netflix/members id first = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.id' |sort -n |head -1)

if [[ "$VALUE" == "690" ]]; then
    pass
else
    fail 690 $VALUE
fi

describe "test-04-06: /orgs/Netflix/members id last = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/members" |jq -r '.[] |.id' |sort -n |tail -1)

if [[ "$VALUE" == "11634397" ]]; then
    pass
else
    fail 11634397 $VALUE
fi

describe "test-04-07: /users/aglover/orgs proxy = "

VALUE=$(curl -s "$BASE_URL/users/aglover/orgs" |jq -r '.[] |.login' |tr -d '\n')

if [[ "$VALUE" == "FoklNetflixhubot-scripts" ]]; then
    pass
else
    echo "[$VALUE]"
    fail "FoklNetflixhubot-scripts" "$VALUE"
fi

describe "test-04-08: /users/zethussuen/orgs proxy = "

VALUE=$(curl -s "$BASE_URL/users/zethussuen/orgs" |jq -r '.[] |.login' |tr -d '\n')

if [[ "$VALUE" == "Netflix" ]]; then
    pass
else
    echo "[$VALUE]"
    fail "Netflix" "$VALUE"
fi

describe "test-05-01: /orgs/Netflix/repos object count = "

COUNT=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '. |length')

if [[ $COUNT -gt 113 ]] && [[ $COUNT -lt 123 ]]; then
    pass
else
    fail "113-123" $COUNT
fi

describe "test-05-02: /orgs/Netflix/repos full_name first alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.full_name' |tr '[:upper:]' '[:lower:]' |sort |head -1)

if [[ "$VALUE" == "netflix/aegisthus" ]]; then
    pass
else
    fail "netflix/aegisthus" "$VALUE"
fi

describe "test-05-03: /orgs/Netflix/members full_name first alpha case-sensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.full_name' |sort |head -1)

if [[ "$VALUE" == "Netflix/AWSObjectMapper" ]]; then
    pass
else
    fail "Netflix/AWSObjectMapper" "$VALUE"
fi

describe "test-05-04: /orgs/Netflix/members login last alpha case-insensitive = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.full_name' |tr '[:upper:]' '[:lower:]' |sort |tail -1)

if [[ "$VALUE" == "netflix/zuul" ]]; then
    pass
else
    fail "netflix/zuul" "$VALUE"
fi

describe "test-05-05: /orgs/Netflix/repos id first = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.id' |sort -n |head -1)

if [[ "$VALUE" == "2044029" ]]; then
    pass
else
    fail 2044029 $VALUE
fi

describe "test-05-06: /orgs/Netflix/repos id last = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.id' |sort -n |tail -1)

if [[ "$VALUE" == "62339483" ]]; then
    pass
else
    fail "62339483" "$VALUE"
fi

describe "test-05-07: /orgs/Netflix/repos languages unique = "

VALUE=$(curl -s "$BASE_URL/orgs/Netflix/repos" |jq -r '.[] |.language' |sort -u |tr -d '\n')

if [[ "$VALUE" == "CC#C++ClojureGoGroovyHTMLJavaJavaScriptNginxPythonRubyScalaShellnull" ]]; then
    pass
else
    fail "CC#C++ClojureGoGroovyHTMLJavaJavaScriptNginxPythonRubyScalaShellnull" "$VALUE"
fi



describe "test-06-01: /view/top/5/forks = "

VALUE=$(curl -s "$BASE_URL/view/top/5/forks")

if [[ "$VALUE" == '[["Netflix/Hystrix", 1169], ["Netflix/SimianArmy", 561], ["Netflix/Cloud-Prize", 530], ["Netflix/eureka", 464], ["Netflix/asgard", 421]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/forks = "

VALUE=$(curl -s "$BASE_URL/view/top/10/forks")

if [[ "$VALUE" == '[["Netflix/Hystrix", 1169], ["Netflix/SimianArmy", 561], ["Netflix/Cloud-Prize", 530], ["Netflix/eureka", 464], ["Netflix/asgard", 421], ["Netflix/astyanax", 352], ["Netflix/curator", 327], ["Netflix/ice", 304], ["Netflix/zuul", 302], ["Netflix/exhibitor", 283]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/last_updated = "

VALUE=$(curl -s "$BASE_URL/view/top/5/last_updated")

if [[ "$VALUE" == '[["Netflix/feign", "2016-07-01T22:01:30Z"], ["Netflix/rend", "2016-07-01T22:01:16Z"], ["Netflix/vizceral", "2016-07-01T21:51:19Z"], ["Netflix/atlas", "2016-07-01T21:44:00Z"], ["Netflix/falcor", "2016-07-01T21:37:39Z"]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/last_updated = "

VALUE=$(curl -s "$BASE_URL/view/top/10/last_updated")

if [[ "$VALUE" == '[["Netflix/feign", "2016-07-01T22:01:30Z"], ["Netflix/rend", "2016-07-01T22:01:16Z"], ["Netflix/vizceral", "2016-07-01T21:51:19Z"], ["Netflix/atlas", "2016-07-01T21:44:00Z"], ["Netflix/falcor", "2016-07-01T21:37:39Z"], ["Netflix/security_monkey", "2016-07-01T21:13:37Z"], ["Netflix/Hystrix", "2016-07-01T18:57:18Z"], ["Netflix/Priam", "2016-07-01T18:11:25Z"], ["Netflix/bless", "2016-07-01T17:38:20Z"], ["Netflix/techreports", "2016-07-01T17:38:19Z"]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/open_issues = "

VALUE=$(curl -s "$BASE_URL/view/top/5/open_issues")

if [[ "$VALUE" == '[["Netflix/astyanax", 153], ["Netflix/asgard", 105], ["Netflix/ribbon", 96], ["Netflix/ice", 89], ["Netflix/archaius", 79]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/open_issues = "

VALUE=$(curl -s "$BASE_URL/view/top/10/open_issues")

if [[ "$VALUE" == '[["Netflix/astyanax", 153], ["Netflix/asgard", 105], ["Netflix/ribbon", 96], ["Netflix/ice", 89], ["Netflix/archaius", 79], ["Netflix/falcor", 75], ["Netflix/Hystrix", 62], ["Netflix/exhibitor", 60], ["Netflix/feign", 54], ["Netflix/security_monkey", 49]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/stars = "

VALUE=$(curl -s "$BASE_URL/view/top/5/stars")

if [[ "$VALUE" == '[["Netflix/falcor", 6440], ["Netflix/Hystrix", 6325], ["Netflix/SimianArmy", 4188], ["Netflix/asgard", 2106], ["Netflix/eureka", 2032]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/stars = "

VALUE=$(curl -s "$BASE_URL/view/top/10/stars")

if [[ "$VALUE" == '[["Netflix/falcor", 6440], ["Netflix/Hystrix", 6325], ["Netflix/SimianArmy", 4188], ["Netflix/asgard", 2106], ["Netflix/eureka", 2032], ["Netflix/vector", 1909], ["Netflix/ice", 1851], ["Netflix/dynomite", 1406], ["Netflix/curator", 1390], ["Netflix/zuul", 1142]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/5/watchers = "

VALUE=$(curl -s "$BASE_URL/view/top/5/watchers")

if [[ "$VALUE" == '[["Netflix/falcor", 6440], ["Netflix/Hystrix", 6325], ["Netflix/SimianArmy", 4188], ["Netflix/asgard", 2106], ["Netflix/eureka", 2032]]' ]]; then
    pass
else
    fail
fi

describe "test-06-01: /view/top/10/watchers = "

VALUE=$(curl -s "$BASE_URL/view/top/10/watchers")

if [[ "$VALUE" == '[["Netflix/falcor", 6440], ["Netflix/Hystrix", 6325], ["Netflix/SimianArmy", 4188], ["Netflix/asgard", 2106], ["Netflix/eureka", 2032], ["Netflix/vector", 1909], ["Netflix/ice", 1851], ["Netflix/dynomite", 1406], ["Netflix/curator", 1390], ["Netflix/zuul", 1142]]' ]]; then
    pass
else
    fail '[["Netflix/falcor", 6440], ["Netflix/Hystrix", 6325], ["Netflix/SimianArmy", 4188], ["Netflix/asgard", 2106], ["Netflix/eureka", 2032], ["Netflix/vector", 1909], ["Netflix/ice", 1851], ["Netflix/dynomite", 1406], ["Netflix/curator", 1390], ["Netflix/zuul", 1142]]' "$VALUE"
fi

report