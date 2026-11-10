#!/usr/bin/env node
// Collects GitHub Changelog posts and VS Code release notes for a date range.
// Collecting is deterministic. Choosing and summarizing items is left to the model.
// Status words are copied verbatim from each source and never inferred.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const SOURCES = Object.freeze(['github-changelog', 'vscode-release-notes']);
export const CHANGELOG_API = 'https://github.blog/wp-json/wp/v2/changelogs';
const ALLOWED_HOSTS = new Set(['github.blog', 'raw.githubusercontent.com']);
const PER_PAGE = 100;
const MAX_PAGES = 20;
const MAX_NOTES = 80;
const SKIPPED_AREAS = new Set(['Release highlights', 'Thank you']);
// VS Code 1.130 shipped on 2026-07-22 and Stable has shipped weekly since then. This only
// chooses where the scan starts; the scan reads each note's Date and walks out of the range.
const WEEKLY_ANCHOR = { minor: 130, date: '2026-07-22' };

export const vscodeRawUrl = minor => `https://raw.githubusercontent.com/microsoft/vscode-docs/main/release-notes/v1_${minor}.md`;
export const vscodePageUrl = minor => `https://code.visualstudio.com/updates/v1_${minor}`;

export function isDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function shiftDate(value, days) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

function daysBetween(from, to) {
  const [a, b] = [from, to].map(value => Date.UTC(...value.split('-').map((part, index) => Number(part) - (index === 1 ? 1 : 0))));
  return Math.round((b - a) / 86400000);
}

export function changelogPageUrl({ since, until, page }) {
  // One extra day on each side: the API compares against the site's local time.
  const after = `${shiftDate(since, -1)}T00:00:00Z`;
  const before = `${shiftDate(until, 1)}T23:59:59Z`;
  return `${CHANGELOG_API}?after=${after}&before=${before}&per_page=${PER_PAGE}&page=${page}&orderby=date&order=asc&_fields=id,date,link,title,content,class_list`;
}

// Matches the heading ids on code.visualstudio.com/updates (checked against 155 headings of 1.140-1.143).
export function vscodeAnchor(heading) {
  return `_${heading.trim().toLowerCase().replace(/`/g, '').replace(/[^a-z0-9 -]/g, '').replace(/\s/g, '-')}`;
}

const STATUS_WORDS = /\b(?:generally available|general availability|limited public preview|limited preview|public preview|technical preview|private preview|research preview|early access|preview|experimental|beta|deprecated|deprecation|retired|retirement|closing down|sunset)\b/gi;
const GA_ABBREVIATION = /\bGA\b/g;

// Returns status phrases in the order they appear, deduplicated case-insensitively,
// keeping the source's own spelling.
export function statusPhrases(text) {
  const value = `${text ?? ''}`;
  const matches = [...value.matchAll(STATUS_WORDS), ...value.matchAll(GA_ABBREVIATION)].sort((a, b) => a.index - b.index);
  const seen = new Set();
  const phrases = [];
  for (const match of matches) {
    const key = match[0].toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    phrases.push(match[0]);
  }
  return phrases;
}

const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', hellip: '…', mdash: '—', ndash: '–', rsquo: '’', lsquo: '‘', rdquo: '”', ldquo: '“' };

export function htmlToText(html) {
  return `${html ?? ''}`
    .replace(/<(script|style)\b[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&(#x[0-9a-f]+|#[0-9]+|[a-z]+);/gi, (whole, code) => {
      if (code[0] !== '#') return ENTITIES[code.toLowerCase()] ?? whole;
      const number = code[1].toLowerCase() === 'x' ? parseInt(code.slice(2), 16) : parseInt(code.slice(1), 10);
      return Number.isInteger(number) && number >= 0 && number <= 0x10ffff ? String.fromCodePoint(number) : whole;
    })
    .replace(/\s+/g, ' ')
    .trim();
}

function markdownToText(markdown) {
  return markdown
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, ' ')
    .replace(/setting\(([^)]+)\)/g, '$1')
    .replace(/\*\*|__|`/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

const excerpt = text => (text.length > 400 ? `${text.slice(0, 399)}…` : text);

export function parseChangelogEntry(entry) {
  const title = htmlToText(entry?.title?.rendered);
  const body = htmlToText(entry?.content?.rendered);
  const classes = Array.isArray(entry?.class_list) ? entry.class_list : Object.values(entry?.class_list ?? {});
  const prefixed = prefix => classes.filter(name => typeof name === 'string' && name.startsWith(prefix)).map(name => name.slice(prefix.length));
  return {
    id: `github-changelog:${entry.id}`,
    source: 'github-changelog',
    date: `${entry.date}`.slice(0, 10),
    title,
    url: entry.link,
    type: prefixed('changelog-type-')[0] ?? null,
    labels: prefixed('changelog-label-'),
    status: { title: statusPhrases(title), body: statusPhrases(body) },
    excerpt: excerpt(body)
  };
}

function sectionItem({ heading, area, sectionText, minor, edition, date }) {
  const version = `1.${minor}`;
  const label = /\(([^()]+)\)\s*$/.exec(heading);
  const headingStatus = label && statusPhrases(label[1]).length > 0 ? [label[1].trim()] : [];
  const anchor = vscodeAnchor(heading);
  const plain = markdownToText(sectionText);
  return {
    id: `vscode:${version}#${anchor}`,
    source: 'vscode-release-notes',
    version,
    edition,
    date,
    area,
    title: headingStatus.length > 0 ? heading.slice(0, label.index).trim() : heading,
    heading,
    url: `${vscodePageUrl(minor)}#${anchor}`,
    rawUrl: vscodeRawUrl(minor),
    settings: [...new Set([...sectionText.matchAll(/setting\(([^)\s]+)\)/g)].map(match => match[1]))],
    status: { title: headingStatus, body: statusPhrases(plain) },
    excerpt: excerpt(plain)
  };
}

export function parseVscodeNotes(markdown, minor) {
  const text = `${markdown}`.replace(/\r\n/g, '\n');
  const front = /^---\n([\s\S]*?)\n---\n/.exec(text);
  if (!front) throw new Error(`v1_${minor}.md has no frontmatter.`);
  const meta = {};
  for (const line of front[1].split('\n')) {
    const field = /^([A-Za-z]+):\s*(.*)$/.exec(line);
    if (field) meta[field[1]] = field[2].trim().replace(/^"(.*)"$/, '$1');
  }
  if (!isDate(meta.Date)) throw new Error(`v1_${minor}.md has no valid Date.`);
  const edition = meta.ProductEdition || '表記なし';
  const body = text.slice(front[0].length).replace(/<!--[\s\S]*?-->/g, '').replace(/^```[\s\S]*?^```/gm, '');
  const headings = [...body.matchAll(/^(#{2,4}) (.+)$/gm)].map(match => ({
    level: match[1].length,
    text: match[2].trim(),
    start: match.index,
    bodyStart: match.index + match[0].length
  }));
  const items = [];
  let area = null;
  headings.forEach((heading, index) => {
    const next = headings[index + 1];
    const sectionText = body.slice(heading.bodyStart, next ? next.start : body.length);
    if (heading.level === 2) {
      area = heading.text;
      const hasChildren = next !== undefined && next.level > 2;
      if (SKIPPED_AREAS.has(area) || hasChildren || !sectionText.trim()) return;
      items.push(sectionItem({ heading: heading.text, area: null, sectionText, minor, edition, date: meta.Date }));
      return;
    }
    if (SKIPPED_AREAS.has(area)) return;
    items.push(sectionItem({ heading: heading.text, area, sectionText, minor, edition, date: meta.Date }));
  });
  const lastUpdated = /_Last updated: ([^_]+)_/.exec(body)?.[1]?.trim() ?? null;
  return { version: `1.${minor}`, minor, edition, date: meta.Date, lastUpdated, items };
}

export async function collectChangelog({ since, until, fetchText }) {
  const items = [];
  for (let page = 1; page <= MAX_PAGES; page += 1) {
    const response = await fetchText(changelogPageUrl({ since, until, page }));
    // WordPress answers 400 for a page past the end.
    if (page > 1 && (response.status === 400 || response.status === 404)) return items;
    if (response.status !== 200) throw new Error(`GitHub Changelog page ${page}: HTTP ${response.status}.`);
    let entries;
    try {
      entries = JSON.parse(response.text);
    } catch {
      throw new Error(`GitHub Changelog page ${page}: the response is not JSON.`);
    }
    if (!Array.isArray(entries)) throw new Error(`GitHub Changelog page ${page}: expected a list of posts.`);
    for (const entry of entries) {
      const item = parseChangelogEntry(entry);
      if (item.date >= since && item.date <= until) items.push(item);
    }
    if (entries.length < PER_PAGE) return items;
  }
  throw new Error(`GitHub Changelog has more than ${MAX_PAGES * PER_PAGE} posts in range. Narrow the dates.`);
}

export function estimateMinor(since) {
  return Math.max(1, WEEKLY_ANCHOR.minor + Math.floor(daysBetween(WEEKLY_ANCHOR.date, since) / 7));
}

export async function collectVscode({ since, until, fetchText, startMinor = null, includeInsiders = true }) {
  const cache = new Map();
  const load = async minor => {
    if (cache.has(minor)) return cache.get(minor);
    if (cache.size >= MAX_NOTES) throw new Error(`Read ${MAX_NOTES} release notes without leaving the range. Pass --start-version.`);
    const response = await fetchText(vscodeRawUrl(minor));
    let notes = null;
    if (response.status === 200) notes = parseVscodeNotes(response.text, minor);
    else if (response.status !== 404) throw new Error(`VS Code 1.${minor} release notes: HTTP ${response.status}.`);
    cache.set(minor, notes);
    return notes;
  };
  const start = startMinor ?? estimateMinor(since);
  let probe = start;
  while (!(await load(probe))) {
    probe -= 1;
    if (probe < 1 || start - probe > 12) throw new Error(`No VS Code release notes found near 1.${start}.`);
  }
  let low = probe;
  while (low > 1) {
    const older = await load(low - 1);
    if (!older || older.date < since) break;
    low -= 1;
  }
  let high = probe;
  while (await load(high + 1)) high += 1;
  const included = [];
  for (let minor = low; minor <= high; minor += 1) {
    const notes = await load(minor);
    if (!notes || notes.date < since) continue;
    // Insiders notes describe the build in progress and carry its planned release date.
    // They are only meaningful when the range reaches today.
    if (notes.edition === 'Insiders' ? includeInsiders : notes.date <= until) included.push(notes);
  }
  return {
    versions: included.map(notes => ({ version: notes.version, edition: notes.edition, date: notes.date, lastUpdated: notes.lastUpdated })),
    items: included.flatMap(notes => notes.items)
  };
}

export async function fetchFromNetwork(url) {
  let current = url;
  for (let hop = 0; hop < 4; hop += 1) {
    const host = new URL(current).hostname;
    if (!ALLOWED_HOSTS.has(host)) throw new Error(`Refusing to fetch from ${host}.`);
    const response = await fetch(current, {
      headers: { 'User-Agent': 'code-to-doc-kit-release-radar' },
      redirect: 'manual',
      signal: AbortSignal.timeout(30000)
    });
    if (response.status >= 300 && response.status < 400 && response.headers.get('location')) {
      current = new URL(response.headers.get('location'), current).toString();
      continue;
    }
    return { status: response.status, text: await response.text() };
  }
  throw new Error(`Too many redirects for ${url}.`);
}

export function fixtureFetcher(mapFile) {
  const directory = path.dirname(mapFile);
  const map = JSON.parse(readFileSync(mapFile, 'utf8'));
  return async url => {
    const entry = map[url];
    if (entry === undefined) return { status: 404, text: '' };
    if (typeof entry === 'object') return { status: entry.status, text: entry.text ?? '' };
    return { status: 200, text: readFileSync(path.isAbsolute(entry) ? entry : path.join(directory, ...entry.split('/')), 'utf8') };
  };
}

export async function collect({ since, until, sources = SOURCES, fetchText = fetchFromNetwork, startMinor = null, now = new Date() }) {
  const result = { schemaVersion: 1, since, until, collectedAt: now.toISOString(), sources: [], items: [] };
  const includeInsiders = until >= localDate(now);
  if (sources.includes('github-changelog')) {
    try {
      const items = await collectChangelog({ since, until, fetchText });
      result.items.push(...items);
      result.sources.push({ name: 'github-changelog', status: 'ok', count: items.length });
    } catch (error) {
      result.sources.push({ name: 'github-changelog', status: 'failed', error: error.message });
    }
  }
  if (sources.includes('vscode-release-notes')) {
    try {
      const { versions, items } = await collectVscode({ since, until, fetchText, startMinor, includeInsiders });
      result.items.push(...items);
      result.sources.push({ name: 'vscode-release-notes', status: 'ok', count: items.length, versions });
    } catch (error) {
      result.sources.push({ name: 'vscode-release-notes', status: 'failed', error: error.message });
    }
  }
  // Stable sort: VS Code sections keep the order of their release notes.
  result.items.sort((a, b) => a.date.localeCompare(b.date) || a.source.localeCompare(b.source));
  return result;
}

export function overallStatus(result) {
  const failed = result.sources.filter(source => source.status !== 'ok').length;
  if (failed === 0) return 'done';
  return failed < result.sources.length ? 'partial' : 'failed';
}

export function localDate(now = new Date()) {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function parseArguments(argv) {
  const options = { since: null, until: null, out: null, sources: [...SOURCES], startMinor: null, fixtures: null, help: false, error: null };
  const valued = new Set(['--since', '--until', '--out', '--sources', '--start-version', '--fixtures']);
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--help' || argument === '-h') {
      options.help = true;
      continue;
    }
    if (!valued.has(argument)) {
      options.error = `Unknown argument ${argument}.`;
      break;
    }
    const value = argv[index + 1];
    index += 1;
    if (value === undefined || value.startsWith('--')) {
      options.error = `${argument} needs a value.`;
      break;
    }
    if (argument === '--since') options.since = value;
    else if (argument === '--until') options.until = value;
    else if (argument === '--out') options.out = value;
    else if (argument === '--fixtures') options.fixtures = value;
    else if (argument === '--sources') options.sources = value.split(',').map(name => name.trim()).filter(Boolean);
    else if (argument === '--start-version') {
      const match = /^1\.(\d+)$/.exec(value);
      if (!match) options.error = '--start-version must look like 1.137.';
      else options.startMinor = Number(match[1]);
    }
  }
  if (options.error || options.help) return options;
  options.until ??= localDate();
  if (!isDate(options.since)) options.error = 'Pass --since YYYY-MM-DD.';
  else if (!isDate(options.until)) options.error = '--until must be YYYY-MM-DD.';
  else if (options.since > options.until) options.error = '--since is after --until.';
  else if (options.sources.length === 0 || options.sources.some(name => !SOURCES.includes(name))) options.error = `--sources must be a comma-separated subset of ${SOURCES.join(',')}.`;
  return options;
}

function isMainModule() {
  if (!process.argv[1]) return false;
  const invoked = path.resolve(process.argv[1]);
  const self = fileURLToPath(import.meta.url);
  return process.platform === 'win32' ? invoked.toLowerCase() === self.toLowerCase() : invoked === self;
}

if (isMainModule()) {
  const options = parseArguments(process.argv.slice(2));
  if (options.help) {
    process.stdout.write('Usage: node collect-releases.mjs --since YYYY-MM-DD [--until YYYY-MM-DD] [--out <file>] [--sources github-changelog,vscode-release-notes] [--start-version 1.137]\n');
  } else if (options.error) {
    process.stdout.write(`uncheckable: ${options.error}\n`);
    process.exitCode = 3;
  } else {
    const fetchText = options.fixtures ? fixtureFetcher(path.resolve(options.fixtures)) : fetchFromNetwork;
    const result = await collect({ since: options.since, until: options.until, sources: options.sources, fetchText, startMinor: options.startMinor });
    const status = overallStatus(result);
    if (options.out) {
      const out = path.resolve(options.out);
      mkdirSync(path.dirname(out), { recursive: true });
      writeFileSync(out, `${JSON.stringify(result, null, 2)}\n`);
      for (const source of result.sources) {
        const detail = source.status === 'ok'
          ? `${source.count} items${source.versions ? ` (${source.versions.map(entry => `${entry.version} ${entry.edition}`).join(', ')})` : ''}`
          : source.error;
        process.stdout.write(`${source.name.padEnd(22)} ${source.status.padEnd(7)} ${detail}\n`);
      }
      process.stdout.write(`Wrote ${out}\nResult: ${status}\n`);
    } else {
      process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    }
    process.exitCode = status === 'done' ? 0 : status === 'partial' ? 1 : 3;
  }
}
