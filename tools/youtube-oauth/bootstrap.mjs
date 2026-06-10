import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import process from 'node:process';

const DEFAULT_SCOPE = 'https://www.googleapis.com/auth/youtube.upload';
const DEFAULT_CALLBACK_PATH = '/oauth2callback';
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';

const args = parseArgs(process.argv.slice(2));
const credentials = args.credentials ? await readCredentials(args.credentials) : {};
const clientId = required(args['client-id'] || credentials.clientId, '--client-id or --credentials');
const clientSecret = args['client-secret'] ?? credentials.clientSecret ?? '';
const scope = args.scope || DEFAULT_SCOPE;
const callbackPath = normalizePath(args['callback-path'] || DEFAULT_CALLBACK_PATH);
const host = args.host || '127.0.0.1';
const requestedPort = number(args.port, 53682);
const open = args.open === 'true';
const out = args.out || '';
const state = base64url(randomBytes(24));
const codeVerifier = base64url(randomBytes(64));
const codeChallenge = base64url(createHash('sha256').update(codeVerifier).digest());

const { server, port, codePromise } = await startServer(host, requestedPort, callbackPath, state);
const redirectUri = `http://${host}:${port}${callbackPath}`;
const authorizationUrl = authorizationUrlFor({
  clientId,
  redirectUri,
  scope,
  state,
  codeChallenge
});

console.log('YouTube OAuth bootstrap is listening locally.');
console.log(`Redirect URI: ${redirectUri}`);
console.log('');
console.log('Open this URL in your browser and approve YouTube upload access:');
console.log(authorizationUrl);
console.log('');

if (open) {
  openBrowser(authorizationUrl);
}

try {
  const code = await codePromise;
  const tokens = await exchangeCode({ code, clientId, clientSecret, redirectUri, codeVerifier });
  if (!tokens.refresh_token) {
    throw new Error('Google did not return a refresh_token. Re-run with consent prompt or revoke the app grant, then try again.');
  }

  console.log('OAuth succeeded. Store these values on the local worker PC only:');
  console.log(`JENERATOR_YOUTUBE_MODE=API`);
  console.log(`JENERATOR_YOUTUBE_CLIENT_ID=${clientId}`);
  if (clientSecret) {
    console.log(`JENERATOR_YOUTUBE_CLIENT_SECRET=${clientSecret}`);
  }
  console.log(`JENERATOR_YOUTUBE_REFRESH_TOKEN=${tokens.refresh_token}`);

  if (out) {
    await writeEnvFile(out, clientId, clientSecret, tokens.refresh_token);
    console.log('');
    console.log(`Wrote worker env file: ${out}`);
  }
} finally {
  server.close();
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

async function readCredentials(file) {
  const json = JSON.parse(await readFile(file, 'utf8'));
  const body = json.installed || json.web || json;
  return {
    clientId: body.client_id,
    clientSecret: body.client_secret || ''
  };
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

function normalizePath(value) {
  return value.startsWith('/') ? value : `/${value}`;
}

function base64url(buffer) {
  return buffer.toString('base64url');
}

async function startServer(host, requestedPort, callbackPath, expectedState) {
  let resolveCode;
  let rejectCode;
  const codePromise = new Promise((resolve, reject) => {
    resolveCode = resolve;
    rejectCode = reject;
  });

  const server = createServer((request, response) => {
    const url = new URL(request.url, `http://${request.headers.host}`);
    if (url.pathname !== callbackPath) {
      response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end('Not found');
      return;
    }
    const error = url.searchParams.get('error');
    if (error) {
      response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end(`OAuth failed: ${error}`);
      rejectCode(new Error(`OAuth failed: ${error}`));
      return;
    }
    if (url.searchParams.get('state') !== expectedState) {
      response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end('OAuth state did not match. You can close this tab.');
      rejectCode(new Error('OAuth state did not match.'));
      return;
    }
    const code = url.searchParams.get('code');
    if (!code) {
      response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end('OAuth code was missing. You can close this tab.');
      rejectCode(new Error('OAuth code was missing.'));
      return;
    }
    response.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Jenerator received YouTube upload approval. You can close this tab.');
    resolveCode(code);
  });

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(requestedPort, host, resolve);
  });

  return {
    server,
    port: server.address().port,
    codePromise
  };
}

function authorizationUrlFor({ clientId, redirectUri, scope, state, codeChallenge }) {
  const url = new URL(AUTH_ENDPOINT);
  url.searchParams.set('client_id', clientId);
  url.searchParams.set('redirect_uri', redirectUri);
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('scope', scope);
  url.searchParams.set('state', state);
  url.searchParams.set('access_type', 'offline');
  url.searchParams.set('prompt', 'consent');
  url.searchParams.set('include_granted_scopes', 'true');
  url.searchParams.set('code_challenge', codeChallenge);
  url.searchParams.set('code_challenge_method', 'S256');
  return url.toString();
}

async function exchangeCode({ code, clientId, clientSecret, redirectUri, codeVerifier }) {
  const form = new URLSearchParams();
  form.set('client_id', clientId);
  if (clientSecret) {
    form.set('client_secret', clientSecret);
  }
  form.set('code', code);
  form.set('code_verifier', codeVerifier);
  form.set('redirect_uri', redirectUri);
  form.set('grant_type', 'authorization_code');

  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Token exchange failed with HTTP ${response.status}: ${text}`);
  }
  return JSON.parse(text);
}

async function writeEnvFile(file, clientId, clientSecret, refreshToken) {
  const lines = [
    'JENERATOR_YOUTUBE_MODE=API',
    `JENERATOR_YOUTUBE_CLIENT_ID=${clientId}`
  ];
  if (clientSecret) {
    lines.push(`JENERATOR_YOUTUBE_CLIENT_SECRET=${clientSecret}`);
  }
  lines.push(`JENERATOR_YOUTUBE_REFRESH_TOKEN=${refreshToken}`);
  lines.push('JENERATOR_YOUTUBE_PRIVACY_STATUS=private');
  await writeFile(file, `${lines.join('\n')}\n`, { encoding: 'utf8', flag: 'wx' });
}

function openBrowser(url) {
  const command = process.platform === 'win32'
    ? ['cmd', ['/c', 'start', '', url]]
    : process.platform === 'darwin'
      ? ['open', [url]]
      : ['xdg-open', [url]];
  const child = spawn(command[0], command[1], {
    detached: true,
    stdio: 'ignore'
  });
  child.unref();
}
