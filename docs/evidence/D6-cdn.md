# D6 — CDN and cache checks

On November 24, 2026, I deployed the cache change to staging as Railway
deployment `86002732-be1c-4c57-8730-225baa5657eb`. This deployment was built
from the uncommitted workspace, not a Git commit. The staging health check
returned 200.

The [selected response headers](D6-cdn-headers-2026-11-24.txt) show a MISS
and HIT after I purged the staging JavaScript URL in Cloudflare, followed by
a conditional 304. A third request returned MISS; CDN cache state can vary
between requests. Before the purge, repeated requests also showed MISS
followed by HIT for both the JavaScript and CSS assets.

The staging HTML and anonymous API response used `no-store`. The backend
tests also check `no-store` for an authenticated operator's incident
response, an unversioned asset, and a missing asset. I did not capture an
authenticated staging response or a screenshot of the purge.
