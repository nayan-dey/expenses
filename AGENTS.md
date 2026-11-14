# Kharcha: guide for AI agents and contributors

This is the single source of truth about this repo for any AI agent or new contributor.
`CLAUDE.md` only imports this file, so edit this one.

Last verified: 2026-11-14, against `main` after the second batch of features (friends, insights,
habit, reminders, widgets, look options).

## 1. Keep this file true

This file is maintained as part of the work, not after it. The rules:

1. **Same commit.** If a change makes any line here wrong or incomplete, fix that line in the
   same commit as the change. A task is not done while this file is out of date.
2. **Code wins.** If this file and the code disagree, trust the code, then correct this file
   before doing anything else.
3. **Facts and plans stay apart.** Things that exist are written as facts. Things that do not
   exist yet are marked **Planned**. When a planned thing is built, move it to fact and remove
   the marker. Never describe a plan as if it were built.
4. **Stamp it.** After updating, change the "Last verified" line above (date and commit) and add
   one line to the change log in section 11.
5. **Keep it short.** Delete what is no longer true or no longer useful. Do not let it grow into
   a history book; git has the history.

What to update when:

| When you change | Update |
|---|---|
| A feature is added, finished or removed | Feature table in section 3 |
| A data class, a field, a file on disk or a data rule | Section 7 |
| A library, version, folder or layer | Section 6 |
| A design token, component choice or design rule | Section 5 |
| A screen or its wording | The matching step in `WalkthroughTest.kt` |
| How to build, test, release or get an APK | Section 8 |
| An open question gets an answer | Remove it from section 10, record the answer where it belongs |
| The owner states a new rule or preference | Section 4 |

## 2. What the app is

Kharcha ("expense") is a personal money tracker for Android. It was built to give Android what
the iOS app "Expenses: Spending Tracker" (Blue Comet Labs) gives iPhones: the owner used that
app, liked it, and wanted the same on Android. Kharcha matches its features. It does not copy
its code, name, icon or look.

What the owner does with it:

- Keep **sheets** (separate ledgers: Personal, a trip, a shared flat), each in its own currency.
- Add **expenses, income and transfers** quickly, with a category, a note, a merchant, a date,
  and receipt pictures.
- See **totals** for a period, **stats** by category and **trends** over time.
- Get data **in and out**: CSV, a full backup file, one sheet as a file to share.

The owner is Nayan (`nayan-dey` on GitHub), based in India: rupees and UPI are the everyday
case, but every currency works.

## 3. Where things stand

One branch, `main`, in `nayan-dey/expenses` (a **public** repo). The working copy is at
`~/Documents/safaguardd/nayan-reverse-eng-apps/kharcha`.

"Written" means the code exists and compiled. "Seen" means it was seen working in walkthrough
screenshots on a cloud emulator. Nothing has been tried on a real phone by an agent.

| Feature | State |
|---|---|
| Sheets: name, currency, total balance or spent, total period, per-sheet form options | Written |
| Sort sheets (manual, edited, created, name), collapse the list | Written |
| All sheets added up in the preferred currency (home hero card) | Written |
| Items: expense, income, transfer between sheets | Written |
| Amount keypad with a calculator (+ − × ÷) | Written |
| Any currency per item, exchange rate frozen on the item, rate editable per item | Written |
| Exchange rates: downloaded, hand-set rates, converter screen | Written |
| Categories: icon (about 190 to choose from), colour, expense or income | Written |
| Category suggestions on the item form (by note, then most used) | Written |
| Merchant, with past merchants offered | Written |
| Pending items (kept out of totals, own tab) | Written |
| Repeating items (daily to yearly), filled in when the app opens | Written |
| Pictures on items (camera or gallery), viewer, share | Written |
| Search (one sheet, or every sheet from home) and filters | Written |
| Select several items: delete, move to another sheet, change category, pending | Written |
| Monthly budget per sheet with a progress bar | Written |
| Stats: ring chart by category, daily average, largest, compared with last month | Written |
| Trends: bars by day, week, month, year; balance over time | Written |
| CSV export (optionally a ZIP with pictures) and CSV import with category review | Written |
| Backup to one file, restore, and merge (latest edit wins) | Written |
| Share one sheet as a file, merge it on another phone | Written |
| Trash for sheets, categories and items; empties itself after 30 days | Written |
| Print a sheet or save it as PDF | Written, cannot be tested on an emulator |
| App lock (fingerprint, face or PIN), block screenshots, sensitive mode | Written, lock cannot be tested on an emulator |
| Light and dark theme, phone (wallpaper) colours, haptics | Written |
| Six launcher icons to choose from | Written |
| Friends: split a bill (who paid, split with), who owes whom, settle up, remind | Written |
| Pay a friend back through any UPI app (amount and UPI address filled in) | Written, cannot be tested on an emulator |
| "Paid with" on items: UPI, cash, card, bank | Written |
| Insights: last 7 or 30 days against the stretch before, with what a change is worth | Written |
| "Ask Gemini": a summary you read first, then share with Gemini or any app | Written, the hand-over cannot be tested on an emulator |
| Daily streak drawn as a tree that grows through six stages | Written |
| Saving and investing goals: target, monthly amount, "done for this month" | Written |
| Daily reminder notification (weekly round-up on Sundays, monthly on the 1st) | Written, timing cannot be tested on an emulator |
| Daily automatic backup to Downloads/Kharcha (Android 10 and newer) | Written, not seen running |
| Two home-screen widgets: "today" and "this month" | Written, not seen on a home screen |
| Look: four typefaces, text size, six accent colours, pure black, corner style | Written |
| Receipt scanning that fills in the form | Not started (see section 10) |
| Live sync between phones | Not started (see section 10) |
| Languages other than English | Not started |

## 4. Rules from the owner

Do not reopen these unless the owner does.

- **Native Android**: Kotlin and Jetpack Compose.
- **Material 3 Expressive**, using Google's real components. Never hand-draw a control that
  Material already provides. The look should be as good as the sibling apps Kerb and Galla.
- **Local first, "no db, no nothing".** All data lives on the phone in plain files. There is no
  database, no account, no sign-up and no server. The app must be fully usable offline, forever.
- **Everything is unlocked.** Features the iOS app sells as "Pro" are simply part of the app.
  There are no purchases and no locked features.
- **It is built around how the owner lives.** He pays almost everything by UPI, goes out with
  the same five or six friends and loses track of who owes whom, and spends on food and friends
  instead of saving. The app must make splitting a bill quick, show where money could be saved,
  and build a daily habit (streak, tree, reminder, goals) that leads towards saving and investing.
- **No investment advice.** The app tracks goals and says how much could be set aside. It never
  names a fund, a share or a product.
- **Match the features, not the artwork.** Kharcha has its own name, icon, colours and layout.
  Do not copy the iOS app's branding or screens pixel for pixel.
- **Builds never run on the owner's laptop.** No JDK or Android SDK is installed there and none
  should be. Build and test through GitHub Actions (section 8).
- **This file is kept up to date by whoever changes the code** (section 1).
- Explain things in plain language. Avoid jargon, or explain it the first time it appears.

## 5. Design system

The identity is **"iris and marigold on ink"**. Tokens live in
`app/src/main/java/com/kharcha/app/ui/theme/`.

- **Colour** (`Color.kt`): surfaces are a near-black with a hint of indigo in dark, a cool paper
  white in light. **Iris**, a blue-violet, is the brand: the one big number on a screen sits on
  an iris "hero" card. **Marigold** is the accent for things waiting for attention (pending, a
  budget nearly used). Money in is mint green, money out is coral; red is kept for delete and
  "over budget". `KharchaExtras` holds the colours Material has no role for.
- **Type** (`Type.kt`): variable fonts in `res/font`. Plus Jakarta Sans is the default; Manrope,
  Figtree and Space Grotesk can be chosen. All four draw ₹ themselves, which is why they were
  picked. Use `MaterialTheme.typography` or `KharchaType` (hero, figure, amount, eyebrow, key);
  both follow the chosen typeface. No one-off sizes, except the hero amount, which shrinks by
  length so it always fits one line. Never name a font family in a screen.
- **Shape** (`Theme.kt`): soft and round, radii 8 / 14 / 22 / 28 / 36. A sheet is shown as its
  first letter on a shape from Material's shape library; a category is a coloured circle with
  an icon.
- **Motion**: `MotionScheme.expressive()`. Amounts roll when they change, chart bars spring up,
  the ring chart sweeps round, the shape in the hero card turns slowly.
- **Phone colours**: an optional setting swaps iris for the wallpaper palette (Android 12 and
  newer). Iris is the default.
- **Look options** (Settings, Appearance; `Look` in `Theme.kt`): theme (automatic, light, dark),
  pure black for the dark theme, six accent colours, four typefaces plus the phone's own, four
  text sizes, three corner styles. The other accents are not separate palettes: `withAccent`
  turns every iris-tinted colour of the scheme to another hue, so one scheme stays the source.
- **The tree** (`ui/habit/Tree.kt`): the streak as a plant in six stages, at 0, 1, 3, 7, 14 and
  30 days. It is an illustration, drawn by hand like the charts.
- **Widgets** cannot use Compose or the theme: they are XML layouts (`res/layout/widget_*.xml`)
  in fixed brand colours, filled in by `platform/Widgets.kt`.
- **The mark**: a ring chart with one slice lifted out. Launcher icon:
  `res/drawable/ic_launcher_foreground*.xml`; in the app: `KharchaLogo` in `Basics.kt`.

Rules:

- Use Google's real Material components. `ui/components/` only picks a component and fills its
  slots; it draws nothing by hand except the three charts, the logo and the streak tree, which
  Material has no component for.
- Inside a list operation (`map`, `joinToString`, `remember { }`) a composable such as `money()`
  cannot be called. Read `LocalSensitive.current` once outside and use `Money.format` inside.
- Use the Expressive version where one exists: `HorizontalFloatingToolbar` with its FAB (the
  bar at the bottom of home and of a sheet), buttons and icon buttons with `shapes`,
  `ToggleButton` groups, `SegmentedListItem`, `TopAppBar` with subtitle,
  `LinearWavyProgressIndicator`, `LoadingIndicator`, `MaterialShapes`. Otherwise use the
  standard Material 3 one (switch, cards, text fields, sliders, snackbar, dialogs, bottom
  sheets, date and time pickers, menus).
- Before using a Material API not already used in this repo, check its exact signature in the
  library source (Material 3 `1.5.0-rc01`; the sources jar is on Google's Maven). There is no
  local compiler to catch a wrong guess.
- Every amount shown on screen goes through `money()` or `moneySigned()` in `Basics.kt`, so
  "Sensitive mode" can hide it.
- Haptics go through `rememberHaptics()` and follow the phone's touch-feedback setting.
- The root `Surface` sets the content colour once, so icons and text are never dark on dark.
- Pickers are bottom sheets (`Sheets.kt`), not separate screens, so a form never loses its place.

## 6. Architecture

One Gradle project at the repo root, single `app` module, package and app id `com.kharcha.app`.

- **Stack**: Kotlin, Jetpack Compose, Navigation 3, kotlinx.serialization (JSON), coroutines
  and Flow, Material 3 `1.5.0-rc01`. Versions are in `gradle/libs.versions.toml`.
- **No database.** No Room, no SQLite, no DataStore. See section 7.
- **No dependency-injection framework.** `AppContainer` builds everything once.
- **Almost no ViewModels.** The whole data set is in memory as a `StateFlow`, so a screen is a
  plain composable that collects it and works out what to show with `remember`. Form state uses
  `rememberSaveable` (with `jsonSaver` for whole objects). The only ViewModel is `NavViewModel`,
  which keeps the back stack and the snackbar host across rotation.
- **One network call.** `data/RatesRepository.kt` downloads exchange rates. Nothing else uses
  the internet and nothing is ever uploaded. Keep it that way. ("Ask Gemini" and "Pay by UPI"
  hand text to another app through Android's share and view intents, when the person taps them.)
- **Background work without a server**: one daily alarm (`platform/Reminders.kt`, `AlarmManager`,
  inexact, no special permission). `DailyReceiver` calls `AppContainer.dailyTick`, which fills
  in repeats, writes the automatic backup, posts the reminder, redraws the widgets and sets the
  next alarm. `BootReceiver` sets the alarm again after a restart.
- **Taps from outside** (a widget, a notification) reach `MainActivity` as an intent action
  listed in `platform/Launch`; `KharchaRoot` then opens the right screen.

| Folder (under `app/src/main/java/com/kharcha/app/`) | Job |
|---|---|
| `core/model/Models.kt` | Every stored class: `Sheet`, `Item`, `Category`, `AppData`, `Settings`, `Rates` |
| `core/util/` | `Money`, `Dates` (periods, repeats), `Calc` (the keypad's calculator), `Csv`, `Currencies`, ids |
| `data/JsonFile.kt` | The storage layer: one value, one JSON file, a `StateFlow` in memory |
| `data/Ledger.kt` | The rules for changing sheets, categories and items |
| `data/Exchange.kt` | Backup, shared sheet, CSV out and in |
| `data/RatesRepository.kt`, `ImageStore.kt`, `Seed.kt` | Exchange rates; receipt pictures; default categories and sample data |
| `domain/Summaries.kt` | Pure sums: totals, day groups, filters, search, stats, trends, budget, who owes whom, the streak |
| `domain/Insights.kt` | The "where could I save" rules, the text for Ask Gemini, the quick look used by home, widgets and the reminder |
| `platform/` | Android-only bits: app lock, launcher icon switch, printing, sharing, the daily alarm and notification (`Reminders`, `Nudges`), automatic backup (`AutoExport`), widgets (`Widgets`) |
| `ui/<screen>/` | One folder per group of screens |
| `ui/components/`, `ui/theme/`, `ui/icons/` | The design system |
| `ui/nav/` | Routes (one key per screen), the `Navigator`, snackbar messages |
| `ui/KharchaRoot.kt` | Theme, welcome screen, lock, and the list of every screen |

Icons are Material Symbols Rounded as vector drawables, listed in `ui/icons/Ic.kt`. To add one,
drop its outline and filled XML in `res/drawable` (without the `android:tint` line) and add its
name to the enum. Category icons are stored by name in the data file, so never rename or remove
an entry. `ui/icons/IconGroups.kt` decides which icons the category picker offers.

The launcher icon is chosen with `activity-alias` entries in the manifest (one per icon, one
enabled at a time); `MainActivity` has no launcher entry of its own.

## 7. Data rules

Three JSON files in the app's private folder, each wrapped by a `JsonFile`:

| File | Holds |
|---|---|
| `kharcha.json` | `AppData`: sheets, categories, items, friends, goals, "nothing spent" days |
| `settings.json` | `Settings` |
| `rates.json` | `Rates`: downloaded and hand-set exchange rates |

Pictures are JPEG files in `images/` beside them.

- **Saving is safe.** A change is applied in memory at once and written a moment later: to a
  new file first, which then replaces the old one, with the previous file kept as `.bak`.
- **The files must stay readable forever.** Every new field needs a default. Never rename or
  remove a field or an enum entry. `KharchaJson` ignores unknown fields, so an older app can
  open a newer file.
- **Money is whole minor units** (paise, cents) in a `Long`, with its currency code. Never
  floating point. `Money` knows how many decimals each currency has.
- **An item keeps its own exchange rate** (`Item.rate`: sheet-currency units per one unit of the
  item's currency), frozen when it is saved, so old totals do not move when rates change.
  Changing a sheet's currency re-prices its items and is refused if a rate is missing.
- **Deleting is soft.** `deletedAt` marks a thing as in the trash. Only "Empty trash", or 30
  days passing, removes it and its pictures.
- **A transfer is two items** (an expense in one sheet, an income in the other) sharing a
  `transferId`. They are always saved, changed and deleted together. Stats and trends leave
  transfers out; a sheet's balance counts them.
- **Pending items** are listed under "Pending" and left out of every total.
- **Two views of money.** `totals()` is the cash view: what really left or reached the pocket.
  `spendTotals()` and `Item.own()` are the spending view: what something cost YOU. Sheet
  balances use the cash view; stats, trends, budgets and insights use the spending view.
- **Split bills.** On an expense you paid, `splits` lists what each friend owes back; your own
  share is the rest. The cash view counts the full amount, the spending view only your share.
- **A bill a friend paid** has `payer` set and its amount is your share. It counts as spending
  but is left out of the cash view, because nothing has left your pocket yet.
- **Settling up** is an item with `settles` set (income = they paid you, expense = you paid
  them). It moves the cash balance and nothing else: like a transfer, it is not spending or
  income. `Item.isMovement` is true for both transfers and settle-ups.
- **Who owes whom** is never stored. `AppData.dues()` works it out from items every time:
  plus their share of bills you paid, minus your share of bills they paid, adjusted by
  repayments, per currency.
- **The streak** is not stored either. A day counts when an item was created on it
  (`Item.createdAt`) or it is listed in `noSpendDays`. `AppData.streak()` counts back from today.
- **Goals** only keep count (`Goal.deposits`). They do not move money between sheets.
- **Repeats**: exactly one item per chain carries the `repeat` rule. When the app opens,
  `Ledger.materializeRepeats` writes the copies that came due and hands the rule to the newest.
- **Ids are random UUIDs** made on the phone, except the default categories, which have fixed
  ids so two fresh installs can merge without duplicates.
- **Merge** (`Ledger.merge`): for anything both sides have, the one with the later `editedAt`
  wins. This is the only "sync" there is.
- **Time** is UTC milliseconds. "Today" and "this month" are worked out in the phone's timezone.
- **A Kharcha file** (backup or shared sheet) is a ZIP with `kharcha.json` (`KharchaFile`,
  `format = 1`) and an `images/` folder.

## 8. Build and test

- **Never try to compile locally.** The owner's machine has no JDK or Android SDK.
- Push to `main` on `nayan-dey/expenses`. `.github/workflows/android.yml` builds the release
  APK and publishes it to the rolling `preview` release:
  https://github.com/nayan-dey/expenses/releases/download/preview/kharcha-preview.apk
- Pushes that change only `.md` files do not start a build.
- To see the screens, push a commit whose message contains `[shots]`.
  `.github/workflows/walkthrough.yml` runs `app/src/androidTest/.../WalkthroughTest.kt` on a
  cloud emulator and uploads screenshots as the `kharcha-walkthrough` artifact
  (`gh run download <run id> -R nayan-dey/expenses`).
- The walkthrough uses UI Automator (tap by on-screen text). Do not wait for the app to go
  "idle": the turning hero shape never stops, so that wait would hang.
- When a screen or its wording changes, update the matching step in `WalkthroughTest.kt`.
- A build that compiles is not a screen that works. Say which screens were actually seen.
- The signing key in `keystore/` is a throwaway so preview builds install over each other. It
  is public, like the repo, and is not a Play Store key.
- Do not commit screenshots or exports of the owner's real data: the repo is public.

## 9. The iOS app, as a reference (behaviour only)

What was learned from the owner's screenshots. Use it to understand what is expected, not how
it should look.

- Home lists sheets (name, total, last edited, item count), with search and a + button.
- A sheet shows a summary card (currency, period, total, expense, income), a list tab and a
  "Pending" tab, then items grouped by day with a daily total.
- The item form: Expense / Income / Transfer, amount with a calculator keypad, currency, notes,
  merchant, category with suggestion chips, sheet, date, time, pending, repeat, image.
- Periods: As of today, This year, This month, This week, Today, Custom.
- Settings: Pro, Sync (iCloud), Categories, Export, Import, Trash, Appearance, App icon, Sort
  sheets by, Print settings, Preferred currency, Privacy, Sensitive mode, About.
- Its sheet menu: Stats, Trends, Exchange rate, Select, Print, Export, Import, Edit sheet.

Where Kharcha differs on purpose: backup and merge instead of iCloud sync; a floating toolbar
instead of a bottom search bar; budgets, an all-sheets total, a trash for items, duplicate, and
"last month" as a period are additions.

## 10. Open questions

Remove each one when it is answered, and record the answer in the right section.

1. **App name and app ID.** "Kharcha" and `com.kharcha.app` were chosen by the agent. Changing
   the app ID later makes it a different app on the phone, so settle it before real use.
2. **Receipt scanning.** Not built: the usual on-device library (Google ML Kit) sends usage
   statistics to Google, which breaks the "nothing leaves the phone" rule. Decide whether that
   trade is acceptable or whether to go without.
3. **Sync.** The iOS app syncs through iCloud. Kharcha has backup, merge and shared-sheet files.
   Real live sync needs a server or a cloud drive login. Is file-based merging enough?
4. **CSV from the iOS app.** The importer matches column names loosely, but it has not been
   tried with a real export from the iOS app.
5. **UPI.** Payments could be logged automatically by reading bank SMS, but that needs a
   sensitive permission. Until the owner asks for it, UPI is a tag you set on the item.
7. **Smarter insights.** The rules in `domain/Insights.kt` are fixed sums. Which categories count
   as "essential" is a fixed list of the default category ids; it should become a per-category
   switch if the owner's own categories are judged wrongly.
8. **Widgets follow the theme?** They use fixed brand colours. Following light and dark, or the
   chosen accent, needs separate layouts or Glance.
6. **Shrinking the APK.** R8 is off (`isMinifyEnabled = false`). Turn it on once the app has
   been used on real phones.

## 11. Change log

Newest first. One line per change to this file.

- 2026-11-14: Second batch built: friends and splits, paid-with, insights, Ask Gemini, streak
  tree, goals, reminder, automatic backup, widgets, look options. Sections 3 to 7 and 10 updated.
- 2026-11-14: File created with the first version of the app. Second batch of features (friends,
  widgets, reminders, insights, streak, goals, auto-export) recorded as planned.
