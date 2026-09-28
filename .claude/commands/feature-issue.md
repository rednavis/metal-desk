---
description: Fix a GitHub issue by number, commit changes, and create a PR
argument-hint: [issue-number]
allowed-tools: Bash(gh *), Bash(git *), Read, Edit, Write
---

Fix GitHub issue $ARGUMENTS following our coding standards.

## Steps

1. **Read the issue** using `gh issue view $ARGUMENTS --json title,body,labels,assignees,number`
2. **Create a branch**: `git checkout -b feature/issue-$ARGUMENTS`
3. When solving a problem, you should always rely on:
 - Old project **dealboard**. Its code and architecture
 - README.md
 - Folder docs
 - Always look for a detailed description in the `tasks` folder, using `$ARGUMENTS` in the filename.
4. **Implement the feature** by reading relevant source files and making necessary edits
5. **Run tests** to verify the fix works (use the project's test command)

If no issue number is provided, use AskUserQuestion to ask the user which issue to fix.
