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
    document.getElementById("token").value = ""
    clearTimeout(logoutTimer);
}

// http://127.0.0.1:5500/login.html
// https://project-frontend-exjf.onrender.com/login.html
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

document.addEventListener('DOMContentLoaded', () => {

    const e1 = document.getElementById("user_timestamp");
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
        } catch (err) {
            show_user(err);
        }
        setInputValue('token', "");
    });

})