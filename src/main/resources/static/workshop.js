const API = '';
let token = sessionStorage.getItem('chat_token') || '';
let username = sessionStorage.getItem('chat_username') || '';
let currentPrompt = null;
let currentSort = 'likes';
let currentSearch = '';

const userDisplay = document.getElementById('userDisplay');
const btnLogout = document.getElementById('btnLogout');
const btnBack = document.getElementById('btnBack');
const uploadBtn = document.getElementById('uploadBtn');
const myPromptsBtn = document.getElementById('myPromptsBtn');
const promptsGrid = document.getElementById('promptsGrid');
const emptyState = document.getElementById('emptyState');
const loadingState = document.getElementById('loadingState');
const searchInput = document.getElementById('searchInput');
const sortSelect = document.getElementById('sortSelect');
const categoryTabs = document.getElementById('categoryTabs');

const uploadModal = document.getElementById('uploadModal');
const promptSelectWrap = document.getElementById('promptSelectWrap');
const promptSelectTrigger = document.getElementById('promptSelectTrigger');
const promptSelectDropdown = document.getElementById('promptSelectDropdown');
const promptSelect = document.getElementById('promptSelect');
const userMessage = document.getElementById('userMessage');
const uploadCategory = document.getElementById('uploadCategory');
const uploadDescription = document.getElementById('uploadDescription');
const imageInput = document.getElementById('imageInput');
const imagePreview = document.getElementById('imagePreview');
const submitUpload = document.getElementById('submitUpload');
const cancelUpload = document.getElementById('cancelUpload');
const cancelUpload2 = document.getElementById('cancelUpload2');

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
const detailAuthorWrap = document.getElementById('detailAuthorWrap');
const detailAvatar = document.getElementById('detailAvatar');
const detailAuthor = document.getElementById('detailAuthor');
const detailCategory = document.getElementById('detailCategory');
const detailViews = document.getElementById('detailViews');
const detailSaves = document.getElementById('detailSaves');
const detailLikes = document.getElementById('detailLikes');
const detailRating = document.getElementById('detailRating');
const detailRatingMeta = document.getElementById('detailRatingMeta');
const detailDate = document.getElementById('detailDate');
const detailDesc = document.getElementById('detailDesc');
const detailImage = document.getElementById('detailImage');
const detailContent = document.getElementById('detailContent');
const detailMessage = document.getElementById('detailMessage');
const detailMessageSection = document.getElementById('detailMessageSection');
const closeDetail = document.getElementById('closeDetail');
const likeBtn = document.getElementById('likeBtn');
const saveBtn = document.getElementById('saveBtn');
const saveBtnText = document.getElementById('saveBtnText');
const downloadBtn = document.getElementById('downloadBtn');
const ratingStars = document.getElementById('ratingStars');

const commentsList = document.getElementById('commentsList');
const commentInput = document.getElementById('commentInput');
const commentSubmitBtn = document.getElementById('commentSubmitBtn');
const replyHint = document.getElementById('replyHint');
const cancelReply = document.getElementById('cancelReply');
let replyToCommentId = null;
let replyToUserName = null;

// 我的创作模态框
const myPromptsModal = document.getElementById('myPromptsModal');
const closeMyPrompts = document.getElementById('closeMyPrompts');
const myPromptsSearch = document.getElementById('myPromptsSearch');
const myPromptsStatusTabs = document.getElementById('myPromptsStatusTabs');
const myPromptsGrid = document.getElementById('myPromptsGrid');
const myPromptsLoading = document.getElementById('myPromptsLoading');
const myPromptsEmpty = document.getElementById('myPromptsEmpty');
let myPromptsData = [];
let myPromptsCurrentStatus = '';
let myPromptsCurrentSearch = '';

// ======== 初始化 ========

function init() {
    if (!token) {
        window.location.href = '/';
        return;
    }
    showLoggedIn(username);
    loadPrompts();
    bindControls();

    // 公告栏收起/展开
    const announcementToggle = document.getElementById('announcementToggle');
    if (announcementToggle) {
        announcementToggle.addEventListener('click', () => {
            const bar = document.getElementById('announcementBar');
            if (bar) {
                bar.classList.toggle('collapsed');
                announcementToggle.textContent = bar.classList.contains('collapsed') ? '+' : '\u00D7';
            }
        });
    }
}

function showLoggedIn(name) {
    userDisplay.innerHTML = `<img class="avatar-mini" src="" alt="" id="hubSelfAvatar"> ${escapeHtml(name)}`;
    fetch(API + '/api/auth/me', {
        headers: { 'Authorization': 'Bearer ' + token }
    }).then(res => res.ok ? res.json() : null).then(user => {
        if (user && user.avatarUrl) {
            const selfAv = document.getElementById('hubSelfAvatar');
            if (selfAv) selfAv.src = user.avatarUrl;
        }
    }).catch(() => {});
}

function bindControls() {
    searchInput.addEventListener('input', debounce(function() {
        currentSearch = this.value.trim();
        loadPrompts();
    }, 400));

    sortSelect.addEventListener('change', function() {
        currentSort = this.value;
        loadPrompts();
    });
}

function debounce(fn, delay) {
    let timer;
    return function(...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), delay);
    };
}

// ======== 数据加载 ========

async function loadPrompts() {
    showLoading(true);
    try {
        let url;
        if (currentSearch) {
            url = API + `/api/prompts-hub/search?q=${encodeURIComponent(currentSearch)}&page=0&size=100`;
        } else {
            url = API + `/api/prompts-hub?page=0&size=100&sort=${currentSort}`;
        }
        const res = await fetch(url, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) {
            if (res.status === 401) { logout(); return; }
            return;
        }
        const pageData = await res.json();
        const prompts = pageData.content || pageData;
        renderPrompts(prompts);
    } catch (e) {
        console.error('加载失败', e);
    }
    showLoading(false);
}

function showLoading(show) {
    loadingState.style.display = show ? 'block' : 'none';
    if (show) {
        promptsGrid.innerHTML = '';
        emptyState.style.display = 'none';
    }
}

// ======== 我的创作模态框 ========

myPromptsBtn.addEventListener('click', openMyPromptsModal);

function openMyPromptsModal() {
    myPromptsModal.classList.add('show');
    myPromptsCurrentStatus = '';
    myPromptsCurrentSearch = '';
    myPromptsSearch.value = '';
    setMyPromptsActiveTab('');
    loadMyPromptsData();
}

closeMyPrompts.addEventListener('click', () => {
    myPromptsModal.classList.remove('show');
});

myPromptsModal.addEventListener('click', (e) => {
    if (e.target === myPromptsModal) {
        myPromptsModal.classList.remove('show');
    }
});

myPromptsSearch.addEventListener('input', debounce(function() {
    myPromptsCurrentSearch = this.value.trim().toLowerCase();
    renderMyPromptsList();
}, 300));

myPromptsStatusTabs.addEventListener('click', function(e) {
    const tab = e.target.closest('.status-tab');
    if (!tab) return;
    const status = tab.dataset.status;
    myPromptsCurrentStatus = status;
    setMyPromptsActiveTab(status);
    loadMyPromptsData();
});

function setMyPromptsActiveTab(status) {
    myPromptsStatusTabs.querySelectorAll('.status-tab').forEach(t => {
        t.classList.toggle('active', t.dataset.status === status);
    });
}

async function loadMyPromptsData() {
    myPromptsLoading.style.display = 'block';
    myPromptsGrid.innerHTML = '';
    myPromptsEmpty.style.display = 'none';
    try {
        let url = API + '/api/prompts-hub/my?page=0&size=50';
        if (myPromptsCurrentStatus) {
            url += '&status=' + myPromptsCurrentStatus;
        }
        const res = await fetch(url, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) { myPromptsLoading.style.display = 'none'; return; }
        const pageData = await res.json();
        myPromptsData = pageData.content || pageData || [];
        renderMyPromptsList();
    } catch (e) {
        console.error('加载我的创作失败', e);
    }
    myPromptsLoading.style.display = 'none';
}

function renderMyPromptsList() {
    let filtered = myPromptsData;
    if (myPromptsCurrentSearch) {
        filtered = myPromptsData.filter(p => {
            const name = (p.name || '').toLowerCase();
            const desc = (p.description || '').toLowerCase();
            return name.includes(myPromptsCurrentSearch) || desc.includes(myPromptsCurrentSearch);
        });
    }
    if (filtered.length === 0) {
        myPromptsGrid.innerHTML = '';
        myPromptsEmpty.style.display = 'block';
        myPromptsEmpty.querySelector('p').textContent = myPromptsCurrentSearch ? '没有匹配的提示词' : '你还没有上传过提示词，快去分享吧！';
        return;
    }
    myPromptsEmpty.style.display = 'none';
    let html = '';
    filtered.forEach(p => {
        const date = new Date(p.createdAt).toLocaleDateString('zh-CN');
        const coverUrl = escapeHtml(p.imageUrl || '');
        const desc = p.description || p.content?.substring(0, 80) || '';
        const starsHtml = renderStarRating(p.avgRating || 0, true);
        const statusLabel = p.status === 'draft' ? '<span class="my-prompt-status status-draft">草稿</span>'
            : p.status === 'rejected' ? '<span class="my-prompt-status status-rejected">审核未通过</span>'
            : p.status === 'removed' ? '<span class="my-prompt-status status-removed">已下架</span>'
            : p.status === 'pending_review' ? '<span class="my-prompt-status status-pending">审核中</span>'
            : p.status === 'published' ? '<span class="my-prompt-status status-published">已发布</span>' : '';
        const actionBtns = [];
        if (p.status === 'draft' || p.status === 'rejected' || p.status === 'removed') {
            actionBtns.push(`<button class="my-prompt-action-btn publish-btn" data-id="${p.id}" title="重新发布"><i data-lucide="send"></i></button>`);
            actionBtns.push(`<button class="my-prompt-action-btn delete-btn" data-id="${p.id}" title="删除"><i data-lucide="trash-2"></i></button>`);
        }
        if (p.status !== 'removed') {
            actionBtns.push(`<button class="my-prompt-action-btn remove-btn" data-id="${p.id}" title="下架"><i data-lucide="archive"></i></button>`);
        }
        html += `<div class="my-prompt-item" data-id="${p.id}">
                    <div class="my-prompt-cover">${coverUrl ? `<img src="${escapeHtml(coverUrl)}" alt="${escapeHtml(p.name)}">` : '<i data-lucide="image"></i>'}</div>
                    <div class="my-prompt-info">
                        <div class="my-prompt-name">${escapeHtml(p.name)}${statusLabel}</div>
                        <div class="my-prompt-desc">${escapeHtml(desc)}</div>
                        <div class="my-prompt-meta">
                            <span>${date}</span>
                            <span class="rating-stars-small">${starsHtml}</span>
                            <span>${Number(p.avgRating || 0).toFixed(1)}</span>
                            <span><i data-lucide="eye"></i> ${p.viewCount || 0}</span>
                            <span><i data-lucide="bookmark"></i> ${p.saveCount || 0}</span>
                            <span><i data-lucide="heart"></i> ${p.likesCount || 0}</span>
                        </div>
                    </div>
                    ${actionBtns.length ? `<div class="my-prompt-actions">${actionBtns.join('')}</div>` : ''}
                </div>`;
    });
    myPromptsGrid.innerHTML = html;
    lucide.createIcons();

    myPromptsGrid.querySelectorAll('.my-prompt-item').forEach(item => {
        item.addEventListener('click', (e) => {
            if (e.target.closest('.my-prompt-action-btn')) return;
            const id = parseInt(item.dataset.id);
            showDetail(id);
        });
    });

    // 下架按钮
    myPromptsGrid.querySelectorAll('.remove-btn').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            const id = parseInt(btn.dataset.id);
            if (!confirm('确定要下架这个提示词吗？')) return;
            await removeMyPrompt(id);
        });
    });

    // 发布按钮
    myPromptsGrid.querySelectorAll('.publish-btn').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            const id = parseInt(btn.dataset.id);
            if (!confirm('确定要提交审核发布吗？')) return;
            await publishMyPrompt(id);
        });
    });

    // 删除按钮
    myPromptsGrid.querySelectorAll('.delete-btn').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            const id = parseInt(btn.dataset.id);
            if (!confirm('确定要永久删除这个提示词吗？此操作不可恢复。')) return;
            await deleteMyPrompt(id);
        });
    });
}

async function removeMyPrompt(id) {
    try {
        const res = await fetch(API + `/api/prompts-hub/${id}/remove`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            loadMyPromptsData();
        } else {
            alert('下架失败');
        }
    } catch (e) {
        console.error('下架失败', e);
    }
}

async function publishMyPrompt(id) {
    try {
        const res = await fetch(API + `/api/prompts-hub/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify({ publish: 'true' })
        });
        if (res.ok) {
            loadMyPromptsData();
            alert('已提交审核');
        } else {
            const err = await res.json().catch(() => ({}));
            alert(err.error || '发布失败');
        }
    } catch (e) {
        console.error('发布失败', e);
    }
}

async function deleteMyPrompt(id) {
    try {
        const res = await fetch(API + `/api/prompts-hub/${id}`, {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            loadMyPromptsData();
        } else {
            const err = await res.json().catch(() => ({}));
            alert(err.error || '删除失败');
        }
    } catch (e) {
        console.error('删除失败', e);
    }
}

// ======== 渲染 ========

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
        const desc = p.description || p.content?.substring(0, 100) || '';
        const starsHtml = renderStarRating(p.avgRating || 0, true);
        const coverUrl = p.imageUrl;
        html += `<div class="prompt-card-hub" data-id="${p.id}">
                    ${coverUrl ? `<div class="card-image-wrap"><img src="${coverUrl}" class="card-image" alt="${p.name}"></div>` : '<div class="card-image-wrap card-image-placeholder"><i data-lucide="image"></i></div>'}
                    <div class="card-body">
                        <h3 class="card-title">${escapeHtml(p.name)}</h3>
                        <p class="card-preview">${escapeHtml(desc)}</p>
                        <div class="card-stats-row">
                            <span class="rating-stars-small">${starsHtml}</span>
                            <span>${Number(p.avgRating || 0).toFixed(1)}</span>
                            <span><i data-lucide="eye"></i> ${p.viewCount || 0}</span>
                            <span><i data-lucide="bookmark"></i> ${p.saveCount || 0}</span>
                        </div>
                        <div class="card-footer">
                            <span class="card-author" data-user-id="${p.userId}" data-username="${escapeHtml(p.userName || '匿名')}" data-avatar="${p.userAvatar || ''}"><img class="avatar-mini"${avatarSrc} alt=""> ${escapeHtml(p.userName || '匿名')}</span>
                            <span class="card-stat-item"><i data-lucide="message-circle"></i> ${p.commentCount || 0}</span>
                            <button class="save-btn-small" data-id="${p.id}"><i data-lucide="bookmark"></i></button>
                            <button class="like-btn-small" data-id="${p.id}"><i data-lucide="heart"></i> <span>${p.likesCount || 0}</span></button>
                        </div>
                    </div>
                </div>`;
    });
    promptsGrid.innerHTML = html;

    lucide.createIcons();

    document.querySelectorAll('.prompt-card-hub').forEach(card => {
        card.addEventListener('click', function(e) {
            if (e.target.closest('.like-btn-small') || e.target.closest('.save-btn-small')) return;
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

    document.querySelectorAll('.save-btn-small').forEach(btn => {
        btn.addEventListener('click', async function(e) {
            e.stopPropagation();
            const id = parseInt(this.dataset.id);
            await toggleSave(id, this);
        });
    });
}

function renderStarRating(rating, small) {
    const full = Math.floor(rating);
    const half = rating - full >= 0.5 ? 1 : 0;
    const empty = 5 - full - half;
    let html = '';
    for (let i = 0; i < full; i++) html += '<i data-lucide="star" fill="#e8b44b"></i>';
    if (half) html += '<i data-lucide="star-half" fill="#e8b44b"></i>';
    for (let i = 0; i < empty; i++) html += '<i data-lucide="star"></i>';
    return html;
}

// ======== 详情 ========

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
        detailAuthorWrap.dataset.userId = p.userId;
        detailAuthorWrap.dataset.username = p.userName || '匿名';
        detailAuthorWrap.dataset.avatar = p.userAvatar || '';
        detailAvatar.src = p.userAvatar || '';
        detailViews.textContent = p.viewCount || 0;
        detailSaves.textContent = p.saveCount || 0;
        detailLikes.textContent = p.likesCount || 0;
        detailRating.textContent = Number(p.avgRating || 0).toFixed(1);
        detailDate.textContent = new Date(p.createdAt).toLocaleString('zh-CN');
        detailDesc.textContent = p.description || '暂无描述';
        detailContent.textContent = p.content;
        detailImage.innerHTML = p.imageUrl ? `<img src="${escapeHtml(p.imageUrl)}" alt="${escapeHtml(p.name)}">` : '';
        
        if (p.userMessage && p.userMessage.trim()) {
            detailMessage.textContent = p.userMessage;
            detailMessageSection.style.display = 'block';
        } else {
            detailMessageSection.style.display = 'none';
        }

        // 收藏状态
        if (p.isSaved) {
            saveBtn.classList.add('saved');
            saveBtnText.textContent = '已收藏';
        } else {
            saveBtn.classList.remove('saved');
            saveBtnText.textContent = '收藏';
        }

        detailModal.classList.add('show');
        loadComments(id);
        resetReply();
        lucide.createIcons();
    } catch (e) {
        console.error('加载详情失败', e);
    }
}

// ======== 收藏 ========

async function toggleSave(promptId, btnEl) {
    try {
        const isSaved = btnEl.classList.contains('saved');
        const url = API + `/api/prompts-hub/${promptId}/${isSaved ? 'unsave' : 'save'}`;
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            if (isSaved) {
                btnEl.classList.remove('saved');
            } else {
                btnEl.classList.add('saved');
            }
            // 同步更新详情页
            if (currentPrompt && currentPrompt.id === promptId) {
                if (isSaved) {
                    saveBtn.classList.remove('saved');
                    saveBtnText.textContent = '收藏';
                } else {
                    saveBtn.classList.add('saved');
                    saveBtnText.textContent = '已收藏';
                }
            }
            loadPrompts();
        }
    } catch (e) {
        console.error('收藏操作失败', e);
    }
}

saveBtn.addEventListener('click', async () => {
    if (!currentPrompt) return;
    const isSaved = saveBtn.classList.contains('saved');
    const url = API + `/api/prompts-hub/${currentPrompt.id}/${isSaved ? 'unsave' : 'save'}`;
    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            if (isSaved) {
                saveBtn.classList.remove('saved');
                saveBtnText.textContent = '收藏';
            } else {
                saveBtn.classList.add('saved');
                saveBtnText.textContent = '已收藏';
            }
            loadPrompts();
        }
    } catch(e) { console.error(e); }
});

// ======== 评分 ========

ratingStars.querySelectorAll('span').forEach(star => {
    star.addEventListener('click', async function() {
        if (!currentPrompt) return;
        const rating = parseInt(this.dataset.rating);
        try {
            const res = await fetch(API + `/api/prompts-hub/${currentPrompt.id}/rate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
                body: JSON.stringify({ rating: rating })
            });
            if (res.ok) {
                updateRatingDisplay(rating);
                loadPrompts();
            } else {
                const err = await res.json();
                alert(err.error || '评分失败');
            }
        } catch(e) {
            console.error('评分失败', e);
        }
    });

    star.addEventListener('mouseenter', function() {
        const r = parseInt(this.dataset.rating);
        ratingStars.querySelectorAll('span').forEach(s => {
            if (parseInt(s.dataset.rating) <= r) s.classList.add('hover');
        });
    });

    star.addEventListener('mouseleave', function() {
        ratingStars.querySelectorAll('span').forEach(s => s.classList.remove('hover'));
    });
});

function updateRatingDisplay(rating) {
    ratingStars.querySelectorAll('span').forEach(s => {
        if (parseInt(s.dataset.rating) <= rating) {
            s.classList.add('active');
        } else {
            s.classList.remove('active');
        }
    });
}

// ======== 点赞 ========

async function likePrompt(id, btnElement) {
    try {
        const res = await fetch(API + `/api/prompts-hub/${id}/like`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.ok) {
            const span = btnElement.querySelector('span');
            if (span) {
                const currentCount = parseInt(span.textContent) || 0;
                span.textContent = currentCount + 1;
            }
            loadPrompts();
        } else {
            const data = await res.json();
            alert(data.error || '点赞失败');
        }
    } catch (e) {
        console.error('点赞失败', e);
    }
}

// ======== 上传 ========

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
        promptSelect.innerHTML = '<option value="">请选择人物卡</option>';
        promptSelectDropdown.innerHTML = '';
        prompts.forEach(p => {
            const option = document.createElement('option');
            option.value = p.id;
            option.textContent = p.name;
            option.dataset.content = p.content;
            promptSelect.appendChild(option);

            const item = document.createElement('div');
            item.className = 'custom-select-item';
            item.textContent = p.name;
            item.dataset.value = p.id;
            item.dataset.content = p.content;
            item.onclick = function(e) {
                e.stopPropagation();
                promptSelect.value = p.id;
                promptSelectTrigger.querySelector('.custom-select-text').textContent = p.name;
                promptSelectWrap.classList.remove('open');
                promptSelectDropdown.querySelectorAll('.custom-select-item').forEach(el => el.classList.remove('selected'));
                item.classList.add('selected');
            };
            promptSelectDropdown.appendChild(item);
        });
    } catch (e) {
        console.error('加载人物卡失败', e);
    }
}

cancelUpload.addEventListener('click', closeUploadModal);
cancelUpload2.addEventListener('click', closeUploadModal);

function closeUploadModal() {
    uploadModal.classList.remove('show');
    userMessage.value = '';
    uploadDescription.value = '';
    if (imageInput) imageInput.value = '';
    if (imagePreview) imagePreview.innerHTML = '';
    // 重置发布方式为默认"提交审核"
    const publishRadio = document.querySelector('input[name="publishMode"][value="publish"]');
    if (publishRadio) publishRadio.checked = true;
}

uploadModal.addEventListener('click', (e) => {
    if (e.target === uploadModal) closeUploadModal();
});

submitUpload.addEventListener('click', async () => {
    const selectedOption = promptSelect.options[promptSelect.selectedIndex];
    if (!selectedOption.value) {
        alert('请选择要上传的人物卡');
        return;
    }
    const name = selectedOption.textContent;
    const content = selectedOption.dataset.content;
    const message = userMessage.value.trim();
    const description = uploadDescription.value.trim();
    const imageFile = imageInput && imageInput.files && imageInput.files[0] ? imageInput.files[0] : null;
    const publishMode = document.querySelector('input[name="publishMode"]:checked');
    const publish = publishMode ? publishMode.value === 'publish' : true;

    submitUpload.disabled = true;
    submitUpload.innerHTML = '<i data-lucide="loader"></i> 上传中...';
    lucide.createIcons();

    try {
        // 1. 创建提示词
        const body = { name, content, publish: String(publish) };
        if (description) body.description = description;
        if (message) body.userMessage = message;

        const createRes = await fetch(API + '/api/prompts-hub/create', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify(body)
        });

        if (!createRes.ok) {
            const err = await createRes.json().catch(() => ({}));
            alert(err.error || '创建失败');
            return;
        }

        const created = await createRes.json();
        const promptId = created.id;

        // 2. 如果有图片，单独上传
        if (imageFile && promptId) {
            const imgFormData = new FormData();
            imgFormData.append('image', imageFile);
            const imgRes = await fetch(API + `/api/prompts-hub/${promptId}/image`, {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token },
                body: imgFormData
            });
            if (!imgRes.ok && imgRes.status === 400) {
                console.warn('图片上传失败，提示词已创建');
            }
        }

        closeUploadModal();
        loadPrompts();
        alert(publish ? '已提交审核，等待管理员审核后公开展示' : '已保存为草稿，可在"我的创作"中查看和发布');
    } catch (e) {
        console.error('上传失败', e);
        alert('网络错误');
    } finally {
        submitUpload.disabled = false;
        submitUpload.innerHTML = '<i data-lucide="upload"></i> 确认上传';
        lucide.createIcons();
    }
});

// ======== 详情模态框操作 ========

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
    await likePrompt(currentPrompt.id, likeBtn);
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
            alert('下载成功！人物卡已保存到你的提示词列表');
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

// ======== 退出/返回 ========

btnLogout.addEventListener('click', logout);
function logout() {
    token = '';
    sessionStorage.removeItem('chat_token');
    sessionStorage.removeItem('chat_username');
    window.location.href = '/';
}
btnBack.addEventListener('click', () => { window.location.href = '/'; });

function escapeHtml(t) {
    const d = document.createElement('div');
    d.textContent = t;
    return d.innerHTML;
}

// ======== 评论区 ========

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
    comments.forEach(c => { html += renderOneComment(c); });
    commentsList.innerHTML = html;
    lucide.createIcons();
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
            <a class="reply-link" data-id="${c.id}" data-name="${escapeHtml(c.userName)}"><i data-lucide="reply"></i> 回复</a>
            <a class="like-comment-link" data-id="${c.id}"><i data-lucide="heart"></i> <span>${c.likesCount || 0}</span></a>
            <a class="delete-comment-link danger" data-id="${c.id}"><i data-lucide="trash-2"></i> 删除</a>
        </div>`;
    if (c.replies && c.replies.length > 0) {
        html += '<div class="comment-replies">';
        c.replies.forEach(r => { html += renderOneComment(r, true); });
        html += '</div>';
    }
    html += '</div>';
    return html;
}

function bindCommentEvents() {
    document.querySelectorAll('.reply-link').forEach(link => {
        link.addEventListener('click', function() {
            replyToCommentId = parseInt(this.dataset.id);
            replyToUserName = this.dataset.name;
            commentInput.placeholder = `回复 @${replyToUserName}...`;
            commentInput.focus();
            replyHint.style.display = 'block';
            replyHint.innerHTML = `回复 <strong>@${escapeHtml(replyToUserName)}</strong> <a id="cancelReply">取消</a>`;
            document.getElementById('cancelReply').addEventListener('click', resetReply);
        });
    });
    document.querySelectorAll('.like-comment-link').forEach(link => {
        link.addEventListener('click', async function() {
            const id = parseInt(this.dataset.id);
            await likeComment(id, this);
        });
    });
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
            const span = el.querySelector('span');
            if (span) { span.textContent = (parseInt(span.textContent) || 0) + 1; }
        } else {
            const data = await res.json();
            alert(data.error || '点赞失败');
        }
    } catch (e) { console.error('点赞评论失败', e); }
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
    } catch (e) { console.error('删除评论失败', e); }
}

function resetReply() {
    replyToCommentId = null;
    replyToUserName = null;
    commentInput.placeholder = '发表评论...';
    replyHint.style.display = 'none';
}

commentSubmitBtn.addEventListener('click', async () => {
    const content = commentInput.value.trim();
    if (!content || !currentPrompt) return;
    commentSubmitBtn.disabled = true;
    commentSubmitBtn.innerHTML = '<i data-lucide="loader"></i> 发送中...';
    lucide.createIcons();
    try {
        const body = { content: content };
        if (replyToCommentId != null) body.parentId = String(replyToCommentId);
        const res = await fetch(API + `/api/prompts-hub/${currentPrompt.id}/comments`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
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
    } catch (e) { console.error('发表评论失败', e); }
    commentSubmitBtn.disabled = false;
    commentSubmitBtn.innerHTML = '<i data-lucide="send"></i> 发布';
    lucide.createIcons();
});

cancelReply.addEventListener('click', resetReply);
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
    cardAvatar.src = avatarUrl || '';
    cardUsername.textContent = username || '';
    cardPid.textContent = '...';
    cardSignature.textContent = '';
    cardShareCount.textContent = '...';
    cardTotalLikes.textContent = '...';
    cardAddFriendBtn.style.display = 'none';
    cardAddFriendBtn.dataset.userId = userId;
    userCardModal.classList.add('show');

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
        if (data.friendStatus === 'ACCEPTED') {
            cardAddFriendBtn.textContent = '已添加';
            cardAddFriendBtn.disabled = true;
            cardAddFriendBtn.style.display = 'block';
        } else if (data.friendStatus === 'PENDING') {
            cardAddFriendBtn.textContent = '已申请';
            cardAddFriendBtn.disabled = true;
            cardAddFriendBtn.style.display = 'block';
        } else {
            cardAddFriendBtn.innerHTML = '<i data-lucide="user-plus"></i> 申请好友';
            cardAddFriendBtn.disabled = false;
            cardAddFriendBtn.style.display = 'block';
        }
        lucide.createIcons();
    } catch(e) { console.error('加载用户名片失败', e); }
}

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

// ======== 自定义下拉框 ========
function togglePromptDropdown() {
    promptSelectWrap.classList.toggle('open');
}
document.addEventListener('click', function(e) {
    if (promptSelectWrap && !promptSelectWrap.contains(e.target)) {
        promptSelectWrap.classList.remove('open');
    }
});

init();
