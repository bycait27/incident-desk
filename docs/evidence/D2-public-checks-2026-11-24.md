# Production HTTPS checks, 2026-11-24

The public hostname `status.csc436.com` was checked from Windows. The source
revision of the deployed app was not established; these checks describe the
public edge, not a particular commit. I didn't record predictions before
these checks.

## HTTP redirect

```powershell
curl.exe -sS -i --max-time 20 'http://status.csc436.com/'
```

The [captured response](D2-http-redirect-2026-11-24.txt) returned `301` and
`Location: https://status.csc436.com/`.

## Certificate

```powershell
'' | openssl s_client -connect status.csc436.com:443 -servername status.csc436.com -showcerts -verify_hostname status.csc436.com -verify_return_error 2>&1 | Select-String 'Certificate chain|^\s*\d+ s:|^\s*i:|^subject=|^issuer=|Verify return code|verify error'
'' | openssl s_client -connect status.csc436.com:443 -servername status.csc436.com 2>$null | openssl x509 -noout -subject -issuer -dates -ext subjectAltName
```

The [filtered chain output](D2-certificate-chain-filtered-2026-11-24.txt)
reports successful verification. The separately captured
[leaf details](D2-certificate-leaf-2026-11-24.txt) show the issuer, validity
dates, and covered hostnames. The chain output is deliberately filtered and
is not a complete raw TLS transcript; certificate and session data were not
retained in that file.

The [Cloudflare screenshot](D2-edge-certificates.png) shows an active
Universal certificate and a backup. Cloudflare documents
[automatic Universal SSL renewal](https://developers.cloudflare.com/ssl/edge-certificates/universal-ssl/).

## Certificate alert exercise

The manually dispatched [certificate monitor run](https://github.com/bycait27/incident-desk/actions/runs/45470699071)
on 2026-11-24 used a deliberately raised 365-day threshold and opened
[exercise issue #16](https://github.com/bycait27/incident-desk/issues/16). A [screenshot](D2-certificate-exercise.png) was captured and the issue was closed. The run logged an expiry of 2027-01-11 17:48:16 UTC for the production
app certificate. This was an intentional alert exercise, not an actual
14-day expiry alert.

## CAA

After adding CAA records, the authoritative nameserver was queried:

```powershell
dig '@seth.ns.cloudflare.com' csc436.com CAA +noall +comments +answer
```

The [captured response](D2-caa-authoritative-2026-11-24.txt) includes
`issue` and `issuewild` entries for Let's Encrypt and Google Trust Services,
alongside additional authorities returned by Cloudflare. The current public
edge certificate was issued by Let's Encrypt. I identified Railway,
Render, and Cloudflare as the services using this domain; their certificate
requirements should be reviewed again if other services are added.
