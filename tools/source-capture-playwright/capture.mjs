import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { chromium } from 'playwright';

const args = parseArgs(process.argv.slice(2));
const url = required(args.url, '--url');
const out = path.resolve(required(args.out, '--out'));
const width = number(args.width, 1080);
const height = number(args.height, 1920);
const shots = number(args.shots, 6);
const delay = number(args.delay, 0);
const interval = number(args.interval, 4);
const manualSeconds = number(args['manual-seconds'], 0);
const userDataDir = args['user-data-dir'] ? path.resolve(args['user-data-dir']) : null;

await mkdir(out, { recursive: true });

let browser;
let context;
if (userDataDir) {
  context = await chromium.launchPersistentContext(userDataDir, {
    channel: args.channel || undefined,
    headless: args.headless === 'true',
    viewport: { width, height }
  });
} else {
  browser = await chromium.launch({
    channel: args.channel || undefined,
    headless: args.headless === 'true'
  });
  context = await browser.newContext({ viewport: { width, height } });
}

const page = context.pages()[0] || await context.newPage();
await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 120_000 });
if (manualSeconds > 0) {
  await page.waitForTimeout(manualSeconds * 1000);
}
if (delay > 0) {
  await page.waitForTimeout(delay * 1000);
}

for (let index = 0; index < shots; index += 1) {
  const filename = `capture-${String(index + 1).padStart(2, '0')}.png`;
  await page.screenshot({ path: path.join(out, filename), fullPage: false });
  if (index < shots - 1) {
    await page.waitForTimeout(interval * 1000);
  }
}

await context.close();
if (browser) {
  await browser.close();
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
