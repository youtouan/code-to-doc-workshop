#!/usr/bin/env node
// agentStop hook for Code-to-Doc: checks changed As-Is documents after each agent turn.
// Default "notify" mode only prints one progress line and never blocks the session.
// "block" mode (CODE_TO_DOC_HOOK_MODE=block) asks the agent for one more turn when a
// changed document is invalid, but never blocks a turn that a previous block forced, and never
// blocks for problems that only a person can fix (a reviewed document without an owner).
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkDocuments, loadContract } from './check-evidence.mjs';

const scriptPath = fileURLToPath(import.meta.url);
// Problems that only a person can fix (the frontmatter owner). They are reported but never force another agent turn.
const PERSON_ONLY_CODES = new Set(['owner-missing']);

function readPayload() {
  if (process.stdin.isTTY) return {};
  try {
    const text = readFileSync(0, 'utf8');
    const value = text.trim() ? JSON.parse(text) : {};
    return value && typeof value === 'object' ? value : {};
  } catch {
    return {};
  }
}

function git(cwd, args) {
  const env = { ...process.env };
  for (const key of Object.keys(env)) if (key.startsWith('GIT_')) delete env[key];
  const run = spawnSync('git', ['--no-pager', '-C', cwd, ...args], {
    encoding: 'utf8', timeout: 10000, maxBuffer: 8 * 1024 * 1024,
    env: { ...env, GIT_TERMINAL_PROMPT: '0', GIT_OPTIONAL_LOCKS: '0' }
  });
  if (run.error || !Number.isInteger(run.status) || run.status !== 0) return null;
  return run.stdout;
}

export function changedDocuments(root, documentsDirectory, marker) {
  const output = git(root, ['status', '--porcelain=v1', '-z', '--untracked-files=all', '--', documentsDirectory]);
  if (output === null) return null;
  const entries = output.split('\0').filter(Boolean);
  const files = [];
  for (let index = 0; index < entries.length; index += 1) {
    const entry = entries[index];
    const code = entry.slice(0, 2);
    const file = entry.slice(3);
    if (code[0] === 'R' || code[0] === 'C') index += 1;
    if (code.includes('D') || !file.endsWith('.md')) continue;
    try {
      const head = readFileSync(path.join(root, file), 'utf8').slice(0, 4096);
      if (head.includes(`<!-- ${marker}`)) files.push(file);
    } catch {
      // A file that disappears between status and read is simply skipped.
    }
  }
  return files.sort();
}

function summarize(report, maxItems) {
  const items = [];
  for (const entry of report.documents) {
    for (const item of entry.problems) items.push(`${entry.path}${item.line ? ` L${item.line}` : ''} ${item.code}: ${item.message}`);
  }
  const counts = { pass: 0, invalid: 0, uncheckable: 0 };
  for (const entry of report.documents) counts[entry.status] += 1;
  return { items, counts, shown: items.slice(0, maxItems), hidden: Math.max(0, items.length - maxItems) };
}

function main() {
  const payload = readPayload();
  const mode = String(process.env.CODE_TO_DOC_HOOK_MODE ?? 'notify').toLowerCase() === 'block' ? 'block' : 'notify';
  const cwd = typeof payload.cwd === 'string' && payload.cwd ? payload.cwd : process.cwd();
  const top = git(cwd, ['rev-parse', '--show-toplevel']);
  if (top === null) return;
  const root = top.trim();
  const contract = loadContract();
  if (!contract) return;
  const documentsDirectory = process.env.CODE_TO_DOC_DOCS_DIR || 'docs/as-is';
  const documents = changedDocuments(root, documentsDirectory, contract.marker);
  if (!documents || documents.length === 0) return;

  const report = checkDocuments({ root, documents });
  const { counts, shown, hidden } = summarize(report, mode === 'block' ? 20 : 2);
  const checker = path.join(path.dirname(scriptPath), 'check-evidence.mjs');
  const relativeChecker = path.relative(root, checker);
  const shownChecker = relativeChecker.startsWith('..') || path.isAbsolute(relativeChecker)
    ? checker
    : relativeChecker.split(path.sep).join('/');
  const command = `node "${shownChecker}" ${documents.join(' ')}`;
  const forcedAlready = payload.stop_hook_active === true || payload.stopHookActive === true;
  const agentFixable = report.documents.some(entry => entry.problems.some(item => !PERSON_ONLY_CODES.has(item.code)));

  if (mode === 'block' && report.status === 'invalid' && agentFixable && !forcedAlready) {
    const reason = [
      'The Code-to-Doc check found evidence problems in changed As-Is documents.',
      'Fix only these problems. Do not invent evidence; if a claim cannot be supported, move it to "## Unknowns".',
      'Never write the frontmatter (owner, sourceRevision, reviewedAt, reviewBy) or the reviewed status: report owner-missing to a person instead.',
      ...shown.map(item => `- ${item}`),
      hidden > 0 ? `- ...and ${hidden} more. Run: ${command}` : `Re-run: ${command}`
    ].join('\n');
    process.stdout.write(`${JSON.stringify({ decision: 'block', reason })}\n`);
    return;
  }
  const head = `Code-to-Doc check (${report.status}): ${documents.length} changed doc(s), ${counts.pass} pass, ${counts.invalid} invalid, ${counts.uncheckable} uncheckable.`;
  const detail = shown.length > 0 ? ` ${shown.join(' | ')}${hidden > 0 ? ` (+${hidden} more)` : ''}` : '';
  const message = `${head}${detail}`.slice(0, 600);
  process.stdout.write(`${JSON.stringify({ type: 'progress', message })}\n`);
}

try {
  main();
} catch {
  // A notification hook must never break the session.
}
process.exitCode = 0;
