/* 应用核心：路由 · 工具 · 弹窗构造器 · 概览与收支模块 */
(function (global) {
  'use strict';

  var FA = global.FA = global.FA || {};
  FA.renderers = FA.renderers || {};

  var TITLES = {
    dashboard: ['家庭概览', '家庭成员的共同视图：账目、试卷、影音与笔记'],
    finance: ['收入支出', '记录家庭的每一笔收入与支出，支持按月份与分类筛选'],
    exam: ['试卷管理', '按科目与年级管理试卷，跟踪成绩变化'],
    photo: ['照片管理', '按相册与时间整理家庭照片'],
    movie: ['影片管理', '管理家庭收藏的影片与录像，记录观看进度'],
    notes: ['学习笔记', '用 Markdown 记录家庭知识与方法，支持实时预览']
  };

  FA.ui = {
    view: 'dashboard',
    q: '',
    finance: { month: '', type: 'all', cat: 'all' },
    exam: { subject: 'all', grade: 'all', status: 'all' },
    photo: { album: 'all', month: 'all' },
    movie: { type: 'all', status: 'all' },
    notes: { id: null, tag: 'all' }
  };

  /* ============ 工具 ============ */
  // views.js 可能已先建好 FA.h 的壳，这里合并进去而不是替换，避免它持到旧引用
  var h = FA.h = Object.assign(FA.h || {}, {
    esc: function (s) {
      return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    },
    money: function (n) {
      return '¥' + (Number(n) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    },
    plain: function (n, d) {
      var f = d === undefined ? 2 : d;
      return (Number(n) || 0).toLocaleString('zh-CN', { minimumFractionDigits: f, maximumFractionDigits: f });
    },
    monthOf: function (date) { return String(date || '').slice(0, 7); },
    currentMonth: function () { return Store.today().slice(0, 7); },
    sortDesc: function (arr, key) {
      return arr.slice().sort(function (a, b) { return String(b[key]).localeCompare(String(a[key])); });
    },
    scoreClass: function (score, full) {
      if (score === null || score === undefined || score === '') return '';
      var pct = (Number(score) / (Number(full) || 100)) * 100;
      if (pct >= 90) return 'good';
      if (pct >= 80) return 'mid';
      if (pct >= 60) return 'warn';
      return 'low';
    },
    sum: function (arr, fn) {
      return arr.reduce(function (s, x) { return s + (Number(fn ? fn(x) : x) || 0); }, 0);
    }
  });

  /* ============ 提示条 ============ */
  FA.toast = function (msg, isError) {
    var root = document.getElementById('toastRoot');
    var el = document.createElement('div');
    el.className = 'toast' + (isError ? ' is-error' : '');
    el.innerHTML = '<svg viewBox="0 0 24 24"><path d="M5 13l4 4L19 7"/></svg><span>' + h.esc(msg) + '</span>';
    root.appendChild(el);
    setTimeout(function () { el.remove(); }, 2200);
  };

  /* ============ 弹窗 ============ */
  var overlay = null;
  function getOverlay() {
    if (!overlay) overlay = document.getElementById('modalOverlay');
    return overlay;
  }
  function closeModal() {
    var o = getOverlay();
    o.hidden = true;
    o.innerHTML = '';
  }
  FA.closeModal = closeModal;

  /* 通用表单弹窗
     fields: {key,label,type(text|number|date|select|textarea|seg|range),options,value,placeholder,full,prefix,suffix,hint,min,max,step} */
  FA.openForm = function (opts) {
    var o = getOverlay();
    var body = opts.fields.map(function (f) {
      var full = f.full ? ' field-full' : '';
      var inner = '';
      if (f.type === 'select') {
        inner = '<select class="control" data-key="' + f.key + '">' +
          f.options.map(function (op) {
            var val = typeof op === 'string' ? op : op.value;
            var label = typeof op === 'string' ? op : op.label;
            return '<option value="' + h.esc(val) + '"' + (String(val) === String(f.value) ? ' selected' : '') + '>' + h.esc(label) + '</option>';
          }).join('') + '</select>';
      } else if (f.type === 'textarea') {
        inner = '<textarea class="control" rows="' + (f.rows || 4) + '" data-key="' + f.key + '" placeholder="' + h.esc(f.placeholder || '') + '" style="height:auto;padding:10px 12px;resize:vertical">' + h.esc(f.value || '') + '</textarea>';
      } else if (f.type === 'seg') {
        inner = '<div class="seg" data-key="' + f.key + '">' +
          f.options.map(function (op) {
            return '<button type="button" class="seg-btn' + (op === f.value ? ' is-active' : '') + '" data-val="' + h.esc(op) + '">' + h.esc(op) + '</button>';
          }).join('') + '</div>';
      } else if (f.type === 'range') {
        inner = '<div class="input-affix"><input class="control" type="range" min="' + (f.min || 0) + '" max="' + (f.max || 100) + '" step="' + (f.step || 1) + '" data-key="' + f.key + '" value="' + (f.value || 0) + '" style="padding:0;border:none;box-shadow:none" /><span class="suffix">' + h.esc(f.value || 0) + '%</span></div>';
      } else {
        var type = f.type === 'number' ? 'number' : (f.type === 'date' ? 'date' : 'text');
        inner = '<div class="input-affix">' +
          (f.prefix ? '<span class="affix">' + h.esc(f.prefix) + '</span>' : '') +
          '<input class="control" type="' + type + '" data-key="' + f.key + '" value="' + h.esc(f.value == null ? '' : f.value) + '" placeholder="' + h.esc(f.placeholder || '') + '"' +
          (f.prefix ? ' style="padding-left:28px"' : '') +
          (f.min !== undefined ? ' min="' + f.min + '"' : '') + (f.max !== undefined ? ' max="' + f.max + '"' : '') +
          (f.step !== undefined ? ' step="' + f.step + '"' : '') + ' />' +
          (f.suffix ? '<span class="suffix">' + h.esc(f.suffix) + '</span>' : '') +
          '</div>';
      }
      var hint = f.hint ? '<span class="form-hint">' + h.esc(f.hint) + '</span>' : '';
      return '<div class="form-item' + full + '"><label>' + h.esc(f.label) + '</label>' + inner + hint + '</div>';
    }).join('');

    o.innerHTML =
      '<div class="modal"' + (opts.width ? ' style="width:min(' + opts.width + 'px,100%)"' : '') + '>' +
        '<div class="modal-head"><div><h3>' + h.esc(opts.title) + '</h3>' +
          (opts.desc ? '<p>' + h.esc(opts.desc) + '</p>' : '') + '</div>' +
          '<button class="modal-close" data-close aria-label="关闭"><svg viewBox="0 0 24 24"><path d="M6 6l12 12"/><path d="M18 6L6 18"/></svg></button></div>' +
        '<div class="modal-body">' + (opts.html || '') + body + '</div>' +
        '<div class="modal-foot"><span class="spacer"></span>' +
          '<button class="btn btn-ghost" data-close>取消</button>' +
          '<button class="btn btn-primary" data-submit>' + h.esc(opts.submitText || '保存') + '</button>' +
        '</div>' +
      '</div>';
    o.hidden = false;

    var modal = o.querySelector('.modal');
    modal.addEventListener('click', function (e) {
      if (e.target.closest('[data-close]')) closeModal();
      var seg = e.target.closest('.seg-btn');
      if (seg) {
        seg.parentNode.querySelectorAll('.seg-btn').forEach(function (s) { s.classList.remove('is-active'); });
        seg.classList.add('is-active');
      }
      var rng = e.target.closest('input[type="range"]');
      if (rng) {
        var suf = rng.parentNode.querySelector('.suffix');
        if (suf) suf.textContent = rng.value + '%';
      }
      if (e.target.closest('[data-submit]')) {
        var values = {};
        modal.querySelectorAll('[data-key]').forEach(function (el) {
          if (el.classList.contains('seg')) {
            var active = el.querySelector('.seg-btn.is-active');
            values[el.dataset.key] = active ? active.dataset.val : '';
          } else if (el.dataset.key && el.type === 'range') {
            values[el.dataset.key] = Number(el.value);
          } else if (el.dataset.key) {
            values[el.dataset.key] = el.value;
          }
        });
        if (opts.collect) Object.assign(values, opts.collect(modal) || {});
        var err = opts.validate ? opts.validate(values) : null;
        if (err) { FA.toast(err, true); return; }
        opts.onSubmit(values);
        closeModal();
      }
    });
  };

  FA.openConfirm = function (opts) {
    var o = getOverlay();
    o.innerHTML =
      '<div class="modal" style="width:min(440px,100%)">' +
        '<div class="modal-head"><div><h3>' + h.esc(opts.title || '确认删除') + '</h3><p>此操作不可撤销</p></div>' +
        '<button class="modal-close" data-close aria-label="关闭"><svg viewBox="0 0 24 24"><path d="M6 6l12 12"/><path d="M18 6L6 18"/></svg></button></div>' +
        '<div class="modal-body" style="grid-template-columns:1fr">' +
          '<div class="confirm-wrap">' +
            '<div class="confirm-icon"><svg viewBox="0 0 24 24"><path d="M4 7h16"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M6 7l1 13h10l1-13"/><path d="M9 7V4h6v3"/></svg></div>' +
            '<div class="confirm-title">' + h.esc(opts.heading || '删除这条记录？') + '</div>' +
            '<div class="confirm-desc">' + h.esc(opts.desc || '删除后无法恢复。') + '</div>' +
            (opts.target ? '<div class="confirm-target">' + h.esc(opts.target) + '</div>' : '') +
          '</div>' +
        '</div>' +
        '<div class="modal-foot"><span class="spacer"></span>' +
          '<button class="btn btn-ghost" data-close>取消</button>' +
          '<button class="btn btn-danger" data-confirm>' + h.esc(opts.confirmText || '确认删除') + '</button>' +
        '</div>' +
      '</div>';
    o.hidden = false;
    o.addEventListener('click', function (e) {
      if (e.target === o || e.target.closest('[data-close]')) closeModal();
      if (e.target.closest('[data-confirm]')) { closeModal(); opts.onConfirm(); }
    });
  };

  /* ============ 收支表单 ============ */
  FA.openTxForm = function (tx) {
    var isEdit = !!tx;
    var cats = tx ? (tx.type === 'income' ? Store.catsIncome : Store.catsExpense) : Store.catsExpense;
    FA.openForm({
      title: isEdit ? '编辑记录' : '记一笔',
      desc: '记录一笔家庭收入或支出',
      fields: [
        { key: 'type', label: '类型', type: 'seg', options: ['支出', '收入'], value: tx ? (tx.type === 'income' ? '收入' : '支出') : '支出', full: true },
        { key: 'amount', label: '金额', type: 'number', prefix: '¥', value: tx ? tx.amount : '', min: 0, step: '0.01' },
        { key: 'date', label: '日期', type: 'date', value: tx ? tx.date : Store.today() },
        { key: 'category', label: '分类', type: 'select', options: cats, value: tx ? tx.category : '餐饮' },
        { key: 'member', label: '成员', type: 'select', options: Store.members, value: tx ? tx.member : '家庭公共' },
        { key: 'note', label: '备注', value: tx ? tx.note : '', placeholder: '例如：周末家庭聚餐', full: true, hint: '备注会显示在列表与搜索结果中' }
      ],
      submitText: isEdit ? '保存修改' : '保存记录',
      validate: function (v) {
        if (!v.amount || Number(v.amount) <= 0) return '请输入大于 0 的金额';
        if (!v.date) return '请选择日期';
        return null;
      },
      onSubmit: function (v) {
        var payload = {
          type: v.type === '收入' ? 'income' : 'expense',
          amount: Number(v.amount),
          date: v.date,
          category: v.category,
          member: v.member,
          note: v.note
        };
        if (isEdit) {
          Store.update('tx', tx.id, payload);
          FA.toast('记录已更新');
        } else {
          Store.insert('tx', payload);
          FA.toast('已记录一笔' + (payload.type === 'income' ? '收入' : '支出'));
        }
        FA.render();
      }
    });
    // 切换收支时联动分类选项
    var modal = getOverlay().querySelector('.modal');
    modal.addEventListener('click', function (e) {
      var seg = e.target.closest('.seg-btn');
      if (!seg) return;
      var sel = modal.querySelector('[data-key="category"]');
      var list = seg.dataset.val === '收入' ? Store.catsIncome : Store.catsExpense;
      sel.innerHTML = list.map(function (c) { return '<option value="' + c + '">' + c + '</option>'; }).join('');
    });
  };

  /* ============ 视图：家庭概览 ============ */
  FA.renderers.dashboard = function () {
    var month = h.currentMonth();
    var tx = Store.list('tx');
    var inMonth = tx.filter(function (t) { return h.monthOf(t.date) === month; });
    var income = h.sum(inMonth.filter(function (t) { return t.type === 'income'; }), function (t) { return t.amount; });
    var expense = h.sum(inMonth.filter(function (t) { return t.type === 'expense'; }), function (t) { return t.amount; });
    var exams = Store.list('exams'), photos = Store.list('photos'), movies = Store.list('movies');

    var pie = categoryPie(inMonth.filter(function (t) { return t.type === 'expense'; }), expense);

    var recent = h.sortDesc(tx, 'date').slice(0, 5).map(function (t) {
      return '<div class="list-item">' +
        '<div class="thumb" style="background:' + (t.type === 'income' ? 'var(--income-weak);color:var(--income)' : 'var(--expense-weak);color:var(--expense)') + '">' + h.esc(t.category.slice(0, 1)) + '</div>' +
        '<div class="main"><div class="title">' + h.esc(t.note || t.category) + '</div>' +
        '<div class="sub">' + h.esc(t.date) + ' · ' + h.esc(t.category) + '</div></div>' +
        '<div class="amount ' + (t.type === 'income' ? 'up' : 'down') + '">' + (t.type === 'income' ? '+' : '-') + h.money(t.amount) + '</div>' +
        '</div>';
    }).join('');

    var notes = h.sortDesc(Store.list('notes'), 'updated').slice(0, 3).map(function (n) {
      return '<div class="list-item">' +
        '<div class="thumb" style="background:var(--primary-weak);color:var(--primary)">' + h.esc(n.tag.slice(0, 1)) + '</div>' +
        '<div class="main"><div class="title">' + h.esc(n.title) + '</div><div class="sub">' + h.esc(n.updated) + '</div></div>' +
        '<span class="tag tag-primary">' + h.esc(n.tag) + '</span></div>';
    }).join('');

    return '<div class="stat-grid">' +
        stat('本月收入', h.money(income), '本月共 ' + inMonth.filter(function (t) { return t.type === 'income'; }).length + ' 笔', 'up', 'income') +
        stat('本月支出', h.money(expense), '本月共 ' + inMonth.filter(function (t) { return t.type === 'expense'; }).length + ' 笔', 'down', 'expense') +
        stat('本月结余', h.money(income - expense),
          !income ? '本月暂无收入' : ('支出占收入 ' + h.plain((expense / income) * 100, 1) + '%'), '', 'primary') +
        stat('试卷与影音', String(exams.length + photos.length + movies.length), '试卷 ' + exams.length + ' · 照片 ' + photos.length + ' · 影片 ' + movies.length, '', 'violet') +
      '</div>' +
      '<div class="grid-7-5">' +
        '<div class="card"><div class="card-head"><div><h3>本月支出结构</h3><p>按分类统计 · 共 ' + h.money(expense) + '</p></div></div>' +
        '<div class="card-body">' + pie + '</div></div>' +
        '<div class="stack">' +
          '<div class="card"><div class="card-head"><div><h3>最近记账</h3><p>近 5 条记录</p></div>' +
          '<button class="btn btn-ghost btn-sm spacer" data-act="go" data-view="finance">查看全部</button></div>' +
          '<div class="card-body"><div class="list">' + (recent || emptyRow('还没有记账记录')) + '</div></div></div>' +
          '<div class="card"><div class="card-head"><div><h3>最新笔记</h3><p>Markdown 学习笔记</p></div>' +
          '<button class="btn btn-ghost btn-sm spacer" data-act="go" data-view="notes">进入笔记</button></div>' +
          '<div class="card-body"><div class="list">' + (notes || emptyRow('还没有笔记')) + '</div></div></div>' +
        '</div>' +
      '</div>';
  };

  function stat(label, value, foot, tone, color) {
    var map = { income: 'var(--income)', expense: 'var(--expense)', primary: 'var(--primary)', violet: 'var(--violet)' };
    return '<div class="card stat"><div class="stat-label"><i class="dot" style="background:' + (map[color] || 'var(--primary)') + '"></i>' + h.esc(label) + '</div>' +
      '<div class="stat-value ' + (tone ? tone : '') + '">' + value + '</div>' +
      '<div class="stat-foot">' + h.esc(foot) + '</div></div>';
  }
  FA.stat = stat;

  function emptyRow(text) {
    return '<div class="list-item"><div class="main"><div class="sub">' + h.esc(text) + '</div></div></div>';
  }
  FA.emptyRow = emptyRow;

  function categoryBars(list, total) {
    if (!list.length) return '<div class="empty"><div class="empty-title">暂无支出数据</div></div>';
    var map = {};
    list.forEach(function (t) { map[t.category] = (map[t.category] || 0) + Number(t.amount || 0); });
    var arr = Object.keys(map).map(function (k) { return { name: k, value: map[k] }; })
      .sort(function (a, b) { return b.value - a.value; });
    var colors = ['var(--primary)', 'var(--amber)', 'var(--violet)', 'var(--teal)', 'var(--income)', 'var(--expense)', 'var(--text-3)'];
    return '<div class="bars">' + arr.map(function (c, i) {
      var pct = total ? (c.value / total) * 100 : 0;
      return '<div class="bar-row"><div class="bar-meta">' +
        '<span class="name"><i style="background:' + colors[i % colors.length] + '"></i>' + h.esc(c.name) + '</span>' +
        '<span class="val">' + h.money(c.value) + ' · ' + h.plain(pct, 1) + '%</span></div>' +
        '<div class="bar-track"><div class="bar-fill" style="width:' + pct.toFixed(1) + '%;background:' + colors[i % colors.length] + '"></div></div></div>';
    }).join('') + '</div>';
  }
  FA.categoryBars = categoryBars;

  /* 环形图（饼状图）：本月支出结构。纯 SVG 路径生成，无外部依赖。
     数据由 Store 实时聚合，与条形图共用同一份分类金额。 */
  function categoryPie(list, total) {
    if (!list.length || !total) {
      return '<div class="empty"><div class="empty-title">暂无支出数据</div></div>';
    }
    var map = {};
    list.forEach(function (t) { map[t.category] = (map[t.category] || 0) + Number(t.amount || 0); });
    var arr = Object.keys(map).map(function (k) { return { name: k, value: map[k] }; })
      .sort(function (a, b) { return b.value - a.value; });
    // 与条形图一致的分类色序（主蓝 / 琥珀 / 紫 / 青 / 收入红 / 支出绿 / 灰）
    var colors = ['var(--primary)', 'var(--amber)', 'var(--violet)', 'var(--teal)', 'var(--income)', 'var(--expense)', 'var(--text-3)'];
    var cx = 100, cy = 100, R = 84, r = 54, angle = -90;
    var paths = '', legend = '';
    arr.forEach(function (d, i) {
      var color = colors[i % colors.length];
      var frac = total ? d.value / total : 0;
      var pct = frac * 100;
      if (frac > 0) {
        if (frac >= 0.9999) {
          // 占满整圆时退化为圆环，避免 360° 弧线接缝问题
          paths += '<circle cx="100" cy="100" r="' + ((R + r) / 2) + '" fill="none" stroke="' + color + '" stroke-width="' + (R - r) + '"></circle>';
        } else {
          var a0 = angle, a1 = angle + frac * 360; angle = a1;
          var large = (a1 - a0) > 180 ? 1 : 0;
          var rad = Math.PI / 180;
          var x0 = cx + R * Math.cos(a0 * rad), y0 = cy + R * Math.sin(a0 * rad);
          var x1 = cx + R * Math.cos(a1 * rad), y1 = cy + R * Math.sin(a1 * rad);
          var xi1 = cx + r * Math.cos(a1 * rad), yi1 = cy + r * Math.sin(a1 * rad);
          var xi0 = cx + r * Math.cos(a0 * rad), yi0 = cy + r * Math.sin(a0 * rad);
          paths += '<path d="M' + x0.toFixed(2) + ' ' + y0.toFixed(2) +
            ' A' + R + ' ' + R + ' 0 ' + large + ' 1 ' + x1.toFixed(2) + ' ' + y1.toFixed(2) +
            ' L' + xi1.toFixed(2) + ' ' + yi1.toFixed(2) +
            ' A' + r + ' ' + r + ' 0 ' + large + ' 0 ' + xi0.toFixed(2) + ' ' + yi0.toFixed(2) + ' Z" fill="' + color + '"></path>';
        }
      }
      legend += '<div class="lg-row"><span class="lg-dot" style="background:' + color + '"></span>' +
        '<span class="lg-name">' + h.esc(d.name) + '</span>' +
        '<span class="lg-val">¥' + h.plain(d.value, 0) + '</span>' +
        '<span class="lg-pct">' + pct.toFixed(1) + '%</span></div>';
    });
    return '<div class="pie-wrap">' +
      '<div class="pie">' +
        '<svg viewBox="0 0 200 200" width="188" height="188">' + paths + '</svg>' +
        '<div class="pie-center">' +
          '<span class="pc-label">本月支出</span>' +
          '<span class="pc-value">¥' + h.plain(total, 0) + '</span>' +
          '<span class="pc-sub">' + arr.length + ' 个分类</span>' +
        '</div>' +
      '</div>' +
      '<div class="pie-legend">' + legend + '</div>' +
    '</div>';
  }
  FA.categoryPie = categoryPie;

  /* ============ 视图：收入支出 ============ */
  function filterTx() {
    var f = FA.ui.finance, q = FA.ui.q.trim();
    return Store.list('tx').filter(function (t) {
      if (f.month && f.month !== 'all' && h.monthOf(t.date) !== f.month) return false;
      if (f.type !== 'all' && t.type !== f.type) return false;
      if (f.cat !== 'all' && t.category !== f.cat) return false;
      if (q && (String(t.note || '') + t.category + t.member).indexOf(q) === -1) return false;
      return true;
    });
  }

  FA.renderers.finance = function () {
    var f = FA.ui.finance;
    var all = Store.list('tx');
    var months = {};
    all.forEach(function (t) { months[h.monthOf(t.date)] = 1; });
    months[h.currentMonth()] = 1;
    var monthList = Object.keys(months).sort().reverse();
    if (!f.month || monthList.indexOf(f.month) === -1) f.month = monthList[0];

    var list = h.sortDesc(filterTx(), 'date');
    var income = h.sum(list.filter(function (t) { return t.type === 'income'; }), function (t) { return t.amount; });
    var expense = h.sum(list.filter(function (t) { return t.type === 'expense'; }), function (t) { return t.amount; });
    var base = /^\d{4}-\d{2}$/.test(f.month) ? new Date(f.month + '-01T00:00:00') : new Date();
    var daysInMonth = new Date(base.getFullYear(), base.getMonth() + 1, 0).getDate() || 30;

    var catOptions = ['all'].concat(f.type === 'income' ? Store.catsIncome : (f.type === 'expense' ? Store.catsExpense : Store.catsExpense.concat(Store.catsIncome)));

    var rows = list.map(function (t) {
      return '<tr>' +
        '<td class="num muted">' + h.esc(t.date) + '</td>' +
        '<td><span class="tag ' + (t.type === 'income' ? 'tag-income' : 'tag-expense') + '">' + (t.type === 'income' ? '收入' : '支出') + '</span></td>' +
        '<td>' + h.esc(t.category) + '</td>' +
        '<td><div class="cell-title">' + h.esc(t.note || '—') + '</div><div class="cell-sub">' + h.esc(t.member) + '</div></td>' +
        '<td class="right"><span class="amount ' + (t.type === 'income' ? 'up' : 'down') + '">' + (t.type === 'income' ? '+' : '-') + h.money(t.amount) + '</span></td>' +
        '<td class="right"><div class="row-actions">' +
          '<button class="btn-text" data-act="edit-tx" data-id="' + t.id + '">编辑</button>' +
          '<button class="btn-text is-danger" data-act="del-tx" data-id="' + t.id + '">删除</button>' +
        '</div></td></tr>';
    }).join('');

    return '<div class="toolbar">' +
        '<div class="field"><label>月份</label><select class="control" data-flt="finance.month"><option value="all"' + (f.month === 'all' ? ' selected' : '') + '>全部</option>' +
          monthList.map(function (m) { return '<option value="' + m + '"' + (m === f.month ? ' selected' : '') + '>' + m + '</option>'; }).join('') + '</select></div>' +
        '<div class="field"><label>类型</label><select class="control" data-flt="finance.type">' +
          ['all', 'income', 'expense'].map(function (v) {
            return '<option value="' + v + '"' + (f.type === v ? ' selected' : '') + '>' + ({ all: '全部', income: '收入', expense: '支出' })[v] + '</option>';
          }).join('') + '</select></div>' +
        '<div class="field"><label>分类</label><select class="control" data-flt="finance.cat">' +
          catOptions.map(function (c) { return '<option value="' + c + '"' + (f.cat === c ? ' selected' : '') + '>' + (c === 'all' ? '全部分类' : c) + '</option>'; }).join('') + '</select></div>' +
        '<div style="margin-left:auto;align-self:flex-end"><button class="btn btn-primary" data-act="new-tx">' +
          '<svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg><span>新增记录</span></button></div>' +
      '</div>' +
      '<div class="stat-grid">' +
        stat('收入合计', h.money(income), list.filter(function (t) { return t.type === 'income'; }).length + ' 笔', 'up', 'income') +
        stat('支出合计', h.money(expense), list.filter(function (t) { return t.type === 'expense'; }).length + ' 笔', 'down', 'expense') +
        stat('结余', h.money(income - expense), f.month === 'all' ? '全部区间' : f.month, '', 'primary') +
        stat('日均支出', h.money(expense / (daysInMonth || 30)), '按 ' + daysInMonth + ' 天计算', '', 'violet') +
      '</div>' +
      '<div class="grid-7-5">' +
        '<div class="card"><div class="card-head"><div><h3>账目明细</h3><p>共 ' + list.length + ' 笔</p></div></div><div class="card-body">' +
          (list.length ? '<div class="table-wrap"><table><thead><tr><th style="width:104px">日期</th><th style="width:76px">类型</th><th style="width:88px">分类</th><th>备注</th><th class="right" style="width:130px">金额</th><th class="right" style="width:130px">操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
            : '<div class="empty"><div class="empty-title">没有符合条件的记录</div><div class="empty-desc">换个筛选条件，或新增一笔试试</div></div>') +
        '</div></div>' +
        '<div class="card"><div class="card-head"><div><h3>支出 TOP 分类</h3><p>当前筛选结果</p></div></div>' +
        '<div class="card-body">' + categoryBars(list.filter(function (t) { return t.type === 'expense'; }), expense) + '</div></div>' +
      '</div>';
  };

  /* ============ 删除确认（各模块复用） ============ */
  FA.confirmRemove = function (name, id, label) {
    var item = Store.find(name, id);
    FA.openConfirm({
      heading: '删除这条记录？',
      desc: '删除后无法恢复，相关统计会同步更新。',
      target: label || (item ? (item.title || item.name || item.note || '') : ''),
      onConfirm: function () {
        Store.remove(name, id);
        FA.toast('已删除');
        FA.render();
      }
    });
  };

  /* ============ 渲染与路由 ============ */
  FA.render = function () {
    var v = FA.ui.view;
    var el = document.getElementById('view-' + v);
    if (!el) return;
    var renderer = FA.renderers[v];
    el.innerHTML = renderer ? renderer() : '';
    document.getElementById('pageTitle').textContent = TITLES[v][0];
    document.getElementById('pageDesc').textContent = TITLES[v][1];
  };

  function go(view) {
    FA.ui.view = view;
    document.querySelectorAll('.nav-item').forEach(function (n) {
      n.classList.toggle('is-active', n.dataset.view === view);
    });
    document.querySelectorAll('.view').forEach(function (s) {
      s.classList.toggle('is-active', s.id === 'view-' + view);
    });
    document.getElementById('sidebar').classList.remove('is-open');
    document.getElementById('scrim').hidden = true;
    FA.render();
  }
  FA.go = go;

  function setPath(path, value) {
    var seg = path.split('.');
    if (seg.length === 1) FA.ui[seg[0]] = value;
    else FA.ui[seg[0]][seg[1]] = value;
  }

  function txLabel(t) {
    if (!t) return '';
    return (t.note || t.category) + ' · ' + (t.type === 'income' ? '+' : '-') + h.money(t.amount);
  }

  /* ============ 全局事件 ============ */
  document.addEventListener('click', function (e) {
    var nav = e.target.closest('.nav-item');
    if (nav) { go(nav.dataset.view); return; }

    var el = e.target.closest('[data-act]');
    if (!el) return;
    var act = el.dataset.act;
    var id = el.dataset.id;

    switch (act) {
      case 'go':
        go(el.dataset.view); break;
      case 'set':
        setPath(el.dataset.path, el.dataset.val); FA.render(); break;

      case 'new-tx': FA.openTxForm(null); break;
      case 'edit-tx': FA.openTxForm(Store.find('tx', id)); break;
      case 'del-tx': FA.confirmRemove('tx', id, txLabel(Store.find('tx', id))); break;

      case 'new-exam': FA.openExamForm(null); break;
      case 'edit-exam': FA.openExamForm(Store.find('exams', id)); break;
      case 'del-exam': FA.confirmRemove('exams', id, (Store.find('exams', id) || {}).name); break;

      case 'new-photo': FA.openPhotoUpload(); break;
      case 'del-photo': FA.confirmRemove('photos', id, (Store.find('photos', id) || {}).title); break;

      case 'new-movie': FA.openMovieForm(null); break;
      case 'edit-movie': FA.openMovieForm(Store.find('movies', id)); break;
      case 'del-movie': FA.confirmRemove('movies', id, (Store.find('movies', id) || {}).title); break;

      case 'view-note': FA.openNotePreview(id); break;
      case 'del-note':
        FA.confirmRemove('notes', id, (Store.find('notes', id) || {}).title);
        break;

      case 'reset':
        FA.openConfirm({
          heading: '重置为示例数据？',
          desc: '当前所有账目、试卷、照片、影片与笔记都会被清空，替换为初始示例数据。',
          confirmText: '确认重置',
          onConfirm: function () {
            Store.reset();
            FA.ui.notes.id = null;
            FA.toast('已恢复示例数据');
            FA.render();
          }
        });
        break;
    }
  });

  document.addEventListener('change', function (e) {
    var el = e.target.closest('[data-flt]');
    if (!el) return;
    setPath(el.dataset.flt, el.value);
    if (el.dataset.flt === 'finance.type') FA.ui.finance.cat = 'all';
    FA.render();
  });

  var searchInput = document.getElementById('globalSearch');
  if (searchInput) {
    searchInput.addEventListener('input', function () {
      FA.ui.q = searchInput.value;
      if (FA.ui.view !== 'notes') FA.render();
      else FA.render();
    });
  }

  document.getElementById('menuBtn').addEventListener('click', function () {
    document.getElementById('sidebar').classList.toggle('is-open');
    document.getElementById('scrim').hidden = !document.getElementById('sidebar').classList.contains('is-open');
  });
  document.getElementById('scrim').addEventListener('click', function () {
    document.getElementById('sidebar').classList.remove('is-open');
    document.getElementById('scrim').hidden = true;
  });

  document.getElementById('modalOverlay').addEventListener('click', function (e) {
    if (e.target === this) FA.closeModal();
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') FA.closeModal();
  });

  /* ============ 启动 ============ */
  Store.load();
  // 兜底：确保弹窗遮罩与侧栏蒙层在启动时一定是关闭的
  document.getElementById('modalOverlay').hidden = true;
  document.getElementById('scrim').hidden = true;
  FA.render();
})(window);
