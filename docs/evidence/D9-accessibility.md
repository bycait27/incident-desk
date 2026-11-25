# D9 — Accessibility checks

On November 24, 2026, I ran axe-core 4.14.1 against the local anonymous
board in headless Chrome. Before the changes, axe found two critical
unlabeled filters and two moderate landmark rules. After adding accessible
names and a main landmark, the final scan reported zero violations:
[before report](D9-axe-before.json) and [after report](D9-axe-after.json).

I also used automated keyboard key presses to refresh the board, search, and
operate both filters: [walkthrough output](D9-keyboard-walkthrough.txt). That
check covers the anonymous board and was run before the final error-boundary
addition.

In the [manual keyboard walkthrough](D9-keyboard-walkthrough.mp4), I used
the signed-in staging app to submit a report, search and filter incidents,
and update an incident as an operator. The recording starts after sign-in.

The final local scan used source commit
`5ffff212ead41ab25e8301488423e88a126bf79d` plus uncommitted frontend
changes. After deploying those changes to staging, I also ran axe against
the anonymous staging board; it reported zero violations
([report](D9-staging-axe-after.json)). These checks do not cover every
application state.
