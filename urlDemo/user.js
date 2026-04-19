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
    const res = await api.get('user/testLogin', null);
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

function show_products(log = {}) {
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
    document.getElementById('products_result').innerHTML = JSON.stringify(messageLog, null, 2);
    document.getElementById("car_result").innerHTML = "";
}

function show_car(log = {}) {
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
    document.getElementById('car_result').innerHTML = JSON.stringify(messageLog, null, 2);
    document.getElementById("products_result").innerHTML = "";
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
    document.getElementById('quotations_user_result').innerHTML = JSON.stringify(messageLog, null, 2);
    document.getElementById("products_result").innerHTML = "";
}

function show_Shipments(log = {}) {
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
    document.getElementById('shipments_result').innerHTML = JSON.stringify(messageLog, null, 2);
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
            const res = await api.post('user/takeToken', data);
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
            const res = await api.post('user/validate', data);
            show_user(res);
            if (res.message.token) {
                let token = res.message.token["1"];
                if (token) {
                    setInputValue('token', token);
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
            const res = await api.post('user/logout', data, token);
            show_user(res);
        } catch (err) {
            show_user(err);
        }
        setInputValue('token', "");
    });

    // 查詢
    document.getElementById('productsCarSelect').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            product_id: document.getElementById('product_id_SelectCar').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/productsCarSelect', data, token);
            show_products(res);
        } catch (err) {
            show_products(err);
        }
    });

    // 新增購物車
    document.getElementById('btnCreateCar').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            product_id: document.getElementById('product_id_CreateCar').value,
            product_quantity: document.getElementById('product_quantity_CreateCar').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/createCarItem', data, token);
            show_car(res);
        } catch (err) {
            show_car(err);
        }
    });

    // 查詢購物車
    document.getElementById('btnQueryCar').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/queryCarItem', data, token);
            show_car(res);
        } catch (err) {
            show_car(err);
        }
    });

    // 更改購物車
    document.getElementById('btnUpdateCar').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            product_id: document.getElementById('product_id_UpdateCar').value,
            product_quantity: document.getElementById('product_quantity_UpdateCar').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/updateCarItem', data, token);
            show_car(res);
        } catch (err) {
            show_car(err);
        }
    });

    // 刪除購物車
    document.getElementById('btnDeleteCar').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            product_id: document.getElementById('product_id_DeleteCar').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/deleteCarItem', data, token);
            show_car(res);
        } catch (err) {
            show_car(err);
        }
    });

    // 確認訂單
    document.getElementById('btnConfirm').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/confirmItem', data, token);
            show_car(res);
        } catch (err) {
            show_car(err);
        }
    });

    // 報價單編號
    document.getElementById('quotationsProductId').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/quotationsProductId', data, token);
            show_quotations(res);
        } catch (err) {
            show_quotations(err);
        }
    });

    // 報價單
    document.getElementById('quotationsProduct').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            quotation_id: document.getElementById('user_quotationsProduct').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/quotationsProduct', data, token);
            show_quotations(res);
        } catch (err) {
            show_quotations(err);
        }
    });

    // 接受
    document.getElementById('user_accepted').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            quotation_id: document.getElementById('user_quotationsProduct').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/userAccepted', data, token);
            show_quotations(res);
        } catch (err) {
            show_quotations(err);
        }
    });

    // 拒絕
    document.getElementById('user_rejected').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            quotation_id: document.getElementById('user_quotationsProduct').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/userRejected', data, token);
            show_quotations(res);
        } catch (err) {
            show_quotations(err);
        }
    });

    // 查詢出貨資訊
    document.getElementById('user_shipments').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            orderId: document.getElementById('user_shipmentsId').value,
            trackingNumber: document.getElementById('user_shipmentsTrackingNumber').value,
            datePart: document.getElementById('user_shipmentsDatePart').value,
            shipmentsStatus: document.getElementById('user_shipmentsStatus').value,
            paymentsStatus: document.getElementById('user_paymentsStatus').value,
            paymentsMethod: document.getElementById('user_paymentsMethod').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/userShipments', data, token);
            show_Shipments(res);
        } catch (err) {
            show_Shipments(err);
        }
    });

    // 查詢付款資訊
    document.getElementById('user_payments').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            trackingNumber: document.getElementById('user_paymentsTrackingNumber').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/userPayments', data, token);
            show_Shipments(res);
        } catch (err) {
            show_Shipments(err);
        }
    });

    // 付款
    document.getElementById('user_payMoney').addEventListener('click', async () => {
        const data = {
            username: document.getElementById('loginUser').value,
            trackingNumber: document.getElementById('user_paymentsTrackingNumber').value,
            amount: document.getElementById('user_paymentsAmount').value,
            paymentsMethod: document.getElementById('user_paymentsPaymentsMethod').value
        };
        try {
            let token = document.getElementById('token').value
            const res = await api.post('user/userPayMoney', data, token);
            show_Shipments(res);
        } catch (err) {
            show_Shipments(err);
        }
    });

});