'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { reserveVersion } = require('./reserve-version.cjs');

function fixture({ tags = [], releases = [], fail = null } = {}) {
  const state = { tags: new Set(tags), releases: new Set(releases), buildsAllowed: 0, creates: 0 };
  const missing = () => Object.assign(new Error('Not found'), { status: 404 });
  const github = { rest: {
    git: {
      async getRef({ ref }) {
        if (fail === 'getRef') throw Object.assign(new Error('API unavailable'), { status: 503 });
        if (!state.tags.has(ref.replace('tags/', ''))) throw missing();
        return { data: {} };
      },
      async createRef({ ref }) {
        const tag = ref.replace('refs/tags/', '');
        if (fail === 'createRef') throw Object.assign(new Error('Invalid SHA'), { status: 422 });
        if (state.tags.has(tag)) throw Object.assign(new Error('Ref already exists'), { status: 422 });
        state.tags.add(tag);
        state.creates++;
        return { data: {} };
      },
    },
    repos: {
      async getReleaseByTag({ tag }) {
        if (!state.releases.has(tag)) throw missing();
        return { data: { id: 1 } };
      },
      async createRelease({ tag_name, draft }) {
        assert.equal(draft, true);
        if (fail === 'createRelease') throw Object.assign(new Error('Release API unavailable'), { status: 503 });
        state.releases.add(tag_name);
        return { data: { id: 1 } };
      },
    },
  } };

  async function run({ version = '0.1.0\n', previousVersion = null, attempt = 1 } = {}) {
    const outputs = {};
    const messages = [];
    await reserveVersion({
      github,
      context: { repo: { owner: 'fixture', repo: 'fixture' }, sha: 'a'.repeat(40), runAttempt: attempt },
      core: { setOutput: (key, value) => { outputs[key] = value; }, info: message => messages.push(message) },
      versionText: version,
      previousVersion,
    });
    if (outputs.is_new === 'true') state.buildsAllowed++;
    return { outputs, messages };
  }
  return { state, run };
}

test('new version reserves exactly one tag and draft, enabling build', async () => {
  const { state, run } = fixture();
  const { outputs } = await run();
  assert.deepEqual(outputs, { is_new: 'true', version: '0.1.0', tag: 'v0.1.0', release_id: '1' });
  assert.equal(state.creates, 1);
  assert.equal(state.buildsAllowed, 1);
  assert.ok(state.releases.has('v0.1.0'));
});

test('unchanged version does not reserve, build, or publish', async () => {
  const { state, run } = fixture();
  assert.equal((await run({ previousVersion: '0.1.0\n' })).outputs.is_new, 'false');
  assert.equal(state.creates, 0);
});

test('whitespace-only change does not allow another build', async () => {
  const { state, run } = fixture();
  assert.equal((await run({ version: ' 0.1.0\r\n', previousVersion: '0.1.0\n' })).outputs.is_new, 'false');
  assert.equal(state.buildsAllowed, 0);
});

test('already used version is skipped even if previous version differs', async () => {
  const { state, run } = fixture({ tags: ['v0.1.0'] });
  assert.equal((await run({ previousVersion: '0.2.0' })).outputs.is_new, 'false');
  assert.equal(state.buildsAllowed, 0);
  assert.equal(state.creates, 0);
});

test('existing release also blocks build when its tag is missing', async () => {
  const { state, run } = fixture({ releases: ['v0.1.0'] });
  assert.equal((await run()).outputs.is_new, 'false');
  assert.equal(state.creates, 0);
});

test('re-running all or failed jobs never permits another build', async () => {
  const { state, run } = fixture();
  assert.equal((await run({ attempt: 2 })).outputs.is_new, 'false');
  assert.equal((await run({ attempt: '3' })).outputs.is_new, 'false');
  assert.equal(state.creates, 0);
});

test('parallel runs for one version permit only one build', async () => {
  const { state, run } = fixture();
  const results = await Promise.all([run(), run(), run(), run()]);
  assert.equal(results.filter(result => result.outputs.is_new === 'true').length, 1);
  assert.equal(state.creates, 1);
  assert.equal(state.buildsAllowed, 1);
});

test('a second independent push for the same version is blocked', async () => {
  const { state, run } = fixture();
  await run();
  assert.equal((await run()).outputs.is_new, 'false');
  assert.equal(state.buildsAllowed, 1);
});

test('failed release creation keeps tag reserved permanently', async () => {
  const { state, run } = fixture({ fail: 'createRelease' });
  await assert.rejects(run(), /Release API unavailable/);
  assert.ok(state.tags.has('v0.1.0'));
  assert.equal((await run()).outputs.is_new, 'false');
  assert.equal(state.buildsAllowed, 0);
});

test('API errors fail closed instead of enabling build', async () => {
  const { state, run } = fixture({ fail: 'getRef' });
  await assert.rejects(run(), /API unavailable/);
  assert.equal(state.buildsAllowed, 0);
});

test('a non-collision validation error is not silently ignored', async () => {
  const { state, run } = fixture({ fail: 'createRef' });
  await assert.rejects(run(), /Invalid SHA/);
  assert.equal(state.buildsAllowed, 0);
});

test('invalid versions are rejected without reserving a number', async () => {
  for (const version of ['v0.1.0', '01.2.3', '1.2', '1.2.3+same', '1.2.3-01', '', '1.2.3\ninjected']) {
    const { state, run } = fixture();
    await assert.rejects(run({ version }), /VERSION must be/);
    assert.equal(state.creates, 0);
  }
});

test('valid prerelease creates a separate version', async () => {
  const { run } = fixture();
  assert.equal((await run({ version: '0.2.0-alpha.1' })).outputs.tag, 'v0.2.0-alpha.1');
});

test('workflow filters only main/VERSION and gates failed-job reruns', () => {
  const workflow = fs.readFileSync('.github/workflows/release.yml', 'utf8');
  assert.match(workflow, /branches: \[main\]/);
  assert.match(workflow, /paths: \[VERSION\]/);
  assert.match(workflow, /if: needs\.reserve\.outputs\.is_new == 'true' && github\.run_attempt == '1'/);
  assert.match(workflow, /runAttempt: process\.env\.GITHUB_RUN_ATTEMPT/);
  assert.doesNotMatch(workflow, /workflow_dispatch|pull_request|schedule:/);
});

test('missing run attempt fails visibly, not a misleading successful skip', async () => {
  await assert.rejects(reserveVersion({
    context: {},
    core: { setOutput() {} },
    versionText: '0.1.0',
  }), /Missing or invalid GITHUB_RUN_ATTEMPT/);
});
