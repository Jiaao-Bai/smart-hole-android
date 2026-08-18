import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { promisify } from 'node:util';
import { Worker } from 'node:worker_threads';
import { zstdCompress, zstdDecompress } from 'node:zlib';

const workerResult = await new Promise((resolve, reject) => {
  const worker = new Worker(
    "require('node:worker_threads').parentPort.postMessage(6 * 7)",
    { eval: true },
  );
  worker.once('message', resolve);
  worker.once('error', reject);
});
assert.equal(workerResult, 42);

const workdir = await mkdtemp(join(tmpdir(), 'dsh-node-'));
const probeFile = join(workdir, 'probe.txt');
await writeFile(probeFile, 'android-bionic');
assert.equal(await readFile(probeFile, 'utf8'), 'android-bionic');

const shell = execFileSync('/system/bin/sh', ['-c', 'printf child-process'], {
  encoding: 'utf8',
});
assert.equal(shell, 'child-process');

const database = new DatabaseSync(':memory:');
assert.equal(database.prepare('select 40 + 2 as answer').get().answer, 42);
database.close();

const compress = promisify(zstdCompress);
const decompress = promisify(zstdDecompress);
const compressed = await compress(Buffer.from('session-jsonl'));
assert.equal((await decompress(compressed)).toString(), 'session-jsonl');

const server = createServer((_request, response) => response.end('loopback'));
await new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(0, '127.0.0.1', resolve);
});
const address = server.address();
assert.equal(typeof address, 'object');
const response = await fetch(`http://127.0.0.1:${address.port}`);
assert.equal(await response.text(), 'loopback');
await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
await rm(workdir, { recursive: true });

console.log(JSON.stringify({
  version: process.version,
  platform: process.platform,
  arch: process.arch,
  uid: process.getuid?.(),
  tmpdir: tmpdir(),
  childProcess: shell,
  worker: workerResult,
  loopbackFetchStatus: response.status,
  sqlite: true,
  zstd: true,
}));
