# 5. Passports, ID cards, tickets and fines

[← Stamps](04-stamps.md) · [Contents](README.md) · [Next: Mail →](06-mail.md)

All four are **blanks made from a template**. The administration (or an organization) designs the template, players receive empty blanks and fill them in themselves.

| Format | Used for |
|---|---|
| **Passport** | A booklet with spreads, a photo, a coat of arms and pages for visas and border stamps |
| **ID card** | A card: staff ID, driving licence, employee pass |
| **Ticket** | Travel or event entry. Works with the [ticket gate](10-access.md#ticket-gate) and the [document reader](10-access.md#document-reader) |
| **Fine** | A fine receipt |

## Templates

### Creating a template (administration)
1. `/seal passport designer` opens an empty designer; `/seal passport designer <country>` opens a ready-made country template.
2. Presets: Germany, France, Italy, Spain, Poland, United Kingdom, Netherlands, Sweden, Norway, Finland, Ukraine, Czechia, Greece, Portugal, Switzerland, Austria, Turkey.
3. In the designer you set:
   - the format, colours, coat of arms and captions;
   - the **fields** (name, date of birth, number, or your own), their layout, whether they are required, and their hints;
   - the photo area.
4. The template is sent for approval.

### Managing templates

| Command | What it does |
|---|---|
| `/seal passport list` · `list pending` | All templates / those awaiting approval |
| `/seal passport approve <template>` | Approve |
| `/seal passport edit <template>` | Open the template in the designer |
| `/seal passport preset <country>` | Install and approve a country template in one step |
| `/seal passport revoke <template>` | Revoke: no new blanks can be filled from it |
| `/seal passport delete <template>` | Remove it from the registry |

## Handing out blanks

- Administration: `/seal passport give <template> <players>` — the players receive **empty blanks**.
- An organization or the administration can also **print blanks on a printer**. Each passport blank costs a **book**; other formats cost a **sheet of paper** (see [Printer](08-office.md#printer)).

## Filling in

1. Take an empty blank and **right-click**.
2. Fill in the fields. Required ones are marked.
3. **Take a photo** — it is captured from your character.
4. **Sign** — draw your signature.
5. The document is issued and written to the registry.

The permission to fill in blanks is `signedsealed.passport.fill` (everyone by default).

## Using them

| Action | How |
|---|---|
| Open | Right-click |
| Show to another player | `/seal show <player>` |
| Verify | `/seal verify` with the passport in hand |
| Add a visa or border stamp | A stamp of the "Visa" or "Border stamp" kind, placed like any stamp |
| Revoke (admin) | Passport in hand → `/seal passport revoke` |
| Pass a gate or reader | Right-click the block with the ticket or ID card in hand |

A used ticket gets a **"Punched"** mark, so it can't be used again.
