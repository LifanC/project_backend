// http://127.0.0.1:5500/userUrl.html
// https://project-frontend-exjf.onrender.com/userUrl.html
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
    const res = await api.get('user/testLogin', null);
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
    input.value = value[0];
}

document.addEventListener('DOMContentLoaded', () => {

    // 取得 Token
    document.getElementById('btnTakeToken').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            password: document.getElementById('loginPass').value
        };
        try {
            const res = await api.post('user/takeToken', data);
            show(res);
        } catch (err) {
            show(err);
        }
    });

    // 驗證 Token
    document.getElementById('btnValidate').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            const res = await api.post('user/validate', data);
            show(res);
            let token = res.message.token;
            if (token) {
                setInputValue('token', token);
            }
        } catch (err) {
            show(err);
        }
    });

    // 登出
    document.getElementById('btnLogout').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/logout', data, token);
            show(res);
        } catch (err) {
            show(err);
        }
        setInputValue('token', ['']);
    });

    // 查使用者
    document.getElementById('btnQueryUser').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/queryUser', data, token);
            show(res);
        } catch (err) {
            show(err);
        }
    });

    // 新增訂單
    document.getElementById('btnCreate').addEventListener('click', async () => {
        let container = document.getElementById('inputContainer');
        let cnt = container.getElementsByTagName('input').length;
        let item = [];
        for (let i = 1; i <= cnt; i++) {
            item.push(document.getElementById('order' + i).value);
        }
        let order_item = [];
        for (let index = 0; index < item.length; index++) {
            let element = item[index];
            if (element) {
                order_item.push(element)
            }
        }
        const data = {
            username: document.getElementById('loginUser').value,
            order_item: order_item
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/createOrderItem', data, token);
            show(res);
        } catch (err) {
            show(err);
        }
    });

    // 查詢訂單
    document.getElementById('btnQuery').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/queryOrderItem', data, token);
            show(res);
        } catch (err) {
            show(err);
        }
    });

    // 更改訂單
    document.getElementById('btnUpdate').addEventListener('click', async () => {
        let container = document.getElementById('inputContainer');
        let cnt = container.getElementsByTagName('input').length;
        let item = [];
        for (let i = 1; i <= cnt; i++) {
            item.push(document.getElementById('order' + i).value);
        }
        let order_item = [];
        for (let index = 0; index < item.length; index++) {
            let element = item[index];
            if (element) {
                order_item.push(element)
            }
        }
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('UserUpdater').value,
            order_item: order_item
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/updateOrderItem', data, token);
            show(res);
        } catch (err) {
            show(err);
        }
    });

    // 刪除訂單
    document.getElementById('btnDelete').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            useruser: document.getElementById('UserDelete').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/deleteOrderItem', data, token);
            show(res);
        } catch (err) {
            show(err);
        }
    });

    // 歷史訂單
    document.getElementById('btnHistory').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/historyOrderItem', data, token);
            show(res);
        } catch (err) {
            show(err);
        }
    });

    const container = document.getElementById('inputContainer');
    const cntInput = document.getElementById('cnt');
    function renderInputs(count = 1) {
        container.innerHTML = '';
        for (let i = 1; i <= count; i++) {
            const input = document.createElement('input');
            input.id = 'order' + i;
            input.placeholder = '資料' + i;
            input.style.width = '300px';
            container.appendChild(input);
        }
    }
    renderInputs(1);
    document.getElementById('cnt').addEventListener('input', async () => {
        let cnt = cntInput.value;

        if (/^[1-9]\d*$/.test(cnt)) {
            cnt = Math.min(parseInt(cnt), 10);
            renderInputs(cnt);
        } else {
            cntInput.value = '';
            renderInputs(1);
        }

    });

    document.getElementById('token').addEventListener('input', async () => {
        let token = document.getElementById('token').value;
        setInputValue('token', [token]);
    });

});