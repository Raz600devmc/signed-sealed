# 12. Server administration

[← Forgery](11-forgery.md) · [Contents](README.md) · [Next: Recipes and FAQ →](13-faq.md)

## Admin panel

Press **K** or type `/seal admin`. Tabs:

| Tab | Contents |
|---|---|
| **Stamps** | Stamp designs awaiting approval, handing out, revoking |
| **Templates** | Passport, ID card, ticket and fine templates awaiting approval |
| **Registry** | Search every issued document and passport, revoke them |

The number on a tab shows how many requests are waiting.

## Setting up an RP server

1. Install the mod on the server and on every client. Versions must match, or the server refuses the client.
2. Install passport templates: `/seal passport preset <country>`, or draw your own with `/seal passport designer`.
3. Create organizations (city hall, police) and appoint managers with `/seal org`.
4. Approve or hand out organization stamps: `/seal stamp list pending` → `approve` → `give`.
5. Hand out passport blanks (`/seal passport give`), or let city hall print them on a printer.
6. Restrict permissions with LuckPerms if needed (see below).

## Permissions

The mod uses the standard Permission API, so permissions are managed with **LuckPerms** or a similar plugin. All nodes start with `signedsealed.`

| Node | Default | Allows |
|---|---|---|
| `document.create` | everyone | Creating documents |
| `document.sign` | everyone | Signing |
| `document.verify` | everyone | Verifying authenticity |
| `document.template` | everyone | Creating custom document templates |
| `stamp.apply` | everyone | Placing stamps |
| `stamp.design` | everyone | Drawing stamp designs |
| `stamp.issue` | OP | Approving and handing out stamps |
| `passport.fill` | everyone | Filling in blanks |
| `passport.issue` | OP | Passport templates, handing out blanks |
| `show` | everyone | `/seal show` |
| `forge` | everyone | Forgery kit |
| `print` | everyone | Printer, archives, folders |
| `newspaper.publish` | everyone | Publishing newspapers |
| `mail.send` | everyone | Sending letters |
| `mail.mass` | OP | Mass mailing |
| `archive.scan` | everyone | Scanner |
| `org.create` | everyone | Creating organizations |
| `admin` | OP | Everything: K panel, revoking, deleting, bypassing locks |

"OP" means operator level 2 or higher.

LuckPerms example — only the `criminal` group can forge documents:
```
/lp group default permission set signedsealed.forge false
/lp group criminal permission set signedsealed.forge true
```

## Revoking and deleting

| Command | Action |
|---|---|
| `/seal revoke` | Revoke the document in hand |
| `/seal passport revoke` | Revoke the passport in hand |
| `/seal stamp revoke <id>` · `delete <id>` | Revoke or delete a stamp |
| `/seal passport revoke <template>` · `delete <template>` | Revoke or delete a template |
| `/seal paper revoke` · `restore` | Revoke or reinstate a newspaper issue |

A revoked document stays with the player, but every check shows **"Revoked"**.

## Data and backups

- The registry is stored in `<world>/data/signedsealed.dat`; organizations, mail and block data in `<world>/data/signedsealed_office.dat`.
- Images (photos, logos, stamps, newspaper layouts) are stored in `<world>/signedsealed/`. The automatic backup does **not** copy this folder (it can be large), so include it in your regular world backups.
- **Automatic backups** (since 2.5.1):
  - copies go to `<world>/signedsealed_backups/<date>_<reason>/`;
  - they are made on startup, on shutdown, and every 30 minutes together with the world save;
  - the latest 20 copies are kept.
- `/seal backup` (permission level 3) makes a copy right now.
- **Automatic restore:** if `signedsealed.dat` is missing at startup, the mod takes the latest backup and logs that it did so.
- **Manual restore:** stop the server, copy both `.dat` files from the backup folder you want into `<world>/data/`, then start the server.

## Performance

- Pictures from links are downloaded **only by the author's client** during upload, and their size is checked. Everyone else receives the image from the server, so their IP addresses are never exposed.
- Notice boards with a newspaper feed query the registry at most once every 5 seconds per publication.
- Documents don't tick: they put no load on the world until someone uses them.
