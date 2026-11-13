# 1. Quick start

[← Contents](README.md) · [Next: Documents →](02-documents.md)

## Installation

- The mod is needed **on the server and on every player's client** — the screens open on the client.
- Versions: **NeoForge 1.21.1** or **Forge 1.20.1**. Client and server must both be on the same `2.5.x` line.
- Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/signed-sealed).

## The key idea: everything is checked against the registry

Every server has its own **secret key** (stored in `signedsealed.dat` in the world folder). When a document is issued, the server signs it with that key and writes it to the **registry**. As a result:

- copying a document through NBT, creative mode or another mod gets you nowhere — the check shows a forgery;
- a revoked, superseded or expired document is spotted immediately;
- a document from another server fails the check here.

Any player can check a document: hold it and type **`/seal verify`**.

## What to craft first

| Item | Recipe | What for |
|---|---|---|
| **Document Form** | Paper + ink sac (shapeless) | Any document: certificate, order, contract, pass… |
| **Envelope** ×2 | `P P` / `PPP` (paper) | Sending letters and documents |
| **Sealing Wax** ×4 | Honeycomb + red dye | Sealing an envelope |
| **Document Folder** | Leather + 2 paper + string | Keeping a stack of documents in one slot |
| **Newspaper Sheet** | 2 paper + ink sac | Newspaper, leaflet or poster |

Blocks (printer, shredder, mailbox, archive, scanner, computer, ticket gate, reader, notice board) are covered in [Office](08-office.md) and [Access control](10-access.md). The full recipe table is in the [FAQ](13-faq.md#recipes).

> **Stamps and passports can't be crafted.** The administration issues them: a stamp is drawn in the designer and approved, a passport comes from an approved template. See [Stamps](04-stamps.md) and [Passports](05-passports.md).

## Your first document in a minute

1. Craft a **Document Form** and right-click with it.
2. Pick a type (◀ ▶ arrows), then fill in the number, your position, the organization, the subject and the text.
3. Press **"Sign and issue"** and draw your signature with the mouse.
4. The document gets a registration number like `RD-000042` and is written to the registry.
5. Want a stamp on it? Hold the stamp in your main hand and the document in your off hand, right-click, then click where the impression should go.

## Useful keys

| Key | Action |
|---|---|
| **V** | Inspect the held item up close: rotate, zoom, UV light, magnifier |
| **K** | Admin panel (admins only) |

Keys can be changed in *Options → Controls → Signed & Sealed*.
