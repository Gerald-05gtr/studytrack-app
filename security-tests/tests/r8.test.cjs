// StudyTrack Lab 2: R8 access-control tests against local Firestore Emulator.
// No real Firebase project is contacted. This script requires emulator on 127.0.0.1:8080.
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc, serverTimestamp, Timestamp } = require('firebase/firestore');

const root = path.resolve(__dirname, '..', '..');
const rulesPath = path.join(root, 'firestore.rules');
const rules = fs.readFileSync(rulesPath, 'utf8');
const hash = crypto.createHash('sha256').update(rules).digest('hex');
const cases = [];
const due = Timestamp.fromDate(new Date('2026-12-02T12:00:00.000Z'));

function task(ownerId, overrides = {}) {
  return {
    ownerId,
    title: 'StudyTrack R8 test task',
    description: 'Testing secure task access',
    categoryId: 'assignment',
    categoryName: 'Assignment',
    deadline: due,
    priority: 2,
    completed: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides
  };
}
async function verify(number, scenario, expected, action) {
  try {
    await (expected === 'ALLOW' ? assertSucceeds(action()) : assertFails(action()));
    cases.push({ number, scenario, expected, observed: expected === 'ALLOW' ? 'Allowed' : 'Permission denied', status: 'PASS', error: '' });
    console.log(`PASS R8-${number}: ${scenario}`);
  } catch (error) {
    const message = String(error?.message || error).replace(/\s+/g, ' ').slice(0, 500);
    cases.push({ number, scenario, expected, observed: 'Unexpected response', status: 'FAIL', error: message });
    console.error(`FAIL R8-${number}: ${scenario} — ${message}`);
  }
}

async function main() {
  let env;
  try {
    env = await initializeTestEnvironment({
      projectId: 'demo-studytrack-lab2',
      firestore: { host: '127.0.0.1', port: 8080, rules }
    });
    // Only seed locally; admin bypass is for test setup, never client access.
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'categories', 'assignment'), {
        name: 'Assignment', description: 'University coursework and assignments', sortOrder: 1
      });
      const created = Timestamp.fromDate(new Date('2026-11-23T10:00:00.000Z'));
      await setDoc(doc(db, 'tasks', 'aliceTask'), task('user-A', {
        createdAt: created, updatedAt: created
      }));
    });
    const alice = env.authenticatedContext('user-A').firestore();
    const bob = env.authenticatedContext('user-B').firestore();
    const guest = env.unauthenticatedContext().firestore();

    await verify(1, 'Read own task as A', 'ALLOW', async () => {
      const snap = await getDoc(doc(alice, 'tasks', 'aliceTask'));
      assert.equal(snap.exists(), true, 'seeded task should exist');
      return snap;
    });
    await verify(2, 'Read A task as B', 'DENY', () => getDoc(doc(bob, 'tasks', 'aliceTask')));
    await verify(3, 'Read A task while signed out', 'DENY', () => getDoc(doc(guest, 'tasks', 'aliceTask')));
    await verify(4, 'Create as A with ownerId B', 'DENY', () =>
      setDoc(doc(alice, 'tasks', 'badOwner'), task('user-B')));
    await verify(5, 'Create priority 3 with too-short description', 'DENY', () =>
      setDoc(doc(alice, 'tasks', 'invalidLogic'), task('user-A', {priority: 3, description: 'short'})));
    await verify(6, 'Update A task to change its ownerId', 'DENY', () =>
      updateDoc(doc(alice, 'tasks', 'aliceTask'), { ownerId: 'user-B', updatedAt: serverTimestamp() }));
    await verify(7, 'Create category as signed-in A', 'DENY', () =>
      setDoc(doc(alice, 'categories', 'hacked'), {name: 'Hacked', description: 'Not allowed', sortOrder: 4}));
    await verify(8, 'Read a category as signed-in A', 'ALLOW', async () => {
      const snap = await getDoc(doc(alice, 'categories', 'assignment'));
      assert.equal(snap.exists(), true, 'seeded category should exist');
      return snap;
    });
  } catch (error) {
    console.error('TEST SETUP ERROR:', error);
    process.exitCode = 1;
  } finally {
    const now = new Date().toISOString();
    const passed = cases.filter(x => x.status === 'PASS').length;
    const failed = cases.filter(x => x.status === 'FAIL').length;
    if (cases.length > 0) {
      const report = [
        '# Lab 2 — R8 Firestore Security Rules Emulator Results',
        '',
        `- Run (UTC): ${now}`,
        '- Environment: Firebase Local Emulator Suite; mock user A/B identities; **not** a production test',
        '- Project ID: `demo-studytrack-lab2` (emulator-only)',
        `- Source file: \`firestore.rules\` (SHA-256: \`${hash}\`)`,
        `- Results: ${passed}/${cases.length} passed${failed ? `; ${failed} failed` : ''}`,
        '',
        '| # | Scenario | Expected | Observed | Result |',
        '|---|---|---|---|---|',
        ...cases.map(c=> `| ${c.number} | ${c.scenario} | ${c.expected} | ${c.observed} | ${c.status} |`),
        '',
        ...cases.filter(c => c.error).map(c => `- R8-${c.number} error: ${c.error}`),
        '',
        '**Note:** This is generated by an actual emulator execution. Verify that `firestore.rules` matches the rules published in Firebase Console before claiming deployed-rule coverage.',
        ''
      ].join('\n');
      const dest = path.join(root, 'docs', 'R8_RESULTS.md');
      fs.mkdirSync(path.dirname(dest), {recursive: true});
      fs.writeFileSync(dest, report, 'utf8');
      console.log(`Saved real results: ${dest}`);
    }
    if (env) await env.cleanup();
    if (cases.length !== 8 || failed !== 0) process.exitCode = 1;
    console.log(`R8 summary: ${passed}/8 PASS (${failed} FAIL).`);
  }
}
main().catch(err=>{console.error(err);process.exitCode=1});
