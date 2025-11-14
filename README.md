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

### Wiring the generated configuration into the Telegram bot

The Java application now reads the business menu layout from a JSON file at
startup. By default it falls back to the baked-in resource
`classpath:config/business-menu.default.json`, which mirrors the hard-coded
order, so existing deployments keep their current behaviour.

To override the menu, point the `business-menu.config-path` property to the file
exported by the web app. Any Spring-supported location works:

```properties
# application.properties
business-menu.config-path=file:/opt/selfservice/CONFIGURATIONS/business-menu.json
```

Or via an environment variable:

```bash
BUSINESS_MENU_CONFIG_PATH=file:/opt/selfservice/CONFIGURATIONS/business-menu.json \
  java -jar selfservice-telegram-bot.jar
```

Once configured, the Telegram bot will render the buttons in the specified
order, using the translation key or label you defined in the no-code tool.
