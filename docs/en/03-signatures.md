# 3. Signatures

[← Documents](02-documents.md) · [Contents](README.md) · [Next: Stamps →](04-stamps.md)

## Hand-drawn signatures

A signature is **drawn with the mouse** in a dedicated window. It isn't text or a picture from the internet, so every signature is unique, just like in real life. A scribble that is too short (fewer than a few points) is rejected.

The signature goes onto the document together with the signer's name and position, and the server records **who actually signed**. Drawing a look-alike signature won't fool anyone: a registry check shows the real signer.

## Several signers

A contract, an order with approvals, an agreement between parties — up to **4 people** can sign one document.

### If the person is nearby
Hand them the document. They right-click it and add their signature.

### If the person is far away — a signature request
1. First **issue the document and sign it yourself**. Only the author or someone who has already signed can request signatures.
2. Hold the document and type `/seal sign ask <name1>, <name2>` (names separated by commas). The players must have joined the server at least once; they don't have to be online.
3. The document goes to the office, and the signers get a notification (plus a reminder when they log in).
4. Each signer opens the request with `/seal sign list` → `/seal sign open <id>` and signs it.
5. When everyone has signed, the document comes back to you.

A document holds **up to 4 signatures** in total, including yours.

| Command | What it does |
|---|---|
| `/seal sign list` | Your incoming and outgoing requests |
| `/seal sign open <id>` | Open a request and sign |
| `/seal sign decline <id>` | Refuse to sign |
| `/seal sign cancel <id>` | Cancel your own request |

You can also request signatures **from a computer**: in "Send document", tick "Request signature" (see [Inbox](08-office.md#inbox)).

## Who can sign

The signing permission is `signedsealed.document.sign` (everyone has it by default). The administration can restrict it with a permissions plugin such as LuckPerms — see [Permissions](12-admin.md#permissions).
