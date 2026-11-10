#!/usr/bin/env node
// Structural and citation checker for Code-to-Doc As-Is documents.
// "pass" means only that the layout and every evidence target are valid (and that a reviewed
// document names its owner). It never proves that a claim is true or that the document is complete.
import { lstatSync, readFileSync, realpathSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
export const DEFAULT_CONTRACT = path.join(scriptDirectory, '..', 'references', 'artifact-contract.json');
export const EXIT_CODES = Object.freeze({ pass: 0, invalid: 1, uncheckable: 3 });

const SEGMENT = /^[A-Za-z0-9_.-]+$/;
const DEVICE_NAME = /^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\.|$)/i;
const TOKEN = /\[\[([A-Za-z-]+):([^\]\s]*)\]\]/g;
const SOURCE_REF = /^(.+)#L([1-9][0-9]*)(?:-L([1-9][0-9]*))?$/;
const TEST_REF = /^(.+)#([A-Za-z0-9_.:-]{1,80})$/;
const OBSERVATION_ID = /^OBS-[0-9]{3,6}$/;
const UNKNOWN_ID = /^U-[0-9]{1,4}$/;
const SHA = /^[0-9a-f]{40}$/;
const OPEN_FENCE = /^\s{0,3}(`{3,}|~{3,})\s*([^`\s]*)/;
const CLOSE_FENCE = /^\s{0,3}(`{3,}|~{3,})\s*$/;
const HEADING_2 = /^##\s+(.+?)\s*#*\s*$/;
const BULLET = /^\s*(?:[-*+]|[0-9]+[.)])\s+\S/;
const TABLE_ROW = /^\s*\|.*\|\s*$/;
const TABLE_SEPARATOR = /^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)*\|?\s*$/;
const MERMAID_START = /^(graph|flowchart|sequenceDiagram|classDiagram|erDiagram|stateDiagram(?:-v2)?)(?=\s|$)/;
const NONE_WITH_REASON = /^\s*[-*+]\s*(?:なし|None(?=\W|$)).*(?:理由|reason)/i;
const UNKNOWN_DEFINITION = /^\s*(?:[-*+]\s+|\|\s*)(U-[0-9]{1,4})(?![0-9])/;
const OBSERVATION_ROW = /^\s*\|\s*(OBS-[0-9]{3,6})\s*\|/;
const METADATA_KEYS = new Set(['schemaVersion', 'kind', 'scope', 'status', 'sourceCommit', 'observationLog']);
const EVIDENCE_TYPES = new Set(['source', 'runtime', 'test']);
const PLACEHOLDER = /\{\{[^{}]*\}\}/;
const ID_CHARACTER = /[A-Za-z0-9_.:-]/;
// Document lifecycle fields in an optional YAML frontmatter block before the metadata comment.
// Only people write them; other frontmatter keys are left to other tools and ignored here.
export const LIFECYCLE_FIELDS = Object.freeze(['owner', 'sourceRevision', 'reviewedAt', 'reviewBy']);
const FRONTMATTER_START = /^\uFEFF?---[ \t]*$/;
const FRONTMATTER_END = /^(?:---|\.\.\.)[ \t]*$/;
const FRONTMATTER_KEY = /^([A-Za-z][A-Za-z0-9_-]*)[ \t]*:(?:[ \t]+(.*))?$/;
const UNSET_VALUE = /^(?:~|null|Null|NULL)?$/;
const ISO_DATE = /^([0-9]{4})-([0-9]{2})-([0-9]{2})$/;

// True when `id` appears in `text` as a whole token: CHAR-ORD-001 must not match CHAR-ORD-0011.
export function containsToken(text, id) {
  for (let index = text.indexOf(id); index >= 0; index = text.indexOf(id, index + 1)) {
    const before = index === 0 ? '' : text[index - 1];
    let after = text[index + id.length] ?? '';
    // Sentence punctuation after an ID ("CHAR-ORD-001." or "CHAR-ORD-001:") still ends the token.
    if ((after === '.' || after === ':') && !ID_CHARACTER.test(text[index + id.length + 1] ?? '')) after = '';
    if (!ID_CHARACTER.test(before) && !ID_CHARACTER.test(after)) return true;
  }
  return false;
}

function problem(code, line, message) {
  return { code, line, message };
}

export function loadContract(contractPath = DEFAULT_CONTRACT) {
  let contract;
  try {
    contract = JSON.parse(readFileSync(contractPath, 'utf8'));
  } catch {
    return null;
  }
  if (contract?.schemaVersion !== 1 || !contract.kinds || typeof contract.kinds !== 'object'
    || typeof contract.marker !== 'string' || !Array.isArray(contract.statusValues)) {
    return null;
  }
  return contract;
}

export function safeRelativeParts(value) {
  if (typeof value !== 'string' || value.length === 0 || value.length > 240) return null;
  if (value.includes('\\') || value.startsWith('/') || /^[A-Za-z]:/.test(value)) return null;
  const parts = value.split('/');
  for (const part of parts) {
    if (!SEGMENT.test(part) || part === '.' || part === '..' || part.toLowerCase() === '.git'
      || DEVICE_NAME.test(part) || /\.$/.test(part)) {
      return null;
    }
  }
  return parts;
}

function isInside(base, target) {
  const relative = path.relative(base, target);
  return relative !== '' && relative !== '..' && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative);
}

// Resolves a repository-relative path to a regular file without following links.
export function locateFile(root, relative, maxBytes) {
  const parts = safeRelativeParts(relative);
  if (!parts) return { error: 'unsafe-path' };
  let current = root;
  for (let index = 0; index < parts.length; index += 1) {
    current = path.join(current, parts[index]);
    let stat;
    try {
      stat = lstatSync(current);
    } catch (error) {
      if (error.code === 'ENOENT' || error.code === 'ENOTDIR') return { error: 'missing' };
      return { error: 'access', detail: error.code };
    }
    if (stat.isSymbolicLink()) return { error: 'link' };
    const last = index === parts.length - 1;
    if (last ? !stat.isFile() : !stat.isDirectory()) return { error: 'not-file' };
    if (last && stat.size > maxBytes) return { error: 'too-large' };
  }
  try {
    if (!isInside(realpathSync.native(root), realpathSync.native(current))) return { error: 'link' };
  } catch (error) {
    return { error: 'access', detail: error.code };
  }
  return { filename: current };
}

function readUtf8(filename) {
  try {
    let text = new TextDecoder('utf-8', { fatal: true }).decode(readFileSync(filename));
    if (text.charCodeAt(0) === 0xfeff) text = text.slice(1);
    return { text };
  } catch (error) {
    if (error instanceof TypeError) return { error: 'invalid-utf8' };
    return { error: 'access', detail: error.code };
  }
}

export function splitLines(text) {
  const lines = text.split(/\r\n|\n|\r/);
  if (lines.length > 0 && lines[lines.length - 1] === '') lines.pop();
  return lines;
}

function tokensIn(text) {
  return [...text.matchAll(TOKEN)].map(match => ({ type: match[1], value: match[2] }));
}

// One flat YAML scalar: surrounding quotes removed, or a trailing " # comment" dropped from a plain value.
function scalarValue(raw) {
  const value = raw.trim();
  const quote = value[0];
  if (quote === '"' || quote === "'") {
    const end = value.indexOf(quote, 1);
    return end > 0 ? value.slice(1, end) : value.slice(1);
  }
  return value.replace(/(?:^|[ \t])#.*$/, '').trim();
}

// Empty, null, ~ and unfilled template placeholders ({{...}}) all mean "not set yet".
export function isSet(value) {
  return typeof value === 'string' && !UNSET_VALUE.test(value) && !PLACEHOLDER.test(value);
}

// A real calendar date written as YYYY-MM-DD.
export function isIsoDate(value) {
  const match = ISO_DATE.exec(String(value));
  if (!match) return false;
  const [year, month, day] = match.slice(1).map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function localDate(now = new Date()) {
  const pad = number => String(number).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

// The optional YAML frontmatter: a "---" first line up to the next "---" (or "...") line.
// Only top-level `key: value` lines of the lifecycle fields are read, and no line of the block is a claim.
// `end` is the index of the closing line. An unclosed block yields no fields.
export function readFrontmatter(lines) {
  if (lines.length === 0 || !FRONTMATTER_START.test(lines[0])) return { present: false, closed: false, end: -1, fields: {} };
  const end = lines.findIndex((line, index) => index > 0 && FRONTMATTER_END.test(line));
  if (end < 0) return { present: true, closed: false, end: -1, fields: {} };
  const fields = {};
  for (let index = 1; index < end; index += 1) {
    const match = FRONTMATTER_KEY.exec(lines[index]);
    if (match && LIFECYCLE_FIELDS.includes(match[1])) fields[match[1]] = { value: scalarValue(match[2] ?? ''), line: index + 1 };
  }
  return { present: true, closed: true, end, fields };
}

// Lifecycle rules. A wrong date and an overdue review are warnings; a reviewed document without an owner is invalid.
function checkLifecycle(frontmatter, metadata, metadataLine, today, problems, warnings) {
  const { fields } = frontmatter;
  for (const name of ['reviewedAt', 'reviewBy']) {
    const field = fields[name];
    if (field && isSet(field.value) && !isIsoDate(field.value)) {
      warnings.push(problem('frontmatter-date', field.line, `${name} must be a date written as YYYY-MM-DD: "${field.value}".`));
    }
  }
  const reviewBy = fields.reviewBy;
  if (reviewBy && isSet(reviewBy.value) && isIsoDate(reviewBy.value) && reviewBy.value < today) {
    warnings.push(problem('review-overdue', reviewBy.line, `reviewBy ${reviewBy.value} has passed (today is ${today}). Re-read the document even if nothing drifted; a person then updates reviewedAt and reviewBy.`));
  }
  if (metadata?.status === 'reviewed' && !isSet(fields.owner?.value)) {
    problems.push(problem('owner-missing', fields.owner?.line ?? metadataLine, 'A reviewed document needs an owner (a person or team) in its YAML frontmatter. Only a person writes it.'));
  }
}

function parseMetadata(lines, contract, problems, startIndex = 0) {
  const opener = `<!-- ${contract.marker}`;
  let first = -1;
  for (let index = startIndex; index < lines.length && first < 0; index += 1) if (lines[index].trim() !== '') first = index;
  if (first < 0 || !lines[first].trim().startsWith(opener)) {
    problems.push(problem('metadata-missing', first < 0 ? startIndex + 1 : first + 1,
      `The first non-empty line${startIndex > 0 ? ' after the YAML frontmatter' : ''} must open a "${opener}" metadata comment.`));
    return null;
  }
  const close = lines.findIndex((line, index) => index > first && line.trim() === '-->');
  if (close < 0) {
    problems.push(problem('metadata-unclosed', first + 1, 'The metadata comment is not closed with a "-->" line.'));
    return null;
  }
  const inline = lines[first].trim().slice(opener.length);
  let metadata;
  try {
    metadata = JSON.parse([inline, ...lines.slice(first + 1, close)].join('\n'));
  } catch {
    problems.push(problem('metadata-json', first + 1, 'The metadata comment does not contain valid JSON.'));
    return { line: first + 1, endIndex: close, metadata: null };
  }
  const at = first + 1;
  if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
    problems.push(problem('metadata-json', at, 'The metadata must be a JSON object.'));
    return { line: at, endIndex: close, metadata: null };
  }
  for (const key of Object.keys(metadata)) {
    if (!METADATA_KEYS.has(key)) problems.push(problem('metadata-field', at, `Unknown metadata field "${key}".`));
  }
  if (metadata.schemaVersion !== 1) problems.push(problem('metadata-version', at, 'schemaVersion must be 1.'));
  if (typeof metadata.kind !== 'string' || !Object.hasOwn(contract.kinds, metadata.kind)) {
    problems.push(problem('metadata-kind', at, `kind must be one of: ${Object.keys(contract.kinds).join(', ')}.`));
  }
  if (typeof metadata.scope !== 'string' || metadata.scope.trim() === '' || metadata.scope.length > 300) {
    problems.push(problem('metadata-scope', at, 'scope must be a non-empty string of at most 300 characters.'));
  }
  if (!contract.statusValues.includes(metadata.status)) {
    problems.push(problem('metadata-status', at, `status must be one of: ${contract.statusValues.join(', ')}.`));
  }
  if (metadata.sourceCommit !== undefined && !(typeof metadata.sourceCommit === 'string' && SHA.test(metadata.sourceCommit))) {
    problems.push(problem('metadata-commit', at, 'sourceCommit, when present, must be a 40-character lowercase hexadecimal SHA.'));
  }
  if (metadata.observationLog !== undefined && !safeRelativeParts(metadata.observationLog)) {
    problems.push(problem('metadata-observation-log', at, 'observationLog must be a safe repository-relative path.'));
  }
  return { line: at, endIndex: close, metadata };
}

// Splits the body into "## " sections. Fenced blocks are kept apart so that
// diagram or code text is never read as a claim.
function collectSections(lines, startIndex) {
  const preamble = { name: null, line: startIndex + 1, lines: [], fences: [] };
  const sections = [];
  let current = preamble;
  let fence = null;
  for (let index = startIndex; index < lines.length; index += 1) {
    const text = lines[index];
    const number = index + 1;
    if (fence) {
      const close = CLOSE_FENCE.exec(text);
      if (close && close[1][0] === fence.char && close[1].length >= fence.length) {
        current.fences.push(fence);
        fence = null;
      } else {
        fence.body.push({ number, text });
      }
      continue;
    }
    const open = OPEN_FENCE.exec(text);
    if (open) {
      fence = { char: open[1][0], length: open[1].length, language: open[2].toLowerCase(), line: number, body: [] };
      continue;
    }
    const heading = HEADING_2.exec(text);
    if (heading) {
      current = { name: heading[1].trim(), line: number, lines: [], fences: [] };
      sections.push(current);
      continue;
    }
    current.lines.push({ number, text });
  }
  return { preamble, sections, unclosedFence: fence };
}

function checkLayout(sections, required, problems, fallbackLine) {
  const names = sections.map(section => section.name);
  const seen = new Set();
  for (const section of sections) {
    if (!required.includes(section.name)) {
      problems.push(problem('unexpected-section', section.line, `Unexpected section "## ${section.name}".`));
    } else if (seen.has(section.name)) {
      problems.push(problem('duplicate-section', section.line, `Duplicate section "## ${section.name}".`));
    }
    seen.add(section.name);
  }
  const missing = required.filter(name => !seen.has(name));
  for (const name of missing) {
    problems.push(problem('missing-section', fallbackLine, `Missing required section "## ${name}".`));
  }
  if (missing.length === 0 && seen.size === names.length) {
    const order = names.filter(name => required.includes(name));
    if (order.some((name, index) => name !== required[index])) {
      problems.push(problem('section-order', sections[0]?.line ?? fallbackLine, `Sections must appear in this order: ${required.join(', ')}.`));
    }
  }
}

function isHeaderRow(rows, index) {
  const next = rows[index + 1];
  return Boolean(next && TABLE_ROW.test(next.text) && TABLE_SEPARATOR.test(next.text));
}

export function checkDocument({ root, relativePath, contract, cache, today = localDate() }) {
  const result = { path: relativePath, status: 'pass', kind: null, problems: [], warnings: [], citations: 0 };
  const fail = (status, code, message, line = null) => {
    result.status = status;
    result.problems.push(problem(code, line, message));
    return result;
  };
  const located = locateFile(root, relativePath, contract.limits.docBytes);
  if (located.error) {
    const errors = {
      'unsafe-path': ['invalid', 'unsafe-path', 'The document path must be a safe repository-relative path.'],
      missing: ['invalid', 'artifact-missing', 'The selected document does not exist.'],
      link: ['invalid', 'unsafe-link', 'Symbolic links, junctions and paths leaving the repository are rejected.'],
      'not-file': ['invalid', 'unsafe-path', 'Expected directories followed by one regular file.'],
      'too-large': ['invalid', 'size-limit', 'The document exceeds the size limit.'],
      access: ['uncheckable', 'file-access', 'The document cannot be inspected.']
    };
    const [status, code, message] = errors[located.error];
    return fail(status, code, message);
  }
  if (!relativePath.endsWith('.md')) return fail('invalid', 'not-markdown', 'Only Markdown (.md) documents can be checked.');
  const read = readUtf8(located.filename);
  if (read.error) {
    return read.error === 'invalid-utf8'
      ? fail('invalid', 'invalid-utf8', 'The document must be UTF-8 text.')
      : fail('uncheckable', 'file-access', 'The document cannot be read.');
  }

  const problems = result.problems;
  const lines = splitLines(read.text);
  const frontmatter = readFrontmatter(lines);
  if (frontmatter.present && !frontmatter.closed) {
    problems.push(problem('frontmatter-unclosed', 1, 'The YAML frontmatter that starts on line 1 is not closed with a "---" line.'));
  }
  const bodyStart = frontmatter.closed ? frontmatter.end + 1 : 0;
  const parsedMetadata = parseMetadata(lines, contract, problems, bodyStart);
  const metadata = parsedMetadata?.metadata ?? null;
  const body = collectSections(lines, parsedMetadata ? parsedMetadata.endIndex + 1 : bodyStart);
  if (body.unclosedFence) problems.push(problem('fence-unclosed', body.unclosedFence.line, 'A fenced block is not closed.'));
  checkLifecycle(frontmatter, metadata, parsedMetadata?.line ?? bodyStart + 1, today, problems, result.warnings);

  const kind = metadata && Object.hasOwn(contract.kinds, metadata.kind) ? metadata.kind : null;
  result.kind = kind;
  if (kind) {
    checkLayout(body.sections, contract.kinds[kind].sections, problems, (parsedMetadata?.endIndex ?? 0) + 1);
    for (const name of contract.kinds[kind].mermaid) {
      const section = body.sections.find(entry => entry.name === name);
      if (!section) continue;
      const diagram = section.fences.find(fence => fence.language === 'mermaid'
        && MERMAID_START.test((fence.body.find(line => line.text.trim() !== '')?.text ?? '').trim()));
      if (!diagram) {
        problems.push(problem('mermaid-missing', section.line, `"## ${name}" needs a mermaid diagram (flowchart, sequenceDiagram, classDiagram, erDiagram or stateDiagram).`));
      } else if (!section.lines.some(line => tokensIn(line.text).some(token => EVIDENCE_TYPES.has(token.type)))) {
        problems.push(problem('diagram-uncited', section.line, `"## ${name}" needs at least one cited line that supports its diagram.`));
      }
    }
  }

  const observationLog = typeof metadata?.observationLog === 'string' ? metadata.observationLog : contract.defaultObservationLog;
  let observations;
  let observationLogReported = false;
  const loadObservations = () => {
    if (observations !== undefined) return observations;
    const found = locateFile(root, observationLog, contract.limits.sourceBytes);
    const text = found.error ? null : readUtf8(found.filename).text;
    if (text === undefined || text === null) {
      observations = null;
      return observations;
    }
    // id -> { count, placeholder }. A row that still holds template placeholders ({{...}}) is not an observation.
    observations = new Map();
    for (const line of splitLines(text)) {
      const id = OBSERVATION_ROW.exec(line)?.[1];
      if (!id) continue;
      const entry = observations.get(id) ?? { count: 0, placeholder: false };
      entry.count += 1;
      if (PLACEHOLDER.test(line)) entry.placeholder = true;
      observations.set(id, entry);
    }
    return observations;
  };
  const sourceInfo = relative => {
    const key = `source:${relative}`;
    if (!cache.has(key)) {
      const found = locateFile(root, relative, contract.limits.sourceBytes);
      if (found.error) {
        cache.set(key, { error: found.error });
      } else {
        const read = readUtf8(found.filename);
        cache.set(key, read.error ? { error: read.error } : { text: read.text, lineCount: splitLines(read.text).length });
      }
    }
    return cache.get(key);
  };
  const fileErrors = {
    missing: ['citation-missing', 'The cited file does not exist.'],
    'unsafe-path': ['unsafe-path', 'The cited path must be a safe repository-relative path.'],
    'not-file': ['unsafe-path', 'The cited path must name a regular file.'],
    link: ['unsafe-link', 'Cited files must not be links or leave the repository.'],
    'too-large': ['size-limit', 'The cited file exceeds the size limit.'],
    'invalid-utf8': ['citation-encoding', 'The cited file is not UTF-8 text.'],
    access: ['file-access', 'The cited file cannot be read.']
  };

  const unknownReferences = [];
  const allSections = [body.preamble, ...body.sections];
  for (const section of allSections) {
    for (const { number, text } of section.lines) {
      for (const token of tokensIn(text)) {
        if (EVIDENCE_TYPES.has(token.type)) result.citations += 1;
        if (token.type === 'source') {
          const match = SOURCE_REF.exec(token.value);
          if (!match) {
            problems.push(problem('citation-format', number, `Use [[source:path#Lstart-Lend]]: "${token.value}".`));
            continue;
          }
          const [, target, startText, endText] = match;
          const start = Number(startText);
          const end = endText ? Number(endText) : start;
          // Case-folded: on case-insensitive file systems (Windows, macOS) .GITHUB/ is the same folder as .github/.
          const folded = target.toLowerCase();
          if (contract.forbiddenEvidencePrefixes.some(prefix => folded.startsWith(prefix.toLowerCase())) || folded === relativePath.toLowerCase()) {
            problems.push(problem('forbidden-evidence', number, `Customization files, runtime output and As-Is documents are not application evidence: "${target}".`));
            continue;
          }
          const info = sourceInfo(target);
          if (info.error) {
            const [code, message] = fileErrors[info.error];
            problems.push(problem(code, number, `${message} (${target})`));
            continue;
          }
          if (end < start || end > info.lineCount) {
            problems.push(problem('citation-range', number, `Line range L${start}-L${end} is outside ${target} (${info.lineCount} lines).`));
          } else if (end - start + 1 > contract.limits.rangeLines) {
            problems.push(problem('citation-too-wide', number, `Cite at most ${contract.limits.rangeLines} lines per citation.`));
          }
        } else if (token.type === 'runtime') {
          if (!OBSERVATION_ID.test(token.value)) {
            problems.push(problem('runtime-format', number, `Use [[runtime:OBS-001]]: "${token.value}".`));
            continue;
          }
          const ids = loadObservations();
          if (ids === null) {
            if (!observationLogReported) {
              problems.push(problem('observation-log-missing', number, `Runtime evidence needs the observation log ${observationLog}.`));
              observationLogReported = true;
            }
          } else if (!ids.has(token.value)) {
            problems.push(problem('runtime-missing', number, `${token.value} is not recorded in ${observationLog}.`));
          } else if (ids.get(token.value).count > 1) {
            problems.push(problem('runtime-duplicate', number, `${token.value} appears ${ids.get(token.value).count} times in ${observationLog}. Give each observation its own ID.`));
          } else if (ids.get(token.value).placeholder) {
            problems.push(problem('runtime-placeholder', number, `${token.value} in ${observationLog} still holds template placeholders ({{...}}). Record the observation first.`));
          }
        } else if (token.type === 'test') {
          const match = TEST_REF.exec(token.value);
          if (!match) {
            problems.push(problem('test-format', number, `Use [[test:path#TEST-ID]]: "${token.value}".`));
            continue;
          }
          const [, target, id] = match;
          const info = sourceInfo(target);
          if (info.error) {
            const [code, message] = fileErrors[info.error];
            problems.push(problem(code === 'citation-missing' ? 'test-missing' : code, number, `${message} (${target})`));
          } else if (!containsToken(info.text, id)) {
            problems.push(problem('test-id-missing', number, `${id} does not appear in ${target}.`));
          }
        } else if (token.type === 'unknown') {
          if (!UNKNOWN_ID.test(token.value)) {
            problems.push(problem('unknown-format', number, `Use [[unknown:U-1]]: "${token.value}".`));
          } else {
            unknownReferences.push({ id: token.value, number });
          }
        } else {
          problems.push(problem('unknown-token', number, `Unknown reference type "${token.type}". Use source, runtime, test or unknown.`));
        }
      }
    }
  }
  if (result.citations > contract.limits.citations) {
    problems.push(problem('too-many-citations', null, `Use at most ${contract.limits.citations} evidence references per document.`));
  }

  for (const section of body.sections) {
    if (contract.freeSections.includes(section.name)) continue;
    const rows = section.lines;
    for (let index = 0; index < rows.length; index += 1) {
      const { number, text } = rows[index];
      const trimmed = text.trim();
      if (trimmed === '' || trimmed.startsWith('<!--') || trimmed.startsWith('#')) continue;
      const isTable = TABLE_ROW.test(text);
      if (isTable && (TABLE_SEPARATOR.test(text) || isHeaderRow(rows, index))) continue;
      const isClaim = isTable || BULLET.test(text);
      const supported = tokensIn(text).some(token => EVIDENCE_TYPES.has(token.type) || token.type === 'unknown');
      if (supported) continue;
      if (isClaim) {
        problems.push(problem('uncited-claim', number, 'List items and table rows need [[source:...]], [[runtime:...]], [[test:...]] or [[unknown:U-n]].'));
      } else {
        result.warnings.push(problem('uncited-prose', number, 'Prose without evidence. Put claims in cited list items or move them to Unknowns.'));
      }
    }
  }

  const evidence = body.sections.find(section => section.name === 'Evidence');
  if (evidence && !evidence.lines.some(line => tokensIn(line.text).some(token => EVIDENCE_TYPES.has(token.type)))) {
    problems.push(problem('evidence-empty', evidence.line, '"## Evidence" must map claims to at least one source, runtime or test reference.'));
  }
  const unknowns = body.sections.find(section => section.name === 'Unknowns');
  if (unknowns) {
    const defined = new Set();
    for (const line of unknowns.lines) {
      const id = UNKNOWN_DEFINITION.exec(line.text)?.[1];
      if (!id) continue;
      if (defined.has(id)) problems.push(problem('unknown-duplicate', line.number, `${id} is defined more than once in "## Unknowns".`));
      defined.add(id);
    }
    const none = unknowns.lines.some(line => NONE_WITH_REASON.test(line.text));
    if (defined.size === 0 && !none) {
      problems.push(problem('unknowns-empty', unknowns.line, 'List unknowns as "- U-1: ..." or write "- なし（理由: ...）" / "- None (reason: ...)".'));
    }
    for (const reference of unknownReferences) {
      if (!defined.has(reference.id)) problems.push(problem('unknown-ref', reference.number, `${reference.id} is not defined in "## Unknowns".`));
    }
  }

  if (problems.length > 0) result.status = 'invalid';
  return result;
}

export function checkDocuments({ root, documents, contractPath = DEFAULT_CONTRACT, today = localDate() }) {
  const contract = loadContract(contractPath);
  if (!contract) return { status: 'uncheckable', reason: 'contract-invalid', documents: [] };
  if (!Array.isArray(documents) || documents.length === 0) return { status: 'uncheckable', reason: 'no-target', documents: [] };
  if (!isIsoDate(today)) return { status: 'uncheckable', reason: 'today-invalid', documents: [] };
  let resolvedRoot;
  try {
    resolvedRoot = realpathSync.native(root);
    if (!lstatSync(resolvedRoot).isDirectory()) throw new Error('not a directory');
  } catch {
    return { status: 'uncheckable', reason: 'root-invalid', documents: [] };
  }
  const cache = new Map();
  const results = documents.map(relativePath => checkDocument({ root: resolvedRoot, relativePath, contract, cache, today }));
  const status = results.some(entry => entry.status === 'invalid')
    ? 'invalid'
    : results.some(entry => entry.status === 'uncheckable') ? 'uncheckable' : 'pass';
  return { status, documents: results };
}

function normalizeArgument(value) {
  return value.replace(/\\/g, '/').replace(/^(\.\/)+/, '');
}

function parseArguments(argv) {
  const options = { root: process.cwd(), json: false, contract: DEFAULT_CONTRACT, today: undefined, documents: [], help: false, error: null };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--json') options.json = true;
    else if (argument === '--help' || argument === '-h') options.help = true;
    else if (argument === '--root' || argument === '--contract' || argument === '--today') {
      const value = argv[index + 1];
      if (value === undefined || value.startsWith('--')) options.error = `${argument} needs a value.`;
      else options[argument.slice(2)] = value;
      index += 1;
    } else if (argument.startsWith('--')) options.error = `Unknown option ${argument}.`;
    else options.documents.push(normalizeArgument(argument));
  }
  return options;
}

function formatText(report) {
  const lines = [];
  for (const entry of report.documents) {
    lines.push(`[${entry.status}] ${entry.path}${entry.kind ? ` (${entry.kind})` : ''}: ${entry.problems.length} problem(s), ${entry.citations} evidence reference(s)`);
    for (const item of entry.problems) lines.push(`  ${item.line ? `L${item.line} ` : ''}${item.code}: ${item.message}`);
    for (const item of entry.warnings) lines.push(`  ${item.line ? `L${item.line} ` : ''}warning ${item.code}: ${item.message}`);
  }
  if (report.reason) lines.push(`reason: ${report.reason}`);
  lines.push(`Result: ${report.status}`);
  lines.push('Note: pass checks structure and evidence targets only. It does not prove that claims are true or complete.');
  return lines.join('\n');
}

const HELP = `Usage: node check-evidence.mjs [--root <repo>] [--today YYYY-MM-DD] [--json] <doc.md>...
Checks Code-to-Doc As-Is documents (frontmatter, metadata, sections, mermaid, citations, unknowns).
--today sets the date that reviewBy is compared with (default: the local date).
Exit codes: 0 pass, 1 invalid, 3 uncheckable.`;

function isMainModule() {
  if (!process.argv[1]) return false;
  const invoked = path.resolve(process.argv[1]);
  const self = fileURLToPath(import.meta.url);
  return process.platform === 'win32' ? invoked.toLowerCase() === self.toLowerCase() : invoked === self;
}

if (isMainModule()) {
  const options = parseArguments(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(`${HELP}\n`);
    process.exit(0);
  }
  const report = options.error
    ? { status: 'uncheckable', reason: 'bad-arguments', message: options.error, documents: [] }
    : checkDocuments({ root: options.root, documents: options.documents, contractPath: options.contract, today: options.today });
  process.stdout.write(`${options.json ? JSON.stringify(report, null, 2) : formatText(report)}\n`);
  process.exit(EXIT_CODES[report.status]);
}
