"use strict";
const form = document.querySelector("#point-form"),
    errors = document.querySelector("#errors"),
    tbody = document.querySelector("#results-body"),
    plot = document.querySelector("#plot");
let x = null,
    results = [],
    currentR = 3,
    busy = false;
const rInputs = [...document.querySelectorAll("[name=r]")];

function draw() {
    const s = 140 / currentR;
    plot.innerHTML = `<g fill="#3498ff"><path d="M70 210H210V280H70Z M140 210L210 140V210Z M210 210H280A70 70 0 0 1 210 280Z"/></g><g fill="none" stroke="#20242b"><path d="M20 210H400l-8 -4m8 4l-8 4M210 400V20l-4 8m4 -8l4 8"/></g><g font-size="14"><text x="400" y="200">x</text><text x="220" y="20">y</text></g>`;
    const ns = "http://www.w3.org/2000/svg";
    for (const [v, label] of [
        [-1, "−R"],
        [-0.5, "−R/2"],
        [0.5, "R/2"],
        [1, "R"],
    ]) {
        for (const [px, py] of [
            [210 + v * 140, 200],
            [220, 215 - v * 140],
        ]) {
            const t = document.createElementNS(ns, "text");
            t.setAttribute("x", px);
            t.setAttribute("y", py);
            t.setAttribute("font-size", 13);
            t.textContent = label;
            plot.append(t);
        }
    }
    for (const item of results.filter((i) => i.r === currentR)) {
        const c = document.createElementNS(ns, "circle");
        c.setAttribute("cx", 210 + item.x * s);
        c.setAttribute("cy", 210 - item.y * s);
        c.setAttribute("r", 4);
        c.setAttribute("fill", item.hit ? "#17823b" : "#c21d40");
        plot.append(c);
    }
    document.querySelector("#plot-caption").textContent =
        `Область при R = ${currentR}. Границы включены.`;
}

function render() {
    tbody.replaceChildren();
    for (const item of [...results].reverse()) {
        const tr = document.createElement("tr");
        for (const value of [
            item.x,
            item.y,
            item.r,
            item.hit ? "Попадание" : "Промах",
            new Date(item.timestamp).toLocaleString("ru-RU"),
            item.elapsedMs.toFixed(3),
        ]) {
            const td = document.createElement("td");
            td.textContent = value;
            tr.append(td);
        }
        tr.children[3].dataset.hit = item.hit;
        tbody.append(tr);
    }
    if (!results.length) {
        const tr = tbody.insertRow();
        const td = tr.insertCell();
        td.colSpan = 6;
        td.textContent = "Проверок пока нет";
    }
    draw();
}

async function request(params) {
    if (busy) {
        return;
    }
    busy = true;
    document.querySelectorAll("button").forEach((b) => (b.disabled = true));
    errors.textContent = "";
    try {
        const data = await $.ajax({
            url: form.action,
            type: "POST",
            data: params.toString(),
            contentType: "application/x-www-form-urlencoded; charset=UTF-8",
            dataType: "json",
        });
        results = data.results;
        render();
    } catch (e) {
        errors.textContent =
            e.responseJSON?.error || e.message || "Не удалось связаться с сервером";
    } finally {
        busy = false;
        document.querySelectorAll("button").forEach((b) => (b.disabled = false));
    }
}
document.querySelectorAll("[data-x]").forEach((b) =>
    b.addEventListener("click", () => {
        x = b.dataset.x;
        document
            .querySelectorAll("[data-x]")
            .forEach((el) => el.setAttribute("aria-pressed", String(el === b)));
    }),
);
rInputs.forEach((i) =>
    i.addEventListener("change", () => {
        currentR = Number(i.checked ? i.value : rInputs.find((r) => r.checked)?.value || 3);
        draw();
    }),
);
form.addEventListener("submit", (e) => {
    e.preventDefault();
    const y = document.querySelector("#y-value").value.trim().replace(",", ".");
    const radii = rInputs.filter((i) => i.checked);
    if (
        x === null ||
        !/^\-?\d+(\.\d+)?$/.test(y) ||
        y.length > 64 ||
        !(Number(y) > -3 && Number(y) < 3) ||
        !radii.length
    ) {
        errors.textContent = "Выберите X, введите −3 < Y < 3 и выберите хотя бы один R.";
        return;
    }
    const p = new URLSearchParams({ action: "check", x, y });
    radii.forEach((r) => p.append("r", r.value));
    request(p);
});
document
    .querySelector("#clear-results")
    .addEventListener("click", () => request(new URLSearchParams({ action: "clear" })));
render();
request(new URLSearchParams({ action: "history" }));
