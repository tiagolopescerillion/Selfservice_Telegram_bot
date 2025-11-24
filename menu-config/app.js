const ROOT_MENU_ID = "home";

const FUNCTION_OPTIONS = [
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

const functionDictionary = FUNCTION_OPTIONS.reduce((acc, option) => {
  acc[option.id] = option;
  return acc;
}, {});

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
const menuConfigurationPanel = document.getElementById("menuConfigurationPanel");
const operationsMonitoringPanel = document.getElementById("operationsMonitoringPanel");
const liveSessionsContainer = document.getElementById("liveSessions");
const sessionHistoryContainer = document.getElementById("sessionHistory");
const monitoringApiBaseInput = document.getElementById("monitoringApiBase");
const monitoringApiBaseMeta = document.querySelector("meta[name='operations-api-base']");

initFunctionSelect(menuFunctionSelect);

let menusById = new Map();
let menuOrder = [];
let selectedMenuId = ROOT_MENU_ID;
let menuIdCounter = 0;
let itemIdCounter = 0;
let liveSessions = [];
let sessionHistory = [];
let monitoringError = null;
const MONITORING_REFRESH_MS = 5000;
const MONITORING_API_STORAGE_KEY = "monitoringApiBase";
let monitoringIntervalId = null;
let monitoringEndpointCache = "";

function initFunctionSelect(selectEl) {
  selectEl.innerHTML = "";
  FUNCTION_OPTIONS.forEach((option) => {
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
  if (!menusById.has(ROOT_MENU_ID)) {
    menusById.set(ROOT_MENU_ID, { id: ROOT_MENU_ID, name: "Home", parentId: null, items: [] });
    menuOrder.unshift(ROOT_MENU_ID);
  }
}

function resetToDefault() {
  loadState(structuredClone(DEFAULT_STRUCTURE));
}

function loadState(menus) {
  menusById = new Map();
  menuOrder = [];
  itemIdCounter = 0;
  menuIdCounter = menus.length;

  menus.forEach((menu) => {
    let resolvedId = menu.id || slugify(menu.name || "") || null;
    if (!resolvedId || menusById.has(resolvedId)) {
      resolvedId = nextMenuId(menu.name || "menu");
    }
    const normalized = {
      id: resolvedId,
      name: menu.name?.trim() || (menu.id === ROOT_MENU_ID ? "Home" : menu.id || "Menu"),
      parentId: menu.parentId ?? null,
      items: (menu.items || []).map((item) => ({
        id: item.id || nextItemId(),
        label: item.label?.trim() || (item.function ? functionDictionary[item.function]?.label || item.function : "Sub-menu"),
        type: item.type === "submenu" ? "submenu" : "function",
        function: item.type === "submenu" ? null : item.function,
        useTranslation: Boolean(item.useTranslation),
        submenuId: item.type === "submenu" ? item.submenuId : null
      }))
    };
    menusById.set(normalized.id, normalized);
    menuOrder.push(normalized.id);
  });

  ensureRootMenu();
  selectedMenuId = menusById.has(selectedMenuId) ? selectedMenuId : ROOT_MENU_ID;
  renderAll();
}

function normalizeIncomingConfig(raw) {
  if (raw && Array.isArray(raw.menus) && raw.menus.length) {
    const seen = new Set();
    const baseMenus = raw.menus.map((menu, index) => {
      if (menu.id === ROOT_MENU_ID) {
        seen.add(ROOT_MENU_ID);
        return { id: ROOT_MENU_ID, name: menu.name?.trim() || "Home", parentId: null, items: [] };
      }
      const base = menu.id || slugify(menu.name || "") || `menu-${index + 1}`;
      let candidate = base;
      let suffix = 1;
      while (seen.has(candidate) || candidate === ROOT_MENU_ID) {
        candidate = `${base}-${suffix++}`;
      }
      seen.add(candidate);
      return {
        id: candidate,
        name: menu.name?.trim() || menu.id || `Menu ${index + 1}`,
        parentId: null,
        items: []
      };
    });
    const menuMap = new Map(baseMenus.map((menu) => [menu.id, menu]));
    if (!menuMap.has(ROOT_MENU_ID)) {
      const root = { id: ROOT_MENU_ID, name: "Home", parentId: null, items: [] };
      menuMap.set(ROOT_MENU_ID, root);
      baseMenus.unshift(root);
    }
    raw.menus.forEach((menu) => {
      const target = menuMap.get(menu.id) || menuMap.get(ROOT_MENU_ID);
      target.items = [];
      const items = Array.isArray(menu.items) ? menu.items : [];
      items.forEach((item) => {
        if (item?.submenuId && menuMap.has(item.submenuId)) {
          const submenu = menuMap.get(item.submenuId);
          submenu.parentId = target.id;
          target.items.push({
            label: item.label ?? submenu.name,
            type: "submenu",
            submenuId: submenu.id
          });
          return;
        }
        if (!functionDictionary[item.function]) {
          return;
        }
        target.items.push({
          label: item.label ?? functionDictionary[item.function].label,
          type: "function",
          function: item.function,
          useTranslation: Boolean(item.translationKey)
        });
      });
    });
    return Array.from(menuMap.values());
  }

  if (raw && Array.isArray(raw.menu) && raw.menu.length) {
    return [
      {
        id: ROOT_MENU_ID,
        name: "Home",
        parentId: null,
        items: raw.menu
          .filter((item) => functionDictionary[item.function])
          .map((item) => ({
            label: item.label ?? functionDictionary[item.function].label,
            type: "function",
            function: item.function,
            useTranslation: Boolean(item.translationKey)
          }))
      }
    ];
  }

  return structuredClone(DEFAULT_STRUCTURE);
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
  if (!menuOrder.includes(ROOT_MENU_ID)) {
    menuOrder.unshift(ROOT_MENU_ID);
  }

  menuOrder.forEach((id) => {
    const menu = menusById.get(id);
    if (!menu) return;
    menuSelect.append(new Option(menu.name, id, false, id === selectedMenuId));
    parentMenuSelect.append(new Option(menu.name, id));
  });

  if (!menusById.has(selectedMenuId)) {
    selectedMenuId = ROOT_MENU_ID;
  }

  menuSelect.value = selectedMenuId;
  parentMenuSelect.value = selectedMenuId;
  menuNameEditor.value = menusById.get(selectedMenuId)?.name ?? "";
  menuNameEditor.disabled = selectedMenuId === ROOT_MENU_ID;
  deleteMenuButton.disabled = selectedMenuId === ROOT_MENU_ID;

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
    .filter((menu) => menu.id !== ROOT_MENU_ID)
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
  if (submenuId === ROOT_MENU_ID) {
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
  const parentId = parentMenuSelect.value || ROOT_MENU_ID;
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
  const timestamp = new Date().toISOString();
  return {
    version: 1,
    generatedAt: timestamp,
    menus: menuOrder
      .filter((id) => menusById.has(id))
      .map((id) => {
        const menu = menusById.get(id);
        return {
          id: menu.id,
          name: menu.name,
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
      })
  };
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
  if (menuId === ROOT_MENU_ID) {
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
  selectedMenuId = ROOT_MENU_ID;
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
  const rootMenu = menusById.get(ROOT_MENU_ID);
  if (!rootMenu || rootMenu.items.length === 0) {
    alert("Please configure at least one menu item before downloading.");
    return;
  }
  const config = buildConfig();
  const blob = new Blob([JSON.stringify(config, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "business-menu.override.json";
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
      const normalized = normalizeIncomingConfig(parsed);
      loadState(normalized);
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
  const parentId = parentMenuSelect.value || ROOT_MENU_ID;
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
  const showMenuConfig = target !== "operations";
  menuConfigurationPanel.classList.toggle("hidden", !showMenuConfig);
  operationsMonitoringPanel.classList.toggle("hidden", showMenuConfig);
  navMenuConfig.classList.toggle("active", showMenuConfig);
  navOperationsMonitoring.classList.toggle("active", !showMenuConfig);
  if (!showMenuConfig) {
    refreshMonitoringData();
  }
}

function getStoredMonitoringApiBase() {
  return localStorage.getItem(MONITORING_API_STORAGE_KEY)?.trim() || "";
}

function restoreMonitoringApiBase() {
  const stored = getStoredMonitoringApiBase();
  if (stored && monitoringApiBaseInput) {
    monitoringApiBaseInput.value = stored;
    return;
  }

  const metaValue = monitoringApiBaseMeta?.content?.trim();
  if (metaValue && monitoringApiBaseInput && !monitoringApiBaseInput.value) {
    monitoringApiBaseInput.placeholder = metaValue;
  }
}

function getConfiguredMonitoringApiBase() {
  const manual = monitoringApiBaseInput?.value?.trim();
  if (manual) {
    return manual;
  }

  const stored = getStoredMonitoringApiBase();
  if (stored) {
    return stored;
  }

  const metaValue = monitoringApiBaseMeta?.content?.trim();
  if (metaValue) {
    return metaValue;
  }

  const origin = window.location?.origin;
  return origin && origin !== "null" ? origin : "";
}

function persistMonitoringApiBase(value) {
  if (value) {
    localStorage.setItem(MONITORING_API_STORAGE_KEY, value);
  } else {
    localStorage.removeItem(MONITORING_API_STORAGE_KEY);
  }
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
  return {
    channel: raw?.channel || "Unknown",
    chatId: sessionId,
    loggedIn: Boolean(raw?.loggedIn),
    username: raw?.username || "",
    startedAt,
    lastSeen: raw?.lastSeen ? new Date(raw.lastSeen) : startedAt
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

    row.append(channel, chat, loggedIn, startDate, startTime);
    container.append(row);
  });
}

function formatDate(date) {
  return new Date(date).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "2-digit" });
}

function formatTime(date) {
  return new Date(date).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function handleMonitoringApiBaseChanged() {
  const value = monitoringApiBaseInput?.value?.trim() || "";
  persistMonitoringApiBase(value);
  monitoringError = null;
  refreshMonitoringData();
}

function initMonitoring() {
  restoreMonitoringApiBase();
  refreshMonitoringData();
  if (monitoringIntervalId === null) {
    monitoringIntervalId = setInterval(refreshMonitoringData, MONITORING_REFRESH_MS);
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
if (monitoringApiBaseInput) {
  monitoringApiBaseInput.addEventListener("change", handleMonitoringApiBaseChanged);
  monitoringApiBaseInput.addEventListener("blur", handleMonitoringApiBaseChanged);
}

toggleAddFormFields();
resetToDefault();
initMonitoring();
setActiveApp("menu");
