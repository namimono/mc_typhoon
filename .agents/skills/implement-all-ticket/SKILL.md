---
name: implement-all-ticket
description: "Implement a Markdown spec's ready-for-agent tickets by delegating one subagent per ticket in dependency order, then mark each verified ticket ready for review. Use when tickets contain Status and Blocked by fields and the work must be completed sequentially with tests, review, commits, and status updates by agents using the implement skill."
---

# Implement All Tickets

Act as the main orchestrator. Read the ticket set, determine its dependency order, and
delegate each ticket to a subagent one at a time. Use the host environment's normal
subagent/delegation mechanism; do not assume a particular agent API or runtime.

## Prepare

1. Read the complete spec, ticket files, repository instructions, and relevant ADRs.
2. Confirm that the `$implement` skill is available to delegated agents.
3. Record the repository, branch, starting commit, and pre-existing working-tree changes.
   Keep unrelated changes out of ticket commits.
4. Run the bundled `scripts/plan_tickets.py` against the ticket directory and use its
   stable `order`. It parses `Status:` and `Blocked by:`, filters to `ready-for-agent`,
   and detects missing dependencies, cycles, and unsatisfied prerequisites.
5. Identify the repository's review-ready status. Use `ready-for-human` when that is the
   repository convention; otherwise use the explicitly defined equivalent. Do not invent
   a new status value.

Only dispatch `ready-for-agent` tickets. Treat `ready-for-human`, `done`, `completed`,
`complete`, `implemented`, and `merged` as already-delivered prerequisites. Stop and
report any other status that blocks an eligible ticket. If the resulting plan is empty,
report why and finish without delegating work.

## Delegate sequentially

For each ticket in the plan:

1. Create one subagent for that ticket with the host's standard delegation facility.
   State that it must use `$implement`, include the repository/spec/ticket paths and
   completed prerequisite context, and tell it to implement only the assigned ticket.
2. Require the subagent to follow `$implement`: use appropriate tests, run typechecking
   and focused tests during development, run the full test suite at the end, perform
   code review, and commit the ticket-scoped changes. As part of successful completion,
   require it to update the ticket's `Status:` from `ready-for-agent` to the review-ready
   status and record the commit, tests, and review result in the ticket's existing comment
   or progress format. Include this metadata in the ticket commit when the repository
   layout permits.
3. Wait for the subagent to finish before creating the next one, including for tickets
   that appear independent.
4. Verify the result from the repository state: inspect the commit and diff, tests,
   working tree, ticket status/comment, and any reported blockers. Record the ticket,
   subagent, commit, status transition, and verification evidence before continuing.

If a subagent fails, times out, requests clarification, or does not produce a clean
verified commit, stop the dependency chain. Ask the same subagent to fix a concrete
failure when possible; otherwise replace it only after checking the working tree. Never
start a dependent ticket while its prerequisite is incomplete, and never implement a
ticket directly as an unannounced fallback. Do not mark a failed or unverified ticket
review-ready; leave it `ready-for-agent` or use the repository's explicitly defined
blocked state.

## Finish

After all eligible tickets are complete:

- run the repository's documented final build, typecheck, and full test suite;
- inspect the combined diff from the starting commit and preserve unrelated changes;
- confirm every eligible ticket was delegated exactly once and every dependency was
  completed in this run or already delivered;
- confirm every successfully completed ticket has the expected review-ready status and
  implementation evidence;
- report commits, verification results, skipped/non-runnable tickets, and remaining
  risks. Do not claim success when required work or evidence is missing.

The main agent owns ordering and verification. Subagents own implementation and must use
`$implement` for their assigned ticket.
