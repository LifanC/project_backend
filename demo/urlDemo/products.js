// http://127.0.0.1:5500/products.html
// https://project-frontend-exjf.onrender.com/products.html
// http://localhost:8080/api/v1/
// https://project-backend-2olo.onrender.com/api/v1/
const BASES = [
    'http://localhost:8080/api/v1/',
    'https://project-backend-2olo.onrender.com/api/v1/'
];
let workingBase = localStorage.getItem('workingBase');

const api = {
    get: (path, data = null) => {
        return resolveRequest('GET', path, data)
    },
    post: (path, data) => {
        return resolveRequest('POST', path, data)
    },
    put: (path, data) => {
        return resolveRequest('PUT', path, data)
    },
    delete: (path, data = null) => {
        return resolveRequest('DELETE', path, data)
    },
    postfile: (path, data) => {
        return resolveRequest('POST_FILE', path, data)
    },
};

const methodMap = new Map([
    ['GET', getJSON],
    ['POST', postJSON],
    ['PUT', putJSON],
    ['DELETE', deleteJSON],
    ['POST_FILE', post_fileJSON],
]);

async function resolveRequest(method, path, data) {
    const fn = methodMap.get(method);
    if (workingBase) {
        try {
            console.log('使用已記住 API:', workingBase + path);
            return await fn(workingBase + path, data);
        } catch (err) {
            if (isFetchNetworkError(err)) {
                console.warn('API 掛了:', base);
                workingBase = null;
                localStorage.removeItem('workingBase');
            }
            return err;
        }
    }

    for (const base of BASES) {
        try {
            console.log('嘗試 API:', base + path);

            workingBase = base;
            const res = await fn(base + path, data);

            localStorage.setItem('workingBase', base);
            return res;
        } catch (err) {
            if (isFetchNetworkError(err)) {
                console.warn('API 掛了:', base);
                continue;
            }
            return err;
        }
    }
}

function isFetchNetworkError(err) {
    return err instanceof TypeError && err.message.includes('fetch');
}

testLogin();
async function testLogin() {
    const res = await api.get('products/testLogin', null);
    show(res);
    starTableFormat(res)
}

function starTableFormat(res) {
    let htmlHead = "";
    htmlHead += "<thead>";
    htmlHead += "<tr>";
    htmlHead += `<th>${"code"}</th>`;
    htmlHead += `<th>${"status"}</th>`;
    htmlHead += "</tr>";
    htmlHead += "<tr>";
    htmlHead += `<td>${res.code}</td>`;
    htmlHead += `<td>${res.status}</td>`;
    htmlHead += "</tr>";
    htmlHead += "</thead>";
    document.getElementById('products_code').innerHTML = htmlHead;

    let html = "";
    // 表頭
    html += "<thead><tr>";
    Object.keys(res.data[0]).forEach(key => {
        if (key.includes("_name")) {
            html += `<th>${res.data[0][key]}</th>`;
        }
    });
    html += "</tr></thead>";
    // 表身
    html += "<tbody>";
    res.data.forEach(row => {
        html += "<tr>";
        Object.keys(row).forEach(key => {
            if (!key.includes("_name")) {
                html += `<td>${row[key]}</td>`;
            }
        });
        html += "</tr>";
    });
    html += "</tbody>";

    getTableById('products_table', html);
}

function getTableById(id, html) {
    document.getElementById(id).innerHTML = html;
}

moveFocus('form');

function show(log = {}) {
    const {
        code = undefined,
        status = undefined,
        data = undefined,
        error = undefined,
        path = undefined,
        timestamp = undefined,
    } = log;
    const messageLog = {
        code,
        status,
        data,
        error,
        path,
        timestamp,
    }
    document.getElementById('result').innerHTML = JSON.stringify(messageLog, null, 2);
}

// POST JSON helper
async function postJSON(url, data) {
    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) throw json;
        return json;
    } catch (err) {
        return Promise.reject(err);
    }
}

// GET helper with query params
async function getJSON(url, params) {
    const query = params ? '?' + new URLSearchParams(params) : '';
    try {
        const res = await fetch(url + query, {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            }
        });
        const json = await res.json();
        if (!res.ok) throw json;
        return json;
    } catch (err) {
        return Promise.reject(err);
    }
}

// PUT JSON helper
async function putJSON(url, data) {
    try {
        const res = await fetch(url, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) throw json;
        return json;
    } catch (err) {
        return Promise.reject(err);
    }
}

// DELETE JSON helper
async function deleteJSON(url, data) {
    try {
        const res = await fetch(url, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(data)
        });
        const json = await res.json();
        if (!res.ok) throw json;
        return json;
    } catch (err) {
        return Promise.reject(err);
    }
}

// POST FILE JSON helper
async function post_fileJSON(url, data) {
    try {
        const res = await fetch(url, {
            method: 'POST',
            enctype: 'multipart/form-data',
            body: data
        });
        const json = await res.json();
        if (!res.ok) throw json;
        return json;
    } catch (err) {
        return Promise.reject(err);
    }
}

function tableFormat(res) {
    let htmlHead = "";
    htmlHead += "<thead>";
    htmlHead += "<tr>";
    htmlHead += `<th>${"code"}</th>`;
    htmlHead += `<th>${"status"}</th>`;
    htmlHead += "</tr>";
    htmlHead += "<tr>";
    htmlHead += `<td>${res.code}</td>`;
    htmlHead += `<td>${res.status}</td>`;
    htmlHead += "</tr>";
    htmlHead += "</thead>";
    document.getElementById('products_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                <col style="width: 80px;">
                <col style="width: 40px;">
                <col style="width: 80px;">
                <col style="width: 80px;">
                <col style="width: 80px;">
                <col style="width: 80px;">
                <col style="width: 80px;">
                <col style="width: 80px;">
            </colgroup>`;
        html += "<thead><tr>";
        let names = ["備註", "編號", "名稱", "價錢", "庫存量", "描述", "新增日期", "更改日期"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "product_id",
            "products_name",
            "price",
            "stock",
            "description",
            "created_date",
            "updated_date"
        ];
        html += "<tbody>";
        res.data.forEach(row => {
            html += "<tr>";

            fields.forEach(key => {
                html += `<td>${row[key] ?? ""}</td>`;
            });

            html += "</tr>";
        });
        html += "</tbody>";
    } else {
        // 表頭
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註"];
        if (len > 1) {
            names.push("錯誤")
        }
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        html += "<tbody>";
        html += "<tr>";
        for (let index = 0; index < len; index++) {
            const row = res.data[index];
            if (index == 0) {
                html += `<td>${row.remark ?? ""}</td>`;
            } else {
                if (row.error) {
                    const err = row.error;
                    const keys = Object.keys(err);
                    html += `<td>`;
                    keys.forEach(key => {
                        html += `${err[key] ?? ""}<br/>`
                    });
                    html += `</td>`;
                } else {
                    html += `<td></td>`;
                }
            }
        }
        html += "</tr>";
        html += "</tbody>";
    }

    getTableById('products_table', html);
}

function tableFormatPreview(res) {
    let htmlHead = "";
    htmlHead += `<colgroup>
                <col style="width: 200px;">
                <col style="width: 200px;">
                <col style="width: 200px;">
            </colgroup>`;
    htmlHead += "<thead>";
    htmlHead += "<tr>";
    htmlHead += `<th>${"檔案名稱"}</th>`;
    htmlHead += `<th>${"檔案尺寸"}</th>`;
    htmlHead += `<th>${"檔案類型"}</th>`;
    htmlHead += "<tr>";
    htmlHead += `<td>${res.name}</td>`;
    htmlHead += `<td>${res.size}</td>`;
    htmlHead += `<td>${res.type}</td>`;
    htmlHead += "</tr>";
    htmlHead += "</tr></thead>";
    document.getElementById('products_code').innerHTML = htmlHead;
}

function tableFormatPreviewData(res) {
    let html = "";
    // 表頭
    html += `<colgroup>
                <col style="width: 80px;">
                <col style="width: 80px;">
                <col style="width: 80px;">
                <col style="width: 360px;">
            </colgroup>`;
    html += "<thead><tr>";
    let names = ["名稱", "價錢", "庫存量", "描述"];
    names.forEach(key => {
        html += `<th>${key}</th>`;
    });
    html += "</tr></thead>";
    // 表身
    const fields = [
        "products_name",
        "price",
        "stock",
        "description"
    ];
    html += "<tbody>";
    res.data.forEach(row => {
        html += "<tr>";

        fields.forEach(key => {
            html += `<td>${row[key] ?? ""}</td>`;
        });

        html += "</tr>";
    });
    html += "</tbody>";

    getTableById('products_table', html);
}

function tableFormatUpload(res) {
    let htmlHead = "";
    htmlHead += "<thead>";
    htmlHead += "<tr>";
    htmlHead += `<th>${"code"}</th>`;
    htmlHead += `<th>${"status"}</th>`;
    htmlHead += "<tr>";
    htmlHead += `<td>${res.code}</td>`;
    htmlHead += `<td>${res.status}</td>`;
    htmlHead += "</tr>";
    htmlHead += "</tr></thead>";
    document.getElementById('products_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 200px;">
                    <col style="width: 200px;">
                    <col style="width: 200px;">
                    <col style="width: 200px;">
                </colgroup>`;
        html += "<thead><tr>";
        let names = ["備註", "描述", "說明", "狀態"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "directions",
        ];
        html += "<tbody>";
        res.data.forEach(row => {
            html += "<tr>";

            fields.forEach(key => {
                html += `<td>${row[key] ?? ""}</td>`;
            });

            const keys = Object.keys(row);
            const result = keys.filter(item => item.includes("details"));
            html += `<td>`;
            html += "<table>";
            html += "<tbody>";
            result.forEach(key => {
                row[key].forEach(details => {
                    html += "<tr>";
                    html += `<td>${details ?? ""}</td>`;
                    html += "</tr>";
                });
            });
            html += "</tbody>";
            html += "</table>";
            html += `</td>`;

            html += `<td>${row["state"] ?? ""}</td>`;

            html += "</tr>";
        });
        html += "</tbody>";
    } else {
        // 表頭
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註"];
        if (len > 1) {
            names.push("錯誤")
        }
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        html += "<tbody>";
        html += "<tr>";
        for (let index = 0; index < len; index++) {
            const row = res.data[index];
            if (index == 0) {
                html += `<td>${row.remark ?? ""}</td>`;
            } else {
                if (row.error) {
                    const err = row.error;
                    const keys = Object.keys(err);
                    html += `<td>`;
                    keys.forEach(key => {
                        html += `${err[key] ?? ""}<br/>`
                    });
                    html += `</td>`;
                } else {
                    html += `<td></td>`;
                }
            }
        }
        html += "</tr>";
        html += "</tbody>";
    }

    getTableById('products_table', html);
}

document.addEventListener('DOMContentLoaded', () => {
    
    const e1 = document.getElementById("products_timestamp");
    const formatter = new Intl.DateTimeFormat("zh-TW", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
    });
    function updateTimestamp() {
        const date = new Date();
        e1.innerHTML = `<h3>${formatter.format(date)}</h3>`;
    }
    // 先立即更新一次（避免空白）
    updateTimestamp();
    // 每秒更新
    setInterval(updateTimestamp, 1000);

    // 新增
    document.getElementById('insert').addEventListener('click', async () => {
        const data = {
            products_name: document.getElementById('products_name').value,
            price: document.getElementById('price').value,
            stock: document.getElementById('stock').value,
            description: document.getElementById('description').value
        };
        try {
            const res = await api.post('products/insert', data);
            show(res);
            tableFormat(res);
        } catch (err) {
            show(err);
        }
    });

    // 查詢
    document.getElementById('select').addEventListener('click', async () => {
        const data = {
            product_id: document.getElementById('product_id').value
        };
        try {
            const res = await api.post('products/select', data);
            show(res);
            tableFormat(res);
        } catch (err) {
            show(err);
        }
    });

    // 更改
    document.getElementById('update').addEventListener('click', async () => {
        const data = {
            product_id: document.getElementById('product_id').value,
            products_name: document.getElementById('products_name').value,
            price: document.getElementById('price').value,
            stock: document.getElementById('stock').value,
            description: document.getElementById('description').value
        };
        try {
            const res = await api.put('products/update', data);
            show(res);
            tableFormat(res);
        } catch (err) {
            show(err);
        }
    });

    // 刪除
    document.getElementById('delete').addEventListener('click', async () => {
        const data = {
            product_id: document.getElementById('product_id').value
        };
        try {
            const res = await api.delete('products/delete', data);
            show(res);
            tableFormat(res);
        } catch (err) {
            show(err);
        }
    });

    // 預覽
    document.getElementById("previewBtn").addEventListener('click', async () => {
        const file = await getValidCsvFile();
        if (!file) {
            return;
        }
        let text = await readFile(file);
        text = text.replace(/^\uFEFF/, "");
        const rows = text.trim().split(/\r?\n/);
        const headers = rows[0].split(",");
        const data = rows.slice(1).map(row => {
            const values = row.split(",");
            return headers.reduce((obj, h, i) => {
                obj[h] = values[i];
                return obj;
            }, {});
        });
        tableFormatPreviewData({ data });
    });

    function readFile(file) {
        return new Promise((resolve) => {
            const reader = new FileReader();
            reader.onload = e => resolve(e.target.result);
            reader.readAsText(file, 'utf-8');
        });
    }

    // 上傳
    document.getElementById("uploadBtn").addEventListener('click', async () => {
        const file = await getValidCsvFile();
        if (!file) {
            return;
        }
        const data = new FormData();
        data.append("file", file);
        try {
            const res = await api.postfile('products/uploadFile', data);
            show(res);
            tableFormatUpload(res);
        } catch (err) {
            show(err);
        }
    });

    // 清除檔案
    document.getElementById("clearFile").addEventListener('click', async () => {
        document.getElementById("fileInput").value = "";
        resetPreview();
    });

    const emptyData = [
        {
            products_name: "",
            price: "0",
            stock: "0",
            description: ""
        }
    ];

    function resetPreview(name = "") {
        tableFormatPreview({
            name,
            size: 0,
            type: ""
        });

        tableFormatPreviewData({
            data: emptyData
        });
    }

    async function getValidCsvFile() {
        const file = document.querySelector("#fileInput").files[0];
        if (!file) {
            resetPreview("未選擇檔案");
            return null;
        }
        tableFormatPreview({
            name: file.name,
            size: file.size,
            type: file.type
        });
        const invalid = () => {
            tableFormatPreviewData({ data: emptyData });
            return null;
        };
        if (!file.name.toLowerCase().endsWith(".csv")) {
            return invalid();
        }
        const rows = (await file.text())
            .trim()
            .split(/\r?\n/);
        const columnCount = rows[0]?.split(",").length ?? 0;
        if (
            rows.length < 2 ||
            !rows.every(row => row.split(",").length === columnCount)
        ) {
            return invalid();
        }
        return file;
    }

});