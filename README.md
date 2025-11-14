# Selfservice Telegram Bot

## Business menu configuration web app

The `menu-config` folder contains a lightweight, no-code web application that
lets administrators design the entire logged-in business menu tree—root and
sub-menus—without touching the Java source.

### How to run it

1. Serve the repository (or the `menu-config` folder) with any static web
   server, for example:
   ```bash
   cd menu-config
   python -m http.server 8080
   ```
2. Open `http://localhost:8080` in your browser.

### Features

- Start from the default configuration that mirrors the menu currently bundled
  with the Telegram bot.
- Choose any existing menu (the root **Home** menu or any sub-menu you create),
  edit its name, and reorder its items.
- Add new sub-menus on the fly, then place buttons inside any menu via the
  dropdown that lists every available destination.
- Assign each button either to a Telegram function or to a sub-menu. When you
  delete a sub-menu that still has child entries, the tool warns you that the
  entire branch will be removed.
- Toggle the **Use built-in translation** switch per function button to either
  keep the localized label or force the exact custom text you typed.
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

1. Open the **Business Menu Builder** (see the “How to run it” section above).
   Use the **Edit menu** dropdown to choose where to work, add function buttons
   or sub-menu entries, and optionally uncheck **Use built-in translation** for
   any button that should always display your custom label.
2. Click **Download JSON** once you are happy with the hierarchy. Rename the
   downloaded file to `business-menu.override.json`.
3. Copy that file into the `CONFIGURATIONS/` directory that sits next to the
   Telegram bot jar.
4. Restart the application so Spring reloads the menu definition.

If you later delete `business-menu.override.json`, the bot automatically reverts
to `CONFIGURATIONS/business-menu.default.json` without requiring any other
changes.

### Runtime navigation behavior

- The bot always presents the buttons defined for the user’s current menu level.
- Entering a sub-menu keeps the user there until they tap a different option;
  executing a function no longer forces a jump back to the root menu.
- Sub-menus automatically include a **Home** button that returns to the root.
  When the user is more than one level deep, a second **Up** button appears next
  to **Home** so they can climb the menu stack one level at a time.
