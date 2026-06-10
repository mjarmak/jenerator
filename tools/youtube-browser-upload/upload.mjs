import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { chromium } from 'playwright';

const args = parseArgs(process.argv.slice(2));
const video = path.resolve(required(args.video, '--video'));
const title = required(args.title, '--title');
const description = args['description-file']
  ? await readFile(path.resolve(args['description-file']), 'utf8')
  : '';
const tags = args.tags || '';
const privacy = (args.privacy || 'private').toLowerCase();
const madeForKids = args['made-for-kids'] === 'true';
const containsSyntheticMedia = args['contains-synthetic-media'] === 'true';
const userDataDir = args['user-data-dir'] ? path.resolve(args['user-data-dir']) : path.resolve('.youtube-profile');
const manualReviewSeconds = number(args['manual-review-seconds'], 90);

const context = await chromium.launchPersistentContext(userDataDir, {
  channel: args.channel || undefined,
  headless: args.headless === 'true',
  viewport: { width: 1440, height: 960 }
});

const page = context.pages()[0] || await context.newPage();
await page.goto('https://studio.youtube.com', { waitUntil: 'domcontentloaded', timeout: 120_000 });

const createButton = page.getByLabel(/create/i).or(page.getByText(/^create$/i));
await createButton.first().click({ timeout: 120_000 });
await page.getByText(/upload videos/i).first().click({ timeout: 60_000 });

const fileChooserPromise = page.waitForEvent('filechooser');
await page.getByText(/select files/i).first().click({ timeout: 60_000 });
const chooser = await fileChooserPromise;
await chooser.setFiles(video);

await fillTextbox(page, /title/i, title);
await fillTextbox(page, /description/i, description);

if (tags) {
  const moreOptions = page.getByText(/show more/i).first();
  await moreOptions.click({ timeout: 30_000 }).catch(() => undefined);
  await fillTextbox(page, /tags/i, tags).catch(() => undefined);
}

await page.getByText(madeForKids ? /yes,.*made for kids/i : /no,.*not made for kids/i).first()
  .click({ timeout: 60_000 })
  .catch(() => undefined);
await setSyntheticMediaDisclosure(page, containsSyntheticMedia);

await next(page);
await next(page);
await next(page);

if (privacy === 'public') {
  await page.getByText(/^public$/i).first().click({ timeout: 60_000 }).catch(() => undefined);
} else if (privacy === 'unlisted') {
  await page.getByText(/^unlisted$/i).first().click({ timeout: 60_000 }).catch(() => undefined);
} else {
  await page.getByText(/^private$/i).first().click({ timeout: 60_000 }).catch(() => undefined);
}

if (manualReviewSeconds > 0) {
  await page.waitForTimeout(manualReviewSeconds * 1000);
}

await page.getByText(/save|publish/i).first().click({ timeout: 120_000 });
await page.waitForTimeout(10_000);
await context.close();

async function fillTextbox(page, label, value) {
  const box = page.getByRole('textbox', { name: label }).first();
  await box.click({ timeout: 60_000 });
  await box.fill(value);
}

async function next(page) {
  await page.getByText(/^next$/i).first().click({ timeout: 120_000 });
}

async function setSyntheticMediaDisclosure(page, containsSyntheticMedia) {
  const section = page.locator('ytcp-video-metadata-editor-section, ytcp-video-metadata-editor-altered-content')
    .filter({ hasText: /altered|synthetic|realistic/i })
    .first();
  if (await section.count().catch(() => 0)) {
    await section.getByText(containsSyntheticMedia ? /^yes$/i : /^no$/i)
      .first()
      .click({ timeout: 30_000 })
      .catch(() => undefined);
    return;
  }
  const disclosureText = containsSyntheticMedia
    ? /yes.*(altered|synthetic|realistic)|(altered|synthetic|realistic).*yes/i
    : /no.*(altered|synthetic|realistic)|(altered|synthetic|realistic).*no/i;
  await page.getByText(disclosureText).first().click({ timeout: 30_000 }).catch(() => undefined);
}

function parseArgs(values) {
  const parsed = {};
  for (let index = 0; index < values.length; index += 1) {
    const key = values[index];
    if (!key.startsWith('--')) {
      continue;
    }
    const next = values[index + 1];
    if (!next || next.startsWith('--')) {
      parsed[key.slice(2)] = 'true';
    } else {
      parsed[key.slice(2)] = next;
      index += 1;
    }
  }
  return parsed;
}

function required(value, name) {
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function number(value, fallback) {
  if (!value) {
    return fallback;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}
