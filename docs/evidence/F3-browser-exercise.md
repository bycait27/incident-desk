# F3/F9 — Staging authenticated REST exercise

Before the exercise on 2026-11-24, we expected 201 and a `Location` for a viewer
submission, 403 for a viewer update, then 200, 400, 200, 409 and 204 for the
operator actions.

The browser sent same-origin `fetch` requests using the signed-in synthetic
sessions and a CSRF token obtained from `/api/auth/csrf`. The browser
returned the status, selected response headers, and body shown below; these
are **not raw HTTP or `curl -i` transcripts**. Cookies, CSRF tokens, and
account identifiers were not captured in this document. The actual staging
deployment SHA was not verified during this exercise.

## Viewer

The viewer submitted this synthetic body to `POST /api/incidents`:

```json
{"title":"Synthetic REST evidence exercise 2026-11-24","description":"Synthetic staging report created solely to demonstrate REST authorization and status codes."}
```

Observed status **201**, `Content-Type: application/json`, and
`Location: /api/incidents/7`. Response body:

```json
{"id":7,"title":"Synthetic REST evidence exercise 2026-11-24","description":"Synthetic staging report created solely to demonstrate REST authorization and status codes.","status":"OPEN","severity":"LOW","reporterId":null,"assignedTo":null,"published":false,"archived":false,"createdAt":"2026-11-24T20:16:15.082020654Z","updatedAt":"2026-11-24T20:16:15.082020654Z"}
```

The viewer attempted `PUT /api/incidents/7` using a valid update with
`status: "IN_PROGRESS"` and `severity: "LOW"`. Observed status **403**,
`Content-Type: application/json`:

```json
{"code":"FORBIDDEN","message":"The current role cannot perform this action."}
```

## Operator

Using the operator session, we verified that incident 7 was the new,
unpublished, unarchived test report before changing it. The valid update body
used was:

```json
{"title":"Synthetic REST evidence exercise 2026-11-24","description":"Synthetic staging report updated to demonstrate REST authorization and status codes.","status":"IN_PROGRESS","severity":"LOW","assignedTo":null}
```

1. `PUT /api/incidents/7` with that body returned **200** and:

   ```json
   {"id":7,"title":"Synthetic REST evidence exercise 2026-11-24","description":"Synthetic staging report updated to demonstrate REST authorization and status codes.","status":"IN_PROGRESS","severity":"LOW","reporterId":null,"assignedTo":null,"published":false,"archived":false,"createdAt":"2026-11-24T20:16:15.082021Z","updatedAt":"2026-11-24T20:21:38.582530705Z"}
   ```

2. The same `PUT`, changing only `status` to `"INVALID"`, returned **400**:

   ```json
   {"code":"VALIDATION_ERROR","message":"Request fields are invalid."}
   ```

3. `POST /api/incidents/7/publish` returned **200** and:

   ```json
   {"id":7,"title":"Synthetic REST evidence exercise 2026-11-24","description":"Synthetic staging report updated to demonstrate REST authorization and status codes.","status":"IN_PROGRESS","severity":"LOW","reporterId":null,"assignedTo":null,"published":true,"archived":false,"createdAt":"2026-11-24T20:16:15.082021Z","updatedAt":"2026-11-24T20:21:38.939986688Z"}
   ```

4. Repeating the publish returned **409**:

   ```json
   {"code":"CONFLICT","message":"Incident 7 cannot be changed in its current state."}
   ```

5. `DELETE /api/incidents/7` returned **204** with an empty body. A subsequent
   operator `GET /api/incidents/7` returned **404**:

   ```json
   {"code":"NOT_FOUND","message":"Incident not found with id: 7"}
   ```

All nonempty operator responses above had `Content-Type: application/json`.
The 400, 403, 404, and 409 bodies in this exercise share `code` and `message`
fields. This does not establish that every possible failure has that shape.

The operator session also received 404 for `GET /api/incidents/9223372036854775807`.
That endpoint excludes archived records even for operators. A later
[read-only database query](F3-staging-missing-id.md) confirmed the ID was
absent from the staging table at query time.

## Reproduction and limits

Sign into staging with a synthetic viewer account. Using the browser's
same-origin session, obtain `/api/auth/csrf` and submit the synthetic report;
attempt an operator-only update and retain its ID. Switch to a synthetic
operator, verify the ID refers to that report, and perform the actions above.
Archive **only** the report created for the exercise. Never copy session
cookies or CSRF tokens into the repository.

The [redacted curl transcript](F3-rest-transcript.txt) contains the
public-list, candidate-missing-ID and anonymous-create responses, plus a
separate authenticated exercise on synthetic incident 8. The viewer received
201 with `Location` and 403; the operator received 200, 400, 200, 409 and 204.
Incident 8 was archived. Request credentials and response cookie values are
not included.
