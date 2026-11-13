# 4. Stamps

[← Signatures](03-signatures.md) · [Contents](README.md) · [Next: Passports →](05-passports.md)

A stamp is an item that belongs to an organization or an official. It **can't be crafted**: someone draws it in the designer, then the administration approves it and hands it out.

## Getting a stamp

1. **Draw a design.** `/seal stamp designer` opens the designer. Choose:
   - the kind: stamp, border stamp or visa;
   - the shape: round, oval, rectangular or image only;
   - the ink colour, the text around the edge and in the centre, and the organization name.

   You can also upload a picture (a coat of arms or logo) from a link or from your disk.
2. **Submit it for approval.** The design joins a queue. One player can have up to 5 designs waiting at a time.
3. **The administration approves it:** `/seal stamp list pending` → `/seal stamp approve <id>`.
4. **The administration hands it out** to the right players: `/seal stamp give <id> <players>`.

> The stamp picture is downloaded only on the designer's own client. Only the finished pixels go to the server, so other players never open anyone's links. Limits: PNG/JPG/GIF/BMP, up to 6 MB, no local addresses.

## Placing a stamp

1. Hold the **stamp in your main hand** and the **document in your off hand**.
2. **Right-click** — the document opens in stamping mode.
3. Controls:

| Action | Input |
|---|---|
| Place the impression | **Left click** |
| Rotate | **Mouse wheel** or **Q / E** |
| Resize | **Ctrl + wheel** |
| Cancel | **Right click** |

One document takes **up to 6 stamps**. Every impression is recorded in the registry, so a check shows which stamp it is, who it was issued to and whether it has been revoked.

## Stamp on sealing wax

If **you have an organization stamp in your inventory** when you seal an envelope, the wax carries its impression. The recipient sees which organization sent the letter and whether it was opened. See [Mail](06-mail.md).

## Revoking and deleting (administration)

| Command | What it does |
|---|---|
| `/seal stamp list` | All stamps on the server |
| `/seal stamp revoke <id>` | Revoke it: it can't be used any more, and on older documents it shows as revoked |
| `/seal stamp delete <id>` | Remove it from the registry |

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `signedsealed.stamp.design` | everyone | Drawing designs |
| `signedsealed.stamp.apply` | everyone | Placing stamps |
| `signedsealed.stamp.issue` | OP only | Approving and handing out |
