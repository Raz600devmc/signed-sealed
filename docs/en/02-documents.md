# 2. Documents

[← Quick start](01-quick-start.md) · [Contents](README.md) · [Next: Signatures →](03-signatures.md)

## The form

A **Document Form** is crafted from paper and an ink sac. A blank form can be edited as often as you like until it is issued. Right-click with it to open the editor.

## The document editor

The editor has two tabs.

### "Document" tab

| Field | What to enter | Limit |
|---|---|---|
| **Type** | Switch with the ◀ ▶ arrows | — |
| **Number** | Internal number, e.g. `42/2026` | 20 characters |
| **Your position** | "Mayor", "Chief of Police"… | 60 |
| **Organization** | Who issues the document | 80 |
| **Subject** | What the document is about | 140 |
| **Document text** | The text itself | 2400 |

**Document types:**

- **Free-form document** — no header, lay it out however you like.
- **Resolution, Order, Certificate, Contract, Pass** — built-in templates with a title and styling.
- **Organization templates** — custom forms with a logo, colours and default text (see [Organizations](09-organizations.md)).

The draft is saved automatically: you can close the editor and come back later.

### "Details" tab

When any field here is filled in, a dot `•` appears on the tab.

| Field | What it does |
|---|---|
| **Refers to (reg. no.)** | Links related documents, e.g. `RD-000012`. Visible in the registry and on the document. |
| **Supersedes (reg. no.)** | This document **replaces** an older one. Once issued, the old one is marked "superseded" in the registry and fails checks. You can only supersede your own documents or your organization's. |
| **Valid until** | A real date `dd.mm.yyyy`. After it the document counts as **expired** everywhere: on checks, in the registry and at readers. |
| **Issued to** | The holder's name. A reader will only let this player through. |
| **Zones** | Comma-separated: `Depot, Site-1`. A reader set to one of these zones accepts the pass. |
| **Public / Private in registry** | Documents are **private** by default: other people see only the status, type and date in the registry. Public shows the details to everyone. |

## Issuing

Press **"Sign and issue"**, draw your signature, and the document:

- gets a **registration number** `RD-xxxxxx`;
- is written to the server registry with a digital signature;
- can **no longer be edited** — to change it, issue a new document that supersedes it.

A document can carry **up to 4 signatures** and **up to 6 stamps**. How to collect other people's signatures is explained in [Signatures](03-signatures.md).

## What you can do with an issued document

| Action | How |
|---|---|
| Read | Right-click with the document |
| Inspect up close | **V** key (see [Verification](11-forgery.md)) |
| Check authenticity | Document in hand → `/seal verify` |
| Show it to a player | `/seal show <player>` — your document opens on their screen |
| Stamp it | Stamp in main hand, document in off hand → right-click |
| Copy it | [Printer](08-office.md#printer) — the copy is marked "COPY" |
| Send it | In an [envelope](06-mail.md) or through the [computer inbox](08-office.md#inbox) |
| Pin it up | [Notice board](07-newspapers.md#notice-board) |
| Revoke it | Document in hand → `/seal revoke` (author or administration) |
| Destroy it | [Shredder](08-office.md#shredder) — the document is annulled in the registry |

## Document statuses

| Status | Meaning |
|---|---|
| ✅ **Authentic** | In the registry, the server signature checks out |
| 📄 **Copy** | Made on a printer, not an original |
| 🔁 **Superseded** | A newer document replaces it |
| ⛔ **Revoked** | Revoked by the author or the administration |
| ⌛ **Expired** | The "Valid until" date has passed |
| ❌ **Forgery** | Not in the registry, or its data don't match the registry |
