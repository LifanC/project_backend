// http://127.0.0.1:5500/permissions.html
// https://project-frontend-exjf.onrender.com/permissions.html
// http://localhost:8080/api/v1/
// https://project-backend-2olo.onrender.com/api/v1/
const BASES = [
    'http://localhost:8080/api/v1/',
    'https://project-backend-2olo.onrender.com/api/v1/'
];
let workingBase = localStorage.getItem('workingBase');

const SafetyUsername = 'lukechen';
const SafetyPassword = '1qaz@WSX';
const authHeader = 'Basic ' + utf8ToB64(SafetyUsername + ':' + SafetyPassword);

function utf8ToB64(str) {
    return btoa(unescape(encodeURIComponent(str)));
}

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
            const res = await fn(base + path, null);

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
}

moveFocus('form');

function show(log = {}) {
    const {
        code = undefined,
        status = undefined,
        message = undefined,
        error = undefined,
        path = undefined,
        timestamp = undefined,
    } = log;
    const messageLog = {
        code,
        status,
        message,
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
async function getJSON(url, params) {
    const query = params ? '?' + new URLSearchParams(params) : '';
    try {
        const res = await fetch(url + query, {
            method: 'GET',
            headers: {
                'Authorization': authHeader,
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
async function deleteJSON(url, params) {
    const del = params ? '?' + new URLSearchParams(params) : '';
    try {
        const res = await fetch(url + del, {
            method: 'DELETE',
            headers: {
                'Authorization': authHeader,
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

document.addEventListener('DOMContentLoaded', () => {

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
        } catch (err) {
            show(err);
        }
    });

    // 查詢
    document.getElementById('btnQuery').addEventListener('click', async () => {
        try {
            const res = await api.get('permissions/query', null);
            show(res);
        } catch (err) {
            show(err);
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
        } catch (err) {
            show(err);
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
        } catch (err) {
            show(err);
        }
    });

});