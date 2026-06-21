/**
 * 知识库管理页面逻辑
 * 依赖: common.js (ChatCommon), api-paths.js (ChatAPI)
 */
; (function () {
  'use strict';

  var C = ChatCommon;

  if (!C.Auth.isLoggedIn()) {
    location.href = '/';
  }

  var API = C.createApi({ baseUrl: '' });

  var editingKbId = null;
  var currentKbId = null;
  var docListTimer = null;

  // ======== 知识库列表 ========
  function loadKBList() {
    API(ChatAPI.KB.LIST).then(function (res) {
      if (!res) return;
      return res.json();
    }).then(function (kbs) {
      var list = document.getElementById('kbList');
      if (!kbs || kbs.length === 0) {
        list.innerHTML = '<div class="empty">暂无知识库，点击右上角新建</div>';
        return;
      }
      list.innerHTML = kbs.map(function (kb) {
        var dc = kb.docCount || kb.documentCount || 0;
        var cc = kb.chunkCount || kb.chunks || 0;
        var ts = kb.totalSize || kb.sizeBytes || 0;
        return '<div class="kb-card" onclick="KBManagerPage.viewDocs(' + kb.id + ',\'' +
          C.escapeAttr(kb.name) + '\')">' +
          '<div class="info">' +
          '<h4>📚 ' + C.escapeHtml(kb.name) + '</h4>' +
          '<div class="meta">' + dc + ' 个文档 · ' + cc + ' 个分块 · ' + C.formatSize(ts) + '</div>' +
          '</div>' +
          '<div class="actions" onclick="event.stopPropagation()">' +
          '<button onclick="KBManagerPage.showEditModal(' + kb.id + ',\'' +
          C.escapeAttr(kb.name) + '\',\'' + C.escapeAttr(kb.description || '') + '\')">编辑</button>' +
          '<button class="btn-del" onclick="KBManagerPage.deleteKB(' + kb.id + ')">删除</button>' +
          '</div>' +
          '</div>';
      }).join('');
    }).catch(function (e) {
      C.showToast('加载知识库失败: ' + e.message);
    });
  }

  // ======== 文档列表 ========
  function viewDocs(kbId, kbName) {
    currentKbId = kbId;
    document.getElementById('docsTitle').textContent = '📁 ' + kbName + ' — 文档列表';
    document.getElementById('docsPanel').style.display = 'block';
    document.getElementById('kbList').parentElement.querySelector('.section-title').style.display = 'none';
    loadDocList();
    if (docListTimer) clearInterval(docListTimer);
    docListTimer = setInterval(loadDocList, 5000);
  }

  function backToList() {
    currentKbId = null;
    document.getElementById('docsPanel').style.display = 'none';
    document.getElementById('kbList').parentElement.querySelector('.section-title').style.display = '';
    if (docListTimer) { clearInterval(docListTimer); docListTimer = null; }
    loadKBList();
  }

  function loadDocList() {
    if (!currentKbId) return;
    API(ChatAPI.KB.DOCS + currentKbId + '/docs').then(function (res) {
      if (!res) return null;
      return res.json();
    }).then(function (docs) {
      if (!docs) return;
      var list = document.getElementById('docList');
      var titleEl = document.getElementById('docsTitle');
      titleEl.textContent = titleEl.textContent.replace(/ — .*$/, '') + ' — ' + docs.length + ' 个文档';
      if (!docs || docs.length === 0) {
        list.innerHTML = '<div class="empty">暂无文档，点击上方上传</div>';
        return;
      }
      list.innerHTML = docs.map(function (d) {
        return '<div class="doc-item">' +
          '<div class="doc-info">' +
          '<div class="doc-name">' + C.escapeHtml(d.fileName) + '</div>' +
          '<div class="doc-meta">' + C.formatSize(d.fileSize) + ' · ' + d.chunkCount +
          ' 分块 · <span class="badge badge-' + d.status.toLowerCase() + '">' + d.status + '</span>' +
          (d.errorMsg ? ' · <span style="color:#ef4444">' + C.escapeHtml(d.errorMsg) + '</span>' : '') +
          '</div>' +
          '</div>' +
          '<div class="doc-actions">' +
          '<button onclick="KBManagerPage.reindexDoc(' + d.id + ')"' +
          (d.status === 'PROCESSING' ? ' disabled' : '') + '>重新索引</button>' +
          '<button onclick="KBManagerPage.deleteDoc(' + d.id + ')" style="color:#ef4444;border-color:#fecaca">删除</button>' +
          '</div>' +
          '</div>';
      }).join('');
      // 全部处理完停掉轮询
      if (docs.every(function (d) { return d.status !== 'PROCESSING'; }) && docListTimer) {
        clearInterval(docListTimer);
        docListTimer = null;
      }
    }).catch(function (e) { console.error(e); });
  }

  // ======== 文档操作 ========
  function uploadDoc(e) {
    var file = e.target.files[0];
    if (!file || !currentKbId) return;
    if (!['txt', 'md', 'pdf'].some(function (ext) { return file.name.toLowerCase().endsWith('.' + ext); })) {
      C.showToast('仅支持 TXT、MD、PDF 格式');
      return;
    }
    var formData = new FormData();
    formData.append('file', file);
    var token = C.Auth.getToken();
    fetch(ChatAPI.KB.DOC_UPLOAD + currentKbId + '/docs/upload', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token },
      body: formData
    }).then(function (res) {
      if (!res.ok) { return res.json().then(function (e) { throw new Error(e.error); }); }
      C.showToast('上传成功，正在处理...');
      loadDocList();
      if (!docListTimer) docListTimer = setInterval(loadDocList, 3000);
    }).catch(function (e) {
      C.showToast('上传失败: ' + e.message);
    });
    e.target.value = '';
  }

  function deleteDoc(docId) {
    if (!confirm('确定删除此文档？')) return;
    var token = C.Auth.getToken();
    fetch(ChatAPI.KB.DOC_DELETE + docId, {
      method: 'DELETE',
      headers: { 'Authorization': 'Bearer ' + token }
    }).then(function () {
      C.showToast('已删除');
      loadDocList();
      loadKBList();
    }).catch(function () { C.showToast('删除失败'); });
  }

  function reindexDoc(docId) {
    var token = C.Auth.getToken();
    fetch(ChatAPI.KB.DOC_REINDEX + docId + '/reindex', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token }
    }).then(function () {
      C.showToast('重新索引已启动');
      loadDocList();
      if (!docListTimer) docListTimer = setInterval(loadDocList, 3000);
    }).catch(function () { C.showToast('操作失败'); });
  }

  // ======== 知识库 CRUD ========
  function showCreateModal() {
    editingKbId = null;
    document.getElementById('kbModalTitle').textContent = '新建知识库';
    document.getElementById('kbName').value = '';
    document.getElementById('kbDesc').value = '';
    document.getElementById('kbError').style.display = 'none';
    document.getElementById('kbSaveBtn').textContent = '创建';
    document.getElementById('kbSaveBtn').onclick = saveKB;
    C.showModal('kbModal');
  }

  function showEditModal(id, name, desc) {
    editingKbId = id;
    document.getElementById('kbModalTitle').textContent = '编辑知识库';
    document.getElementById('kbName').value = name;
    document.getElementById('kbDesc').value = desc || '';
    document.getElementById('kbError').style.display = 'none';
    document.getElementById('kbSaveBtn').textContent = '保存';
    document.getElementById('kbSaveBtn').onclick = saveKB;
    C.showModal('kbModal');
  }

  function closeKbModal() {
    C.hideModal('kbModal');
  }

  function saveKB() {
    var name = document.getElementById('kbName').value.trim();
    var description = document.getElementById('kbDesc').value.trim();
    if (!name) {
      document.getElementById('kbError').textContent = '请输入知识库名称';
      document.getElementById('kbError').style.display = 'block';
      return;
    }
    var url = editingKbId ? ChatAPI.KB.UPDATE + editingKbId : ChatAPI.KB.CREATE;
    var method = editingKbId ? 'PUT' : 'POST';
    fetch(url, {
      method: method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + C.Auth.getToken()
      },
      body: JSON.stringify({ name: name, description: description })
    }).then(function (res) {
      if (!res.ok) { return res.json().then(function (e) { throw new Error(e.error); }); }
      closeKbModal();
      C.showToast(editingKbId ? '已更新' : '已创建');
      loadKBList();
    }).catch(function (e) {
      C.showToast('保存失败: ' + e.message);
    });
  }

  function deleteKB(id) {
    if (!confirm('确定删除此知识库？所有文档和向量数据将被永久删除。')) return;
    var token = C.Auth.getToken();
    fetch(ChatAPI.KB.DELETE + id, {
      method: 'DELETE',
      headers: { 'Authorization': 'Bearer ' + token }
    }).then(function () {
      C.showToast('已删除');
      if (currentKbId === id) backToList();
      loadKBList();
    }).catch(function () { C.showToast('删除失败'); });
  }

  // ======== 初始加载 ========
  loadKBList();

  // ======== 暴露到全局 ========
  window.KBManagerPage = {
    viewDocs: viewDocs,
    backToList: backToList,
    uploadDoc: uploadDoc,
    deleteDoc: deleteDoc,
    reindexDoc: reindexDoc,
    showCreateModal: showCreateModal,
    showEditModal: showEditModal,
    closeKbModal: closeKbModal,
    saveKB: saveKB,
    deleteKB: deleteKB
  };

})();
