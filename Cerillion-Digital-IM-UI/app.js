const MENU_TYPES = {
  LOGIN: "login",
  BUSINESS: "business"
};

const ROOT_MENU_ID = "home";
const LOGIN_ROOT_MENU_ID = "login-home";

const BASE_FUNCTION_OPTIONS = [
  {
    id: "HELLO_WORLD",
    label: "Hello World",
    callbackData: "HELLO_WORLD",
    translationKey: "ButtonHelloWorld",
    description: "Sends the Hello World welcome message."
  },
  {
    id: "HELLO_CERILLION",
    label: "Hello Cerillion",
    callbackData: "HELLO_CERILLION",
    translationKey: "ButtonHelloCerillion",
    description: "Sends the Hello Cerillion greeting."
  },
  {
    id: "VIEW_TROUBLE_TICKET",
    label: "Trouble Ticket",
    callbackData: "VIEW_TROUBLE_TICKET",
    translationKey: "ButtonTroubleTicket",
    description: "Shows the most recent ticket information."
  },
  {
    id: "SELECT_SERVICE",
    label: "Select a Service",
    callbackData: "SELECT_SERVICE",
    translationKey: "ButtonSelectService",
    description: "Displays the list of available services for an account."
  },
  {
    id: "MY_ISSUES",
    label: "My Issues",
    callbackData: "MY_ISSUES",
    translationKey: "ButtonMyIssues",
    description: "Lists the tickets associated with the selected account."
  },
  {
    id: "DIGITAL_LOGIN",
    label: "Self-service login",
    callbackData: "SELF_SERVICE_LOGIN",
    translationKey: "ButtonSelfServiceLogin",
    description: "Starts the digital self-service login flow."
  },
  {
    id: "CRM_LOGIN",
    label: "Direct login",
    callbackData: "DIRECT_LOGIN",
    translationKey: "ButtonDirectLogin",
    description: "Performs a direct CRM login."
  },
  {
    id: "SETTINGS",
    label: "Settings",
    callbackData: "SETTINGS_MENU",
    translationKey: "ButtonSettings",
    description: "Opens the settings sub-menu."
  },
  {
    id: "OPT_IN",
    label: "Consent management",
    callbackData: "OPT_IN",
    translationKey: "ButtonOptIn",
    description: "Manages user consent preferences."
  },
  {
    id: "CHANGE_LANGUAGE",
    label: "Language settings",
    callbackData: "CHANGE_LANGUAGE",
    translationKey: "ButtonChangeLanguage",
    description: "Lets the user pick a language."
  },
  {
    id: "MENU",
    label: "Back to menu",
    callbackData: "MENU",
    translationKey: "ButtonMenu",
    description: "Returns to the previous menu."
  },
  {
    id: "LOGOUT",
    label: "Logout",
    callbackData: "LOGOUT",
    translationKey: "ButtonLogout",
    description: "Ends the authenticated session."
  }
];

const DEFAULT_STRUCTURE = [
  {
    id: ROOT_MENU_ID,
    name: "Home",
    parentId: null,
    items: [
      { label: "Hello World", type: "function", function: "HELLO_WORLD", useTranslation: true },
      { label: "Hello Cerillion", type: "function", function: "HELLO_CERILLION", useTranslation: true },
      { label: "Trouble Ticket", type: "function", function: "VIEW_TROUBLE_TICKET", useTranslation: true },
      { label: "Select a Service", type: "function", function: "SELECT_SERVICE", useTranslation: true },
      { label: "My Issues", type: "function", function: "MY_ISSUES", useTranslation: true }
    ]
  }
];

const DEFAULT_LOGIN_STRUCTURE = [
  {
    id: LOGIN_ROOT_MENU_ID,
    name: "Home",
    parentId: null,
    items: [
      { label: "Self-service login", type: "function", function: "DIGITAL_LOGIN", useTranslation: true },
      { label: "Direct login", type: "function", function: "CRM_LOGIN", useTranslation: true },
      { label: "Settings", type: "submenu", submenuId: "login-settings" }
    ]
  },
  {
    id: "login-settings",
    name: "Settings",
    parentId: LOGIN_ROOT_MENU_ID,
    items: [
      { label: "Consent management", type: "function", function: "OPT_IN", useTranslation: true },
      { label: "Language settings", type: "function", function: "CHANGE_LANGUAGE", useTranslation: true },
      { label: "Back to menu", type: "function", function: "MENU", useTranslation: true }
    ]
  }
];

function getConfigFetchPaths() {
  const monitoringBase = getConfiguredMonitoringApiBase();
  const apiOverride = monitoringBase ? new URL("/menu-config", monitoringBase).toString() : null;
  const apiDefault = monitoringBase ? new URL("/menu-config/default", monitoringBase).toString() : null;

  return {
    override: [
      apiOverride,
      "/menu-config",
      "CONFIGURATIONS/IM-menus.override.json",
      "IM-menus.override.json"
    ],
    default: [
      apiDefault,
      "/menu-config/default",
      "CONFIGURATIONS/IM-menus.default.json",
      "IM-menus.default.json"
    ]
  };
}

const functionDictionary = {};
const functionOptions = [];

function registerFunctionOption(option) {
  if (!option?.id) return;
  if (functionDictionary[option.id]) return;
  const normalized = {
    description: "",
    translationKey: null,
    callbackData: option.id,
    ...option
  };
  functionDictionary[normalized.id] = normalized;
  functionOptions.push(normalized);
}

BASE_FUNCTION_OPTIONS.forEach(registerFunctionOption);

const menuTypeSelect = document.getElementById("menuTypeSelect");
const menuContainer = document.getElementById("menuContainer");
const menuSelect = document.getElementById("menuSelect");
const parentMenuSelect = document.getElementById("parentMenuSelect");
const menuNameEditor = document.getElementById("menuNameEditor");
const createMenuButton = document.getElementById("createMenuButton");
const deleteMenuButton = document.getElementById("deleteMenuButton");
const addItemForm = document.getElementById("addItemForm");
const itemLabelInput = document.getElementById("itemLabelInput");
const menuFunctionSelect = document.getElementById("menuFunctionSelect");
const itemTypeSelect = document.getElementById("itemTypeSelect");
const useTranslationInput = document.getElementById("useTranslationInput");
const functionFields = document.getElementById("functionFields");
const submenuFields = document.getElementById("submenuFields");
const submenuSelect = document.getElementById("submenuSelect");
const inlineCreateSubmenu = document.getElementById("inlineCreateSubmenu");
const resetButton = document.getElementById("resetButton");
const downloadButton = document.getElementById("downloadButton");
const preview = document.getElementById("preview");
const importInput = document.getElementById("importInput");
const navMenuConfig = document.getElementById("navMenuConfig");
const navOperationsMonitoring = document.getElementById("navOperationsMonitoring");
const navSendMessages = document.getElementById("navSendMessages");
const navImServerAdmin = document.getElementById("navImServerAdmin");
const navServiceFunctions = document.getElementById("navServiceFunctions");
const menuConfigurationPanel = document.getElementById("menuConfigurationPanel");
const operationsMonitoringPanel = document.getElementById("operationsMonitoringPanel");
const sendMessagesPanel = document.getElementById("sendMessagesPanel");
const imServerAdminPanel = document.getElementById("imServerAdminPanel");
const serviceFunctionsPanel = document.getElementById("serviceFunctionsPanel");
const serviceFunctionsTableBody = document.getElementById("serviceFunctionsTableBody");
const serviceFunctionsStatus = document.getElementById("serviceFunctionsStatus");
const addServiceFunctionButton = document.getElementById("addServiceFunctionButton");
const newServiceFunctionForm = document.getElementById("newServiceFunctionForm");
const newServiceFunctionName = document.getElementById("newServiceFunctionName");
const newServiceFunctionEndpoint = document.getElementById("newServiceFunctionEndpoint");
const newServiceFunctionAccountContext = document.getElementById("newServiceFunctionAccountContext");
const newServiceFunctionServiceContext = document.getElementById("newServiceFunctionServiceContext");
const newServiceFunctionQueryParams = document.getElementById("newServiceFunctionQueryParams");
const saveNewServiceFunction = document.getElementById("saveNewServiceFunction");
const cancelNewServiceFunction = document.getElementById("cancelNewServiceFunction");
const downloadQueryParamsButton = document.getElementById("downloadQueryParamsButton");
const liveSessionsContainer = document.getElementById("liveSessions");
const sessionHistoryContainer = document.getElementById("sessionHistory");
const monitoringApiBaseInput = document.getElementById("monitoringApiBase");
const monitoringApiBaseMeta = document.querySelector("meta[name='operations-api-base']");
const adminApiBaseMeta = document.querySelector("meta[name='admin-api-base']");
const notificationApiBaseInput = document.getElementById("notificationApiBase");
const notificationChannelSelect = document.getElementById("notificationChannel");
const notificationChatIdInput = document.getElementById("notificationChatId");
const notificationMessageInput = document.getElementById("notificationMessage");
const notificationResult = document.getElementById("notificationResult");
const sendMessageForm = document.getElementById("sendMessageForm");
const imConfigContent = document.getElementById("imConfigContent");
const configFileName = document.getElementById("configFileName");
const configStatus = document.getElementById("configStatus");
const refreshConfigButton = document.getElementById("refreshConfigButton");
const configTree = document.getElementById("configTree");
const configEmptyState = document.getElementById("configEmptyState");

const PUBLIC_BASE_URL_PLACEHOLDER = "YOUR_SERVER_PUBLIC_URL";

let resolvedMonitoringApiBase = "";

function normalizeApiBase(base) {
  return (base || "").trim();
}

function isPlaceholderBase(value) {
  return !value || normalizeApiBase(value) === PUBLIC_BASE_URL_PLACEHOLDER;
}

function getConfiguredPublicBaseFromMeta() {
  const metaValue = normalizeApiBase(monitoringApiBaseMeta?.content);
  return metaValue && metaValue !== PUBLIC_BASE_URL_PLACEHOLDER ? metaValue : "";
}

function getConfiguredAdminBaseFromMeta() {
  const metaValue = normalizeApiBase(adminApiBaseMeta?.content);
  return metaValue && metaValue !== PUBLIC_BASE_URL_PLACEHOLDER ? metaValue : "";
}

function applyConfiguredApiBase(base) {
  const normalized = normalizeApiBase(base);
  const effectiveValue = isPlaceholderBase(normalized) ? "" : normalized;
  resolvedMonitoringApiBase = effectiveValue;

  const persistedValue = effectiveValue || PUBLIC_BASE_URL_PLACEHOLDER;
  localStorage.setItem(MONITORING_API_STORAGE_KEY, persistedValue);
  localStorage.setItem(NOTIFICATION_API_STORAGE_KEY, persistedValue);

  if (monitoringApiBaseInput) {
    monitoringApiBaseInput.value = persistedValue;
    monitoringApiBaseInput.placeholder = PUBLIC_BASE_URL_PLACEHOLDER;
    monitoringApiBaseInput.readOnly = true;
  }

  if (notificationApiBaseInput) {
    notificationApiBaseInput.value = persistedValue;
    notificationApiBaseInput.placeholder = PUBLIC_BASE_URL_PLACEHOLDER;
    notificationApiBaseInput.readOnly = true;
  }
}

initFunctionSelect(menuFunctionSelect);

const menuStores = {
  [MENU_TYPES.BUSINESS]: createMenuStore(ROOT_MENU_ID, DEFAULT_STRUCTURE),
  [MENU_TYPES.LOGIN]: createMenuStore(LOGIN_ROOT_MENU_ID, DEFAULT_LOGIN_STRUCTURE)
};

let activeMenuType = MENU_TYPES.BUSINESS;
let currentRootMenuId = ROOT_MENU_ID;
let menusById = menuStores[MENU_TYPES.BUSINESS].menusById;
let menuOrder = menuStores[MENU_TYPES.BUSINESS].menuOrder;
let selectedMenuId = currentRootMenuId;
let menuIdCounter = 0;
let itemIdCounter = 0;
let liveSessions = [];
let sessionHistory = [];
let monitoringError = null;
const MONITORING_REFRESH_MS = 5000;
const MONITORING_API_STORAGE_KEY = "monitoringApiBase";
const NOTIFICATION_API_STORAGE_KEY = "notificationApiBase";
let monitoringIntervalId = null;
let monitoringEndpointCache = "";
let monitoringConfigLoaded = false;
let notificationEndpointCache = "";
let configEndpointCache = "";
let serviceFunctionEntries = [];
let availableServiceFunctionEndpoints = [];
const serviceFunctionOverrides = new Map();

function getServiceFunctionOverride(key) {
  const existing = serviceFunctionOverrides.get(key);
  return {
    queryParams: "",
    accountContext: false,
    serviceContext: false,
    ...existing
  };
}

function setServiceFunctionOverride(key, updates) {
  const current = getServiceFunctionOverride(key);
  serviceFunctionOverrides.set(key, { ...current, ...updates });
}

applyConfiguredApiBase(getConfiguredPublicBaseFromMeta());

function initFunctionSelect(selectEl) {
  if (!selectEl) return;
  selectEl.innerHTML = "";
  functionOptions
    .slice()
    .sort((a, b) => a.label.localeCompare(b.label))
    .forEach((option) => {
      const opt = document.createElement("option");
      opt.value = option.id;
      opt.textContent = option.label;
      opt.title = option.description;
      selectEl.append(opt);
    });
}

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function createMenuStore(rootId, defaults) {
  return {
    rootId,
    defaults,
    menusById: new Map(),
    menuOrder: [],
    selectedMenuId: rootId,
    menuIdCounter: 0,
    itemIdCounter: 0
  };
}

function persistActiveStore() {
  const store = menuStores[activeMenuType];
  store.menusById = menusById;
  store.menuOrder = menuOrder;
  store.selectedMenuId = selectedMenuId;
  store.menuIdCounter = menuIdCounter;
  store.itemIdCounter = itemIdCounter;
}

function restoreActiveStore() {
  const store = menuStores[activeMenuType];
  currentRootMenuId = store.rootId;
  menusById = store.menusById;
  menuOrder = store.menuOrder;
  selectedMenuId = store.selectedMenuId || currentRootMenuId;
  menuIdCounter = store.menuIdCounter || 0;
  itemIdCounter = store.itemIdCounter || 0;
}

function switchMenuType(type) {
  if (!menuStores[type]) return;
  persistActiveStore();
  activeMenuType = type;
  restoreActiveStore();
  if (menuTypeSelect) {
    menuTypeSelect.value = type;
  }
  renderAll();
}

function nextMenuId(name) {
  const base = slugify(name) || `menu-${++menuIdCounter}`;
  let candidate = base;
  let suffix = 1;
  while (menusById.has(candidate)) {
    candidate = `${base}-${suffix++}`;
  }
  return candidate;
}

function nextItemId() {
  itemIdCounter += 1;
  return `item-${itemIdCounter}`;
}

function ensureRootMenu() {
  if (!menusById.has(currentRootMenuId)) {
    menusById.set(currentRootMenuId, { id: currentRootMenuId, name: "Home", parentId: null, items: [] });
    menuOrder.unshift(currentRootMenuId);
  }
}

async function resetToDefault() {
  await loadRemoteMenuConfig({ preferDefault: true });
}

function loadState(state) {
  normalizeIncomingConfig(state || {});
}

function applyMenusToStore(menuType, menus) {
  const previousType = activeMenuType;
  activeMenuType = menuType;
  restoreActiveStore();

  const store = menuStores[menuType];
  store.menusById = new Map();
  store.menuOrder = [];
  store.menuIdCounter = Array.isArray(menus) ? menus.length : 0;
  store.itemIdCounter = 0;
  menuIdCounter = store.menuIdCounter;
  itemIdCounter = store.itemIdCounter;

  console.info(`Applying ${menus?.length || 0} menus to store`, { menuType, menus });

  (Array.isArray(menus) ? menus : []).forEach((menu) => {
    let resolvedId = menu.id || slugify(menu.name || "") || null;
    if (!resolvedId || store.menusById.has(resolvedId)) {
      resolvedId = `${menuType}-${resolvedId || nextMenuId(menu.name || "menu")}`;
    }
    const normalized = {
      id: resolvedId,
      name: menu.name?.trim() || (menu.id === store.rootId ? "Home" : menu.id || "Menu"),
      parentId: menu.parentId ?? null,
      items: Array.isArray(menu.items) ? menu.items : []
    };
    store.menusById.set(normalized.id, { ...normalized, items: [] });
    store.menuOrder.push(normalized.id);
  });

  if (!store.menusById.has(store.rootId)) {
    store.menusById.set(store.rootId, { id: store.rootId, name: "Home", parentId: null, items: [] });
    store.menuOrder.unshift(store.rootId);
  }

  (Array.isArray(menus) ? menus : []).forEach((menu) => {
    const target = store.menusById.get(menu.id) || store.menusById.get(store.rootId);
    target.items = [];
    const items = Array.isArray(menu.items) ? menu.items : [];
    console.info(`Processing ${items.length} items for menu ${menu.id}`, items);
    items.forEach((item) => {
      if (item?.submenuId && store.menusById.has(item.submenuId)) {
        const submenu = store.menusById.get(item.submenuId);
        submenu.parentId = target.id;
        target.items.push({
          id: item.id || nextItemId(),
          label: item.label ?? submenu.name,
          type: "submenu",
          submenuId: submenu.id,
          function: null,
          useTranslation: false
        });
        return;
      }
      const functionId = item.function || item.id;
      if (functionId && !functionDictionary[functionId]) {
        registerFunctionOption({ id: functionId, label: item.label || functionId, translationKey: item.translationKey, callbackData: item.callbackData || functionId });
      }
      if (functionId) {
        const meta = functionDictionary[functionId] || { id: functionId, label: functionId };
        target.items.push({
          id: item.id || nextItemId(),
          label: item.label ?? meta.label,
          type: "function",
          function: functionId,
          useTranslation: item.useTranslation ?? Boolean(item.translationKey),
          submenuId: null
        });
      }
    });
  });

  store.selectedMenuId = store.menusById.has(store.selectedMenuId) ? store.selectedMenuId : store.rootId;
  initFunctionSelect(menuFunctionSelect);

  persistActiveStore();
  activeMenuType = previousType;
  restoreActiveStore();
}

function extractLoginMenus(loginMenu) {
  if (Array.isArray(loginMenu?.menus) && loginMenu.menus.length) {
    console.info("Using nested login menu definition", loginMenu.menus);
    return loginMenu.menus;
  }
  if (Array.isArray(loginMenu?.menu) || Array.isArray(loginMenu?.settingsMenu)) {
    console.info("Using legacy login menu definition with settingsMenu");
    const settingsId = "login-settings";
    return [
      {
        id: LOGIN_ROOT_MENU_ID,
        name: "Home",
        parentId: null,
        items: [
          ...(Array.isArray(loginMenu.menu) ? loginMenu.menu : []).map((item) => ({
            label: item.label,
            function: item.function,
            useTranslation: Boolean(item.translationKey)
          })),
          { label: "Settings", type: "submenu", submenuId: settingsId }
        ]
      },
      {
        id: settingsId,
        name: "Settings",
        parentId: LOGIN_ROOT_MENU_ID,
        items: (loginMenu.settingsMenu || []).map((item) => ({
          label: item.label,
          function: item.function,
          useTranslation: Boolean(item.translationKey)
        }))
      }
    ];
  }
  return null;
}

async function fetchConfigFromPaths(paths) {
  const filteredPaths = (paths || []).filter(Boolean);
  console.info("Attempting to load IM menu configuration from candidates", filteredPaths);

  for (const path of filteredPaths) {
    try {
      const response = await fetch(path, { cache: "no-cache" });
      if (!response.ok) {
        console.info(`Skipping configuration path ${path} due to status ${response.status}`);
        continue;
      }
      const text = await response.text();
      if (!text?.trim()) {
        console.info(`Skipping configuration path ${path} because it returned empty content`);
        continue;
      }
      console.info("Loaded IM menu configuration from", path);
      return JSON.parse(text);
    } catch (error) {
      console.warn(`Unable to load configuration from ${path}`, error);
    }
  }
  return null;
}

async function loadRemoteMenuConfig(options = {}) {
  const preferDefault = Boolean(options.preferDefault);
  const fetchPaths = getConfigFetchPaths();
  const overrideConfig = preferDefault ? null : await fetchConfigFromPaths(fetchPaths.override);
  const defaultConfig = await fetchConfigFromPaths(fetchPaths.default);
  const selected = overrideConfig || defaultConfig;

  if (selected) {
    normalizeIncomingConfig(selected);
    return true;
  }

  normalizeIncomingConfig({});
  return false;
}

async function bootstrapMenuConfig() {
  const loaded = await loadRemoteMenuConfig();
  if (!loaded) {
    console.warn("Falling back to built-in menu structure because no configuration file was found.");
  }
}

function normalizeMenuTree(rawMenus, defaults, rootId) {
  if (Array.isArray(rawMenus) && rawMenus.length) {
    return rawMenus.map((menu) => ({
      id: menu.id || slugify(menu.name || ""),
      name: menu.name,
      parentId: menu.parentId ?? null,
      items: Array.isArray(menu.items) ? menu.items : []
    }));
  }
  return structuredClone(defaults).map((menu) => ({ ...menu, parentId: menu.parentId ?? null, id: menu.id || rootId }));
}

function normalizeIncomingConfig(raw) {
  console.info("Normalizing incoming IM menu config", raw);
  const businessSource = Array.isArray(raw?.menus)
    ? raw.menus
    : Array.isArray(raw?.menu)
      ? [
          {
            id: ROOT_MENU_ID,
            name: "Home",
            parentId: null,
            items: raw.menu
          }
      ]
      : null;
  const businessMenus = normalizeMenuTree(businessSource, DEFAULT_STRUCTURE, ROOT_MENU_ID);
  const loginMenus = normalizeMenuTree(extractLoginMenus(raw?.loginMenu), DEFAULT_LOGIN_STRUCTURE, LOGIN_ROOT_MENU_ID);

  console.info(
    "Normalized menus", {
      businessMenusCount: businessMenus.length,
      loginMenusCount: loginMenus.length,
      businessMenus,
      loginMenus
    }
  );

  applyMenusToStore(MENU_TYPES.BUSINESS, businessMenus);
  applyMenusToStore(MENU_TYPES.LOGIN, loginMenus);
  restoreActiveStore();
  renderAll();
}

function renderAll() {
  renderMenuSelectors();
  renderMenuItems();
  toggleAddFormFields();
  updatePreview();
}

function renderMenuSelectors() {
  menuSelect.innerHTML = "";
  parentMenuSelect.innerHTML = "";

  menuOrder = menuOrder.filter((id) => menusById.has(id));
  if (!menuOrder.includes(currentRootMenuId)) {
    menuOrder.unshift(currentRootMenuId);
  }

  menuOrder.forEach((id) => {
    const menu = menusById.get(id);
    if (!menu) return;
    menuSelect.append(new Option(menu.name, id, false, id === selectedMenuId));
    parentMenuSelect.append(new Option(menu.name, id));
  });

  if (!menusById.has(selectedMenuId)) {
    selectedMenuId = currentRootMenuId;
  }

  menuSelect.value = selectedMenuId;
  parentMenuSelect.value = selectedMenuId;
  menuNameEditor.value = menusById.get(selectedMenuId)?.name ?? "";
  menuNameEditor.disabled = selectedMenuId === currentRootMenuId;
  deleteMenuButton.disabled = selectedMenuId === currentRootMenuId;

  updateAddFormSubmenuOptions();
}

function renderMenuItems() {
  const menu = menusById.get(selectedMenuId);
  menuContainer.innerHTML = "";
  if (!menu || !menu.items.length) {
    const empty = document.createElement("p");
    empty.textContent = "No menu items yet. Use the form below to add one.";
    menuContainer.append(empty);
    return;
  }

  menu.items.forEach((item, index) => {
    const row = document.createElement("div");
    row.className = "menu-item";

    const fields = document.createElement("div");
    fields.className = "menu-item-fields";

    const labelWrapper = document.createElement("label");
    labelWrapper.textContent = "Label";
    const labelInput = document.createElement("input");
    labelInput.type = "text";
    labelInput.value = item.label;
    labelInput.addEventListener("input", (event) => {
      menu.items[index].label = event.target.value;
      updatePreview();
    });
    labelWrapper.append(labelInput);

    const typeWrapper = document.createElement("label");
    typeWrapper.textContent = "Type";
    typeWrapper.className = "menu-item-type";
    const typeSelect = document.createElement("select");
    typeSelect.innerHTML = "<option value=\"function\">Function</option><option value=\"submenu\">Sub-menu</option>";
    typeSelect.value = item.type;
    typeSelect.addEventListener("change", (event) => handleItemTypeChange(menu.id, index, event.target.value, event));
    typeWrapper.append(typeSelect);

    const details = document.createElement("div");
    details.className = "menu-item-details";
    renderItemDetails(details, menu.id, item, index);

    fields.append(labelWrapper, typeWrapper, details);

    const actions = document.createElement("div");
    actions.className = "menu-item-actions";
    const upButton = document.createElement("button");
    upButton.type = "button";
    upButton.textContent = "↑";
    upButton.title = "Move up";
    upButton.disabled = index === 0;
    upButton.addEventListener("click", () => moveItem(menu.id, index, index - 1));

    const downButton = document.createElement("button");
    downButton.type = "button";
    downButton.textContent = "↓";
    downButton.title = "Move down";
    downButton.disabled = index === menu.items.length - 1;
    downButton.addEventListener("click", () => moveItem(menu.id, index, index + 1));

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.textContent = "✕";
    deleteButton.title = "Remove";
    deleteButton.addEventListener("click", () => deleteItem(menu.id, index));

    actions.append(upButton, downButton, deleteButton);
    row.append(fields, actions);
    menuContainer.append(row);
  });
}

function renderItemDetails(container, menuId, item, index) {
  container.innerHTML = "";
  if (item.type === "function") {
    const functionWrapper = document.createElement("label");
    functionWrapper.textContent = "Function";
    const functionDropdown = document.createElement("select");
    initFunctionSelect(functionDropdown);
    functionDropdown.value = item.function;
    functionDropdown.addEventListener("change", (event) => {
      if (!functionDictionary[event.target.value]) {
        return;
      }
      menusById.get(menuId).items[index].function = event.target.value;
      updatePreview();
    });
    functionWrapper.append(functionDropdown);

    const translationToggle = document.createElement("label");
    translationToggle.className = "checkbox";
    const translationCheckbox = document.createElement("input");
    translationCheckbox.type = "checkbox";
    translationCheckbox.checked = Boolean(item.useTranslation);
    translationCheckbox.addEventListener("change", (event) => {
      menusById.get(menuId).items[index].useTranslation = event.target.checked;
      updatePreview();
    });
    const translationText = document.createElement("span");
    translationText.textContent = "Use built-in translation";
    translationToggle.append(translationCheckbox, translationText);

    const translationHint = document.createElement("p");
    translationHint.className = "hint";
    translationHint.textContent = "Keeps the original multilingual text for this function.";

    container.append(functionWrapper, translationToggle, translationHint);
    return;
  }

  const submenuWrapper = document.createElement("label");
  submenuWrapper.textContent = "Sub-menu";
  const submenuDropdown = buildSubmenuDropdown(menuId, item.submenuId);
  submenuDropdown.addEventListener("change", (event) => assignSubmenu(menuId, index, event.target.value, () => {
    submenuDropdown.value = item.submenuId || "";
  }));
  submenuWrapper.append(submenuDropdown);

  const submenuHint = document.createElement("p");
  submenuHint.className = "hint";
  submenuHint.textContent = "Opens another menu when tapped.";

  container.append(submenuWrapper, submenuHint);
}

function buildSubmenuDropdown(parentId, currentSubmenuId) {
  const select = document.createElement("select");
  const options = availableSubmenus(parentId, currentSubmenuId);
  if (!options.length) {
    const placeholder = new Option("No sub-menus available", "", true, true);
    placeholder.disabled = true;
    select.append(placeholder);
    select.disabled = true;
    return select;
  }
  options.forEach((menu) => {
    select.append(new Option(menu.name, menu.id, false, menu.id === currentSubmenuId));
  });
  return select;
}

function availableSubmenus(parentId, currentSubmenuId) {
  return menuOrder
    .map((id) => menusById.get(id))
    .filter(Boolean)
    .filter((menu) => menu.id !== currentRootMenuId)
    .filter((menu) => menu.id !== parentId)
    .filter((menu) => {
      if (isAncestor(menu.id, parentId)) {
        return false;
      }
      if (menu.parentId && menu.parentId !== parentId) {
        return menu.id === currentSubmenuId;
      }
      return true;
    });
}

function isAncestor(targetId, childId) {
  let current = menusById.get(childId);
  while (current && current.parentId) {
    if (current.parentId === targetId) {
      return true;
    }
    current = menusById.get(current.parentId);
  }
  return false;
}

function handleItemTypeChange(menuId, itemIndex, newType, event) {
  const menu = menusById.get(menuId);
  if (!menu) return;
  const item = menu.items[itemIndex];
  if (!item) return;

  if (item.type === "submenu" && item.submenuId) {
    detachSubmenu(item.submenuId, menuId);
  }

  if (newType === "submenu") {
    const options = availableSubmenus(menuId);
    if (!options.length) {
      alert("Create a sub-menu before linking it.");
      event.target.value = "function";
      return;
    }
    const target = options[0];
    if (!linkSubmenu(menuId, target.id)) {
      event.target.value = "function";
      return;
    }
    item.type = "submenu";
    item.submenuId = target.id;
    item.function = null;
    item.useTranslation = false;
  } else {
    item.type = "function";
    item.function = item.function && functionDictionary[item.function] ? item.function : FUNCTION_OPTIONS[0].id;
    item.submenuId = null;
    item.useTranslation = item.useTranslation ?? true;
  }

  renderMenuItems();
  updatePreview();
}

function moveItem(menuId, from, to) {
  const menu = menusById.get(menuId);
  if (!menu) return;
  if (to < 0 || to >= menu.items.length) return;
  const updated = [...menu.items];
  const [moved] = updated.splice(from, 1);
  updated.splice(to, 0, moved);
  menu.items = updated;
  renderMenuItems();
  updatePreview();
}

function deleteItem(menuId, index) {
  const menu = menusById.get(menuId);
  if (!menu) return;
  const [removed] = menu.items.splice(index, 1);
  if (removed?.type === "submenu" && removed.submenuId) {
    detachSubmenu(removed.submenuId, menuId);
  }
  renderMenuItems();
  updatePreview();
}

function detachSubmenu(submenuId, parentId) {
  const submenu = menusById.get(submenuId);
  if (submenu && submenu.parentId === parentId) {
    submenu.parentId = null;
  }
}

function assignSubmenu(menuId, index, submenuId, onFailure) {
  if (!submenuId) {
    onFailure?.();
    return;
  }
  if (!linkSubmenu(menuId, submenuId)) {
    onFailure?.();
    return;
  }
  menusById.get(menuId).items[index].submenuId = submenuId;
  updatePreview();
}

function linkSubmenu(parentId, submenuId) {
  const submenu = menusById.get(submenuId);
  if (!submenu) {
    alert("Unknown sub-menu selected.");
    return false;
  }
  if (submenuId === currentRootMenuId) {
    alert("The Home menu cannot be nested inside another menu.");
    return false;
  }
  if (submenuId === parentId) {
    alert("A menu cannot target itself.");
    return false;
  }
  if (isAncestor(submenuId, parentId)) {
    alert("This would create a circular menu hierarchy.");
    return false;
  }
  if (submenu.parentId && submenu.parentId !== parentId) {
    alert(`"${submenu.name}" already belongs to another menu.`);
    return false;
  }
  submenu.parentId = parentId;
  return true;
}

function updateAddFormSubmenuOptions() {
  const parentId = parentMenuSelect.value || currentRootMenuId;
  const options = availableSubmenus(parentId);
  submenuSelect.innerHTML = "";
  if (!options.length) {
    const placeholder = new Option("No sub-menus available", "", true, true);
    placeholder.disabled = true;
    submenuSelect.append(placeholder);
    submenuSelect.disabled = true;
  } else {
    options.forEach((menu) => {
      submenuSelect.append(new Option(menu.name, menu.id));
    });
    submenuSelect.disabled = false;
  }
}

function toggleAddFormFields() {
  const type = itemTypeSelect.value;
  if (type === "function") {
    functionFields.classList.remove("hidden");
    submenuFields.classList.add("hidden");
  } else {
    functionFields.classList.add("hidden");
    submenuFields.classList.remove("hidden");
    updateAddFormSubmenuOptions();
  }
}

function updatePreview() {
  const config = buildConfig();
  preview.textContent = JSON.stringify(config, null, 2);
}

function buildConfig() {
  persistActiveStore();
  const timestamp = new Date().toISOString();
  const loginMenus = serializeStore(menuStores[MENU_TYPES.LOGIN]);
  const businessMenus = serializeStore(menuStores[MENU_TYPES.BUSINESS]);
  const legacyLogin = buildLegacyLoginSections(loginMenus);
  return {
    version: 1,
    generatedAt: timestamp,
    loginMenu: { menus: loginMenus, ...legacyLogin },
    menus: businessMenus
  };
}

function serializeStore(store) {
  return store.menuOrder
    .filter((id) => store.menusById.has(id))
    .map((id) => {
      const menu = store.menusById.get(id);
      return {
        id: menu.id,
        name: menu.name,
        parentId: menu.parentId ?? null,
        items: menu.items.map((item, index) => {
          if (item.type === "submenu") {
            return {
              order: index + 1,
              label: item.label,
              function: null,
              callbackData: null,
              translationKey: null,
              submenuId: item.submenuId
            };
          }
          const meta = functionDictionary[item.function] || {};
          return {
            order: index + 1,
            label: item.label,
            function: item.function,
            callbackData: meta.callbackData || item.function,
            translationKey: item.useTranslation && meta.translationKey ? meta.translationKey : null,
            submenuId: null
          };
        })
      };
    });
}

function buildLegacyLoginSections(loginMenus) {
  const legacyMenu = [];
  const legacySettings = [];
  const rootMenu = loginMenus.find((menu) => menu.id === LOGIN_ROOT_MENU_ID) || loginMenus[0];
  const rootItems = Array.isArray(rootMenu?.items) ? rootMenu.items : [];

  rootItems
    .filter((item) => item.type !== "submenu")
    .forEach((item, index) => {
      const meta = functionDictionary[item.function] || {};
      legacyMenu.push({
        order: index + 1,
        label: item.label,
        function: item.function,
        callbackData: meta.callbackData || item.function,
        translationKey: item.useTranslation && meta.translationKey ? meta.translationKey : null
      });
    });

  const settingsTargetId = rootItems.find((item) => item.type === "submenu")?.submenuId;
  const settingsMenu = loginMenus.find((menu) => menu.id === settingsTargetId);
  const settingsItems = Array.isArray(settingsMenu?.items) ? settingsMenu.items : [];
  settingsItems
    .filter((item) => item.type !== "submenu")
    .forEach((item, index) => {
      const meta = functionDictionary[item.function] || {};
      legacySettings.push({
        order: index + 1,
        label: item.label,
        function: item.function,
        callbackData: meta.callbackData || item.function,
        translationKey: item.useTranslation && meta.translationKey ? meta.translationKey : null
      });
    });

  return { menu: legacyMenu, settingsMenu: legacySettings };
}

function promptForMenuName() {
  const name = prompt("Sub-menu name");
  if (!name) return null;
  const trimmed = name.trim();
  return trimmed || null;
}

function createSubmenu(initialName) {
  const name = initialName ?? promptForMenuName();
  if (!name) return null;
  const id = nextMenuId(name);
  menusById.set(id, { id, name, parentId: null, items: [] });
  menuOrder.push(id);
  return id;
}

function deleteMenuWithConfirmation(menuId) {
  if (menuId === currentRootMenuId) {
    return;
  }
  const menu = menusById.get(menuId);
  if (!menu) return;
  if (menu.items.length > 0) {
    const confirmed = confirm(
      "Are you sure you want to delete this sub-menu and all its associated functions and sub-menus?"
    );
    if (!confirmed) {
      return;
    }
  }
  const toRemove = collectDescendants(menuId);
  toRemove.forEach(removeMenu);
  selectedMenuId = currentRootMenuId;
  renderAll();
}

function collectDescendants(menuId) {
  const ids = [];
  function visit(id) {
    ids.push(id);
    const menu = menusById.get(id);
    if (!menu) return;
    menu.items.forEach((item) => {
      if (item.type === "submenu" && menusById.has(item.submenuId)) {
        visit(item.submenuId);
      }
    });
  }
  visit(menuId);
  return ids;
}

function removeMenu(id) {
  const menu = menusById.get(id);
  if (!menu) return;
  if (menu.parentId) {
    const parent = menusById.get(menu.parentId);
    if (parent) {
      parent.items = parent.items.filter((item) => !(item.type === "submenu" && item.submenuId === id));
    }
  }
  menusById.delete(id);
  menuOrder = menuOrder.filter((value) => value !== id);
}

function downloadConfig() {
  const rootMenu = menusById.get(currentRootMenuId);
  if (!rootMenu || rootMenu.items.length === 0) {
    alert("Please configure at least one menu item before downloading.");
    return;
  }
  const config = buildConfig();
  const blob = new Blob([JSON.stringify(config, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "IM-menus.override.json";
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function importConfig(event) {
  const [file] = event.target.files;
  if (!file) {
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const parsed = JSON.parse(reader.result);
      normalizeIncomingConfig(parsed);
    } catch (error) {
      alert(`Unable to import configuration: ${error.message}`);
    } finally {
      event.target.value = "";
    }
  };
  reader.readAsText(file);
}

function addMenuItem(event) {
  event.preventDefault();
  const parentId = parentMenuSelect.value || currentRootMenuId;
  const parentMenu = menusById.get(parentId);
  if (!parentMenu) {
    alert("Please select a valid menu.");
    return;
  }
  const label = itemLabelInput.value.trim();
  if (!label) {
    alert("Menu item name is required.");
    return;
  }
  if (itemTypeSelect.value === "function") {
    const functionId = menuFunctionSelect.value;
    if (!functionDictionary[functionId]) {
      alert("Please choose a valid function.");
      return;
    }
    parentMenu.items.push({
      id: nextItemId(),
      label,
      type: "function",
      function: functionId,
      useTranslation: useTranslationInput.checked,
      submenuId: null
    });
  } else {
    if (submenuSelect.disabled || !submenuSelect.value) {
      alert("Create a sub-menu before adding this item.");
      return;
    }
    if (!linkSubmenu(parentId, submenuSelect.value)) {
      return;
    }
    parentMenu.items.push({
      id: nextItemId(),
      label,
      type: "submenu",
      submenuId: submenuSelect.value
    });
  }
  selectedMenuId = parentId;
  addItemForm.reset();
  itemTypeSelect.value = "function";
  useTranslationInput.checked = false;
  toggleAddFormFields();
  renderAll();
}

function setActiveApp(target) {
  const showMenuConfig = target === "menu";
  const showOperations = target === "operations";
  const showSendMessages = target === "notifications";
  const showImServerAdmin = target === "admin";
  const showServiceFunctions = target === "service-functions";
  menuConfigurationPanel.classList.toggle("hidden", !showMenuConfig);
  operationsMonitoringPanel.classList.toggle("hidden", !showOperations);
  sendMessagesPanel.classList.toggle("hidden", !showSendMessages);
  imServerAdminPanel.classList.toggle("hidden", !showImServerAdmin);
  serviceFunctionsPanel.classList.toggle("hidden", !showServiceFunctions);
  navMenuConfig.classList.toggle("active", showMenuConfig);
  navOperationsMonitoring.classList.toggle("active", showOperations);
  navSendMessages.classList.toggle("active", showSendMessages);
  navImServerAdmin.classList.toggle("active", showImServerAdmin);
  navServiceFunctions.classList.toggle("active", showServiceFunctions);
  if (showOperations) {
    refreshMonitoringData();
  }
  if (showImServerAdmin) {
    loadImServerConfig();
  }
  if (showServiceFunctions) {
    loadServiceFunctions();
  }
}

function formatQueryParams(params) {
  if (!params || typeof params !== "object") {
    return "";
  }
  return Object.entries(params)
    .map(([key, value]) => `${key}=${value ?? ""}`)
    .join("&");
}

function parseQueryParamString(input) {
  const result = {};
  const trimmed = (input || "").trim();
  if (!trimmed) {
    return result;
  }
  trimmed.split(/[&;]/).forEach((segment) => {
    if (!segment) return;
    const [rawKey, ...rest] = segment.split("=");
    const key = (rawKey || "").trim();
    const value = rest.join("=").trim();
    if (key) {
      result[key] = value;
    }
  });
  return result;
}

function renderServiceFunctionEndpointOptions() {
  if (!newServiceFunctionEndpoint) return;
  newServiceFunctionEndpoint.innerHTML = "";
  availableServiceFunctionEndpoints.forEach((endpoint) => {
    const option = document.createElement("option");
    option.value = endpoint.endpointKey || endpoint.key || endpoint.name;
    option.textContent = endpoint.name || endpoint.endpointKey || endpoint.key;
    option.disabled = !endpoint.configured;
    newServiceFunctionEndpoint.append(option);
  });
}

function getServiceFunctionBase(key) {
  return availableServiceFunctionEndpoints.find(
    (endpoint) => endpoint.endpointKey === key || endpoint.key === key || endpoint.name === key
  );
}

function resetNewServiceFunctionForm() {
  if (!newServiceFunctionForm) return;
  newServiceFunctionForm.reset();
  renderServiceFunctionEndpointOptions();
  if (newServiceFunctionEndpoint && newServiceFunctionEndpoint.options.length > 0) {
    newServiceFunctionEndpoint.value = newServiceFunctionEndpoint.options[0].value;
    prefillQueryParamsFromEndpoint(newServiceFunctionEndpoint.value);
  }
}

function toggleNewServiceFunctionForm(show) {
  if (!newServiceFunctionForm) return;
  const shouldShow = Boolean(show);
  newServiceFunctionForm.classList.toggle("hidden", !shouldShow);
  if (shouldShow) {
    resetNewServiceFunctionForm();
    newServiceFunctionName?.focus();
  }
}

function prefillQueryParamsFromEndpoint(endpointKey) {
  if (!newServiceFunctionQueryParams) return;
  const base = getServiceFunctionBase(endpointKey);
  if (!base) return;
  const defaultValue =
    formatQueryParams(base.configuredQueryParams) || formatQueryParams(base.defaultQueryParams) || "";
  newServiceFunctionQueryParams.value = defaultValue;
  newServiceFunctionAccountContext.checked = Boolean(base.accountContextEnabled);
  newServiceFunctionServiceContext.checked = Boolean(base.serviceContextEnabled);
}

function createCustomServiceFunction() {
  if (!newServiceFunctionName || !newServiceFunctionEndpoint) return;

  const name = (newServiceFunctionName.value || "").trim();
  const endpointKey = newServiceFunctionEndpoint.value;
  const base = getServiceFunctionBase(endpointKey);
  if (!name || !base) {
    alert("Provide a name and endpoint for the new service function.");
    return;
  }

  const slug = slugify(name) || `service-function-${serviceFunctionEntries.length + 1}`;
  const baseQueryParamKey = `service-functions.${slug}.query-params`;
  let queryParamKey = baseQueryParamKey;
  let suffix = 1;
  while (serviceFunctionEntries.some((entry) => entry.queryParamKey === queryParamKey)) {
    queryParamKey = `${baseQueryParamKey}-${suffix++}`;
  }

  const contextConfig = {
    accountContext: Boolean(newServiceFunctionAccountContext?.checked),
    serviceContext: Boolean(newServiceFunctionServiceContext?.checked)
  };

  const newEntry = {
    key: `service-functions.${slug}`,
    name,
    url: base.url,
    configured: base.configured,
    services: base.services,
    method: base.method,
    defaultQueryParams: base.defaultQueryParams,
    configuredQueryParams: base.configuredQueryParams,
    queryParamKey,
    accountContextParam: base.accountContextParam,
    accountContextEnabled: contextConfig.accountContext,
    serviceContextParam: base.serviceContextParam,
    serviceContextEnabled: contextConfig.serviceContext,
    endpointKey,
    custom: true
  };

  const overrideValue = (newServiceFunctionQueryParams?.value || "").trim();
  setServiceFunctionOverride(queryParamKey, {
    queryParams:
      overrideValue || formatQueryParams(base.configuredQueryParams) || formatQueryParams(base.defaultQueryParams) || "",
    ...contextConfig
  });

  serviceFunctionEntries = [...serviceFunctionEntries, newEntry];
  renderServiceFunctionTable(serviceFunctionEntries);
  toggleNewServiceFunctionForm(false);
  serviceFunctionsStatus.textContent = `Added custom service function "${name}".`;
  serviceFunctionsStatus.className = "hint";
}

async function loadServiceFunctions() {
  if (!serviceFunctionsTableBody || !serviceFunctionsStatus) {
    return;
  }
  const endpoint = buildAdminEndpoint("/admin/service-functions");
  if (!endpoint) {
    serviceFunctionsStatus.textContent = "Set the monitoring API base URL so the Java server can be reached.";
    serviceFunctionsStatus.className = "hint error-state";
    renderServiceFunctionTable([]);
    return;
  }

  serviceFunctionsStatus.textContent = `Loading endpoints from ${endpoint}...`;
  serviceFunctionsStatus.className = "hint";

  serviceFunctionsTableBody.innerHTML = "";

  try {
    const response = await fetch(endpoint, { cache: "no-cache" });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    const endpoints = Array.isArray(payload?.endpoints) ? payload.endpoints : [];
    const availableEndpoints = Array.isArray(payload?.availableEndpoints)
      ? payload.availableEndpoints
      : endpoints.filter((entry) => !entry.custom);
    availableServiceFunctionEndpoints = availableEndpoints;
    serviceFunctionEntries = endpoints;
    serviceFunctionOverrides.clear();
    serviceFunctionEntries.forEach((endpoint) => {
      setServiceFunctionOverride(endpoint.queryParamKey, {
        queryParams:
          formatQueryParams(endpoint.configuredQueryParams) || formatQueryParams(endpoint.defaultQueryParams) || "",
        accountContext: Boolean(endpoint.accountContextEnabled),
        serviceContext: Boolean(endpoint.serviceContextEnabled)
      });
    });
    renderServiceFunctionEndpointOptions();
    renderServiceFunctionTable(serviceFunctionEntries);
    serviceFunctionsStatus.textContent = endpoints.length
      ? `Found ${endpoints.length} APIMAN endpoints.`
      : "No APIMAN endpoint URLs were found.";
    serviceFunctionsStatus.className = "hint";
  } catch (error) {
    console.error("Unable to load service function list", error);
    renderServiceFunctionTable([]);
    serviceFunctionsStatus.textContent = `Unable to load endpoints: ${error.message}`;
    serviceFunctionsStatus.className = "hint error-state";
  }
}

function extractApiPath(value) {
  if (!value) {
    return "";
  }

  const stringValue = value.toString().trim();
  if (!stringValue) {
    return "";
  }

  const placeholderBases = ["${apiman.base-url}", "${endpoints.apiman-base-url}", "${endpoints.rest-server-base-url}"];
  for (const base of placeholderBases) {
    if (stringValue.startsWith(base)) {
      const remainder = stringValue.slice(base.length);
      return remainder || "/";
    }
  }

  try {
    const parsed = new URL(stringValue);
    const path = `${parsed.pathname}${parsed.search}`;
    return path || "/";
  } catch (_error) {
    // Fall through to heuristic parsing for non-URL strings.
  }

  const withoutProtocol = stringValue.replace(/^https?:\/\/[^/]+/i, "");
  if (withoutProtocol !== stringValue) {
    return withoutProtocol || "/";
  }

  return stringValue;
}

function renderServiceFunctionTable(endpoints) {
  serviceFunctionsTableBody.innerHTML = "";
  if (!endpoints.length) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = "No endpoints available.";
    serviceFunctionsTableBody.append(empty);
    return;
  }

  endpoints.forEach((endpoint) => {
    const card = document.createElement("div");
    card.className = "service-function-card";

    const summary = document.createElement("div");
    summary.className = "service-function__line service-function__summary";

    const title = document.createElement("div");
    title.className = "service-function__title";
    const name = document.createElement("div");
    name.className = "service-function__name";
    name.textContent = endpoint.name || endpoint.key || "–";
    if (endpoint.custom) {
      const badge = document.createElement("span");
      badge.className = "tag";
      badge.textContent = "Custom";
      name.append(" ", badge);
    }
    const services = document.createElement("div");
    services.className = "service-function__path service-function__services";
    services.textContent = endpoint.services?.length
      ? `Java services: ${endpoint.services.join(", ")}`
      : "Java services: None";
    title.append(name, services);

    const path = document.createElement("div");
    path.className = "service-function__path";
    const apiPath = extractApiPath(endpoint.value);
    path.textContent = apiPath || "";
    const method = document.createElement("span");
    method.className = "pill pill--method";
    method.textContent = endpoint.method || "GET";
    path.append(method);

    summary.append(title, path);

    const defaultParams = formatQueryParams(endpoint.defaultQueryParams) || "None";
    const overrideState = getServiceFunctionOverride(endpoint.queryParamKey);

    const defaultRow = document.createElement("div");
    defaultRow.className = "service-function__line service-function__default";
    const defaultLabel = document.createElement("div");
    defaultLabel.className = "service-function__label";
    defaultLabel.textContent = "Default query params";
    const defaultValue = document.createElement("div");
    defaultValue.className = "service-function__value";
    defaultValue.textContent = defaultParams;
    defaultRow.append(defaultLabel, defaultValue);

    const overrideRow = document.createElement("div");
    overrideRow.className = "service-function__line service-function__override";
    const overrideLabel = document.createElement("div");
    overrideLabel.className = "service-function__label";
    overrideLabel.textContent = "Override query params";
    const overrideInput = document.createElement("input");
    overrideInput.type = "text";
    overrideInput.className = "query-param-input";
    overrideInput.value = overrideState.queryParams || "";
    overrideInput.placeholder = defaultParams || "key=value";
    overrideInput.addEventListener("input", (event) => {
      setServiceFunctionOverride(endpoint.queryParamKey, { queryParams: event.target.value });
    });
    overrideRow.append(overrideLabel, overrideInput);

    const contextRows = [];
    if (endpoint.accountContextParam) {
      const accountRow = document.createElement("div");
      accountRow.className = "service-function__line service-function__context";
      const accountLabel = document.createElement("div");
      accountLabel.className = "service-function__label";
      accountLabel.textContent = "Account context";
      const accountValue = document.createElement("label");
      accountValue.className = "service-function__value checkbox";
      const accountCheckbox = document.createElement("input");
      accountCheckbox.type = "checkbox";
      accountCheckbox.checked = Boolean(overrideState.accountContext);
      accountCheckbox.addEventListener("change", (event) => {
        setServiceFunctionOverride(endpoint.queryParamKey, { accountContext: event.target.checked });
      });
      const accountText = document.createElement("span");
      accountText.textContent = `Add ${endpoint.accountContextParam} using Account Context`;
      accountValue.append(accountCheckbox, accountText);
      accountRow.append(accountLabel, accountValue);
      contextRows.push(accountRow);
    }

    if (endpoint.serviceContextParam) {
      const serviceRow = document.createElement("div");
      serviceRow.className = "service-function__line service-function__context";
      const serviceLabel = document.createElement("div");
      serviceLabel.className = "service-function__label";
      serviceLabel.textContent = "Service context";
      const serviceValue = document.createElement("label");
      serviceValue.className = "service-function__value checkbox";
      const serviceCheckbox = document.createElement("input");
      serviceCheckbox.type = "checkbox";
      serviceCheckbox.checked = Boolean(overrideState.serviceContext);
      serviceCheckbox.addEventListener("change", (event) => {
        setServiceFunctionOverride(endpoint.queryParamKey, { serviceContext: event.target.checked });
      });
      const serviceText = document.createElement("span");
      serviceText.textContent = `Add ${endpoint.serviceContextParam} using Service Context`;
      serviceValue.append(serviceCheckbox, serviceText);
      serviceRow.append(serviceLabel, serviceValue);
      contextRows.push(serviceRow);
    }

    card.append(summary, defaultRow, overrideRow, ...contextRows);

    serviceFunctionsTableBody.append(card);
  });
}

async function downloadQueryParamConfig() {
  if (!serviceFunctionEntries.length) {
    alert("Load service functions before downloading.");
    return;
  }

  const endpoint = buildAdminEndpoint("/admin/service-functions/export");
  if (!endpoint) {
    serviceFunctionsStatus.textContent = "Set the monitoring API base URL so the Java server can be reached.";
    serviceFunctionsStatus.className = "hint error-state";
    return;
  }

  const updates = {};
  const customFunctions = [];
  serviceFunctionEntries.forEach((entry) => {
    const overrideState = getServiceFunctionOverride(entry.queryParamKey);
    const rawValue = (overrideState.queryParams || "").trim();
    const fallbackValue =
      formatQueryParams(entry.configuredQueryParams) || formatQueryParams(entry.defaultQueryParams) || "";
    const parsed = parseQueryParamString(rawValue || fallbackValue);
    if (entry.accountContextParam) {
      if (overrideState.accountContext) {
        parsed[entry.accountContextParam] = "";
      } else {
        delete parsed[entry.accountContextParam];
      }
    }
    if (entry.serviceContextParam) {
      if (overrideState.serviceContext) {
        parsed[entry.serviceContextParam] = "";
      } else {
        delete parsed[entry.serviceContextParam];
      }
    }
    if (entry.custom) {
      customFunctions.push({
        name: entry.name,
        endpointKey: entry.endpointKey || entry.key,
        queryParams: parsed,
        accountContext: Boolean(overrideState.accountContext),
        serviceContext: Boolean(overrideState.serviceContext)
      });
    } else {
      updates[entry.queryParamKey] = parsed;
    }
  });

  try {
    serviceFunctionsStatus.textContent = "Preparing application-local.yml for download...";
    serviceFunctionsStatus.className = "hint";
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ updates, serviceFunctions: customFunctions })
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "application-local.yml";
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    serviceFunctionsStatus.textContent = "application-local.yml downloaded with updated query parameters.";
    serviceFunctionsStatus.className = "hint";
  } catch (error) {
    console.error("Unable to export application-local.yml", error);
    serviceFunctionsStatus.textContent = `Unable to export application-local.yml: ${error.message}`;
    serviceFunctionsStatus.className = "hint error-state";
  }
}

function getStoredMonitoringApiBase() {
  const stored = normalizeApiBase(localStorage.getItem(MONITORING_API_STORAGE_KEY));
  return stored && stored !== PUBLIC_BASE_URL_PLACEHOLDER ? stored : "";
}

function restoreMonitoringApiBase() {
  const metaConfigured = getConfiguredPublicBaseFromMeta();
  const storedConfigured = getStoredMonitoringApiBase();
  applyConfiguredApiBase(metaConfigured || storedConfigured);
}

function getStoredNotificationApiBase() {
  const stored = normalizeApiBase(localStorage.getItem(NOTIFICATION_API_STORAGE_KEY));
  return stored && stored !== PUBLIC_BASE_URL_PLACEHOLDER ? stored : "";
}

function restoreNotificationApiBase() {
  const metaConfigured = getConfiguredPublicBaseFromMeta();
  const storedConfigured = getStoredNotificationApiBase();
  applyConfiguredApiBase(metaConfigured || storedConfigured);
}

async function prefillMonitoringApiBaseFromServer() {
  const metaConfigured = getConfiguredPublicBaseFromMeta();
  if (metaConfigured) {
    applyConfiguredApiBase(metaConfigured);
    monitoringConfigLoaded = true;
    return;
  }

  try {
    const response = await fetch("/operations/config", { cache: "no-cache" });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    const configuredBase =
      payload?.["public-base-url"] || payload?.publicBaseUrl || payload?.publicBaseURL || payload?.publicBase;
    if (configuredBase) {
      applyConfiguredApiBase(configuredBase);
    }
  } catch (error) {
    console.error("Unable to prefill monitoring API base from server", error);
  } finally {
    monitoringConfigLoaded = true;
  }
}

function getConfiguredMonitoringApiBase() {
  const metaConfigured = getConfiguredPublicBaseFromMeta();
  const storedConfigured = getStoredMonitoringApiBase();
  return (
    resolvedMonitoringApiBase ||
    metaConfigured ||
    storedConfigured ||
    (typeof window !== "undefined" ? window.location.origin : "")
  );
}

function getConfiguredAdminApiBase() {
  return getConfiguredAdminBaseFromMeta() || getConfiguredMonitoringApiBase();
}

function persistMonitoringApiBase(value) {
  applyConfiguredApiBase(value);
}

function getConfiguredNotificationApiBase() {
  const metaConfigured = getConfiguredPublicBaseFromMeta();
  const storedConfigured = getStoredNotificationApiBase();
  return (
    resolvedMonitoringApiBase ||
    metaConfigured ||
    storedConfigured ||
    (typeof window !== "undefined" ? window.location.origin : "")
  );
}

function persistNotificationApiBase(value) {
  applyConfiguredApiBase(value);
}

function buildOperationsEndpoint(path) {
  const base = getConfiguredMonitoringApiBase();
  if (!base) {
    monitoringEndpointCache = path;
    return null;
  }

  try {
    const endpoint = new URL(path, base).toString();
    monitoringEndpointCache = endpoint;
    return endpoint;
  } catch (error) {
    console.error("Invalid monitoring API base", base, error);
    monitoringError = `Invalid monitoring API base URL: ${base}`;
    return null;
  }
}

function buildNotificationEndpoint(path) {
  const base = getConfiguredNotificationApiBase();
  if (!base) {
    notificationEndpointCache = path;
    return null;
  }

  try {
    const endpoint = new URL(path, base).toString();
    notificationEndpointCache = endpoint;
    return endpoint;
  } catch (error) {
    console.error("Invalid notification API base", base, error);
    notificationResult.textContent = `Invalid notifications API base URL: ${base}`;
    notificationResult.className = "notification-result error";
    return null;
  }
}

function buildAdminEndpoint(path) {
  const base = getConfiguredAdminApiBase();
  const fallbackBase = typeof window !== "undefined" ? window.location.origin : "";
  const candidateBase = !isPlaceholderBase(base) ? base : "";

  try {
    const endpoint = new URL(path, candidateBase || fallbackBase || undefined).toString();
    configEndpointCache = endpoint;
    return endpoint;
  } catch (error) {
    console.error("Invalid admin API base", candidateBase || fallbackBase || base, error);
    configEndpointCache = path;
    return null;
  }
}

function formatTimestamp(millis) {
  if (!millis) {
    return "";
  }
  try {
    return new Date(millis).toLocaleString();
  } catch (error) {
    return "";
  }
}

function renderConfigTree(nodes) {
  if (!configTree || !configEmptyState) {
    return;
  }

  configTree.innerHTML = "";

  if (!nodes || !nodes.length) {
    configTree.classList.add("hidden");
    configEmptyState.classList.remove("hidden");
    return;
  }

  configEmptyState.classList.add("hidden");
  configTree.classList.remove("hidden");

  nodes.forEach((node) => renderConfigNode(node, 0, configTree));
}

function renderConfigNode(node, depth, parentEl) {
  const hasChildren = Array.isArray(node?.children) && node.children.length > 0;
  const row = document.createElement("div");
  row.className = hasChildren ? "config-group" : "config-entry";
  row.style.setProperty("--depth", depth);

  const keyEl = document.createElement("div");
  keyEl.className = "config-entry__key";
  keyEl.textContent = node?.key || node?.path || "value";
  row.append(keyEl);

  if (hasChildren) {
    const divider = document.createElement("div");
    divider.className = "config-group__divider";
    row.append(divider);
    parentEl.append(row);
    node.children.forEach((child) => renderConfigNode(child, depth + 1, parentEl));
    return;
  }

  const valueEl = document.createElement("input");
  const isNumber = node?.type === "number";
  valueEl.type = isNumber ? "number" : "text";
  valueEl.value = node?.value ?? "";
  valueEl.placeholder = isNumber ? "number" : "value";
  valueEl.setAttribute("aria-label", `Edit value for ${keyEl.textContent}`);

  row.append(valueEl);
  parentEl.append(row);
}

async function loadImServerConfig() {
  if (!imConfigContent || !configFileName || !configStatus) {
    return;
  }

  const endpoint = buildAdminEndpoint("/admin/application-config");
  if (!endpoint) {
    configStatus.textContent = "Set the monitoring API base URL so the Java server can be reached.";
    configStatus.className = "hint error-state";
    imConfigContent.textContent = "";
    configFileName.textContent = "Unavailable";
    console.log("TIAGO -- No admin endpoint available for loading IM server configuration");
    renderConfigTree([]);
    return;
  }

  configStatus.textContent = `Loading configuration from ${endpoint}...`;
  configStatus.className = "hint";
  try {
    const response = await fetch(endpoint);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const reason = payload?.reason || response.statusText || `HTTP ${response.status}`;
      throw new Error(reason);
    }
    const content = payload?.content || "";
    imConfigContent.textContent = content || "Configuration file is empty.";
    const tree = buildRenderableTree(payload?.tree, payload?.entries);
    renderConfigTree(tree);
    configFileName.textContent = payload?.fileName || "Unknown source";
    const timestamp = formatTimestamp(payload?.lastModified);
    configStatus.textContent = timestamp
      ? `Last updated ${timestamp}`
      : "Configuration loaded from server.";
    configStatus.className = "hint";
  } catch (error) {
    imConfigContent.textContent = "";
    renderConfigTree([]);
    configStatus.textContent = `Unable to load configuration from ${endpoint}: ${error?.message || error}`;
    configStatus.className = "hint error-state";
  }
}

function buildRenderableTree(tree, entries) {
  if (Array.isArray(tree) && tree.length) {
    return tree;
  }

  if (Array.isArray(entries) && entries.length) {
    return entries.map((entry) => ({
      key: entry?.key,
      path: entry?.key,
      type: entry?.type,
      value: entry?.value,
      children: []
    }));
  }

  return [];
}

async function refreshMonitoringData() {
  const endpoint = buildOperationsEndpoint("/operations/sessions");
  if (!endpoint) {
    monitoringError = "Set the monitoring API base URL so the Java server can be reached.";
    liveSessions = [];
    sessionHistory = [];
    renderMonitoring();
    return;
  }

  try {
    const response = await fetch(endpoint);
    if (!response.ok) {
      throw new Error(`Failed to load sessions (HTTP ${response.status})`);
    }
    const payload = await response.json();
    liveSessions = (payload.active || payload.live || payload.activeSessions || [])
      .map(normalizeSession);
    sessionHistory = (payload.history || payload.recent || [])
      .map(normalizeSession);
    monitoringError = null;
  } catch (error) {
    console.error("Monitoring refresh failed", error);
    monitoringError = `Unable to load monitoring data from ${endpoint}: ${error?.message || error}`;
    liveSessions = [];
    sessionHistory = [];
  }
  renderMonitoring();
}

function normalizeSession(raw) {
  const startedAt = raw?.startedAt ? new Date(raw.startedAt) : null;
  const sessionId = raw?.sessionId || raw?.chatId || raw?.id || "Unknown";
  const tokenState = raw?.token?.state || raw?.tokenState || "";
  const tokenValue = raw?.token?.token ?? raw?.token ?? null;
  return {
    channel: raw?.channel || "Unknown",
    chatId: sessionId,
    loggedIn: Boolean(raw?.loggedIn),
    username: raw?.username || raw?.user || raw?.displayName || "",
    startedAt,
    lastSeen: raw?.lastSeen ? new Date(raw.lastSeen) : startedAt,
    optIn: Boolean(raw?.optIn),
    tokenState,
    token: tokenValue
  };
}

function renderMonitoring() {
  renderSessionList(liveSessionsContainer, liveSessions, "No active sessions yet.");
  renderSessionList(sessionHistoryContainer, sessionHistory, "No completed sessions yet.");
}

function renderSessionList(container, sessions, emptyMessage) {
  container.innerHTML = "";
  if (monitoringError) {
    const errorRow = document.createElement("div");
    errorRow.className = "empty-state error-state";
    errorRow.textContent = monitoringError;
    container.append(errorRow);
  }
  if (!sessions.length) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = monitoringError ? "" : emptyMessage;
    container.append(empty);
    return;
  }

  sessions.forEach((session) => {
    const row = document.createElement("div");
    row.className = "session-row";

    const channel = document.createElement("span");
    channel.textContent = session.channel;

    const chat = document.createElement("span");
    chat.textContent = session.chatId;

    const loggedIn = document.createElement("span");
    loggedIn.className = "session-row__meta";
    const statusDot = document.createElement("span");
    statusDot.className = `status-dot ${session.loggedIn ? "online" : "offline"}`;
    const statusText = document.createElement("span");
    statusText.textContent = session.loggedIn ? "Yes" : "No";
    loggedIn.append(statusDot, statusText);
    if (session.loggedIn && session.username) {
      const userLabel = document.createElement("span");
      userLabel.className = "session-row__user";
      userLabel.textContent = `— ${session.username}`;
      loggedIn.append(userLabel);
    }

    const startDate = document.createElement("span");
    startDate.textContent = session.startedAt ? formatDate(session.startedAt) : "—";

    const startTime = document.createElement("span");
    startTime.textContent = session.startedAt ? formatTime(session.startedAt) : "—";

    const optInCell = document.createElement("span");
    optInCell.className = "optin-pill";
    optInCell.textContent = session.optIn ? "Opted in" : "Opted out";
    optInCell.classList.add(session.optIn ? "optin-pill--yes" : "optin-pill--no");

    const tokenCell = document.createElement("span");
    tokenCell.className = "session-row__token";
    const tokenStatus = describeToken(session.tokenState);
    const tokenPill = document.createElement("span");
    tokenPill.className = `token-pill ${tokenStatus.className}`;
    tokenPill.textContent = tokenStatus.label;
    tokenCell.append(tokenPill);
    if (session.token) {
      const link = document.createElement("a");
      link.href = `data:text/plain;charset=utf-8,${encodeURIComponent(session.token)}`;
      link.target = "_blank";
      link.rel = "noreferrer";
      link.textContent = "Open token";
      link.className = "token-link";
      tokenCell.append(link);
    }

    row.append(channel, chat, loggedIn, optInCell, tokenCell, startDate, startTime);
    container.append(row);
  });
}

function describeToken(state) {
  const normalized = (state || "").toString().toUpperCase();
  switch (normalized) {
    case "VALID":
    case "YES":
      return { label: "Yes", className: "valid" };
    case "EXPIRED":
      return { label: "Expired", className: "expired" };
    case "INVALID":
      return { label: "Invalid", className: "invalid" };
    case "NONE":
    case "NO":
    case "":
      return { label: "NO TOKEN", className: "none" };
    default:
      return { label: normalized, className: "none" };
  }
}

function formatDate(date) {
  return new Date(date).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "2-digit" });
}

function formatTime(date) {
  return new Date(date).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function handleMonitoringApiBaseChanged() {
  const configuredBase = getConfiguredMonitoringApiBase();
  applyConfiguredApiBase(configuredBase);
  monitoringError = null;
  refreshMonitoringData();
}

function handleNotificationApiBaseChanged() {
  const configuredBase = getConfiguredNotificationApiBase();
  applyConfiguredApiBase(configuredBase);
  notificationEndpointCache = "";
}

function initMonitoring() {
  restoreMonitoringApiBase();
  prefillMonitoringApiBaseFromServer().finally(() => {
    refreshMonitoringData();
  });
  if (monitoringIntervalId === null) {
    monitoringIntervalId = setInterval(refreshMonitoringData, MONITORING_REFRESH_MS);
  }
}

async function handleSendNotification(event) {
  event.preventDefault();
  notificationResult.textContent = "";
  notificationResult.className = "notification-result";

  const endpoint = buildNotificationEndpoint("/notifications");
  if (!endpoint) {
    notificationResult.textContent = "Set the notifications API base URL so the server can be reached.";
    notificationResult.classList.add("error");
    return;
  }

  const payload = {
    channel: notificationChannelSelect?.value || "",
    chatId: notificationChatIdInput?.value?.trim() || "",
    message: notificationMessageInput?.value?.trim() || ""
  };

  if (!payload.channel || !payload.chatId || !payload.message) {
    notificationResult.textContent = "Channel, chat ID, and message are required.";
    notificationResult.classList.add("error");
    return;
  }

  try {
    notificationResult.textContent = "Sending notification...";
    notificationResult.classList.add("info");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      notificationResult.textContent = body?.reason || `Request failed (HTTP ${response.status})`;
      notificationResult.classList.remove("info");
      notificationResult.classList.add("error");
      return;
    }
    notificationResult.textContent = body?.status === "sent"
      ? `Message sent via ${body.channel || payload.channel} to ${body.chatId || payload.chatId}.`
      : "Message sent.";
    notificationResult.classList.remove("info");
    notificationResult.classList.add("success");
  } catch (error) {
    notificationResult.textContent = error?.message || "Unexpected error while sending notification.";
    notificationResult.classList.remove("info");
    notificationResult.classList.add("error");
  }
}

menuSelect.addEventListener("change", (event) => {
  selectedMenuId = event.target.value;
  renderAll();
});

menuNameEditor.addEventListener("change", () => {
  if (menuNameEditor.disabled) {
    return;
  }
  const menu = menusById.get(selectedMenuId);
  if (!menu) {
    return;
  }
  const value = menuNameEditor.value.trim();
  if (!value) {
    menuNameEditor.value = menu.name;
    return;
  }
  menu.name = value;
  renderMenuSelectors();
  updatePreview();
});

createMenuButton.addEventListener("click", () => {
  const id = createSubmenu();
  if (id) {
    selectedMenuId = id;
    renderAll();
    menuNameEditor.focus();
  }
});

deleteMenuButton.addEventListener("click", () => deleteMenuWithConfirmation(selectedMenuId));

if (menuTypeSelect) {
  menuTypeSelect.addEventListener("change", (event) => switchMenuType(event.target.value));
}

parentMenuSelect.addEventListener("change", updateAddFormSubmenuOptions);
itemTypeSelect.addEventListener("change", toggleAddFormFields);
inlineCreateSubmenu.addEventListener("click", () => {
  const id = createSubmenu();
  if (id) {
    renderAll();
    submenuSelect.value = id;
  }
});

addItemForm.addEventListener("submit", addMenuItem);
resetButton.addEventListener("click", () => {
  if (confirm("Reset the menu to the default configuration?")) {
    resetToDefault();
  }
});

downloadButton.addEventListener("click", downloadConfig);
importInput.addEventListener("change", importConfig);
navMenuConfig.addEventListener("click", () => setActiveApp("menu"));
navOperationsMonitoring.addEventListener("click", () => setActiveApp("operations"));
navSendMessages.addEventListener("click", () => setActiveApp("notifications"));
navImServerAdmin.addEventListener("click", () => setActiveApp("admin"));
navServiceFunctions.addEventListener("click", () => setActiveApp("service-functions"));
if (downloadQueryParamsButton) {
  downloadQueryParamsButton.addEventListener("click", downloadQueryParamConfig);
}

if (addServiceFunctionButton) {
  addServiceFunctionButton.addEventListener("click", () => toggleNewServiceFunctionForm(true));
}

if (cancelNewServiceFunction) {
  cancelNewServiceFunction.addEventListener("click", () => toggleNewServiceFunctionForm(false));
}

if (newServiceFunctionEndpoint) {
  newServiceFunctionEndpoint.addEventListener("change", (event) => {
    prefillQueryParamsFromEndpoint(event.target.value);
  });
}

if (newServiceFunctionForm) {
  newServiceFunctionForm.addEventListener("submit", (event) => {
    event.preventDefault();
    createCustomServiceFunction();
  });
}
if (monitoringApiBaseInput) {
  monitoringApiBaseInput.addEventListener("change", handleMonitoringApiBaseChanged);
  monitoringApiBaseInput.addEventListener("blur", handleMonitoringApiBaseChanged);
}
if (notificationApiBaseInput) {
  notificationApiBaseInput.addEventListener("change", handleNotificationApiBaseChanged);
  notificationApiBaseInput.addEventListener("blur", handleNotificationApiBaseChanged);
}
if (sendMessageForm) {
  sendMessageForm.addEventListener("submit", handleSendNotification);
}
if (refreshConfigButton) {
  refreshConfigButton.addEventListener("click", loadImServerConfig);
}

if (menuTypeSelect) {
  menuTypeSelect.value = activeMenuType;
}

toggleAddFormFields();
bootstrapMenuConfig();
initMonitoring();
restoreNotificationApiBase();
setActiveApp("admin");
