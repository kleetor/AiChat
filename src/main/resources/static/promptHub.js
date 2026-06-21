const API = '';
let token = localStorage.getItem('chat_token') || '';
let username = localStorage.getItem('chat_username') || '';
let currentPrompt = null;

const userDisplay = document.getElementById('userDisplay');
const btnLogout = document.getElementById('btnLogout');
const btnBack = document.getElementById('btnBack');
const uploadBtn = document.getElementById('uploadBtn');
const promptsGrid = document.getElementById('promptsGrid');
const emptyState = document.getElementById('emptyState');

const uploadModal = document.getElementById('uploadModal');
const promptSelect = document.getElementById('promptSelect');
const userMessage = document.getElementById('userMessage');
const imageInput = document.getElementById('imageInput');
const imagePreview = document.getElementById('imagePreview');
const submitUpload = document.getElementById('submitUpload');
const cancelUpload = document.getElementById('cancelUpload');

if (imageInput) {
    imageInput.addEventListener('change', function () {
        imagePreview.innerHTML = '';
        const file = this.files && this.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = function (e) {
            const img = document.createElement('img');
            img.src = e.target.result;
            img.style.maxWidth = '160px';
            img.style.maxHeight = '120px';
            imagePreview.appendChild(img);
        };
        reader.readAsDataURL(file);
    });
}

const detailModal = document.getElementById('detailModal');
const detailName = document.getElementById('detailName');
const detailAvatar = document.getElementById('detailAvatar');
const detailAuthor = document.getElementById('detailAuthor');
const detailLikes = document.getElementById('detailLikes');
const detailDate = document.getElementById('detailDate');
const detailImage = document.getElementById('detailImage');
const detailContent = document.getElementById('detailContent');
const detailMessage = document.getElementById('detailMessage');
const detailMessageSection = document.getElementById('detailMessageSection');
const closeDetail = document.getElementById('closeDetail');
const likeBtn = document.getElementById('likeBtn');
const downloadBtn = document.getElementById('downloadBtn');

// 评论区元素
const commentsList = document.getElementById('commentsList');
const commentInput = document.getElementById('commentInput');
const commentSubmitBtn = document.getElementById('commentSubmitBtn');
const replyHint = document.getElementById('replyHint');
const cancelReply = document.getElementById('cancelReply');
let replyToCommentId = null;       // 正在回复的评论ID
let replyToUserName = null;        // 正在回复的用户名

function init() {
    if (!token) {
        window.location.href = '/';
        return;
    }
    showLoggedIn(username);
    loadPrompts();
}

function showLoggedIn(name) {
    userDisplay.innerHTML = `<img class="avatar-mini" src="" alt="" id="hubSelfAvatar"> ${escapeHtml(name)}`;
    // 加载当前用户头像
    fetch(API + '/api/auth/me', {
        headers: { 'Authorization': 'Bearer ' + token }
    }).then(res => res.ok ? res.json() : null).then(user => {
        if (user && user.avatarUrl) {
            const selfAv = document.getElementById('hubSelfAvatar');
            if (selfAv) selfAv.src = user.avatarUrl;
        }
    }).catch(() => {});
}

async function loadPrompts() {
    try {
        const res = await fetch(API + '/api/prompts-hub', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) {
            if (res.status === 401) {
                logout();
                return;
            }
            return;
        }
        const prompts = await res.json();
        renderPrompts(prompts);
    } catch (e) {
        console.error('加载提示词失败', e);
    }
}

function renderPrompts(prompts) {
    if (!prompts || prompts.length === 0) {
        emptyState.style.display = 'block';
        promptsGrid.innerHTML = '';
        return;
    }
    emptyState.style.display = 'none';
    let html = '';
    prompts.forEach(p => {
        const date = new Date(p.createdAt).toLocaleDateString('zh-CN');
        const avatarSrc = p.userAvatar ? ` src="${escapeHtml(p.userAvatar)}"` : '';
        html += `<div class="prompt-card-hub" data-id="${p.id}">
                    ${p.imageUrl ? `<img src="${p.imageUrl}" class="card-image" alt="${p.name}">` : ''}
                    <div class="card-body">
                        <h3 class="card-title">${escapeHtml(p.name)}</h3>
                        <p class="card-preview">${escapeHtml(p.content).substring(0, 100)}...</p>
                        <div class="card-footer">
                            <span class="card-author" data-user-id="${p.userId}" data-username="${escapeHtml(p.userName || '匿名')}" data-avatar="${p.userAvatar || ''}"><img class="avatar-mini"${avatarSrc} alt=""> ${escapeHtml(p.userName || '匿名')}</span>
                            <span class="card-comments">💬 ${p.commentCount || 0}</span>
                            <button class="like-btn-small" data-id="${p.id}">❤️ ${p.likesCount || 0}</button>
                        </div>
                    </div>
                </div>`;
    });
    promptsGrid.innerHTML = html;

    document.querySelectorAll('.prompt-card-hub').forEach(card => {
        card.addEventListener('click', function(e) {
            if (e.target.classList.contains('like-btn-small')) return;
            const id = parseInt(this.dataset.id);
            showDetail(id);
        });
    });

    document.querySelectorAll('.like-btn-small').forEach(btn => {
        btn.addEventListener('click', async function(e) {
            e.stopPropagation();
            const id = parseInt(this.dataset.id);
            await likePrompt(id, this);
        });
    });
}

async function showDetail(id) {
    try {
        const res = await fetch(API + `/api/prompts-hub/${id}`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) return;
        const p = await res.json();
        currentPrompt = p;
        detailName.textContent = p.name;
        detailAuthor.textContent = p.userName || '匿名';
        detailAuthor.dataset.userId = p.userId;
        detailAuthor.dataset.username = p.userName || '匿名';
        detailAuthor.dataset.avatar = p.userAvatar || '';
        detailAuthor.classList.add('card-author');
        detailAvatar.src = p.userAvatar || '';
        detailLikes.textContent = p.likesCount || 0;
        detailDate.textContent = new Date(p.createdAt).toLocaleString('zh-CN');
        detailContent.textContent = p.content;
        detailImage.innerHTML = p.imageUrl ? `<img src="${p.imageUrl}" alt="${p.name}">` : '';
        
        if (p.userMessage && p.userMessage.trim()) {
            detailMessage.textContent = p.userMessage;
            detailMessageSection.style.display = 'block';
        } else {
            detailMessageSection.style.display = 'none';
        }
        
        detailModal.classList.add('show');
        // 加载评论
        loadComments(id);
        // 重置回复状态
        resetReply();
    } catch (e) {
        console.error('加载详情失败', e);
    }
}

async function likePrompt(id, btnElement) {
    try {
        const res = await fetch(API + `/api/prompts-hub/${id}/like`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            const currentCount = parseInt(btnElement.textContent.split(' ')[1]) || 0;
            btnElement.textContent = `❤️ ${currentCount + 1}`;
            loadPrompts();
        } else {
            const data = await res.json();
            alert(data.error || '点赞失败');
        }
    } catch (e) {
        console.error('点赞失败', e);
    }
}

uploadBtn.addEventListener('click', async () => {
    await loadUserPrompts();
    uploadModal.classList.add('show');
});

async function loadUserPrompts() {
    try {
        const res = await fetch(API + '/api/prompts', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) return;
        const prompts = await res.json();
        promptSelect.innerHTML = '<option value="">请选择提示词</option>';
        prompts.forEach(p => {
            const option = document.createElement('option');
            option.value = p.id;
            option.textContent = p.name;
            option.dataset.content = p.content;
            promptSelect.appendChild(option);
        });
    } catch (e) {
        console.error('加载用户提示词失败', e);
    }
}

cancelUpload.addEventListener('click', () => {
    uploadModal.classList.remove('show');
    userMessage.value = '';
    if (imageInput) imageInput.value = '';
    if (imagePreview) imagePreview.innerHTML = '';
});

uploadModal.addEventListener('click', (e) => {
    if (e.target === uploadModal) {
        uploadModal.classList.remove('show');
        userMessage.value = '';
        if (imageInput) imageInput.value = '';
        if (imagePreview) imagePreview.innerHTML = '';
    }
});

submitUpload.addEventListener('click', async () => {
    const selectedOption = promptSelect.options[promptSelect.selectedIndex];
    if (!selectedOption.value) {
        alert('请选择要上传的提示词');
        return;
    }
    const name = selectedOption.textContent;
    const content = selectedOption.dataset.content;
    const message = userMessage.value.trim();
    const imageFile = imageInput && imageInput.files && imageInput.files[0] ? imageInput.files[0] : null;

    try {
        const formData = new FormData();
        formData.append('name', name);
        formData.append('content', content);
        if (message) formData.append('userMessage', message);
        if (imageFile) formData.append('image', imageFile);

        const res = await fetch(API + '/api/prompts-hub/upload-with-image', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token
            },
            body: formData
        });
        if (res.ok) {
            uploadModal.classList.remove('show');
            userMessage.value = '';
            if (imageInput) imageInput.value = '';
            if (imagePreview) imagePreview.innerHTML = '';
            loadPrompts();
            alert('上传成功！');
        } else if (res.status === 400) {
            alert('上传失败：图片格式不支持或文件过大（最大 5MB）');
        } else {
            alert('上传失败');
        }
    } catch (e) {
        console.error('上传失败', e);
        alert('网络错误');
    }
});

closeDetail.addEventListener('click', () => {
    detailModal.classList.remove('show');
    currentPrompt = null;
    resetReply();
    commentsList.innerHTML = '';
});

detailModal.addEventListener('click', (e) => {
    if (e.target === detailModal) {
        detailModal.classList.remove('show');
        currentPrompt = null;
        resetReply();
        commentsList.innerHTML = '';
    }
});

likeBtn.addEventListener('click', async () => {
    if (!currentPrompt) return;
    await likePrompt(currentPrompt.id, { textContent: `❤️ ${currentPrompt.likesCount || 0}` });
    currentPrompt.likesCount = (currentPrompt.likesCount || 0) + 1;
    detailLikes.textContent = currentPrompt.likesCount;
});

downloadBtn.addEventListener('click', async () => {
    if (!currentPrompt) return;
    try {
        const res = await fetch(API + '/api/prompts', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify({ 
                name: currentPrompt.name, 
                content: currentPrompt.content 
            })
        });
        if (res.ok) {
            alert('下载成功！提示词已保存到你的提示词列表');
            detailModal.classList.remove('show');
            currentPrompt = null;
        } else {
            alert('下载失败');
        }
    } catch (e) {
        console.error('下载失败', e);
        alert('网络错误');
    }
});

btnLogout.addEventListener('click', logout);

function logout() {
    token = '';
    localStorage.removeItem('chat_token');
    localStorage.removeItem('chat_username');
    window.location.href = '/';
}

btnBack.addEventListener('click', () => {
    window.location.href = '/';
});

function escapeHtml(t) {
    const d = document.createElement('div');
    d.textContent = t;
    return d.innerHTML;
}

// ======== 评论区功能 ========

async function loadComments(promptId) {
    try {
        const res = await fetch(API + `/api/prompts-hub/${promptId}/comments`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) {
            commentsList.innerHTML = '<div class="comment-empty">加载评论失败</div>';
            return;
        }
        const comments = await res.json();
        renderComments(comments);
    } catch (e) {
        console.error('加载评论失败', e);
    }
}

function renderComments(comments) {
    if (!comments || comments.length === 0) {
        commentsList.innerHTML = '<div class="comment-empty">暂无评论，来发表第一条吧</div>';
        return;
    }
    let html = '';
    comments.forEach(c => {
        html += renderOneComment(c);
    });
    commentsList.innerHTML = html;
    // 绑定事件
    bindCommentEvents();
}

function renderOneComment(c, isReply = false) {
    const time = new Date(c.createdAt).toLocaleString('zh-CN');
    const replyToLabel = isReply && c.replyToName
        ? ` 回复 <strong>@${escapeHtml(c.replyToName)}</strong>`
        : '';
    const avatarSrc = c.userAvatar ? ` src="${escapeHtml(c.userAvatar)}"` : '';
    let html = `<div class="comment-item" data-comment-id="${c.id}">
        <div class="comment-header">
            <img class="avatar-mini"${avatarSrc} alt="">
            <span class="comment-author" data-user-id="${c.userId}" data-username="${escapeHtml(c.userName)}" data-avatar="${c.userAvatar || ''}">${escapeHtml(c.userName)}</span>
            <span class="comment-time">${time}</span>
        </div>
        <div class="comment-body">${replyToLabel ? replyToLabel + '：' : ''}${escapeHtml(c.content)}</div>
        <div class="comment-actions">
            <a class="reply-link" data-id="${c.id}" data-name="${escapeHtml(c.userName)}">回复</a>
            <a class="like-comment-link" data-id="${c.id}">❤️ ${c.likesCount || 0}</a>
            <a class="delete-comment-link danger" data-id="${c.id}">删除</a>
        </div>`;
    if (c.replies && c.replies.length > 0) {
        html += '<div class="comment-replies">';
        c.replies.forEach(r => {
            html += renderOneComment(r, true);
        });
        html += '</div>';
    }
    html += '</div>';
    return html;
}

function bindCommentEvents() {
    // 回复
    document.querySelectorAll('.reply-link').forEach(link => {
        link.addEventListener('click', function() {
            replyToCommentId = parseInt(this.dataset.id);
            replyToUserName = this.dataset.name;
            commentInput.placeholder = `回复 @${replyToUserName}...`;
            commentInput.focus();
            replyHint.style.display = 'block';
            replyHint.querySelector('span').textContent = `回复 @${replyToUserName}`;
            // 更新hint中的文本
            replyHint.innerHTML = `回复 <strong>@${replyToUserName}</strong>，<a id="cancelReply">取消</a>`;
            document.getElementById('cancelReply').addEventListener('click', resetReply);
        });
    });

    // 点赞评论
    document.querySelectorAll('.like-comment-link').forEach(link => {
        link.addEventListener('click', async function() {
            const id = parseInt(this.dataset.id);
            await likeComment(id, this);
        });
    });

    // 删除评论
    document.querySelectorAll('.delete-comment-link').forEach(link => {
        link.addEventListener('click', async function() {
            const id = parseInt(this.dataset.id);
            if (!confirm('确定要删除这条评论及其回复吗？')) return;
            await deleteCommentFromDB(id);
        });
    });
}

async function likeComment(commentId, el) {
    try {
        const res = await fetch(API + `/api/prompts-hub/comments/${commentId}/like`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            const count = parseInt(el.textContent.replace('❤️ ', '')) || 0;
            el.textContent = `❤️ ${count + 1}`;
        } else {
            const data = await res.json();
            alert(data.error || '点赞失败');
        }
    } catch (e) {
        console.error('点赞评论失败', e);
    }
}

async function deleteCommentFromDB(commentId) {
    try {
        const res = await fetch(API + `/api/prompts-hub/comments/${commentId}`, {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok && currentPrompt) {
            loadComments(currentPrompt.id);
            alert('删除成功');
        } else {
            const data = await res.json();
            alert(data.error || '删除失败');
        }
    } catch (e) {
        console.error('删除评论失败', e);
    }
}

function resetReply() {
    replyToCommentId = null;
    replyToUserName = null;
    commentInput.placeholder = '发表评论...';
    replyHint.style.display = 'none';
}

// 发表评论
commentSubmitBtn.addEventListener('click', async () => {
    const content = commentInput.value.trim();
    if (!content || !currentPrompt) return;
    commentSubmitBtn.disabled = true;
    commentSubmitBtn.textContent = '发布中...';
    try {
        const body = { content: content };
        if (replyToCommentId != null) {
            body.parentId = String(replyToCommentId);
        }
        const res = await fetch(API + `/api/prompts-hub/${currentPrompt.id}/comments`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify(body)
        });
        if (res.ok) {
            commentInput.value = '';
            resetReply();
            loadComments(currentPrompt.id);
        } else {
            const err = await res.json();
            alert(err.error || '发表失败');
        }
    } catch (e) {
        console.error('发表评论失败', e);
    }
    commentSubmitBtn.disabled = false;
    commentSubmitBtn.textContent = '发布';
});

cancelReply.addEventListener('click', resetReply);

// Ctrl+Enter 发表评论
commentInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        commentSubmitBtn.click();
    }
});

// ======== 个人名片 ========
const userCardModal = document.getElementById('userCardModal');
const cardAvatar = document.getElementById('cardAvatar');
const cardUsername = document.getElementById('cardUsername');
const cardPid = document.getElementById('cardPid');
const cardSignature = document.getElementById('cardSignature');
const cardShareCount = document.getElementById('cardShareCount');
const cardTotalLikes = document.getElementById('cardTotalLikes');
const closeUserCard = document.getElementById('closeUserCard');
const cardAddFriendBtn = document.getElementById('cardAddFriendBtn');

closeUserCard.addEventListener('click', () => userCardModal.classList.remove('show'));
userCardModal.addEventListener('click', e => {
    if (e.target === userCardModal) userCardModal.classList.remove('show');
});

// 事件委托：点击用户名打开名片
document.addEventListener('click', function(e) {
    const el = e.target.closest('.card-author, .comment-author');
    if (!el) return;
    e.preventDefault();
    e.stopPropagation();
    const userId = el.dataset.userId;
    if (!userId) return;
    showUserCard(userId, el.dataset.username, el.dataset.avatar);
});

async function showUserCard(userId, username, avatarUrl) {
    // 先显示本地已知数据
    cardAvatar.src = avatarUrl || '';
    cardUsername.textContent = username || '';
    cardPid.textContent = '...';
    cardSignature.textContent = '';
    cardShareCount.textContent = '...';
    cardTotalLikes.textContent = '...';
    cardAddFriendBtn.style.display = 'none';
    cardAddFriendBtn.dataset.userId = userId;
    userCardModal.classList.add('show');

    // 加载完整信息
    try {
        const res = await fetch(API + `/api/auth/user/${userId}`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) return;
        const data = await res.json();
        cardAvatar.src = data.avatarUrl || '';
        cardUsername.textContent = data.username;
        cardPid.textContent = data.pid;
        cardSignature.textContent = data.signature || '';
        cardShareCount.textContent = data.shareCount;
        cardTotalLikes.textContent = data.totalLikes;
        // 好友按钮状态
        if (data.friendStatus === 'ACCEPTED') {
            cardAddFriendBtn.textContent = '✓ 已添加';
            cardAddFriendBtn.disabled = true;
            cardAddFriendBtn.style.display = 'block';
        } else if (data.friendStatus === 'PENDING') {
            cardAddFriendBtn.textContent = '已申请';
            cardAddFriendBtn.disabled = true;
            cardAddFriendBtn.style.display = 'block';
        } else {
            cardAddFriendBtn.innerHTML = '<i data-lucide="user-plus"></i> 申请好友';
            lucide.createIcons();
            cardAddFriendBtn.disabled = false;
            cardAddFriendBtn.style.display = 'block';
        }
    } catch(e) {
        console.error('加载用户名片失败', e);
    }
}

// 名片添加好友按钮
cardAddFriendBtn.addEventListener('click', async function() {
    const userId = parseInt(this.dataset.userId);
    if (!userId || this.disabled) return;
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

init();