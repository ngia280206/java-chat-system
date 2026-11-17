import {run as base} from './baseline_concurrency.mjs';
import {run as sync} from './sync_concurrency.mjs';
try {await base(); await sync();} catch(e) {console.error(e); process.exitCode=1;}
