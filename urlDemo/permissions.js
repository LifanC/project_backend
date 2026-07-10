// http://127.0.0.1:5500/permissions.html
// https://project-frontend-exjf.onrender.com/permissions.html
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
    }
};

const methodMap = new Map([
    ['GET', getJSON],
    ['POST', postJSON],
    ['PUT', putJSON],
    ['DELETE', deleteJSON]
]);

async function resolveRequest(method, path, data) {
    const fn = methodMap.get(method);
    if (workingBase) {
        try {
            console.log('使用已記住 API:', workingBase + path);
            return await fn(workingBase + path, data);
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

testLogin()
async function testLogin() {
    const res = await api.get('permissions/testLogin', null);
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
    document.getElementById('permissions_code').innerHTML = htmlHead;

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

    getTableById('permissions_table', html);
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
async function deleteJSON(url, params) {
    const del = params ? '?' + new URLSearchParams(params) : '';
    try {
        const res = await fetch(url + del, {
            method: 'DELETE',
            headers: {
                'Accept': 'application/json'
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
    document.getElementById('permissions_code').innerHTML = htmlHead;

    let html = "";
    // 表頭
    html += "<thead><tr>";
    let names = ["備註", "帳號", "權限", "新增日期", "更改日期"];
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

    getTableById('permissions_table', html);
}

document.addEventListener('DOMContentLoaded', () => {

    const e1 = document.getElementById("permissions_timestamp");
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

    // 註冊
    document.getElementById('btnRegister').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('regUser').value,
            password: document.getElementById('regPass').value,
            permissions: document.querySelector('input[name="permission"]:checked').value
        };
        try {
            const res = await api.post('permissions/register', data);
            show(res);
            tableFormat(res)
        } catch (err) {
            show(err);
            tableFormat(err)
        }
    });

    // 查詢
    document.getElementById('btnQuery').addEventListener('click', async () => {
        try {
            const res = await api.get('permissions/query', null);
            show(res);
            tableFormat(res)
        } catch (err) {
            show(err);
            tableFormat(err)
        }
    });

    // 更改權限
    document.getElementById('btnUpdate').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('regUser').value,
            password: document.getElementById('regPass').value,
            permissions: document.querySelector('input[name="permission"]:checked').value
        };
        try {
            const res = await api.put('permissions/update', data);
            show(res);
            tableFormat(res)
        } catch (err) {
            show(err);
            tableFormat(err)
        }
    });

    // 刪除
    document.getElementById('btnDelete').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('regUser').value,
            password: document.getElementById('regPass').value
        };
        try {
            const res = await api.delete('permissions/delete', data);
            show(res);
            tableFormat(res)
        } catch (err) {
            show(err);
            tableFormat(err)
        }
    });

});