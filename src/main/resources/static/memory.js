/**
 * 记忆管理页面逻辑
 * 依赖: common.js (ChatCommon), api-paths.js (ChatAPI)
 */
; (function () {
  'use strict';

  var C = ChatCommon;
  var API = C.createApi({ baseUrl: '' });

  if (!C.Auth.isLoggedIn()) {
    document.getElementById('app').innerHTML =
      '<div class="empty"><div class="emoji">🔒</div>请先在主页登录后访问</div>';
  }

  var memories = [];
  var searchResults = [];
  var currentTab = 'all';
  var editingId = null;

  // ======== 辅助函数 ========
  function detailLabel(level) {
    if (level === 'FULL') return '<span class="badge badge-full">清晰</span>';
    if (level === 'BRIEF') return '<span class="badge badge-brief">模糊</span>';
    if (level === 'TITLE') return '<span class="badge badge-title">轮廓</span>';
    return level;
  }

  function sourceLabel(s) {
    if (s === 'MANUAL') return '<span class="badge badge-manual">手动</span>';
    return '<span class="badge badge-auto">自动</span>';
  }

  // ======== 数据加载 ========
  async function loadMemories() {
    try {
      var res = await API(ChatAPI.MEMORY.LIST);
      if (!res) return;
      memories = await res.json();
      updateStats();
      renderList(memories);
    } catch (e) {
      console.error('加载记忆失败', e);
      document.getElementById('memList').innerHTML =
        '<div class="empty">加载失败，请检查网络</div>';
    }
  }

  async function loadEnabled() {
    try {
      var res = await API(ChatAPI.MEMORY.ENABLED);
      if (!res) return;
      var enabled = await res.json();
      renderList(enabled);
    } catch (e) {
      console.error('加载失败', e);
    }
  }

  function updateStats() {
    document.getElementById('statTotal').textContent = memories.length;
    document.getElementById('statEnabled').textContent =
      memories.filter(function (m) { return m.enabled; }).length;
    document.getElementById('statManual').textContent =
      memories.filter(function (m) { return m.source === 'MANUAL'; }).length;
  }

  function renderList(list) {
    var container = document.getElementById('memList');
    if (!list || list.length === 0) {
      var msg = currentTab === 'search'
        ? '没有找到匹配的记忆'
        : '还没有记忆，AI会在对话中自动提取，你也可以手动添加';
      container.innerHTML = '<div class="empty"><div class="emoji">🧠</div>' + msg + '</div>';
      document.getElementById('listCount').textContent = '';
      return;
    }
    document.getElementById('listCount').textContent = '（共 ' + list.length + ' 条）';
    container.innerHTML = list.map(function (m) {
      return '<div class="mem-item' + (m.enabled ? '' : ' disabled') + '">' +
        '<div class="mem-info">' +
        '<div class="mem-text">' + C.escapeHtml(m.value) + '</div>' +
        '<div class="mem-meta">' +
        detailLabel(m.detailLevel) +
        sourceLabel(m.source) +
        '<span>👁 ' + (m.accessCount || 0) + '次</span>' +
        '<span>🕐 ' + C.timeLabel(m.lastAccessedAt) + '</span>' +
        '</div>' +
        '</div>' +
        '<div class="mem-actions">' +
        '<button class="' + (m.enabled ? 'btn-disable' : 'btn-enable') +
        '" onclick="MemoryPage.toggleMem(' + m.id + ',' + !m.enabled + ')">' +
        (m.enabled ? '禁用' : '启用') + '</button>' +
        '<button class="btn-edit" onclick="MemoryPage.openEdit(' + m.id + ',\'' +
        C.escapeAttr(m.value) + '\')">编辑</button>' +
        '<button class="btn-del" onclick="MemoryPage.deleteMem(' + m.id + ')">删除</button>' +
        '</div>' +
        '</div>';
    }).join('');
  }

  // ======== Tab 切换 ========
  function switchTab(tab) {
    currentTab = tab;
    var tabs = document.querySelectorAll('.tabs button');
    tabs.forEach(function (b) { b.classList.remove('active'); });
    var idx = ['all', 'enabled', 'search'].indexOf(tab) + 1;
    document.querySelector('.tabs button:nth-child(' + idx + ')').classList.add('active');
    document.getElementById('searchBar').style.display =
      tab === 'search' ? 'block' : 'none';
    document.getElementById('listTitle').textContent =
      tab === 'all' ? '全部记忆' : tab === 'enabled' ? '已启用记忆' : '搜索结果';
    if (tab === 'all') { renderList(memories); }
    else if (tab === 'enabled') { loadEnabled(); }
    else { renderList(searchResults); }
  }

  // ======== CRUD 操作 ========
  async function addMemory() {
    var input = document.getElementById('newMemoryInput');
    var value = input.value.trim();
    if (!value) return;
    try {
      var res = await API(ChatAPI.MEMORY.ADD, { method: 'POST', body: { value: value } });
      if (!res) return;
      input.value = '';
      C.showToast('已添加');
      await loadMemories();
    } catch (e) {
      C.showToast('添加失败');
    }
  }

  async function searchMemories() {
    var query = document.getElementById('searchInput').value.trim();
    if (!query) return;
    try {
      var res = await API(ChatAPI.MEMORY.SEARCH, {
        method: 'POST', body: { query: query }
      });
      if (!res) return;
      searchResults = await res.json();
      switchTab('search');
      var tabs = document.querySelectorAll('.tabs button');
      tabs.forEach(function (b) { b.classList.remove('active'); });
      document.querySelector('.tabs button:nth-child(3)').classList.add('active');
    } catch (e) {
      C.showToast('搜索失败');
    }
  }

  function openEdit(id, value) {
    editingId = id;
    document.getElementById('editText').value = value;
    document.getElementById('editModal').classList.add('show');
  }

  function closeEdit() {
    editingId = null;
    document.getElementById('editModal').classList.remove('show');
  }

  async function saveEdit() {
    var value = document.getElementById('editText').value.trim();
    if (!value) return;
    try {
      var res = await API(ChatAPI.MEMORY.UPDATE + editingId, {
        method: 'PUT', body: { value: value }
      });
      if (!res || !res.ok) { C.showToast('保存失败'); return; }
      closeEdit();
      C.showToast('已更新');
      await loadMemories();
    } catch (e) {
      C.showToast('保存失败');
    }
  }

  async function toggleMem(id, enabled) {
    try {
      var res = await API(ChatAPI.MEMORY.TOGGLE + id + '/toggle?enabled=' + enabled, {
        method: 'PUT'
      });
      if (!res || !res.ok) { C.showToast('操作失败'); return; }
      await loadMemories();
    } catch (e) {
      C.showToast('操作失败');
    }
  }

  async function deleteMem(id) {
    if (!confirm('确定要删除这条记忆吗？')) return;
    try {
      var res = await API(ChatAPI.MEMORY.DELETE + id, { method: 'DELETE' });
      if (!res || !res.ok) { C.showToast('删除失败'); return; }
      C.showToast('已删除');
      await loadMemories();
    } catch (e) {
      C.showToast('删除失败');
    }
  }

  async function clearAll() {
    if (!confirm('确定要清空全部记忆吗？此操作不可恢复。')) return;
    try {
      var res = await API(ChatAPI.MEMORY.CLEAR, { method: 'DELETE' });
      if (!res || !res.ok) { C.showToast('清空失败'); return; }
      C.showToast('已清空全部记忆');
      await loadMemories();
    } catch (e) {
      C.showToast('清空失败');
    }
  }

  // ======== 初始加载 ========
  if (C.Auth.isLoggedIn()) {
    loadMemories();
  }

  // ======== 暴露到全局 (供 onclick 使用) ========
  window.MemoryPage = {
    switchTab: switchTab,
    addMemory: addMemory,
    searchMemories: searchMemories,
    openEdit: openEdit,
    closeEdit: closeEdit,
    saveEdit: saveEdit,
    toggleMem: toggleMem,
    deleteMem: deleteMem,
    clearAll: clearAll
  };

})();
