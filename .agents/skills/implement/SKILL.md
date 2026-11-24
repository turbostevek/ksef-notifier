---
name: implement
description: "Implement a piece of work based on a spec or set of tickets."
disable-model-invocation: true
---

Implement the work described by the user in the spec or tickets.

If the user passes a ticket reference, fetch it from the issue tracker and state its title before starting. If the reference is ambiguous, ask.

Before making changes, create a new task branch from an up-to-date `main`. If existing changes or local commits on `main` prevent a clean starting point, ask the user how to handle them; preserve their work.

Call the Skill tool with "tdd" where possible, at pre-agreed seams.

Run typechecking regularly, single test files regularly, and the full test suite once at the end.

Once done, call the Skill tool with "code-review" to review the work.

Commit your work to the task branch, push it, and create a PR targeting `main`. Do not merge without the user's request.

After a requested PR merge, check out `main` and pull it. Start the next implementation on a new task branch.
