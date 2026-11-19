# Staffdesk user guide

## Sign in
Your administrator provides an account and initial password. Start the native desktop
application and sign in. Employee records are distinct from login accounts: adding an
employee does not automatically grant access. No public self-registration exists.
Passwords and JWTs are not saved to a local file; sessions expire after 15 minutes by
default. When the server returns 401, sign in again. Signing out clears in-memory credentials;
a previously copied bearer token remains valid until expiry or administrator revocation.

## Find a colleague
Type a name or email in Search (Ctrl+F / Cmd+F focuses it). Search waits 350 ms after
keystrokes and runs on the server. Select a department/status to narrow the result.
Use Sort by and asc/desc, select rows per page, and use Previous/Next. An empty
search shows all permitted employees. Refresh reloads data and department choices.
Double-click a row to open details; viewers see a read-only form.

## Create (administrator/editor)
Click **Add employee**. Required: first/last names, valid unique work email,
department, job title, hire date and status. Phone is optional (digits, +, (), spaces,
periods and hyphens). Name maximum 80 characters each; department 80; job title
120; email 254; phone 32. Hire date cannot be in the future (server date is authoritative).
Choose ACTIVE, ON_LEAVE or INACTIVE. Save; errors keep the form open.

## Edit or delete
Click **Edit**, change values, Save changes. The form sends the version it opened.
If someone else saved meanwhile, the server rejects the stale edit with 409. Close,
refresh and reopen; your application will never silently overwrite that edit.
Click **Delete** and explicitly confirm to permanently remove an employee. There is
no undo or recycle bin. Prefer INACTIVE for former employees when retention is needed.

## Account access (administrator)
- Open **Account access** in the sidebar.
- **Add account**: email, initial password and role. Password minimum 12 characters,
  maximum 72 UTF-8 bytes (non-ASCII characters may use multiple bytes).
- **Manage**: change role or disable/enable the account. Email is immutable in this UI/API.
- **Reset password**: replace a password, then share it via an approved secure channel,
  not email/chat in plaintext. Existing sessions are immediately invalidated.
- Updating your own account or password requires you to sign in again.
- You cannot demote or disable your own administrator account. Maintain at least
  two administrators for operational resilience; at least one must remain enabled.
- Accounts cannot be hard-deleted through this interface; disable them for auditability.

| Capability | Viewer | Editor | Administrator |
|---|---|---|---|
| Search/read employee details | Yes | Yes | Yes |
| Create/edit/delete employees | No | Yes | Yes |
| Manage accounts/passwords | No | No | Yes |

Role checks are enforced on the server, not just by hiding controls. On errors, retain
the request ID shown in the dialog and provide it to support. Do not send passwords
or bearer tokens in support messages. Payroll/salaries, attachments, organizational
hierarchies and bulk import/export are outside this release’s scope.
