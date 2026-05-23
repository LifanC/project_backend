const Cookie = {
    set(name, value, maxAge = null) {
        const val =
            typeof value === "object"
                ? JSON.stringify(value)
                : value;

        let cookieStr = `${name}=${encodeURIComponent(val)}; path=/`;
        // 👇 有設定才變成「持久 cookie」
        if (maxAge !== null) {
            cookieStr += `; max-age=${maxAge}`;
        }

        document.cookie = cookieStr;
    },

    get(name) {
        const cookies = document.cookie.split("; ");

        for (const cookie of cookies) {
            const index = cookie.indexOf("=");
            const key = cookie.substring(0, index);
            const value = cookie.substring(index + 1);

            if (key === name) {
                const decoded = decodeURIComponent(value);

                try {
                    return JSON.parse(decoded);
                } catch {
                    return decoded;
                }
            }
        }

        return null;
    },

    remove(name) {
        document.cookie = `${name}=; max-age=0; path=/`;
    }
};

let logoutTimer = null;

// 10分鐘自動登出
function startAutoLogout() {
    clearTimeout(logoutTimer);

    logoutTimer = setTimeout(() => {
        const yes = confirm("已自動登出（10分鐘到期）是否繼續?");
        if (yes) {
            startAutoLogout();
        } else {
            logout();
        }
    }, 600000); // 600000ms = 10分鐘
}

function checkLogin() {
    const profiletoken = Cookie.get("profiletoken");
    if (profiletoken) {
        startAutoLogout();
    }
}

function logout() {
    Cookie.remove("profiletoken");
    document.getElementById("token").value = ""
    clearTimeout(logoutTimer);
}

// http://127.0.0.1:5500/orderbackend.html
// https://project-frontend-exjf.onrender.com/orderbackend.html
// http://localhost:8080/api/v1/
// https://project-backend-2olo.onrender.com/api/v1/
const BASES = [
    'http://localhost:8080/api/v1/',
    'https://project-backend-2olo.onrender.com/api/v1/'
];
var workingBase = localStorage.getItem('workingBase');

const api = {
    get: (path, data = null) => {
        return resolveRequest('GET', path, data)
    },
    post: (path, data, token) => {
        return resolveRequest('POST', path, data, token)
    },
    put: (path, data) => {
        return resolveRequest('PUT', path, data)
    },
    delete: (path, data = null) => {
        return resolveRequest('DELETE', path, data)
    }
};

const methodMap = new Map([
    ['GET', getJSON],
    ['POST', postJSON],
    ['PUT', putJSON],
    ['DELETE', deleteJSON]
]);

async function resolveRequest(method, path, data, token) {
    const fn = methodMap.get(method);
    if (workingBase) {
        try {
            console.log('使用已記住 API:', workingBase + path);
            return await fn(workingBase + path, data, token);
        } catch (err) {
            if (isFetchNetworkError(err)) {
                console.warn('API 掛了:', workingBase);
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
            const res = await fn(base + path, data, token);

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

testLogin()
async function testLogin() {
    const res = await api.get('orderbackend/testLogin', null);
    show_user(res);
    starTableFormat(res);
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
    document.getElementById('user_code').innerHTML = htmlHead;

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

    getTableById('user_table', html);
    document.getElementById('user_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
}

function getTableById(id, html) {
    document.getElementById(id).innerHTML = html;
}

function escapeHTML(str) {
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

moveFocus('form');

function show_user(log = {}) {
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
    document.getElementById('user_result').innerHTML = JSON.stringify(messageLog, null, 2);
}

// POST JSON helper
async function postJSON(url, data, token) {
    const authHeader = `Bearer ${token}`;
    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': authHeader
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
async function getJSON(url, params, token) {
    const authHeader = `Bearer ${token}`;
    const query = params ? '?' + new URLSearchParams(params) : '';
    try {
        const res = await fetch(url + query, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Authorization': authHeader
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
async function putJSON(url, data, token) {
    const authHeader = `Bearer ${token}`;
    try {
        const res = await fetch(url, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': authHeader
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
async function deleteJSON(url, params, token) {
    const authHeader = `Bearer ${token}`;
    const del = params ? '?' + new URLSearchParams(params) : '';
    try {
        const res = await fetch(url + del, {
            method: 'DELETE',
            headers: {
                'Accept': 'application/json',
                'Authorization': authHeader
            },
            body: JSON.stringify(params)
        });
        const json = await res.json();
        if (!res.ok) throw json;
        return json;
    } catch (err) {
        return Promise.reject(err);
    }
}

// 將值塞入 input 並自動調整寬度
function setInputValue(inputId, value) {
    const input = document.getElementById(inputId);
    input.value = value;
}

function tableFormat(res) {
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
    document.getElementById('user_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "帳號", "權限", "日期"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";

        // 表身
        const fields = [
            "remark",
            "username",
            "permissions",
            "created_date",
        ];
        html += "<tbody>";
        for (let index = 0; index < res.data.length; index++) {
            const row = res.data[index];
            html += "<tr>";
            if (index == 0) {
                fields.forEach(key => {
                    html += `<td>${row[key] ?? ""}</td>`;
                });
            } else {
                if (row.token) {
                    // 不加
                }
            }
            html += "</tr>";
        };
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

    getTableById('user_table', html);
    document.getElementById('user_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
}

// 報價-查詢用戶名單
function tableFormatQueryUser(res) {
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
    document.getElementById('quotations_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "帳號", "權限", "使用者名單"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "username",
            "permissions",
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
            result.forEach(key => {
                html += `${row[key] ?? ""}<br/>`
            });
            html += `</td>`;

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

    getTableById('quotations_table', html);
    document.getElementById('quotations_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
}

// 報價-用戶商品報價單
function tableFormatQuotationsProduct(res) {
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
    document.getElementById('quotations_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 100px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 200px;">
                    <col style="width: 140px;">
                </colgroup>`;
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "帳號", "權限", "使用者", "商品報價"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "username",
            "permissions",
            "user",
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

    getTableById('quotations_table', html);
    document.getElementById('quotations_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
}

// 報價-確認、刪除、查詢、送出
function tableFormatConfirmQuotationsProduct(res) {
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
    document.getElementById('quotations_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 220px;">
                    <col style="width: 60px;">
                </colgroup>`;
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "用戶編號", "報價單編號", "庫存量", "細項", "狀態"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "useruser",
            "quotationsId",
            "stock",
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

    getTableById('quotations_table', html);
    document.getElementById('quotations_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
}

// 訂單
function tableFormatOrdersUser(res) {
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
    document.getElementById('order_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 220px;">
                    <col style="width: 60px;">
                </colgroup>`;
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "用戶編號", "訂單編號", "報價單編號", "細項", "狀態"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "useruser",
            "orderId",
            "quotationsId",
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

    getTableById('order_table', html);
    document.getElementById('order_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
}

// 出貨
function tableFormatShipments(res) {
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
    document.getElementById('shipments_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 220px;">
                    <col style="width: 60px;">
                </colgroup>`;
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "用戶編號", "訂單編號", "報價單編號", "細項", "狀態"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "useruser",
            "orderId",
            "quotationsId",
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

    getTableById('shipments_table', html);
    document.getElementById('shipments_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
}

document.addEventListener('DOMContentLoaded', () => {

    checkLogin();
    const profile = Cookie.get("profile");
    const profiletoken = Cookie.get("profiletoken");
    if (profile) {
        document.getElementById("loginUser").value = profile.username
        document.getElementById("loginPass").value = profile.password
    }
    if (profiletoken) {
        document.getElementById("token").value = profiletoken.token
    }

    // 取得 Token
    document.getElementById('btnTakeToken').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            password: document.getElementById('loginPass').value
        };
        try {
            const res = await api.post('orderbackend/takeToken', data);
            show_user(res);
            tableFormat(res);

            Cookie.set("profile", data);
        } catch (err) {
            show_user(err);
        }
    });

    // 驗證 Token
    document.getElementById('btnValidate').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            const res = await api.post('orderbackend/validate', data);
            show_user(res);
            tableFormat(res);
            if (res.data.length > 1) {
                if (res.data[1].token) {
                    let token = res.data[1].token;
                    if (token) {
                        setInputValue('token', token);

                        Cookie.set("profiletoken", { token });
                        startAutoLogout();
                    }
                }
            }
        } catch (err) {
            show_user(err);
        }
    });

    // 登出
    document.getElementById('btnLogout').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/logout', data, token);
            show_user(res);
            tableFormat(res);

            Cookie.remove("profile");
            Cookie.remove("profiletoken");
            clearTimeout(logoutTimer);
        } catch (err) {
            show_user(err);
        }
        setInputValue('token', "");
    });

    // 查用戶
    document.getElementById('btnQueryUser').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/queryUser', data, token);
            show_user(res);
            tableFormatQueryUser(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢用戶商品報價
    document.getElementById('btnQuotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserQuotations').value,
            userPercent: document.getElementById('userPercent').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/quotationsProductItem', data, token);
            show_user(res);
            tableFormatQuotationsProduct(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 確認報價單
    document.getElementById('btnConfirmQuotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserQuotations').value,
            userPercent: document.getElementById('userPercent').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/confirmQuotationsProductItem', data, token);
            show_user(res);
            tableFormatConfirmQuotationsProduct(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 刪除報價單
    document.getElementById('btnDeleteQuotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserQuotations').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/deleteQuotationsProduct', data, token);
            show_user(res);
            tableFormatConfirmQuotationsProduct(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢報價單
    document.getElementById('btnQueryQuotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserQuotations').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/queryQuotationsProduct', data, token);
            show_user(res);
            tableFormatConfirmQuotationsProduct(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 送出報價單
    document.getElementById('btnSendQuotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserQuotationsSend').value,
            userUserQuotationsId: document.getElementById('userUserQuotationsId').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/sendQuotationsProduct', data, token);
            show_user(res);
            tableFormatConfirmQuotationsProduct(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢用戶訂單名單
    document.getElementById('btnOrdersUser').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/ordersUser', data, token);
            show_user(res);
            tableFormatOrdersUser(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢訂單
    document.getElementById('btnOrdersProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserOrders').value,
            orderId: document.getElementById('userOrderId').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/ordersProduct', data, token);
            show_user(res);
            tableFormatOrdersUser(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 確認訂單
    document.getElementById('btnOrdersConfirmed').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserOrders').value,
            orderId: document.getElementById('userOrderId').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/ordersConfirmed', data, token);
            show_user(res);
            tableFormatOrdersUser(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 取消訂單
    document.getElementById('btnOrdersCancelled').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserOrders').value,
            orderId: document.getElementById('userOrderId').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/ordersCancelled', data, token);
            show_user(res);
            tableFormatOrdersUser(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢用戶出貨名單
    document.getElementById('btnShipmentsUser').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserShipments').value,
            orderId: document.getElementById('userUserShipmentsId').value,
            trackingNumber: document.getElementById('userShipmentsTrackingNumber').value,
            datePart: document.getElementById('userShipmentsDatePart').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/shipmentsTrackingNumber', data, token);
            show_user(res);
            tableFormatShipments(res)
        } catch (err) {
            show_user(err);
        }
    });

    // 已出貨
    document.getElementById('btnShipmentsShipped').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            trackingNumber: document.getElementById('userTrackingNumber').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/shipmentsShipped', data, token);
            show_user(res);
            tableFormatShipments(res)
        } catch (err) {
            show_user(err);
        }
    });

    // 已送達
    document.getElementById('btnShipmentsDelivered').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            trackingNumber: document.getElementById('userTrackingNumber').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/shipmentsDelivered', data, token);
            show_user(res);
            tableFormatShipments(res)
        } catch (err) {
            show_user(err);
        }
    });

    // 恢復出貨狀態
    document.getElementById('btnShipmentsRollback').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            trackingNumber: document.getElementById('userTrackingNumber').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/shipmentsRollback', data, token);
            show_user(res);
            tableFormatShipments(res)
        } catch (err) {
            show_user(err);
        }
    });

});