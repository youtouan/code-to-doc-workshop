import assert from 'node:assert/strict';

export function decodeHtml(value) {
  return value.replace(/&(#x[0-9a-f]+|#[0-9]+|amp|quot|apos|lt|gt|nbsp);/gi, (_, entity) => {
    if (entity[0] === '#') {
      const hex = entity[1].toLowerCase() === 'x';
      return String.fromCodePoint(Number.parseInt(entity.slice(hex ? 2 : 1), hex ? 16 : 10));
    }
    return { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: ' ' }[entity.toLowerCase()];
  });
}

function attributes(source) {
  const result = {};
  for (const match of source.matchAll(/([^\s=<>/]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g)) {
    result[match[1].toLowerCase()] = decodeHtml(match[2] ?? match[3] ?? match[4] ?? '');
  }
  return result;
}

export function forms(html) {
  const result = [];
  for (const match of html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form>/gi)) {
    const form = attributes(match[1]);
    const fields = new URLSearchParams();
    const controls = [];
    const buttons = [];
    for (const input of match[2].matchAll(/<input\b([^>]*)>/gi)) {
      const item = attributes(input[1]);
      controls.push(item);
      if (!item.name || Object.hasOwn(item, 'disabled')) continue;
      const type = (item.type || 'text').toLowerCase();
      if (['submit', 'image'].includes(type)) {
        buttons.push(item);
        continue;
      }
      if (['button', 'reset', 'file'].includes(type)) continue;
      if (['checkbox', 'radio'].includes(type) && !Object.hasOwn(item, 'checked')) continue;
      fields.append(item.name, item.value ?? (['checkbox', 'radio'].includes(type) ? 'on' : ''));
    }
    for (const textarea of match[2].matchAll(/<textarea\b([^>]*)>([\s\S]*?)<\/textarea>/gi)) {
      const item = attributes(textarea[1]);
      controls.push(item);
      if (item.name && !Object.hasOwn(item, 'disabled')) fields.append(item.name, decodeHtml(textarea[2]));
    }
    for (const select of match[2].matchAll(/<select\b([^>]*)>([\s\S]*?)<\/select>/gi)) {
      const item = attributes(select[1]);
      const options = [];
      for (const option of select[2].matchAll(/<option\b([^>]*)>([\s\S]*?)<\/option>/gi)) {
        const choice = attributes(option[1]);
        options.push({ ...choice, value: choice.value ?? decodeHtml(option[2].replace(/<[^>]+>/g, '')) });
      }
      controls.push({ ...item, options });
      if (!item.name || Object.hasOwn(item, 'disabled')) continue;
      const chosen = options.filter(option => Object.hasOwn(option, 'selected'));
      if (chosen.length === 0 && options.length && !Object.hasOwn(item, 'multiple')) chosen.push(options[0]);
      for (const choice of chosen) fields.append(item.name, choice.value);
    }
    for (const button of match[2].matchAll(/<button\b([^>]*)>([\s\S]*?)<\/button>/gi)) {
      const item = attributes(button[1]);
      if (!Object.hasOwn(item, 'disabled') && (item.type || 'submit').toLowerCase() === 'submit') {
        buttons.push(item);
      }
    }
    result.push({ ...form, method: (form.method || 'get').toUpperCase(), fields, controls, buttons });
  }
  return result;
}

export function links(html, baseUrl) {
  const result = [];
  for (const anchor of html.matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a>/gi)) {
    const item = attributes(anchor[1]);
    if (item.href && !item.href.startsWith('#')) {
      result.push({
        url: new URL(item.href, baseUrl),
        text: decodeHtml(anchor[2].replace(/<[^>]+>/g, '')).trim()
      });
    }
  }
  return result;
}

export function suggestions(html) {
  const result = [];
  for (const list of html.matchAll(/<datalist\b([^>]*)>([\s\S]*?)<\/datalist>/gi)) {
    const owner = attributes(list[1]);
    for (const option of list[2].matchAll(/<option\b([^>]*)>([\s\S]*?)(?:<\/option>|(?=<option\b)|$)/gi)) {
      const entry = attributes(option[1]);
      result.push({ listId: owner.id, value: entry.value, text: decodeHtml(option[2].replace(/<[^>]+>/g, '')).trim() });
    }
  }
  return result;
}

export function findForm(page, operation) {
  const candidates = forms(page.body).filter(form => form.fields.get('op') === operation
    || form.buttons.some(button => button.name === 'op' && button.value === operation));
  assert.equal(candidates.length, 1, `Expected one ${operation} form at ${page.url.pathname}`);
  const form = candidates[0];
  const fields = new URLSearchParams(form.fields);
  fields.set('op', operation);
  return { ...form, fields };
}

export function withFields(form, changes) {
  const fields = new URLSearchParams(form.fields);
  for (const [name, value] of Object.entries(changes)) {
    fields.delete(name);
    if (value === null || value === undefined || value === false) continue;
    for (const item of Array.isArray(value) ? value : [value]) fields.append(name, String(item));
  }
  return fields;
}

export class HttpClient {
  constructor(baseUrl) {
    this.base = new URL(baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`);
    assert.match(this.base.protocol, /^https?:$/);
    this.cookies = new Map();
  }

  cookie(name) {
    return this.cookies.get(name);
  }

  async request(path, { method = 'GET', fields, follow = true } = {}) {
    let url = path instanceof URL ? path : new URL(path.replace(/^\//, ''), this.base);
    for (let redirects = 0; redirects <= 6; redirects++) {
      assert.equal(url.origin, this.base.origin, 'Refusing to forward credentials to another origin');
      assert.ok(url.pathname.startsWith(this.base.pathname), 'Refusing a redirect outside the application');
      const headers = { Accept: 'text/html,application/json', Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ') };
      let body;
      if (fields !== undefined && method !== 'GET') {
        headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
        body = fields.toString();
      }
      const response = await fetch(url, { method, headers, body, redirect: 'manual', signal: AbortSignal.timeout(20000) });
      for (const cookie of response.headers.getSetCookie()) {
        const [pair] = cookie.split(';');
        const delimiter = pair.indexOf('=');
        if (delimiter < 1) continue;
        const name = pair.slice(0, delimiter);
        if (/max-age=0(?:;|$)/i.test(cookie)) this.cookies.delete(name);
        else this.cookies.set(name, pair.slice(delimiter + 1));
      }
      const text = await response.text();
      const page = { status: response.status, headers: response.headers, body: text, url };
      if (!follow || ![301, 302, 303, 307, 308].includes(response.status)) return page;
      const location = response.headers.get('location');
      assert.ok(location, 'Redirect has no Location header');
      url = new URL(location, url);
      if ([301, 302, 303].includes(response.status)) {
        method = 'GET';
        fields = undefined;
      }
    }
    throw new Error('Application redirect loop');
  }

  async get(path) {
    return this.request(path);
  }

  async submit(page, operation, changes = {}, { follow = true } = {}) {
    const form = findForm(page, operation);
    assert.equal(form.method, 'POST', `${operation} must use POST`);
    return this.request(new URL(form.action, page.url), {
      method: 'POST',
      fields: withFields(form, changes),
      follow
    });
  }

  async login(login) {
    const page = await this.get('login.do');
    assertPage(page);
    const session = this.cookie('JSESSIONID');
    assert.ok(session, 'Login GET must establish a CSRF session');
    const authenticated = await this.submit(page, 'login', { login, password: `Demo-${login}-2026!` });
    assertPage(authenticated);
    assert.notEqual(this.cookie('JSESSIONID'), session, 'Authentication must rotate the session identifier');
    assert.ok(!authenticated.url.pathname.endsWith('/login.do'), `Login rejected for demo account ${login}`);
    assert.ok(!authenticated.body.includes(`Demo-${login}-2026!`), 'Passwords must not be reflected in HTML');
    return authenticated;
  }
}

export function assertPage(page, expected = 200) {
  const requestId = page.headers.get('x-request-id') || 'none';
  assert.equal(page.status, expected, `${page.url.pathname} returned ${page.status}; request=${requestId}`);
  if (expected === 200) {
    assert.match(page.headers.get('content-type') || '', /text\/html.*charset=UTF-8/i);
    assert.ok(page.body.includes('</html>'), `${page.url.pathname} did not finish rendering`);
  }
}
