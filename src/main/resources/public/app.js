const MENU_TYPES = {
  LOGIN: "login",
  BUSINESS: "business"
};

const ITEM_TYPES = {
  FUNCTION: "function",
  FUNCTION_MENU: "function-menu",
  SUBMENU: "submenu",
  WEBLINK: "weblink"
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
    id: "CHANGE_ACCOUNT",
    label: "Select a different account",
    callbackData: "CHANGE_ACCOUNT",
    translationKey: "ButtonChangeAccount",
    description: "Allows the user to switch to another available account."
  },
  {
    id: "MENU",
    label: "Back to menu",
    callbackData: "MENU",
    translationKey: "ButtonMenu",
    description: "Returns to the previous menu."
  },
  {
    id: "BUSINESS_MENU_UP",
    label: "Menu Up",
    callbackData: "BUSINESS_MENU_UP",
    translationKey: "BusinessMenuUp",
    description: "Navigates up one menu level."
  },
  {
    id: "LOGOUT",
    label: "Logout",
    callbackData: "LOGOUT",
    translationKey: "ButtonLogout",
    description: "Ends the authenticated session."
  }
];

const FUNCTION_RULES = {
  LOGOUT: {
    note: "Logout menu option will be displayed in the menus, when user is logged in",
  },
  MENU: {
    note: "Back to Menu option will be displayed in the menu level 2 and above",
  },
  CHANGE_ACCOUNT: {
    note: "Select a Different Account option will be displayed when users have access to more than one account",
  },
  BUSINESS_MENU_UP: {
    note: "Menu Up option will be displayed in the menus of level 3 and above",
  },
};

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
      { label: "My Issues", type: "function", function: "MY_ISSUES", useTranslation: true },
      { label: "Settings", type: "submenu", submenuId: "settings" }
    ]
  },
  {
    id: "settings",
    name: "Settings",
    parentId: ROOT_MENU_ID,
    items: [
      { label: "Consent management", type: "function", function: "OPT_IN", useTranslation: true },
      { label: "Language settings", type: "function", function: "CHANGE_LANGUAGE", useTranslation: true },
      { label: "Back to menu", type: "function", function: "MENU", useTranslation: true }
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
const serviceFunctionOptionIds = new Set();

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

function titleCase(value) {
  return (value || "")
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function syncServiceFunctionOptions(services) {
  serviceFunctionOptionIds.forEach((id) => removeFunctionOption(id));
  serviceFunctionOptionIds.clear();

  if (!Array.isArray(services)) return;

  services.forEach((service) => {
    if (!service?.name) return;
    const id = service.name;
    const label = titleCase(service.name) || service.name;
    const description = service.apiName
      ? `Service Builder function for ${service.apiName}`
      : "Service Builder function";
    registerFunctionOption({
      id,
      label,
      callbackData: id,
      description
    });
    serviceFunctionOptionIds.add(id);
  });

  initFunctionSelect(menuFunctionSelect);
  renderMenuItems();
  updatePreview();
}

function removeFunctionOption(id) {
  if (!id) return;
  delete functionDictionary[id];
  const index = functionOptions.findIndex((option) => option.id === id);
  if (index !== -1) {
    functionOptions.splice(index, 1);
  }
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
const contextFields = document.getElementById("contextFields");
const menuContextToggle = document.getElementById("menuContextToggle");
const menuContextKeyInput = document.getElementById("menuContextKeyInput");
const menuContextLabelInput = document.getElementById("menuContextLabelInput");
const serviceContextToggle = document.getElementById("serviceContextToggle");
const serviceContextKeyInput = document.getElementById("serviceContextKeyInput");
const serviceContextLabelInput = document.getElementById("serviceContextLabelInput");
const accountContextToggle = document.getElementById("accountContextToggle");
const accountContextKeyInput = document.getElementById("accountContextKeyInput");
const accountContextLabelInput = document.getElementById("accountContextLabelInput");
const submenuFields = document.getElementById("submenuFields");
const submenuSelect = document.getElementById("submenuSelect");
const weblinkFields = document.getElementById("weblinkFields");
const menuWeblinkSelect = document.getElementById("menuWeblinkSelect");
const inlineCreateSubmenu = document.getElementById("inlineCreateSubmenu");
const resetButton = document.getElementById("resetButton");
const downloadButton = document.getElementById("downloadButton");
const preview = document.getElementById("preview");
const importInput = document.getElementById("importInput");
const navMenuConfig = document.getElementById("navMenuConfig");
const navOperationsMonitoring = document.getElementById("navOperationsMonitoring");
const navSendMessages = document.getElementById("navSendMessages");
const navImServerAdmin = document.getElementById("navImServerAdmin");
const navApiRegistration = document.getElementById("navApiRegistration");
const navServiceBuilder = document.getElementById("navServiceBuilder");
const navConnectors = document.getElementById("navConnectors");
const navWeblinks = document.getElementById("navWeblinks");
const saveOverlayButton = document.getElementById("saveOverlayButton");
const menuConfigurationPanel = document.getElementById("menuConfigurationPanel");
const operationsMonitoringPanel = document.getElementById("operationsMonitoringPanel");
const sendMessagesPanel = document.getElementById("sendMessagesPanel");
const imServerAdminPanel = document.getElementById("imServerAdminPanel");
const apiRegistryPanel = document.getElementById("apiRegistryPanel");
const serviceBuilderPanel = document.getElementById("serviceBuilderPanel");
const connectorsPanel = document.getElementById("connectorsPanel");
const weblinksPanel = document.getElementById("weblinksPanel");
const addApiButton = document.getElementById("addApiButton");
const downloadApiListButton = document.getElementById("downloadApiListButton");
const apiForm = document.getElementById("apiForm");
const apiNameInput = document.getElementById("apiNameInput");
const apiUrlInput = document.getElementById("apiUrlInput");
const cancelApiButton = document.getElementById("cancelApiButton");
const apiList = document.getElementById("apiList");
const apiRegistryStatus = document.getElementById("apiRegistryStatus");
const addServiceButton = document.getElementById("addServiceButton");
const downloadServicesButton = document.getElementById("downloadServicesButton");
const serviceForm = document.getElementById("serviceForm");
const serviceNameInput = document.getElementById("serviceNameInput");
const serviceApiSelect = document.getElementById("serviceApiSelect");
const serviceQueryParamsInput = document.getElementById("serviceQueryParamsInput");
const serviceResponseTemplate = document.getElementById("serviceResponseTemplate");
const outputFieldsContainer = document.getElementById("outputFieldsContainer");
const addOutputFieldButton = document.getElementById("addOutputFieldButton");
const cancelServiceButton = document.getElementById("cancelServiceButton");
const serviceList = document.getElementById("serviceList");
const serviceBuilderStatus = document.getElementById("serviceBuilderStatus");
const addServiceFunctionButton = document.getElementById("addServiceFunctionButton");
const cancelNewServiceFunction = document.getElementById("cancelNewServiceFunction");
const newServiceFunctionEndpoint = document.getElementById("newServiceFunctionEndpoint");
const newServiceFunctionForm = document.getElementById("newServiceFunctionForm");
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
const connectorsFileName = document.getElementById("connectorsFileName");
const connectorsStatus = document.getElementById("connectorsStatus");
const connectorsPreview = document.getElementById("connectorsPreview");
const connectorsConfigTree = document.getElementById("connectorsConfigTree");
const connectorsDownloadButton = document.getElementById("connectorsDownloadButton");
const connectorsReloadButton = document.getElementById("connectorsReloadButton");
const connectorsTabButtons = document.querySelectorAll("[data-connectors-tab]");
const connectorsTabPanels = document.querySelectorAll("[data-connectors-tab-panel]");
const connectorToggles = {
  telegram: document.getElementById("telegramConnectorToggle"),
  whatsapp: document.getElementById("whatsappConnectorToggle"),
  messenger: document.getElementById("messengerConnectorToggle")
};
const connectorTrees = {
  telegram: document.getElementById("telegramConfigTree"),
  whatsapp: document.getElementById("whatsappConfigTree"),
  messenger: document.getElementById("messengerConfigTree")
};
const connectorPreviews = {
  telegram: document.getElementById("telegramPreview"),
  whatsapp: document.getElementById("whatsappPreview"),
  messenger: document.getElementById("messengerPreview")
};
const connectorStatuses = {
  telegram: document.getElementById("telegramConfigStatus"),
  whatsapp: document.getElementById("whatsappConfigStatus"),
  messenger: document.getElementById("messengerConfigStatus")
};
const connectorReloadButtons = {
  telegram: document.getElementById("telegramReloadButton"),
  whatsapp: document.getElementById("whatsappReloadButton"),
  messenger: document.getElementById("messengerReloadButton")
};
const connectorDisabledMessages = {
  telegram: document.getElementById("telegramDisabledMessage"),
  whatsapp: document.getElementById("whatsappDisabledMessage"),
  messenger: document.getElementById("messengerDisabledMessage")
};
const connectorDownloadButtons = {
  telegram: document.getElementById("telegramDownloadButton"),
  whatsapp: document.getElementById("whatsappDownloadButton"),
  messenger: document.getElementById("messengerDownloadButton")
};
const weblinksFileName = document.getElementById("weblinksFileName");
const weblinksStatus = document.getElementById("weblinksStatus");
const weblinksList = document.getElementById("weblinksList");
const weblinksPreview = document.getElementById("weblinksPreview");
const addWeblinkButton = document.getElementById("addWeblinkButton");
const weblinksDownloadButton = document.getElementById("weblinksDownloadButton");
const weblinksReloadButton = document.getElementById("weblinksReloadButton");
const weblinksAddForm = document.getElementById("weblinksAddForm");
const weblinkNameInput = document.getElementById("weblinkNameInput");
const weblinkUrlInput = document.getElementById("weblinkUrlInput");
const weblinkAuthInput = document.getElementById("weblinkAuthInput");
const weblinkContextInput = document.getElementById("weblinkContextInput");
const confirmWeblinkButton = document.getElementById("confirmWeblinkButton");
const cancelWeblinkButton = document.getElementById("cancelWeblinkButton");

const PUBLIC_BASE_URL_PLACEHOLDER = "YOUR_SERVER_PUBLIC_URL";

let resolvedMonitoringApiBase = "";
const CONNECTOR_KEYS = ["telegram", "whatsapp", "messenger"];
let connectorSettings = {
  telegram: true,
  whatsapp: true,
  messenger: true
};
let connectorsYamlObject = {
  connectors: { telegram: true, whatsapp: true, messenger: true }
};
let connectorsContent = "";
let connectorsOriginalContent = "";
let connectorContents = {
  telegram: "",
  whatsapp: "",
  messenger: ""
};
let connectorOriginalContents = {
  telegram: "",
  whatsapp: "",
  messenger: ""
};
let connectorYamlObjects = {
  telegram: {},
  whatsapp: {},
  messenger: {}
};
let connectorFileNames = {
  telegram: "telegram-local.yml",
  whatsapp: "whatsapp-local.yml",
  messenger: "messenger-local.yml"
};
let connectorsLoading = false;

let weblinks = [];
let weblinksContent = "";
let weblinksOriginalContent = "";
let weblinksFile = "weblinks-local.yml";

let activeApp = "menu";
let activeConnectorsTab = "general";

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
let apiRegistryEntries = [];
let serviceBuilderEntries = [];
let editingApiName = null;
let editingServiceIndex = null;

function normalizeServiceDefinition(service = {}) {
  const outputs = normalizeOutputFields(Array.isArray(service.outputs) ? service.outputs : parseLegacyOutputs(service.output));
  return {
    name: (service.name || "").trim(),
    apiName: service.apiName || "",
    queryParameters:
      typeof service.queryParameters === "object" && service.queryParameters !== null
        ? { ...service.queryParameters }
        : {},
    responseTemplate: service.responseTemplate || "JSON",
    outputs
  };
}

function createBlankServiceDefinition() {
  return normalizeServiceDefinition({
    name: `service-${serviceBuilderEntries.length + 1}`,
    apiName: apiRegistryEntries[0]?.name || "",
    outputs: [{ field: "", label: "", objectContext: false }]
  });
}

function prepareServiceForSave(service, index = 0) {
  const normalized = normalizeServiceDefinition(service);
  const slug = normalized.name ? slugify(normalized.name) : `service-${index + 1}`;
  return { ...normalized, name: slug };
}

function normalizeOutputFields(outputs = []) {
  const normalized = outputs
    .map((entry) => {
      const field = (entry?.field || entry?.Field || entry?.path || entry?.output || "").trim();
      const label = (entry?.label || entry?.Label || field).trim();
      const objectContext = Boolean(entry?.objectContext || entry?.["Object Context"]);
      if (!field) return null;
      return { field, label: label || field, objectContext };
    })
    .filter(Boolean);

  let contextSet = false;
  return normalized.map((entry) => {
    const flag = entry.objectContext && !contextSet;
    if (flag) {
      contextSet = true;
    }
    return { ...entry, objectContext: flag };
  });
}

function parseLegacyOutputs(rawOutput) {
  if (!rawOutput || typeof rawOutput !== "string") {
    return [];
  }
  return rawOutput
    .split(",")
    .map((part) => ({ field: part.trim(), label: part.trim(), objectContext: false }))
    .filter((entry) => entry.field);
}

function renderOutputFieldInputs(outputs = []) {
  if (!outputFieldsContainer) return;
  outputFieldsContainer.innerHTML = "";
  const normalized = normalizeOutputFields(outputs);
  const fields = normalized.length ? normalized : [{ field: "", label: "", objectContext: false }];
  fields.forEach((field) => addOutputFieldRow(field));
}

function addOutputFieldRow(field = { field: "", label: "", objectContext: false }) {
  if (!outputFieldsContainer) return;
  const row = document.createElement("div");
  row.className = "output-field-row";

  const fieldLabel = document.createElement("label");
  fieldLabel.textContent = "Field";
  const fieldInput = document.createElement("input");
  fieldInput.type = "text";
  fieldInput.value = field.field || "";
  fieldInput.placeholder = "items[0].id";
  fieldLabel.append(fieldInput);

  const labelLabel = document.createElement("label");
  labelLabel.textContent = "Label";
  const labelInput = document.createElement("input");
  labelInput.type = "text";
  labelInput.value = field.label || "";
  labelInput.placeholder = "Invoice ID";
  labelLabel.append(labelInput);

  const actions = document.createElement("div");
  actions.className = "output-actions";

  const contextWrapper = document.createElement("label");
  contextWrapper.className = "output-context-toggle";
  const contextInput = document.createElement("input");
  contextInput.type = "radio";
  contextInput.name = "objectContextField";
  contextInput.checked = Boolean(field.objectContext);
  contextWrapper.append(contextInput, document.createTextNode("Object Context"));

  const removeBtn = document.createElement("button");
  removeBtn.type = "button";
  removeBtn.className = "secondary";
  removeBtn.textContent = "Remove";
  removeBtn.addEventListener("click", () => {
    row.remove();
    if (!outputFieldsContainer.children.length) {
      addOutputFieldRow();
    }
  });

  actions.append(contextWrapper, removeBtn);

  contextInput.addEventListener("change", () => {
    document.querySelectorAll('input[name="objectContextField"]').forEach((input) => {
      if (input !== contextInput) {
        input.checked = false;
      }
    });
  });

  row.append(fieldLabel, labelLabel, actions);
  outputFieldsContainer.append(row);
}

function collectOutputFieldsFromForm() {
  if (!outputFieldsContainer) return [];
  const rows = Array.from(outputFieldsContainer.querySelectorAll(".output-field-row"));
  const entries = rows
    .map((row) => {
      const [fieldInput, labelInput] = row.querySelectorAll("input[type='text']");
      const contextInput = row.querySelector('input[name="objectContextField"]');
      const field = (fieldInput?.value || "").trim();
      const label = (labelInput?.value || "").trim();
      if (!field) return null;
      return { field, label: label || field, objectContext: Boolean(contextInput?.checked) };
    })
    .filter(Boolean);

  let contextApplied = false;
  return entries.map((entry) => {
    const flag = entry.objectContext && !contextApplied;
    if (flag) {
      contextApplied = true;
    }
    return { ...entry, objectContext: flag };
  });
}

function extractContextFields(item = {}) {
  return {
    accountContextEnabled: Boolean(item.accountContextEnabled),
    accountContextKey: item.accountContextKey || "",
    accountContextLabel: item.accountContextLabel || "",
    serviceContextEnabled: Boolean(item.serviceContextEnabled),
    serviceContextKey: item.serviceContextKey || "",
    serviceContextLabel: item.serviceContextLabel || "",
    menuContextEnabled: Boolean(item.menuContextEnabled),
    menuContextKey: item.menuContextKey || "",
    menuContextLabel: item.menuContextLabel || ""
  };
}

function serializeContextFields(item = {}) {
  return {
    accountContextEnabled: Boolean(item.accountContextEnabled),
    accountContextKey: item.accountContextKey || null,
    accountContextLabel: item.accountContextLabel || null,
    serviceContextEnabled: Boolean(item.serviceContextEnabled),
    serviceContextKey: item.serviceContextKey || null,
    serviceContextLabel: item.serviceContextLabel || null,
    menuContextEnabled: Boolean(item.menuContextEnabled),
    menuContextKey: item.menuContextKey || null,
    menuContextLabel: item.menuContextLabel || null
  };
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

function applyFunctionRuleNotice(element, functionId) {
  if (!element) return;
  const rule = FUNCTION_RULES[functionId];
  if (!rule?.note) {
    element.textContent = "";
    element.style.display = "none";
    return;
  }
  element.textContent = rule.note;
  element.style.display = "block";
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
  menusById = store.menusById;
  menuOrder = store.menuOrder;
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
      const explicitType = item?.type;
      const hasSubmenu = item?.submenuId && store.menusById.has(item.submenuId);
      const functionId = item.function || item.id;
      const isWeblink =
        explicitType === ITEM_TYPES.WEBLINK ||
        (!explicitType || explicitType === ITEM_TYPES.WEBLINK)
          ? Boolean(item?.weblink)
          : explicitType === ITEM_TYPES.WEBLINK;

      if (explicitType === ITEM_TYPES.FUNCTION_MENU || (hasSubmenu && functionId && explicitType !== ITEM_TYPES.SUBMENU)) {
        if (!functionDictionary[functionId]) {
          registerFunctionOption({
            id: functionId,
            label: item.label || functionId,
            translationKey: item.translationKey,
            callbackData: item.callbackData || functionId
          });
        }
        const submenu = store.menusById.get(item.submenuId);
        submenu.parentId = target.id;
        const meta = functionDictionary[functionId] || { id: functionId, label: functionId };
        const context = extractContextFields(item);
        target.items.push({
          id: item.id || nextItemId(),
          label: item.label ?? meta.label,
          type: ITEM_TYPES.FUNCTION_MENU,
          function: functionId,
          submenuId: submenu.id,
          useTranslation: item.useTranslation ?? Boolean(item.translationKey),
          ...context
        });
        return;
      }

      if (explicitType === ITEM_TYPES.SUBMENU || hasSubmenu) {
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

      if (isWeblink) {
        target.items.push({
          id: item.id || nextItemId(),
          label: item.label ?? item.weblink ?? "Web link",
          type: "weblink",
          weblink: item.weblink || item.id,
          function: null,
          submenuId: null,
          useTranslation: false
        });
        return;
      }
      if (functionId && !functionDictionary[functionId]) {
        registerFunctionOption({ id: functionId, label: item.label || functionId, translationKey: item.translationKey, callbackData: item.callbackData || functionId });
      }
      if (functionId) {
        const meta = functionDictionary[functionId] || { id: functionId, label: functionId };
        const context = extractContextFields(item);
        target.items.push({
          id: item.id || nextItemId(),
          label: item.label ?? meta.label,
          type: "function",
          function: functionId,
          useTranslation: item.useTranslation ?? Boolean(item.translationKey),
          submenuId: null,
          ...context
        });
      }
    });
  });

  store.selectedMenuId = store.menusById.has(store.selectedMenuId) ? store.selectedMenuId : store.rootId;
  selectedMenuId = store.selectedMenuId;
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
  const hasLegacyMenu = Array.isArray(loginMenu?.menu) && loginMenu.menu.length;
  const hasLegacySettingsMenu = Array.isArray(loginMenu?.settingsMenu) && loginMenu.settingsMenu.length;

  if (hasLegacyMenu || hasLegacySettingsMenu) {
    console.info("Using legacy login menu definition without auto-appended items");
    const legacyMenus = [];

    if (hasLegacyMenu) {
      legacyMenus.push({
        id: LOGIN_ROOT_MENU_ID,
        name: "Home",
        parentId: null,
        items: loginMenu.menu.map((item) => ({ ...item }))
      });
    }

    if (hasLegacySettingsMenu) {
      legacyMenus.push({
        id: "login-settings",
        name: "Settings",
        parentId: LOGIN_ROOT_MENU_ID,
        items: loginMenu.settingsMenu.map((item) => ({ ...item }))
      });
    }

    return legacyMenus;
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
  ensureRootMenu();

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
    typeSelect.innerHTML =
      "<option value=\"function\">Function</option>" +
      "<option value=\"function-menu\">Menu item with function</option>" +
      "<option value=\"submenu\">Sub-menu</option>" +
      "<option value=\"weblink\">Web link</option>";
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

function appendContextEditors(container, menuId, index, item) {
  const contextWrapper = document.createElement("div");
  contextWrapper.className = "context-fields";
  const contexts = [
    { label: "Menu context", flag: "menuContextEnabled", key: "menuContextKey", text: "menuContextLabel" },
    { label: "Service context", flag: "serviceContextEnabled", key: "serviceContextKey", text: "serviceContextLabel" },
    { label: "Account context", flag: "accountContextEnabled", key: "accountContextKey", text: "accountContextLabel" }
  ];

  contexts.forEach((ctx) => {
    const row = document.createElement("div");
    row.className = "context-row";

    const checkboxLabel = document.createElement("label");
    checkboxLabel.className = "checkbox";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = Boolean(item[ctx.flag]);
    const checkboxText = document.createElement("span");
    checkboxText.textContent = ctx.label;
    checkboxLabel.append(checkbox, checkboxText);

    const inputs = document.createElement("div");
    inputs.className = "context-row__inputs";
    const keyInput = document.createElement("input");
    keyInput.type = "text";
    keyInput.placeholder = "Query key";
    keyInput.value = item[ctx.key] || "";
    keyInput.disabled = !checkbox.checked;
    keyInput.addEventListener("input", (event) => {
      menusById.get(menuId).items[index][ctx.key] = event.target.value;
      updatePreview();
    });

    const labelInput = document.createElement("input");
    labelInput.type = "text";
    labelInput.placeholder = "Display label";
    labelInput.value = item[ctx.text] || "";
    labelInput.disabled = !checkbox.checked;
    labelInput.addEventListener("input", (event) => {
      menusById.get(menuId).items[index][ctx.text] = event.target.value;
      updatePreview();
    });

    checkbox.addEventListener("change", (event) => {
      menusById.get(menuId).items[index][ctx.flag] = event.target.checked;
      keyInput.disabled = !event.target.checked;
      labelInput.disabled = !event.target.checked;
      updatePreview();
    });

    inputs.append(keyInput, labelInput);
    row.append(checkboxLabel, inputs);
    contextWrapper.append(row);
  });

  const hint = document.createElement("p");
  hint.className = "hint";
  hint.textContent = "Enabled contexts add their key/value to the API call and prefix the response.";
  contextWrapper.append(hint);

  container.append(contextWrapper);
}

function renderItemDetails(container, menuId, item, index) {
  container.innerHTML = "";
  if (item.type === ITEM_TYPES.FUNCTION || item.type === ITEM_TYPES.FUNCTION_MENU) {
    const functionWrapper = document.createElement("label");
    functionWrapper.textContent = "Function";
    const functionDropdown = document.createElement("select");
    initFunctionSelect(functionDropdown);
    functionDropdown.value = item.function;
    const functionRuleNotice = document.createElement("p");
    functionRuleNotice.className = "hint";
    applyFunctionRuleNotice(functionRuleNotice, functionDropdown.value);
    functionDropdown.addEventListener("change", (event) => {
      if (!functionDictionary[event.target.value]) {
        return;
      }
      menusById.get(menuId).items[index].function = event.target.value;
      applyFunctionRuleNotice(functionRuleNotice, event.target.value);
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

    appendContextEditors(container, menuId, index, menusById.get(menuId).items[index]);

    container.append(functionRuleNotice);

    if (item.type === ITEM_TYPES.FUNCTION_MENU) {
      const submenuWrapper = document.createElement("label");
      submenuWrapper.textContent = "Target sub-menu";
      const submenuDropdown = buildSubmenuDropdown(menuId, item.submenuId);
      submenuDropdown.addEventListener("change", (event) => assignSubmenu(menuId, index, event.target.value, () => {
        submenuDropdown.value = item.submenuId || "";
      }));
      submenuWrapper.append(submenuDropdown);

      const submenuHint = document.createElement("p");
      submenuHint.className = "hint";
      submenuHint.textContent = "Runs the function and then opens the selected sub-menu for its results.";

      container.append(submenuWrapper, submenuHint);
    }
    return;
  }

  if (item.type === "weblink") {
    const linkWrapper = document.createElement("label");
    linkWrapper.textContent = "Web link";
    const linkDropdown = document.createElement("select");
    renderWeblinkOptions(linkDropdown, item.weblink);
    linkDropdown.addEventListener("change", (event) => {
      menusById.get(menuId).items[index].weblink = event.target.value;
      updatePreview();
    });
    linkWrapper.append(linkDropdown);

    const linkHint = document.createElement("p");
    linkHint.className = "hint";
    linkHint.textContent = "Opens the configured link on the user's device.";

    container.append(linkWrapper, linkHint);
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

  if ((item.type === ITEM_TYPES.SUBMENU || item.type === ITEM_TYPES.FUNCTION_MENU) && item.submenuId) {
    detachSubmenu(item.submenuId, menuId);
  }

  if (newType === ITEM_TYPES.SUBMENU) {
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
    item.weblink = null;
  } else if (newType === ITEM_TYPES.FUNCTION_MENU) {
    const options = availableSubmenus(menuId);
    if (!options.length) {
      alert("Create a sub-menu before linking it.");
      event.target.value = ITEM_TYPES.FUNCTION;
      return;
    }
    const target = options[0];
    if (!linkSubmenu(menuId, target.id)) {
      event.target.value = ITEM_TYPES.FUNCTION;
      return;
    }
    item.type = ITEM_TYPES.FUNCTION_MENU;
    item.submenuId = target.id;
    item.function = item.function && functionDictionary[item.function] ? item.function : FUNCTION_OPTIONS[0].id;
    item.useTranslation = item.useTranslation ?? true;
    item.weblink = null;
  } else if (newType === ITEM_TYPES.WEBLINK) {
    item.type = "weblink";
    item.weblink = (weblinks[0] && weblinks[0].name) || "";
    item.function = null;
    item.submenuId = null;
    item.useTranslation = false;
  } else {
    item.type = "function";
    item.function = item.function && functionDictionary[item.function] ? item.function : FUNCTION_OPTIONS[0].id;
    item.submenuId = null;
    item.useTranslation = item.useTranslation ?? true;
    item.weblink = null;
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
  if ((removed?.type === ITEM_TYPES.SUBMENU || removed?.type === ITEM_TYPES.FUNCTION_MENU) && removed.submenuId) {
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

function getContextFormState() {
  return {
    menuContextEnabled: Boolean(menuContextToggle?.checked),
    menuContextKey: (menuContextKeyInput?.value || "").trim(),
    menuContextLabel: (menuContextLabelInput?.value || "").trim(),
    serviceContextEnabled: Boolean(serviceContextToggle?.checked),
    serviceContextKey: (serviceContextKeyInput?.value || "").trim(),
    serviceContextLabel: (serviceContextLabelInput?.value || "").trim(),
    accountContextEnabled: Boolean(accountContextToggle?.checked),
    accountContextKey: (accountContextKeyInput?.value || "").trim(),
    accountContextLabel: (accountContextLabelInput?.value || "").trim()
  };
}

function resetContextForm() {
  [menuContextToggle, serviceContextToggle, accountContextToggle].forEach((toggle) => {
    if (toggle) toggle.checked = false;
  });
  [menuContextKeyInput, menuContextLabelInput, serviceContextKeyInput, serviceContextLabelInput,
    accountContextKeyInput, accountContextLabelInput].forEach((input) => {
    if (input) input.value = "";
  });
  syncContextInputStates();
}

function syncContextInputStates() {
  const pairs = [
    [menuContextToggle, menuContextKeyInput, menuContextLabelInput],
    [serviceContextToggle, serviceContextKeyInput, serviceContextLabelInput],
    [accountContextToggle, accountContextKeyInput, accountContextLabelInput]
  ];
  pairs.forEach(([toggle, keyInput, labelInput]) => {
    const enabled = Boolean(toggle?.checked);
    if (keyInput) keyInput.disabled = !enabled;
    if (labelInput) labelInput.disabled = !enabled;
  });
}

function toggleContextFormVisibility(enabled) {
  if (!contextFields) return;
  contextFields.classList.toggle("hidden", !enabled);
  syncContextInputStates();
}

function toggleAddFormFields() {
  const type = itemTypeSelect.value;
  if (type === ITEM_TYPES.FUNCTION) {
    functionFields.classList.remove("hidden");
    submenuFields.classList.add("hidden");
    weblinkFields.classList.add("hidden");
    toggleContextFormVisibility(true);
  } else if (type === ITEM_TYPES.SUBMENU) {
    functionFields.classList.add("hidden");
    submenuFields.classList.remove("hidden");
    updateAddFormSubmenuOptions();
    weblinkFields.classList.add("hidden");
    toggleContextFormVisibility(false);
  } else if (type === ITEM_TYPES.FUNCTION_MENU) {
    functionFields.classList.remove("hidden");
    submenuFields.classList.remove("hidden");
    updateAddFormSubmenuOptions();
    weblinkFields.classList.add("hidden");
    toggleContextFormVisibility(true);
  } else {
    functionFields.classList.add("hidden");
    submenuFields.classList.add("hidden");
    weblinkFields.classList.remove("hidden");
    renderWeblinkOptions(menuWeblinkSelect);
    toggleContextFormVisibility(false);
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
          if (item.type === ITEM_TYPES.FUNCTION_MENU) {
            const meta = functionDictionary[item.function] || {};
          return {
            order: index + 1,
            type: ITEM_TYPES.FUNCTION_MENU,
            label: item.label,
            function: item.function,
            callbackData: meta.callbackData || item.function,
            translationKey: item.useTranslation && meta.translationKey ? meta.translationKey : null,
            submenuId: item.submenuId,
            ...serializeContextFields(item)
          };
        }

          if (item.type === ITEM_TYPES.SUBMENU) {
            return {
              order: index + 1,
              type: ITEM_TYPES.SUBMENU,
              label: item.label,
              function: null,
              callbackData: null,
              translationKey: null,
              submenuId: item.submenuId
            };
          }

          if (item.type === ITEM_TYPES.WEBLINK) {
            const linkMeta = findWeblinkMeta(item.weblink);
            return {
              order: index + 1,
              type: ITEM_TYPES.WEBLINK,
              label: item.label,
              function: null,
              callbackData: null,
              translationKey: null,
              submenuId: null,
              weblink: item.weblink || null,
              url: linkMeta?.url || null,
              authenticated: Boolean(linkMeta?.authenticated),
              context: linkMeta?.context || "noContext"
            };
          }

          const meta = functionDictionary[item.function] || {};
          return {
            order: index + 1,
            type: ITEM_TYPES.FUNCTION,
            label: item.label,
            function: item.function,
            callbackData: meta.callbackData || item.function,
            translationKey: item.useTranslation && meta.translationKey ? meta.translationKey : null,
            submenuId: null,
            ...serializeContextFields(item)
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
  if (itemTypeSelect.value === ITEM_TYPES.FUNCTION) {
    const functionId = menuFunctionSelect.value;
    if (!functionDictionary[functionId]) {
      alert("Please choose a valid function.");
      return;
    }
    const contextState = getContextFormState();
    parentMenu.items.push({
      id: nextItemId(),
      label,
      type: "function",
      function: functionId,
      useTranslation: useTranslationInput.checked,
      submenuId: null,
      ...contextState
    });
  } else if (itemTypeSelect.value === ITEM_TYPES.SUBMENU) {
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
  } else if (itemTypeSelect.value === ITEM_TYPES.FUNCTION_MENU) {
    const functionId = menuFunctionSelect.value;
    if (!functionDictionary[functionId]) {
      alert("Please choose a valid function.");
      return;
    }
    if (submenuSelect.disabled || !submenuSelect.value) {
      alert("Create a sub-menu before adding this item.");
      return;
    }
    if (!linkSubmenu(parentId, submenuSelect.value)) {
      return;
    }
    const contextState = getContextFormState();
    parentMenu.items.push({
      id: nextItemId(),
      label,
      type: ITEM_TYPES.FUNCTION_MENU,
      function: functionId,
      submenuId: submenuSelect.value,
      useTranslation: useTranslationInput.checked,
      ...contextState
    });
  } else {
    if (!menuWeblinkSelect.value) {
      alert("Add a web link configuration first.");
      return;
    }
    parentMenu.items.push({
      id: nextItemId(),
      label,
      type: "weblink",
      weblink: menuWeblinkSelect.value
    });
  }
  selectedMenuId = parentId;
  addItemForm.reset();
  resetContextForm();
  itemTypeSelect.value = "function";
  useTranslationInput.checked = false;
  toggleAddFormFields();
  renderAll();
}

function setActiveApp(target) {
  activeApp = target;
  const showMenuConfig = target === "menu";
  const showOperations = target === "operations";
  const showSendMessages = target === "notifications";
  const showImServerAdmin = target === "admin";
  const showApiRegistry = target === "api-registry";
  const showServiceBuilder = target === "service-builder";
  const showConnectors = target === "connectors";
  const showWeblinks = target === "weblinks";
  menuConfigurationPanel.classList.toggle("hidden", !showMenuConfig);
  operationsMonitoringPanel.classList.toggle("hidden", !showOperations);
  sendMessagesPanel.classList.toggle("hidden", !showSendMessages);
  imServerAdminPanel.classList.toggle("hidden", !showImServerAdmin);
  apiRegistryPanel.classList.toggle("hidden", !showApiRegistry);
  serviceBuilderPanel.classList.toggle("hidden", !showServiceBuilder);
  if (connectorsPanel) {
    connectorsPanel.classList.toggle("hidden", !showConnectors);
  }
  if (weblinksPanel) {
    weblinksPanel.classList.toggle("hidden", !showWeblinks);
  }
  navMenuConfig.classList.toggle("active", showMenuConfig);
  navOperationsMonitoring.classList.toggle("active", showOperations);
  navSendMessages.classList.toggle("active", showSendMessages);
  navImServerAdmin.classList.toggle("active", showImServerAdmin);
  if (navApiRegistration) {
    navApiRegistration.classList.toggle("active", showApiRegistry);
  }
  if (navServiceBuilder) {
    navServiceBuilder.classList.toggle("active", showServiceBuilder);
  }
  if (navConnectors) {
    navConnectors.classList.toggle("active", showConnectors);
  }
  if (navWeblinks) {
    navWeblinks.classList.toggle("active", showWeblinks);
  }
  if (showOperations) {
    refreshMonitoringData();
  }
  if (showMenuConfig) {
    loadServiceBuilder();
  }
  if (showImServerAdmin) {
    loadImServerConfig();
  }
  if (showApiRegistry) {
    loadApiRegistry();
  }
  if (showServiceBuilder) {
    loadServiceBuilder();
  }
  if (showConnectors) {
    loadConnectorsPanel();
  }
  updateSaveButtonVisibility();
}

function updateSaveButtonVisibility() {
  if (!saveOverlayButton) return;
  const saveableSections = ["menu", "api-registry", "service-builder", "weblinks", "connectors", "admin"];
  const shouldShow = saveableSections.includes(activeApp);
  saveOverlayButton.classList.toggle("hidden", !shouldShow);
}

async function loadApiRegistry() {
  const endpoint = buildAdminEndpoint("/admin/apis");
  if (!endpoint) {
    apiRegistryStatus.textContent = "Set the monitoring API base URL to load APIs.";
    apiRegistryStatus.className = "hint error-state";
    return;
  }

  try {
    const response = await fetch(endpoint, { cache: "no-cache" });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    apiRegistryEntries = Array.isArray(payload?.apis) ? payload.apis : [];
    renderApiList();
    hydrateServiceApiOptions();
    apiRegistryStatus.textContent = "";
    apiRegistryStatus.className = "hint";
  } catch (error) {
    apiRegistryStatus.textContent = `Unable to load API list: ${error?.message || error}`;
    apiRegistryStatus.className = "hint error-state";
  }
}

function toggleApiForm(show, api = null) {
  if (!apiForm) return;
  apiForm.classList.toggle("hidden", !show);
  if (show && api) {
    apiNameInput.value = api.name || "";
    apiUrlInput.value = api.url || "";
    editingApiName = api.name;
  } else if (show) {
    apiForm.reset();
    editingApiName = null;
  } else {
    apiForm.reset();
    editingApiName = null;
  }
}

function renderApiList() {
  if (!apiList) return;
  apiList.innerHTML = "";

  if (!apiRegistryEntries.length) {
    apiList.textContent = "No APIs configured yet.";
    return;
  }

  apiRegistryEntries.forEach((api, index) => {
    const row = document.createElement("div");
    row.className = "config-entry config-entry--inline";

    const nameField = document.createElement("input");
    nameField.type = "text";
    nameField.value = api?.name || "";
    nameField.placeholder = "API name";
    nameField.dataset.previousName = api?.name || "";
    nameField.addEventListener("input", (event) => {
      const previous = nameField.dataset.previousName || apiRegistryEntries[index].name;
      const nextName = event.target.value;
      apiRegistryEntries[index].name = nextName;
      if (previous && previous !== nextName) {
        serviceBuilderEntries = serviceBuilderEntries.map((svc) =>
          svc.apiName === previous ? { ...svc, apiName: nextName } : svc
        );
        nameField.dataset.previousName = nextName;
        hydrateServiceApiOptions();
        renderServiceList();
      }
    });

    const urlField = document.createElement("input");
    urlField.type = "url";
    urlField.value = api?.url || "";
    urlField.placeholder = "https://example.com/api";
    urlField.addEventListener("input", (event) => {
      apiRegistryEntries[index].url = event.target.value;
    });

    const actions = document.createElement("div");
    actions.className = "config-entry__actions";

    const deleteBtn = document.createElement("button");
    deleteBtn.type = "button";
    deleteBtn.textContent = "✕";
    deleteBtn.title = "Remove";
    deleteBtn.addEventListener("click", () => {
      const name = apiRegistryEntries[index]?.name;
      apiRegistryEntries.splice(index, 1);
      if (name) {
        serviceBuilderEntries = serviceBuilderEntries.map((svc) =>
          svc.apiName === name ? { ...svc, apiName: "" } : svc
        );
      }
      hydrateServiceApiOptions();
      renderServiceList();
      renderApiList();
    });

    actions.append(deleteBtn);
    row.append(nameField, urlField, actions);
    apiList.append(row);
  });
}

async function loadServiceBuilder() {
  const endpoint = buildAdminEndpoint("/admin/services");
  if (!endpoint) {
    serviceBuilderStatus.textContent = "Set the monitoring API base URL to load services.";
    serviceBuilderStatus.className = "hint error-state";
    return;
  }

  try {
    const response = await fetch(endpoint, { cache: "no-cache" });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    apiRegistryEntries = Array.isArray(payload?.apis) ? payload.apis : apiRegistryEntries;
    serviceBuilderEntries = Array.isArray(payload?.services)
      ? payload.services.map((svc) => normalizeServiceDefinition(svc))
      : [];
    hydrateServiceApiOptions();
    renderApiList();
    renderServiceList();
    syncServiceFunctionOptions(serviceBuilderEntries);
    serviceBuilderStatus.textContent = "";
    serviceBuilderStatus.className = "hint";
  } catch (error) {
    serviceBuilderStatus.textContent = `Unable to load services: ${error?.message || error}`;
    serviceBuilderStatus.className = "hint error-state";
  }
}

function toggleServiceForm(show, service = null) {
  if (!serviceForm) return;
  serviceForm.classList.toggle("hidden", !show);
  if (show && service) {
    serviceNameInput.value = service.name || "";
    serviceApiSelect.value = service.apiName || "";
    serviceQueryParamsInput.value = formatQueryParams(service.queryParameters);
    serviceResponseTemplate.value = service.responseTemplate || "JSON";
    renderOutputFieldInputs(service.outputs);
    editingServiceIndex = serviceBuilderEntries.findIndex((entry) => entry === service);
  } else if (show) {
    serviceForm.reset();
    renderOutputFieldInputs([]);
    editingServiceIndex = null;
  } else {
    serviceForm.reset();
    renderOutputFieldInputs([]);
    editingServiceIndex = null;
  }
}

function hydrateServiceApiOptions() {
  if (!serviceApiSelect) return;
  const currentValue = serviceApiSelect.value;
  serviceApiSelect.innerHTML = "";
  apiRegistryEntries
    .slice()
    .sort((a, b) => (a.name || "").localeCompare(b.name || ""))
    .forEach((api) => {
      const option = document.createElement("option");
      option.value = api.name;
      option.textContent = api.name;
      serviceApiSelect.appendChild(option);
    });
  if (currentValue) {
    serviceApiSelect.value = currentValue;
  }
}

function renderServiceList() {
  if (!serviceList) return;
  serviceBuilderEntries = serviceBuilderEntries.map((svc) => normalizeServiceDefinition(svc));
  serviceList.innerHTML = "";

  if (!serviceBuilderEntries.length) {
    serviceList.textContent = "No services configured yet.";
    return;
  }

  serviceBuilderEntries.forEach((service, index) => {
    const card = document.createElement("div");
    card.className = "service-card";

    const header = document.createElement("div");
    header.className = "service-card__header";

    const title = document.createElement("div");
    title.className = "service-card__title";
    title.textContent = service.name || "Unnamed service";
    const meta = document.createElement("div");
    meta.className = "service-card__meta";
    const templateLabel = service.responseTemplate === "CARD" ? "List of Objects" : service.responseTemplate;
    meta.textContent = `API: ${service.apiName || ""} • Type: ${templateLabel}`;
    header.append(title, meta);

    const queryLine = document.createElement("div");
    queryLine.className = "service-card__meta";
    queryLine.textContent = `Query Parameters: ${formatQueryParams(service.queryParameters) || "None"}`;

    const outputsBox = document.createElement("div");
    outputsBox.className = "service-card__outputs";
    const outputs = normalizeOutputFields(service.outputs);
    if (!outputs.length) {
      outputsBox.textContent = "No output fields configured.";
    } else {
      outputs.forEach((output) => {
        const line = document.createElement("div");
        const label = document.createElement("strong");
        label.textContent = `${output.label}: `;
        const value = document.createElement("span");
        value.textContent = output.field;
        line.append(label, value);
        if (output.objectContext) {
          const pill = document.createElement("span");
          pill.className = "service-pill";
          pill.textContent = "Object Context";
          line.append(" ", pill);
        }
        outputsBox.append(line);
      });
    }

    const actions = document.createElement("div");
    actions.className = "service-card__actions";
    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.textContent = "Edit";
    editBtn.addEventListener("click", () => startServiceEdit(service, index));

    const deleteBtn = document.createElement("button");
    deleteBtn.type = "button";
    deleteBtn.className = "secondary";
    deleteBtn.textContent = "Delete";
    deleteBtn.addEventListener("click", () => {
      serviceBuilderEntries.splice(index, 1);
      syncServiceFunctionOptions(serviceBuilderEntries);
      renderServiceList();
    });

    actions.append(editBtn, deleteBtn);

    card.append(header, queryLine, outputsBox, actions);
    serviceList.append(card);
  });
}

function startServiceEdit(service, index = null) {
  editingServiceIndex = index;
  toggleServiceForm(true, normalizeServiceDefinition(service));
  serviceNameInput?.focus();
}

function handleServiceFormSubmit(event) {
  event.preventDefault();
  const name = (serviceNameInput?.value || "").trim();
  const apiName = serviceApiSelect?.value || "";
  if (!name || !apiName) {
    alert("Please provide both Service Name and API Name.");
    return;
  }
  const queryParameters = parseQueryParams(serviceQueryParamsInput?.value || "");
  const outputs = collectOutputFieldsFromForm();
  const responseTemplate = serviceResponseTemplate?.value || "JSON";
  const payload = normalizeServiceDefinition({ name, apiName, queryParameters, outputs, responseTemplate });

  if (editingServiceIndex !== null && editingServiceIndex >= 0) {
    serviceBuilderEntries[editingServiceIndex] = payload;
  } else {
    serviceBuilderEntries = [...serviceBuilderEntries, payload];
  }
  editingServiceIndex = null;
  toggleServiceForm(false);
  syncServiceFunctionOptions(serviceBuilderEntries);
  renderServiceList();
  serviceBuilderStatus.textContent = "Changes saved. Publish to apply.";
  serviceBuilderStatus.className = "hint";
}

function toggleNewServiceFunctionForm() {}

function prefillQueryParamsFromEndpoint() {}

function createCustomServiceFunction() {}

async function persistContent(endpoint, content, statusEl) {
  if (!endpoint) {
    if (statusEl) {
      statusEl.textContent = "Set the monitoring API base URL so the Java server can be reached.";
      statusEl.className = "hint error-state";
    }
    return false;
  }

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ content })
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      const reason = payload?.reason || response.statusText || `HTTP ${response.status}`;
      throw new Error(reason);
    }
    if (statusEl) {
      statusEl.textContent = "Saved. Reloading UI...";
      statusEl.className = "hint";
    }
    setTimeout(() => window.location.reload(), 300);
    return true;
  } catch (error) {
    if (statusEl) {
      statusEl.textContent = `Unable to save configuration: ${error?.message || error}`;
      statusEl.className = "hint error-state";
    }
    return false;
  }
}

async function saveMenuConfigurationFile() {
  const config = buildConfig();
  if (!config?.menus || !config.menus.length) {
    alert("Please configure at least one menu item before saving.");
    return;
  }
  const endpoint = buildAdminEndpoint("/menu-config/save");
  if (!endpoint) {
    configStatus.textContent = "Set the monitoring API base URL so the Java server can be reached.";
    configStatus.className = "hint error-state";
    return;
  }
  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(config)
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    setTimeout(() => window.location.reload(), 300);
  } catch (error) {
    configStatus.textContent = `Unable to save menu configuration: ${error?.message || error}`;
    configStatus.className = "hint error-state";
  }
}

async function saveApiRegistryFile() {
  const endpoint = buildAdminEndpoint("/admin/apis/save");
  if (!endpoint) {
    apiRegistryStatus.textContent = "Set the monitoring API base URL to save APIs.";
    apiRegistryStatus.className = "hint error-state";
    return;
  }
  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(apiRegistryEntries)
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    apiRegistryStatus.textContent = "APIs saved. Reloading UI...";
    apiRegistryStatus.className = "hint";
    setTimeout(() => window.location.reload(), 300);
  } catch (error) {
    apiRegistryStatus.textContent = `Unable to save API list: ${error?.message || error}`;
    apiRegistryStatus.className = "hint error-state";
  }
}

async function saveServiceBuilderFile() {
  const endpoint = buildAdminEndpoint("/admin/services/save");
  if (!endpoint) {
    serviceBuilderStatus.textContent = "Set the monitoring API base URL to save services.";
    serviceBuilderStatus.className = "hint error-state";
    return;
  }
  try {
    const payload = serviceBuilderEntries.map((svc, index) => prepareServiceForSave(svc, index));
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    serviceBuilderStatus.textContent = "Services saved. Reloading UI...";
    serviceBuilderStatus.className = "hint";
    setTimeout(() => window.location.reload(), 300);
  } catch (error) {
    serviceBuilderStatus.textContent = `Unable to save services: ${error?.message || error}`;
    serviceBuilderStatus.className = "hint error-state";
  }
}

async function saveWeblinksFile() {
  const endpoint = buildAdminEndpoint("/admin/config/weblinks");
  const content = weblinksContent || buildWeblinksYaml();
  await persistContent(endpoint, content, weblinksStatus);
}

async function saveConnectorsFile() {
  const tab = activeConnectorsTab || "general";
  const targetEndpoint = tab === "general" ? "/admin/config/connectors" : `/admin/config/${tab}`;
  const endpoint = buildAdminEndpoint(targetEndpoint);
  const statusEl = tab === "general" ? connectorsStatus : connectorStatuses[tab];
  const content = tab === "general"
    ? connectorsContent || buildConnectorsYaml()
    : connectorContents[tab] || stringifySimpleYaml(connectorYamlObjects[tab]) || defaultConnectorTemplate(tab);
  await persistContent(endpoint, content, statusEl);
}

async function saveImServerConfigFile() {
  const endpoint = buildAdminEndpoint("/admin/config/application");
  const content = imConfigContent?.textContent || "";
  await persistContent(endpoint, content, configStatus);
}

async function handleSaveClick() {
  if (!saveOverlayButton) return;
  saveOverlayButton.disabled = true;
  switch (activeApp) {
    case "menu":
      await saveMenuConfigurationFile();
      break;
    case "api-registry":
      await saveApiRegistryFile();
      break;
    case "service-builder":
      await saveServiceBuilderFile();
      break;
    case "weblinks":
      await saveWeblinksFile();
      break;
    case "connectors":
      await saveConnectorsFile();
      break;
    case "admin":
      await saveImServerConfigFile();
      break;
    default:
      break;
  }
  saveOverlayButton.disabled = false;
}

function formatConnectorLabel(key) {
  if (!key) return "Connector";
  return key.charAt(0).toUpperCase() + key.slice(1);
}

function resolveBooleanFlag(value, fallback = true) {
  if (value === undefined || value === null) return fallback;
  if (typeof value === "boolean") return value;
  const normalized = value.toString().trim().toLowerCase();
  if (["true", "y", "yes", "1"].includes(normalized)) return true;
  if (["false", "n", "no", "0"].includes(normalized)) return false;
  return fallback;
}

function normalizeYamlValue(value) {
  if (value === true || value === false || value === null || typeof value === "number") {
    return value;
  }
  const trimmed = (value ?? "").toString().trim();
  if (trimmed.toLowerCase() === "true") return true;
  if (trimmed.toLowerCase() === "false") return false;
  const numeric = Number(trimmed);
  if (!Number.isNaN(numeric) && trimmed !== "") {
    return numeric;
  }
  return trimmed;
}

function setNestedValue(target, path, rawValue) {
  if (!target || !Array.isArray(path) || path.length === 0) return;
  const value = normalizeYamlValue(rawValue);
  let current = target;
  const [lastKey] = path.slice(-1);
  path.slice(0, -1).forEach((segment) => {
    if (current[segment] === undefined || current[segment] === null || typeof current[segment] !== "object") {
      current[segment] = {};
    }
    current = current[segment];
  });
  current[lastKey] = value;
}

function getNestedValue(target, path, fallback) {
  if (!target || !Array.isArray(path)) return fallback;
  return path.reduce((acc, key) => (acc && acc[key] !== undefined ? acc[key] : undefined), target) ?? fallback;
}

function parseSimpleYaml(content) {
  const root = {};
  if (!content) return root;

  const stack = [{ indent: -1, obj: root }];
  content.split(/\r?\n/).forEach((rawLine) => {
    const line = (rawLine || "").replace(/\t/g, "  ");
    if (!line.trim() || line.trim().startsWith("#")) return;
    const match = line.match(/^(\s*)([^:#]+):\s*(.*)$/);
    if (!match) return;
    const indent = match[1].length;
    const key = match[2].trim();
    const value = match[3];

    while (stack.length && indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }
    const parent = stack[stack.length - 1]?.obj || root;

    if (value === undefined || value === "") {
      parent[key] = parent[key] && typeof parent[key] === "object" ? parent[key] : {};
      stack.push({ indent, obj: parent[key] });
    } else {
      parent[key] = normalizeYamlValue(value);
    }
  });

  return root;
}

function stringifySimpleYaml(obj, depth = 0) {
  if (obj === null || obj === undefined) return "";
  if (typeof obj !== "object") return `${obj}`;
  const indent = "  ".repeat(depth);
  return Object.entries(obj)
    .map(([key, value]) => {
      if (value && typeof value === "object") {
        const child = stringifySimpleYaml(value, depth + 1);
        return `${indent}${key}:\n${child}`;
      }
      return `${indent}${key}: ${value ?? ""}`;
    })
    .join("\n");
}

function objectToNodes(obj, path = []) {
  if (!obj || typeof obj !== "object") return [];
  return Object.entries(obj).map(([key, value]) => ({
    key,
    value,
    path: [...path, key]
  }));
}

function renderYamlTree(container, obj, onValueChange) {
  if (!container) return;
  container.innerHTML = "";
  const nodes = objectToNodes(obj);
  nodes.forEach((node) => renderYamlNode(node, 0, container, onValueChange));
}

function renderYamlNode(node, depth, container, onValueChange) {
  const hasChildren = node?.value && typeof node.value === "object";
  const row = document.createElement("div");
  row.className = hasChildren ? "config-group" : "config-entry";
  row.style.setProperty("--depth", depth);

  const keyEl = document.createElement("div");
  keyEl.className = "config-entry__key";
  keyEl.textContent = node?.key || "value";
  row.append(keyEl);

  if (hasChildren) {
    const divider = document.createElement("div");
    divider.className = "config-group__divider";
    row.append(divider);
    container.append(row);
    objectToNodes(node.value, node.path).forEach((child) =>
      renderYamlNode(child, depth + 1, container, onValueChange)
    );
    return;
  }

  const valueEl = document.createElement("input");
  const isNumber = typeof node.value === "number";
  valueEl.type = isNumber ? "number" : "text";
  valueEl.value = node.value ?? "";
  valueEl.placeholder = isNumber ? "number" : "value";
  valueEl.setAttribute("aria-label", `Edit value for ${keyEl.textContent}`);
  valueEl.addEventListener("input", (event) => {
    const val = isNumber ? event.target.valueAsNumber : event.target.value;
    onValueChange?.(node.path, val);
  });

  row.append(valueEl);
  container.append(row);
}

function buildConnectorsYaml() {
  const source = connectorsYamlObject?.connectors ? connectorsYamlObject : {
    connectors: { ...connectorSettings }
  };
  return stringifySimpleYaml(source);
}

function applyConnectorSettingsFromYaml() {
  connectorSettings = {
    telegram: Boolean(getNestedValue(connectorsYamlObject, ["connectors", "telegram"], true)),
    whatsapp: Boolean(getNestedValue(connectorsYamlObject, ["connectors", "whatsapp"], true)),
    messenger: Boolean(getNestedValue(connectorsYamlObject, ["connectors", "messenger"], true))
  };
}

function ensureConnectorYamlDefaults() {
  if (!connectorsYamlObject || typeof connectorsYamlObject !== "object") {
    connectorsYamlObject = { connectors: { ...connectorSettings } };
  }
  if (!connectorsYamlObject.connectors || typeof connectorsYamlObject.connectors !== "object") {
    connectorsYamlObject.connectors = { ...connectorSettings };
  }
  CONNECTOR_KEYS.forEach((key) => {
    if (connectorsYamlObject.connectors[key] === undefined) {
      connectorsYamlObject.connectors[key] = connectorSettings[key] ?? true;
    }
  });
  applyConnectorSettingsFromYaml();
  connectorsContent = buildConnectorsYaml();
}

function syncConnectorsFromContent() {
  connectorsYamlObject = parseSimpleYaml(connectorsContent || buildConnectorsYaml());
  ensureConnectorYamlDefaults();
}

function ensureConnectorObjectDefaults(key) {
  const current = connectorYamlObjects[key] && typeof connectorYamlObjects[key] === "object"
    ? connectorYamlObjects[key]
    : {};
  if (!current[key] || typeof current[key] !== "object") {
    current[key] = current[key] && typeof current[key] === "object" ? current[key] : {};
  }
  connectorYamlObjects[key] = current;
  connectorContents[key] = stringifySimpleYaml(current) || defaultConnectorTemplate(key);
  return current;
}

function syncConnectorObjectFromContent(key) {
  connectorYamlObjects[key] = parseSimpleYaml(connectorContents[key] || defaultConnectorTemplate(key));
  ensureConnectorObjectDefaults(key);
  connectorContents[key] = stringifySimpleYaml(connectorYamlObjects[key]);
}

function renderConnectorsGeneral() {
  if (!connectorsPreview || !connectorsFileName || !connectorsStatus) {
    return;
  }

  ensureConnectorYamlDefaults();

  const updateToggleUi = () => {
    Object.entries(connectorToggles).forEach(([toggleKey, input]) => {
      if (!input) return;
      const enabled = Boolean(connectorSettings[toggleKey]);
      input.checked = enabled;
      const valueLabel = input.parentElement?.querySelector(".switch-value");
      if (valueLabel) {
        valueLabel.textContent = enabled ? "On" : "Off";
      }
    });
  };

  updateToggleUi();
  renderYamlTree(connectorsConfigTree, connectorsYamlObject, (path, value) => {
    setNestedValue(connectorsYamlObject, path, value);
    applyConnectorSettingsFromYaml();
    connectorsContent = buildConnectorsYaml();
    updateToggleUi();
    connectorsPreview.textContent = connectorsContent;
  });

  const content = connectorsContent || buildConnectorsYaml();
  connectorsPreview.textContent = content;
}

function renderConnectorPreview(key) {
  const preview = connectorPreviews[key];
  if (!preview) return;
  preview.textContent = connectorContents[key] || stringifySimpleYaml(connectorYamlObjects[key]) || defaultConnectorTemplate(key);
}

function renderConnectorTab(key) {
  const enabled = Boolean(connectorSettings[key]);
  const tree = connectorTrees[key];
  const preview = connectorPreviews[key];
  const disabledNotice = connectorDisabledMessages[key];
  const downloadButton = connectorDownloadButtons[key];
  const status = connectorStatuses[key];

  if (disabledNotice) {
    disabledNotice.classList.toggle("hidden", enabled);
    disabledNotice.textContent = `${formatConnectorLabel(key)} is disabled in your configuration.`;
  }

  if (downloadButton) {
    downloadButton.disabled = !enabled;
  }

  if (tree) {
    tree.classList.toggle("hidden", !enabled);
    if (enabled) {
      const configObj = ensureConnectorObjectDefaults(key);
      renderYamlTree(tree, configObj, (path, value) => {
        setNestedValue(configObj, path, value);
        connectorContents[key] = stringifySimpleYaml(configObj);
        renderConnectorPreview(key);
      });
    }
  }

  if (preview) {
    preview.parentElement?.classList.toggle("hidden", !enabled);
    preview.textContent = enabled
      ? connectorContents[key] || stringifySimpleYaml(connectorYamlObjects[key]) || defaultConnectorTemplate(key)
      : "";
  }

  if (status) {
    if (!enabled) {
      status.className = "hint";
      status.textContent = `${formatConnectorLabel(key)} is disabled. Enable it to edit settings.`;
    } else if (!status.textContent) {
      status.className = "hint";
      status.textContent = `${formatConnectorLabel(key)} configuration is ready to edit.`;
    }
  }
}

function setActiveConnectorsTab(target) {
  const selected = target || "general";
  activeConnectorsTab = selected;
  connectorsTabButtons.forEach((button) => {
    const isActive = button?.dataset?.connectorsTab === selected;
    button?.classList.toggle("active", isActive);
  });
  connectorsTabPanels.forEach((panel) => {
    const isActive = panel?.dataset?.connectorsTabPanel === selected;
    panel?.classList.toggle("connector-tab--active", isActive);
  });

  if (CONNECTOR_KEYS.includes(selected)) {
    renderConnectorTab(selected);
  }
}

async function loadConnectorsPanel() {
  if (connectorsLoading) {
    return;
  }
  connectorsLoading = true;
  await loadConnectorsConfig();
  await Promise.all(CONNECTOR_KEYS.map((key) => loadConnectorFile(key)));
  renderConnectorsGeneral();
  CONNECTOR_KEYS.forEach(renderConnectorTab);
  setActiveConnectorsTab("general");
  connectorsLoading = false;
}

async function reloadConnectorsConfigFromSource() {
  if (connectorsOriginalContent) {
    connectorsContent = connectorsOriginalContent;
    syncConnectorsFromContent();
    renderConnectorsGeneral();
    CONNECTOR_KEYS.forEach(renderConnectorTab);
    if (connectorsStatus) {
      connectorsStatus.textContent = `Reloaded from ${connectorsFileName?.textContent || "connectors-local.yml"}`;
      connectorsStatus.className = "hint";
    }
    return;
  }

  if (connectorsStatus) {
    connectorsStatus.textContent = "Reloading connectors-local.yml...";
    connectorsStatus.className = "hint";
  }
  await loadConnectorsConfig();
  renderConnectorsGeneral();
  CONNECTOR_KEYS.forEach(renderConnectorTab);
}

async function reloadConnectorFromSource(key) {
  const status = connectorStatuses[key];
  if (connectorOriginalContents[key]) {
    connectorContents[key] = connectorOriginalContents[key];
    syncConnectorObjectFromContent(key);
    renderConnectorTab(key);
    if (status) {
      status.textContent = `Reloaded from ${connectorFileNames[key] || `${key}-local.yml`}`;
      status.className = "hint";
    }
    return;
  }

  if (status) {
    status.textContent = `Reloading ${formatConnectorLabel(key)} configuration...`;
    status.className = "hint";
  }
  await loadConnectorFile(key);
  renderConnectorTab(key);
}

function extractConnectorFlags(entries) {
  const flags = { ...connectorSettings };
  if (Array.isArray(entries)) {
    entries.forEach((entry) => {
      const key = (entry?.key || "").toString().toLowerCase();
      const value = entry?.value;
      if (key === "connectors.telegram") {
        flags.telegram = resolveBooleanFlag(value, true);
      }
      if (key === "connectors.whatsapp") {
        flags.whatsapp = resolveBooleanFlag(value, true);
      }
      if (key === "connectors.messenger") {
        flags.messenger = resolveBooleanFlag(value, true);
      }
    });
  }
  return flags;
}

async function loadConnectorsConfig() {
  if (!connectorsStatus || !connectorsPreview) {
    return;
  }

  const endpoint = buildAdminEndpoint("/admin/config/connectors");
  if (!endpoint) {
    connectorsStatus.textContent = "Set the monitoring API base URL so the Java server can be reached.";
    connectorsStatus.className = "hint error-state";
    connectorsFileName.textContent = "Unavailable";
    renderConnectorsGeneral();
    return;
  }

  connectorsStatus.textContent = "Loading connectors configuration...";
  connectorsStatus.className = "hint";
  try {
    const response = await fetch(endpoint);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const reason = payload?.reason || response.statusText || `HTTP ${response.status}`;
      throw new Error(reason);
    }
    connectorSettings = extractConnectorFlags(payload?.entries);
    connectorsContent = payload?.content || buildConnectorsYaml();
    connectorsOriginalContent = connectorsContent;
    syncConnectorsFromContent();
    connectorsFileName.textContent = payload?.fileName || "connectors-local.yml";
    const timestamp = formatTimestamp(payload?.lastModified);
    connectorsStatus.textContent = timestamp
      ? `Last updated ${timestamp}`
      : `Loaded ${connectorsFileName.textContent}`;
    connectorsStatus.className = "hint";
  } catch (error) {
    connectorsStatus.textContent = `Unable to load connectors configuration: ${error?.message || error}`;
    connectorsStatus.className = "hint error-state";
    connectorSettings = { telegram: true, whatsapp: true, messenger: true };
    connectorsContent = buildConnectorsYaml();
    connectorsOriginalContent = connectorsContent;
    syncConnectorsFromContent();
    connectorsFileName.textContent = "connectors-local.yml";
  }
}

async function loadConnectorFile(key) {
  const endpoint = buildAdminEndpoint(`/admin/config/${key}`);
  const status = connectorStatuses[key];
  const defaultContent = defaultConnectorTemplate(key);

  if (!endpoint) {
    if (status) {
      status.textContent = "Set the monitoring API base URL so the Java server can be reached.";
      status.className = "hint error-state";
    }
    connectorContents[key] = defaultContent;
    syncConnectorObjectFromContent(key);
    renderConnectorTab(key);
    return;
  }

  if (status) {
    status.textContent = `Loading ${formatConnectorLabel(key)} configuration...`;
    status.className = "hint";
  }

  try {
    const response = await fetch(endpoint);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const reason = payload?.reason || response.statusText || `HTTP ${response.status}`;
      throw new Error(reason);
    }
    connectorContents[key] = payload?.content || defaultContent;
    connectorOriginalContents[key] = connectorContents[key];
    syncConnectorObjectFromContent(key);
    connectorFileNames[key] = payload?.fileName || `${key}-local.yml`;
    const timestamp = formatTimestamp(payload?.lastModified);
    if (status) {
      status.textContent = timestamp
        ? `Loaded from ${connectorFileNames[key]} (updated ${timestamp})`
        : `Loaded from ${connectorFileNames[key]}`;
      status.className = "hint";
    }
  } catch (error) {
    if (status) {
      status.textContent = `Unable to load ${formatConnectorLabel(key)} configuration: ${error?.message || error}`;
      status.className = "hint error-state";
    }
    connectorContents[key] = defaultContent;
    connectorOriginalContents[key] = connectorContents[key];
    syncConnectorObjectFromContent(key);
    connectorFileNames[key] = `${key}-local.yml`;
  }
}

function defaultConnectorTemplate(key) {
  switch (key) {
    case "telegram":
      return "telegram:\n  bot:\n    token: YOUR_TELEGRAM_BOT_TOKEN  # Bot token issued by BotFather\n";
    case "whatsapp":
      return [
        "whatsapp:",
        "  callback-url: ${app.public-base-url}/webhook/whatsapp  # Public webhook endpoint for WhatsApp callbacks",
        "  verify-token: YOUR_WHATSAPP_VERIFY_TOKEN               # Verification token configured in Meta App settings",
        "  phone-number-id: YOUR_PHONE_NUMBER_ID                  # WhatsApp Business phone number ID",
        "  access-token: YOUR_WHATSAPP_ACCESS_TOKEN               # WhatsApp Graph API access token",
        "  ux-mode: TEST  # UX mode: BASIC (plain text), PRODUCTION (interactive cards), TEST (show both)"
      ].join("\n");
    case "messenger":
      return [
        "messenger:",
        "  callback-url: ${app.public-base-url}/messenger/webhook  # Public webhook endpoint for Facebook Messenger callbacks",
        "  verify-token: YOUR_FACEBOOK_VERIFY_TOKEN                # Verification token configured for the Facebook app",
        "  page-access-token: YOUR_FACEBOOK_PAGE_ACCESS_TOKEN      # Page access token for sending messages"
      ].join("\n");
    default:
      return `# Add settings for ${formatConnectorLabel(key)}`;
  }
}

function parseWeblinksYaml(content) {
  const parsed = parseSimpleYaml(content || "");
  if (!parsed || typeof parsed !== "object") {
    return [];
  }
  return Object.entries(parsed).map(([name, value]) => {
    const meta = typeof value === "object" && value !== null ? value : {};
    const url = meta.URL || meta.url || "";
    const authKey = Object.keys(meta || {}).find((key) => key.toLowerCase() === "authenticated-user");
    const authValue = authKey ? meta[authKey] : meta.authenticated;
    const contextKey = Object.keys(meta || {}).find((key) => key.toLowerCase() === "context");
    const contextValue = (contextKey ? meta[contextKey] : meta.context) || "noContext";
    const normalizedContext = (() => {
      const candidate = String(contextValue).toLowerCase();
      if (candidate === "account") return "account";
      if (candidate === "service") return "service";
      return "noContext";
    })();
    return {
      name,
      url,
      authenticated: resolveBooleanFlag(authValue, false),
      context: normalizedContext
    };
  });
}

function buildWeblinksYaml() {
  const root = {};
  (weblinks || []).forEach((link) => {
    if (!link?.name) return;
    root[link.name] = {
      URL: link.url || "",
      "Authenticated-User": link.authenticated ? "Y" : "N",
      Context: link.context || "noContext"
    };
  });
  return stringifySimpleYaml(root);
}

function renderWeblinkOptions(targetSelect, selectedValue) {
  if (!targetSelect) return;
  targetSelect.innerHTML = "";
  if (!weblinks.length) {
    const placeholder = new Option("No weblinks configured", "", true, true);
    placeholder.disabled = true;
    targetSelect.append(placeholder);
    targetSelect.disabled = true;
    return;
  }
  weblinks.forEach((link) => {
    targetSelect.append(new Option(link.name, link.name, false, link.name === selectedValue));
  });
  targetSelect.disabled = false;
}

function findWeblinkMeta(name) {
  const entry = (weblinks || []).find((entry) => entry.name === name) || null;
  if (entry && !entry.context) {
    entry.context = "noContext";
  }
  return entry;
}

function renderWeblinksList() {
  if (!weblinksList) return;
  weblinksList.innerHTML = "";
  if (!Array.isArray(weblinks) || !weblinks.length) {
    const empty = document.createElement("p");
    empty.className = "hint";
    empty.textContent = "No weblinks configured yet. Add one below.";
    weblinksList.append(empty);
    return;
  }

  weblinks.forEach((link, index) => {
    const row = document.createElement("div");
    row.className = "config-entry";

    const nameField = document.createElement("input");
    nameField.type = "text";
    nameField.value = link.name;
    nameField.placeholder = "URL name";
    nameField.addEventListener("input", (event) => {
      weblinks[index].name = event.target.value;
      weblinksContent = buildWeblinksYaml();
      renderWeblinksPreview();
    });

    const urlField = document.createElement("input");
    urlField.type = "url";
    urlField.value = link.url || "";
    urlField.placeholder = "https://example.com";
    urlField.addEventListener("input", (event) => {
      weblinks[index].url = event.target.value;
      weblinksContent = buildWeblinksYaml();
      renderWeblinksPreview();
    });

    const authLabel = document.createElement("label");
    authLabel.className = "checkbox";
    const authInput = document.createElement("input");
    authInput.type = "checkbox";
    authInput.checked = Boolean(link.authenticated);
    authInput.addEventListener("change", (event) => {
      weblinks[index].authenticated = event.target.checked;
      weblinksContent = buildWeblinksYaml();
      renderWeblinksPreview();
    });
    const authText = document.createElement("span");
    authText.textContent = "Authenticated user";
    authLabel.append(authInput, authText);

    const contextWrapper = document.createElement("label");
    contextWrapper.textContent = "Context";
    const contextSelect = document.createElement("select");
    const currentContext = link.context || "noContext";
    [
      { value: "noContext", label: "None" },
      { value: "account", label: "Account" },
      { value: "service", label: "Service" }
    ].forEach((option) => {
      contextSelect.append(new Option(option.label, option.value, false, option.value === currentContext));
    });
    contextSelect.addEventListener("change", (event) => {
      weblinks[index].context = event.target.value;
      weblinksContent = buildWeblinksYaml();
      renderWeblinksPreview();
    });
    contextWrapper.append(contextSelect);

    const actions = document.createElement("div");
    actions.className = "menu-item-actions";
    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.textContent = "✕";
    deleteButton.title = "Remove";
    deleteButton.addEventListener("click", () => {
      weblinks.splice(index, 1);
      weblinksContent = buildWeblinksYaml();
      renderWeblinksList();
      renderWeblinksPreview();
    });
    actions.append(deleteButton);

    row.append(nameField, urlField, authLabel, contextWrapper, actions);
    weblinksList.append(row);
  });
}

function renderWeblinksPreview() {
  if (weblinksPreview) {
    weblinksPreview.textContent = weblinksContent || buildWeblinksYaml();
  }
}

function syncWeblinksFromContent() {
  weblinks = parseWeblinksYaml(weblinksContent || "");
  if (!weblinks.length) {
    weblinks = [
      { name: "Example Dashboard", url: "https://example.com/dashboard", authenticated: true, context: "account" },
      { name: "Support Portal", url: "https://support.example.com/help", authenticated: false, context: "noContext" }
    ];
    weblinksContent = buildWeblinksYaml();
  }
  renderWeblinksList();
  renderWeblinksPreview();
  renderWeblinkOptions(menuWeblinkSelect);
  renderMenuItems();
}

async function loadWeblinksConfig() {
  if (!weblinksStatus) return;
  const endpoint = buildAdminEndpoint("/admin/config/weblinks");
  if (!endpoint) {
    weblinksStatus.textContent = "Set the monitoring API base URL so the Java server can be reached.";
    weblinksStatus.className = "hint error-state";
    weblinksFileName.textContent = "Unavailable";
    syncWeblinksFromContent();
    return;
  }
  weblinksStatus.textContent = "Loading weblinks configuration...";
  weblinksStatus.className = "hint";
  try {
    const response = await fetch(endpoint);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const reason = payload?.reason || response.statusText || `HTTP ${response.status}`;
      throw new Error(reason);
    }
    weblinksFile = payload?.fileName || "weblinks-local.yml";
    weblinksFileName.textContent = weblinksFile;
    weblinksContent = payload?.content || "";
    weblinksOriginalContent = weblinksContent;
    const timestamp = formatTimestamp(payload?.lastModified);
    weblinksStatus.textContent = timestamp
      ? `Last updated ${timestamp}`
      : `Loaded ${weblinksFile}`;
    weblinksStatus.className = "hint";
  } catch (error) {
    weblinksStatus.textContent = `Unable to load weblinks configuration: ${error?.message || error}`;
    weblinksStatus.className = "hint error-state";
    weblinksFileName.textContent = "weblinks-local.yml";
    weblinksContent = "";
  }
  syncWeblinksFromContent();
}

function reloadWeblinksFromSource() {
  loadWeblinksConfig();
}

function downloadWeblinksConfig() {
  const content = weblinksContent || buildWeblinksYaml();
  downloadYamlFile(content, weblinksFile || "weblinks-local.yml");
}

function toggleWeblinkForm(show) {
  if (!weblinksAddForm) return;
  weblinksAddForm.classList.toggle("hidden", !show);
  if (!show) {
    weblinksAddForm.reset();
  }
}

function handleAddWeblink(event) {
  event.preventDefault();
  const name = weblinkNameInput?.value?.trim();
  const url = weblinkUrlInput?.value?.trim();
  const authenticated = Boolean(weblinkAuthInput?.checked);
  const context = weblinkContextInput?.value || "noContext";
  if (!name || !url) {
    alert("URL name and URL are required.");
    return;
  }
  weblinks.push({ name, url, authenticated, context });
  weblinksContent = buildWeblinksYaml();
  syncWeblinksFromContent();
  toggleWeblinkForm(false);
}

function downloadYamlFile(content, filename) {
  const blob = new Blob([content], { type: "text/yaml" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function downloadConnectorConfig(key) {
  const content = connectorContents[key] || defaultConnectorTemplate(key);
  downloadYamlFile(content, connectorFileNames[key] || `${key}-local.yml`);
}

function formatQueryParams(params) {
  if (!params || typeof params !== "object") {
    return "";
  }
  return Object.entries(params)
    .map(([key, value]) => `${key}=${value ?? ""}`)
    .join("&");
}

function parseQueryParams(input) {
  return parseQueryParamString(input);
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
  notificationResult.classList.add("hidden");

  const endpoint = buildNotificationEndpoint("/notifications");
  if (!endpoint) {
    notificationResult.textContent = "Set the notifications API base URL so the server can be reached.";
    notificationResult.classList.add("error");
    notificationResult.classList.remove("hidden");
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
    notificationResult.classList.remove("hidden");
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
      notificationResult.classList.remove("hidden");
      return;
    }
    notificationResult.textContent = body?.status === "sent"
      ? `Message sent via ${body.channel || payload.channel} to ${body.chatId || payload.chatId}.`
      : "Message sent.";
    notificationResult.classList.remove("info");
    notificationResult.classList.add("success");
    notificationResult.classList.remove("hidden");
  } catch (error) {
    notificationResult.textContent = error?.message || "Unexpected error while sending notification.";
    notificationResult.classList.remove("info");
    notificationResult.classList.add("error");
    notificationResult.classList.remove("hidden");
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
if (navConnectors) {
  navConnectors.addEventListener("click", () => setActiveApp("connectors"));
}
if (navWeblinks) {
  navWeblinks.addEventListener("click", () => {
    setActiveApp("weblinks");
    loadWeblinksConfig();
  });
}
if (navApiRegistration) {
  navApiRegistration.addEventListener("click", () => setActiveApp("api-registry"));
}
if (navServiceBuilder) {
  navServiceBuilder.addEventListener("click", () => setActiveApp("service-builder"));
}

[menuContextToggle, serviceContextToggle, accountContextToggle].forEach((toggle) => {
  if (toggle) {
    toggle.addEventListener("change", syncContextInputStates);
  }
});

connectorsTabButtons.forEach((button) => {
  button.addEventListener("click", () => setActiveConnectorsTab(button?.dataset?.connectorsTab));
});

if (connectorsReloadButton) {
  connectorsReloadButton.addEventListener("click", reloadConnectorsConfigFromSource);
}

Object.entries(connectorReloadButtons).forEach(([key, button]) => {
  if (!button) return;
  button.addEventListener("click", () => reloadConnectorFromSource(key));
});

Object.entries(connectorToggles).forEach(([key, input]) => {
  if (!input) return;
  input.addEventListener("change", () => {
    connectorSettings[key] = input.checked;
    setNestedValue(connectorsYamlObject, ["connectors", key], input.checked);
    connectorsContent = buildConnectorsYaml();
    applyConnectorSettingsFromYaml();
    renderConnectorsGeneral();
    renderConnectorTab(key);
  });
});

Object.entries(connectorDownloadButtons).forEach(([key, button]) => {
  if (!button) return;
  button.addEventListener("click", () => downloadConnectorConfig(key));
});

if (connectorsDownloadButton) {
  connectorsDownloadButton.addEventListener("click", () => {
    const content = connectorsContent || buildConnectorsYaml();
    downloadYamlFile(content, "connectors-local.yml");
  });
}

if (addApiButton) {
  addApiButton.addEventListener("click", () => toggleApiForm(true));
}
if (cancelApiButton) {
  cancelApiButton.addEventListener("click", () => toggleApiForm(false));
}
if (apiForm) {
  apiForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = (apiNameInput.value || "").trim();
    const url = (apiUrlInput.value || "").trim();
    if (!name || !url) {
      alert("Provide both an API name and URL.");
      return;
    }
    const slug = slugify(name);
    const previousName = editingApiName;
    const renamed = previousName && previousName !== slug;
    const filteredApis = apiRegistryEntries.filter((api) => api.name !== slug && api.name !== previousName);
    apiRegistryEntries = [...filteredApis, { name: slug, url }];
    if (renamed) {
      serviceBuilderEntries = serviceBuilderEntries.map((svc) =>
        svc.apiName === previousName ? { ...svc, apiName: slug } : svc
      );
    }
    apiRegistryStatus.textContent = `Saved API ${slug}.`;
    apiRegistryStatus.className = "hint";
    hydrateServiceApiOptions();
    renderApiList();
    toggleApiForm(false);
  });
}
if (downloadApiListButton) {
  downloadApiListButton.addEventListener("click", async () => {
    const endpoint = buildAdminEndpoint("/admin/apis/export");
    if (!endpoint) {
      apiRegistryStatus.textContent = "Set the monitoring API base URL to download APIs.";
      apiRegistryStatus.className = "hint error-state";
      return;
    }
    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(apiRegistryEntries)
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "API-list-local.yml";
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      apiRegistryStatus.textContent = "API-list-local.yml downloaded.";
      apiRegistryStatus.className = "hint";
    } catch (error) {
      apiRegistryStatus.textContent = `Unable to download API list: ${error.message}`;
      apiRegistryStatus.className = "hint error-state";
    }
  });
}

if (saveOverlayButton) {
  saveOverlayButton.addEventListener("click", handleSaveClick);
}

if (addServiceButton) {
  addServiceButton.addEventListener("click", () => {
    editingServiceIndex = null;
    toggleServiceForm(true, createBlankServiceDefinition());
    serviceBuilderStatus.textContent = "Fill in the new service, then use Publish to save.";
    serviceBuilderStatus.className = "hint";
  });
}
if (serviceForm) {
  serviceForm.classList.add("hidden");
  serviceForm.addEventListener("submit", handleServiceFormSubmit);
}
if (addOutputFieldButton) {
  addOutputFieldButton.addEventListener("click", () => addOutputFieldRow());
}
if (cancelServiceButton) {
  cancelServiceButton.addEventListener("click", () => {
    editingServiceIndex = null;
    toggleServiceForm(false);
  });
}
if (downloadServicesButton) {
  downloadServicesButton.addEventListener("click", async () => {
    const endpoint = buildAdminEndpoint("/admin/services/export");
    if (!endpoint) {
      serviceBuilderStatus.textContent = "Set the monitoring API base URL to download services.";
      serviceBuilderStatus.className = "hint error-state";
      return;
    }
    try {
      const payload = serviceBuilderEntries.map((svc, index) => prepareServiceForSave(svc, index));
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "services-local.yml";
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      serviceBuilderStatus.textContent = "services-local.yml downloaded.";
      serviceBuilderStatus.className = "hint";
    } catch (error) {
      serviceBuilderStatus.textContent = `Unable to download services file: ${error.message}`;
      serviceBuilderStatus.className = "hint error-state";
    }
  });
}

if (weblinksReloadButton) {
  weblinksReloadButton.addEventListener("click", reloadWeblinksFromSource);
}

if (weblinksDownloadButton) {
  weblinksDownloadButton.addEventListener("click", downloadWeblinksConfig);
}

if (addWeblinkButton) {
  addWeblinkButton.addEventListener("click", () => toggleWeblinkForm(true));
}

if (cancelWeblinkButton) {
  cancelWeblinkButton.addEventListener("click", () => toggleWeblinkForm(false));
}

if (weblinksAddForm) {
  weblinksAddForm.addEventListener("submit", handleAddWeblink);
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

setActiveConnectorsTab("general");
toggleAddFormFields();
bootstrapMenuConfig();
initMonitoring();
restoreNotificationApiBase();
loadWeblinksConfig();
loadApiRegistry();
loadServiceBuilder();
setActiveApp("admin");


function hideNotificationResult() {
  notificationResult.classList.add("hidden");
  notificationResult.classList.remove("success", "error", "info");
}

document.getElementById("notificationChannel").addEventListener("change", hideNotificationResult);
document.getElementById("notificationChatId").addEventListener("input", hideNotificationResult);
document.getElementById("notificationMessage").addEventListener("input", hideNotificationResult);
document.getElementById("notificationChannel").addEventListener("click", hideNotificationResult);
document.getElementById("notificationChatId").addEventListener("click", hideNotificationResult);
document.getElementById("notificationMessage").addEventListener("click", hideNotificationResult);