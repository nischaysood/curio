import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hashPassword, verifyPassword, looksLikeEmail } from '../src/auth.js';

/**
 * Password handling is the one place in this codebase where a bug is other
 * people's problem rather than ours. These tests are cheap; the failure they
 * prevent is not.
 */

test('a correct password verifies', async () => {
  const stored = await hashPassword('correct horse battery staple');
  assert.equal(await verifyPassword('correct horse battery staple', stored), true);
});

test('a wrong password does not verify', async () => {
  const stored = await hashPassword('correct horse battery staple');
  assert.equal(await verifyPassword('correct horse battery stapl', stored), false);
});

test('the same password hashes differently every time', async () => {
  // Random salt per user. Without it, identical passwords produce identical
  // hashes and one leaked table tells you who shares a password with whom.
  const a = await hashPassword('same password');
  const b = await hashPassword('same password');
  assert.notEqual(a, b);
  assert.equal(await verifyPassword('same password', a), true);
  assert.equal(await verifyPassword('same password', b), true);
});

test('the stored format carries its own parameters', async () => {
  // pbkdf2$<iterations>$<salt>$<hash> — so the iteration count can be raised
  // later without invalidating every existing password.
  const stored = await hashPassword('x');
  const [scheme, iterations, salt, hash] = stored.split('$');
  assert.equal(scheme, 'pbkdf2');
  assert.ok(Number(iterations) >= 100_000);
  assert.ok(salt.length > 0 && hash.length > 0);
});

test('a malformed stored hash returns false rather than throwing', async () => {
  // A corrupt row should fail the login, not 500 the endpoint.
  for (const bad of ['', 'garbage', 'pbkdf2$only$three', 'bcrypt$1$a$b', null, undefined]) {
    assert.equal(await verifyPassword('anything', bad), false);
  }
});

test('an empty password still hashes and verifies consistently', async () => {
  // Signup rejects these for length, but the primitive must not have a
  // surprising edge case underneath that check.
  const stored = await hashPassword('');
  assert.equal(await verifyPassword('', stored), true);
  assert.equal(await verifyPassword('x', stored), false);
});

test('email validation accepts real addresses and rejects obvious junk', () => {
  for (const good of ['a@b.co', 'nischay02sood@gmail.com', 'first.last+tag@sub.domain.org']) {
    assert.equal(looksLikeEmail(good), true, good);
  }
  for (const bad of ['', 'no-at-sign', 'a@b', 'a@.com', 'two @spaces.com', null]) {
    assert.equal(looksLikeEmail(bad), false, String(bad));
  }
});
