---
description: Fix a GitHub issue by number, commit changes, and create a PR
argument-hint: [issue-number]
allowed-tools: Bash(gh *), Bash(git *), Read, Edit, Write
---

Fix GitHub issue $ARGUMENTS following our coding standards.

## Steps

1. **Read the issue** using `gh issue view $ARGUMENTS --json title,body,labels,assignees,number`
2. **Extract the task ID** from the issue title. Titles start with a task ID of the form
   `T-<digits>` (e.g. issue #11 is titled `T-010 — libs/share: money, weight and identity
   primitives` → task ID `T-010`). Take the first match of the regex `T-[0-9]+` in the title,
   exactly as written (keep leading zeros). The GitHub issue number is **not** the task ID.
   If the title has no `T-<digits>` prefix, use AskUserQuestion to ask the user for the task ID.
3. **Create a branch** named after the task ID: `git checkout -b feature/issue-<TASK_ID>`
   (e.g. `feature/issue-T-010`). If the branch already exists, switch to it with
   `git checkout feature/issue-<TASK_ID>` instead of creating it.
4. When solving a problem, you should always rely on:
 - Old project **dealboard**. Its code and architecture
 - README.md
 - Folder docs
 - Always look for a detailed description in the `tasks` folder: the file whose name starts with
   the task ID (e.g. `tasks/T-010-share-primitives.md`).
5. **Implement the feature** by reading relevant source files and making necessary edits
6. **Run tests** to verify the fix works (use the project's test command)
7. When creating a PR, use the issue title (e.g., 'T-010 — libs/share: money, weight and identity primitives').

If no issue number is provided, use AskUserQuestion to ask the user which issue to fix.
