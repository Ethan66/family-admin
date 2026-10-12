/* 轻量 Markdown 解析器 —— 不依赖任何 CDN，离线可用
   支持：标题 / 段落 / 有序无序列表 / 任务清单 / 引用 / 分割线
        / 表格 / 围栏代码块 / 行内粗体斜体删除线行内代码链接图片 */
(function (global) {
  'use strict';

  function esc(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function inline(text) {
    var s = esc(text);
    var codes = [];
    s = s.replace(/`([^`]+)`/g, function (m, c) {
      codes.push('<code>' + c + '</code>');
      return '\u0000C' + (codes.length - 1) + '\u0000';
    });
    s = s.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, function (m, alt, url) {
      return '<img src="' + url + '" alt="' + alt + '" />';
    });
    s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, function (m, t, u) {
      return '<a href="' + u + '" target="_blank" rel="noreferrer">' + t + '</a>';
    });
    s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>');
    s = s.replace(/~~([^~]+)~~/g, '<del>$1</del>');
    s = s.replace(/\u0000C(\d+)\u0000/g, function (m, i) { return codes[+i]; });
    return s;
  }

  function parseRow(str) {
    return str.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map(function (c) { return c.trim(); });
  }

  function isBlockStart(line) {
    return /^\s*(```|#{1,6}\s|>|\s*([-*+]\s|\d+[.)]\s))/.test(line) ||
      /^\s*(-{3,}|\*{3,}|_{3,})\s*$/.test(line);
  }

  function parse(src) {
    var lines = String(src == null ? '' : src).replace(/\r\n?/g, '\n').split('\n');
    var blocks = [];
    var i = 0;

    while (i < lines.length) {
      var line = lines[i];

      if (/^\s*$/.test(line)) { i++; continue; }

      if (/^\s*```/.test(line)) {
        var lang = line.replace(/^\s*```/, '').trim();
        var buf = []; i++;
        while (i < lines.length && !/^\s*```/.test(lines[i])) { buf.push(lines[i]); i++; }
        i++;
        blocks.push({ t: 'code', lang: lang, text: buf.join('\n') });
        continue;
      }

      var h = /^(#{1,6})\s+(.*)$/.exec(line);
      if (h) { blocks.push({ t: 'h', level: h[1].length, text: h[2] }); i++; continue; }

      if (/^\s*(-{3,}|\*{3,}|_{3,})\s*$/.test(line)) { blocks.push({ t: 'hr' }); i++; continue; }

      if (/^\s*>/.test(line)) {
        var q = [];
        while (i < lines.length && /^\s*>/.test(lines[i])) {
          q.push(lines[i].replace(/^\s*>\s?/, ''));
          i++;
        }
        blocks.push({ t: 'quote', html: parse(q.join('\n')) });
        continue;
      }

      if (/\|/.test(line) && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(lines[i + 1])) {
        var head = parseRow(line);
        i += 2;
        var rows = [];
        while (i < lines.length && /\|/.test(lines[i]) && !/^\s*$/.test(lines[i])) {
          rows.push(parseRow(lines[i]));
          i++;
        }
        blocks.push({ t: 'table', head: head, rows: rows });
        continue;
      }

      if (/^\s*[-*+]\s+/.test(line)) {
        var items = [];
        while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) {
          var txt = lines[i].replace(/^\s*[-*+]\s+/, '');
          var checked = null;
          var cb = /^\[( |x|X)\]\s*/.exec(txt);
          if (cb) { checked = cb[1].toLowerCase() === 'x'; txt = txt.replace(cb[0], ''); }
          i++;
          var sub = [];
          while (i < lines.length && /^\s{2,}[-*+]\s+/.test(lines[i])) {
            sub.push(lines[i].replace(/^\s+[-*+]\s+/, ''));
            i++;
          }
          items.push({ text: txt, checked: checked, sub: sub });
        }
        blocks.push({ t: 'ul', items: items });
        continue;
      }

      if (/^\s*\d+[.)]\s+/.test(line)) {
        var oi = [];
        while (i < lines.length && /^\s*\d+[.)]\s+/.test(lines[i])) {
          oi.push(lines[i].replace(/^\s*\d+[.)]\s+/, ''));
          i++;
        }
        blocks.push({ t: 'ol', items: oi });
        continue;
      }

      var para = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) && !isBlockStart(lines[i])) {
        para.push(lines[i]);
        i++;
      }
      blocks.push({ t: 'p', text: para.join('\n') });
    }

    return blocks;
  }

  function toHtml(b) {
    switch (b.t) {
      case 'h':
        return '<h' + b.level + '>' + inline(b.text) + '</h' + b.level + '>';
      case 'hr':
        return '<hr />';
      case 'code':
        var cls = b.lang ? ' class="lang-' + esc(b.lang) + '"' : '';
        return '<pre><code' + cls + '>' + esc(b.text) + '</code></pre>';
      case 'quote':
        return '<blockquote>' + b.html + '</blockquote>';
      case 'table':
        var th = b.head.map(function (c) { return '<th>' + inline(c) + '</th>'; }).join('');
        var tb = b.rows.map(function (r) {
          return '<tr>' + r.map(function (c) { return '<td>' + inline(c) + '</td>'; }).join('') + '</tr>';
        }).join('');
        return '<table><thead><tr>' + th + '</tr></thead><tbody>' + tb + '</tbody></table>';
      case 'ul':
        var task = b.items.some(function (it) { return it.checked !== null; });
        var li = b.items.map(function (it) {
          var box = it.checked === null ? '' :
            '<input type="checkbox" disabled' + (it.checked ? ' checked' : '') + ' /> ';
          var sub = it.sub.length
            ? '<ul>' + it.sub.map(function (s) { return '<li>' + inline(s) + '</li>'; }).join('') + '</ul>'
            : '';
          return '<li>' + box + inline(it.text) + sub + '</li>';
        }).join('');
        return '<ul' + (task ? ' class="md-task"' : '') + '>' + li + '</ul>';
      case 'ol':
        return '<ol>' + b.items.map(function (t) { return '<li>' + inline(t) + '</li>'; }).join('') + '</ol>';
      default:
        return '<p>' + inline(b.text).replace(/\n/g, '<br />') + '</p>';
    }
  }

  function render(src) {
    if (!src || !String(src).trim()) {
      return '<p class="muted">这篇笔记还没有内容，开始写点什么吧。</p>';
    }
    return parse(src).map(toHtml).join('\n');
  }

  global.Markdown = { render: render };
})(window);
