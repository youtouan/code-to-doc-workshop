#!/usr/bin/env node
// Lists changes to the fields that only people may decide, between two revisions or two folders:
// finding decisions (adopted/rejected, Reason, Decided by), Unknowns marked resolved or accepted,
// ADR Status and the Decision / Rationale / Decided by / Date sections, documents marked reviewed,
// and the lifecycle fields of the YAML frontmatter (owner, sourceRevision, reviewedAt, reviewBy).
// It does not block anything by itself. CI uses the list so that CODEOWNERS, required reviews and
// rulesets can gate the change; the Code to Doc Board's "human only" buttons are not a security boundary.
// Exit codes: 0 no human field changed, 2 human fields changed, 3 uncheckable.
import { spawnSync } from 'node:child_process';
import { lstatSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { isSet, LIFECYCLE_FIELDS, readFrontmatter } from './check-evidence.mjs';

export const EXIT_CODES = Object.freeze({ clean: 0, changed: 2, uncheckable: 3 });
const OPEN_FENCE = /^\s{0,3}(`{3,}|~{3,})/;
const HUMAN_SECTIONS = ['Decision', 'Rationale', 'Decided by / Date'];
const SAFE_REF = /^(?!-)[A-Za-z0-9_./~^@{}-]{1,200}$/;

class Uncheckable extends Error {}

function lines(text) {
  return text.replace(/^\uFEFF/, '').split(/\r?\n/);
}

// Lines outside fenced code blocks, with their index, from `start` on.
function outsideFences(all, start = 0) {
  const result = [];
  let fence = null;
  all.forEach((line, index) => {
    if (index < start) return;
    const open = OPEN_FENCE.exec(line);
    if (fence) {
      if (open && open[1][0] === fence[0] && open[1].length >= fence.length && line.trim() === open[1]) fence = null;
      return;
    }
    if (open) {
      fence = open[1];
      return;
    }
    result.push({ line, index });
  });
  return result;
}

const cells = line => {
  const body = line.trim().replace(/^\|/, '').replace(/\|\s*$/, '');
  return body.split(/(?<!\\)\|/).map(cell => cell.trim());
};
const isTableRow = line => /^\s*\|.*\|\s*$/.test(line);
const isSeparator = line => /^\s*\|(\s*:?-{3,}:?\s*\|)+\s*$/.test(line);
const normalize = value => String(value ?? '').replace(/\s+/g, ' ').trim();
const withoutComments = text => text.replace(/<!--[\s\S]*?-->/g, '');

function metadataStatus(text) {
  const match = /<!--\s*code-to-doc-artifact\s*([\s\S]*?)-->/.exec(text);
  if (!match) return null;
  try {
    const status = JSON.parse(match[1])?.status;
    return typeof status === 'string' ? status : null;
  } catch {
    return null;
  }
}

function findingRows(visible, fields) {
  for (let index = 0; index + 1 < visible.length; index += 1) {
    const { line } = visible[index];
    if (!isTableRow(line) || !isSeparator(visible[index + 1].line)) continue;
    const header = cells(line).map(cell => cell.toLowerCase());
    const column = name => header.indexOf(name);
    const [id, decision, reason, decidedBy] = [column('id'), column('decision'), column('reason'), column('decided by')];
    if (id === -1 || decision === -1) continue;
    const seen = new Map();
    for (let row = index + 2; row < visible.length && isTableRow(visible[row].line); row += 1) {
      const values = cells(visible[row].line);
      const rawId = normalize(values[id]);
      if (!rawId) continue;
      const count = (seen.get(rawId) ?? 0) + 1;
      seen.set(rawId, count);
      const key = count === 1 ? rawId : `${rawId}#${count}`;
      const decided = normalize(values[decision]).replace(/`/g, '');
      // pending (or blank) is what agents write; anything else is a human decision.
      if (decided && decided.toLowerCase() !== 'pending') fields.set(`finding:${key}:decision`, decided);
      if (reason !== -1 && normalize(values[reason])) fields.set(`finding:${key}:reason`, normalize(values[reason]));
      if (decidedBy !== -1 && normalize(values[decidedBy])) fields.set(`finding:${key}:decided-by`, normalize(values[decidedBy]));
    }
    return;
  }
}

function unknownTags(visible, fields) {
  const seen = new Map();
  for (const { line } of visible) {
    const match = /^\s*(?:[-*]\s+|\|\s*)(U-[0-9]+)\b/.exec(line);
    if (!match) continue;
    const count = (seen.get(match[1]) ?? 0) + 1;
    seen.set(match[1], count);
    const status = /\[status:\s*([a-z]+)\s*\]/i.exec(line)?.[1]?.toLowerCase();
    if (status === 'resolved' || status === 'accepted') fields.set(`unknown:${count === 1 ? match[1] : `${match[1]}#${count}`}:status`, status);
  }
}

function adrFields(all, visible, fields) {
  const status = visible.find(({ line }) => /^\s*-\s*Status:/i.test(line));
  const headings = visible.filter(({ line }) => /^##\s+/.test(line));
  if (!status || !headings.some(({ line }) => /^##\s+Decision\s*$/i.test(line))) return;
  const value = normalize(status.line.replace(/^\s*-\s*Status:/i, ''));
  if (value && value.toLowerCase() !== 'proposed') fields.set('adr:status', value);
  for (const name of HUMAN_SECTIONS) {
    const start = headings.find(({ line }) => normalize(line.replace(/^##\s+/, '')).toLowerCase() === name.toLowerCase());
    if (!start) continue;
    const next = headings.find(({ index }) => index > start.index);
    const body = normalize(withoutComments(all.slice(start.index + 1, next ? next.index : all.length).join('\n')));
    if (body) fields.set(`adr:${name.toLowerCase()}`, body);
  }
}

// Human-owned fields of one Markdown file, as a Map from field key to value. Absent means "not decided".
// Frontmatter lines are read only as lifecycle fields, never as findings, unknowns or ADR lines.
export function humanFieldsOf(text) {
  const all = lines(text);
  const frontmatter = readFrontmatter(all);
  const visible = outsideFences(all, frontmatter.closed ? frontmatter.end + 1 : 0);
  const fields = new Map();
  for (const name of LIFECYCLE_FIELDS) {
    const value = frontmatter.fields[name]?.value;
    if (isSet(value)) fields.set(`document:${name}`, value);
  }
  if (metadataStatus(text) === 'reviewed') fields.set('document:status', 'reviewed');
  findingRows(visible, fields);
  unknownTags(visible, fields);
  adrFields(all, visible, fields);
  return fields;
}

// Field-by-field differences between two { path: text } maps.
export function diffHumanFields(baseFiles, headFiles) {
  const changes = [];
  const paths = [...new Set([...baseFiles.keys(), ...headFiles.keys()])].sort();
  for (const file of paths) {
    const before = baseFiles.has(file) ? humanFieldsOf(baseFiles.get(file)) : new Map();
    const after = headFiles.has(file) ? humanFieldsOf(headFiles.get(file)) : new Map();
    for (const key of [...new Set([...before.keys(), ...after.keys()])].sort()) {
      if (before.get(key) === after.get(key)) continue;
      const [kind, id, field] = key.split(':');
      changes.push({
        path: file, kind, id: field === undefined ? null : id, field: field ?? id,
        before: before.get(key) ?? null, after: after.get(key) ?? null
      });
    }
  }
  return changes;
}

function runGit(root, args) {
  const result = spawnSync('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, windowsHide: true });
  if (result.error || result.status !== 0) {
    throw new Uncheckable(`git ${args.join(' ')} failed: ${(result.error?.message ?? result.stderr ?? '').trim()}`);
  }
  return result.stdout;
}

function readFromRef(root, ref, docs) {
  if (!SAFE_REF.test(ref)) throw new Uncheckable(`Unsafe Git ref: ${JSON.stringify(ref)}.`);
  runGit(root, ['rev-parse', '--verify', '--quiet', `${ref}^{commit}`]);
  const names = runGit(root, ['-c', 'core.quotePath=false', 'ls-tree', '-r', '-z', '--name-only', ref, '--', docs])
    .split('\0').filter(name => name.toLowerCase().endsWith('.md'));
  const files = new Map();
  for (const name of names) files.set(name, runGit(root, ['show', `${ref}:${name}`]));
  return files;
}

function readFromDirectory(directory, docs) {
  const files = new Map();
  const start = path.join(directory, ...docs.split('/'));
  let stat;
  try {
    stat = lstatSync(start);
  } catch (error) {
    if (error.code === 'ENOENT') return files;
    throw new Uncheckable(`Cannot read ${start} (${error.code}).`);
  }
  if (!stat.isDirectory()) throw new Uncheckable(`${start} is not a directory.`);
  const walk = relative => {
    let entries;
    try {
      entries = readdirSync(path.join(directory, ...relative.split('/')), { withFileTypes: true });
    } catch (error) {
      throw new Uncheckable(`Cannot list ${relative} (${error.code}).`);
    }
    for (const entry of entries) {
      const child = `${relative}/${entry.name}`;
      if (entry.isDirectory()) walk(child);
      else if (entry.isFile() && entry.name.toLowerCase().endsWith('.md')) {
        try {
          files.set(child, readFileSync(path.join(directory, ...child.split('/')), 'utf8'));
        } catch (error) {
          throw new Uncheckable(`Cannot read ${child} (${error.code}).`);
        }
      }
    }
  };
  walk(docs);
  return files;
}

// Each side is either a Git ref (in root) or a directory. Both sides without any Markdown under docs is uncheckable.
export function checkHumanFields({ root = process.cwd(), base, head, baseDir, headDir, docs = 'docs' } = {}) {
  try {
    if ((base === undefined) === (baseDir === undefined)) throw new Uncheckable('Give exactly one of --base <ref> or --base-dir <folder>.');
    if (head !== undefined && headDir !== undefined) throw new Uncheckable('Give at most one of --head <ref> or --head-dir <folder>.');
    if (typeof docs !== 'string' || docs === '' || docs.includes('..') || path.isAbsolute(docs)) throw new Uncheckable(`Unsafe docs folder: ${JSON.stringify(docs)}.`);
    const read = (ref, directory) => (directory !== undefined ? readFromDirectory(path.resolve(directory), docs) : readFromRef(root, ref, docs));
    const baseFiles = read(base, baseDir);
    const headFiles = read(headDir === undefined ? head ?? 'HEAD' : undefined, headDir);
    if (baseFiles.size === 0 && headFiles.size === 0) throw new Uncheckable(`No Markdown under ${docs}/ on either side.`);
    const changes = diffHumanFields(baseFiles, headFiles);
    return { status: changes.length > 0 ? 'changed' : 'clean', scanned: { base: baseFiles.size, head: headFiles.size }, changes };
  } catch (error) {
    if (error instanceof Uncheckable) return { status: 'uncheckable', reason: error.message, changes: [] };
    throw error;
  }
}

function parseArguments(argv) {
  const options = {};
  const names = { '--root': 'root', '--base': 'base', '--head': 'head', '--base-dir': 'baseDir', '--head-dir': 'headDir', '--docs': 'docs' };
  for (let index = 0; index < argv.length; index += 1) {
    const name = names[argv[index]];
    if (!name || index + 1 >= argv.length) throw new Uncheckable(`Unknown or incomplete option: ${argv[index]}.`);
    options[name] = argv[index += 1];
  }
  return options;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  let result;
  try {
    result = checkHumanFields(parseArguments(process.argv.slice(2)));
  } catch (error) {
    result = { status: 'uncheckable', reason: error.message, changes: [] };
  }
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  if (result.status === 'changed') {
    process.stderr.write('Human-owned fields changed. Route this change to a person (CODEOWNERS / required review):\n');
    for (const change of result.changes) {
      process.stderr.write(`  ${change.path} ${change.kind}${change.id ? ` ${change.id}` : ''} ${change.field}: ${JSON.stringify(change.before)} -> ${JSON.stringify(change.after)}\n`);
    }
  }
  process.exitCode = EXIT_CODES[result.status];
}
