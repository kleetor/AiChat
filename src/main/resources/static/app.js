
    // ======== 全局状态 ========
    const API = ''; // 同源
    let token = localStorage.getItem('chat_token') || '';
    let username = localStorage.getItem('chat_username') || '';
    let currentConvId = null;          // 当前选中的会话ID
    let currentPromptId = null;        // 当前选中的提示词ID

    // DOM 元素
    const userDisplay = document.getElementById('userDisplay');
    const btnLogin = document.getElementById('btnLogin');
    const btnLogout = document.getElementById('btnLogout');
    const convList = document.getElementById('convList');
    const newConvBtn = document.getElementById('newConvBtn');
    const msgContainer = document.getElementById('msgContainer');
    const userInput = document.getElementById('userInput');
    const sendBtn = document.getElementById('sendBtn');
    const btnPrompt = document.getElementById('btnPrompt');
    const currentPromptIndicator = document.getElementById('currentPromptIndicator');
    const promptNameDisplay = document.getElementById('promptNameDisplay');
    const removePromptBtn = document.getElementById('removePromptBtn');

    // 模态框元素
    const authModal = document.getElementById('authModal');
    const modalTitle = document.getElementById('modalTitle');
    const modalUsername = document.getElementById('modalUsername');
    const modalPassword = document.getElementById('modalPassword');
    const modalSubmit = document.getElementById('modalSubmit');
    const modalError = document.getElementById('modalError');
    const switchText = document.getElementById('switchText');
    const switchLink = document.getElementById('switchLink');
    const promptModal = document.getElementById('promptModal');
    const promptList = document.getElementById('promptList');
    const newPromptBtn = document.getElementById('newPromptBtn');
    const closePromptModalBtn = document.getElementById('closePromptModalBtn');
    const editPromptModal = document.getElementById('editPromptModal');
    const editPromptTitle = document.getElementById('editPromptTitle');
    const editPromptName = document.getElementById('editPromptName');
    const editPromptContent = document.getElementById('editPromptContent');
    const savePromptBtn = document.getElementById('savePromptBtn');
    const cancelEditPrompt = document.getElementById('cancelEditPrompt');

    let isLoginMode = true;
    let editingPromptId = null;

    // ======== 初始化 ========
    function init() {
        if (token) {
            showLoggedIn(username);
            loadConversations();
            loadPromptsSilent(); // 加载但不显示，只用于恢复已选的提示词名称
            restorePromptIndicator();
        } else {
            showLoggedOut();
        }
    }

    function restorePromptIndicator() {
        const savedPromptId = localStorage.getItem('current_prompt_id');
        if (savedPromptId) {
            currentPromptId = parseInt(savedPromptId);
            // 名称将在 loadPromptsSilent 后更新
        }
    }

    // ======== UI 状态 ========
    function showLoggedIn(name) {
        username = name;
        localStorage.setItem('chat_username', name);
        userDisplay.textContent = `👤 ${name}`;
        btnLogin.style.display = 'none';
        btnLogout.style.display = 'inline-block';
        if (currentConvId) {
            enableInput(true);
        } else {
            enableInput(false);
        }
    }

    function showLoggedOut() {
        username = '';
        localStorage.removeItem('chat_username');
        localStorage.removeItem('chat_token');
        localStorage.removeItem('current_prompt_id');
        token = '';
        currentConvId = null;
        currentPromptId = null;
        currentPromptIndicator.style.display = 'none';
        userDisplay.textContent = '';
        btnLogin.style.display = 'inline-block';
        btnLogout.style.display = 'none';
        userInput.disabled = true;
        sendBtn.disabled = true;
        convList.innerHTML = '<div class="no-conv">请登录</div>';
        msgContainer.innerHTML = `<div class="welcome" id="welcomeMsg"><h2>👋 欢迎</h2><p>请登录后使用。</p></div>`;
    }

    function enableInput(enabled) {
        userInput.disabled = !enabled;
        sendBtn.disabled = !enabled;
    }

    // ======== 登录/注册模态框 ========
    function openAuthModal(loginMode = true) {
        isLoginMode = loginMode;
        modalTitle.textContent = loginMode ? '登录' : '注册';
        modalSubmit.textContent = loginMode ? '登录' : '注册';
        switchText.innerHTML = loginMode
            ? '还没有账号？<a id="switchLink">注册</a>'
            : '已有账号？<a id="switchLink">登录</a>';
        modalError.classList.remove('show');
        modalUsername.value = '';
        modalPassword.value = '';
        authModal.classList.add('show');
        document.getElementById('switchLink').addEventListener('click', function(e) {
            e.preventDefault();
            openAuthModal(!isLoginMode);
        });
    }

    function closeAuthModal() { authModal.classList.remove('show'); }

    authModal.addEventListener('click', function(e) {
        if (e.target === authModal) closeAuthModal();
    });

    btnLogin.addEventListener('click', () => openAuthModal(true));

    modalSubmit.addEventListener('click', async function() {
        const user = modalUsername.value.trim();
        const pass = modalPassword.value.trim();
        if (!user || !pass) { showModalError('请填写完整'); return; }
        const endpoint = isLoginMode ? '/api/auth/login' : '/api/auth/register';
        try {
            const res = await fetch(API + endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: user, password: pass })
            });
            const data = await res.json();
            if (!res.ok) {
                showModalError(typeof data === 'string' ? data : (data.message || '错误'));
                return;
            }
            token = data.token;
            username = data.username;
            localStorage.setItem('chat_token', token);
            localStorage.setItem('chat_username', username);
            showLoggedIn(username);
            closeAuthModal();
            loadConversations();
            loadPromptsSilent();
        } catch (e) {
            showModalError('网络错误');
        }
    });

    function showModalError(msg) { modalError.textContent = msg; modalError.classList.add('show'); }

    // ======== 退出 ========
    btnLogout.addEventListener('click', () => {
        logout();
    });

    function logout() {
        token = '';
        localStorage.removeItem('chat_token');
        localStorage.removeItem('chat_username');
        localStorage.removeItem('current_prompt_id');
        showLoggedOut();
    }

    // ======== 会话管理 ========
    async function loadConversations() {
        if (!token) { convList.innerHTML = '<div class="no-conv">请登录</div>'; return; }
        try {
            const res = await fetch(API + '/api/conversations', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) { if (res.status === 401) logout(); return; }
            const convs = await res.json();
            if (!convs || convs.length === 0) {
                convList.innerHTML = '<div class="no-conv">暂无会话，点击上方新建</div>';
                enableInput(false);
                return;
            }
            let html = '';
            convs.forEach(conv => {
                const active = (conv.id === currentConvId) ? 'active' : '';
                const time = new Date(conv.createdAt).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
                html += `<div class="conv-item ${active}" data-id="${conv.id}">
                            <span class="conv-title">${escapeHtml(conv.title || '对话')}</span>
                            <span class="conv-time">${time}</span>
                            <button class="del-btn" data-id="${conv.id}">×</button>
                        </div>`;
            });
            convList.innerHTML = html;
            // 绑定点击选择会话
            document.querySelectorAll('.conv-item').forEach(item => {
                item.addEventListener('click', function(e) {
                    if (e.target.classList.contains('del-btn')) return;
                    const convId = parseInt(this.dataset.id);
                    selectConversation(convId);
                });
            });
            // 绑定删除按钮
            document.querySelectorAll('.del-btn').forEach(btn => {
                btn.addEventListener('click', async function(e) {
                    e.stopPropagation();
                    const convId = parseInt(this.dataset.id);
                    if (!confirm('确定删除此会话？所有消息将被清除。')) return;
                    try {
                        const res = await fetch(API + `/api/conversations/${convId}`, {
                            method: 'DELETE',
                            headers: { 'Authorization': 'Bearer ' + token }
                        });
                        if (res.ok) {
                            if (currentConvId === convId) {
                                currentConvId = null;
                                msgContainer.innerHTML = `<div class="welcome"><h2>会话已删除</h2><p>选择其他会话或新建一个。</p></div>`;
                                enableInput(false);
                            }
                            loadConversations();
                        } else {
                            alert('删除失败');
                        }
                    } catch (e) { alert('网络错误'); }
                });
            });
            // 如果当前选中会话不在列表中，清除
            if (currentConvId && !convs.some(c => c.id === currentConvId)) {
                currentConvId = null;
                msgContainer.innerHTML = `<div class="welcome"><h2>请选择会话</h2></div>`;
                enableInput(false);
            }
        } catch (e) { console.error('加载会话失败', e); }
    }

    async function selectConversation(convId) {
        currentConvId = convId;
        document.querySelectorAll('.conv-item').forEach(item => {
            item.classList.toggle('active', parseInt(item.dataset.id) === convId);
        });
        enableInput(true);
        // 加载该会话的历史消息
        try {
            const res = await fetch(API + `/api/chat/${convId}/history`, {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) { if (res.status===401) logout(); return; }
            const data = await res.json();
            const messages = data.messages || [];
            msgContainer.innerHTML = '';
            if (messages.length === 0) {
                msgContainer.innerHTML = `<div class="welcome"><h2>新会话</h2><p>开始你的第一句话吧。</p></div>`;
            } else {
                messages.forEach(msg => {
                    appendMsg('user', msg.userMessage);
                    appendMsg('ai', msg.aiReply);
                });
            }
            msgContainer.scrollTop = msgContainer.scrollHeight;
        } catch (e) { console.error('加载历史失败', e); }
    }

    async function newConversation() {
        if (!token) return;
        try {
            const res = await fetch(API + '/api/conversations', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) { alert('创建失败'); return; }
            const conv = await res.json();
            loadConversations();
            selectConversation(conv.id);
        } catch (e) { alert('网络错误'); }
    }

    newConvBtn.addEventListener('click', newConversation);

    // ======== 聊天功能 ========
    sendBtn.addEventListener('click', sendMessage);
    userInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') sendMessage();
    });

    async function sendMessage() {
        const text = userInput.value.trim();
        if (!text || !currentConvId || !token) return;
        sendBtn.disabled = true;
        sendBtn.textContent = '发送中...';
        appendMsg('user', text);
        userInput.value = '';
        try {
            const body = { message: text };
            if (currentPromptId) body.promptId = currentPromptId;
            const res = await fetch(API + `/api/chat/${currentConvId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify(body)
            });
            const data = await res.json();
            if (res.ok) {
                appendMsg('ai', data.reply);
            } else {
                appendMsg('ai', '错误: ' + (data.reply || '请求失败'));
                if (res.status === 401) logout();
            }
        } catch (e) {
            appendMsg('ai', '网络错误');
        }
        sendBtn.disabled = false;
        sendBtn.textContent = '发送';
        userInput.focus();
    }

    function appendMsg(role, text) {
        const div = document.createElement('div');
        div.className = 'message ' + role;
        div.innerHTML = `<div class="bubble">${escapeHtml(text)}</div>`;
        msgContainer.appendChild(div);
        msgContainer.scrollTop = msgContainer.scrollHeight;
    }

    function escapeHtml(t) {
        const d = document.createElement('div');
        d.textContent = t;
        return d.innerHTML;
    }

    // ======== 提示词管理 ========
    // 打开提示词管理模态框
    btnPrompt.addEventListener('click', () => {
        loadPrompts();
        promptModal.classList.add('show');
    });

    closePromptModalBtn.addEventListener('click', () => promptModal.classList.remove('show'));

    // 静默加载提示词（用于恢复选中名称）
    async function loadPromptsSilent() {
        if (!token) return;
        try {
            const res = await fetch(API + '/api/prompts', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const prompts = await res.json();
            if (currentPromptId) {
                const p = prompts.find(x => x.id === currentPromptId);
                if (p) {
                    promptNameDisplay.textContent = p.name;
                    currentPromptIndicator.style.display = 'inline-flex';
                } else {
                    // 已删除
                    currentPromptId = null;
                    localStorage.removeItem('current_prompt_id');
                    currentPromptIndicator.style.display = 'none';
                }
            }
        } catch(e) { /* ignore */ }
    }

    async function loadPrompts() {
        if (!token) { promptList.innerHTML = '<div class="prompt-empty">请先登录</div>'; return; }
        try {
            const res = await fetch(API + '/api/prompts', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) { if (res.status===401) logout(); return; }
            const prompts = await res.json();
            renderPrompts(prompts);
        } catch(e) { console.error(e); }
    }

    function renderPrompts(prompts) {
        if (!prompts || prompts.length === 0) {
            promptList.innerHTML = '<div class="prompt-empty">暂无提示词，点击上方新建</div>';
            return;
        }
        let html = '';
        prompts.forEach(p => {
            const isActive = currentPromptId === p.id;
            html += `<div class="prompt-card" data-id="${p.id}">
                        <div class="card-header">
                            <span class="card-name">${escapeHtml(p.name)}</span>
                            <div class="card-actions">
                                <button class="edit-prompt" data-id="${p.id}">编辑</button>
                                <button class="del-prompt" data-id="${p.id}">删除</button>
                            </div>
                        </div>
                        <div class="card-content">${escapeHtml(p.content)}</div>
                        <button class="card-use-btn ${isActive?'active':''}" data-id="${p.id}">
                            ${isActive ? '✓ 使用中' : '使用'}
                        </button>
                    </div>`;
        });
        promptList.innerHTML = html;

        // 绑定编辑、删除、使用事件
        document.querySelectorAll('.edit-prompt').forEach(btn => {
            btn.addEventListener('click', function() {
                const id = parseInt(this.dataset.id);
                const card = this.closest('.prompt-card');
                const name = card.querySelector('.card-name').textContent;
                const content = card.querySelector('.card-content').textContent;
                openEditPromptModal(id, name, content);
            });
        });
        document.querySelectorAll('.del-prompt').forEach(btn => {
            btn.addEventListener('click', async function() {
                if (!confirm('删除此提示词？')) return;
                const id = parseInt(this.dataset.id);
                try {
                    const res = await fetch(API + `/api/prompts/${id}`, {
                        method: 'DELETE',
                        headers: { 'Authorization': 'Bearer ' + token }
                    });
                    if (res.ok) {
                        if (currentPromptId === id) {
                            currentPromptId = null;
                            localStorage.removeItem('current_prompt_id');
                            updatePromptIndicator();
                        }
                        loadPrompts();
                    } else { alert('删除失败'); }
                } catch(e) { alert('网络错误'); }
            });
        });
        document.querySelectorAll('.card-use-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                const id = parseInt(this.dataset.id);
                selectPrompt(id);
                promptModal.classList.remove('show');
            });
        });
    }

    function selectPrompt(id) {
        currentPromptId = id;
        localStorage.setItem('current_prompt_id', id);
        // 立即更新显示名称
        const card = promptList.querySelector(`.prompt-card[data-id="${id}"]`);
        if (card) {
            promptNameDisplay.textContent = card.querySelector('.card-name').textContent;
        } else {
            // 如果列表已经关闭，则从后台获取
            loadPromptsSilent();
        }
        currentPromptIndicator.style.display = 'inline-flex';
    }

    function updatePromptIndicator() {
        if (currentPromptId) {
            const card = promptList.querySelector(`.prompt-card[data-id="${currentPromptId}"]`);
            if (card) {
                promptNameDisplay.textContent = card.querySelector('.card-name').textContent;
                currentPromptIndicator.style.display = 'inline-flex';
            } else {
                // 可能已删除或未加载，尝试静默加载
                loadPromptsSilent();
            }
        } else {
            currentPromptIndicator.style.display = 'none';
        }
    }

    removePromptBtn.addEventListener('click', function() {
        currentPromptId = null;
        localStorage.removeItem('current_prompt_id');
        currentPromptIndicator.style.display = 'none';
    });

    // 新建提示词
    newPromptBtn.addEventListener('click', () => openEditPromptModal());
    cancelEditPrompt.addEventListener('click', () => editPromptModal.classList.remove('show'));

    function openEditPromptModal(id = null, name = '', content = '') {
        editingPromptId = id;
        editPromptTitle.textContent = id ? '编辑提示词' : '新建提示词';
        editPromptName.value = name;
        editPromptContent.value = content;
        editPromptModal.classList.add('show');
    }

    savePromptBtn.addEventListener('click', async function() {
        const name = editPromptName.value.trim();
        const content = editPromptContent.value.trim();
        if (!name || !content) { alert('请填写完整'); return; }
        try {
            let url = API + '/api/prompts';
            let method = 'POST';
            if (editingPromptId) {
                url += '/' + editingPromptId;
                method = 'PUT';
            }
            const res = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify({ name, content })
            });
            if (res.ok) {
                editPromptModal.classList.remove('show');
                loadPrompts();
                // 如果当前使用的提示词被编辑并保存，更新指示器名称
                if (editingPromptId && editingPromptId === currentPromptId) {
                    promptNameDisplay.textContent = name;
                }
            } else {
                const err = await res.text();
                alert('保存失败: ' + err);
            }
        } catch(e) { alert('网络错误'); }
    });

    // ======== 启动 ========
    init();
