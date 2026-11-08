import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webHtml,webJs,webCss} from './web.mjs';

test('central online has OTP, read-only history and account deletion',()=>{
 assert.match(webHtml,/id="email"/);
 assert.match(webHtml,/id="deleteAccount"/);
 assert.match(webJs,/\/v1\/cloud\/quotes/);
 assert.match(webJs,/\/v1\/cloud\/account/);
 assert.match(webJs,/EXCLUIR MINHA CONTA/);
 assert.match(webCss,/section\{/);
});
