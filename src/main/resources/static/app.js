
    // ======== 全局状态 ========
    const API = ''; // 同源
    let token = localStorage.getItem('chat_token') || '';
    let username = localStorage.getItem('chat_username') || '';
    let currentConvId = null;          // 当前选中的会话ID
    let currentPromptId = null;        // 当前选中的提示词ID
    let currentModelConfigId = null;   // 当前选中的模型配置ID

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
    const btnModel = document.getElementById('btnModel');
    const currentPromptIndicator = document.getElementById('currentPromptIndicator');
    const promptNameDisplay = document.getElementById('promptNameDisplay');
    const removePromptBtn = document.getElementById('removePromptBtn');
    const currentModelIndicator = document.getElementById('currentModelIndicator');
    const modelNameDisplay = document.getElementById('modelNameDisplay');
    const removeModelBtn = document.getElementById('removeModelBtn');
    
    // 联网搜索开关
    const webSearchToggle = document.getElementById('webSearchToggle');
    const searchToggleLabel = document.querySelector('.search-toggle');

    // 模态框元素
    const authModal = document.getElementById('authModal');
    const modalTitle = document.getElementById('modalTitle');
    const modalUsername = document.getElementById('modalUsername');
    const modalEmail = document.getElementById('modalEmail');
    const modalPassword = document.getElementById('modalPassword');
    const modalPasswordGroup = document.getElementById('modalPasswordGroup');
    const modalCode = document.getElementById('modalCode');
    const modalSendCode = document.getElementById('modalSendCode');
    const modalSubmit = document.getElementById('modalSubmit');
    const modalError = document.getElementById('modalError');
    const switchText = document.getElementById('switchText');
    const switchLink = document.getElementById('switchLink');
    const modalUsernameGroup = document.getElementById('modalUsernameGroup');
    const modalEmailGroup = document.getElementById('modalEmailGroup');
    const modalCodeGroup = document.getElementById('modalCodeGroup');
    const forgotPasswordLink = document.getElementById('forgotPasswordLink');
    const resetPwdModal = document.getElementById('resetPwdModal');
    const resetUsername = document.getElementById('resetUsername');
    const resetCode = document.getElementById('resetCode');
    const resetSendCode = document.getElementById('resetSendCode');
    const resetNewPassword = document.getElementById('resetNewPassword');
    const resetPwdError = document.getElementById('resetPwdError');
    const resetPwdSubmit = document.getElementById('resetPwdSubmit');
    const backToLoginLink = document.getElementById('backToLoginLink');
    const promptModal = document.getElementById('promptModal');
    const promptList = document.getElementById('promptList');
    const newPromptBtn = document.getElementById('newPromptBtn');
    const hubPromptBtn = document.getElementById('hubPromptBtn');
    const closePromptModalBtn = document.getElementById('closePromptModalBtn');
    const editPromptModal = document.getElementById('editPromptModal');
    const editPromptTitle = document.getElementById('editPromptTitle');
    const editPromptName = document.getElementById('editPromptName');
    const editPromptContent = document.getElementById('editPromptContent');
    const savePromptBtn = document.getElementById('savePromptBtn');
    const cancelEditPrompt = document.getElementById('cancelEditPrompt');

    // 模型相关元素
    const modelModal = document.getElementById('modelModal');
    const modelList = document.getElementById('modelList');
    const closeModelModalBtn = document.getElementById('closeModelModalBtn');

    // 设置相关元素
    const btnSettings = document.getElementById('btnSettings');
    const settingsModal = document.getElementById('settingsModal');
    const settingsTabs = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');
    const closeSettingsBtn = document.getElementById('closeSettingsBtn');
    
    // 设置表单元素
    const settingsUsername = document.getElementById('settingsUsername');
    const settingsEmail = document.getElementById('settingsEmail');
    const settingsPid = document.getElementById('settingsPid');
    
    // 密码修改相关元素
    const btnChangePassword = document.getElementById('btnChangePassword');
    const verifyPasswordModal = document.getElementById('verifyPasswordModal');
    const verifyCurrentPassword = document.getElementById('verifyCurrentPassword');
    const verifyPasswordBtn = document.getElementById('verifyPasswordBtn');
    const cancelVerifyPassword = document.getElementById('cancelVerifyPassword');
    const verifyPasswordError = document.getElementById('verifyPasswordError');
    
    const newPasswordModal = document.getElementById('newPasswordModal');
    const newPassword = document.getElementById('newPassword');
    const confirmPassword = document.getElementById('confirmPassword');
    const saveNewPasswordBtn = document.getElementById('saveNewPasswordBtn');
    const cancelNewPassword = document.getElementById('cancelNewPassword');
    const newPasswordError = document.getElementById('newPasswordError');
    
    let verifiedCurrentPassword = '';

    let isLoginMode = true;
    let editingPromptId = null;

    // ======== 初始化 ========
    function init() {
        if (token) {
            showLoggedIn(username);
            loadConversations();
            loadPromptsSilent();
            loadModelsSilent();
            restorePromptIndicator();
            restoreModelIndicator();
        } else {
            showLoggedOut();
        }
    }

    function restorePromptIndicator() {
        const savedPromptId = localStorage.getItem('current_prompt_id');
        if (savedPromptId) {
            currentPromptId = parseInt(savedPromptId);
        }
    }

    function restoreModelIndicator() {
        const savedModelId = localStorage.getItem('current_model_config_id');
        if (savedModelId) {
            currentModelConfigId = parseInt(savedModelId);
            loadModelsSilent();
        }
    }

    // ======== UI 状态 ========
    function showLoggedIn(name) {
        username = name;
        localStorage.setItem('chat_username', name);
        userDisplay.textContent = `👤 ${name}`;
        btnLogin.style.display = 'none';
        btnLogout.style.display = 'inline-block';
        btnSettings.style.display = 'block';
        btnPrompt.style.display = 'inline-block';
        btnModel.style.display = 'inline-block';
        searchToggleLabel.classList.add('visible');
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
        localStorage.removeItem('current_model_config_id');
        token = '';
        currentConvId = null;
        currentPromptId = null;
        currentModelConfigId = null;
        currentPromptIndicator.style.display = 'none';
        currentModelIndicator.style.display = 'none';
        balanceIndicator.style.display = 'none';
        balanceAmount.textContent = '0.0000';
        searchToggleLabel.classList.remove('visible');
        webSearchToggle.checked = false;
        userDisplay.textContent = '';
        btnLogin.style.display = 'inline-block';
        btnLogout.style.display = 'none';
        btnSettings.style.display = 'none';
        btnPrompt.style.display = 'none';
        btnModel.style.display = 'none';
        userInput.disabled = true;
        sendBtn.disabled = true;
        userInput.placeholder = '请先登录并选择模型配置...';
        convList.innerHTML = '<div class="no-conv">请登录</div>';
        msgContainer.innerHTML = `<div class="welcome" id="welcomeMsg"><h2>👋 欢迎</h2><p>请登录后选择或新建一个会话，然后选择AI模型开始对话。</p></div>`;
    }

    function enableInput(enabled) {
        const hasModel = currentModelConfigId !== null;
        userInput.disabled = !enabled || !hasModel;
        sendBtn.disabled = !enabled || !hasModel;
        if (!hasModel) {
            userInput.placeholder = '请先选择模型配置...';
        } else {
            userInput.placeholder = '输入消息...';
        }
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
        modalCode.value = '';
        modalUsernameGroup.style.display = 'block';
        modalUsername.placeholder = loginMode ? '用户名或邮箱' : '请输入用户名';
        modalEmailGroup.style.display = loginMode ? 'none' : 'block';
        modalEmail.value = '';
        modalPasswordGroup.style.display = 'block';
        modalCodeGroup.style.display = loginMode ? 'none' : 'block';
        forgotPasswordLink.style.display = loginMode ? 'inline' : 'none';
        resetSendCodeBtn();
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

    // 忘记密码
    forgotPasswordLink.addEventListener('click', function(e) {
        e.preventDefault();
        closeAuthModal();
        resetPwdError.classList.remove('show');
        resetUsername.value = '';
        resetCode.value = '';
        resetNewPassword.value = '';
        resetPwdSendCodeBtn();
        resetPwdModal.classList.add('show');
    });

    // 返回登录
    backToLoginLink.addEventListener('click', function(e) {
        e.preventDefault();
        resetPwdModal.classList.remove('show');
        openAuthModal(true);
    });

    resetPwdModal.addEventListener('click', function(e) {
        if (e.target === resetPwdModal) {
            resetPwdModal.classList.remove('show');
        }
    });

    // 重置密码发送验证码
    let resetSendCodeTimer = null;
    function resetPwdSendCodeBtn() {
        if (resetSendCodeTimer) {
            clearInterval(resetSendCodeTimer);
            resetSendCodeTimer = null;
        }
        resetSendCode.disabled = false;
        resetSendCode.textContent = '发送验证码';
        resetSendCode.style.background = '#4f46e5';
    }

    resetSendCode.addEventListener('click', async function() {
        const usernameVal = resetUsername.value.trim();
        if (!usernameVal) { showResetPwdError('请输入用户名或邮箱'); return; }
        resetPwdError.classList.remove('show');
        resetSendCode.disabled = true;
        resetSendCode.textContent = '发送中...';
        try {
            const res = await fetch(API + '/api/auth/send-reset-code', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: usernameVal })
            });
            const data = await res.json();
            if (!res.ok) {
                resetPwdSendCodeBtn();
                showResetPwdError(data.message || '发送失败');
                return;
            }
            let seconds = 60;
            resetSendCode.textContent = seconds + 's后重发';
            resetSendCode.style.background = '#9ca3af';
            resetSendCodeTimer = setInterval(() => {
                seconds--;
                if (seconds <= 0) {
                    resetPwdSendCodeBtn();
                } else {
                    resetSendCode.textContent = seconds + 's后重发';
                }
            }, 1000);
        } catch (e) {
            resetPwdSendCodeBtn();
            showResetPwdError('网络错误');
        }
    });

    function showResetPwdError(msg) { resetPwdError.textContent = msg; resetPwdError.classList.add('show'); }

    resetPwdSubmit.addEventListener('click', async function() {
        const usernameVal = resetUsername.value.trim();
        const code = resetCode.value.trim();
        const newPass = resetNewPassword.value.trim();
        if (!usernameVal) { showResetPwdError('请输入用户名或邮箱'); return; }
        if (!code) { showResetPwdError('请输入验证码'); return; }
        if (!newPass) { showResetPwdError('请输入新密码'); return; }
        if (newPass.length < 6) { showResetPwdError('密码长度至少6位'); return; }
        try {
            const res = await fetch(API + '/api/auth/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: usernameVal, code: code, newPassword: newPass })
            });
            const data = await res.json();
            if (!res.ok) {
                showResetPwdError(data.message || '重置失败');
                return;
            }
            alert('密码重置成功，请使用新密码登录');
            resetPwdModal.classList.remove('show');
            openAuthModal(true);
        } catch (e) {
            showResetPwdError('网络错误');
        }
    });

    // 发送验证码
    let sendCodeTimer = null;
    function resetSendCodeBtn() {
        if (sendCodeTimer) {
            clearInterval(sendCodeTimer);
            sendCodeTimer = null;
        }
        modalSendCode.disabled = false;
        modalSendCode.textContent = '发送验证码';
        modalSendCode.style.background = '#4f46e5';
    }

    modalSendCode.addEventListener('click', async function() {
        const email = modalEmail.value.trim();
        if (!email) { showModalError('请先输入邮箱'); return; }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            showModalError('邮箱格式不正确'); return;
        }
        modalError.classList.remove('show');
        modalSendCode.disabled = true;
        modalSendCode.textContent = '发送中...';
        try {
            const res = await fetch(API + '/api/auth/send-code', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: email })
            });
            const data = await res.json();
            if (!res.ok) {
                resetSendCodeBtn();
                showModalError(data.message || '发送失败');
                return;
            }
            // 60秒倒计时
            let seconds = 60;
            modalSendCode.textContent = seconds + 's后重发';
            modalSendCode.style.background = '#9ca3af';
            sendCodeTimer = setInterval(() => {
                seconds--;
                if (seconds <= 0) {
                    resetSendCodeBtn();
                } else {
                    modalSendCode.textContent = seconds + 's后重发';
                }
            }, 1000);
        } catch (e) {
            resetSendCodeBtn();
            showModalError('网络错误');
        }
    });

    modalSubmit.addEventListener('click', async function() {
        const usernameVal = modalUsername.value.trim();
        const emailVal = modalEmail.value.trim();
        const pass = modalPassword.value.trim();
        const code = modalCode.value.trim();

        if (isLoginMode) {
            // 登录：用户名或邮箱 + 密码
            if (!usernameVal) { showModalError('请输入用户名或邮箱'); return; }
            if (!pass) { showModalError('请输入密码'); return; }
            try {
                const res = await fetch(API + '/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username: usernameVal, password: pass })
                });
                const data = await res.json();
                if (!res.ok) {
                    showModalError(data.message || '登录失败');
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
                loadModelsSilent();
            } catch (e) {
                showModalError('网络错误');
            }
        } else {
            // 注册：用户名 + 邮箱 + 密码 + 验证码
            if (!usernameVal) { showModalError('请输入用户名'); return; }
            if (!emailVal) { showModalError('请输入邮箱'); return; }
            if (!pass) { showModalError('请输入密码'); return; }
            if (!code) { showModalError('请输入验证码'); return; }
            try {
                const res = await fetch(API + '/api/auth/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username: usernameVal, email: emailVal, password: pass, code: code })
                });
                const data = await res.json();
                if (!res.ok) {
                    showModalError(data.message || '注册失败');
                    return;
                }
                token = data.token;
                username = data.username;
                const balance = data.balance || 0;
                localStorage.setItem('chat_token', token);
                localStorage.setItem('chat_username', username);
                showLoggedIn(username);
                closeAuthModal();
                loadConversations();
                loadPromptsSilent();
                loadModelsSilent();
                // 显示新用户赠送提示
                if (balance > 0) {
                    updateBalanceDisplay(balance);
                    setTimeout(() => {
                        alert('注册成功！已赠送您 ' + balance + ' 元体验金，快去体验吧~');
                    }, 100);
                }
            } catch (e) {
                showModalError('网络错误');
            }
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
        localStorage.removeItem('current_model_config_id');
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
                if (currentModelConfigId) {
                    msgContainer.innerHTML = `<div class="welcome"><h2>新会话</h2><p>开始你的第一句话吧。</p></div>`;
                } else {
                    msgContainer.innerHTML = `<div class="welcome"><h2>请先选择模型</h2><p>点击上方"🤖 模型"按钮选择AI模型后再开始对话。</p></div>`;
                }
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
        // 创建空的 AI 气泡，用于流式追加内容
        const aiBubble = appendMsg('ai', '');
        let aiText = '';
        try {
            const body = { message: text };
            if (currentPromptId) body.promptId = currentPromptId;
            if (currentModelConfigId) body.modelConfigId = currentModelConfigId;
            body.webSearchEnabled = webSearchToggle.checked;
            const res = await fetch(API + `/api/chat/${currentConvId}/stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify(body)
            });
            if (!res.ok) {
                let errText = '请求失败';
                try {
                    const data = await res.json();
                    errText = data.reply || data.message || errText;
                } catch (e) {
                    errText = res.statusText || errText;
                }
                if (res.status === 401) { logout(); return; }
                aiBubble.textContent = '错误: ' + errText;
                return;
            }

            const reader = res.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';
            let stopped = false;

            while (!stopped) {
                const { value, done } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });

                // 按 SSE 事件块（以空行分隔）切分
                let sepIdx;
                while ((sepIdx = buffer.indexOf('\n\n')) !== -1) {
                    const eventBlock = buffer.slice(0, sepIdx);
                    buffer = buffer.slice(sepIdx + 2);

                    const lines = eventBlock.split('\n');
                    let eventName = '';
                    let dataLines = [];
                    for (const ln of lines) {
                        if (ln.startsWith('event:')) {
                            eventName = ln.slice(6).trim();
                        } else if (ln.startsWith('data:')) {
                            dataLines.push(ln.slice(5).trimStart());
                        } else if (ln.startsWith('data')) {
                            // 兼容可能出现的 data:...
                            dataLines.push(ln.slice(5).trimStart());
                        }
                    }
                    const data = dataLines.join('\n');

                    if (eventName === 'done' || data === '[DONE]') {
                        stopped = true;
                        break;
                    }
                    if (eventName === 'error') {
                        aiBubble.textContent = (aiText ? aiText + '\n' : '') + '错误: ' + data;
                        stopped = true;
                        break;
                    }
                    if (data) {
                        aiText += data;
                        aiBubble.textContent = aiText;
                        msgContainer.scrollTop = msgContainer.scrollHeight;
                    }
                }
            }
        } catch (e) {
            console.error(e);
            aiBubble.textContent = aiText ? aiText : '网络错误';
        }
        sendBtn.disabled = false;
        sendBtn.textContent = '发送';
        userInput.focus();
    }

    function appendMsg(role, text) {
        const div = document.createElement('div');
        div.className = 'message ' + role;
        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        if (text) {
            bubble.textContent = text;
        }
        div.appendChild(bubble);
        msgContainer.appendChild(div);
        msgContainer.scrollTop = msgContainer.scrollHeight;
        return { container: div, bubble: bubble };
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

    hubPromptBtn.addEventListener('click', () => {
        promptModal.classList.remove('show');
        window.location.href = '/prompt-hub';
    });

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
        const card = promptList.querySelector(`.prompt-card[data-id="${id}"]`);
        if (card) {
            promptNameDisplay.textContent = card.querySelector('.card-name').textContent;
        } else {
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
                if (editingPromptId && editingPromptId === currentPromptId) {
                    promptNameDisplay.textContent = name;
                }
            } else {
                const err = await res.text();
                alert('保存失败: ' + err);
            }
        } catch(e) { alert('网络错误'); }
    });

    // ======== 模型配置管理 ========
    btnModel.addEventListener('click', () => {
        loadModels();
        modelModal.classList.add('show');
    });

    closeModelModalBtn.addEventListener('click', () => modelModal.classList.remove('show'));

    async function loadModelsSilent() {
        if (!token) return;
        try {
            const res = await fetch(API + '/api/model-configs', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const models = await res.json();
            if (currentModelConfigId) {
                const m = models.find(x => x.id === currentModelConfigId);
                if (m) {
                    modelNameDisplay.textContent = m.modelName;
                    currentModelIndicator.style.display = 'inline-flex';
                } else {
                    currentModelConfigId = null;
                    localStorage.removeItem('current_model_config_id');
                    currentModelIndicator.style.display = 'none';
                }
            }
        } catch(e) { /* ignore */ }
    }

    async function loadModels() {
        if (!token) { modelList.innerHTML = '<div class="prompt-empty">请先登录</div>'; return; }
        try {
            const res = await fetch(API + '/api/model-configs', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) { if (res.status===401) logout(); return; }
            const models = await res.json();
            renderModels(models);
        } catch(e) { console.error(e); }
    }

    function renderModels(models) {
        if (!models || models.length === 0) {
            modelList.innerHTML = '<div class="prompt-empty">暂无模型配置</div>';
            return;
        }
        let html = '';
        models.forEach(m => {
            const isActive = currentModelConfigId === m.id;
            html += `<div class="prompt-card" data-id="${m.id}">
                        <div class="card-header">
                            <span class="card-name">${escapeHtml(m.modelName)}</span>
                        </div>
                        <div class="card-content">
                            <div><strong>API URL:</strong> ${escapeHtml(m.apiUrl)}</div>
                        </div>
                        <button class="card-use-btn ${isActive?'active':''}" data-id="${m.id}">
                            ${isActive ? '✓ 使用中' : '使用'}
                        </button>
                    </div>`;
        });
        modelList.innerHTML = html;

        // 绑定使用事件
        document.querySelectorAll('.card-use-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                const id = parseInt(this.dataset.id);
                selectModel(id);
                modelModal.classList.remove('show');
            });
        });
    }

    function selectModel(id) {
        currentModelConfigId = id;
        localStorage.setItem('current_model_config_id', id);
        const card = modelList.querySelector(`.prompt-card[data-id="${id}"]`);
        if (card) {
            modelNameDisplay.textContent = card.querySelector('.card-name').textContent;
        } else {
            loadModelsSilent();
        }
        currentModelIndicator.style.display = 'inline-flex';
        
        if (currentConvId) {
            enableInput(true);
            const welcomeMsg = msgContainer.querySelector('.welcome');
            if (welcomeMsg) {
                welcomeMsg.innerHTML = `<h2>新会话</h2><p>开始你的第一句话吧。</p>`;
            }
        } else {
            enableInput(false);
        }
    }

    function updateModelIndicator() {
        if (currentModelConfigId) {
            const card = modelList.querySelector(`.prompt-card[data-id="${currentModelConfigId}"]`);
            if (card) {
                modelNameDisplay.textContent = card.querySelector('.card-name').textContent;
                currentModelIndicator.style.display = 'inline-flex';
            } else {
                loadModelsSilent();
            }
        } else {
            currentModelIndicator.style.display = 'none';
        }
    }

    removeModelBtn.addEventListener('click', function() {
        currentModelConfigId = null;
        localStorage.removeItem('current_model_config_id');
        currentModelIndicator.style.display = 'none';
        enableInput(false);
        userInput.placeholder = '请先选择模型配置...';
    });
    
    // ======== 设置功能 ========
    btnSettings.addEventListener('click', openSettingsModal);
    closeSettingsBtn.addEventListener('click', closeSettingsModal);
    
    settingsTabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const tabId = this.dataset.tab;
            settingsTabs.forEach(t => t.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));
            this.classList.add('active');
            document.getElementById(tabId + '-tab').classList.add('active');
        });
    });
    
    settingsModal.addEventListener('click', function(e) {
        if (e.target === settingsModal) closeSettingsModal();
    });
    
    function openSettingsModal() {
        loadUserInfo();
        settingsModal.classList.add('show');
    }
    
    function closeSettingsModal() {
        settingsModal.classList.remove('show');
    }
    
    async function loadUserInfo() {
        try {
            const res = await fetch(API + '/api/auth/me', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) { 
                if (res.status === 401) {
                    logout(); 
                    return; 
                }
                return;
            }
            const user = await res.json();
            settingsUsername.textContent = user.username || '';
            settingsEmail.textContent = user.email || '';
            settingsPid.textContent = user.pid || '';
        } catch(e) { 
            console.error('加载用户信息失败', e); 
        }
    }
    
    // ======== 修改密码流程 ========
    btnChangePassword.addEventListener('click', function() {
        verifyCurrentPassword.value = '';
        verifyPasswordError.classList.remove('show');
        verifyPasswordModal.classList.add('show');
    });
    
    cancelVerifyPassword.addEventListener('click', function() {
        verifyPasswordModal.classList.remove('show');
    });
    
    verifyPasswordModal.addEventListener('click', function(e) {
        if (e.target === verifyPasswordModal) {
            verifyPasswordModal.classList.remove('show');
        }
    });
    
    verifyPasswordBtn.addEventListener('click', async function() {
        const currentPass = verifyCurrentPassword.value.trim();
        
        if (!currentPass) {
            verifyPasswordError.textContent = '请输入当前密码';
            verifyPasswordError.classList.add('show');
            return;
        }
        
        verifyPasswordError.classList.remove('show');
        
        try {
            const res = await fetch(API + '/api/auth/verify-password', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify({ password: currentPass })
            });
            
            if (res.ok) {
                verifiedCurrentPassword = currentPass;
                verifyPasswordModal.classList.remove('show');
                newPassword.value = '';
                confirmPassword.value = '';
                newPasswordError.classList.remove('show');
                newPasswordModal.classList.add('show');
            } else {
                const err = await res.text();
                verifyPasswordError.textContent = err || '密码验证失败';
                verifyPasswordError.classList.add('show');
            }
        } catch(e) {
            verifyPasswordError.textContent = '网络错误';
            verifyPasswordError.classList.add('show');
        }
    });
    
    cancelNewPassword.addEventListener('click', function() {
        newPasswordModal.classList.remove('show');
        verifiedCurrentPassword = '';
    });
    
    newPasswordModal.addEventListener('click', function(e) {
        if (e.target === newPasswordModal) {
            newPasswordModal.classList.remove('show');
            verifiedCurrentPassword = '';
        }
    });
    
    saveNewPasswordBtn.addEventListener('click', async function() {
        const newPass = newPassword.value.trim();
        const confirmPass = confirmPassword.value.trim();
        
        if (!newPass || !confirmPass) {
            newPasswordError.textContent = '请填写所有字段';
            newPasswordError.classList.add('show');
            return;
        }
        
        if (newPass.length < 6) {
            newPasswordError.textContent = '新密码长度至少6位';
            newPasswordError.classList.add('show');
            return;
        }
        
        if (newPass !== confirmPass) {
            newPasswordError.textContent = '两次输入的密码不一致';
            newPasswordError.classList.add('show');
            return;
        }
        
        newPasswordError.classList.remove('show');
        
        try {
            const res = await fetch(API + '/api/auth/change-password', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify({
                    currentPassword: verifiedCurrentPassword,
                    newPassword: newPass
                })
            });
            
            if (res.ok) {
                alert('密码修改成功，请重新登录');
                newPasswordModal.classList.remove('show');
                verifiedCurrentPassword = '';
                logout();
            } else {
                const err = await res.text();
                newPasswordError.textContent = err || '密码修改失败';
                newPasswordError.classList.add('show');
            }
        } catch(e) {
            newPasswordError.textContent = '网络错误';
            newPasswordError.classList.add('show');
        }
    });
    
    // ======== 余额相关功能 ========
    const balanceIndicator = document.getElementById('balanceIndicator');
    const balanceAmount = document.getElementById('balanceAmount');
    const settingsBalance = document.getElementById('settingsBalance');
    const usageRecords = document.getElementById('usageRecords');
    
    function formatMoney(value) {
        if (!value) return '0.0000';
        const num = parseFloat(value);
        return num.toFixed(4);
    }

    function updateBalanceDisplay(balance) {
        const formattedBalance = formatMoney(balance);
        balanceAmount.textContent = formattedBalance;
        balanceIndicator.style.display = 'inline-flex';
    }
    
    async function loadBalance() {
        if (!token) return;
        try {
            const res = await fetch(API + '/api/billing/balance', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) {
                if (res.status === 401) logout();
                return;
            }
            const data = await res.json();
            balanceAmount.textContent = formatMoney(data.balance);
            settingsBalance.textContent = formatMoney(data.balance);
            balanceIndicator.style.display = 'inline-flex';
        } catch(e) {
            console.error('加载余额失败', e);
        }
    }
    
    async function loadUsageRecords() {
        if (!token) return;
        try {
            const res = await fetch(API + '/api/billing/usage-records?page=0&size=20', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) {
                if (res.status === 401) logout();
                return;
            }
            const data = await res.json();
            const records = data.content || [];
            if (records.length === 0) {
                usageRecords.innerHTML = '<div class="usage-empty">暂无消费记录</div>';
                return;
            }
            let html = '';
            records.forEach(record => {
                const time = new Date(record.createdAt).toLocaleString('zh-CN', { 
                    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' 
                });
                html += `<div class="usage-record-item">
                            <div class="usage-record-left">
                                <div class="usage-record-model">${escapeHtml(record.modelName)}</div>
                                <div class="usage-record-time">${time}</div>
                            </div>
                            <div class="usage-record-right">
                                <div class="usage-record-tokens">${record.inputTokens}/${record.outputTokens} tokens</div>
                                <div class="usage-record-amount">-¥${formatMoney(record.costAmount)}</div>
                            </div>
                        </div>`;
            });
            usageRecords.innerHTML = html;
        } catch(e) {
            console.error('加载消费记录失败', e);
        }
    }
    
    // ======== 赞助功能 ========
    const btnSponsor = document.getElementById('btnSponsor');
    const sponsorModal = document.getElementById('sponsorModal');
    const sponsorUploadArea = document.getElementById('sponsorUploadArea');
    const sponsorUploadPlaceholder = document.getElementById('sponsorUploadPlaceholder');
    const sponsorFileInput = document.getElementById('sponsorFileInput');
    const sponsorPreview = document.getElementById('sponsorPreview');
    const btnSponsorCreate = document.getElementById('btnSponsorCreate');
    const sponsorAmount = document.getElementById('sponsorAmount');
    const sponsorError = document.getElementById('sponsorError');
    const sponsorSuccess = document.getElementById('sponsorSuccess');
    const closeSponsorModalBtn = document.getElementById('closeSponsorModalBtn');

    let selectedSponsorFile = null;
    
    // 打开赞助模态框
    btnSponsor.addEventListener('click', showSponsorModal);
    
    function showSponsorModal() {
        resetSponsorForm();
        sponsorModal.classList.add('show');
    }
    
    function resetSponsorForm() {
        selectedSponsorFile = null;
        sponsorFileInput.value = '';
        sponsorPreview.style.display = 'none';
        sponsorUploadPlaceholder.style.display = 'block';
        sponsorAmount.value = '';
        sponsorError.classList.remove('show');
        sponsorSuccess.style.display = 'none';
        btnSponsorCreate.disabled = false;
        btnSponsorCreate.textContent = '📤 创建赞助审核';
    }
    
    // 关闭赞助模态框
    function closeSponsorModal() {
        sponsorModal.classList.remove('show');
        resetSponsorForm();
    }
    
    sponsorModal.addEventListener('click', function(e) {
        if (e.target === sponsorModal) closeSponsorModal();
    });
    
    closeSponsorModalBtn.addEventListener('click', closeSponsorModal);
    
    // 点击上传区域选择文件
    sponsorUploadArea.addEventListener('click', function(e) {
        if (e.target !== sponsorPreview) {
            sponsorFileInput.click();
        }
    });
    
    // 拖拽上传支持
    sponsorUploadArea.addEventListener('dragover', function(e) {
        e.preventDefault();
        this.style.borderColor = '#4f46e5';
        this.style.background = '#f0f4ff';
    });
    
    sponsorUploadArea.addEventListener('dragleave', function(e) {
        e.preventDefault();
        this.style.borderColor = '#d1d5db';
        this.style.background = '#fafafa';
    });
    
    sponsorUploadArea.addEventListener('drop', function(e) {
        e.preventDefault();
        this.style.borderColor = '#d1d5db';
        this.style.background = '#fafafa';
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleSponsorFile(files[0]);
        }
    });
    
    // 文件选择事件
    sponsorFileInput.addEventListener('change', function() {
        if (this.files.length > 0) {
            handleSponsorFile(this.files[0]);
        }
    });
    
    function handleSponsorFile(file) {
        const allowedTypes = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif'];
        if (!allowedTypes.includes(file.type)) {
            sponsorError.textContent = '仅支持 PNG / JPG / GIF 格式的图片';
            sponsorError.classList.add('show');
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            sponsorError.textContent = '图片大小不能超过 5MB';
            sponsorError.classList.add('show');
            return;
        }
        
        sponsorError.classList.remove('show');
        selectedSponsorFile = file;
        
        const reader = new FileReader();
        reader.onload = function(e) {
            sponsorPreview.src = e.target.result;
            sponsorPreview.style.display = 'block';
            sponsorUploadPlaceholder.style.display = 'none';
        };
        reader.readAsDataURL(file);
    }
    
    // 创建赞助审核
    btnSponsorCreate.addEventListener('click', async function() {
        if (!selectedSponsorFile) {
            sponsorError.textContent = '请先选择赞助截图';
            sponsorError.classList.add('show');
            return;
        }
        
        const amountVal = sponsorAmount.value.trim();
        if (!amountVal || parseFloat(amountVal) <= 0) {
            sponsorError.textContent = '请输入有效的赞助金额';
            sponsorError.classList.add('show');
            return;
        }
        
        sponsorError.classList.remove('show');
        sponsorSuccess.style.display = 'none';
        btnSponsorCreate.disabled = true;
        btnSponsorCreate.textContent = '提交中...';
        
        try {
            const formData = new FormData();
            formData.append('image', selectedSponsorFile);
            formData.append('amount', amountVal);
            
            const res = await fetch(API + '/api/billing/sponsor-create', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token },
                body: formData
            });
            
            const data = await res.json();
            
            if (!res.ok || !data.success) {
                sponsorError.textContent = data.message || '提交失败';
                sponsorError.classList.add('show');
                btnSponsorCreate.disabled = false;
                btnSponsorCreate.textContent = '📤 创建赞助审核';
                return;
            }
            
            sponsorSuccess.textContent = data.message || '赞助审核已提交，请等待管理员审核后发放 Token';
            sponsorSuccess.style.display = 'block';
            sponsorSuccess.classList.add('show');
            sponsorFileInput.value = '';
            selectedSponsorFile = null;
            sponsorPreview.style.display = 'none';
            sponsorUploadPlaceholder.style.display = 'block';
            sponsorAmount.value = '';
            btnSponsorCreate.disabled = false;
            btnSponsorCreate.textContent = '📤 创建赞助审核';
        } catch(e) {
            sponsorError.textContent = '网络错误，请稍后重试';
            sponsorError.classList.add('show');
            btnSponsorCreate.disabled = false;
            btnSponsorCreate.textContent = '📤 创建赞助审核';
        }
    });
    
    // 更新登录后的操作，加载余额
    function showLoggedIn(name) {
        username = name;
        localStorage.setItem('chat_username', name);
        userDisplay.textContent = `👤 ${name}`;
        btnLogin.style.display = 'none';
        btnLogout.style.display = 'inline-block';
        btnSettings.style.display = 'block';
        btnPrompt.style.display = 'inline-block';
        btnModel.style.display = 'inline-block';
        searchToggleLabel.classList.add('visible');
        loadBalance();
        if (currentConvId) {
            enableInput(true);
        } else {
            enableInput(false);
        }
    }
    
    // 更新设置模态框打开时加载余额和消费记录
    function openSettingsModal() {
        loadUserInfo();
        loadBalance();
        loadUsageRecords();
        settingsModal.classList.add('show');
    }
    
    // 更新发送消息处理402错误
    async function sendMessage() {
        const text = userInput.value.trim();
        if (!text || !currentConvId || !token) return;
        sendBtn.disabled = true;
        sendBtn.textContent = '发送中...';
        appendMsg('user', text);
        userInput.value = '';
        const { container: aiContainer, bubble: aiBubble } = appendMsg('ai', '');
        let aiText = '';
        let tokenUsageData = null;
        try {
            const body = { message: text };
            if (currentPromptId) body.promptId = currentPromptId;
            if (currentModelConfigId) body.modelConfigId = currentModelConfigId;
            body.webSearchEnabled = webSearchToggle.checked;
            const res = await fetch(API + `/api/chat/${currentConvId}/stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify(body)
            });
            if (!res.ok) {
                let errText = '请求失败';
                try {
                    const data = await res.json();
                    errText = data.error || data.message || errText;
                } catch (e) {
                    errText = res.statusText || errText;
                }
                if (res.status === 401) { logout(); return; }
                if (res.status === 402) {
                    aiBubble.textContent = errText + '，请前往设置页面充值。';
                    loadBalance();
                } else {
                    aiBubble.textContent = '错误: ' + errText;
                }
                return;
            }

            const reader = res.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';
            let stopped = false;

            while (!stopped) {
                const { value, done } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });

                let sepIdx;
                while ((sepIdx = buffer.indexOf('\n\n')) !== -1) {
                    const eventBlock = buffer.slice(0, sepIdx);
                    buffer = buffer.slice(sepIdx + 2);

                    const lines = eventBlock.split('\n');
                    let eventName = '';
                    let dataLines = [];
                    for (const ln of lines) {
                        if (ln.startsWith('event:')) {
                            eventName = ln.slice(6).trim();
                        } else if (ln.startsWith('data:')) {
                            dataLines.push(ln.slice(5).trimStart());
                        } else if (ln.startsWith('data')) {
                            dataLines.push(ln.slice(5).trimStart());
                        }
                    }
                    const data = dataLines.join('\n');

                    if (eventName === 'done' || data === '[DONE]') {
                        stopped = true;
                        break;
                    }
                    if (eventName === 'error') {
                        aiBubble.textContent = (aiText ? aiText + '\n' : '') + '错误: ' + data;
                        stopped = true;
                        break;
                    }
                    if (eventName === 'token_usage') {
                        try {
                            tokenUsageData = JSON.parse(data);
                        } catch(e) {
                            console.error('解析token使用数据失败', e);
                        }
                        continue;
                    }
                    if (data) {
                        aiText += data;
                        aiBubble.textContent = aiText;
                        msgContainer.scrollTop = msgContainer.scrollHeight;
                    }
                }
            }
            
            if (tokenUsageData) {
                appendTokenUsage(aiContainer, tokenUsageData);
            }
            
            loadBalance();
        } catch (e) {
            console.error(e);
            aiBubble.textContent = aiText ? aiText : '网络错误';
        }
        sendBtn.disabled = false;
        sendBtn.textContent = '发送';
        userInput.focus();
    }
    
    function appendTokenUsage(container, tokenData) {
        const line = document.createElement('div');
        line.className = 'token-usage-line';
        let parts = [];
        if (tokenData.inputTokens > 0) {
            parts.push('输入 ' + tokenData.inputTokens + ' tokens');
        }
        if (tokenData.outputTokens > 0) {
            parts.push('输出 ' + tokenData.outputTokens + ' tokens');
        }
        if (tokenData.costAmount) {
            parts.push('消耗 ¥' + parseFloat(tokenData.costAmount).toFixed(4));
        }
        line.textContent = parts.join(' · ');
        container.appendChild(line);
        msgContainer.scrollTop = msgContainer.scrollHeight;
    }
    
    // ======== 启动 ========
    init();
