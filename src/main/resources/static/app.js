
    // ======== 全局状态 ========
    const API = ''; // 同源
    let token = ChatCommon.Auth.getToken();
    let username = ChatCommon.Auth.getUsername();
    let currentConvId = null;          // 当前选中的会话ID
    let currentPromptId = null;        // 当前选中的提示词ID
    let currentModelConfigId = null;   // 当前选中的模型配置ID
    let abortController = null;        // 用于中断流式输出
    let currentImageDescription = null; // 当前已上传图片的识别描述

    // DOM 元素
    const userDisplay = document.getElementById('userDisplay');
    const btnLogin = document.getElementById('btnLogin');
    const btnLogout = document.getElementById('btnLogout');
    const convList = document.getElementById('convList');
    const newConvBtn = document.getElementById('newConvBtn');
    const msgContainer = document.getElementById('msgContainer');
    const userInput = document.getElementById('userInput');
    const sendBtn = document.getElementById('sendBtn');
    const stopBtn = document.getElementById('stopBtn');
    const imageInput = document.getElementById('imageInput');
    const uploadBtn = document.getElementById('uploadBtn');
    const uploadPreview = document.getElementById('uploadPreview');
    const previewImg = document.getElementById('previewImg');
    const removePreviewBtn = document.getElementById('removePreviewBtn');
    const btnPrompt = document.getElementById('btnPrompt');
    const btnMessage = document.getElementById('btnMessage');
    const btnFriend = document.getElementById('btnFriend');
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
    const kbSelector = document.getElementById('kbSelector');
    const btnKB = document.getElementById('btnKB');
    const btnMemory = document.getElementById('btnMemory');

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
    const settingsSignature = document.getElementById('settingsSignature');
    const btnSaveSignature = document.getElementById('btnSaveSignature');
    const signatureSaved = document.getElementById('signatureSaved');
    const userPromptsList = document.getElementById('userPromptsList');
    const avatarImg = document.getElementById('avatarImg');
    const avatarFileInput = document.getElementById('avatarFileInput');
    const avatarWrapper = document.querySelector('.avatar-wrapper');

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

    // 消息通知
    const messageModal = document.getElementById('messageModal');
    const messageList = document.getElementById('messageList');
    const markAllReadBtn = document.getElementById('markAllReadBtn');
    const closeMessageModalBtn = document.getElementById('closeMessageModalBtn');
    
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
        ChatCommon.Auth.setUsername(name);
        userDisplay.textContent = `${name}`;
        btnLogin.style.display = 'none';
        btnLogout.style.display = 'inline-block';
        btnSettings.style.display = 'block';
        btnPrompt.style.display = 'inline-block';
        btnMessage.style.display = 'inline-block';
        btnFriend.style.display = 'inline-block';
        btnModel.style.display = 'inline-block';
        btnMemory.style.display = 'inline-block';
        kbSelector.style.display = 'inline-block';
        btnKB.style.display = 'inline-block';
        searchToggleLabel.classList.add('visible');
        loadBalance();
        loadUnreadCount();
        loadKnowledgeBases();
        if (currentConvId) {
            enableInput(true);
        } else {
            enableInput(false);
        }
        // 首次登录欢迎弹窗
        if (!localStorage.getItem('chat_welcome_shown')) {
            setTimeout(() => showWelcomeModal(), 500);
        }
    }

    function showWelcomeModal() {
        document.getElementById('welcomeModal').classList.add('show');
    }

    function closeWelcome() {
        document.getElementById('welcomeModal').classList.remove('show');
        localStorage.setItem('chat_welcome_shown', '1');
    }

    function showLoggedOut() {
        username = '';
        ChatCommon.Auth.clear();
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
        btnMessage.style.display = 'none';
        btnFriend.style.display = 'none';
        btnModel.style.display = 'none';
        btnMemory.style.display = 'none';
        btnKB.style.display = 'none';
        kbSelector.style.display = 'none';
        uploadBtn.disabled = true;
        uploadPreview.style.display = 'none';
        currentImageDescription = null;
        userInput.disabled = true;
        sendBtn.disabled = true;
        sendBtn.style.display = 'inline-block';
        stopBtn.style.display = 'none';
        userInput.placeholder = '请先登录并选择模型配置...';
        convList.innerHTML = '<div class="no-conv">请登录</div>';
        msgContainer.innerHTML = `<div class="welcome" id="welcomeMsg"><h2><i data-lucide='hand'></i> 欢迎</h2><p>请登录后选择或新建一个会话，然后选择AI模型开始对话。</p></div>`;
        lucide.createIcons();
    }

    function enableInput(enabled) {
        const hasModel = currentModelConfigId !== null;
        userInput.disabled = !enabled || !hasModel;
        sendBtn.disabled = !enabled || !hasModel;
        uploadBtn.disabled = !enabled || !hasModel;
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
            ChatCommon.Auth.setToken(data.token);
            ChatCommon.Auth.setUsername(data.username);
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
                ChatCommon.Auth.setToken(data.token);
                ChatCommon.Auth.setUsername(data.username);
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
        ChatCommon.Auth.clear();
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
                    msgContainer.innerHTML = `<div class="welcome"><h2>请先选择模型</h2><p>点击上方"<i data-lucide='bot'></i> 模型"按钮选择AI模型后再开始对话。</p></div>`;
                    lucide.createIcons();
                }
            } else {
                messages.forEach(msg => {
                    appendMsg('user', msg.userMessage, msg.id);
                    appendMsg('ai', msg.aiReply, msg.id);
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
    stopBtn.addEventListener('click', stopGeneration);
    uploadBtn.addEventListener('click', () => imageInput.click());
    imageInput.addEventListener('change', handleImageUpload);
    removePreviewBtn.addEventListener('click', clearImageUpload);
    userInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            sendMessage();
        }
    });

    // textarea 自动调整高度，最多3倍
    let inputBaseHeight = null;
    userInput.addEventListener('input', function() {
        if (inputBaseHeight === null) {
            // 记录单行基准高度
            userInput.style.height = 'auto';
            inputBaseHeight = userInput.scrollHeight;
        }
        userInput.style.height = 'auto';
        const maxHeight = inputBaseHeight * 3;
        const newHeight = Math.min(userInput.scrollHeight, maxHeight);
        userInput.style.height = newHeight + 'px';
        userInput.style.overflowY = userInput.scrollHeight > maxHeight ? 'auto' : 'hidden';
    });

    function stopGeneration() {
        if (abortController) {
            abortController.abort();
            abortController = null;
        }
    }

    function appendMsg(role, text, msgId) {
        const div = document.createElement('div');
        div.className = 'message ' + role;
        if (msgId != null) {
            div.dataset.msgId = msgId;
        }
        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        if (text) {
            bubble.textContent = text;
        }
        // 删除按钮
        const delBtn = document.createElement('button');
        delBtn.className = 'msg-delete-btn';
        delBtn.textContent = '✕';
        delBtn.title = '删除此消息（将从上下文中移除）';
        delBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            deleteMessageFromDB(div);
        });
        bubble.appendChild(delBtn);
        div.appendChild(bubble);
        msgContainer.appendChild(div);
        msgContainer.scrollTop = msgContainer.scrollHeight;
        return { container: div, bubble: bubble };
    }

    async function deleteMessageFromDB(msgElement) {
        const msgId = msgElement.dataset.msgId;
        if (!token) return;
        // 先找到同组消息对（用户+AI共享同一msgId）
        if (msgId != null) {
            try {
                await fetch(API + `/api/chat/messages/${msgId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
            } catch (e) {
                console.error('删除消息失败', e);
            }
        }
        // 删除同组的所有气泡（user + ai + token-usage）
        const allMessages = msgContainer.querySelectorAll('.message');
        let removing = false;
        for (const el of allMessages) {
            if (el === msgElement) {
                removing = true;
                // 删除前面的token-usage行
                const prev = el.previousElementSibling;
                if (prev && prev.classList.contains('token-usage-line')) {
                    prev.remove();
                }
                el.remove();
                continue;
            }
            if (removing && el.dataset.msgId === msgId) {
                el.remove();
                continue;
            }
            if (removing && el.dataset.msgId !== msgId) {
                // 下一个token-usage也删除
                const prev = el.previousElementSibling;
                if (prev && prev.classList.contains('token-usage-line')) {
                    prev.remove();
                }
                removing = false;
            }
        }
        if (removing) {
            // 删除末尾的token-usage
            const last = msgContainer.lastElementChild;
            if (last && last.classList.contains('token-usage-line')) {
                last.remove();
            }
        }
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
        window.location.href = '/workshop';
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

    // ======== 消息通知 ========
    btnMessage.addEventListener('click', () => {
        loadNotifications();
        loadUnreadCount();
        messageModal.classList.add('show');
    });

    closeMessageModalBtn.addEventListener('click', () => messageModal.classList.remove('show'));

    messageModal.addEventListener('click', function(e) {
        if (e.target === messageModal) messageModal.classList.remove('show');
    });

    markAllReadBtn.addEventListener('click', async () => {
        if (!token) return;
        await fetch(API + '/api/notifications/read-all', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        loadNotifications();
        loadUnreadCount();
    });

    async function loadNotifications() {
        if (!token) return;
        try {
            const res = await fetch(API + '/api/notifications', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const list = await res.json();
            if (!list || list.length === 0) {
                messageList.innerHTML = '<div class="msg-empty">暂无消息</div>';
                return;
            }
            const typeIconMap = {
                'PROMPT_LIKE': '❤️',
                'PROMPT_COMMENT': '💬',
                'COMMENT_REPLY': '↩️',
                'COMMENT_LIKE': '👍',
                'FRIEND_REQUEST': '👋',
                'FRIEND_ACCEPT': '✅',
                'FRIEND_MESSAGE': '💬'
            };
            let html = '';
            list.forEach(n => {
                const icon = typeIconMap[n.type] || '🔔';
                const time = new Date(n.createdAt).toLocaleString('zh-CN');
                const unreadClass = n.isRead ? '' : 'unread';
                html += `<div class="msg-notif ${unreadClass}" data-id="${n.id}" data-type="${n.type || ''}" data-prompt-id="${n.promptId || ''}" data-read="${n.isRead}">
                    <button class="msg-notif-delete" title="删除">×</button>
                    <div><span class="notif-icon">${icon}</span><span class="notif-title">${escapeHtml(n.title)}</span></div>
                    ${n.content ? `<div class="notif-content">${escapeHtml(n.content)}</div>` : ''}
                    <div class="notif-time">${time}</div>
                </div>`;
            });
            messageList.innerHTML = html;
            // 点击通知：标记已读 + 跳转
            messageList.querySelectorAll('.msg-notif').forEach(item => {
                item.addEventListener('click', function(e) {
                    if (e.target.closest('.msg-notif-delete')) return;
                    const id = parseInt(this.dataset.id);
                    const type = this.dataset.type;
                    const promptId = this.dataset.promptId;
                    // 标记已读
                    if (this.dataset.read === 'false') {
                        markNotificationRead(id);
                        this.classList.remove('unread');
                        this.dataset.read = 'true';
                    }
                    // 跳转
                    if (type === 'FRIEND_REQUEST' || type === 'FRIEND_ACCEPT' || type === 'FRIEND_MESSAGE') {
                        messageModal.classList.remove('show');
                        openFriendModal();
                    } else if (promptId) {
                        window.open('/prompt-hub', '_blank');
                    }
                });
            });
            // 删除按钮
            messageList.querySelectorAll('.msg-notif-delete').forEach(btn => {
                btn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    const id = parseInt(this.closest('.msg-notif').dataset.id);
                    deleteNotification(id);
                });
            });
        } catch (e) {
            console.error('加载消息失败', e);
        }
    }

    async function markNotificationRead(id) {
        try {
            await fetch(API + '/api/notifications/' + id + '/read', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token }
            });
            loadUnreadCount();
        } catch (e) { /* 静默失败 */ }
    }

    async function deleteNotification(id) {
        try {
            const res = await fetch(API + '/api/notifications/' + id, {
                method: 'DELETE',
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (res.ok) {
                loadNotifications();
                loadUnreadCount();
            }
        } catch (e) {
            console.error('删除消息失败', e);
        }
    }

    async function loadUnreadCount() {
        if (!token) return;
        try {
            const res = await fetch(API + '/api/notifications/unread-count', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const data = await res.json();
            const count = data.count || 0;
            if (count > 0) {
                btnMessage.innerHTML = '<i data-lucide="message-circle"></i> 消息<span class="msg-badge">' + count + '</span>';
            } else {
                btnMessage.innerHTML = '<i data-lucide="message-circle"></i> 消息';
            }
            lucide.createIcons();
        } catch (e) {
            console.error('加载未读数失败', e);
        }
    }
 
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

    btnSaveSignature.addEventListener('click', async () => {
        const signature = settingsSignature.value.trim();
        if (signature.length > 200) {
            alert('签名长度不能超过200个字符');
            return;
        }
        try {
            const res = await fetch(API + '/api/auth/update-profile', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify({ signature: signature })
            });
            if (!res.ok) {
                const err = await res.text();
                alert('保存失败: ' + err);
                return;
            }
            signatureSaved.style.display = 'block';
            setTimeout(() => { signatureSaved.style.display = 'none'; }, 2000);
        } catch(e) {
            alert('网络错误');
        }
    });

    // 头像上传（裁剪流程）
    const cropModal = document.getElementById('cropModal');
    const cropViewport = document.getElementById('cropViewport');
    const cropImage = document.getElementById('cropImage');
    const cropZoom = document.getElementById('cropZoom');
    const cropCancel = document.getElementById('cropCancel');
    const cropConfirm = document.getElementById('cropConfirm');

    let cropImgNaturalW = 0, cropImgNaturalH = 0;
    let cropScale = 1;
    let cropOffsetX = 0, cropOffsetY = 0;
    let cropDragging = false, cropDragStartX = 0, cropDragStartY = 0;
    let cropBaseOffsetX = 0, cropBaseOffsetY = 0;

    const CROP_SIZE = 300; // 裁剪正方形边长

    function updateCropImage() {
        const w = cropImgNaturalW * cropScale;
        const h = cropImgNaturalH * cropScale;
        cropImage.style.width = w + 'px';
        cropImage.style.height = h + 'px';
        cropImage.style.left = cropOffsetX + 'px';
        cropImage.style.top = cropOffsetY + 'px';
    }

    function clampCropOffset() {
        const vpW = cropViewport.clientWidth;
        const vpH = cropViewport.clientHeight;
        const imgW = cropImgNaturalW * cropScale;
        const imgH = cropImgNaturalH * cropScale;

        // 裁剪框在视口内居中: left=(vpW-CROP_SIZE)/2=20, top=(vpH-CROP_SIZE)/2=20
        const cropLeft = (vpW - CROP_SIZE) / 2;
        const cropTop = (vpH - CROP_SIZE) / 2;

        // 图片必须完全覆盖裁剪框
        const minX = cropLeft + CROP_SIZE - imgW;
        const maxX = cropLeft;
        const minY = cropTop + CROP_SIZE - imgH;
        const maxY = cropTop;

        cropOffsetX = Math.min(maxX, Math.max(minX, cropOffsetX));
        cropOffsetY = Math.min(maxY, Math.max(minY, cropOffsetY));
    }

    avatarWrapper.addEventListener('click', () => avatarFileInput.click());

    avatarFileInput.addEventListener('change', function () {
        const file = this.files[0];
        if (!file) return;
        if (!file.type.startsWith('image/')) { alert('请选择图片文件'); return; }
        const reader = new FileReader();
        reader.onload = function (e) {
            cropImage.src = e.target.result;
            cropImage.onload = function () {
                cropImgNaturalW = cropImage.naturalWidth;
                cropImgNaturalH = cropImage.naturalHeight;
                // 初始缩放：让图片短边填满裁剪框
                cropScale = CROP_SIZE / Math.min(cropImgNaturalW, cropImgNaturalH);
                cropOffsetX = (cropViewport.clientWidth - cropImgNaturalW * cropScale) / 2;
                cropOffsetY = (cropViewport.clientHeight - cropImgNaturalH * cropScale) / 2;
                cropZoom.value = Math.round(cropScale * 100);
                updateCropImage();
                cropModal.classList.add('show');
            };
        };
        reader.readAsDataURL(file);
        this.value = '';
    });

    // 拖拽
    cropViewport.addEventListener('mousedown', function (e) {
        e.preventDefault();
        cropDragging = true;
        cropDragStartX = e.clientX;
        cropDragStartY = e.clientY;
        cropBaseOffsetX = cropOffsetX;
        cropBaseOffsetY = cropOffsetY;
    });

    window.addEventListener('mousemove', function (e) {
        if (!cropDragging) return;
        cropOffsetX = cropBaseOffsetX + (e.clientX - cropDragStartX);
        cropOffsetY = cropBaseOffsetY + (e.clientY - cropDragStartY);
        clampCropOffset();
        updateCropImage();
    });

    window.addEventListener('mouseup', function () {
        cropDragging = false;
    });

    // 滚轮缩放
    cropViewport.addEventListener('wheel', function (e) {
        e.preventDefault();
        const delta = e.deltaY > 0 ? -0.05 : 0.05;
        const newScale = Math.min(3, Math.max(0.5, cropScale + delta * cropScale));
        // 以裁剪框中心为缩放原点
        const cx = (cropViewport.clientWidth) / 2;
        const cy = (cropViewport.clientHeight) / 2;
        const ratio = newScale / cropScale;
        cropOffsetX = cx - ratio * (cx - cropOffsetX);
        cropOffsetY = cy - ratio * (cy - cropOffsetY);
        cropScale = newScale;
        clampCropOffset();
        updateCropImage();
        cropZoom.value = Math.round(cropScale * 100);
    });

    // 缩放滑块
    cropZoom.addEventListener('input', function () {
        const newScale = parseInt(this.value) / 100;
        const cx = (cropViewport.clientWidth) / 2;
        const cy = (cropViewport.clientHeight) / 2;
        const ratio = newScale / cropScale;
        cropOffsetX = cx - ratio * (cx - cropOffsetX);
        cropOffsetY = cy - ratio * (cy - cropOffsetY);
        cropScale = newScale;
        clampCropOffset();
        updateCropImage();
    });

    // 触摸支持
    cropViewport.addEventListener('touchstart', function (e) {
        if (e.touches.length === 1) {
            cropDragging = true;
            cropDragStartX = e.touches[0].clientX;
            cropDragStartY = e.touches[0].clientY;
            cropBaseOffsetX = cropOffsetX;
            cropBaseOffsetY = cropOffsetY;
        }
    }, { passive: false });

    cropViewport.addEventListener('touchmove', function (e) {
        if (!cropDragging) return;
        e.preventDefault();
        cropOffsetX = cropBaseOffsetX + (e.touches[0].clientX - cropDragStartX);
        cropOffsetY = cropBaseOffsetY + (e.touches[0].clientY - cropDragStartY);
        clampCropOffset();
        updateCropImage();
    }, { passive: false });

    cropViewport.addEventListener('touchend', function () {
        cropDragging = false;
    });

    // 取消
    cropCancel.addEventListener('click', function () {
        cropModal.classList.remove('show');
    });

    cropModal.addEventListener('click', function (e) {
        if (e.target === cropModal) cropModal.classList.remove('show');
    });

    // 确认裁剪并上传
    cropConfirm.addEventListener('click', async function () {
        const canvas = document.createElement('canvas');
        canvas.width = 200;
        canvas.height = 200;
        const ctx = canvas.getContext('2d');

        // 裁剪区域在视口内居中，坐标偏移
        const cropLeft = (cropViewport.clientWidth - CROP_SIZE) / 2;
        const cropTop = (cropViewport.clientHeight - CROP_SIZE) / 2;

        // 从原始图片上裁剪对应区域
        const sx = (cropLeft - cropOffsetX) / cropScale;
        const sy = (cropTop - cropOffsetY) / cropScale;
        const sw = CROP_SIZE / cropScale;
        const sh = CROP_SIZE / cropScale;

        ctx.drawImage(cropImage, sx, sy, sw, sh, 0, 0, 200, 200);

        canvas.toBlob(async function (blob) {
            const formData = new FormData();
            formData.append('file', blob, 'avatar.png');
            try {
                const res = await fetch(API + '/api/auth/upload-avatar', {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + token },
                    body: formData
                });
                if (!res.ok) {
                    const err = await res.text();
                    alert('上传失败: ' + err);
                    return;
                }
                const data = await res.json();
                avatarImg.src = data.avatarUrl + '?t=' + Date.now();
                cropModal.classList.remove('show');
            } catch (e) {
                alert('网络错误');
            }
        }, 'image/png', 0.92);
    });

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
            settingsSignature.value = user.signature || '';
            if (user.avatarUrl) {
                avatarImg.src = user.avatarUrl;
            } else {
                avatarImg.src = '';
            }
        } catch(e) { 
            console.error('加载用户信息失败', e); 
        }
        loadUserPrompts();
    }

    async function loadUserPrompts() {
        try {
            const res = await fetch(API + '/api/prompts-hub/user', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const prompts = await res.json();
            if (!prompts || prompts.length === 0) {
                userPromptsList.innerHTML = '<div class="prompt-empty">暂无分享</div>';
                return;
            }
            let html = '';
            prompts.forEach(p => {
                html += `<div class="user-prompt-item">
                    <span class="prompt-name" title="${escapeHtml(p.name)}">${escapeHtml(p.name)}</span>
                    <span class="prompt-likes">❤️ ${p.likesCount || 0}</span>
                </div>`;
            });
            userPromptsList.innerHTML = html;
        } catch(e) {
            console.error('加载用户分享失败', e);
        }
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
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
        btnSponsorCreate.innerHTML = '<i data-lucide="upload"></i> 创建赞助审核';
        lucide.createIcons();
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
        btnSponsorCreate.innerHTML = '<i data-lucide="loader-circle"></i> 提交中...';
        lucide.createIcons();
        
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
                btnSponsorCreate.innerHTML = '<i data-lucide="upload"></i> 创建赞助审核';
                lucide.createIcons();
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
            btnSponsorCreate.innerHTML = '<i data-lucide="upload"></i> 创建赞助审核';
            lucide.createIcons();
        } catch(e) {
            sponsorError.textContent = '网络错误，请稍后重试';
            sponsorError.classList.add('show');
            btnSponsorCreate.disabled = false;
            btnSponsorCreate.innerHTML = '<i data-lucide="upload"></i> 创建赞助审核';
            lucide.createIcons();
        }
    });

    // 加载知识库列表
    async function loadKnowledgeBases() {
        try {
            const res = await fetch(API + '/api/kb/list', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const kbs = await res.json();
            kbSelector.innerHTML = '<option value="">不使用知识库</option>';
            kbs.forEach(kb => {
                const opt = document.createElement('option');
                opt.value = kb.id;
                opt.textContent = kb.name;
                kbSelector.appendChild(opt);
            });
        } catch (e) {
            console.error('加载知识库列表失败', e);
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
    // ======== 图片上传与识别 ========
    async function handleImageUpload(e) {
        const file = e.target.files[0];
        if (!file) return;

        // 清空 file input 以便可以重新选择同一文件
        imageInput.value = '';

        // 1. 立即显示 loading 预览条
        uploadBtn.disabled = true;
        uploadBtn.innerHTML = '<i data-lucide="loader-circle"></i>';
        lucide.createIcons();
        currentImageDescription = null;

        // 读取本地预览图
        const reader = new FileReader();
        reader.onload = function(ev) {
            previewImg.src = ev.target.result;
        };
        reader.readAsDataURL(file);

        // 显示预览条（loading 状态）
        const previewLabel = uploadPreview.querySelector('.preview-label');
        if (previewLabel) previewLabel.textContent = '正在分析图片...';
        uploadPreview.style.display = 'flex';

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch(API + '/api/image/upload', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token },
                body: formData
            });

            if (!res.ok) {
                const data = await res.json();
                throw new Error(data.error || '上传失败');
            }

            const data = await res.json();
            // 图片描述仅存储在内存中，随消息隐式发送，不暴露在 UI 中
            currentImageDescription = data.description;

            // 更新预览条为完成状态
            if (previewLabel) previewLabel.textContent = '图片处理完成，识别结果将随消息发送';
            uploadBtn.innerHTML = '<i data-lucide="check-circle"></i>';
            lucide.createIcons();
        } catch (err) {
            alert('图片处理失败: ' + err.message);
            uploadBtn.innerHTML = '<i data-lucide="image"></i>';
            lucide.createIcons();
            uploadBtn.disabled = currentModelConfigId !== null;
            uploadPreview.style.display = 'none';
            currentImageDescription = null;
        }

        // 清空 file input（reader 已读取）
        imageInput.value = '';
    }

    function clearImageUpload() {
        uploadPreview.style.display = 'none';
        previewImg.src = '';
        currentImageDescription = null;
        uploadBtn.innerHTML = '<i data-lucide="image"></i>';
        lucide.createIcons();
        uploadBtn.disabled = currentModelConfigId !== null;
    }

    async function sendMessage() {
        const text = userInput.value.trim();
        if (!text || !currentConvId || !token) return;
        sendBtn.style.display = 'none';
        stopBtn.style.display = 'inline-block';
        const { container: userContainer } = appendMsg('user', text);
        userInput.value = '';
        const { container: aiContainer, bubble: aiBubble } = appendMsg('ai', '');
        let aiText = '';
        let tokenUsageData = null;
        // 创建新的 AbortController
        abortController = new AbortController();
        let reader = null;
        try {
            const body = { message: text };
            if (currentPromptId) body.promptId = currentPromptId;
            if (currentModelConfigId) body.modelConfigId = currentModelConfigId;
            body.webSearchEnabled = webSearchToggle.checked;
            body.longMemoryEnabled = true;
            if (currentImageDescription) body.imageDescription = currentImageDescription;
            if (kbSelector.value) body.knowledgeBaseId = parseInt(kbSelector.value);
            const res = await fetch(API + `/api/chat/${currentConvId}/stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify(body),
                signal: abortController.signal
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

            reader = res.body.getReader();
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
                        // 解析 messageId
                        if (eventName === 'done' && data !== '[DONE]') {
                            try {
                                const doneObj = JSON.parse(data);
                                if (doneObj.messageId != null) {
                                    userContainer.dataset.msgId = doneObj.messageId;
                                    aiContainer.dataset.msgId = doneObj.messageId;
                                }
                            } catch(e) {}
                        }
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
            if (e.name === 'AbortError') {
                // 用户主动中断，追加提示
                if (!aiText) {
                    aiBubble.textContent = '已停止生成。';
                } else {
                    aiBubble.textContent = aiText + '\n\n[已停止生成]';
                }
            } else {
                console.error(e);
                aiBubble.textContent = aiText ? aiText : '网络错误';
            }
        } finally {
            stopBtn.style.display = 'none';
            sendBtn.style.display = 'inline-block';
            sendBtn.disabled = false;
            sendBtn.textContent = '发送';
            abortController = null;
            clearImageUpload();
            userInput.focus();
        }
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
    
    // ======== 好友功能 ========
    const friendModal = document.getElementById('friendModal');
    const friendList = document.getElementById('friendList');
    const friendMessages = document.getElementById('friendMessages');
    const friendInput = document.getElementById('friendInput');
    const friendSendBtn = document.getElementById('friendSendBtn');
    const friendChatPlaceholder = document.getElementById('friendChatPlaceholder');
    const friendChatPanel = document.getElementById('friendChatPanel');
    const friendChatName = document.getElementById('friendChatName');
    const friendRequestBadge = document.getElementById('friendRequestBadge');

    const addFriendModal = document.getElementById('addFriendModal');
    const friendSearchInput = document.getElementById('friendSearchInput');
    const friendSearchBtn = document.getElementById('friendSearchBtn');
    const friendSearchResults = document.getElementById('friendSearchResults');

    const friendRequestsModal = document.getElementById('friendRequestsModal');
    const friendRequestsList = document.getElementById('friendRequestsList');

    let currentFriend = null; // { userId, friendshipId, username, avatarUrl }
    let openFriendModal;

    btnFriend.addEventListener('click', () => openFriendModal());

    // 添加好友
    document.getElementById('btnAddFriend').addEventListener('click', () => {
        addFriendModal.classList.add('show');
        friendSearchInput.value = '';
        friendSearchResults.innerHTML = '<div class="friend-empty">输入关键词搜索用户</div>';
    });

    addFriendModal.addEventListener('click', e => {
        if (e.target === addFriendModal) addFriendModal.classList.remove('show');
    });
    document.getElementById('closeAddFriend').addEventListener('click', () => addFriendModal.classList.remove('show'));

    friendSearchBtn.addEventListener('click', doSearch);
    friendSearchInput.addEventListener('keydown', e => { if (e.key === 'Enter') doSearch(); });

    async function doSearch() {
        const keyword = friendSearchInput.value.trim();
        if (!keyword) return;
        try {
            const res = await fetch(API + `/api/friends/search?keyword=${encodeURIComponent(keyword)}`, {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const list = await res.json();
            if (!list || list.length === 0) {
                friendSearchResults.innerHTML = '<div class="friend-empty">未找到用户</div>';
                return;
            }
            let html = '';
            list.forEach(u => {
                const avatarSrc = u.avatarUrl ? ` src="${escapeHtml(u.avatarUrl)}"` : '';
                html += `<div class="friend-search-item">
                    <img class="s-avatar"${avatarSrc} alt="">
                    <div class="s-info">
                        <div class="s-name">${escapeHtml(u.username)}</div>
                        <div class="s-pid">PID: ${escapeHtml(u.pid)}</div>
                    </div>
                    <button class="s-btn" data-user-id="${u.id}" ${u.alreadyRelated ? 'disabled' : ''}>${u.alreadyRelated ? '已申请' : '申请'}</button>
                </div>`;
            });
            friendSearchResults.innerHTML = html;
            friendSearchResults.querySelectorAll('.s-btn:not([disabled])').forEach(btn => {
                btn.addEventListener('click', async function() {
                    const userId = parseInt(this.dataset.userId);
                    try {
                        const res = await fetch(API + '/api/friends/request', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
                            body: JSON.stringify({ userId: userId })
                        });
                        if (res.ok) {
                            this.textContent = '已申请';
                            this.disabled = true;
                        } else {
                            const err = await res.json();
                            alert(err.error || '申请失败');
                        }
                    } catch(e) { alert('网络错误'); }
                });
            });
        } catch(e) { console.error('搜索失败', e); }
    }

    // 好友申请列表
    document.getElementById('btnFriendRequests').addEventListener('click', () => {
        friendRequestsModal.classList.add('show');
        loadFriendRequests();
    });
    friendRequestsModal.addEventListener('click', e => {
        if (e.target === friendRequestsModal) friendRequestsModal.classList.remove('show');
    });
    document.getElementById('closeFriendRequests').addEventListener('click', () => friendRequestsModal.classList.remove('show'));

    async function loadFriendRequests() {
        try {
            const res = await fetch(API + '/api/friends/pending', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const list = await res.json();
            if (!list || list.length === 0) {
                friendRequestsList.innerHTML = '<div class="friend-empty">暂无好友申请</div>';
                return;
            }
            let html = '';
            list.forEach(r => {
                const avatarSrc = r.avatarUrl ? ` src="${escapeHtml(r.avatarUrl)}"` : '';
                html += `<div class="friend-request-item" data-id="${r.friendshipId}">
                    <img class="r-avatar"${avatarSrc} alt="">
                    <div class="r-info"><div class="r-name">${escapeHtml(r.fromUsername)}</div></div>
                    <div class="r-actions">
                        <button class="r-accept" data-id="${r.friendshipId}">接受</button>
                        <button class="r-reject" data-id="${r.friendshipId}">拒绝</button>
                    </div>
                </div>`;
            });
            friendRequestsList.innerHTML = html;
            friendRequestsList.querySelectorAll('.r-accept').forEach(btn => {
                btn.addEventListener('click', async function() {
                    await handleRequest(this.dataset.id, 'accept');
                });
            });
            friendRequestsList.querySelectorAll('.r-reject').forEach(btn => {
                btn.addEventListener('click', async function() {
                    await handleRequest(this.dataset.id, 'reject');
                });
            });
        } catch(e) { console.error('加载好友申请失败', e); }
    }

    async function handleRequest(friendshipId, action) {
        try {
            const res = await fetch(API + `/api/friends/${action}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
                body: JSON.stringify({ friendshipId: parseInt(friendshipId) })
            });
            if (res.ok) {
                loadFriendRequests();
                loadFriendList();
                loadPendingRequestCount();
            }
        } catch(e) { console.error('操作失败', e); }
    }

    async function loadPendingRequestCount() {
        try {
            const res = await fetch(API + '/api/friends/pending', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const list = await res.json();
            const count = list ? list.length : 0;
            if (count > 0) {
                friendRequestBadge.textContent = count;
                friendRequestBadge.style.display = 'inline-block';
            } else {
                friendRequestBadge.style.display = 'none';
            }
        } catch(e) {}
    }

    // 好友列表
    async function loadFriendList() {
        try {
            const res = await fetch(API + '/api/friends/list', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const list = await res.json();
            if (!list || list.length === 0) {
                friendList.innerHTML = '<div class="friend-empty">暂无好友</div>';
                return;
            }
            let html = '';
            list.forEach(f => {
                const avatarSrc = f.avatarUrl ? ` src="${escapeHtml(f.avatarUrl)}"` : '';
                const pidStr = f.pid ? `PID: ${escapeHtml(f.pid)}` : '';
                const lastMsg = f.lastMessage ? `<span class="f-last-msg">${escapeHtml(f.lastMessage)}</span>` : '';
                html += `<div class="friend-item" data-user-id="${f.userId}" data-friendship-id="${f.friendshipId}" data-username="${escapeHtml(f.username)}" data-avatar="${f.avatarUrl || ''}">
                    <img class="f-avatar"${avatarSrc} alt="">
                    <div class="f-info">
                        <span class="f-name">${escapeHtml(f.username)}</span>
                        <span class="f-pid">${pidStr}</span>
                        ${lastMsg}
                    </div>
                </div>`;
            });
            friendList.innerHTML = html;
            friendList.querySelectorAll('.friend-item').forEach(item => {
                item.addEventListener('click', function() {
                    friendList.querySelectorAll('.friend-item').forEach(i => i.classList.remove('active'));
                    this.classList.add('active');
                    currentFriend = {
                        userId: parseInt(this.dataset.userId),
                        friendshipId: parseInt(this.dataset.friendshipId),
                        username: this.dataset.username,
                        avatarUrl: this.dataset.avatar
                    };
                    openChat(currentFriend);
                });
            });
        } catch(e) { console.error('加载好友列表失败', e); }
    }

    function openChat(friend) {
        friendChatPlaceholder.style.display = 'none';
        friendChatPanel.style.display = 'flex';
        friendChatName.textContent = friend.username;
        friendInput.disabled = false;
        friendSendBtn.disabled = false;
        loadFriendMessages(friend.userId);
        // 移动端：聊天区全屏覆盖
        if (window.innerWidth <= 768) {
            document.getElementById('friendChatArea').classList.add('active');
            document.getElementById('friendSidebar').style.display = 'none';
        }
        // 标记已读
        fetch(API + `/api/friends/read/${friend.userId}`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        }).catch(() => {});
    }

    async function loadFriendMessages(friendUserId) {
        try {
            const res = await fetch(API + `/api/friends/chat/${friendUserId}`, {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (!res.ok) return;
            const msgs = await res.json();
            let html = '';
            msgs.forEach(m => {
                const cls = m.isMe ? 'me' : 'other';
                const time = new Date(m.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
                html += `<div class="friend-msg ${cls}">
                        <div class="f-msg-bubble">${escapeHtml(m.content)}</div>
                        <div class="f-msg-time">${time}</div>
                </div>`;
            });
            friendMessages.innerHTML = html || '<div style="text-align:center;color:#999;padding:24px;">暂无消息，打个招呼吧</div>';
            friendMessages.scrollTop = friendMessages.scrollHeight;
        } catch(e) { console.error('加载聊天记录失败', e); }
    }

    // 发送消息
    friendSendBtn.addEventListener('click', sendFriendMessage);
    friendInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendFriendMessage();
        }
    });

    async function sendFriendMessage() {
        if (!currentFriend) return;
        const content = friendInput.value.trim();
        if (!content) return;
        friendSendBtn.disabled = true;
        try {
            const res = await fetch(API + '/api/friends/message', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
                body: JSON.stringify({ friendshipId: currentFriend.friendshipId, content: content })
            });
            if (res.ok) {
                friendInput.value = '';
                const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
                friendMessages.insertAdjacentHTML('beforeend', `<div class="friend-msg me">
                        <div class="f-msg-bubble">${escapeHtml(content)}</div>
                        <div class="f-msg-time">${time}</div>
                </div>`);
                friendMessages.scrollTop = friendMessages.scrollHeight;
            }
        } catch(e) { console.error('发送失败', e); }
        friendSendBtn.disabled = false;
    }

    // 定时刷新聊天消息（每3秒）
    let friendPollInterval = null;
    openFriendModal = function() {
        friendModal.classList.add('show');
        loadFriendList();
        loadPendingRequestCount();
        if (friendPollInterval) clearInterval(friendPollInterval);
        friendPollInterval = setInterval(() => {
            if (currentFriend && friendModal.classList.contains('show')) {
                loadFriendMessages(currentFriend.userId);
            }
        }, 3000);
    };

    // 关闭时清理
    friendModal.addEventListener('click', function(e) {
        if (e.target === friendModal) {
            closeFriendModal();
        }
    });

    function closeFriendModal() {
        if (friendPollInterval) { clearInterval(friendPollInterval); friendPollInterval = null; }
        friendModal.classList.remove('show');
        // 移动端：重置为好友列表视图
        document.getElementById('friendChatArea').classList.remove('active');
        document.getElementById('friendSidebar').style.display = '';
    }

    // 移动端：返回好友列表
    document.getElementById('friendBackBtn').addEventListener('click', () => {
        document.getElementById('friendChatArea').classList.remove('active');
        document.getElementById('friendSidebar').style.display = '';
    });

    // 移动端：关闭按钮（好友列表页 & 聊天页）
    document.getElementById('friendCloseBtn').addEventListener('click', closeFriendModal);
    document.getElementById('friendChatCloseBtn').addEventListener('click', closeFriendModal);

    // ======== 启动 ========
    init();

    // ==========================================
    //   移动端响应式交互逻辑
    // ==========================================
    (function() {
        const MOBILE_BP = 768;
        let isMobile = window.innerWidth <= MOBILE_BP;

        // DOM
        const mobileMenuBtn = document.getElementById('mobileMenuBtn');
        const mobileMenuPanel = document.getElementById('mobileMenuPanel');
        const mobileMenuMask = document.getElementById('mobileMenuMask');
        const mobileMenuCloseBtn = document.getElementById('mobileMenuCloseBtn');
        const mobileMenuBody = document.getElementById('mobileMenuBody');
        const sidebarMask = document.getElementById('sidebarMask');

        // ---- 填充移动端菜单面板 ----
        function buildMobileMenu() {
            // 复制头部按钮到移动端菜单
            const userInfo = document.querySelector('.header .user-info');
            const indicators = userInfo.querySelectorAll('.current-prompt-indicator, .current-model-indicator');
            const balance = document.getElementById('balanceIndicator');
            const kbSel = document.getElementById('kbSelector');
            const searchTgl = document.querySelector('.search-toggle');
            const buttons = userInfo.querySelectorAll('.auth-btn');
            const userDisp = document.getElementById('userDisplay');

            mobileMenuBody.innerHTML = '';

            // 用户信息
            if (userDisp && userDisp.textContent.trim()) {
                const userRow = document.createElement('div');
                userRow.style.cssText = 'display:flex;align-items:center;gap:8px;padding:8px 0;font-size:15px;font-weight:600;color:#3a3f47;';
                userRow.innerHTML = '<i data-lucide="user"></i> ' + userDisp.textContent.trim();
                mobileMenuBody.appendChild(userRow);
            }

            // 余额
            if (balance && balance.style.display !== 'none') {
                const balRow = document.createElement('div');
                balRow.style.cssText = 'display:flex;align-items:center;gap:8px;padding:6px 0;font-size:14px;color:#3a3f47;';
                balRow.innerHTML = balance.innerHTML;
                mobileMenuBody.appendChild(balRow);
            }

            // 提示词/模型指示器
            indicators.forEach(ind => {
                if (ind.style.display !== 'none') {
                    const clone = ind.cloneNode(true);
                    clone.style.cssText = 'display:flex;align-items:center;gap:6px;padding:10px 14px;background:linear-gradient(135deg,#ede9fe,#e0d4fc);border-radius:12px;font-size:14px;color:#5b4ea1;margin:2px 0;';
                    mobileMenuBody.appendChild(clone);
                }
            });

            // 联网搜索
            if (searchTgl && searchTgl.classList.contains('visible')) {
                const sRow = document.createElement('div');
                sRow.style.cssText = 'padding:8px 0;';
                const lbl = document.createElement('label');
                lbl.style.cssText = 'display:flex;align-items:center;gap:8px;font-size:14px;color:#5a6270;cursor:pointer;';
                const cb = document.createElement('input');
                cb.type = 'checkbox';
                cb.checked = webSearchToggle.checked;
                cb.addEventListener('change', () => { webSearchToggle.checked = cb.checked; webSearchToggle.dispatchEvent(new Event('change')); });
                lbl.appendChild(cb);
                lbl.appendChild(document.createTextNode(' 联网搜索'));
                sRow.appendChild(lbl);
                mobileMenuBody.appendChild(sRow);
            }

            // 知识库选择器
            if (kbSel && kbSel.style.display !== 'none') {
                const kbLabel = document.createElement('div');
                kbLabel.style.cssText = 'font-size:13px;color:#7a8599;margin-top:4px;';
                kbLabel.textContent = '知识库';
                mobileMenuBody.appendChild(kbLabel);
                const kbClone = kbSel.cloneNode(true);
                kbClone.style.cssText = 'width:100%;padding:10px 14px;border:none;border-radius:10px;font-size:14px;background:#eef1f5;color:#3a3f47;box-shadow:inset 3px 3px 5px rgba(163,177,198,0.22);margin-bottom:4px;';
                kbClone.addEventListener('change', () => { kbSel.value = kbClone.value; kbSel.dispatchEvent(new Event('change')); });
                mobileMenuBody.appendChild(kbClone);
            }

            // 功能按钮
            buttons.forEach(btn => {
                if (btn.style.display === 'none') return;
                const cloneBtn = document.createElement('button');
                cloneBtn.className = 'auth-btn';
                cloneBtn.innerHTML = btn.innerHTML;
                cloneBtn.style.cssText = 'width:100%;text-align:left;justify-content:flex-start;padding:12px 16px;font-size:15px;margin:2px 0;';
                cloneBtn.addEventListener('click', () => {
                    btn.click();
                    closeMobileMenu();
                });
                mobileMenuBody.appendChild(cloneBtn);
            });

            lucide.createIcons({ attrs: { parent: mobileMenuBody } });
        }

        // ---- 打开/关闭移动端菜单 ----
        function openMobileMenu() {
            buildMobileMenu();
            mobileMenuPanel.classList.add('open');
            mobileMenuMask.classList.add('show');
            document.body.style.overflow = 'hidden';
        }

        function closeMobileMenu() {
            mobileMenuPanel.classList.remove('open');
            mobileMenuMask.classList.remove('show');
            document.body.style.overflow = '';
        }

        // ---- 侧边栏抽屉 ----
        function openDrawer() {
            document.querySelector('.sidebar').classList.add('open');
            sidebarMask.classList.add('show');
            document.body.style.overflow = 'hidden';
        }

        function closeDrawer() {
            document.querySelector('.sidebar').classList.remove('open');
            sidebarMask.classList.remove('show');
            document.body.style.overflow = '';
        }

        // ---- 事件绑定 ----
        mobileMenuBtn.addEventListener('click', openDrawer);
        mobileMenuCloseBtn.addEventListener('click', closeMobileMenu);
        mobileMenuMask.addEventListener('click', closeMobileMenu);
        sidebarMask.addEventListener('click', closeDrawer);

        // 主聊天页「会话」按钮 -> 移动端打开侧边栏抽屉
        const origNewConvBtn = document.getElementById('newConvBtn');
        // 在聊天区上方插入一个"会话"按钮（移动端可见）
        const chatArea = document.querySelector('.chat-area');
        const sidebarToggleBtn = document.createElement('button');
        sidebarToggleBtn.className = 'sidebar-toggle-btn';
        sidebarToggleBtn.innerHTML = '<i data-lucide="message-circle"></i> 会话';
        sidebarToggleBtn.addEventListener('click', openDrawer);
        sidebarToggleBtn.style.cssText = 'display:none;margin:8px 12px;align-self:flex-start;';
        chatArea.insertBefore(sidebarToggleBtn, chatArea.firstChild);

        // 窗口大小改变：从移动端切回桌面端时清理
        window.addEventListener('resize', () => {
            const wasMobile = isMobile;
            isMobile = window.innerWidth <= MOBILE_BP;
            if (wasMobile && !isMobile) {
                closeDrawer();
                closeMobileMenu();
            }
        });

        // 暴露 API 到全局
        window.openMobileMenu = openMobileMenu;
        window.closeMobileMenu = closeMobileMenu;
        window.openDrawer = openDrawer;
        window.closeDrawer = closeDrawer;

        lucide.createIcons({ attrs: { parent: chatArea } });
    })();
