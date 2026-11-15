'use strict';

const VERSION_PATTERN = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*))?$/;

async function reserveVersion({ github, context, core, versionText, previousVersion = null }) {
  core.setOutput('is_new', 'false');
  if (String(context.runAttempt) !== '1') {
    core.info('Re-run: building a version twice is forbidden.');
    return;
  }

  const version = versionText.trim();
  if (!VERSION_PATTERN.test(version)) {
    throw new Error('VERSION must be MAJOR.MINOR.PATCH[-prerelease], without v or +metadata.');
  }
  if (previousVersion !== null && previousVersion.trim() === version) {
    core.info(`Unchanged version ${version}: skipping build and release.`);
    return;
  }

  const repo = context.repo;
  const tag = `v${version}`;
  async function tagExists() {
    try {
      await github.rest.git.getRef({ ...repo, ref: `tags/${tag}` });
      return true;
    } catch (error) {
      if (error.status === 404) return false;
      throw error;
    }
  }

  if (await tagExists()) {
    core.info(`Version ${version} is already reserved: skipping build and release.`);
    return;
  }
  try {
    await github.rest.repos.getReleaseByTag({ ...repo, tag });
    core.info(`Release ${tag} already exists: skipping build and release.`);
    return;
  } catch (error) {
    if (error.status !== 404) throw error;
  }

  // Creating a ref is atomic. Never delete the reservation, even after failure.
  try {
    await github.rest.git.createRef({ ...repo, ref: `refs/tags/${tag}`, sha: context.sha });
  } catch (error) {
    if (error.status === 422 && await tagExists()) {
      core.info(`Another run reserved ${tag}: skipping build and release.`);
      return;
    }
    throw error;
  }

  const { data: release } = await github.rest.repos.createRelease({
    ...repo,
    tag_name: tag,
    target_commitish: context.sha,
    name: `Decaying Aftermath ${version}`,
    body: `Minecraft 26.3 / Fabric.\n\nVersion reserved before build. Commit: ${context.sha}`,
    draft: true,
    prerelease: version.includes('-'),
  });

  core.setOutput('version', version);
  core.setOutput('tag', tag);
  core.setOutput('release_id', String(release.id));
  core.setOutput('is_new', 'true');
  core.info(`Reserved ${tag}; exactly one build attempt is allowed.`);
}

module.exports = { reserveVersion };
