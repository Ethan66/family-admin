/* 数据层：localStorage 持久化 + 首次运行的示例数据 */
(function (global) {
  'use strict';

  var KEY = 'family-admin-v1';

  var CATS_EXPENSE = ['居住', '餐饮', '教育', '交通', '医疗', '日用', '其他'];
  var CATS_INCOME = ['薪资', '副业', '理财', '红包', '其他'];
  var MEMBERS = ['家庭公共', '爸爸', '妈妈', '孩子'];
  var SUBJECTS = ['语文', '数学', '英语', '物理', '化学', '历史', '地理', '生物'];
  var GRADES = ['初一', '初二', '初三', '高一', '高二', '高三'];
  var ALBUMS = ['家人', '旅行', '节日', '成长记录', '证件照'];
  var MOVIE_TYPES = ['电影', '纪录片', '动画', '剧集', '家庭录像'];
  var MOVIE_STATUS = ['想看', '在看', '已看'];
  var NOTE_TAGS = ['家庭', '财务', '生活', '教育', '旅行'];
  var PALETTE = [
    'linear-gradient(135deg,#8FB8FF 0%,#3D6BF2 100%)',
    'linear-gradient(135deg,#FFD28F 0%,#F0A02C 100%)',
    'linear-gradient(135deg,#A5E8C6 0%,#17A34A 100%)',
    'linear-gradient(135deg,#C7B8FF 0%,#7A5AF8 100%)',
    'linear-gradient(135deg,#8FE3E8 0%,#12A5A5 100%)',
    'linear-gradient(135deg,#FFB3B8 0%,#E0484B 100%)'
  ];

  function uid() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  }

  function today() {
    var d = new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }
  function pad(n) { return n < 10 ? '0' + n : '' + n; }

  function seed() {
    var m = today().slice(0, 7);
    var d = function (day) { return m + '-' + day; };
    return {
      tx: [
        { id: uid(), date: d('20'), type: 'expense', category: '餐饮', member: '家庭公共', amount: 468, note: '周末家庭聚餐' },
        { id: uid(), date: d('18'), type: 'expense', category: '居住', member: '家庭公共', amount: 3600, note: '房屋租金' },
        { id: uid(), date: d('15'), type: 'income', category: '薪资', member: '爸爸', amount: 18600, note: '9 月工资入账' },
        { id: uid(), date: d('15'), type: 'income', category: '理财', member: '家庭公共', amount: 1240, note: '基金分红' },
        { id: uid(), date: d('12'), type: 'expense', category: '教育', member: '孩子', amount: 1820, note: '兴趣班学费' },
        { id: uid(), date: d('10'), type: 'expense', category: '餐饮', member: '妈妈', amount: 742, note: '生鲜采购' },
        { id: uid(), date: d('08'), type: 'expense', category: '居住', member: '家庭公共', amount: 380, note: '物业费' },
        { id: uid(), date: d('06'), type: 'expense', category: '医疗', member: '妈妈', amount: 640, note: '体检补充项目' },
        { id: uid(), date: d('05'), type: 'expense', category: '交通', member: '爸爸', amount: 200, note: '地铁卡充值' },
        { id: uid(), date: d('03'), type: 'income', category: '副业', member: '妈妈', amount: 4960, note: '稿费结算' },
        { id: uid(), date: d('01'), type: 'expense', category: '日用', member: '家庭公共', amount: 296, note: '洗衣液、纸巾' }
      ],
      exams: [
        { id: uid(), name: '九年级上学期数学期中', subject: '数学', grade: '初三', date: d('19'), full: 100, score: 92, status: '已批改', note: '第 18 题函数定义域出错' },
        { id: uid(), name: '初二物理力学单元测', subject: '物理', grade: '初二', date: d('17'), full: 100, score: 68, status: '已批改', note: '受力分析不熟练' },
        { id: uid(), name: '语文古诗文默写小测', subject: '语文', grade: '初二', date: d('14'), full: 100, score: 96, status: '已批改', note: '' },
        { id: uid(), name: '英语阅读理解专项训练', subject: '英语', grade: '初二', date: d('15'), full: 100, score: 85, status: '已批改', note: '长难句需加强' },
        { id: uid(), name: '数学函数综合模拟卷', subject: '数学', grade: '初三', date: d('10'), full: 100, score: 78, status: '已批改', note: '' },
        { id: uid(), name: '化学实验操作模拟卷', subject: '化学', grade: '初三', date: d('12'), full: 100, score: null, status: '待批改', note: '' },
        { id: uid(), name: '英语听力模拟（三）', subject: '英语', grade: '初三', date: d('08'), full: 100, score: null, status: '待批改', note: '' },
        { id: uid(), name: '七年级历史单元测试', subject: '历史', grade: '初一', date: d('05'), full: 100, score: 88, status: '已归档', note: '' }
      ],
      photos: [
        { id: uid(), title: '周末家庭聚餐', album: '家人', date: d('20'), desc: '外婆家的中秋聚餐', color: PALETTE[0], dataUrl: '' },
        { id: uid(), title: '中秋夜的月亮', album: '节日', date: d('17'), desc: '阳台上拍的满月', color: PALETTE[1], dataUrl: '' },
        { id: uid(), title: '开学第一天', album: '成长记录', date: d('01'), desc: '校门口留影', color: PALETTE[2], dataUrl: '' },
        { id: uid(), title: '公园散步', album: '家人', date: d('13'), desc: '傍晚的公园', color: PALETTE[3], dataUrl: '' },
        { id: uid(), title: '海岛之行 · 第一天', album: '旅行', date: '2026-08-18', desc: '上岛的渡轮上', color: PALETTE[4], dataUrl: '' },
        { id: uid(), title: '海岛之行 · 日落', album: '旅行', date: '2026-08-19', desc: '西海岸日落', color: PALETTE[5], dataUrl: '' },
        { id: uid(), title: '入学证件照底片', album: '证件照', date: '2026-08-25', desc: '白底一寸', color: PALETTE[0], dataUrl: '' },
        { id: uid(), title: '外婆生日', album: '家人', date: '2026-08-06', desc: '家里办的生日宴', color: PALETTE[1], dataUrl: '' }
      ],
      movies: [
        { id: uid(), title: '海上钢琴师', type: '电影', year: 1998, director: '朱塞佩·托纳多雷', minutes: 165, rating: 9.3, status: '已看', progress: 100, desc: '被遗弃在远洋客轮上的孤儿，用钢琴度过了一生。', color: PALETTE[0] },
        { id: uid(), title: '人生一串 第三季', type: '纪录片', year: 2021, director: '陈英杰', minutes: 180, rating: 9.0, status: '在看', progress: 62, desc: '6 集 · 观看至第 4 集', color: PALETTE[4] },
        { id: uid(), title: '疯狂动物城', type: '动画', year: 2016, director: '拜伦·霍华德', minutes: 109, rating: 9.2, status: '已看', progress: 100, desc: '兔子警官与狐狸的搭档故事。', color: PALETTE[3] },
        { id: uid(), title: '中秋家庭录像 2026', type: '家庭录像', year: 2026, director: '爸爸', minutes: 23, rating: null, status: '已看', progress: 100, desc: '自家拍摄的中秋聚会。', color: PALETTE[1] },
        { id: uid(), title: '寻枪', type: '电影', year: 2002, director: '陆川', minutes: 96, rating: 7.8, status: '想看', progress: 0, desc: '', color: PALETTE[5] },
        { id: uid(), title: '宇宙时空之旅', type: '纪录片', year: 2014, director: '安·德鲁扬', minutes: 572, rating: 9.4, status: '在看', progress: 28, desc: '13 集 · 观看至第 3 集', color: PALETTE[2] }
      ],
      notes: [
        {
          id: uid(), title: '家庭财务管理办法', tag: '财务', updated: d('18') + ' 21:40',
          body: '# 家庭财务管理办法\n\n> 目标：让每一笔钱都能追溯到 **人、事、时间**。\n\n## 三条底线\n\n- **先记账，后消费**：超过 `¥500` 的支出当天记录\n- 每月 5 号做一次对账\n- 大额支出（> ¥2000）需两人确认\n\n## 分类口径\n\n| 分类 | 包含范围 | 备注 |\n| --- | --- | --- |\n| 居住 | 房租、物业、水电 | 固定支出 |\n| 餐饮 | 买菜、外食、外卖 | 弹性最大 |\n| 教育 | 学费、书籍、兴趣班 | 按学期计提 |\n\n## 每月流程\n\n1. 1 号导出上月账单\n2. 5 号家庭对账会议\n3. 10 号调整本月预算\n\n## 待办\n\n- [x] 建立三个账户：日常 / 教育 / 应急\n- [ ] 设置大额支出提醒\n- [ ] 整理 2026 年票据\n\n```text\n应急金目标 = 月均支出 × 6\n当前进度：¥68,000 / ¥68,187\n```\n'
        },
        {
          id: uid(), title: '家庭应急物资清单', tag: '生活', updated: d('10') + ' 09:20',
          body: '# 家庭应急物资清单\n\n## 必备\n\n- 饮用水：每人每天 3 升 × 3 天\n- 压缩食品与罐头\n- 手电筒 + 备用电池\n- 常用药品：退烧、止泻、创可贴\n\n## 证件备份\n\n> 身份证、户口本、房产证扫描件另存一份在移动硬盘。\n\n## 检查周期\n\n每半年检查一次保质期，过期即换。\n'
        },
        {
          id: uid(), title: '每月家庭会议纪要模板', tag: '家庭', updated: d('02') + ' 20:10',
          body: '# 家庭会议纪要 · 2026-09\n\n## 参会人\n\n- 爸爸、妈妈、孩子\n\n## 议题\n\n1. 本月开支复盘\n2. 孩子学习计划\n3. 下月安排\n\n## 结论\n\n- 餐饮超支 8%，下月控制外食次数\n- 物理每周加一次错题整理\n\n## 待办\n\n- [ ] 更新家庭预算表\n- [ ] 预约体检\n'
        }
      ]
    };
  }

  var data = null;

  function load() {
    if (data) return data;
    try {
      var raw = localStorage.getItem(KEY);
      if (raw) {
        var parsed = JSON.parse(raw);
        if (parsed && parsed.tx) { data = parsed; return data; }
      }
    } catch (e) { /* 读取失败则回落到示例数据 */ }
    data = seed();
    save();
    return data;
  }

  function save() {
    try {
      localStorage.setItem(KEY, JSON.stringify(data));
    } catch (e) { /* 存储不可用时静默降级为内存态 */ }
  }

  function reset() {
    data = seed();
    save();
    return data;
  }

  function list(name) { return load()[name]; }

  function insert(name, item) {
    item.id = item.id || uid();
    load()[name].unshift(item);
    save();
    return item;
  }

  function update(name, id, patch) {
    var arr = load()[name];
    for (var i = 0; i < arr.length; i++) {
      if (arr[i].id === id) {
        Object.keys(patch).forEach(function (k) { arr[i][k] = patch[k]; });
        save();
        return arr[i];
      }
    }
    return null;
  }

  function find(name, id) {
    var arr = load()[name];
    for (var i = 0; i < arr.length; i++) if (arr[i].id === id) return arr[i];
    return null;
  }

  function remove(name, id) {
    var arr = load()[name];
    for (var i = 0; i < arr.length; i++) {
      if (arr[i].id === id) { arr.splice(i, 1); save(); return true; }
    }
    return false;
  }

  global.Store = {
    KEY: KEY,
    uid: uid,
    today: today,
    palette: PALETTE,
    catsExpense: CATS_EXPENSE,
    catsIncome: CATS_INCOME,
    members: MEMBERS,
    subjects: SUBJECTS,
    grades: GRADES,
    albums: ALBUMS,
    movieTypes: MOVIE_TYPES,
    movieStatus: MOVIE_STATUS,
    noteTags: NOTE_TAGS,
    load: load,
    save: save,
    reset: reset,
    list: list,
    insert: insert,
    update: update,
    find: find,
    remove: remove
  };
})(window);
