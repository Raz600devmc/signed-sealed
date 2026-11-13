# 11. Forgery and verification

[← Access control](10-access.md) · [Contents](README.md) · [Next: Administration →](12-admin.md)

Forgery is part of the game: spies, smugglers, con artists. The mod lets players forge, and gives the authorities ways to catch them.

## Forgery Kit

Recipe: `FIP` / `LLL` — feather, ink sac, paper, leather.

1. Hold a **genuine issued** document, passport, ID card, ticket or fine **in your off hand**.
2. **Right-click** with the kit.
3. Change what you want: number, position, organization, subject, text. For a passport you can insert a different photo (from a link or a file).
4. Press **"Make forgery"**. It costs a **sheet of paper** for a document or a **book** for a passport.

### Fake stamp
In the same window, under "Stamps on the document", pick an impression to create a **fake stamp** of that organization. It costs an **iron ingot**. The fake stamp works, but checks recognise its impressions as fake.

Permission: `signedsealed.forge`. Everyone has it by default; serious RP servers often grant it only to "criminal" roles.

## Catching a forgery

A forgery looks identical to the eye. It is given away by:

| Method | How |
|---|---|
| **Registry check** | `/seal verify` with the document in hand, the "Check authenticity" button in the viewer, or a [document reader](10-access.md#document-reader). The data is compared with the registry record |
| **UV light** | The "UV light" button in the document window. A genuine document shows hidden security marks |
| **Magnifier** | The "Magnifier" button: microtext and fine details |
| **Security code** | A unique code on the genuine document, checked against the registry |
| **Inspection (V key)** | 3D inspection of the held document: mouse rotates, wheel zooms, Space/right-click opens or flips it |

## Verification results

| Result | Meaning |
|---|---|
| **Authentic** | Matches the registry |
| **Forgery** | The data or an impression doesn't match the registry |
| **Revoked** / **Annulled** | Was valid, but revoked by the administration or destroyed in a shredder |
| **Not in registry** | The document isn't from this server |
| **Copy** | A certified copy, not the original |
| **Draft** | Never issued, so it has no force |
| **Outdated version** | Signatures or stamps were added to the document after this copy was made, so only the current version is valid |
| **Expired** | The "Valid until" date has passed |
