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

function init() {
    if (!token) {
        window.location.href = '/';
        return;
    }
    showLoggedIn(username);
    loadPrompts();
}

function showLoggedIn(name) {
    userDisplay.textContent = `👤 ${name}`;
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
        html += `<div class="prompt-card-hub" data-id="${p.id}">
                    ${p.imageUrl ? `<img src="${p.imageUrl}" class="card-image" alt="${p.name}">` : ''}
                    <div class="card-body">
                        <h3 class="card-title">${escapeHtml(p.name)}</h3>
                        <p class="card-preview">${escapeHtml(p.content).substring(0, 100)}...</p>
                        <div class="card-footer">
                            <span class="card-author">👤 ${escapeHtml(p.userName || '匿名')}</span>
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
});

detailModal.addEventListener('click', (e) => {
    if (e.target === detailModal) {
        detailModal.classList.remove('show');
        currentPrompt = null;
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

init();