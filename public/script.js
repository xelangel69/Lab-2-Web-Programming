"use strict";

const STORAGE_KEY = "areaCheckResults.v2";

const form = document.getElementById("point-form");
const xInput = document.getElementById("x-value");
const yInput = document.getElementById("y-value");
const rInputs = document.querySelectorAll('input[name="r"]');
const errors = document.getElementById("errors");
const resultsBody = document.getElementById("results-body");
const clearButton = document.getElementById("clear-results");
const canvas = document.getElementById("plot");


function parseDecimal(value) {
    const text = String(value);
    const negative = text.startsWith("-");
    const unsigned = negative ? text.slice(1) : text;
    const parts = unsigned.split(".");
    const fraction = parts[1] || "";
    const numerator = BigInt(parts[0] + fraction);
    return {
        numerator: negative ? -numerator : numerator,
        denominator: 10n ** BigInt(fraction.length)
    };
}

let results = loadResults();
let currentR = 3;

function loadResults() {
    let saved;
    try { saved = localStorage.getItem(STORAGE_KEY); } catch { return []; }

    if (saved === null) {
        return [];
    }

    try {
        const data = JSON.parse(saved);
        return Array.isArray(data) ? data.filter(item =>
            item !== null && typeof item === 'object'
            && Number.isInteger(item.x) && item.x >= -4 && item.x <= 4
            && typeof item.y === 'string' && item.y.length <= 100
            && /^-?\d+(\.\d+)?$/.test(item.y)
            && Number.isFinite(Number(item.y)) && Math.abs(Number(item.y)) <= 5
            && Number.isInteger(item.r) && item.r >= 1 && item.r <= 5
            && typeof item.hit === 'boolean'
            && typeof item.timestamp === 'string' && Number.isFinite(Date.parse(item.timestamp))
            && typeof item.durationMs === 'number' && Number.isFinite(item.durationMs) && item.durationMs >= 0
        ) : [];
    } catch (error) {
        return [];
    }
}

function saveResults() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(results));
}

function getSelectedR() {
    const selected = [];

    rInputs.forEach(function (input) {
        if (input.checked) {
            selected.push(Number(input.value));
        }
    });

    return selected;
}

function validateForm() {
    const messages = [];
    const x = Number(xInput.value);
    const yText = yInput.value.trim().replace(",", ".");
    const radii = getSelectedR();

    if (xInput.value === "") {
        messages.push("выберите X");
    } else if (!Number.isInteger(x) || x < -4 || x > 4) {
        messages.push("X должен быть целым числом от -4 до 4");
    }

    if (yText.length > 100) {
        messages.push("Y должен содержать не более 100 символов");
    } else if (yText === "" || !/^-?\d+(\.\d+)?$/.test(yText)) {
        messages.push("Y должен быть числом");
    } else {
        const y = parseDecimal(yText);
        if (y.numerator < -5n * y.denominator || y.numerator > 5n * y.denominator) {
            messages.push("Y должен быть от -5 до 5");
        }
    }

    if (radii.length === 0) {
        messages.push("выберите хотя бы один радиус R");
    } else if (radii.some(function (r) {
        return !Number.isInteger(r) || r < 1 || r > 5;
    })) {
        messages.push("R должен быть целым числом от 1 до 5");
    }

    errors.textContent = messages.join("; ");

    if (messages.length > 0) {
        return null;
    }

    return { x: x, y: yText, radii: radii };
}

function addCell(row, text, hit) {
    const cell = document.createElement("td");
    cell.textContent = text;

    if (hit !== undefined) {
        cell.setAttribute("data-hit", hit);
    }

    row.appendChild(cell);
}

function showResults() {
    resultsBody.innerHTML = "";

    if (results.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.textContent = "Проверок пока нет";
        cell.colSpan = 6;
        cell.className = "empty-row";
        row.appendChild(cell);
        resultsBody.appendChild(row);
        return;
    }

    for (let i = results.length - 1; i >= 0; i--) {
        const item = results[i];
        const row = document.createElement("tr");

        addCell(row, item.x);
        addCell(row, String(item.y).replace(".", ","));
        addCell(row, item.r);
        addCell(row, item.hit ? "Попадание" : "Промах", item.hit);
        addCell(row, new Date(item.timestamp).toLocaleString("ru-RU"));
        addCell(row, Number(item.durationMs).toFixed(3));

        resultsBody.appendChild(row);
    }
}

function drawPlot(r) {
    currentR = r;
    const ns = 'http://www.w3.org/2000/svg';
    canvas.replaceChildren();
    function shape(tag, attrs, text) {
        const el = document.createElementNS(ns, tag);
        Object.entries(attrs).forEach(([key,value]) => el.setAttribute(key,value));
        if (text) el.textContent = text;
        canvas.append(el);
    }
    shape('path', {d:'M140 210V70H210V210Z M70 210H210V280Z M210 210H280A70 70 0 0 1 210 280Z',fill:'#69a8df'});
    shape('path', {d:'M20 210H400L392 206M400 210L392 214M210 400V20L206 28M210 20L214 28',fill:'none',stroke:'#20242b'});
    [-140,-70,70,140].forEach(v => {
        shape('path',{d:`M${210+v} 206v8M206 ${210-v}h8`,stroke:'#20242b'});
        const label = (v<0?'-':'') + (Math.abs(v)===70?'R/2':'R');
        shape('text',{x:202+v,y:198,'font-size':12},label);
        shape('text',{x:220,y:214-v,'font-size':12},label);
    });
    shape('text',{x:400,y:195},'x'); shape('text',{x:220,y:20},'y');
    results.filter(item=>Number(item.r)===r).forEach(item=>shape('circle',{cx:210+Number(item.x)*140/r,cy:210-Number(item.y)*140/r,r:5,fill:item.hit?'#17823b':'#d20f40',stroke:'white'}));
}

rInputs.forEach(function (input) {
    input.addEventListener("change", function () {
        const selectedR = getSelectedR();

        if (input.checked) {
            drawPlot(Number(input.value));
        } else if (selectedR.length > 0) {
            drawPlot(selectedR[selectedR.length - 1]);
        } else {
            drawPlot(3);
        }

        errors.textContent = "";
    });
});

xInput.addEventListener("change", function () {
    errors.textContent = "";
});

yInput.addEventListener("input", function () {
    errors.textContent = "";
});

form.addEventListener("submit", async function (event) {
    event.preventDefault();
    const values = validateForm();
    if (!values) return;
    const button = form.querySelector('button[type="submit"]');
    if (button.disabled) return;
    button.disabled = true;
    errors.textContent = 'Обработка…';
    try {
        const response = await fetch('/api/check', {
            method: 'POST',
            headers: {'Content-Type':'application/json'},
            body: JSON.stringify(values.radii.map(r=>({x:values.x,y:values.y,r}))),
            signal: AbortSignal.timeout(15000)
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || 'Ошибка сервера');
        results.push(...data.results.map(item=>({...item,durationMs:data.durationMs})));
        showResults();
        drawPlot(values.radii[values.radii.length-1]);
        errors.textContent = '';
        try { saveResults(); } catch { errors.textContent = 'Результат получен, но браузер не смог сохранить историю.'; }
    } catch (error) {
        errors.textContent = 'Не удалось выполнить проверку: ' + error.message;
    } finally { button.disabled = false; }
});

clearButton.addEventListener("click", function () {
    results = [];
    showResults();
    drawPlot(currentR);
    errors.textContent = '';
    try { localStorage.removeItem(STORAGE_KEY); }
    catch { errors.textContent = 'Таблица очищена, но сохранённую историю удалить не удалось.'; }
});

showResults();
drawPlot(currentR);
