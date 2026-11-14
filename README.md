# Kharcha

A private money tracker for Android. Sheets, categories, any currency, repeats, pictures, stats
and trends. Everything is stored on the phone: no account, no server, nothing to buy.

Built with Kotlin, Jetpack Compose and Material 3 Expressive.

## Get the app

Download the latest build on an Android phone and open the file to install it:

https://github.com/nayan-dey/expenses/releases/download/preview/kharcha-preview.apk

Android will ask you to allow installing from your browser the first time. Newer builds install
over older ones and keep your data.

## What it does

- **Sheets**: separate ledgers (Personal, a trip, a shared flat), each in its own currency.
- **Items**: expenses, income and transfers, typed on a keypad that can also add, subtract,
  multiply and divide.
- **Any currency**: pay in dollars from a rupee sheet and it is converted. Rates download by
  themselves, or type your own.
- **Categories** with icons and colours, suggested as you type a note.
- **Pending and repeating items**, receipt **pictures**, **search** and **filters**.
- **Stats** by category, **trends** over time, a monthly **budget** per sheet.
- **Export and import** CSV, **backup** everything to one file, **share** a sheet as a file and
  merge it on another phone.
- **Privacy**: app lock, hidden amounts, blocked screenshots. The only thing the app goes online
  for is downloading exchange rates, and that can be switched off.

## For developers and AI agents

Read [`AGENTS.md`](AGENTS.md). It explains the architecture, the folders, the data rules, the
design system and how to build. Builds run on GitHub Actions; nothing needs to be installed.

## Credits

Material 3 and Material Symbols by Google (Apache 2.0). Plus Jakarta Sans by Tokotype (SIL Open
Font License). Exchange rates from fawazahmed0/exchange-api, with open.er-api.com as a fallback.
