// Characterization test template (Node.js 22 or later, no dependencies).
// Pins what the legacy system does today. Run it against the legacy system first.
//
// How to run:  LEGACY_BASE_URL=http://127.0.0.1:8080/<context> node --test "tests/characterization/**/*.test.mjs"
// How to start the legacy system: {{START_COMMAND_AND_DATA_RESET}}
import { test } from 'node:test';
import assert from 'node:assert/strict';

const BASE_URL = process.env.LEGACY_BASE_URL ?? 'http://127.0.0.1:8080';

// Replace only values that the current contract does not promise. Explain each rule.
function normalize(text) {
  return text
    // Timestamps differ on every run.
    .replace(/\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?/g, '<timestamp>');
}

test('CHAR-EXAMPLE-001 {{WHAT_THIS_TEST_PINS}}', async () => {
  const response = await fetch(`${BASE_URL}/{{PATH}}`, { redirect: 'manual' });
  // Write the values the legacy system returns today, not the values you expect it should return.
  assert.equal(response.status, 200);
  const body = normalize(await response.text());
  assert.ok(body.includes('{{EXACT_TEXT_SHOWN_TODAY}}'));
});
