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
    const res = await api.get('products/testLogin', null);
    show(res);
    // tableFormat(res)
}

// function tableFormat(res) {
//     let htmlHead = "";
//     htmlHead += "<thead>";
//     htmlHead += "<tr>";
//     htmlHead += `<td>${"code"}</td>`;
//     htmlHead += `<td>${res.code}</td>`;
//     htmlHead += "</tr>";
//     htmlHead += "<tr>";
//     htmlHead += `<td>${"status"}</td>`;
//     htmlHead += `<td>${res.status}</td>`;
//     htmlHead += "</tr>";
//     htmlHead += "</tr></thead>";
//     document.getElementById('products_code').innerHTML = htmlHead;
    
//     let html = "";
//     // 表頭
//     html += "<thead><tr>";
//     Object.keys(res.data[0]).forEach(key => {
//         if (key.includes("_name")) {
//             html += `<th>${res.data[0][key]}</th>`;
//         }
//     });
//     html += "</tr></thead>";
//     // 表身
//     html += "<tbody>";
//     res.data.forEach(row => {
//         html += "<tr>";
//         Object.keys(row).forEach(key => {
//             if (!key.includes("_name")) {
//                 html += `<td>${row[key]}</td>`;
//             }
//         });
//         html += "</tr>";
//     });
//     html += "</tbody>";
    
//     document.getElementById('products_table').innerHTML = html;
//     document.getElementById('products_timestamp').innerHTML = `<h3>${res.timestamp}</h3>`;
// }

// function escapeHTML(str) {
//     return String(str)
//         .replace(/&/g, "&amp;")
//         .replace(/</g, "&lt;")
//         .replace(/>/g, "&gt;")
//         .replace(/"/g, "&quot;")
//         .replace(/'/g, "&#039;");
// }

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

document.addEventListener('DOMContentLoaded', () => {

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
        } catch (err) {
            show(err);
        }
    });

    // 查詢
    document.getElementById('select').addEventListener('click', async () => {
        const data = {
            product_id: document.getElementById('product_id_select').value
        };
        try {
            const res = await api.post('products/select', data);
            show(res);
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
        } catch (err) {
            show(err);
        }
    });

});