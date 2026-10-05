const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const html = fs.readFileSync(path.resolve(__dirname, '../../main/assets/captcha.html'), 'utf8');
const source = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function host(provider = 'turnstile') {
  let now = 0, nextTimer = 0, options, resets = 0, shows = 0, destroyed = 0;
  const timers = new Map();
  const elements = { widget: { classList: { add() {} } } };
  const context = {
    document: {
      getElementById: id => elements[id],
      createElement: () => ({}),
      head: { appendChild(script) { context.script = script; } },
      body: { classList: { toggle() {} }, getBoundingClientRect: () => ({ height: 240 }) }
    },
    ResizeObserver: class { constructor(fn) { this.fn = fn; } observe() { this.fn(); } },
    setTimeout: (fn, ms) => { timers.set(++nextTimer, { fn, at: now + ms }); return nextTimer; },
    clearTimeout: id => timers.delete(id),
    addEventListener() {},
    turnstile: { render: (_, value) => { options = value; return 'widget'; }, reset: () => resets++ },
    hcaptcha: { render: (_, value) => { options = value; return 'widget'; }, reset: () => resets++ },
    TencentCaptcha: function (element, key, callback, opts) {
      options = { callback, ...opts };
      this.show = () => shows++;
      this.destroy = () => destroyed++;
    }
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(source.replace('/*CONFIG*/', JSON.stringify({ provider, attempt: 'attempt-1', key: 'fixture',
    dark: false })), context);
  return {
    context, elements,
    get state() { return context.__ftCaptchaState; },
    get options() { return options; },
    get resets() { return resets; },
    get shows() { return shows; },
    get destroyed() { return destroyed; },
    load() { provider === 'hcaptcha' ? context.ftHCaptchaReady() : context.script.onload(); },
    advance(ms) {
      const target = now + ms;
      while (true) {
        const due = [...timers].filter(([, timer]) => timer.at <= target).sort((a, b) => a[1].at - b[1].at)[0];
        if (!due) break;
        now = due[1].at; timers.delete(due[0]); due[1].fn();
      }
      now = target;
    }
  };
}

test('blocked scripts signal failure to native controls after ten seconds', () => {
  const page = host();
  assert.equal(page.state.loaded, false);
  page.advance(9999); assert.equal(page.state.escape, false);
  page.advance(1); assert.equal(page.state.escape, true);
  assert.equal(page.state.action, undefined);
  assert.equal(page.state.reason, 'script_blocked');
});

test('a stalled live challenge gets fifteen seconds and can still succeed after the warning', () => {
  const page = host(); page.load();
  assert.equal(page.state.loaded, true);
  page.advance(14999); assert.equal(page.state.escape, false);
  page.advance(1); assert.equal(page.state.escape, true);
  page.options.callback('fixture-token'); assert.equal(page.state.token, 'fixture-token');
  page.options.callback('duplicate'); assert.equal(page.state.token, 'fixture-token');
});

test('interactive challenges wait for the user and resume the timer after closing', () => {
  const page = host('hcaptcha'); page.load();
  page.options['open-callback'](); page.advance(120000);
  assert.equal(page.state.escape, false);
  assert.equal(page.state.interactive, true);
  page.options['close-callback']();
  assert.equal(page.state.interactive, false);
  page.advance(15000); assert.equal(page.state.escape, true);
});

test('recoverable errors reset at most three times, then retain the widget and expose switching', () => {
  const page = host(); page.load();
  for (let i = 0; i < 3; i++) page.options['error-callback']('failure');
  assert.equal(page.resets, 3); assert.equal(page.state.escape, false);
  page.options['error-callback']('failure');
  assert.equal(page.resets, 3); assert.equal(page.state.escape, true);
  page.options.callback('late-success'); assert.equal(page.state.token, 'late-success');
});

test('unsupported browsers and script errors offer an immediate escape', () => {
  const page = host(); page.load(); page.options['unsupported-callback']();
  assert.equal(page.state.escape, true);
  assert.equal(page.state.reason, 'unsupported_browser');
  const blocked = host(); blocked.context.script.onerror(); assert.equal(blocked.state.escape, true);
});

test('Tencent uses embedded challenges, rejects trerror placeholders, and encodes ticket and randstr', () => {
  const page = host('tencent'); page.load();
  assert.equal(page.options.type, 'embed'); assert.equal(page.shows, 1);
  page.options.ready(); page.advance(120000); assert.equal(page.state.escape, false);
  page.options.callback({ ret: 0, ticket: 'trerror-placeholder', randstr: 'random' });
  assert.equal(page.state.token, undefined); assert.equal(page.destroyed, 1); assert.equal(page.shows, 2);
  page.options.callback({ ret: 0, ticket: 'fixture-ticket', randstr: 'fixture-randstr' });
  assert.deepEqual(JSON.parse(page.state.token), { ticket: 'fixture-ticket', randstr: 'fixture-randstr' });
});
