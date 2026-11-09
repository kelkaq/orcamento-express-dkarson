import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webHtml,webJs,webCss} from './web.mjs';
test('central web com chave sem e-mail e opção de exclusão',()=>{
 assert.match(webHtml,/id="recoveryKey"/);
 assert.match(webHtml,/id="createAccount"/);
 assert.match(webJs,/\/v1\/cloud\/create-private-account/);
 assert.match(webHtml,/id="deleteAccount"/);
 assert.doesNotMatch(webHtml,/Receber código por e-mail/);
 assert.match(webJs,/\/v1\/cloud\/recover-private-account/);
 assert.match(webJs,/\/v1\/cloud\/quotes/);
 assert.match(webJs,/\/v1\/cloud\/account/);
 assert.match(webJs,/EXCLUIR MINHA CONTA/);
 assert.match(webCss,/section\{/);
});