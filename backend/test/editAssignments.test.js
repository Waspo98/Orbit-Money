import assert from 'node:assert/strict';
import test from 'node:test';

import { appendUserEditAssignment } from '../src/lib/editAssignments.js';

test('manual edits to an original value stay user-owned when a rule had claimed the field', () => {
  const sets = [];
  const values = [];
  const result = appendUserEditAssignment({
    sets,
    values,
    editColumn: 'edited_category_id',
    sourceColumn: 'edited_category_id_source',
    value: 14,
    originalValue: 14,
    existingSource: 'rule:7'
  });

  assert.equal(result, 'user');
  assert.deepEqual(sets, ['edited_category_id = ?', "edited_category_id_source = 'user'"]);
  assert.deepEqual(values, [14]);
});

test('unchanged original values remain stored as no edit', () => {
  const sets = [];
  const values = [];
  const result = appendUserEditAssignment({
    sets,
    values,
    editColumn: 'edited_category_id',
    sourceColumn: 'edited_category_id_source',
    value: 14,
    originalValue: 14,
    existingSource: null
  });

  assert.equal(result, 'clear');
  assert.deepEqual(sets, ['edited_category_id = NULL', 'edited_category_id_source = NULL']);
  assert.deepEqual(values, []);
});
