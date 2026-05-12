import assert from 'node:assert/strict';
import test from 'node:test';

import {
  parseBooleanField,
  parseBoundedInteger,
  parseId,
  parseInteger
} from '../src/lib/routeParams.js';

test('parseInteger and parseId return null for invalid integers', () => {
  assert.equal(parseInteger('42'), 42);
  assert.equal(parseInteger('-3'), -3);
  assert.equal(parseInteger(' 7 '), 7);
  assert.equal(parseInteger('12abc'), null);
  assert.equal(parseInteger('1.5'), null);
  assert.equal(parseInteger(''), null);
  assert.equal(parseInteger(undefined), null);
  assert.equal(parseId('42'), 42);
  assert.equal(parseId(42), 42);
  assert.equal(parseId('0'), null);
  assert.equal(parseId('-1'), null);
  assert.equal(parseId('abc'), null);
  assert.equal(parseId(undefined), null);
});

test('parseBoundedInteger applies fallback and clamps bounds', () => {
  assert.equal(parseBoundedInteger('10', { fallback: 5, min: 1, max: 20 }), 10);
  assert.equal(parseBoundedInteger('0', { fallback: 5, min: 1, max: 20 }), 1);
  assert.equal(parseBoundedInteger('50', { fallback: 5, min: 1, max: 20 }), 20);
  assert.equal(parseBoundedInteger('bad', { fallback: 5, min: 1, max: 20 }), 5);
});

test('parseBooleanField only accepts actual booleans', () => {
  assert.equal(parseBooleanField({ enabled: true }, 'enabled'), true);
  assert.equal(parseBooleanField({ enabled: false }, 'enabled'), false);
  assert.equal(parseBooleanField({ enabled: 'true' }, 'enabled'), null);
});
