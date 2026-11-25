# D8 — Performance measurements

On November 24, 2026, I compared the production frontend build locally with
Lighthouse 13.6.0 and headless Chrome 158 using the same local API and
Lighthouse settings. The baseline used source commit
`5ffff212ead41ab25e8301488423e88a126bf79d`; the second run used that commit
plus the uncommitted frontend changes in this workspace.

The change defers the report form and operator controls until they are needed.
Vite reported an initial JavaScript bundle of 244.66 kB before and 242.26 kB
after, uncompressed. The reported gzip sizes were 75.83 kB before and
75.87 kB after, so the compressed transfer size did not improve.

| Lighthouse measurement | Before | After |
| --- | ---: | ---: |
| Largest Contentful Paint | 1.672 s | 1.661 s |
| Cumulative Layout Shift | 0.0232 | 0.0071 |
| First Contentful Paint | 0.756 s | 0.754 s |
| Total Blocking Time | 0 ms | 0 ms |

These are single lab runs, not field measurements. The differences could be
normal run-to-run variation and do not establish that the code change caused
an improvement. Lighthouse does not measure real-user Interaction to Next
Paint in this run.

The unmodified local reports are [before](D8-lighthouse-before.json) and
[after](D8-lighthouse-after.json).

I also ran Lighthouse against staging before and after deployment. The
performance score was 0.99 in both runs, LCP went from 1.578 s to 1.586 s,
and CLS went from 0.0302 to 0.0232. These single runs also don't establish
a performance improvement. The unmodified staging reports are
[before](D8-staging-lighthouse-before.json) and
[after](D8-staging-lighthouse-after.json).
