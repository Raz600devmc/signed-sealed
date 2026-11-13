# 8. Office equipment and storage

[← Newspapers](07-newspapers.md) · [Contents](README.md) · [Next: Organizations →](09-organizations.md)

**Rules shared by all mod blocks**

- Whoever places a block becomes its **owner**.
- **Shift + right-click with an empty hand** opens the settings (owner only): name, **lock**, and the list of allowed people.
- The allowed list takes comma-separated names and `@Organization`. That organization's managers count as owners of the block, and its members count as allowed.
- Only the owner or an OP can break a locked block.

## Printer

Recipe: `III` / `PRP` / `ICI` — iron, paper, redstone, black dye.

**Right-click** the printer:

| What | Needs | Result |
|---|---|---|
| **Copy a document** | An issued document in hand + 1 paper | A copy marked **"COPY"**; it is not an original |
| **Blanks** | A passport/ID card/ticket/fine template. 1 **book** per passport blank, 1 **paper** per other blank | Empty blanks. Only the template's organization or the administration may print them |
| **Newspaper print run** | A published issue in hand + paper | Up to 64 copies (see [Newspapers](07-newspapers.md#print-run)) |

Passports, newspapers and letters can't be copied.

Permission: `signedsealed.print`.

## Shredder

Recipe: `IRI` / `SSS` / `I I` — iron, redstone, shears.

Hold a document, passport, newspaper, letter or stamp in your main hand and **right-click** the shredder. The item is **destroyed**, and a document is also **annulled in the registry**: any later check shows that it was destroyed.

## Archive Shelf and Filing Cabinet

| Block | Recipe | Feature |
|---|---|---|
| **Archive Shelf** | `PPP` / `SBS` / `PPP` (planks, wooden slab, book) | Shows what it holds |
| **Filing Cabinet** | `III` / `ICI` / `III` (iron, chest) | Holds more |

You can put in one item or **everything** from your inventory at once, take items back, or **view** a document without taking it out.

## Document Folder

Recipe: leather + 2 paper + string.

- Holds up to **16** documents, passports, newspapers and letters.
- With a document in your off hand, **right-click** with the folder to put it in. You can also put in every document from your inventory at once.
- A folder can be locked.

## Scanner and computer (digital archive)

| Block | Recipe |
|---|---|
| **Scanner** | `GGG` / `IRI` — glass, iron, redstone |
| **Computer** | `IGI` / `IRI` / `CCC` — iron, glass pane, redstone block, stone button |

**Scanner.** Hold a document, passport, newspaper, opened letter or folder in your main hand and **right-click**. A copy goes into the **digital archive** named in the scanner's settings (Shift + right-click). One archive holds up to 5000 records. Newspaper drafts and sealed letters can't be scanned.

**Computer.** It connects to the archive with the same name.
- **Search** the archive (up to 100 results) and view a record.
- **Print** a record as a paper copy marked "COPY". This works for documents only.
- **Delete** a record (owner only).

### Inbox

Every player has an **inbox** that opens from any computer.

- **Send document:** hold what you want to send and enter a name or `@organization`.
  - The **original** goes to one recipient; everyone else gets a **certified copy** (1 paper each).
  - Passports, letters and folders have no copies, so they can only be sent as originals.
  - Tick **"Request signature"** and the recipient signs the document and sends it back (see [Signatures](03-signatures.md)).
- The recipient is told that a new document is waiting in their inbox.
- Inbox actions: **take** the original, **print a copy**, **return to sender**, **delete**.
- An inbox holds up to 60 entries.
