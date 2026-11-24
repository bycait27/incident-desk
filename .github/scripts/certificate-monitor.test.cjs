const { test } = require('node:test');
const assert = require('node:assert/strict');
const { run } = require('./certificate-monitor.cjs');

const now = Date.parse('2026-11-24T00:00:00Z');
const day = 24 * 60 * 60 * 1000;

function fixture(exercise = false, existing = []) {
  const created = [];
  const failures = [];
  return {
    created,
    failures,
    options: {
      now,
      context: {
        eventName: exercise ? 'workflow_dispatch' : 'schedule',
        payload: { inputs: { exercise: String(exercise) } },
        repo: { owner: 'owner', repo: 'repo' },
        serverUrl: 'https://github.com',
        runId: 123
      },
      core: { info() {}, warning() {}, setFailed: message => failures.push(message) },
      github: {
        paginate: async () => existing,
        rest: { issues: {
          listForRepo() {},
          create: async issue => {
            created.push(issue);
            return { data: { html_url: 'https://github.com/owner/repo/issues/1' } };
          }
        } }
      }
    }
  };
}

test('scheduled checks alert at the threshold but not before it', async () => {
  const f = fixture();
  f.options.read = async host => now + (host.startsWith('status') ? 14 : 15) * day;
  await run(f.options);
  assert.equal(f.created.length, 1);
  assert.equal(f.created[0].title, 'TLS certificate alert: status.csc436.com');
  assert.equal(f.failures.length, 1);
});

test('exercise is labeled, uses the observed expiry, and does not duplicate an open issue', async () => {
  const f = fixture(true, [{ title: '[Exercise] TLS certificate alert: status.csc436.com', html_url: 'https://github.com/owner/repo/issues/1' }]);
  f.options.read = async () => now + 48 * day;
  await run(f.options);
  assert.equal(f.created.length, 0);
  assert.deepEqual(f.failures, []);
});

test('exercise creates a clearly identified alert for a certificate within 365 days', async () => {
  const f = fixture(true);
  f.options.read = async () => now + 48 * day;
  await run(f.options);
  assert.equal(f.created.length, 1);
  assert.equal(f.created[0].title, '[Exercise] TLS certificate alert: status.csc436.com');
  assert.match(f.created[0].body, /deliberately raised to 365 days/);
  assert.match(f.created[0].body, /2027-01-11T00:00:00.000Z/);
  assert.deepEqual(f.failures, []);
});

test('TLS failures create a real alert even in exercise mode', async () => {
  const f = fixture(true);
  f.options.read = async () => { throw new Error('connection failed'); };
  await run(f.options);
  assert.equal(f.created[0].title, 'TLS certificate alert: status.csc436.com');
  assert.match(f.created[0].body, /connection failed/);
  assert.equal(f.failures.length, 1);
});

test('an exercise that does not reach its threshold fails instead of claiming success', async () => {
  const f = fixture(true);
  f.options.read = async () => now + 366 * day;
  await run(f.options);
  assert.equal(f.created.length, 0);
  assert.equal(f.failures.length, 1);
});
