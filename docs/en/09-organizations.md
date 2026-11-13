# 9. Organizations

[← Office](08-office.md) · [Contents](README.md) · [Next: Access control →](10-access.md)

An organization is a city hall, police force, bank, newspaper office or company. It has an **owner**, **managers** and **members**.

## Creating one

- `/seal org create <name>`, or `/seal org` → "Create".
- The name needs at least 2 characters and can't contain `@`, `,` or `;`.
- A player can own **up to 5** organizations (no limit for the administration).
- An organization has up to **200** people.
- Permission: `signedsealed.org.create` (everyone by default).

## Roles

| Role | Can |
|---|---|
| **Owner** | Everything: rename, appoint managers, transfer the organization to another player, delete it |
| **Manager** | Change the member list, run the organization's mailing lists and templates, configure blocks that list `@Organization` |
| **Member** | Use the organization's blocks and receive mail sent to `@Organization` |

Manage it with `/seal org`. Added players get a notification. You can only add players who have joined the server at least once.

## Where organizations are used

- **Mail:** `@CityHall` in the recipient field reaches every member ([Mail](06-mail.md)).
- **Computer inbox:** sending to `@Organization`.
- **Blocks:** `@CityHall` in the allowed list of a printer, archive, reader, board and so on ([Office](08-office.md)).
- **Organization stamps** and **wax impressions** ([Stamps](04-stamps.md)).
- **Document templates:** `/seal template` opens your own form design — header, PNG logo up to 512 px, frame, paper, fonts, default subject and text.
  - An organization's template is saved by one of its managers.
  - Each owner can keep up to 20 templates.
  - Permission: `signedsealed.document.template`.
- **Passport and ID blanks:** the organization named in the template can print them.

Deleting an organization deletes its mailing lists; its templates stay but are no longer tied to it.
