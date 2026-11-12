# Signed & Sealed 2.5.1

## English
- **Automatic backups of the signing key.** `signedsealed.dat` holds the key that verifies every document. It is now copied to `<world>/signedsealed_backups/` on server start, on stop and every 30 minutes (last 20 kept).
- **Auto-restore:** if `data/signedsealed.dat` disappears, the latest backup is restored on startup with a clear warning in the console.
- **`/seal backup`** (op level 3) — make a backup right now.
- **Network version is now MAJOR.MINOR (`2.5`)**: patch updates like 2.5.x no longer require everyone to update at once. On a mismatch the disconnect message shows which versions clash.
- **Safer web images:** the declared resolution is checked before decoding, so a small file claiming a huge size can't freeze the client.
- **Performance:** notice boards that follow the same newspaper share one registry lookup every 5 seconds.

## Русский
- **Автобэкапы ключа подписи.** В `signedsealed.dat` хранится ключ, которым проверяются все документы. Теперь он копируется в `<мир>/signedsealed_backups/` при запуске, при остановке и каждые 30 минут (хранятся последние 20).
- **Автовосстановление:** если `data/signedsealed.dat` пропал, при запуске восстанавливается последний бэкап, в консоли — заметное предупреждение.
- **`/seal backup`** (op 3) — сделать бэкап прямо сейчас.
- **Сетевая версия теперь MAJOR.MINOR (`2.5`)**: патчи 2.5.x больше не требуют обновлять всех одновременно. При несовпадении в сообщении об отключении видно, какие версии конфликтуют.
- **Безопаснее с картинками по ссылке:** разрешение проверяется до распаковки, маленький файл с огромным разрешением не повесит клиент.
- **Производительность:** доски объявлений с одной газетой делают один общий поиск по реестру раз в 5 секунд.
