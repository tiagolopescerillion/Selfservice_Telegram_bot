# Selfservice Telegram Bot

## Business menu configuration web app

The `menu-config` folder contains a lightweight, no-code web application that
lets administrators rearrange or extend the root items of the logged-in
business menu without touching the Java source.

### How to run it

1. Serve the repository (or the `menu-config` folder) with any static web
   server, for example:
   ```bash
   cd menu-config
   python -m http.server 8080
   ```
2. Open `http://localhost:8080` in your browser.

### Features

- Start from the default configuration that mirrors the menu currently hard
  coded inside `TelegramService`.
- Edit labels, reorder items, or assign different functions from the dropdown.
- Import an existing JSON configuration file for further adjustments.
- Download a ready-to-use JSON file. Rename it to
  `business-menu.override.json` and copy it to the
  `CONFIGURATIONS/` folder alongside the Telegram bot to override the menu.

The default configuration file used by the tool lives at
`CONFIGURATIONS/business-menu.default.json` and represents the same order the
Telegram bot ships with today.

### Wiring the generated configuration into the Telegram bot

On startup the Java application looks for
`CONFIGURATIONS/business-menu.override.json`. If that file exists, its contents
define the order and labels of the logged-in menu. If it is missing, unreadable,
or empty, the bot automatically falls back to
`CONFIGURATIONS/business-menu.default.json`. As a last resort it will use the
packaged resource at `classpath:config/business-menu.default.json`, which keeps
the legacy hard-coded order alive.

### Step-by-step: applying a custom layout

1. Open the **Business Menu Builder** (see the “How to run it” section above)
   and click **Download JSON** once you are happy with the menu.
2. Rename the downloaded file to `business-menu.override.json`.
3. Copy that file into the `CONFIGURATIONS/` directory that sits next to the
   Telegram bot jar.
4. Restart the application so Spring reloads the menu definition.

If you later delete `business-menu.override.json`, the bot automatically reverts
to `CONFIGURATIONS/business-menu.default.json` without requiring any other
changes.
