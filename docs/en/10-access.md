# 10. Access control: ticket gates and document readers

[← Organizations](09-organizations.md) · [Contents](README.md) · [Next: Forgery →](11-forgery.md)

Both blocks emit a **redstone signal** when access is granted, so you can wire them to doors, pistons or gates.

## Ticket gate

Recipe: ` R ` / ` I ` / `III` — redstone and iron.

It accepts **tickets only**. Walk up and **right-click** with a ticket in hand.

**Settings** (Shift + right-click with an empty hand, owner):

| Setting | Meaning |
|---|---|
| Name | Written into the punch mark on the ticket |
| Filter | Comma-separated words. A ticket matches if any of them appears in its template name, organization or ID. Empty means any ticket |
| Mode | **Punch** (one pass per ticket) · **Keep ticket** · **Check only** |
| Signal | 1–10 seconds |
| Lock and allowed list | As on every block |

The gate refuses tickets that are invalid (revoked, forged, missing from the registry), already punched, or not matching the filter.

## Document reader

Recipe: `IGI` / `IRI` / `IPI` — iron, glass pane, redstone, paper.

It accepts **passports, ID cards, tickets and documents** (for example passes) and keeps a **log** of the last 12 attempts.

**Settings:**

| Setting | Meaning |
|---|---|
| Accepts | Checkboxes: Passports / ID cards / Tickets / Documents & passes |
| Filter | Comma-separated words, matched against the template, organization, number, holder and subject |
| **Zone** | For example `Warehouse`. Only a **document** whose "Details" tab lists this zone gets through |
| Holder only | The document must be issued to the person presenting it (on by default) |
| Mode | **Check only** · **Punch (single use)** · **Keep the document** |
| Signal | 1–30 seconds |

**Reasons for refusal:**
- the document is invalid, expired or issued to someone else;
- there is no access to the zone;
- it is a copy (the original is needed) or a draft;
- this kind of document isn't accepted, or it doesn't match the filter.

## Example: a warehouse pass

1. City hall issues a document from the **Pass** template. In "Details" they set the holder to `Alex`, the zone to `Warehouse` and an expiry date.
2. A reader at the warehouse door accepts "Documents & passes", with zone `Warehouse` and mode "Check only".
3. Alex presents the pass, the reader sends a signal, and the door opens. A pass that belongs to someone else, has expired or was revoked won't work.
