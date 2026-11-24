const tls = require('node:tls');

const DAY_MS = 24 * 60 * 60 * 1000;
const HOSTS = ['status.csc436.com', 'mcp.csc436.com'];

function readCertificate(host) {
  return new Promise((resolve, reject) => {
    const socket = tls.connect({ host, port: 443, servername: host, timeout: 10000 });
    socket.once('secureConnect', () => {
      const expiresAt = Date.parse(socket.getPeerCertificate().valid_to);
      socket.end();
      if (!Number.isFinite(expiresAt)) {
        reject(new Error('Certificate has no valid expiry date'));
      } else {
        resolve(expiresAt);
      }
    });
    socket.once('timeout', () => socket.destroy(new Error('TLS connection timed out')));
    socket.once('error', reject);
  });
}

async function run({ github, context, core, read = readCertificate, now = Date.now() }) {
  const exercise = context.eventName === 'workflow_dispatch' &&
    context.payload.inputs?.exercise === 'true';
  const hosts = exercise ? [HOSTS[0]] : HOSTS;
  const thresholdDays = exercise ? 365 : 14;
  const { owner, repo } = context.repo;
  const runUrl = `${context.serverUrl}/${owner}/${repo}/actions/runs/${context.runId}`;
  let failed = false;
  let exerciseTriggered = false;

  for (const host of hosts) {
    let detail;
    let isExpiry = false;
    try {
      const expiresAt = await read(host);
      if (!Number.isFinite(expiresAt)) throw new Error('Invalid certificate expiry');
      detail = `Certificate expires at ${new Date(expiresAt).toISOString()}.`;
      isExpiry = expiresAt - now <= thresholdDays * DAY_MS;
      if (!isExpiry) {
        core.info(`${host}: ${detail} Outside the ${thresholdDays}-day threshold.`);
        continue;
      }
    } catch (error) {
      detail = `Could not validate the certificate: ${error.message}`;
      failed = true;
    }

    if (exercise && isExpiry) exerciseTriggered = true;
    if (!exercise) failed = true;
    const isExerciseAlert = exercise && isExpiry;
    const title = `${isExerciseAlert ? '[Exercise] ' : ''}TLS certificate alert: ${host}`;
    const issues = await github.paginate(github.rest.issues.listForRepo, {
      owner, repo, state: 'open', per_page: 100
    });
    const existing = issues.find(issue => !issue.pull_request && issue.title === title);
    if (existing) {
      core.warning(`Open alert already exists: ${existing.html_url}. ${detail}`);
      continue;
    }
    const body = [
      isExerciseAlert
        ? '**Exercise:** the threshold was deliberately raised to 365 days. This is not a normal expiry warning.'
        : '**Action required:** inspect the public TLS certificate and automatic renewal.',
      '',
      `Host: \`${host}\``,
      `Checked: ${new Date(now).toISOString()}`,
      detail,
      `Threshold: ${thresholdDays} days`,
      `Workflow run: ${runUrl}`,
      '',
      'See the certificate section of the operations runbook. Close this issue after confirming the appropriate response.'
    ].join('\n');
    const created = await github.rest.issues.create({ owner, repo, title, body });
    core.warning(`Opened alert: ${created.data.html_url}. ${detail}`);
  }
  if (exercise && !exerciseTriggered) {
    core.setFailed('The expiry exercise did not trigger an expiry alert.');
  } else if (failed) {
    core.setFailed('At least one production TLS certificate needs attention.');
  }
}

module.exports = { run };
