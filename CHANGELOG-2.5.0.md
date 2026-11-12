# Signed & Sealed 2.5.0

## English

**Document reader**
- New wall block that reads passports, ID cards, tickets, documents and passes. A valid item gives a redstone pulse, like a button. It also powers the block the reader hangs on.
- The owner sets it up with Sneak + right-click (empty hand):
  - what it accepts;
  - an "accept only" filter (organization, type, name);
  - a zone;
  - "holder only";
  - the mode: check only / punch once / keep the document;
  - pulse length (1–30 s);
  - an access list with a lock.
- Green light means access granted. Red light means refused, with the reason on screen.
- The reader keeps a log of the last 12 attempts and counts passes.
- Recipe: iron, glass pane, redstone and paper.

**Passes and expiry dates**
- New document type "Pass".
- The editor has a new **Details** tab:
  - **Valid until** — a real-world date, dd.mm.yyyy. After it the document counts as expired everywhere: on verification, in the registry and at readers.
  - **Issued to** — only this player can use it at a reader.
  - **Zones** — a reader set to one of these zones accepts the pass.
  - **Public / private in the registry.**

**Registry search on the computer**
- Search by registration number. You see the status: valid, superseded (by which document, and when), revoked or expired.
- Documents are **private by default**. Strangers see only the status, type and date. The author, signers, holder, organization members and admins see everything and can open the document.

**Computer inbox — send documents between computers**
- Send the item in your main hand to players or to a whole organization (`@Org`).
- You choose:
  - **Original** — it leaves your hands and goes to one recipient.
  - **Certified copies** — 1 paper per recipient; you keep the original.
- You can attach a message, or ask the recipient to sign. The original stays in the office and comes back to you signed.
- The recipient takes the item, returns it to the sender, or deletes the entry. You get a reminder on login if you have new mail.

**A brand-new computer: «КанцОС 98»**
- A desktop with icons, a Start menu, a taskbar with a clock, and windows.
- Apps: Digital archive, Document registry, Inbox, Send document, Document viewer (shows the verification result), About.

**Notice board (3×2 blocks)**
- Hang it on a wall and pin up to 6 documents, newspapers, leaflets or envelopes. Right-click a spot with the item in hand.
- They are visible in the world.
- Right-click with an empty hand opens the board: read, take down, settings.
- The owner can set a caption and lock the board to an access list (names or `@Org`).
- **Feed:** enter a newspaper title and the board always shows its latest issue.
- Recipe: planks, paper, planks.

**Other**
- Text fields on the computer are now drawn cleanly, without a shadow.
- Minor layout fixes.
- Network protocol bumped: clients and servers must both be on 2.5.0.

---

## Русский

**Считыватель документов**
- Новый настенный блок. Читает паспорта, удостоверения, билеты, документы и пропуска. Если всё в порядке, даёт импульс редстоуна, как кнопка, и запитывает блок, на котором висит.
- Владелец настраивает его через Shift + ПКМ пустой рукой:
  - что принимать;
  - фильтр «принимать только» (организация, тип, ник);
  - зону;
  - «только владелец документа»;
  - режим: только проверять / компостировать один раз / забирать документ;
  - длительность сигнала (1–30 с);
  - список доступа и замок.
- Зелёный огонёк — проход разрешён. Красный — отказ, причина пишется на экране.
- Журнал последних 12 попыток и счётчик проходов.
- Рецепт: железо, стеклянная панель, редстоун, бумага.

**Пропуска и сроки действия**
- Новый тип документа «Пропуск».
- В редакторе новая вкладка **«Реквизиты»**:
  - **Действует до** — реальная дата, дд.мм.гггг. После неё документ недействителен везде: при проверке, в реестре и на считывателях.
  - **Кому выдан** — на считывателе пройдёт только этот игрок.
  - **Зоны** — считыватель с такой зоной примет пропуск.
  - **Публичный / закрытый в реестре.**

**Поиск по реестру на компьютере**
- Ищите по регистрационному номеру. Видно состояние: действует, заменён (каким документом и когда), отозван или истёк.
- Документы **по умолчанию закрытые**. Посторонний видит только статус, тип и дату. Автор, подписанты, владелец, сотрудники организации и админы видят всё и могут открыть документ.

**Входящие на компьютере — пересылка документов**
- Отправьте предмет из основной руки игрокам или всей организации (`@Организация`).
- Можно выбрать:
  - **Оригинал** — уходит из рук к одному получателю.
  - **Заверенные копии** — по 1 бумаге за каждого получателя, оригинал остаётся у вас.
- Можно добавить сообщение или запросить подпись: оригинал остаётся в канцелярии и вернётся к вам подписанным.
- Получатель забирает предмет, возвращает отправителю или удаляет запись. При входе на сервер приходит напоминание о новых письмах.

**Совершенно новый компьютер — «КанцОС 98»**
- Рабочий стол с иконками, меню «Пуск», панель задач с часами, окна.
- Программы: Цифровой архив, Реестр документов, Входящие, Отправить документ, Просмотр (с результатом проверки), О системе.

**Доска объявлений (3×2 блока)**
- Вешается на стену. Можно приколоть до 6 документов, газет, листовок или конвертов: ПКМ по нужному месту с предметом в руке.
- Всё видно прямо в мире.
- ПКМ пустой рукой открывает доску: читать, снять, настройки.
- Владелец задаёт надпись и может запереть доску для списка (ники или `@Организация`).
- **Лента:** впишите название газеты, и на доске всегда будет её последний выпуск.
- Рецепт: доски, бумага, доски.

**Прочее**
- Поля ввода на компьютере теперь рисуются чисто, без тени.
- Мелкие исправления вёрстки.
- Сетевой протокол обновлён: клиент и сервер должны быть на 2.5.0.
