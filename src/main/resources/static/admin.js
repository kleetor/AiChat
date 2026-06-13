// ==================== 全局状态 ====================
let token = '';
let currentPage = 'dashboard';
let currentUserPage = 0, currentSponsorPage = 0, currentPromptPage = 0, currentUsagePage = 0, currentConvPage = 0;

// ==================== 工具函数 ====================
function showToast(msg, type = '') {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.className = 'toast ' + type + ' show';
    setTimeout(() => { t.className = 'toast'; }, 2500);
}

function maskApiKey(key) {
    if (!key || key.length <= 8) return '****';
    return key.substring(0, 4) + '****' + key.substring(key.length - 4);
}

function formatDateTime(dt) {
    if (!dt) return '-';
    const d = new Date(dt);
    const pad = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate()) +
        ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
}

function formatDate(dt) {
    if (!dt) return '-';
    const d = new Date(dt);
    const pad = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate());
}

function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    return fetch(path, { ...options, headers: { ...headers, ...options.headers } });
}

// ==================== 登录 / 登出 ====================
async function doLogin() {
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value.trim();
    const errorEl = document.getElementById('loginError');
    const btn = document.getElementById('loginBtn');

    if (!username || !password) {
        errorEl.textContent = '请输入用户名和密码';
        errorEl.classList.add('show');
        return;
    }

    btn.disabled = true;
    btn.textContent = '登录中...';
    errorEl.classList.remove('show');

    try {
        const res = await fetch('/api/admin/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        if (res.ok) {
            token = data.token;
            document.getElementById('adminName').textContent = data.username;
            document.getElementById('adminUserInfo').textContent = '管理员: ' + data.username;
            document.getElementById('loginPage').style.display = 'none';
            document.getElementById('adminLayout').style.display = 'flex';
            loadDashboard();
        } else {
            errorEl.textContent = data.error || data.message || '登录失败';
            errorEl.classList.add('show');
        }
    } catch (e) {
        errorEl.textContent = '网络错误，请稍后重试';
        errorEl.classList.add('show');
    } finally {
        btn.disabled = false;
        btn.textContent = '登 录';
    }
}

function doLogout() {
    token = '';
    document.getElementById('loginPage').style.display = 'flex';
    document.getElementById('adminLayout').style.display = 'none';
    document.getElementById('loginUsername').value = '';
    document.getElementById('loginPassword').value = '';
}

// 回车登录
document.getElementById('loginPassword').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') doLogin();
});

// ==================== 页面切换 ====================
function switchPage(page, el) {
    document.querySelectorAll('.sidebar-nav a').forEach(a => a.classList.remove('active'));
    el.classList.add('active');
    document.querySelectorAll('.page-content').forEach(p => p.style.display = 'none');
    document.getElementById('page-' + page).style.display = 'block';

    const titles = {
        dashboard: '仪表盘', users: '用户管理', sponsors: '赞助审核',
        models: '模型管理', prompts: '社区管理', usage: '消费记录', conversations: '聊天记录'
    };
    document.getElementById('pageTitle').textContent = titles[page] || page;
    currentPage = page;

    if (page === 'dashboard') loadDashboard();
    else if (page === 'users') loadUsers();
    else if (page === 'sponsors') loadSponsors();
    else if (page === 'models') loadModels();
    else if (page === 'prompts') loadPrompts();
    else if (page === 'usage') loadUsage();
    else if (page === 'conversations') loadConversations();
}

// ==================== 仪表盘 ====================
async function loadDashboard() {
    try {
        const res = await api('/api/admin/dashboard');
        const data = await res.json();
        document.getElementById('statTotalUsers').textContent = data.totalUsers || 0;
        document.getElementById('statTodayUsers').textContent = data.todayNewUsers || 0;
        document.getElementById('statPendingReviews').textContent = data.pendingReviews || 0;
        document.getElementById('statTotalRevenue').textContent = (data.totalRevenue || 0).toFixed(4);
        document.getElementById('statConversations').textContent = data.totalConversations || 0;
        document.getElementById('statMessages').textContent = data.totalMessages || 0;
        document.getElementById('statTodayMsg').textContent = data.todayMessages || 0;
    } catch (e) {
        showToast('加载仪表盘失败', 'error');
    }
}

// ==================== 用户管理 ====================
async function loadUsers(page = currentUserPage) {
    currentUserPage = page;
    const keyword = document.getElementById('userSearchInput').value.trim();
    try {
        const res = await api('/api/admin/users?page=' + page + '&size=20&keyword=' + encodeURIComponent(keyword) + '&sortBy=id&order=desc');
        const data = await res.json();
        const tbody = document.getElementById('userTableBody');
        tbody.innerHTML = data.content.map(u => `
            <tr>
                <td>${u.id}</td>
                <td>${u.username}</td>
                <td>${u.email}</td>
                <td>${u.pid}</td>
                <td>${(u.balance || 0).toFixed(4)}</td>
                <td><span class="tag tag-${u.role === 'ADMIN' ? 'admin' : 'user'}">${u.role}</span></td>
                <td><span class="tag ${u.enabled ? 'tag-approved' : 'tag-rejected'}">${u.enabled ? '正常' : '禁用'}</span></td>
                <td>
                    <div class="action-btns">
                        <button class="btn btn-outline btn-sm" onclick="openBalanceModal(${u.id})">余额</button>
                        <button class="btn btn-outline btn-sm" onclick="openRoleModal(${u.id}, '${u.role}')">角色</button>
                        <button class="btn btn-sm ${u.enabled ? 'btn-danger' : 'btn-success'}" onclick="toggleUserStatus(${u.id}, ${!u.enabled})">${u.enabled ? '禁用' : '启用'}</button>
                    </div>
                </td>
            </tr>`).join('');
        renderPagination('userPagination', data, page, loadUsers);
    } catch (e) {
        showToast('加载用户列表失败', 'error');
    }
}

async function openBalanceModal(userId) {
    document.getElementById('balanceModal').classList.add('show');
    document.getElementById('balanceModal').dataset.userId = userId;
    document.getElementById('balanceAmount').value = '';
    document.getElementById('balanceReason').value = '';
}

function closeBalanceModal(e) {
    if (e && e.target !== document.getElementById('balanceModal')) return;
    document.getElementById('balanceModal').classList.remove('show');
}

async function submitBalance() {
    const userId = document.getElementById('balanceModal').dataset.userId;
    const amount = document.getElementById('balanceAmount').value;
    const reason = document.getElementById('balanceReason').value || '管理员手动操作';
    if (!amount) { showToast('请输入金额', 'error'); return; }
    try {
        const res = await api('/api/admin/users/' + userId + '/balance', {
            method: 'PUT',
            body: JSON.stringify({ amount: parseFloat(amount), reason })
        });
        if (res.ok) {
            showToast('余额更新成功', 'success');
            closeBalanceModal();
            loadUsers();
        } else {
            const d = await res.json();
            showToast(d.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

async function openRoleModal(userId, currentRole) {
    document.getElementById('roleModal').classList.add('show');
    document.getElementById('roleModal').dataset.userId = userId;
    document.getElementById('roleSelect').value = currentRole;
}

function closeRoleModal(e) {
    if (e && e.target !== document.getElementById('roleModal')) return;
    document.getElementById('roleModal').classList.remove('show');
}

async function submitRole() {
    const userId = document.getElementById('roleModal').dataset.userId;
    const role = document.getElementById('roleSelect').value;
    try {
        const res = await api('/api/admin/users/' + userId + '/role', {
            method: 'PUT',
            body: JSON.stringify({ role })
        });
        if (res.ok) {
            showToast('角色更新成功', 'success');
            closeRoleModal();
            loadUsers();
        } else {
            const d = await res.json();
            showToast(d.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

async function toggleUserStatus(userId, enabled) {
    if (!confirm('确定要' + (enabled ? '启用' : '禁用') + '该用户吗？')) return;
    try {
        const res = await api('/api/admin/users/' + userId + '/status', {
            method: 'PUT',
            body: JSON.stringify({ enabled })
        });
        if (res.ok) {
            showToast('状态更新成功', 'success');
            loadUsers();
        } else {
            const d = await res.json();
            showToast(d.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

// ==================== 赞助审核 ====================
async function loadSponsors(page = currentSponsorPage) {
    currentSponsorPage = page;
    const status = document.getElementById('sponsorStatusFilter').value;
    try {
        const res = await api('/api/admin/sponsor-reviews?page=' + page + '&size=20&status=' + status);
        const data = await res.json();
        const tbody = document.getElementById('sponsorTableBody');

        if (!data.content || data.content.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" class="empty-state">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = data.content.map(o => `
                <tr>
                    <td>${o.orderNo}</td>
                    <td>${o.userPid || '-'}</td>
                    <td>${o.userName || '-'}</td>
                    <td>${(o.amount || 0).toFixed(2)}</td>
                    <td>${o.sponsorImagePath
                        ? `<img src="${o.sponsorImagePath}" class="sponsor-img" onclick="previewImg('${o.sponsorImagePath}')">`
                        : '-'}</td>
                    <td><span class="tag tag-${o.reviewStatus === 'PENDING' ? 'pending' : o.reviewStatus === 'APPROVED' ? 'approved' : 'rejected'}">${o.reviewStatus === 'PENDING' ? '待审核' : o.reviewStatus === 'APPROVED' ? '已通过' : '已拒绝'}</span></td>
                    <td>${o.reviewComment || '-'}</td>
                    <td>${formatDateTime(o.createdAt)}</td>
                    <td>
                        ${o.reviewStatus === 'PENDING' ? `
                        <div class="action-btns">
                            <button class="btn btn-success btn-sm" onclick="openApproveModal(${o.id})">通过</button>
                            <button class="btn btn-danger btn-sm" onclick="openRejectModal(${o.id})">拒绝</button>
                        </div>` : '<span style="color:#999">已审核</span>'}
                    </td>
                </tr>`).join('');
        }
        renderPagination('sponsorPagination', data, page, loadSponsors);
    } catch (e) {
        showToast('加载赞助列表失败', 'error');
        document.getElementById('sponsorTableBody').innerHTML = '<tr><td colspan="9" class="empty-state">加载失败</td></tr>';
    }
}

function openApproveModal(orderId) {
    document.getElementById('approveModal').classList.add('show');
    document.getElementById('approveModal').dataset.orderId = orderId;
    document.getElementById('approveAmount').value = '';
    document.getElementById('approveComment').value = '';
}

function closeApproveModal(e) {
    if (e && e.target !== document.getElementById('approveModal')) return;
    document.getElementById('approveModal').classList.remove('show');
}

async function submitApprove() {
    const orderId = document.getElementById('approveModal').dataset.orderId;
    const tokens = document.getElementById('approveAmount').value;
    const comment = document.getElementById('approveComment').value;
    if (!tokens) { showToast('请输入发放金额', 'error'); return; }
    try {
        const res = await api('/api/admin/sponsor-reviews/' + orderId + '/approve', {
            method: 'PUT',
            body: JSON.stringify({ tokens: parseFloat(tokens), comment })
        });
        if (res.ok) {
            showToast('审核通过', 'success');
            closeApproveModal();
            loadSponsors();
        } else {
            const d = await res.json();
            showToast(d.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

function openRejectModal(orderId) {
    document.getElementById('rejectModal').classList.add('show');
    document.getElementById('rejectModal').dataset.orderId = orderId;
    document.getElementById('rejectComment').value = '';
}

function closeRejectModal(e) {
    if (e && e.target !== document.getElementById('rejectModal')) return;
    document.getElementById('rejectModal').classList.remove('show');
}

async function submitReject() {
    const orderId = document.getElementById('rejectModal').dataset.orderId;
    const comment = document.getElementById('rejectComment').value;
    try {
        const res = await api('/api/admin/sponsor-reviews/' + orderId + '/reject', {
            method: 'PUT',
            body: JSON.stringify({ comment })
        });
        if (res.ok) {
            showToast('已拒绝', 'success');
            closeRejectModal();
            loadSponsors();
        } else {
            const d = await res.json();
            showToast(d.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

// ==================== 模型管理 ====================
async function loadModels() {
    try {
        const res = await api('/api/admin/model-configs');
        const data = await res.json();
        document.getElementById('modelTableBody').innerHTML = data.map(m => `
            <tr>
                <td>${m.displayName || '-'}</td>
                <td>${m.modelName}</td>
                <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${m.apiUrl}">${m.apiUrl}</td>
                <td class="masked-key">${maskApiKey(m.apiKey)}</td>
                <td>${(m.inputTokenPrice || 0).toFixed(6)}</td>
                <td>${(m.outputTokenPrice || 0).toFixed(6)}</td>
                <td>
                    <div class="action-btns">
                        <button class="btn btn-outline btn-sm" onclick='openModelEditModal(${JSON.stringify(m).replace(/'/g, "&#39;")})'>编辑</button>
                        <button class="btn btn-danger btn-sm" onclick="deleteModel(${m.id})">删除</button>
                    </div>
                </td>
            </tr>`).join('');
    } catch (e) {
        showToast('加载模型配置失败', 'error');
    }
}

function openModelModal() {
    document.getElementById('modelModalTitle').textContent = '新增模型';
    document.getElementById('modelEditId').value = '';
    document.getElementById('modelDisplayName').value = '';
    document.getElementById('modelModelName').value = '';
    document.getElementById('modelApiUrl').value = '';
    document.getElementById('modelApiKey').value = '';
    document.getElementById('modelInputPrice').value = '0.001';
    document.getElementById('modelOutputPrice').value = '0.002';
    document.getElementById('modelModal').classList.add('show');
}

function openModelEditModal(m) {
    document.getElementById('modelModalTitle').textContent = '编辑模型';
    document.getElementById('modelEditId').value = m.id;
    document.getElementById('modelDisplayName').value = m.displayName || '';
    document.getElementById('modelModelName').value = m.modelName || '';
    document.getElementById('modelApiUrl').value = m.apiUrl || '';
    document.getElementById('modelApiKey').value = m.apiKey || '';
    document.getElementById('modelInputPrice').value = m.inputTokenPrice || 0.001;
    document.getElementById('modelOutputPrice').value = m.outputTokenPrice || 0.002;
    document.getElementById('modelModal').classList.add('show');
}

function closeModelModal(e) {
    if (e && e.target !== document.getElementById('modelModal')) return;
    document.getElementById('modelModal').classList.remove('show');
}

async function submitModel() {
    const id = document.getElementById('modelEditId').value;
    const body = {
        displayName: document.getElementById('modelDisplayName').value,
        modelName: document.getElementById('modelModelName').value,
        apiUrl: document.getElementById('modelApiUrl').value,
        apiKey: document.getElementById('modelApiKey').value,
        inputTokenPrice: parseFloat(document.getElementById('modelInputPrice').value) || 0.001,
        outputTokenPrice: parseFloat(document.getElementById('modelOutputPrice').value) || 0.002
    };
    try {
        const url = id ? '/api/admin/model-configs/' + id : '/api/admin/model-configs';
        const method = id ? 'PUT' : 'POST';
        const res = await api(url, { method, body: JSON.stringify(body) });
        if (res.ok) {
            showToast(id ? '模型更新成功' : '模型创建成功', 'success');
            closeModelModal();
            loadModels();
        } else {
            const d = await res.json();
            showToast(d.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

async function deleteModel(id) {
    if (!confirm('确定要删除该模型配置吗？')) return;
    try {
        const res = await api('/api/admin/model-configs/' + id, { method: 'DELETE' });
        if (res.ok) {
            showToast('模型已删除', 'success');
            loadModels();
        } else {
            const d = await res.json();
            showToast(d.message || '删除失败', 'error');
        }
    } catch (e) {
        showToast('删除失败', 'error');
    }
}

// ==================== 社区管理 ====================
async function loadPrompts(page = currentPromptPage) {
    currentPromptPage = page;
    const keyword = document.getElementById('promptSearchInput').value.trim();
    try {
        const res = await api('/api/admin/prompts-hub?page=' + page + '&size=20&keyword=' + encodeURIComponent(keyword));
        const data = await res.json();
        const tbody = document.getElementById('promptTableBody');
        if (!data.content || data.content.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty-state">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = data.content.map(p => `
                <tr>
                    <td>${p.id}</td>
                    <td>${p.name}</td>
                    <td style="max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${p.content || '-'}</td>
                    <td>${p.userName || p.userId}</td>
                    <td>${p.likesCount || 0}</td>
                    <td><span class="tag ${p.featured ? 'tag-approved' : 'tag-pending'}">${p.featured ? '精选' : '普通'}</span></td>
                    <td>
                        <div class="action-btns">
                            <button class="btn btn-outline btn-sm" onclick="toggleFeatured(${p.id}, ${!p.featured})">${p.featured ? '取消精选' : '设为精选'}</button>
                            <button class="btn btn-danger btn-sm" onclick="deletePrompt(${p.id})">删除</button>
                        </div>
                    </td>
                </tr>`).join('');
        }
        renderPagination('promptPagination', data, page, loadPrompts);
    } catch (e) {
        showToast('加载提示词列表失败', 'error');
    }
}

async function toggleFeatured(id, featured) {
    try {
        const res = await api('/api/admin/prompts-hub/' + id + '/feature', {
            method: 'PUT',
            body: JSON.stringify({ featured })
        });
        if (res.ok) {
            showToast(featured ? '已设为精选' : '已取消精选', 'success');
            loadPrompts();
        } else {
            showToast('操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

async function deletePrompt(id) {
    if (!confirm('确定要删除该提示词吗？')) return;
    try {
        const res = await api('/api/admin/prompts-hub/' + id, { method: 'DELETE' });
        if (res.ok) {
            showToast('提示词已删除', 'success');
            loadPrompts();
        } else {
            showToast('删除失败', 'error');
        }
    } catch (e) {
        showToast('删除失败', 'error');
    }
}

// ==================== 消费记录 ====================
async function loadUsage(page = currentUsagePage) {
    currentUsagePage = page;
    const userId = document.getElementById('usageUserId').value.trim();
    const startDate = document.getElementById('usageStartDate').value;
    const endDate = document.getElementById('usageEndDate').value;
    let url = '/api/admin/usage-records?page=' + page + '&size=20';
    if (userId) url += '&userId=' + userId;
    if (startDate) url += '&startDate=' + startDate + 'T00:00:00';
    if (endDate) url += '&endDate=' + endDate + 'T23:59:59';
    try {
        const res = await api(url);
        const data = await res.json();
        const tbody = document.getElementById('usageTableBody');
        if (!data.content || data.content.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="empty-state">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = data.content.map(u => `
                <tr>
                    <td>${u.userId}</td>
                    <td>${u.modelName}</td>
                    <td>${u.inputTokens || 0}</td>
                    <td>${u.outputTokens || 0}</td>
                    <td class="amount-positive">${(u.costAmount || 0).toFixed(6)}</td>
                    <td>${formatDateTime(u.createdAt)}</td>
                </tr>`).join('');
        }
        renderPagination('usagePagination', data, page, loadUsage);
    } catch (e) {
        showToast('加载消费记录失败', 'error');
    }
}

// ==================== 聊天记录 ====================
async function loadConversations(page = currentConvPage) {
    currentConvPage = page;
    const userId = document.getElementById('convUserId').value.trim();
    let url = '/api/admin/conversations?page=' + page + '&size=20';
    if (userId) url += '&userId=' + userId;
    try {
        const res = await api(url);
        const data = await res.json();
        const tbody = document.getElementById('convTableBody');
        if (!data.content || data.content.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="empty-state">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = data.content.map(c => `
                <tr>
                    <td>${c.id}</td>
                    <td>${c.title || '(无标题)'}</td>
                    <td>${formatDateTime(c.createdAt)}</td>
                    <td><button class="btn btn-outline btn-sm" onclick="viewChatDetail(${c.id})">查看详情</button></td>
                </tr>`).join('');
        }
        renderPagination('convPagination', data, page, loadConversations);
    } catch (e) {
        showToast('加载会话列表失败', 'error');
    }
}

async function viewChatDetail(convId) {
    try {
        const res = await api('/api/admin/conversations/' + convId + '/messages');
        const data = await res.json();
        const content = document.getElementById('chatDetailContent');
        content.innerHTML = data.map(m => `
            <div class="chat-msg user">
                <div class="msg-role">用户</div>
                <div class="msg-content">${m.userMessage || ''}</div>
            </div>
            <div class="chat-msg assistant">
                <div class="msg-role">AI 回复</div>
                <div class="msg-content">${m.aiReply || ''}</div>
            </div>`).join('');
        document.getElementById('chatDetailModal').classList.add('show');
    } catch (e) {
        showToast('加载聊天详情失败', 'error');
    }
}

function closeChatDetail(e) {
    if (e && e.target !== document.getElementById('chatDetailModal')) return;
    document.getElementById('chatDetailModal').classList.remove('show');
}

// ==================== 图片预览 ====================
function previewImg(src) {
    document.getElementById('imgPreviewSrc').src = src;
    document.getElementById('imgPreview').style.display = 'flex';
}

// ==================== 分页渲染 ====================
function renderPagination(id, data, currentPage, loadFn) {
    const el = document.getElementById(id);
    if (!data || data.totalPages <= 1) {
        el.innerHTML = '';
        return;
    }
    const total = data.totalPages;
    let html = '<button ' + (data.first ? 'disabled' : '') + ' onclick="' + loadFn.name + '(' + (currentPage - 1) + ')">上一页</button>';
    html += '<span class="page-info">' + (currentPage + 1) + ' / ' + total + '</span>';
    html += '<button ' + (data.last ? 'disabled' : '') + ' onclick="' + loadFn.name + '(' + (currentPage + 1) + ')">下一页</button>';
    el.innerHTML = html;
}

// ==================== 初始加载 ====================
// 检查是否已有登录态（URL中带token或者localStorage）
(function init() {
    // 纯前端管理后台，始终从登录开始
    document.getElementById('loginPage').style.display = 'flex';
    document.getElementById('adminLayout').style.display = 'none';
})();
