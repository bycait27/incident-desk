# D11 — Cost control and monthly estimate

The [Cloudflare account screenshot](D11-cloudflare-free-plan.png) shows the
Free plan active for this project's domain on November 24, 2026. Railway is
currently on a trial, not the Hobby plan assumed below; the trial ends when
its time or credits run out. Railway rejected an attempt to set a $5 email
alert because the workspace doesn't have an active subscription, so no
Railway budget alert is configured.

This is a hypothetical sizing model, not a bill or a measurement of current
usage. It models Railway Hobby for both environments in one workspace,
Cloudflare's free CDN plan, and Better Stack's free plan. Actual subscriptions,
usage, domain registration, taxes, and any paid add-ons need to be checked
separately. The Cloudflare screenshot verifies only the Cloudflare plan, not
the other assumed plans or the total cost of running the application.

I assumed 10,000 origin requests per month at 1× traffic, with 0.0005 GB of
Railway egress per request. For both environments combined, the model assumes
2 GB of baseline RAM plus 0.05 GB per 10,000 requests, 0.1 vCPU baseline plus
0.01 vCPU per 10,000 requests, and 1 GB of volume storage. The resources are
monthly averages; the scaling assumptions are estimates, not load-test results.

Railway lists $10 per GB-month of RAM, $20 per vCPU-month, $0.15 per GB-month
of volume storage, and $0.05 per GB of egress. Hobby costs $5 per month and
includes $5 of usage, so the modeled Railway bill is the greater of $5 and
the usage total.

| Traffic | Requests | RAM | CPU | Storage | Egress | Estimated Railway bill |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1× | 10,000 | $20.50 | $2.20 | $0.15 | $0.25 | $23.10 |
| 10× | 100,000 | $25.00 | $4.00 | $0.15 | $2.50 | $31.65 |
| 100× | 1,000,000 | $70.00 | $22.00 | $0.15 | $25.00 | $117.15 |

For example, the 1× calculation is `(2.05 × $10) + (0.11 × $20) +
(1 × $0.15) + (5 × $0.05) = $23.10`. The figures above exclude any charges
outside the assumed Railway, Cloudflare, and Better Stack plans, so they are
not a complete account-specific total. CDN caching, polling, database growth,
or additional replicas could change the results.

Pricing sources checked November 24, 2026:
[Railway](https://docs.railway.com/pricing/plans),
[Cloudflare](https://www.cloudflare.com/plans/network-cdn/), and
[Better Stack](https://betterstack.com/pricing/).
