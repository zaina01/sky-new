import test from 'node:test';
import assert from 'node:assert/strict';
import { createContext, resultValue } from '../sdk.mjs';
const ctx = () => createContext({ jobId:'1',requestId:'r',inputJson:'{}' }, { SKY_PLUGIN_ID:'p',SKY_PLUGIN_INSTANCE_ID:'i',SKY_PLUGIN_TOKEN:'t' }, {}, Date.now()+1000,new AbortController().signal);
test('requires an explicit SDK result', () => { assert.throws(()=>resultValue({succeeded:true}),/must return/);assert.throws(()=>resultValue(undefined),/must return/); });
test('rejects JSON values that would silently lose data', () => { for(const value of [NaN,Infinity,undefined,1n,()=>{}])assert.throws(()=>resultValue(ctx().success('',{value}))); });
test('serializes success and business failure', () => { assert.deepEqual(resultValue(ctx().success('done',{count:1})),{succeeded:true,message:'done',resultJson:'{"count":1}'});assert.equal(resultValue(ctx().failure('no')).succeeded,false); });
