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

const DEFAULT_MENU = [
  { label: "Hello World", function: "HELLO_WORLD" },
  { label: "Hello Cerillion", function: "HELLO_CERILLION" },
  { label: "Trouble Ticket", function: "VIEW_TROUBLE_TICKET" },
  { label: "Select a Service", function: "SELECT_SERVICE" },
  { label: "My Issues", function: "MY_ISSUES" }
];

const functionDictionary = FUNCTION_OPTIONS.reduce((acc, option) => {
  acc[option.id] = option;
  return acc;
}, {});

let menuItems = structuredClone(DEFAULT_MENU);

const menuContainer = document.getElementById("menuContainer");
const addItemForm = document.getElementById("addItemForm");
const nameInput = document.getElementById("menuNameInput");
const functionSelect = document.getElementById("menuFunctionSelect");
const resetButton = document.getElementById("resetButton");
const downloadButton = document.getElementById("downloadButton");
const preview = document.getElementById("preview");
const importInput = document.getElementById("importInput");

function initFunctionSelect(selectEl) {
  selectEl.innerHTML = "";
  FUNCTION_OPTIONS.forEach((option) => {
    const opt = document.createElement("option");
    opt.value = option.id;
    opt.textContent = `${option.label}`;
    opt.title = option.description;
    selectEl.append(opt);
  });
}

initFunctionSelect(functionSelect);

function renderMenu() {
  menuContainer.innerHTML = "";

  if (!menuItems.length) {
    const empty = document.createElement("p");
    empty.textContent = "No menu items yet. Use the form below to add one.";
    menuContainer.append(empty);
    updatePreview();
    return;
  }

  menuItems.forEach((item, index) => {
    const row = document.createElement("div");
    row.className = "menu-item";

    const labelWrapper = document.createElement("label");
    labelWrapper.textContent = "Label";
    const labelInput = document.createElement("input");
    labelInput.type = "text";
    labelInput.value = item.label;
    labelInput.required = true;
    labelInput.addEventListener("input", (event) => {
      menuItems[index].label = event.target.value;
      updatePreview();
    });
    labelWrapper.append(labelInput);

    const functionWrapper = document.createElement("label");
    functionWrapper.textContent = "Function";
    const functionDropdown = document.createElement("select");
    initFunctionSelect(functionDropdown);
    functionDropdown.value = item.function;
    functionDropdown.addEventListener("change", (event) => {
      menuItems[index].function = event.target.value;
      updatePreview();
    });
    functionWrapper.append(functionDropdown);

    const actions = document.createElement("div");
    actions.className = "menu-item-actions";

    const upButton = document.createElement("button");
    upButton.type = "button";
    upButton.textContent = "↑";
    upButton.title = "Move up";
    upButton.disabled = index === 0;
    upButton.addEventListener("click", () => moveItem(index, index - 1));

    const downButton = document.createElement("button");
    downButton.type = "button";
    downButton.textContent = "↓";
    downButton.title = "Move down";
    downButton.disabled = index === menuItems.length - 1;
    downButton.addEventListener("click", () => moveItem(index, index + 1));

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.textContent = "✕";
    deleteButton.title = "Remove";
    deleteButton.addEventListener("click", () => deleteItem(index));

    actions.append(upButton, downButton, deleteButton);
    row.append(labelWrapper, functionWrapper, actions);
    menuContainer.append(row);
  });

  updatePreview();
}

function moveItem(from, to) {
  if (to < 0 || to >= menuItems.length) {
    return;
  }
  const updated = [...menuItems];
  const [moved] = updated.splice(from, 1);
  updated.splice(to, 0, moved);
  menuItems = updated;
  renderMenu();
}

function deleteItem(index) {
  menuItems.splice(index, 1);
  renderMenu();
}

addItemForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const label = nameInput.value.trim();
  const func = functionSelect.value;
  if (!label || !func) {
    return;
  }
  menuItems.push({ label, function: func });
  nameInput.value = "";
  functionSelect.selectedIndex = 0;
  renderMenu();
});

resetButton.addEventListener("click", () => {
  if (confirm("Reset the menu to the default configuration?")) {
    menuItems = structuredClone(DEFAULT_MENU);
    renderMenu();
  }
});

function buildConfig() {
  const timestamp = new Date().toISOString();
  return {
    version: 1,
    generatedAt: timestamp,
    menu: menuItems.map((item, index) => {
      const meta = functionDictionary[item.function];
      return {
        order: index + 1,
        label: item.label,
        function: item.function,
        callbackData: meta?.callbackData ?? item.function,
        translationKey: meta?.translationKey ?? null
      };
    })
  };
}

function updatePreview() {
  const config = buildConfig();
  preview.textContent = JSON.stringify(config, null, 2);
}

function downloadConfig() {
  if (!menuItems.length) {
    alert("Please add at least one menu item before downloading.");
    return;
  }
  const config = buildConfig();
  const blob = new Blob([JSON.stringify(config, null, 2)], {
    type: "application/json"
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  const dateSuffix = new Date().toISOString().replace(/[:.]/g, "-");
  anchor.href = url;
  anchor.download = `business-menu-${dateSuffix}.json`;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

downloadButton.addEventListener("click", downloadConfig);

importInput.addEventListener("change", (event) => {
  const [file] = event.target.files;
  if (!file) {
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const parsed = JSON.parse(reader.result);
      if (!Array.isArray(parsed.menu)) {
        throw new Error("Invalid configuration file (missing menu array).");
      }
      const invalidItems = parsed.menu.filter(
        (item) => !functionDictionary[item.function]
      );
      if (invalidItems.length) {
        alert(
          `Unknown functions in file: ${invalidItems
            .map((item) => item.function)
            .join(", ")}. They will be ignored.`
        );
      }
      const mapped = parsed.menu
        .filter((item) => functionDictionary[item.function])
        .map((item) => ({
          label: item.label ?? functionDictionary[item.function].label,
          function: item.function
        }));
      if (!mapped.length) {
        throw new Error("The configuration file did not contain valid menu entries.");
      }
      menuItems = mapped;
      renderMenu();
    } catch (error) {
      alert(`Unable to import configuration: ${error.message}`);
    } finally {
      event.target.value = "";
    }
  };
  reader.readAsText(file);
});

renderMenu();
updatePreview();
