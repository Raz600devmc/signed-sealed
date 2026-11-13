# 7. Newspapers, leaflets and posters

[← Mail](06-mail.md) · [Contents](README.md) · [Next: Office →](08-office.md)

## Publication types

| Type | Description |
|---|---|
| **Newspaper** | A numbered issue of up to **8 pages** |
| **Leaflet** | A single-page issue |
| **Poster** | Hangs on a wall |

## Your first issue

1. Craft a **Newspaper Sheet** (2 paper + ink sac) and **right-click** — the **layout editor** opens.
2. Enter the **publication name** and choose the type.
3. Lay out the pages:
   - blocks: headline, article, picture, rules;
   - columns, line spacing, drop cap, alignment;
   - in articles: `**bold**`, `__italic__`, and `# ` at the start of a line for a subheading;
   - PNG/JPG pictures can be **dragged from a folder** straight into the game window. A single issue holds up to 32 pictures, each up to 1024 px;
   - mouse wheel zooms, right mouse button pans the sheet;
   - Ctrl+Z undoes, Ctrl+D duplicates a block, Del deletes it;
   - a red "+" means the text doesn't fit its block.
4. Press **Save** (draft) or **Publish**.

When the first issue is published, the name is **registered to you**, and nobody else can publish under it without your permission. A published issue never changes.

## Print run

- Take a published issue and print copies on the [printer](08-office.md#printer).
- At most **64** copies per run.
- Each copy costs **ceil(pages / 2)** sheets of paper: 1–2 pages cost 1 sheet, 8 pages cost 4.
- Only the publication's staff can print runs.

## The next issue

Open your issue and press **"Layout for the next issue"** (costs a sheet of paper). The layout is copied and the issue number goes up by 1.

## Editorial staff

| Command | What it does |
|---|---|
| `/seal paper titles` | List the publications |
| `/seal paper staff add <player>` | Add a staff member (hold an issue or layout of the publication) |
| `/seal paper staff remove <name>` | Remove a staff member (hold an issue or layout) |
| `/seal paper revoke` · `restore` | Revoke or reinstate the held issue (admin) |

## Reading and subscriptions

- **Right-click** a newspaper to read it. The window has "Check authenticity", "Magnifier", "Save PDF" and "Save page as PNG" buttons.
- **Subscribing:** `/seal paper subscribe <publication>` or the "Subscribe / unsubscribe" button. To unsubscribe: `/seal paper unsubscribe <publication>`.
- Staff send new issues to subscribers by [mail](06-mail.md) addressed to `@subscribers`.

## Poster

To hang a poster, Shift + right-click a wall.

## Notice board

The **Notice Board** (`WWW` / `PPP` / `WWW`: planks and paper) hangs on a wall and has **6 slots**.

- **Right-click** with a document, newspaper, letter or passport to pin it.
- **Right-click** with an empty hand to open the board, view items or take them down.
- **Shift + right-click** with an empty hand (owner) opens the settings: name, lock, who may pin, and the **newspaper feed**.
- With a feed set, the first slot always shows the **latest issue** of the chosen publication.

## Forged newspapers

"Check authenticity" compares the copy with the registry. An issue that isn't in the registry, or whose imprint doesn't match it, is flagged as a **FORGERY**.
