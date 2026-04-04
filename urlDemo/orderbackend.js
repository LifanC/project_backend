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
}

moveFocus('form');

function show_user(log = {}) {
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
    document.getElementById('user_result').innerHTML = JSON.stringify(messageLog, null, 2);
}

function show_quotations(log = {}) {
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
    document.getElementById('quotations_result').innerHTML = JSON.stringify(messageLog, null, 2);
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

    // 取得 Token
    document.getElementById('btnTakeToken').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            password: document.getElementById('loginPass').value
        };
        try {
            const res = await api.post('orderbackend/takeToken', data);
            show_user(res);
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
            let token = res.message.token["1"];
            if (token) {
                setInputValue('token', token);
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
        } catch (err) {
            show_user(err);
        }
    });

    // 用戶商品報價
    document.getElementById('btnQuotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserQuotations').value,
            userPercent: document.getElementById('userPercent').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/quotationsProductItem', data, token);
            show_quotations(res);
        } catch (err) {
            show_quotations(err);
        }
    });
    
    // 確認報價
    document.getElementById('btnConfirmQuotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('userUserQuotations').value,
            userPercent: document.getElementById('userPercent').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('orderbackend/confirmQuotationsProductItem', data, token);
            show_quotations(res);
        } catch (err) {
            show_quotations(err);
        }
    });

});