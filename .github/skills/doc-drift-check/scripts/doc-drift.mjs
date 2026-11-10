#!/usr/bin/env node
// Finds As-Is document claims whose cited source lines were touched by a change.
// It reports what to re-read; it never edits documents or source files.
// Exit codes: 0 no cited evidence touched, 2 drift found, 3 uncheckable.
import { spawnSync } from 'node:child_process';
import { lstatSync, readdirSync, readFileSync, realpathSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const EXIT_CODES = Object.freeze({ clean: 0, drift: 2, uncheckable: 3 });
const SOURCE_TOKEN = /\[\[source:([^\]\s#]+)#L([1-9][0-9]*)(?:-L([1-9][0-9]*))?\]\]/g;
const OPEN_FENCE = /^\s{0,3}(`{3,}|~{3,})/;
const FRONTMATTER_START = /^\uFEFF?---[ \t]*$/;
const FRONTMATTER_END = /^(?:---|\.\.\.)[ \t]*$/;
const HUNK = /^@@ -([0-9]+)(?:,([0-9]+))? \+([0-9]+)(?:,([0-9]+))? @@/;
export const IMPACT_ORDER = Object.freeze(['file-removed', 'content-changed', 'citation-changed', 'file-renamed', 'line-shift']);

function unquote(value) {
  return value.startsWith('"') && value.endsWith('"') ? value.slice(1, -1) : value;
}

// Parses `git diff -U0 -M` output. Entries are keyed by the pre-change path,
// because citations point at the revision the document was written against.
export function parseUnifiedDiff(text) {
  const entries = new Map();
  let current = null;
  const finish = () => {
    if (current?.oldPath) entries.set(current.oldPath, current);
    current = null;
  };
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith('diff --git ')) {
      finish();
      const match = /^diff --git a\/(.+) b\/(.+)$/.exec(line);
      current = { oldPath: match ? match[1] : null, newPath: match ? match[2] : null, status: 'modified', hunks: [] };
      continue;
    }
    if (!current) continue;
    if (line.startsWith('deleted file mode')) current.status = 'deleted';
    else if (line.startsWith('new file mode')) current.status = 'added';
    else if (line.startsWith('rename from ')) {
      current.oldPath = unquote(line.slice('rename from '.length));
      current.status = 'renamed';
    } else if (line.startsWith('rename to ')) current.newPath = unquote(line.slice('rename to '.length));
    else if (line.startsWith('--- ')) {
      const value = unquote(line.slice(4));
      if (value !== '/dev/null') current.oldPath = value.replace(/^a\//, '');
    } else if (line.startsWith('+++ ')) {
      const value = unquote(line.slice(4));
      if (value !== '/dev/null') current.newPath = value.replace(/^b\//, '');
    } else {
      const hunk = HUNK.exec(line);
      if (hunk) {
        current.hunks.push({
          oldStart: Number(hunk[1]),
          oldCount: hunk[2] === undefined ? 1 : Number(hunk[2]),
          newStart: Number(hunk[3]),
          newCount: hunk[4] === undefined ? 1 : Number(hunk[4])
        });
      }
    }
  }
  finish();
  return entries;
}

// Classifies how a change affects a cited range [start, end] of the old file.
export function classifyImpact(entry, start, end) {
  if (!entry || entry.status === 'added') return null;
  if (entry.status === 'deleted') return 'file-removed';
  let shifted = false;
  for (const hunk of entry.hunks) {
    if (hunk.oldCount === 0) {
      // Pure insertion after old line `oldStart`.
      if (hunk.oldStart >= start && hunk.oldStart < end) return 'content-changed';
      if (hunk.oldStart < start && hunk.newCount > 0) shifted = true;
      continue;
    }
    const first = hunk.oldStart;
    const last = hunk.oldStart + hunk.oldCount - 1;
    if (first <= end && last >= start) return 'content-changed';
    if (last < start && hunk.newCount !== hunk.oldCount) shifted = true;
  }
  if (entry.status === 'renamed') return 'file-renamed';
  return shifted ? 'line-shift' : null;
}

// Lists Markdown files under a working-tree folder. Errors are returned, never skipped:
// a missing folder or an unreadable file must make the result uncheckable, not clean.
function listMarkdown(root, directory) {
  const files = [];
  const top = directory.replace(/\\/g, '/').replace(/\/+$/, '');
  try {
    if (!lstatSync(path.join(root, ...top.split('/'))).isDirectory()) return { error: `${top} is not a directory.` };
  } catch {
    return { error: `${top} does not exist.` };
  }
  const walk = relative => {
    const absolute = path.join(root, ...relative.split('/'));
    const stat = lstatSync(absolute);
    if (stat.isSymbolicLink()) return;
    if (stat.isDirectory()) {
      for (const name of readdirSync(absolute).sort()) walk(`${relative}/${name}`);
    } else if (stat.isFile() && relative.endsWith('.md')) {
      files.push(relative);
    }
  };
  try {
    walk(top);
  } catch (error) {
    return { error: `${top} cannot be listed: ${error.message}` };
  }
  return { files };
}

// Number of leading lines taken by a closed YAML frontmatter block (people's lifecycle fields such as
// owner and reviewBy). Those lines are never claims. An unclosed block is not skipped.
function frontmatterLength(lines) {
  if (lines.length === 0 || !FRONTMATTER_START.test(lines[0])) return 0;
  const end = lines.findIndex((line, index) => index > 0 && FRONTMATTER_END.test(line));
  return end < 0 ? 0 : end + 1;
}

export function citationsInText(document, text) {
  const citations = [];
  let fence = null;
  const lines = `${text}`.split(/\r\n|\n|\r/);
  const skipped = frontmatterLength(lines);
  lines.forEach((line, index) => {
    if (index < skipped) return;
    const open = OPEN_FENCE.exec(line);
    if (fence) {
      if (open && open[1][0] === fence[0] && open[1].length >= fence.length && line.trim() === open[1]) fence = null;
      return;
    }
    if (open) {
      fence = open[1];
      return;
    }
    for (const match of line.matchAll(SOURCE_TOKEN)) {
      const start = Number(match[2]);
      citations.push({
        document, line: index + 1, path: match[1], start, end: match[3] ? Number(match[3]) : start,
        token: match[0], excerpt: line.trim().slice(0, 160)
      });
    }
  });
  return citations;
}

// Reads the Markdown documents of a working-tree folder: { documents: Map(path -> text) } or { error }.
export function readWorkingDocuments(root, directory) {
  const listed = listMarkdown(root, directory);
  if (listed.error) return listed;
  const documents = new Map();
  for (const document of listed.files) {
    try {
      documents.set(document, readFileSync(path.join(root, ...document.split('/')), 'utf8'));
    } catch (error) {
      return { error: `${document} cannot be read: ${error.message}` };
    }
  }
  return { documents };
}

// Reads the Markdown documents of a folder at a commit. `git(root, args)` may return a value or a promise.
async function readCommitDocuments(git, root, commit, directory) {
  const top = directory.replace(/\\/g, '/').replace(/\/+$/, '');
  const listed = await git(root, ['ls-tree', '-r', '-z', commit, '--', top]);
  if (listed.error) return { error: `git ls-tree failed at ${commit}: ${listed.error}` };
  const documents = new Map();
  for (const record of listed.stdout.split('\0').filter(Boolean)) {
    const tab = record.indexOf('\t');
    const [mode, type] = record.slice(0, tab).split(' ');
    const file = record.slice(tab + 1);
    if (type !== 'blob' || mode === '120000' || !file.endsWith('.md')) continue;
    const shown = await git(root, ['show', `${commit}:./${file}`]);
    if (shown.error) return { error: `${file} cannot be read at ${commit}: ${shown.error}` };
    documents.set(file, shown.stdout);
  }
  return { documents };
}

function collect(documents) {
  return [...documents].flatMap(([document, text]) => citationsInText(document, text));
}

export function findCitations(root, directory) {
  const read = readWorkingDocuments(root, directory);
  return read.error ? [] : collect(read.documents);
}

const citationKey = citation => `${citation.path}#${citation.start}-${citation.end}`;

// Judges the citations of the base side. A change cannot hide drift by rewriting the document in
// the same change: a base citation whose code changed is drift even when the head document no
// longer cites it, and a base citation that is missing at head (deleted or retargeted) is drift too.
export function judgeCitations({ entries, baseDocuments, headDocuments = null }) {
  const findings = [];
  const remaining = new Map();
  if (headDocuments) {
    for (const citation of collect(headDocuments)) remaining.set(citationKey(citation), (remaining.get(citationKey(citation)) ?? 0) + 1);
  }
  for (const citation of collect(baseDocuments)) {
    const entry = entries.get(citation.path);
    let impact = classifyImpact(entry, citation.start, citation.end);
    let kept = true;
    if (headDocuments) {
      const count = remaining.get(citationKey(citation)) ?? 0;
      if (count > 0) remaining.set(citationKey(citation), count - 1);
      else kept = false;
    }
    if (!impact && !kept) impact = 'citation-changed';
    if (impact) findings.push({ ...citation, impact, renamedTo: entry?.status === 'renamed' ? entry.newPath : undefined });
  }
  findings.sort((a, b) => a.document.localeCompare(b.document) || a.line - b.line);
  return findings;
}

function noDocuments(directory) {
  return { status: 'uncheckable', reason: `No As-Is documents (*.md) were found in ${directory}.`, findings: [] };
}

// Diff-file mode: the documents on disk are taken as the base side.
export function detectDrift({ diffText, root, documentsDirectory }) {
  const read = readWorkingDocuments(root, documentsDirectory);
  if (read.error) return { status: 'uncheckable', reason: read.error, findings: [] };
  if (read.documents.size === 0) return noDocuments(documentsDirectory);
  const entries = parseUnifiedDiff(diffText);
  const findings = judgeCitations({ entries, baseDocuments: read.documents });
  return { status: findings.length > 0 ? 'drift' : 'clean', findings, changedFiles: [...entries.keys()] };
}

// Git mode. Range mode compares the merge base of base and head with head (like `base...head`);
// working-tree mode compares the base commit with the files on disk.
export async function driftBetween({ root, base, head = 'HEAD', workingTree = false, documentsDirectory = 'docs/as-is', git = runGit }) {
  let baseCommit;
  if (workingTree) {
    const verified = await git(root, ['rev-parse', '--verify', '--quiet', `${base}^{commit}`]);
    if (verified.error || !verified.stdout.trim()) return { status: 'uncheckable', reason: `${base} is not a commit.`, findings: [] };
    baseCommit = verified.stdout.trim();
  } else {
    const merged = await git(root, ['merge-base', base, head]);
    if (merged.error || !merged.stdout.trim()) return { status: 'uncheckable', reason: `git merge-base failed: ${merged.error ?? 'no common ancestor'}`, findings: [] };
    baseCommit = merged.stdout.trim();
  }
  const range = workingTree ? [baseCommit] : [`${base}...${head}`];
  const diff = await git(root, ['diff', '--no-color', '--no-ext-diff', '-U0', '-M', ...range, '--']);
  if (diff.error) return { status: 'uncheckable', reason: `git diff failed: ${diff.error}`, findings: [] };
  const baseRead = await readCommitDocuments(git, root, baseCommit, documentsDirectory);
  if (baseRead.error) return { status: 'uncheckable', reason: baseRead.error, findings: [] };
  const headRead = workingTree ? readWorkingDocuments(root, documentsDirectory) : await readCommitDocuments(git, root, head, documentsDirectory);
  // A folder deleted by the change is judged from the base side alone (every citation is then missing).
  if (headRead.error && !(workingTree && baseRead.documents.size > 0 && /does not exist/.test(headRead.error))) {
    return { status: 'uncheckable', reason: headRead.error, findings: [] };
  }
  const headDocuments = headRead.documents ?? new Map();
  if (baseRead.documents.size === 0 && headDocuments.size === 0) return noDocuments(documentsDirectory);
  const entries = parseUnifiedDiff(diff.stdout);
  const findings = judgeCitations({ entries, baseDocuments: baseRead.documents, headDocuments });
  return { status: findings.length > 0 ? 'drift' : 'clean', findings, changedFiles: [...entries.keys()] };
}
function runGit(root, args) {
  const env = { ...process.env };
  for (const key of Object.keys(env)) if (key.startsWith('GIT_')) delete env[key];
  const run = spawnSync('git', ['--no-pager', '-C', root, ...args], {
    encoding: 'utf8', timeout: 30000, maxBuffer: 64 * 1024 * 1024,
    env: { ...env, GIT_TERMINAL_PROMPT: '0', GIT_OPTIONAL_LOCKS: '0' }
  });
  if (run.error || !Number.isInteger(run.status) || run.status !== 0) {
    return { error: (run.stderr || run.error?.message || `exit ${run.status}`).trim() };
  }
  return { stdout: run.stdout };
}

function parseArguments(argv) {
  const options = { root: process.cwd(), base: null, head: 'HEAD', workingTree: false, docs: 'docs/as-is', diffFile: null, json: false, help: false, error: null };
  const valued = { '--root': 'root', '--base': 'base', '--head': 'head', '--docs': 'docs', '--diff-file': 'diffFile' };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--json') options.json = true;
    else if (argument === '--working-tree') options.workingTree = true;
    else if (argument === '--help' || argument === '-h') options.help = true;
    else if (Object.hasOwn(valued, argument)) {
      const value = argv[index + 1];
      if (value === undefined || value.startsWith('--')) options.error = `${argument} needs a value.`;
      else options[valued[argument]] = value;
      index += 1;
    } else options.error = `Unknown argument ${argument}.`;
  }
  if (!options.error && !options.diffFile && !options.base) options.error = 'Pass --base <ref> (or --diff-file <path>).';
  return options;
}

function formatMarkdown(report, options) {
  const lines = ['## Code-to-Doc drift report', ''];
  if (options.diffFile) lines.push(`Diff: \`${options.diffFile}\``);
  else lines.push(`Range: \`${options.base}\` → ${options.workingTree ? 'working tree' : `\`${options.head}\``}`);
  lines.push('');
  if (report.findings.length === 0) {
    lines.push('No cited evidence in the As-Is documents was touched by this change.');
    return lines.join('\n');
  }
  lines.push(`${report.findings.length} cited claim(s) may be outdated. Re-read the cited code before changing any document.`, '');
  lines.push('| Document | Line | Evidence | Impact |', '|---|---|---|---|');
  for (const finding of report.findings) {
    const target = finding.renamedTo ? `${finding.token} → \`${finding.renamedTo}\`` : finding.token;
    lines.push(`| \`${finding.document}\` | ${finding.line} | ${target.replace(/\|/g, '\\|')} | ${finding.impact} |`);
  }
  lines.push('', `Impact order: ${IMPACT_ORDER.join(' > ')}. citation-changed means that the change deleted or retargeted a citation of the base side.`,
    'Next: update the claim and its citation, or move the claim to "## Unknowns". Never edit cited code to match a document.');
  return lines.join('\n');
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
    process.stdout.write('Usage: node doc-drift.mjs --base <ref> [--head <ref> | --working-tree] [--docs docs/as-is] [--root <repo>] [--json]\n       node doc-drift.mjs --diff-file <unified.diff> [--docs docs/as-is] [--root <repo>] [--json]\n');
    process.exit(0);
  }
  let report;
  if (options.error) {
    report = { status: 'uncheckable', reason: options.error, findings: [] };
  } else {
    let root;
    try {
      root = realpathSync.native(options.root);
    } catch {
      root = null;
    }
    if (!root) report = { status: 'uncheckable', reason: 'The --root directory does not exist.', findings: [] };
    else if (options.diffFile) {
      let diffText = null;
      try {
        diffText = readFileSync(options.diffFile, 'utf8');
      } catch {
        report = { status: 'uncheckable', reason: 'The --diff-file cannot be read.', findings: [] };
      }
      if (diffText !== null) report = detectDrift({ diffText, root, documentsDirectory: options.docs });
    } else {
      report = await driftBetween({ root, base: options.base, head: options.head, workingTree: options.workingTree, documentsDirectory: options.docs });
    }
  }
  if (options.json) process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  else if (report.status === 'uncheckable') process.stdout.write(`Result: uncheckable (${report.reason})\n`);
  else process.stdout.write(`${formatMarkdown(report, options)}\n`);
  process.exit(report.status === 'drift' ? EXIT_CODES.drift : report.status === 'clean' ? EXIT_CODES.clean : EXIT_CODES.uncheckable);
}
