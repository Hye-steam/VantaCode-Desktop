/* dsh-pet 视频桌宠引擎 · 挂到既有的 PetAPI 上
 * 在大肥鱼那套 webm 帧动画里复刻原来的行为：
 * 链式随机播放、朝向镜像、走路位移、点击/拖拽/事件响应。
 * 由 native 通过 PetAPI.setModel('film/...') 切换进来。
 */
(function () {
  'use strict';

  if (!window.PetAPI) {
    return;
  }

  var CFG = window.PET_CONFIG || {};

  var S = {
    front: null,
    back: null,
    gen: 0,
    name: '',
    facing: Math.random() < 0.5 ? 1 : -1,
    chainTimer: null,
    lastRandom: '',
    lastEventPick: {},
    mode: 'chain',
    started: false,
    paused: false,
    dragging: false,
    randomOn: true,
    eventLoop: null,
    custom: {},
    active: false,
    box: null
  };

  function bridge() {
    try {
      if (typeof AndroidPet !== 'undefined' && AndroidPet) return AndroidPet;
    } catch (e) {}
    return null;
  }

  function info(msg) {
    try {
      var b = bridge();
      if (b && b.onInfo) b.onInfo(String(msg));
    } catch (e) {}
  }

  function pick(list) {
    if (!list || !list.length) return null;
    return list[Math.floor(Math.random() * list.length)];
  }

  function pickDiff(list, last) {
    if (!list || !list.length) return null;
    if (list.length === 1) return list[0];
    for (var i = 0; i < 5; i++) {
      var v = pick(list);
      if (v !== last) return v;
    }
    return pick(list);
  }

  function fileOf(name) {
    return (CFG.files || {})[name] || '';
  }

  function mirrorOf() {
    return S.facing > 0;
  }

  function applyMirror(video, mirror) {
    if (!video) return;
    try {
      video.style.transform = 'translateX(-50%) scaleX(' + (mirror ? -1 : 1) + ')';
    } catch (e) {}
  }

  function ensureBox() {
    if (S.box) return S.box;
    var box = document.createElement('div');
    box.id = 'filmStage';
    box.style.cssText = [
      'position:fixed', 'inset:0', 'background:transparent',
      'display:none', 'pointer-events:none', 'overflow:hidden',
      'transform-origin:50% 100%'
    ].join(';');
    document.body.appendChild(box);
    var a = mkVideo();
    var b = mkVideo();
    box.appendChild(a);
    box.appendChild(b);
    S.front = a;
    S.back = b;
    S.box = box;
    fitFilm();
    return box;
  }

  function mkVideo() {
    var v = document.createElement('video');
    v.className = 'petvid';
    v.muted = true;
    v.setAttribute('playsinline', '');
    v.setAttribute('webkit-playsinline', '');
    v.setAttribute('disablepictureinpicture', '');
    v.setAttribute('muted', '');
    v.preload = 'auto';
    v.style.cssText = [
      'position:absolute', 'top:50%', 'bottom:auto', 'left:50%', 'right:auto',
      'width:100%', 'height:auto', 'max-width:none',
      'object-fit:contain', 'object-position:center center',
      'opacity:0', 'transform:translateX(-50%)', 'transform-origin:50% 91%',
      'transition:opacity .14s linear', 'background:transparent',
      'pointer-events:none', 'will-change:opacity,transform',
      'backface-visibility:hidden'
    ].join(';');
    v.addEventListener('ended', onEnded);
    v.addEventListener('error', function () {
      if (v === S.front) {
        info('video error ' + S.name);
        schedule(200);
      }
    });
    return v;
  }

  function showFrameFlag(v, show) {
    try {
      v.style.opacity = show ? '1' : '0';
    } catch (e) {}
  }

  function play(name, opts) {
    opts = opts || {};
    if (!name) {
      schedule(120);
      return;
    }
    var file = fileOf(name);
    var url = S.custom[name] || '';
    if (!file && !url) {
      info('missing animation: ' + name);
      schedule(300);
      return;
    }
    if (opts.loop && S.name === name && S.front && !S.front.ended) return;

    ensureBox();

    var g = ++S.gen;
    var next = S.back;
    var prev = S.front;
    var mirror = (typeof opts.mirror === 'boolean') ? opts.mirror : mirrorOf();

    try {
      next.pause();
      next.removeAttribute('src');
      next.load();
    } catch (e) {}

    next.loop = !!opts.loop;
    applyMirror(next, mirror);

    var revealed = false;
    var showFrame = function () {
      if (revealed || g !== S.gen) return;
      revealed = true;
      try {
        prev.pause();
      } catch (e) {}
      var oldPrev = prev;
      next.style.zIndex = '2';
      oldPrev.style.zIndex = '1';
      showFrameFlag(next, true);
      setTimeout(function () {
        if (g !== S.gen) return;
        showFrameFlag(oldPrev, false);
        oldPrev.style.zIndex = '0';
      }, 160);
      S.front = next;
      S.back = prev;
      S.name = name;
      S.started = true;
    };

    var waitFirst = function () {
      if (g !== S.gen || revealed) return;
      if (next.requestVideoFrameCallback) {
        var fired = false;
        next.requestVideoFrameCallback(function () {
          fired = true;
          if (g === S.gen) showFrame();
        });
        setTimeout(function () {
          if (!fired && g === S.gen && !revealed) {
            if (next.readyState >= 2 && next.currentTime > 0) showFrame();
          }
        }, 700);
      } else {
        setTimeout(function () {
          if (g === S.gen && !revealed && next.readyState >= 2) showFrame();
        }, 60);
      }
    };

    var startPlay = function () {
      if (g !== S.gen || revealed) return;
      var pr = null;
      try {
        pr = next.play();
      } catch (e) {
        pr = null;
      }
      if (pr && pr.then) {
        pr.then(waitFirst).catch(function () {
          info('play rejected: ' + file);
        });
      } else {
        waitFirst();
      }
    };

    next.addEventListener('loadeddata', startPlay, { once: true });
    setTimeout(function () {
      if (!revealed && g === S.gen && next.readyState >= 2) startPlay();
    }, 900);

    next.src = url ? url : ('film/webm/' + encodeURIComponent(file));
    try {
      next.currentTime = 0;
    } catch (e) {}
    try {
      next.load();
    } catch (e) {}

    if (opts.onMeta) {
      var onMeta = function () {
        next.removeEventListener('loadedmetadata', onMeta);
        if (g !== S.gen) return;
        try {
          opts.onMeta(next.duration || 0);
        } catch (e) {}
      };
      next.addEventListener('loadedmetadata', onMeta);
    }
  }

  function schedule(ms) {
    if (S.chainTimer) {
      clearTimeout(S.chainTimer);
      S.chainTimer = null;
    }
    S.chainTimer = setTimeout(function () {
      S.chainTimer = null;
      nextChain();
    }, ms || 90);
  }

  function pickKind() {
    if (!S.randomOn) return 'idle';
    var A = CFG.animations || {};
    var W = CFG.weights || { idle: 10, turn: 5, move: 5 };
    var cats = A.categories || [];
    var total = (W.idle || 0) + (W.turn || 0) + (W.move || 0);
    for (var i = 0; i < cats.length; i++) total += (cats[i].weight || 0);
    if (total <= 0) return 'idle';
    var r = Math.random() * total;
    if ((W.idle || 0) > 0) {
      if (r < W.idle) return 'idle';
      r -= W.idle;
    }
    if ((W.turn || 0) > 0) {
      if (r < W.turn) return 'turn';
      r -= W.turn;
    }
    if ((W.move || 0) > 0) {
      if (r < W.move) return 'move';
    }
    return 'category';
  }

  function pickCategory() {
    var cats = (CFG.animations || {}).categories || [];
    var total = 0;
    for (var i = 0; i < cats.length; i++) total += (cats[i].weight || 0);
    if (total <= 0) return null;
    var r = Math.random() * total;
    for (var j = 0; j < cats.length; j++) {
      r -= (cats[j].weight || 0);
      if (r < 0) return cats[j];
    }
    return cats[cats.length - 1] || null;
  }

  function nextChain() {
    if (!S.started || S.paused || S.dragging) return;
    S.mode = 'chain';
    var A = CFG.animations || {};
    var kind = pickKind();

    if (kind === 'idle') {
      var idle = pick(A.idle || []);
      if (idle) play(idle, {});
      else schedule(1500);
      return;
    }
    if (kind === 'turn') {
      S.facing = -S.facing;
      var turn = pick(A.turn || []);
      if (turn) play(turn, { mirror: S.facing > 0 });
      else schedule(600);
      return;
    }
    if (kind === 'move') {
      walk();
      return;
    }
    var cat = pickCategory();
    if (!cat) {
      schedule(500);
      return;
    }
    var act = pickDiff(cat.actions || [], S.lastRandom);
    if (!act) {
      schedule(400);
      return;
    }
    S.lastRandom = act;
    play(act, { mirror: cat.noMirror ? false : S.facing > 0 });
  }

  function walk() {
    var A = CFG.animations || {};
    var moves = A.moves || {};
    var actions = moves.actions || [];
    if (!actions.length) {
      schedule(400);
      return;
    }
    var chosen = pick(actions);
    var def = moves['default'] || { minDist: 60, maxDist: 240, leadSec: 2, tailSec: 2 };
    var p = {};
    for (var k in def) p[k] = def[k];
    var src = chosen.params || {};
    for (var k2 in src) p[k2] = src[k2];

    var minD = Number(p.minDist) || 60;
    var maxD = Number(p.maxDist) || Math.max(minD, 240);
    var dist = minD + Math.random() * Math.max(0, maxD - minD);
    var lead = Number(p.leadSec) || 2;
    var tail = Number(p.tailSec) || 2;

    var dirs = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]];
    var d = dirs[Math.floor(Math.random() * dirs.length)];
    if (d[0] !== 0) S.facing = d[0];

    play(chosen.name, {
      mirror: mirrorOf(),
      loop: true,
      onMeta: function (duration) {
        var dur = (isFinite(duration) && duration > 0) ? duration : 4;
        dur = Math.max(1.4, Math.min(12.0, dur));
        var b = bridge();
        try {
          if (b && b.onMove2) {
            b.onMove2(String(chosen.name), d[0], d[1], Math.round(dist), Math.round(dur * 1000),
              Math.round(lead * 1000), Math.round(tail * 1000));
          }
        } catch (e) {
          info('onMove2: ' + e);
        }
      }
    });
  }

  function onEnded(e) {
    if (e.target !== S.front) return;
    if (S.paused || S.dragging) return;
    if (S.eventLoop && S.eventLoop.candidates && S.eventLoop.candidates.length > 1) {
      var pool = S.eventLoop.candidates;
      var nextName = pool[0];
      for (var i = 0; i < 8 && nextName === S.eventLoop.last; i++) {
        nextName = pool[Math.floor(Math.random() * pool.length)];
      }
      S.eventLoop.last = nextName;
      S.mode = 'event';
      play(nextName, { mirror: S.facing > 0, loop: false });
      return;
    }
    S.mode = 'chain';
    schedule(80);
  }

  /* ---------------- 尺寸与表现 ---------------- */

  var filmScaleX = 1;
  var filmScaleY = 1;

  // 视频素材本身是竖长条（宽/高约 0.35~0.5）。
  // 纯按高度铺满会让画面显得很窄，这里按高度定基准，再给宽度一个增益，
  // 允许适度超出窗口（居中显示，两侧裁掉一点），主体就会更饱满。
  function filmNaturalRatio(v) {
    try {
      if (v && v.videoWidth > 0 && v.videoHeight > 0) {
        return v.videoWidth / v.videoHeight;
      }
    } catch (e) {}
    return 1.7778;
  }

  // 素材 640x360（16:9）。完整包含：整只塞进视口，绝不裁切。
  // FILM_ZOOM 允许略微放大（>1 会裁掉一点边），默认 1.0 全身可见。
  var FILM_ZOOM = 0.92;

  function fitFilm() {
    if (!S.box) return;
    var w = window.innerWidth;
    var h = window.innerHeight;

    var RATIO = 640 / 360;

    // 完整包含：取宽、高两个方向更紧的那个
    var fitW = w;
    var fitH = fitW / RATIO;
    if (fitH > h) {
      fitH = h;
      fitW = fitH * RATIO;
    }

    // 再乘用户缩放（大小滑杆），上限不高以免裁切过多
    var targetW = Math.round(fitW * FILM_ZOOM * filmScaleX);
    var targetH = Math.round(fitH * FILM_ZOOM * filmScaleY);

    [S.front, S.back].forEach(function (v) {
      if (!v) return;
      v.style.width = targetW + 'px';
      v.style.height = targetH + 'px';
      v.style.left = '50%';
      // 略微下移，保证脚不被裁
      v.style.top = 'calc(50% + ' + Math.round(h * 0.01) + 'px)';
      v.style.bottom = 'auto';
      v.style.right = 'auto';
      v.style.transform = 'translate(-50%,-50%)';
      v.style.objectFit = 'contain';
      v.style.objectPosition = 'center bottom';
    });

    S.box.style.transform = '';
    window.__petFit = {
      w: w, h: h, film: true,
      videoW: targetW, videoH: targetH,
      zoom: FILM_ZOOM,
      scaleX: filmScaleX, scaleY: filmScaleY
    };
  }

function lookFilm(x, y) {
    if (!S.front) return;
    var nx = Math.max(0, Math.min(1, x));
    var flip = S.facing > 0 ? -1 : 1;
    var tilt = (nx - 0.5) * 2 * 2.6 * flip;
    var mirror = S.facing > 0 ? -1 : 1;
    S.front.style.transition = 'transform 140ms ease-out, opacity .14s linear';
    S.front.style.transform = 'translateX(-50%) scaleX(' + mirror + ') rotate(' + tilt.toFixed(2) + 'deg)';
  }

  function squash(scale) {
    if (!S.active || !S.box) return;
    var s = (typeof scale === 'number' && scale > 0.4 && scale < 1.2) ? scale : 0.9;
    var wide = 1 + (1 - s) * 0.85;
    S.box.style.transition = 'transform 90ms ease-out';
    S.box.style.transform = 'scale(' + wide.toFixed(3) + ',' + s.toFixed(3) + ')';
    setTimeout(function () {
      if (!S.box) return;
      S.box.style.transition = 'transform 170ms cubic-bezier(.22,1.2,.36,1)';
      S.box.style.transform = filmScaleX !== 1 ? 'scaleX(' + filmScaleX + ')' : '';
    }, 90);
  }

  function popHearts(n) {
    var count = n || 1;
    for (var i = 0; i < count; i++) {
      (function (idx) {
        setTimeout(function () {
          var s = document.createElement('div');
          s.textContent = idx % 3 === 0 ? '\u266a' : '\u2665';
          s.style.cssText = [
            'position:fixed', 'left:' + (32 + Math.random() * 36) + '%',
            'bottom:' + (12 + Math.random() * 16) + '%',
            'font-size:16px', 'line-height:1', 'color:#F26B8A',
            'pointer-events:none', 'z-index:9',
            'text-shadow:0 1px 3px rgba(0,0,0,.25)',
            'animation:petHeartUp .95s ease-out forwards'
          ].join(';');
          document.body.appendChild(s);
          setTimeout(function () {
            if (s.parentNode) s.parentNode.removeChild(s);
          }, 1000);
        }, idx * 90);
      })(i);
    }
  }

  function injectKeyframes() {
    if (document.getElementById('petFilmCss')) return;
    var st = document.createElement('style');
    st.id = 'petFilmCss';
    st.textContent = '@keyframes petHeartUp{0%{opacity:0;transform:translate(-50%,0) scale(.6)}' +
      '18%{opacity:1}100%{opacity:0;transform:translate(-50%,-44px) scale(1.12)}}';
    document.head.appendChild(st);
  }

  /* ---------------- 接管 PetAPI 的入口 ---------------- */

  var API = window.PetAPI;
  var live2dSetModel = API.setModel.bind(API);
  var live2dSetSize = API.setSize.bind(API);
  var live2dResize = API.resize.bind(API);
  var live2dFocus = API.focus.bind(API);
  var live2dRandom = API.randomMotion.bind(API);
  var live2dEmote = API.emote.bind(API);
  var live2dPoke = API.poke.bind(API);
  var live2dSpeakStart = API.speakStart.bind(API);
  var live2dSpeakStop = API.speakStop.bind(API);

  function isFilm(path) {
    return /^film(\/|$)/.test(String(path || ''));
  }

  function enterFilm(name) {
    ensureBox();
    injectKeyframes();
    var stageEl = document.getElementById('stage');
    if (stageEl) stageEl.style.display = 'none';
    S.box.style.display = 'block';
    S.active = true;
    S.started = false;
    S.paused = false;
    S.mode = 'chain';
    S.gen++;
    window.__petStatus = 'loaded';
    window.__petInfo = { name: name || '', motions: [], expressions: [], film: true };
    fitFilm();
    var idle = pick((CFG.animations || {}).idle || []);
    if (idle) play(idle, { mirror: S.facing > 0 });
    else schedule(200);
    try {
      if (window.AndroidPet && AndroidPet.onInfo) {
        AndroidPet.onInfo(JSON.stringify(window.__petInfo));
      }
    } catch (e) {}
  }

  function leaveFilm() {
    S.active = false;
    S.started = false;
    if (S.chainTimer) {
      clearTimeout(S.chainTimer);
      S.chainTimer = null;
    }
    S.gen++;
    if (S.front) {
      try {
        S.front.pause();
      } catch (e) {}
    }
    if (S.back) {
      try {
        S.back.pause();
      } catch (e) {}
    }
    if (S.box) S.box.style.display = 'none';
    var stageEl = document.getElementById('stage');
    if (stageEl) stageEl.style.display = '';
  }

  API.setModel = function (path, name) {
    if (isFilm(path)) {
      enterFilm(name);
      return;
    }
    leaveFilm();
    live2dSetModel(path, name);
  };

  API.setSize = function (fx, fy) {
    if (S.active) {
      filmScaleX = (fx && fx > 0) ? fx : 1;
      filmScaleY = (fy && fy > 0) ? fy : 1;
      fitFilm();
      setTimeout(fitFilm, 60);
      return;
    }
    live2dSetSize(fx, fy);
  };

  API.resize = function () {
    if (S.active) {
      fitFilm();
      setTimeout(fitFilm, 120);
      return;
    }
    live2dResize();
  };

  API.focus = function (x, y) {
    if (S.active) {
      lookFilm(x, y);
      return;
    }
    live2dFocus(x, y);
  };

  API.focusVec = function (dx, dy) {
    if (S.active) return;
    try {
      var m = window.__getModel && window.__getModel();
      if (!m) return;
      var fc = m.internalModel && m.internalModel.focusController;
      if (fc && fc.focus) {
        fc.focus(Math.max(-1, Math.min(1, dx)), Math.max(-1, Math.min(1, dy)), false);
      }
    } catch (e) {}
  };

  API.randomMotion = function (burst) {
    if (S.active) {
      if (S.paused) return false;
      S.eventLoop = null;
      S.mode = 'once';
      var A = CFG.animations || {};
      var pool = (A.clicks || []).concat(A.idle || []);
      var c = pick(pool);
      if (c) play(c, { mirror: S.facing > 0 });
      return !!c;
    }
    return live2dRandom(burst);
  };

  API.emote = function (kind) {
    if (S.active) {
      if (kind === 'happy' || kind === 'shy') {
        var c = pick((CFG.animations || {}).clicks || []);
        if (c) play(c, { mirror: S.facing > 0 });
        popHearts(1);
      }
      return;
    }
    live2dEmote(kind);
  };

  API.poke = function () {
    if (S.active) {
      popHearts(1);
      var c = pick((CFG.animations || {}).clicks || []);
      if (c) play(c, { mirror: S.facing > 0 });
      return true;
    }
    return live2dPoke();
  };

  API.speakStart = function (ms) {
    if (S.active) return true;
    return live2dSpeakStart(ms);
  };

  API.speakStop = function () {
    if (S.active) return;
    live2dSpeakStop();
  };

  API.beginDrag = function () {
    if (!S.active) return;
    S.dragging = true;
    S.eventLoop = null;
    S.mode = 'hold';
    var d = pick((CFG.animations || {}).drag || []);
    if (d) play(d, { loop: true, mirror: S.facing > 0 });
  };

  API.endDrag = function () {
    if (!S.active) return;
    S.dragging = false;
    S.mode = 'chain';
    var idle = pick((CFG.animations || {}).idle || []);
    if (idle) play(idle, {});
    else schedule(100);
  };

  API.squash = squash;
  API.hearts = popHearts;

  API.playAction = function (name) {
    if (!S.active || !name) return;
    S.eventLoop = null;
    S.mode = 'once';
    play(name, { mirror: S.facing > 0 });
  };

  API.setPaused = function (p) {
    S.paused = !!p;
    if (!S.paused && S.mode === 'chain' && S.front && S.front.ended) schedule(100);
  };

  API.setRandomActions = function (on) {
    S.randomOn = !!on;
  };

  API.filmFacing = function (dir) {
    S.facing = Number(dir) < 0 ? -1 : 1;
    applyMirror(S.front, mirrorOf());
    applyMirror(S.back, mirrorOf());
  };

  API.filmState = function () {
    return JSON.stringify({
      current: S.name,
      mode: S.mode,
      facing: S.facing,
      paused: S.paused,
      dragging: S.dragging,
      active: S.active
    });
  };

  API.isFilm = function () {
    return S.active;
  };

  window.addEventListener('resize', function () {
    if (S.active) fitFilm();
  });

  /* 兜底：随机动作关掉时也保证偶尔走两步 */
  setInterval(function () {
    if (!S.active || !S.started || S.paused || S.dragging || !S.randomOn) return;
    if (S.mode !== 'chain') return;
    if (!S.chainTimer) schedule(120);
  }, 3000);

  if (document.readyState === 'complete') {
    injectKeyframes();
  } else {
    window.addEventListener('load', injectKeyframes);
  }
})();
