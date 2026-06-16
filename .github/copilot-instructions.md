# Agent Rule

- At the beginning of each chat session, create a session file under `.github/sessions`.

- The session file name must follow this format: `session-YYYYMMDD-HHmmss.md`.

- If `.github/sessions` does not exist, create the directory first.

- The session file must not record the full conversation.

- The session file should only record the final working state, including:
    - Summary
    - Decisions made
    - Files changed
    - Validation performed
    - Pending items, if any

- If any decisions are made during the conversation, update the generated session file to reflect those decisions.

- Keep changes narrowly scoped to the user's request.

- Do not store secrets, credentials, tokens, or raw environment values in the session file or project documents.

- Use placeholders for environment variables where configuration examples are needed.

# Session Termination

When a session termination command is received, such as `/end-session`, `end session`, or `session end`:

1. Review the session file.
2. Update the project files listed below only when the session changes are relevant to them.
3. Write a Git commit message based on the final session contents.
4. Do not create an actual Git commit unless explicitly requested.
5. Delete the session file after the project documents and commit message are finalized.

# Project Files

- `README.md`: A high-level overview of the project. List and describe the main features.

# Commit Message

- The commit message should be concise and based on the session result.
- Prefer this format:

```text
type(scope): summary

- Detail 1
- Detail 2
```

- Use common types such as `docs`, `feat`, `fix`, `refactor`, `chore`, or `test`.
