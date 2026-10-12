/* 视图渲染：试卷管理 · 照片管理 · 影片管理 · 学习笔记 */
(function (global) {
  'use strict';

  var FA = global.FA = global.FA || {};
  FA.renderers = FA.renderers || {};
  // 本文件在 app.js 之前加载，此刻 FA.h 还不存在。这里先建壳，
  // 稍后 app.js 会把工具方法合并进同一个对象，引用因此始终有效。
  var h = FA.h = FA.h || {};

  /* ==================== 试卷管理 ==================== */
  FA.openExamForm = function (exam) {
    var isEdit = !!exam;
    FA.openForm({
      title: isEdit ? '编辑试卷' : '录入试卷',
      desc: '未批改时得分留空，状态会自动置为「待批改」',
      fields: [
        { key: 'name', label: '试卷名称', value: exam ? exam.name : '', placeholder: '例如：九年级上学期数学期中', full: true },
        { key: 'subject', label: '科目', type: 'select', options: Store.subjects, value: exam ? exam.subject : '数学' },
        { key: 'grade', label: '年级', type: 'select', options: Store.grades, value: exam ? exam.grade : '初二' },
        { key: 'date', label: '考试日期', type: 'date', value: exam ? exam.date : Store.today() },
        { key: 'full', label: '满分', type: 'number', suffix: '分', value: exam ? exam.full : 100, min: 1 },
        { key: 'score', label: '得分', type: 'number', suffix: '分', value: exam && exam.score !== null ? exam.score : '', min: 0, placeholder: '未批改可留空' },
        { key: 'status', label: '状态', type: 'select', options: ['已批改', '待批改', '已归档'], value: exam ? exam.status : '待批改' },
        { key: 'note', label: '错题与复盘', value: exam ? exam.note : '', placeholder: '例如：第 18 题函数定义域出错', full: true }
      ],
      submitText: isEdit ? '保存试卷' : '录入试卷',
      validate: function (v) {
        if (!v.name.trim()) return '请填写试卷名称';
        if (!v.full || Number(v.full) <= 0) return '请填写满分';
        if (v.score !== '' && Number(v.score) > Number(v.full)) return '得分不能高于满分';
        return null;
      },
      onSubmit: function (v) {
        var payload = {
          name: v.name.trim(),
          subject: v.subject,
          grade: v.grade,
          date: v.date,
          full: Number(v.full),
          score: v.score === '' ? null : Number(v.score),
          status: v.score === '' ? '待批改' : v.status,
          note: v.note
        };
        if (isEdit) { Store.update('exams', exam.id, payload); FA.toast('试卷已更新'); }
        else { Store.insert('exams', payload); FA.toast('试卷已录入'); }
        FA.render();
      }
    });
  };

  FA.renderers.exam = function () {
    var f = FA.ui.exam, q = FA.ui.q.trim();
    var all = Store.list('exams');
    var list = h.sortDesc(all.filter(function (e) {
      if (f.subject !== 'all' && e.subject !== f.subject) return false;
      if (f.grade !== 'all' && e.grade !== f.grade) return false;
      if (f.status !== 'all' && e.status !== f.status) return false;
      if (q && (e.name + e.subject + e.note).indexOf(q) === -1) return false;
      return true;
    }), 'date');

    var scored = all.filter(function (e) { return e.score !== null && e.score !== undefined; });
    var avg = scored.length ? h.sum(scored, function (e) { return e.score; }) / scored.length : 0;
    var pending = all.filter(function (e) { return e.status === '待批改'; }).length;

    var bySubject = {};
    scored.forEach(function (e) {
      bySubject[e.subject] = bySubject[e.subject] || { sum: 0, n: 0 };
      bySubject[e.subject].sum += Number(e.score);
      bySubject[e.subject].n += 1;
    });
    var weak = '';
    var weakAvg = 0;
    Object.keys(bySubject).forEach(function (s) {
      var a = bySubject[s].sum / bySubject[s].n;
      if (!weak || a < weakAvg) { weak = s; weakAvg = a; }
    });

    var chips = ['all'].concat(Store.subjects).map(function (s) {
      return '<button class="chip' + (f.subject === s ? ' is-active' : '') + '" data-act="set" data-path="exam.subject" data-val="' + s + '">' + (s === 'all' ? '全部科目' : s) + '</button>';
    }).join('');

    var rows = list.map(function (e) {
      var cls = h.scoreClass(e.score, e.full);
      var scoreCell = e.score === null || e.score === undefined
        ? '<span class="muted">—</span>'
        : '<span class="score ' + cls + '">' + e.score + '</span><span class="muted"> / ' + e.full + '</span>';
      var statusTag = e.status === '已批改' ? 'tag-expense' : (e.status === '待批改' ? 'tag-amber' : '');
      return '<tr>' +
        '<td><div class="cell-title">' + h.esc(e.name) + '</div>' + (e.note ? '<div class="cell-sub">' + h.esc(e.note) + '</div>' : '') + '</td>' +
        '<td>' + h.esc(e.subject) + '</td>' +
        '<td>' + h.esc(e.grade) + '</td>' +
        '<td class="num muted">' + h.esc(e.date) + '</td>' +
        '<td class="right">' + scoreCell + '</td>' +
        '<td><span class="tag ' + statusTag + '">' + h.esc(e.status) + '</span></td>' +
        '<td class="right"><div class="row-actions">' +
          '<button class="btn-text" data-act="edit-exam" data-id="' + e.id + '">' + (e.score === null ? '批改' : '编辑') + '</button>' +
          '<button class="btn-text is-danger" data-act="del-exam" data-id="' + e.id + '">删除</button>' +
        '</div></td></tr>';
    }).join('');

    return '<div class="stat-grid">' +
        FA.stat('试卷总数', String(all.length), '本学期共录入 ' + all.length + ' 份', '', 'primary') +
        FA.stat('平均分', scored.length ? h.plain(avg, 1) : '—', scored.length ? '基于 ' + scored.length + ' 份已批改' : '还没有已批改的试卷', '', 'violet') +
        FA.stat('待批改', String(pending), pending ? '点击「批改」即可录入分数' : '全部已批改', '', 'expense') +
        FA.stat('薄弱科目', weak || '—', weak ? '平均 ' + h.plain(weakAvg, 1) + ' 分' : '暂无数据', '', 'amber') +
      '</div>' +
      '<div class="toolbar">' +
        '<div class="chips">' + chips + '</div>' +
        '<select class="control" style="min-width:104px" data-flt="exam.grade"><option value="all">全部年级</option>' +
          Store.grades.map(function (g) { return '<option value="' + g + '"' + (f.grade === g ? ' selected' : '') + '>' + g + '</option>'; }).join('') + '</select>' +
        '<select class="control" style="min-width:104px" data-flt="exam.status"><option value="all">全部状态</option>' +
          ['已批改', '待批改', '已归档'].map(function (s) { return '<option value="' + s + '"' + (f.status === s ? ' selected' : '') + '>' + s + '</option>'; }).join('') + '</select>' +
        '<div style="margin-left:auto;align-self:flex-end"><button class="btn btn-primary" data-act="new-exam">' +
          '<svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg><span>录入试卷</span></button></div>' +
      '</div>' +
        (list.length ? '<div class="table-wrap is-compact"><table><thead><tr><th>试卷名称</th><th style="width:80px">科目</th><th style="width:80px">年级</th><th style="width:100px">考试日期</th><th class="right" style="width:110px">得分</th><th style="width:96px">状态</th><th class="right" style="width:140px">操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
          : '<div class="empty"><div class="empty-title">没有符合条件的试卷</div><div class="empty-desc">换个筛选条件，或录入一份新试卷</div></div>');
  };

  /* ==================== 照片管理 ==================== */
  function downscale(dataUrl, max, cb) {
    var img = new Image();
    img.onload = function () {
      var w = img.width, hh = img.height;
      var scale = Math.min(1, max / Math.max(w, hh));
      var c = document.createElement('canvas');
      c.width = Math.round(w * scale);
      c.height = Math.round(hh * scale);
      c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
      cb(c.toDataURL('image/jpeg', 0.82));
    };
    img.onerror = function () { cb(dataUrl); };
    img.src = dataUrl;
  }

  FA.openPhotoUpload = function () {
    var o = document.getElementById('modalOverlay');
    var picked = [];
    o.innerHTML =
      '<div class="modal">' +
        '<div class="modal-head"><div><h3>上传照片</h3><p>支持一次选择多张，自动按拍摄日期归档</p></div>' +
        '<button class="modal-close" data-close><svg viewBox="0 0 24 24"><path d="M6 6l12 12"/><path d="M18 6L6 18"/></svg></button></div>' +
        '<div class="modal-body">' +
          '<div class="form-item field-full"><label>选择照片</label>' +
            '<div class="upload-drop" data-drop>' +
              '<div class="up-icon"><svg viewBox="0 0 24 24"><path d="M12 16V6"/><path d="M7 11l5-5 5 5"/><path d="M5 19h14"/></svg></div>' +
              '<div class="up-title">拖拽照片到此处，或点击选择</div>' +
              '<div class="up-sub">支持 JPG / PNG / HEIC，单张不超过 20 MB</div>' +
              '<input type="file" accept="image/*" multiple hidden id="photoFile" />' +
            '</div>' +
          '</div>' +
          '<div class="form-item field-full"><label data-picked>已选 0 张</label><div class="pick-grid" data-preview></div></div>' +
          '<div class="form-item"><label>所属相册</label><select class="control" data-key="album">' +
            Store.albums.map(function (a) { return '<option>' + a + '</option>'; }).join('') + '</select></div>' +
          '<div class="form-item"><label>拍摄日期</label><input class="control" type="date" data-key="date" value="' + Store.today() + '" /></div>' +
          '<div class="form-item field-full"><label>照片描述</label><input class="control" data-key="desc" placeholder="例如：外婆家的中秋聚餐" /></div>' +
        '</div>' +
        '<div class="modal-foot"><span class="spacer"></span>' +
          '<button class="btn btn-ghost" data-close>取消</button>' +
          '<button class="btn btn-primary" data-submit>加入相册</button>' +
        '</div>' +
      '</div>';
    o.hidden = false;

    var modal = o.querySelector('.modal');
    var input = modal.querySelector('#photoFile');
    var drop = modal.querySelector('[data-drop]');
    var preview = modal.querySelector('[data-preview]');
    var label = modal.querySelector('[data-picked]');

    function refresh() {
      label.textContent = '已选 ' + picked.length + ' 张';
      preview.innerHTML = picked.map(function (p, i) {
        return '<div class="pick-cell"><img src="' + p.dataUrl + '" alt="" /><button class="photo-del" style="opacity:1" data-rm="' + i + '">×</button></div>';
      }).join('');
    }

    function addFiles(fileList) {
      Array.prototype.forEach.call(fileList, function (file) {
        if (!/^image\//.test(file.type)) return;
        var reader = new FileReader();
        reader.onload = function (ev) {
          downscale(ev.target.result, 900, function (url) {
            picked.push({ name: file.name, dataUrl: url });
            refresh();
          });
        };
        reader.readAsDataURL(file);
      });
    }

    drop.addEventListener('click', function () { input.click(); });
    input.addEventListener('change', function () { addFiles(input.files); });
    ['dragenter', 'dragover'].forEach(function (t) {
      drop.addEventListener(t, function (e) { e.preventDefault(); });
    });
    drop.addEventListener('drop', function (e) {
      e.preventDefault();
      if (e.dataTransfer && e.dataTransfer.files) addFiles(e.dataTransfer.files);
    });
    modal.addEventListener('click', function (e) {
      if (e.target.closest('[data-close]')) FA.closeModal();
      var rm = e.target.closest('[data-rm]');
      if (rm) { picked.splice(Number(rm.dataset.rm), 1); refresh(); return; }
      if (e.target.closest('[data-submit]')) {
        var album = modal.querySelector('[data-key="album"]').value;
        var date = modal.querySelector('[data-key="date"]').value || Store.today();
        var desc = modal.querySelector('[data-key="desc"]').value;
        if (!picked.length) {
          Store.insert('photos', {
            title: desc || '未命名照片', album: album, date: date, desc: desc,
            color: Store.palette[Math.floor(Math.random() * Store.palette.length)], dataUrl: ''
          });
          FA.toast('已添加一张占位照片');
        } else {
          picked.forEach(function (p) {
            Store.insert('photos', {
              title: p.name.replace(/\.[^.]+$/, ''), album: album, date: date, desc: desc,
              color: Store.palette[Math.floor(Math.random() * Store.palette.length)], dataUrl: p.dataUrl
            });
          });
          FA.toast('已添加 ' + picked.length + ' 张照片');
        }
        FA.closeModal();
        FA.render();
      }
    });
  };

  FA.renderers.photo = function () {
    var f = FA.ui.photo, q = FA.ui.q.trim();
    var all = Store.list('photos');
    var list = h.sortDesc(all.filter(function (p) {
      if (f.album !== 'all' && p.album !== f.album) return false;
      if (f.month !== 'all' && h.monthOf(p.date) !== f.month) return false;
      if (q && (p.title + p.desc + p.album).indexOf(q) === -1) return false;
      return true;
    }), 'date');

    var months = {};
    all.forEach(function (p) { months[h.monthOf(p.date)] = 1; });
    var monthList = Object.keys(months).sort().reverse();

    var chips = ['all'].concat(Store.albums).map(function (a) {
      return '<button class="chip' + (f.album === a ? ' is-active' : '') + '" data-act="set" data-path="photo.album" data-val="' + a + '">' + (a === 'all' ? '全部' : a) + '</button>';
    }).join('');

    var groups = {};
    list.forEach(function (p) { (groups[h.monthOf(p.date)] = groups[h.monthOf(p.date)] || []).push(p); });

    var body = Object.keys(groups).sort().reverse().map(function (m) {
      return '<div class="group-label">' + m + ' · ' + groups[m].length + ' 张</div>' +
        '<div class="photo-grid">' + groups[m].map(function (p) {
          var thumb = p.dataUrl ? '<img src="' + p.dataUrl + '" alt="" />' : '';
          return '<div class="photo-card">' +
            '<div class="photo-thumb" style="' + (p.dataUrl ? '' : 'background:' + p.color) + '">' + thumb +
              '<span class="photo-badge">' + h.esc(p.album) + '</span>' +
              '<button class="photo-del" data-act="del-photo" data-id="' + p.id + '">删除</button>' +
            '</div>' +
            '<div class="photo-info"><div class="photo-title">' + h.esc(p.title) + '</div>' +
            '<div class="photo-sub">' + h.esc(p.date) + (p.desc ? ' · ' + h.esc(p.desc) : '') + '</div></div>' +
          '</div>';
        }).join('') + '</div>';
    }).join('');

    return '<div class="toolbar">' +
        '<div class="chips">' + chips + '</div>' +
        '<select class="control" style="min-width:120px" data-flt="photo.month"><option value="all">全部时间</option>' +
          monthList.map(function (m) { return '<option value="' + m + '"' + (f.month === m ? ' selected' : '') + '>' + m + '</option>'; }).join('') + '</select>' +
        '<div style="margin-left:auto;align-self:flex-end"><button class="btn btn-primary" data-act="new-photo">' +
          '<svg viewBox="0 0 24 24"><path d="M12 16V5"/><path d="M7 10l5-5 5 5"/><path d="M5 19h14"/></svg><span>上传照片</span></button></div>' +
      '</div>' +
      '<div class="card"><div class="card-head"><div><h3>照片墙</h3><p>共 ' + list.length + ' 张' + (list.length !== all.length ? '（全部 ' + all.length + ' 张）' : '') + '</p></div></div>' +
      '<div class="card-body">' + (body || '<div class="empty"><div class="empty-title">还没有照片</div><div class="empty-desc">点右上角「上传照片」添加第一张</div></div>') + '</div></div>';
  };

  /* ==================== 影片管理 ==================== */
  FA.openMovieForm = function (movie) {
    var isEdit = !!movie;
    FA.openForm({
      title: isEdit ? '编辑影片' : '添加影片',
      desc: '收录电影、纪录片、剧集或家庭录像',
      fields: [
        { key: 'title', label: '片名', value: movie ? movie.title : '', full: true, placeholder: '例如：海上钢琴师' },
        { key: 'type', label: '类型', type: 'select', options: Store.movieTypes, value: movie ? movie.type : '电影' },
        { key: 'year', label: '年份', type: 'number', value: movie ? movie.year : new Date().getFullYear(), min: 1900, max: 2100 },
        { key: 'director', label: '导演 / 作者', value: movie ? movie.director : '' },
        { key: 'minutes', label: '时长', type: 'number', suffix: '分钟', value: movie ? movie.minutes : '', min: 1 },
        { key: 'rating', label: '评分', type: 'number', value: movie && movie.rating !== null ? movie.rating : '', min: 0, max: 10, step: '0.1', placeholder: '0 - 10' },
        { key: 'status', label: '观看状态', type: 'select', options: Store.movieStatus, value: movie ? movie.status : '想看' },
        { key: 'progress', label: '观看进度', type: 'range', value: movie ? movie.progress : 0, min: 0, max: 100, full: true },
        { key: 'desc', label: '简介', type: 'textarea', value: movie ? movie.desc : '', rows: 3, full: true }
      ],
      submitText: isEdit ? '保存影片' : '加入片库',
      validate: function (v) {
        if (!v.title.trim()) return '请填写片名';
        return null;
      },
      onSubmit: function (v) {
        var payload = {
          title: v.title.trim(),
          type: v.type,
          year: Number(v.year) || new Date().getFullYear(),
          director: v.director,
          minutes: Number(v.minutes) || 0,
          rating: v.rating === '' ? null : Number(v.rating),
          status: v.status,
          progress: v.status === '已看' ? 100 : (v.status === '想看' ? 0 : Number(v.progress)),
          desc: v.desc,
          color: movie ? movie.color : Store.palette[Math.floor(Math.random() * Store.palette.length)]
        };
        if (isEdit) { Store.update('movies', movie.id, payload); FA.toast('影片已更新'); }
        else { Store.insert('movies', payload); FA.toast('影片已加入片库'); }
        FA.render();
      }
    });
  };

  FA.renderers.movie = function () {
    var f = FA.ui.movie, q = FA.ui.q.trim();
    var all = Store.list('movies');
    var list = all.filter(function (m) {
      if (f.type !== 'all' && m.type !== f.type) return false;
      if (f.status !== 'all' && m.status !== f.status) return false;
      if (q && (m.title + m.director + m.desc).indexOf(q) === -1) return false;
      return true;
    });

    var watched = all.filter(function (m) { return m.status === '已看'; }).length;
    var watching = all.filter(function (m) { return m.status === '在看'; }).length;
    var totalHours = h.sum(all, function (m) { return m.minutes; }) / 60;

    var chips = ['all'].concat(Store.movieTypes).map(function (t) {
      return '<button class="chip' + (f.type === t ? ' is-active' : '') + '" data-act="set" data-path="movie.type" data-val="' + t + '">' + (t === 'all' ? '全部' : t) + '</button>';
    }).join('');

    var rows = list.map(function (m) {
      var tag = m.status === '已看' ? 'tag-expense' : (m.status === '在看' ? 'tag-amber' : '');
      var progress = m.status === '在看'
        ? '<div class="progress"><div class="progress-fill" style="width:' + m.progress + '%"></div></div>' +
          '<div class="movie-sub">观看进度 ' + m.progress + '%</div>'
        : '';
      return '<div class="movie-row">' +
        '<div class="poster" style="background:' + m.color + '">' + h.esc(m.year) + '</div>' +
        '<div class="movie-main"><div class="movie-title">' + h.esc(m.title) + '</div>' +
          '<div class="movie-sub">' + h.esc(m.director || '—') + ' · ' + h.esc(m.type) + ' · ' + h.esc(m.year) + '</div>' + progress + '</div>' +
        '<div class="movie-time num">' + (m.minutes ? m.minutes + ' 分钟' : '—') + '</div>' +
        '<div class="rating">' + (m.rating === null || m.rating === undefined ? '<span class="muted">—</span>' : h.plain(m.rating, 1)) + '</div>' +
        '<span class="tag ' + tag + '" style="width:56px;justify-content:center">' + h.esc(m.status) + '</span>' +
        '<div class="row-actions">' +
          '<button class="btn-text" data-act="edit-movie" data-id="' + m.id + '">编辑</button>' +
          '<button class="btn-text is-danger" data-act="del-movie" data-id="' + m.id + '">删除</button>' +
        '</div></div>';
    }).join('');

    return '<div class="stat-grid">' +
        FA.stat('收藏影片', String(all.length), '共 ' + all.length + ' 部', '', 'violet') +
        FA.stat('已看完', String(watched), all.length ? '完成率 ' + h.plain((watched / all.length) * 100, 1) + '%' : '暂无', '', 'expense') +
        FA.stat('观看中', String(watching), watching ? '可记录进度' : '当前没有在看的', '', 'amber') +
        FA.stat('累计时长', h.plain(totalHours, 1), '小时', '', 'primary') +
      '</div>' +
      '<div class="toolbar">' +
        '<div class="chips">' + chips + '</div>' +
        '<select class="control" style="min-width:112px" data-flt="movie.status"><option value="all">全部状态</option>' +
          Store.movieStatus.map(function (s) { return '<option value="' + s + '"' + (f.status === s ? ' selected' : '') + '>' + s + '</option>'; }).join('') + '</select>' +
        '<div style="margin-left:auto;align-self:flex-end"><button class="btn btn-primary" data-act="new-movie">' +
          '<svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg><span>添加影片</span></button></div>' +
      '</div>' +
      '<div class="list-head"><span></span><span>影片</span><span>时长</span><span>评分</span><span>状态</span><span class="right">操作</span></div>' +
      '<div class="movie-list is-compact">' +
        (rows || '<div class="empty"><div class="empty-title">还没有影片</div><div class="empty-desc">点右上角「添加影片」开始建立片库</div></div>') +
      '</div>';
  };

  /* ==================== 学习笔记（列表 + 预览 + 删除） ==================== */
  FA.openNotePreview = function (id) {
    var n = Store.find('notes', id);
    if (!n) return;
    var o = document.getElementById('modalOverlay');
    o.innerHTML =
      '<div class="modal" style="width:min(720px,100%)">' +
        '<div class="modal-head"><div><h3>' + h.esc(n.title) + '</h3>' +
          '<p>' + h.esc(n.tag) + ' · 更新于 ' + h.esc(n.updated) + ' · ' + n.body.length + ' 字</p></div>' +
          '<button class="modal-close" data-close aria-label="关闭"><svg viewBox="0 0 24 24"><path d="M6 6l12 12"/><path d="M18 6L6 18"/></svg></button></div>' +
        '<div class="modal-body" style="grid-template-columns:1fr">' +
          '<div class="md-view"><div class="md-body">' + Markdown.render(n.body) + '</div></div>' +
        '</div>' +
        '<div class="modal-foot"><span class="spacer"></span>' +
          '<button class="btn btn-ghost" data-close>关闭</button></div>' +
      '</div>';
    o.hidden = false;
    o.addEventListener('click', function (e) {
      if (e.target === o || e.target.closest('[data-close]')) FA.closeModal();
    });
  };

  FA.renderers.notes = function () {
    var f = FA.ui.notes, q = FA.ui.q.trim();
    var all = Store.list('notes');
    var list = h.sortDesc(all.filter(function (n) {
      if (f.tag !== 'all' && n.tag !== f.tag) return false;
      if (q && (n.title + n.tag + n.body).indexOf(q) === -1) return false;
      return true;
    }), 'updated');

    var chips = ['all'].concat(Store.noteTags).map(function (t) {
      return '<button class="chip' + (f.tag === t ? ' is-active' : '') + '" data-act="set" data-path="notes.tag" data-val="' + t + '">' +
        (t === 'all' ? '全部标签' : t) + '</button>';
    }).join('');

    var rows = list.map(function (n) {
      return '<tr>' +
        '<td><div class="cell-title">' + h.esc(n.title) + '</div></td>' +
        '<td style="width:88px"><span class="tag tag-primary">' + h.esc(n.tag) + '</span></td>' +
        '<td class="num muted" style="width:150px">' + h.esc(n.updated) + '</td>' +
        '<td class="num muted right" style="width:88px">' + n.body.length + ' 字</td>' +
        '<td class="right" style="width:120px"><div class="row-actions">' +
          '<button class="btn-text" data-act="view-note" data-id="' + n.id + '">预览</button>' +
          '<button class="btn-text is-danger" data-act="del-note" data-id="' + n.id + '">删除</button>' +
        '</div></td></tr>';
    }).join('');

    return '<div class="toolbar"><div class="chips">' + chips + '</div></div>' +
      (list.length
        ? '<div class="table-wrap is-compact"><table><thead><tr>' +
            '<th>笔记标题</th><th style="width:88px">标签</th><th style="width:150px">更新时间</th>' +
            '<th class="right" style="width:88px">字数</th><th class="right" style="width:120px">操作</th>' +
          '</tr></thead><tbody>' + rows + '</tbody></table></div>'
        : '<div class="empty"><div class="empty-title">没有符合条件的笔记</div>' +
          '<div class="empty-desc">换个标签，或清空搜索词试试</div></div>');
  };
})(window);
