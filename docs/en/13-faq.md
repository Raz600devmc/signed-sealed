# 13. Recipes and FAQ

[← Administration](12-admin.md) · [Contents](README.md)

## Recipes

Notation for the 3×3 grid: each row is a row of the crafting table, a space is an empty slot.

### Items

| Item | Recipe | Output |
|---|---|---|
| **Document Form** | Paper + ink sac (shapeless) | 1 |
| **Envelope** | `P P` / `PPP` — paper | 2 |
| **Newspaper Sheet** | 2 paper + ink sac (shapeless) | 1 |
| **Document Folder** | Leather + 2 paper + string (shapeless) | 1 |
| **Sealing Wax** | Honeycomb + red dye (shapeless) | 4 |
| **Forgery Kit** | `FIP` / `LLL` — F feather, I ink sac, P paper, L leather | 1 |
| **Stamp** | No recipe — issued by the administration (`/seal stamp give`) | — |
| **Passport / blank** | No recipe — issued by the administration or printed on a printer | — |

### Blocks

| Block | Recipe | Key |
|---|---|---|
| **Printer** | `III` / `PRP` / `ICI` | I iron, P paper, R redstone, C black dye |
| **Shredder** | `IRI` / `SSS` / `I I` | I iron, R redstone, S shears |
| **Scanner** | `GGG` / `IRI` | G glass, I iron, R redstone |
| **Computer** | `IGI` / `IRI` / `CCC` | I iron, G glass pane, R redstone block, C stone button |
| **Mailbox** | `III` / `ICI` / ` I ` | I iron, C chest |
| **Archive Shelf** | `PPP` / `SBS` / `PPP` | P planks, S wooden slab, B book |
| **Filing Cabinet** | `III` / `ICI` / `III` | I iron, C chest |
| **Notice Board** | `WWW` / `PPP` / `WWW` | W planks, P paper |
| **Ticket Gate** | ` R ` / ` I ` / `III` | R redstone, I iron |
| **Document Reader** | `IGI` / `IRI` / `IPI` | I iron, G glass pane, R redstone, P paper |

All recipes also show up in JEI/REI/EMI if you have one installed.

## FAQ

**Does the mod need to be on the client?**
Yes, the mod is needed on the server and on every player's client, with matching versions.

**Does it work in singleplayer?**
Yes. You are the administrator of your own world. The **K** panel and all commands are available when cheats are enabled.

**Why does my document say "Forgery" when I never forged it?**
Most likely it was carried over to another server or world. Every world has its own registry.

**I lost a document. Can it be restored?**
If it is in a digital archive or an inbox, print a copy on a [computer](08-office.md#scanner-and-computer-digital-archive). The original itself can't be reissued: ask the author to issue a new document that **supersedes** the old one (the "Supersedes (reg. no.)" field in the "Details" tab).

**Why won't my picture link load?**
Use a direct link to a PNG/JPG file, not to a web page. Pictures that are too large (over 12000 px per side) are rejected. The easiest way is to drag the file straight into the game window.

**Why can't I use the printer or archive?**
The owner has locked the block. Ask to be added to its allowed list or to its organization. It is also possible that the administration has taken away your `signedsealed.print` permission.

**How do I save a document to my computer?**
The viewer has **"Save PDF"** and **"Save page as PNG"** buttons. The files appear in the game folder under `signedsealed_exports/`.

**The server crashed and all documents are gone!**
Since 2.5.1 the mod restores the registry from the latest backup on its own. See [Administration](12-admin.md#data-and-backups).

**Is there a Fabric version?**
Not yet: only NeoForge 1.21.1 and Forge 1.20.1.

**Where do I report a bug or suggest an idea?**
On the mod's Discord server (link on the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/signed-sealed)) in the bugs and suggestions channels, or in [GitHub Issues](https://github.com/Raz600devmc/signed-sealed/issues).

---

[← Contents](README.md)
