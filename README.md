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
- Download a ready-to-use `business-menu-*.json` file that can be copied to the
  server configuration directory (for example
  `CONFIGURATIONS/business-menu.json`).

The default configuration file used by the tool lives at
`CONFIGURATIONS/business-menu.default.json` and represents the same order the
Telegram bot ships with today.
