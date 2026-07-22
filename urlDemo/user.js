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
    const profiletoken = Cookie.get("user_profiletoken");
    if (profiletoken) {
        startAutoLogout();
    }
}

function logout() {
    Cookie.remove("user_profiletoken");
    clearTimeout(logoutTimer);
}

// http://127.0.0.1:5500/user.html
// https://project-frontend-exjf.onrender.com/user.html
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
    const res = await api.get('login/testLogin', null);
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
}

function loginTableFormat(res) {
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
}

function getTableById(id, html) {
    document.getElementById(id).innerHTML = html;
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

// 商品
function tableFormatProduct(res) {
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
    document.getElementById('product_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        html += `<colgroup>
                    <col style="width: 100px;">
                    <col style="width: 100px;">
                    <col style="width: 100px;">
                    <col style="width: 300px;">
                </colgroup>`;
        // 表頭
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "編號", "名稱", "描述"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "product_id",
            "products_name",
            "description",
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

    getTableById('product_table', html);
}

// 購物車
function tableFormatCar(res) {
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
    document.getElementById('car_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 100px;">
                </colgroup>`;
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "帳號", "商品編號", "商品名稱", "數量", "描述", "新增日期", "更改日期"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "username",
            "product_id",
            "products_name",
            "product_quantity",
            "description",
            "created_date",
            "updated_date",
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

    getTableById('car_table', html);
}

// 查詢購物車
function tableFormatQueryCar(res) {
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
    document.getElementById('car_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 100px;">
                    <col style="width: 100px;">
                    <col style="width: 400px;">
                </colgroup>`;
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "帳號", "購物車明細"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "username",
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

    getTableById('car_table', html);
}

// 報價
function tableFormatQuotationsProductId(res) {
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
        let names = ["備註", "帳號", "權限", "報價單編號", "細項", "狀態"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "username",
            "permissions",
            "quotation_id",
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
        let names = ["備註", "帳號", "權限", "訂單編號", "細項", "狀態"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "username",
            "permissions",
            "orderId",
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
}

// 付款
function tableFormatPayments(res) {
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
    document.getElementById('payments_code').innerHTML = htmlHead;

    let statuss = [200];
    let html = "";
    if (statuss.includes(res.status)) {
        // 表頭
        html += `<colgroup>
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 80px;">
                    <col style="width: 190px;">
                    <col style="width: 45px;">
                    <col style="width: 45px;">
                </colgroup>`;
        html += "<thead><tr>";
        let len = res.data.length;
        let names = ["備註", "帳號", "權限", "訂單編號", "細項", "狀態", "方法"];
        names.forEach(key => {
            html += `<th>${key}</th>`;
        });
        html += "</tr></thead>";
        // 表身
        const fields = [
            "remark",
            "username",
            "permissions",
            "orderId",
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
            html += `<td>${row["method"] ?? ""}</td>`;

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

    getTableById('payments_table', html);
}

document.addEventListener('DOMContentLoaded', () => {
    
    const e1 = document.getElementById("timestamp");
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

    checkLogin();
    const profile = Cookie.get("user_profile");
    const profiletoken = Cookie.get("user_profiletoken");
    if (profile) {
        document.getElementById("loginUser").value = profile.username
        document.getElementById("loginPass").value = profile.password
    }
    if (profiletoken) {
        document.getElementById("token").value = profiletoken.token
    }

    let notificationInterval = null;
    function startNotificationPolling() {
        if (notificationInterval) {
            return; // 避免重複建立多個 interval
        }
        loadNotifications();
        notificationInterval = setInterval(
            loadNotifications,
            5000
        );
    }

    function stopNotificationPolling() {

        if (notificationInterval) {
            clearInterval(notificationInterval);
            notificationInterval = null;
        }
    }

    async function loadNotifications() {
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser
        };
        const res = await api.post('notifications/unread', data, token);
        const area = document.getElementById("notificationArea");
        area.innerHTML = "";
        if (res.data[0]['details'] === undefined) return;
        area.innerHTML += `
            <div>
                ${res.data[0]['details']}
            </div>
        `;
        if (res.data[1]['cnt'] === undefined) return;
        if (res.data[1]['cnt'] > 0) {
            let num = 0;
            for (let index = 2; index < res.data.length; index++) {
                const element = res.data[index];
                area.innerHTML += `
                    <div>
                        ${(num + 1)}. ${element['details' + num]};${element['status' + num]}
                    </div>
                `;
                num++;
            }
        }
    }

    let loginUser = (profile) ? profile.username : '';
    // socket
    const socket = new SockJS(
        workingBase + "ws?username=" + encodeURIComponent(loginUser)
    );
    const client = StompJs.Stomp.over(socket);
    client.connect(
        {},
        function () {
            const profile = Cookie.get("user_profile");
            let loginUser = (profile) ? profile.username : '';

            console.log("WebSocket connected");


            client.subscribe(
                "/user/queue/notifications",
                function(message) {


                    const notification =
                        JSON.parse(message.body);


                    console.log(
                        "收到個人通知:",
                        notification
                    );


                    alert(
                        notification.title +
                        "\n" +
                        notification.content
                    );
                }
            );

        }
    );

    // 取得 Token
    document.getElementById('btnTakeToken').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            password: document.getElementById('loginPass').value
        };
        try {
            const res = await api.post('login/takeToken', data);
            show_user(res);
            loginTableFormat(res);

            Cookie.set("user_profile", data);
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
            const res = await api.post('login/validate', data);
            show_user(res);
            loginTableFormat(res);
            if (res.data.length > 1) {
                if (res.data[1].token) {
                    let token = res.data[1].token;
                    if (token) {
                        setInputValue('token', token);

                        Cookie.set("user_profiletoken", { token });
                        startAutoLogout();
                        // startNotificationPolling();
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
            const res = await api.post('login/logout', data, token);
            show_user(res);
            loginTableFormat(res);

            Cookie.remove("user_profile");
            Cookie.remove("user_profiletoken");
            clearTimeout(logoutTimer);
            stopNotificationPolling();
        } catch (err) {
            show_user(err);
        }
        setInputValue('token', "");
    });

    // 查詢商品
    document.getElementById('productsCarSelect').addEventListener('click', async () => {
        let product_id_SelectCar1 = document.getElementById('product_id_SelectCar1').value;
        let product_id_SelectCar2 = document.getElementById('product_id_SelectCar2').value;
        let product_ids = [product_id_SelectCar1, product_id_SelectCar2];
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            product_id: document.getElementById('product_id_SelectCar1').value,
            product_ids: product_ids
        };
        try {
            const res = await api.post('user/car/productsCarSelect', data, token);
            show_user(res);
            tableFormatProduct(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 新增購物車
    document.getElementById('btnCreateCar').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            product_id: document.getElementById('product_id_CreateCar').value,
            product_quantity: document.getElementById('product_quantity_CreateCar').value
        };
        try {
            const res = await api.post('user/car/createCarItem', data, token);
            show_user(res);
            tableFormatCar(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢購物車
    document.getElementById('btnQueryCar').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser
        };
        try {
            const res = await api.post('user/car/queryCarItem', data, token);
            show_user(res);
            tableFormatQueryCar(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 更改購物車
    document.getElementById('btnUpdateCar').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            product_id: document.getElementById('product_id_UpdateCar').value,
            product_quantity: document.getElementById('product_quantity_UpdateCar').value
        };
        try {
            const res = await api.post('user/car/updateCarItem', data, token);
            show_user(res);
            tableFormatCar(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 刪除購物車
    document.getElementById('btnDeleteCar').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            product_id: document.getElementById('product_id_DeleteCar').value
        };
        try {
            const res = await api.post('user/car/deleteCarItem', data, token);
            show_user(res);
            tableFormatQueryCar(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 確認訂單
    document.getElementById('btnConfirm').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser
        };
        try {
            const res = await api.post('user/car/confirmItem', data, token);
            show_user(res);
            tableFormatQueryCar(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢報價單編號
    document.getElementById('quotationsProductId').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser
        };
        try {
            const res = await api.post('user/quotation/quotationsProductId', data, token);
            show_user(res);
            tableFormatQuotationsProductId(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢報價單
    document.getElementById('quotationsProduct').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            quotation_id: document.getElementById('user_quotationsProduct').value
        };
        try {
            const res = await api.post('user/quotation/quotationsProduct', data, token);
            show_user(res);
            tableFormatQuotationsProductId(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 接受報價單
    document.getElementById('user_accepted').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            quotation_id: document.getElementById('user_quotationsProduct').value
        };
        try {
            const res = await api.post('user/quotation/userAccepted', data, token);
            show_user(res);
            tableFormatQuotationsProductId(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 拒絕報價單
    document.getElementById('user_rejected').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            quotation_id: document.getElementById('user_quotationsProduct').value
        };
        try {
            const res = await api.post('user/quotation/userRejected', data, token);
            show_user(res);
            tableFormatQuotationsProductId(res);
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢出貨資訊
    document.getElementById('user_shipments').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            orderId: document.getElementById('user_shipmentsId').value,
            trackingNumber: document.getElementById('user_shipmentsTrackingNumber').value,
            datePart: document.getElementById('user_shipmentsDatePart').value,
            shipmentsStatus: document.getElementById('user_shipmentsStatus').value,
            paymentsStatus: document.getElementById('user_paymentsStatus').value,
            paymentsMethod: document.getElementById('user_paymentsMethod').value
        };
        try {
            const res = await api.post('user/shipment/userShipments', data, token);
            show_user(res);
            tableFormatShipments(res)
        } catch (err) {
            show_user(err);
        }
    });

    // 查詢付款資訊
    document.getElementById('user_payments').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            trackingNumber: document.getElementById('user_paymentsTrackingNumber').value
        };
        try {
            const res = await api.post('user/payment/userPayments', data, token);
            show_user(res);
            tableFormatPayments(res)
        } catch (err) {
            show_user(err);
        }
    });

    // 付款
    document.getElementById('user_payMoney').addEventListener('click', async () => {
        const profile = Cookie.get("user_profile");
        const profiletoken = Cookie.get("user_profiletoken");
        let loginUser = (profile) ? profile.username : '';
        let token = (profiletoken) ? profiletoken.token : '';
        const data = {
            username: loginUser,
            trackingNumber: document.getElementById('user_paymentsTrackingNumber').value,
            amount: document.getElementById('user_paymentsAmount').value,
            paymentsMethod: document.getElementById('user_paymentsPaymentsMethod').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/payment/userPayMoney', data, token);
            show_user(res);
            tableFormatPayments(res)
        } catch (err) {
            show_user(err);
        }
    });

});