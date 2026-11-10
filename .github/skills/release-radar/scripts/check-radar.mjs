#!/usr/bin/env node
// Checks a release radar against the collection it was written from.
// pass means: every row cites an item from the collection (or sits in the manual section),
// its status is copied from that item's source, and its date or version matches.
// It does not judge whether the right items were chosen or summarized well.
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDate } from './collect-releases.mjs';

const COLUMNS = {
  evidence: ['根拠', 'Evidence'],
  status: ['状態（原文）', 'Status (verbatim)'],
  when: ['日付・版', 'Date and version']
};
const MANUAL_SECTIONS = new Set(['手動で追加した項目', 'Manual additions']);
const NO_LABEL = new Set(['表記なし', 'No label']);
const UNVERIFIED = new Set(['未確認', 'Unverified']);
const GA_EQUIVALENTS = new Set(['ga', 'generally available', 'general availability']);

function readMetadata(text) {
  const match = /<!--\s*release-radar\s*\n([\s\S]*?)\n\s*-->/.exec(text);
  if (!match) return { error: 'Missing the <!-- release-radar {...} --> metadata block.' };
  let value;
  try {
    value = JSON.parse(match[1]);
  } catch (error) {
    return { error: `The metadata is not valid JSON: ${error.message}` };
  }
  if (value?.schemaVersion !== 1) return { error: 'schemaVersion must be 1.' };
  for (const key of ['since', 'checkedAt']) if (!isDate(value[key])) return { error: `${key} must be a date (YYYY-MM-DD).` };
  if (value.checkedAt < value.since) return { error: 'checkedAt is before since.' };
  if (typeof value.collection !== 'string' || value.collection.length === 0) return { error: 'collection must name the collection file.' };
  return { value };
}

function splitRow(line) {
  const cells = line.trim().replace(/^\|/, '').replace(/\|$/, '').split(/(?<!\\)\|/);
  return cells.map(cell => cell.trim());
}

function findColumns(header) {
  const columns = {};
  for (const [key, names] of Object.entries(COLUMNS)) {
    const index = header.findIndex(cell => names.includes(cell));
    if (index >= 0) columns[key] = index;
  }
  return columns;
}

const firstUrl = cell => /https?:\/\/[^\s)>\]|]+/.exec(cell)?.[0] ?? null;
const MARKDOWN_LINK = /\[([^\]]*)\]\(\s*<?(https?:\/\/[^\s)>]+)>?(?:\s+"[^"]*")?\s*\)/g;

// The evidence is the link target (href), not the text a reader sees. Every URL visible in the cell,
// as link text or bare, must be the same URL as the target; otherwise the cell shows one source and
// points at another.
function evidenceUrl(cell) {
  const links = [...cell.matchAll(MARKDOWN_LINK)];
  if (links.length === 0) return { url: firstUrl(cell) };
  const url = links[0][2];
  const shown = [
    ...links.flatMap(link => [link[2], ...[...link[1].matchAll(/https?:\/\/[^\s)>\]|]+/g)].map(match => match[0])]),
    ...[...cell.replace(MARKDOWN_LINK, ' ').matchAll(/https?:\/\/[^\s)>\]|]+/g)].map(match => match[0])
  ];
  const other = shown.find(candidate => normalizeUrl(candidate) !== normalizeUrl(url));
  return { url, mismatch: other ?? null };
}

function normalizeUrl(url) {
  try {
    const parsed = new URL(url);
    parsed.hostname = parsed.hostname.toLowerCase();
    parsed.pathname = parsed.pathname.replace(/\/+$/, '') || '/';
    return parsed.toString();
  } catch {
    return url;
  }
}

const hasToken = (cell, token) => new RegExp(`(^|[^0-9.])${token.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&')}(?![0-9])`).test(cell);

function checkStatus(cell, item, report) {
  const parts = cell.split(/[／/、,]/).map(part => part.trim()).filter(Boolean);
  if (parts.length === 0) {
    report('error', 'status-missing', 'Write the status copied from the source, or 表記なし.');
    return;
  }
  const titled = item.status?.title ?? [];
  const mentioned = item.status?.body ?? [];
  const known = new Set([...titled, ...mentioned].map(phrase => phrase.toLowerCase()));
  const knownGa = [...known].some(phrase => GA_EQUIVALENTS.has(phrase));
  const titledLower = new Set(titled.map(phrase => phrase.toLowerCase()));
  const titledGa = [...titledLower].some(phrase => GA_EQUIVALENTS.has(phrase));
  // "GA" may abbreviate "generally available" or "general availability"; every other word must match.
  const matchesTitle = part => titledLower.has(part.toLowerCase()) || (part === 'GA' && titledGa);
  // The body often mentions an earlier status ("it was in public preview"), so a labeled item
  // must carry its own label, not only words found further down the text.
  if (titled.length > 0 && !parts.some(matchesTitle) && !parts.some(part => NO_LABEL.has(part))) {
    report('error', 'status-not-title-label', `The source labels this item "${titled.join('", "')}". Use that label.`);
  }
  for (const part of parts) {
    if (NO_LABEL.has(part)) {
      if (titled.length > 0) report('error', 'status-dropped', `The source labels this item "${titled.join('", "')}". Copy that instead of ${part}.`);
      else if (mentioned.length > 0) report('warning', 'status-in-text', `The source text mentions "${mentioned.join('", "')}". Check whether it applies to this item.`);
      continue;
    }
    if (UNVERIFIED.has(part)) {
      report('warning', 'status-unverified', 'This row is in the collection, so its status can be copied from the source.');
      continue;
    }
    const lower = part.toLowerCase();
    if (known.has(lower) || (part === 'GA' && knownGa)) continue;
    const found = [...titled, ...mentioned];
    report('error', 'status-not-in-source', `"${part}" does not appear in the source. The source says: ${found.length > 0 ? found.map(phrase => `"${phrase}"`).join(', ') : '(no status words)'}.`);
  }
}

function checkWhen(cell, item, report) {
  if (item.source === 'vscode-release-notes') {
    if (!hasToken(cell, item.version)) report('error', 'version', `Write the version ${item.version}.`);
    const saysInsiders = /insiders/i.test(cell);
    if (item.edition === 'Insiders' && !saysInsiders) report('error', 'edition', 'This comes from the Insiders release notes. Write "Insiders".');
    if (item.edition !== 'Insiders' && saysInsiders) report('error', 'edition', `This comes from the ${item.edition} release notes. Do not write "Insiders".`);
    return;
  }
  const [year, month, day] = item.date.split('-');
  const forms = [item.date, `${Number(month)}/${Number(day)}`, `${year}/${month}/${day}`];
  if (!forms.some(form => hasToken(cell, form))) report('error', 'date', `Write the date ${item.date} (or ${forms[1]}).`);
}

export function checkRadar({ radarText, collection }) {
  const findings = [];
  const counts = { rows: 0, verified: 0, manual: 0 };
  const text = `${radarText}`.replace(/\r\n/g, '\n');
  const metadata = readMetadata(text);
  if (metadata.error) findings.push({ severity: 'error', line: 1, rule: 'metadata', message: metadata.error });
  const failed = collection.sources.filter(source => source.status !== 'ok');
  if (failed.length > 0) findings.push({ severity: 'error', line: 1, rule: 'incomplete-collection', message: `The collection is incomplete: ${failed.map(source => `${source.name} (${source.error ?? source.status})`).join(', ')}. Collect again.` });
  if (metadata.value) {
    if (collection.since > metadata.value.since) findings.push({ severity: 'error', line: 1, rule: 'collection-range', message: `The collection starts on ${collection.since}, after the radar's since date ${metadata.value.since}.` });
    if (collection.until < metadata.value.checkedAt) findings.push({ severity: 'error', line: 1, rule: 'collection-range', message: `The collection ends on ${collection.until}, before checkedAt ${metadata.value.checkedAt}.` });
  }
  const byUrl = new Map(collection.items.map(item => [normalizeUrl(item.url), item]));
  const seen = new Set();
  const lines = text.split('\n');
  let section = null;
  let header = null;
  let columns = null;
  lines.forEach((line, index) => {
    const lineNumber = index + 1;
    const report = (severity, rule, message) => findings.push({ severity, line: lineNumber, rule, message });
    const heading = /^##\s+(.+?)\s*$/.exec(line);
    if (heading) {
      section = heading[1];
      header = null;
      return;
    }
    if (!line.trim().startsWith('|')) {
      header = null;
      return;
    }
    const cells = splitRow(line);
    if (!header) {
      header = cells;
      columns = findColumns(cells);
      const missing = Object.keys(COLUMNS).filter(key => columns[key] === undefined);
      if (missing.length > 0) report('error', 'table-columns', `The table needs the columns ${missing.map(key => COLUMNS[key][0]).join('、')}.`);
      return;
    }
    if (cells.every(cell => /^:?-{3,}:?$/.test(cell))) return;
    if (Object.keys(COLUMNS).some(key => columns[key] === undefined)) return;
    counts.rows += 1;
    const evidence = evidenceUrl(cells[columns.evidence] ?? '');
    const url = evidence.url;
    if (!url) {
      report('error', 'evidence', 'Every row needs an evidence URL.');
      return;
    }
    if (evidence.mismatch) {
      report('error', 'evidence-link-mismatch', `The evidence link shows ${evidence.mismatch} but points to ${url}. Show and link the same URL.`);
      return;
    }
    const manual = MANUAL_SECTIONS.has(section);
    const item = byUrl.get(normalizeUrl(url));
    if (!item) {
      if (manual) {
        counts.manual += 1;
        if (!(cells[columns.status] ?? '').trim()) report('error', 'status-missing', 'Write the status copied from the source, or 未確認.');
        return;
      }
      report('error', 'not-in-collection', `${url} is not in the collection. Collect it, or move the row to "手動で追加した項目".`);
      return;
    }
    if (manual) report('warning', 'manual-but-collected', 'This item is in the collection. Move it to a regular section so that it is checked.');
    if (seen.has(item.id)) report('warning', 'duplicate', `${url} appears more than once.`);
    seen.add(item.id);
    checkStatus(cells[columns.status] ?? '', item, report);
    checkWhen(cells[columns.when] ?? '', item, report);
    counts.verified += 1;
  });
  const errors = findings.filter(finding => finding.severity === 'error');
  const warnings = findings.filter(finding => finding.severity === 'warning');
  if (counts.rows === 0) errors.push({ severity: 'error', line: 1, rule: 'empty', message: 'The radar has no rows.' });
  return { status: errors.length > 0 ? 'invalid' : 'pass', errors, warnings, counts };
}

function loadCollection(file) {
  if (!existsSync(file)) return { error: `The collection ${file} does not exist.` };
  let value;
  try {
    value = JSON.parse(readFileSync(file, 'utf8'));
  } catch (error) {
    return { error: `The collection is not valid JSON: ${error.message}` };
  }
  if (value?.schemaVersion !== 1 || !Array.isArray(value.items) || !Array.isArray(value.sources) || !isDate(value.since) || !isDate(value.until)) {
    return { error: 'The collection does not look like collect-releases.mjs output.' };
  }
  return { value };
}

export function checkRadarFile({ radar, collection = null }) {
  if (!existsSync(radar)) return { status: 'uncheckable', reason: `The radar ${radar} does not exist.`, errors: [], warnings: [], counts: null };
  const radarText = readFileSync(radar, 'utf8');
  let collectionFile = collection;
  if (!collectionFile) {
    const metadata = readMetadata(radarText.replace(/\r\n/g, '\n'));
    if (metadata.error) return { status: 'uncheckable', reason: `${metadata.error} Pass --collection to check the rows anyway.`, errors: [], warnings: [], counts: null };
    collectionFile = path.resolve(path.dirname(radar), metadata.value.collection);
  }
  const loaded = loadCollection(collectionFile);
  if (loaded.error) return { status: 'uncheckable', reason: loaded.error, errors: [], warnings: [], counts: null };
  return checkRadar({ radarText, collection: loaded.value });
}

function parseArguments(argv) {
  const options = { radar: null, collection: null, json: false, help: false, error: null };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--json') options.json = true;
    else if (argument === '--help' || argument === '-h') options.help = true;
    else if (argument === '--collection') {
      const value = argv[index + 1];
      if (value === undefined || value.startsWith('--')) options.error = '--collection needs a value.';
      else options.collection = value;
      index += 1;
    } else if (argument.startsWith('--')) options.error = `Unknown argument ${argument}.`;
    else if (options.radar) options.error = 'Pass one radar file.';
    else options.radar = argument;
  }
  if (!options.error && !options.help && !options.radar) options.error = 'Pass the radar file.';
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
    process.stdout.write('Usage: node check-radar.mjs <radar.md> [--collection <collection.json>] [--json]\n');
  } else {
    const result = options.error
      ? { status: 'uncheckable', reason: options.error, errors: [], warnings: [], counts: null }
      : checkRadarFile({ radar: path.resolve(options.radar), collection: options.collection ? path.resolve(options.collection) : null });
    if (options.json) {
      process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    } else {
      for (const finding of result.errors) process.stdout.write(`L${finding.line} ${finding.rule}: ${finding.message}\n`);
      for (const finding of result.warnings) process.stdout.write(`warning L${finding.line} ${finding.rule}: ${finding.message}\n`);
      if (result.reason) process.stdout.write(`reason: ${result.reason}\n`);
      const counts = result.counts ? ` (rows ${result.counts.rows}, verified ${result.counts.verified}, manual ${result.counts.manual})` : '';
      process.stdout.write(`Result: ${result.status}${counts}\n`);
    }
    process.exitCode = result.status === 'pass' ? 0 : result.status === 'invalid' ? 1 : 3;
  }
}
