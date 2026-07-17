// ==================== 全局状态 ====================
let token = '';
let currentPage = 'dashboard';
let currentUserPage = 0, currentSponsorPage = 0, currentPromptPage = 0, currentUsagePage = 0, currentConvPage = 0, currentAuditPage = 0;
let conversationChart = null, modelChart = null;

// ==================== 移动端菜单切换 ====================
document.addEventListener('DOMContentLoaded', function() {
    const menuToggle = document.getElementById('menuToggle');
    const sidebar = document.querySelector('.sidebar');
    if (menuToggle && sidebar) {
        menuToggle.addEventListener('click', function() {
            sidebar.classList.toggle('open');
        });
        // 点击主内容区关闭侧边栏
        document.querySelector('.main-area').addEventListener('click', function() {
            if (sidebar.classList.contains('open')) {
                sidebar.classList.remove('open');
            }
        });
        // 模型编辑按钮事件委托
        document.getElementById('modelTableBody').addEventListener('click', function(e) {
            const btn = e.target.closest('.model-edit-btn');
            if (btn) {
                openModelEditFromBtn(btn);
            }
        });
    }
});

// ==================== 工具函数 ====================
function showToast(msg, type = '') {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.className = 'toast ' + type + ' show';
    setTimeout(() => { t.className = 'toast'; }, 2500);
}

// 表格加载骨架屏
function showTableLoading(tbodyId, colSpan) {
    const tbody = document.getElementById(tbodyId);
    let rows = '';
    for (let i = 0; i < 4; i++) {
        rows += '<tr><td colspan="' + colSpan + '"><div class="skeleton-row"></div></td></tr>';
    }
    tbody.innerHTML = rows;
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
    // 通知服务端失效 token
    if (token) {
        fetch('/api/admin/logout', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        }).catch(() => {});
    }
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
        'prompt-audit': '提示词审核',
        models: '模型管理', prompts: '社区管理', usage: '消费记录', conversations: '聊天记录',
        apitest: '接口测试'
    };
    document.getElementById('pageTitle').textContent = titles[page] || page;
    currentPage = page;

    if (page === 'dashboard') loadDashboard();
    else if (page === 'users') loadUsers();
    else if (page === 'sponsors') loadSponsors();
    else if (page === 'prompt-audit') loadAudit();
    else if (page === 'models') loadModels();
    else if (page === 'prompts') loadPrompts();
    else if (page === 'rules') loadRules();
    else if (page === 'usage') loadUsage();
    else if (page === 'conversations') loadConversations();
    else if (page === 'apitest') resetApiTest();

    // 重新渲染 Lucide 图标
    if (typeof lucide !== 'undefined') lucide.createIcons();
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

        loadChartData(7);
    } catch (e) {
        showToast('加载仪表盘失败', 'error');
    }
}

async function loadChartData(days) {
    document.querySelectorAll('.time-range-btn').forEach(btn => {
        btn.classList.remove('active');
        if (parseInt(btn.textContent) === days) btn.classList.add('active');
    });

    try {
        const res = await api('/api/admin/dashboard/charts?days=' + days);
        const data = await res.json();
        renderConversationChart(data.dates, data.conversationCounts);
        renderModelChart(data.modelStats);
    } catch (e) {
        showToast('加载图表数据失败', 'error');
    }
}

function renderConversationChart(dates, counts) {
    const dom = document.getElementById('conversationChart');
    if (!dom) return;

    if (!conversationChart) {
        conversationChart = echarts.init(dom);
        window.addEventListener('resize', () => conversationChart?.resize());
    }

    const option = {
        backgroundColor: 'transparent',
        tooltip: {
            trigger: 'axis',
            backgroundColor: 'rgba(255,255,255,0.95)',
            borderColor: '#e8d5dc',
            borderWidth: 1,
            textStyle: { color: '#3d2c2f' },
            formatter: (params) => {
                const p = params[0];
                return `${p.name}<br/>对话数: <strong>${p.value}</strong>`;
            }
        },
        grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            top: '10%',
            containLabel: true
        },
        xAxis: {
            type: 'category',
            boundaryGap: false,
            data: dates,
            axisLine: { lineStyle: { color: '#d4c0c8' } },
            axisLabel: { color: '#8c7a82', fontSize: 12 },
            axisTick: { show: false }
        },
        yAxis: {
            type: 'value',
            axisLine: { show: false },
            axisLabel: { color: '#8c7a82', fontSize: 12 },
            splitLine: { lineStyle: { color: '#f0e4ea', type: 'dashed' } }
        },
        series: [{
            name: '对话数',
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 6,
            itemStyle: { color: '#d4839a' },
            lineStyle: { width: 3, color: '#d4839a' },
            areaStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: 'rgba(212,131,154,0.35)' },
                    { offset: 1, color: 'rgba(212,131,154,0.05)' }
                ])
            },
            data: counts
        }]
    };

    conversationChart.setOption(option);
}

function renderModelChart(modelStats) {
    const dom = document.getElementById('modelChart');
    if (!dom) return;

    if (!modelChart) {
        modelChart = echarts.init(dom);
        window.addEventListener('resize', () => modelChart?.resize());
    }

    const colors = ['#d4839a', '#ab47bc', '#f59e0b', '#22c55e', '#3b82f6', '#ec4899', '#8b5cf6', '#06b6d4'];
    const data = modelStats.map((m, i) => ({
        name: m.modelName,
        value: m.totalTokens,
        itemStyle: { color: colors[i % colors.length] }
    }));

    const option = {
        backgroundColor: 'transparent',
        tooltip: {
            trigger: 'item',
            backgroundColor: 'rgba(255,255,255,0.95)',
            borderColor: '#e8d5dc',
            borderWidth: 1,
            textStyle: { color: '#3d2c2f' },
            formatter: (params) => {
                const m = modelStats.find(s => s.modelName === params.name);
                return `${params.name}<br/>Token数: <strong>${params.value.toLocaleString()}</strong><br/>占比: <strong>${(m?.percentage || 0)}%</strong>`;
            }
        },
        legend: {
            orient: 'vertical',
            right: '5%',
            top: 'center',
            textStyle: { color: '#5a4650', fontSize: 13 },
            itemWidth: 14,
            itemHeight: 14
        },
        series: [{
            type: 'pie',
            radius: ['45%', '70%'],
            center: ['35%', '50%'],
            avoidLabelOverlap: true,
            itemStyle: {
                borderRadius: 8,
                borderColor: '#fff',
                borderWidth: 2
            },
            label: {
                show: false
            },
            emphasis: {
                label: {
                    show: true,
                    fontSize: 14,
                    fontWeight: 'bold',
                    color: '#3d2c2f'
                },
                itemStyle: {
                    shadowBlur: 10,
                    shadowOffsetX: 0,
                    shadowColor: 'rgba(0,0,0,0.2)'
                }
            },
            labelLine: {
                show: false
            },
            data: data
        }]
    };

    modelChart.setOption(option);
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
                <td>${escHtml(u.username)}</td>
                <td>${escHtml(u.email)}</td>
                <td>${escHtml(u.pid)}</td>
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
                    <td>${escHtml(o.orderNo)}</td>
                    <td>${escHtml(o.userPid || '-')}</td>
                    <td>${escHtml(o.userName || '-')}</td>
                    <td>${(o.amount || 0).toFixed(2)}</td>
                    <td>${o.sponsorImagePath
                        ? `<img src="${escHtml(o.sponsorImagePath)}" class="sponsor-img" onclick="previewImg('${escHtml(o.sponsorImagePath).replace(/'/g, "&#39;")}')">`
                        : '-'}</td>
                    <td><span class="tag tag-${o.reviewStatus === 'PENDING' ? 'pending' : o.reviewStatus === 'APPROVED' ? 'approved' : 'rejected'}">${o.reviewStatus === 'PENDING' ? '待审核' : o.reviewStatus === 'APPROVED' ? '已通过' : '已拒绝'}</span></td>
                    <td>${escHtml(o.reviewComment || '-')}</td>
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
                <td>${escHtml(m.displayName || '-')}</td>
                <td>${escHtml(m.modelName)}</td>
                <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${escHtml(m.apiUrl)}">${escHtml(m.apiUrl)}</td>
                <td>${(m.inputTokenPrice || 0).toFixed(6)}</td>
                <td>${(m.outputTokenPrice || 0).toFixed(6)}</td>
                <td>
                    <div class="action-btns">
                        <button class="btn btn-outline btn-sm model-edit-btn"
                                data-id="${m.id}"
                                data-display-name="${escHtml(m.displayName || '')}"
                                data-model-name="${escHtml(m.modelName)}"
                                data-api-url="${escHtml(m.apiUrl)}">编辑</button>
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

function openModelEditFromBtn(btn) {
    document.getElementById('modelModalTitle').textContent = '编辑模型';
    document.getElementById('modelEditId').value = btn.dataset.id;
    document.getElementById('modelDisplayName').value = btn.dataset.displayName || '';
    document.getElementById('modelModelName').value = btn.dataset.modelName || '';
    document.getElementById('modelApiUrl').value = btn.dataset.apiUrl || '';
    document.getElementById('modelApiKey').value = '';
    document.getElementById('modelInputPrice').value = '0.001';
    document.getElementById('modelOutputPrice').value = '0.002';
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
                    <td>${escHtml(p.name)}</td>
                    <td style="max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escHtml(p.content || '-')}</td>
                    <td>${escHtml(p.userName || p.userId)}</td>
                    <td>${p.likesCount || 0}</td>
                    <td><span class="tag ${p.featured ? 'tag-approved' : 'tag-pending'}">${p.featured ? '精选' : '普通'}</span></td>
                    <td>
                        <div class="action-btns">
                            <button class="btn btn-outline btn-sm" onclick="toggleFeatured(${p.id}, ${!p.featured})">${p.featured ? '取消精选' : '设为精选'}</button>
                            <button class="btn btn-outline btn-sm" onclick="unpublishPrompt(${p.id})">下架</button>
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

async function unpublishPrompt(id) {
    if (!confirm('确定要下架该提示词吗？下架后用户将无法查看。')) return;
    try {
        const res = await api('/api/admin/prompts-hub/' + id + '/unpublish', { method: 'POST' });
        if (res.ok) {
            showToast('提示词已下架', 'success');
            loadPrompts();
        } else {
            showToast('下架失败', 'error');
        }
    } catch (e) {
        showToast('下架失败', 'error');
    }
}

// ==================== 提示词审核 ====================
async function loadAudit(page = currentAuditPage) {
    currentAuditPage = page;
    const status = document.getElementById('auditStatusFilter').value;
    try {
        const res = await api('/api/admin/prompts-hub/audit?status=' + status + '&page=' + page + '&size=20');
        const data = await res.json();
        const tbody = document.getElementById('auditTableBody');
        if (!data.content || data.content.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="empty-state">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = data.content.map(p => `
                <tr>
                    <td>${p.id}</td>
                    <td>${p.imageUrl ? `<img src="${p.imageUrl}" alt="预览" style="width:60px;height:60px;object-fit:cover;border-radius:6px;cursor:pointer" onclick="window.open('${p.imageUrl}')" title="点击查看大图" />` : '<span style="color:var(--text-muted);font-size:12px">无</span>'}</td>
                    <td>${escHtml(p.name)}</td>
                    <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escHtml(p.description || '-')}</td>
                    <td>${escHtml(p.userName || p.userId)}</td>
                    <td>${formatDateTime(p.createdAt)}</td>
                    <td><span class="tag ${p.status === 'published' ? 'tag-approved' : p.status === 'rejected' ? 'tag-rejected' : 'tag-pending'}">${p.status === 'published' ? '已通过' : p.status === 'rejected' ? '已驳回' : '审核中'}</span></td>
                    <td>
                        <div class="action-btns">
                            ${p.status === 'pending_review' ? `
                                <button class="btn btn-success btn-sm" onclick="approveAudit(${p.id})">通过</button>
                                <button class="btn btn-danger btn-sm" onclick="rejectAudit(${p.id})">拒绝</button>
                            ` : '<span style="color:var(--text-muted);font-size:12px">-</span>'}
                        </div>
                    </td>
                </tr>`).join('');
        }
        renderPagination('auditPagination', data, page, loadAudit);
    } catch (e) {
        showToast('加载审核列表失败', 'error');
    }
}

async function approveAudit(id) {
    if (!confirm('确定要通过该提示词的审核吗？')) return;
    try {
        const res = await api('/api/admin/prompts-hub/' + id + '/approve', { method: 'POST' });
        if (res.ok) {
            showToast('已审核通过', 'success');
            loadAudit();
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

async function rejectAudit(id) {
    const reason = prompt('请输入拒绝原因（可选）：');
    if (reason === null) return; // 用户取消
    try {
        const res = await api('/api/admin/prompts-hub/' + id + '/reject', {
            method: 'POST',
            body: JSON.stringify({ reason: reason || '' })
        });
        if (res.ok) {
            showToast('已拒绝', 'success');
            loadAudit();
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败', 'error');
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
                    <td>${escHtml(u.modelName)}</td>
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
                    <td>${escHtml(c.title || '(无标题)')}</td>
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
                <div class="msg-content">${escHtml(m.userMessage || '')}</div>
            </div>
            <div class="chat-msg assistant">
                <div class="msg-role">AI 回复</div>
                <div class="msg-content">${escHtml(m.aiReply || '')}</div>
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

// ==================== 系统规则管理 ====================

async function loadRules() {
    try {
        const res = await fetch('/api/admin/system-rules', { headers: { 'Authorization': 'Bearer ' + token } });
        if (!res.ok) throw new Error('加载失败');
        const rules = await res.json();
        const tbody = document.getElementById('ruleTableBody');
        if (!rules || rules.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:20px">暂无系统规则</td></tr>';
            return;
        }
        tbody.innerHTML = rules.map(r => `
            <tr>
                <td>${r.sortOrder}</td>
                <td><strong>${escHtml(r.name)}</strong></td>
                <td><span class="badge ${r.isActive ? 'badge-success' : 'badge-danger'}">${r.isActive ? '启用' : '禁用'}</span></td>
                <td style="max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escHtml(r.content)}</td>
                <td>
                    <button class="btn btn-sm btn-outline" onclick="toggleRule(${r.id})">${r.isActive ? '禁用' : '启用'}</button>
                    <button class="btn btn-sm btn-outline" onclick="editRule(${r.id}, '${escHtml(r.name).replace(/'/g, "\\'")}', ${r.sortOrder}, ${r.isActive}, \`${escHtml(r.content).replace(/`/g, '\\`')}\`)">编辑</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteRule(${r.id})">删除</button>
                </td>
            </tr>`).join('');
    } catch (e) {
        showToast('加载系统规则失败: ' + e.message, 'error');
    }
}

function openRuleModal(editId) {
    document.getElementById('ruleEditId').value = editId || '';
    document.getElementById('ruleModalTitle').textContent = editId ? '编辑规则' : '新增规则';
    if (!editId) {
        document.getElementById('ruleName').value = '';
        document.getElementById('ruleSortOrder').value = '0';
        document.getElementById('ruleActive').value = 'true';
        document.getElementById('ruleContent').value = '';
    }
    document.getElementById('ruleModal').style.display = 'flex';
}

function closeRuleModal(e) {
    if (e && e.target !== document.getElementById('ruleModal')) return;
    document.getElementById('ruleModal').style.display = 'none';
}

async function editRule(id, name, sortOrder, isActive, content) {
    document.getElementById('ruleEditId').value = id;
    document.getElementById('ruleModalTitle').textContent = '编辑规则';
    document.getElementById('ruleName').value = name;
    document.getElementById('ruleSortOrder').value = sortOrder;
    document.getElementById('ruleActive').value = String(isActive);
    document.getElementById('ruleContent').value = content;
    document.getElementById('ruleModal').style.display = 'flex';
}

async function submitRule() {
    const id = document.getElementById('ruleEditId').value;
    const body = {
        name: document.getElementById('ruleName').value.trim(),
        content: document.getElementById('ruleContent').value.trim(),
        sortOrder: parseInt(document.getElementById('ruleSortOrder').value) || 0,
        isActive: document.getElementById('ruleActive').value === 'true'
    };
    if (!body.name || !body.content) {
        showToast('名称和内容不能为空', 'error');
        return;
    }
    try {
        const url = id ? `/api/admin/system-rules/${id}` : '/api/admin/system-rules';
        const method = id ? 'PUT' : 'POST';
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
            body: JSON.stringify(body)
        });
        if (res.ok) {
            closeRuleModal();
            loadRules();
            showToast(id ? '规则已更新' : '规则已创建');
        } else {
            const err = await res.json();
            showToast(err.message || '操作失败', 'error');
        }
    } catch (e) {
        showToast('操作失败: ' + e.message, 'error');
    }
}

async function toggleRule(id) {
    try {
        const res = await fetch(`/api/admin/system-rules/${id}/toggle`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            loadRules();
            showToast('状态已切换');
        }
    } catch (e) {
        showToast('操作失败', 'error');
    }
}

async function deleteRule(id) {
    if (!confirm('确定删除这条规则？')) return;
    try {
        const res = await fetch(`/api/admin/system-rules/${id}`, {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            loadRules();
            showToast('规则已删除');
        }
    } catch (e) {
        showToast('删除失败', 'error');
    }
}

function escHtml(s) {
    if (!s) return '';
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ==================== 接口测试 ====================
function resetApiTest() {
    document.getElementById('apiTestResult').innerHTML = `
        <div style="text-align:center;color:var(--text-muted);padding:40px">
            <i data-lucide="activity" style="width:48px;height:48px;margin-bottom:12px;opacity:0.3"></i>
            <p>点击「一键检测」按钮开始检查所有接口运行状态</p>
        </div>`;
    document.getElementById('apiTestSummary').textContent = '';
    lucide.createIcons();
}

async function runApiTest() {
    const btn = document.getElementById('apiTestBtn');
    const resultDiv = document.getElementById('apiTestResult');
    const summaryEl = document.getElementById('apiTestSummary');

    btn.disabled = true;
    btn.innerHTML = '<i data-lucide="loader" style="width:16px;height:16px;margin-right:4px;animation:spin 1s linear infinite"></i> 检测中...';
    lucide.createIcons();

    // 骨架屏
    resultDiv.innerHTML = Array(5).fill(
        '<div class="skeleton-row" style="height:52px;margin-bottom:4px"></div>'
    ).join('');
    summaryEl.textContent = '';

    try {
        const res = await fetch('/api/admin/api-test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }
        });
        const data = await res.json();

        const total = data.length;
        const passCount = data.filter(d => d.success).length;
        const failCount = total - passCount;
        const avgTime = Math.round(data.reduce((s, d) => s + d.timeMs, 0) / total);

        summaryEl.innerHTML = passCount === total
            ? '<span style="color:#22c55e;font-weight:600">全部通过 (' + total + '/' + total + ')</span>'
            : '<span style="color:#e74c3c;font-weight:600">通过 ' + passCount + '/' + total + '</span>'
            + ' <span style="color:var(--text-muted)">| 平均 ' + avgTime + 'ms</span>';

        // 分类标签
        let currentCategory = '';
        let html = '';
        for (const item of data) {
            if (item.category !== currentCategory) {
                currentCategory = item.category;
                html += '<div style="font-size:12px;font-weight:600;color:var(--text-muted);margin:8px 0 4px 4px;text-transform:uppercase">'
                    + (currentCategory === 'admin' ? '管理后台接口' : '用户端接口') + '</div>';
            }
            const statusColor = item.success ? '#22c55e' : '#e74c3c';
            const statusIcon = item.success ? 'check-circle' : 'x-circle';
            const statusLabel = item.success ? '正常' : '异常';
            const timeColor = item.timeMs < 200 ? '#22c55e' : item.timeMs < 500 ? '#f59e0b' : '#e74c3c';

            html += `
                <div style="display:flex;align-items:center;padding:10px 14px;background:#fefafb;border-radius:10px;
                    border:1px solid ${item.success ? '#e8f5e9' : '#fce4e4'};gap:12px">
                    <i data-lucide="${statusIcon}" style="width:20px;height:20px;color:${statusColor};flex-shrink:0"></i>
                    <div style="flex:1;min-width:0">
                        <div style="font-size:14px;font-weight:500;color:#3d2c2f">${escHtml(item.name)}</div>
                        <div style="font-size:12px;color:var(--text-muted);margin-top:2px">
                            ${item.method} ${escHtml(item.url)}
                        </div>
                    </div>
                    <div style="text-align:right;flex-shrink:0">
                        <span style="font-size:12px;font-weight:600;color:${statusColor}">${statusLabel}</span>
                        <span style="font-size:12px;color:${timeColor};margin-left:2px">${item.status}</span>
                    </div>
                    <div style="text-align:right;flex-shrink:0;min-width:70px">
                        <span style="font-size:13px;font-weight:600;color:${timeColor}">${item.timeMs}ms</span>
                        ${item.detail ? '<div style="font-size:11px;color:var(--text-muted)">' + escHtml(item.detail) + '</div>' : ''}
                    </div>
                </div>`;
        }
        resultDiv.innerHTML = html;
    } catch (e) {
        summaryEl.innerHTML = '<span style="color:#e74c3c;font-weight:600">请求失败</span>';
        resultDiv.innerHTML = `
            <div style="text-align:center;padding:40px;color:#e74c3c">
                <i data-lucide="alert-triangle" style="width:40px;height:40px;margin-bottom:12px"></i>
                <p>测试请求失败: ${escHtml(e.message || '未知错误')}</p>
            </div>`;
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i data-lucide="play" style="width:16px;height:16px;margin-right:4px"></i> 一键检测';
        lucide.createIcons();
    }
}

// ==================== 初始加载 ====================
// 检查是否已有登录态（URL中带token或者localStorage）
(function init() {
    // 纯前端管理后台，始终从登录开始
    document.getElementById('loginPage').style.display = 'flex';
    document.getElementById('adminLayout').style.display = 'none';
})();
