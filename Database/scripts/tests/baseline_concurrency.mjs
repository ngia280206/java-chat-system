// Optional integration tests. Use an isolated database ending in _test or
// _validation. Fixtures are committed and intentionally retained for inspection.
// Connection: standard PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE variables.
import pg from 'pg';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

export async function run(options = {}) {
  const observer = new pg.Client(options);
  const first = new pg.Client(options);
  const second = new pg.Client(options);
  await Promise.all([observer.connect(), first.connect(), second.connect()]);
  let passed = 0;
  try {
    const database = (await observer.query('SELECT current_database() AS name')).rows[0].name;
    assert.match(database, /_(test|validation)$/, 'Use a disposable database ending in _test or _validation');
    for (const client of [observer, first, second]) {
      await client.query("SET lock_timeout='5s'; SET statement_timeout='15s'");
    }
    const secondPid = (await second.query('SELECT pg_backend_pid() AS pid')).rows[0].pid;
    const prefix = `concurrent_${Date.now()}_${process.pid}_`;
    const ids = [];
    for (let i = 0; i < 6; i++) {
      ids.push((await observer.query(`INSERT INTO chat.users(username,email,password_hash,full_name)
        VALUES($1,$2,$3,$4) RETURNING user_id`, [prefix+i, prefix+i+'@example.com',
        '$TEST_ONLY_HASH_NOT_A_VALID_LOGIN_HASH$', 'Concurrency test '+i])).rows[0].user_id);
    }
    const [a,b,c,d,e,f] = ids;
    async function beginBoth() { await first.query('BEGIN'); await second.query('BEGIN'); }
    const resultOf = promise => promise.then(result => ({ result }), error => ({ error }));
    async function waitForSecondLock() {
      const deadline = Date.now() + 3000;
      while (Date.now() < deadline) {
        const state = await observer.query('SELECT wait_event_type FROM pg_stat_activity WHERE pid=$1', [secondPid]);
        if (state.rows[0]?.wait_event_type === 'Lock') return;
        await new Promise(resolve => setTimeout(resolve, 20));
      }
      throw new Error('Second transaction did not block on the expected database lock');
    }
    function success(outcome) { if (outcome.error) throw outcome.error; return outcome.result; }
    function failure(outcome, code) { assert.equal(outcome.error?.code, code); }
    function pass(label) { passed++; console.log(`PASS concurrency ${passed}: ${label}`); }

    // The helper returns the existing conversation when another creator wins.
    await beginBoth();
    const direct = (await first.query('SELECT chat.create_direct_conversation($1,$2) AS id',[a,b])).rows[0].id;
    let pending = resultOf(second.query('SELECT chat.create_direct_conversation($1,$2) AS id',[b,a]));
    await waitForSecondLock(); await first.query('COMMIT');
    assert.equal(success(await pending).rows[0].id,direct); await second.query('COMMIT');
    pass('simultaneous helper calls produce one DIRECT conversation');

    // The invariant also holds for direct SQL instead of the helper.
    await beginBoth();
    const raw1=(await first.query("INSERT INTO chat.conversations(type,created_by) VALUES('DIRECT',$1) RETURNING conversation_id",[c])).rows[0].conversation_id;
    const raw2=(await second.query("INSERT INTO chat.conversations(type,created_by) VALUES('DIRECT',$1) RETURNING conversation_id",[d])).rows[0].conversation_id;
    await first.query('INSERT INTO chat.conversation_members(conversation_id,user_id) VALUES($1,$2),($1,$3)',[raw1,c,d]);
    pending=resultOf(second.query('INSERT INTO chat.conversation_members(conversation_id,user_id) VALUES($1,$2),($1,$3)',[raw2,d,c]));
    await waitForSecondLock(); await first.query('COMMIT');
    failure(await pending,'23505'); await second.query('ROLLBACK');
    pass('raw SQL cannot create a duplicate DIRECT pair concurrently');

    await beginBoth();
    await first.query('SELECT chat.send_text_message($1,$2,$3)',[a,direct,'first concurrent message']);
    pending=resultOf(second.query('SELECT chat.send_text_message($1,$2,$3)',[b,direct,'second concurrent message']));
    await waitForSecondLock(); await first.query('COMMIT'); success(await pending); await second.query('COMMIT');
    const sequences=await observer.query('SELECT message_seq FROM chat.messages WHERE conversation_id=$1 ORDER BY message_seq',[direct]);
    assert.deepEqual(sequences.rows.map(x=>x.message_seq),['1','2']);
    assert.equal((await observer.query("SELECT count(*) FROM chat.activity_logs WHERE conversation_id=$1 AND event_type='MESSAGE_SENT'",[direct])).rows[0].count,'2');
    pass('simultaneous messages receive unique ordered sequences and one log each');

    await beginBoth();
    assert.equal((await first.query('SELECT chat.clear_history($1,$2) AS boundary',[a,direct])).rows[0].boundary,'2');
    pending=resultOf(second.query('SELECT chat.send_text_message($1,$2,$3)',[b,direct,'after concurrent clear']));
    await waitForSecondLock(); await first.query('COMMIT'); success(await pending); await second.query('COMMIT');
    assert.equal((await observer.query('SELECT count(*) FROM chat.get_visible_messages($1,$2)',[a,direct])).rows[0].count,'1');
    pass('message after concurrent history-clear stays visible');

    await observer.query('BEGIN');
    const group=(await observer.query("SELECT chat.create_group($1,'Concurrent admin test',ARRAY[$2::bigint]) AS id",[a,b])).rows[0].id;
    await observer.query("SELECT chat.change_group_role($1,$2,$3,'ADMIN')",[a,group,b]); await observer.query('COMMIT');
    await beginBoth();
    await first.query("SELECT chat.change_group_role($1,$2,$1,'MEMBER')",[a,group]);
    pending=resultOf(second.query("SELECT chat.change_group_role($1,$2,$1,'MEMBER')",[b,group]));
    await waitForSecondLock(); await first.query('COMMIT'); success(await pending);
    failure(await resultOf(second.query('COMMIT')),'23514'); await second.query('ROLLBACK');
    assert.equal((await observer.query('SELECT admin_count FROM chat.v_group_summary WHERE conversation_id=$1',[group])).rows[0].admin_count,'1');
    pass('concurrent demotions cannot remove the final group admin');

    await beginBoth(); await first.query('SELECT chat.block_user($1,$2)',[a,b]);
    pending=resultOf(second.query('SELECT chat.send_text_message($1,$2,$3)',[b,direct,'must be blocked']));
    await waitForSecondLock(); await first.query('COMMIT'); failure(await pending,'23514'); await second.query('ROLLBACK');
    await observer.query('SELECT chat.unblock_user($1,$2)',[a,b]);
    pass('send waiting behind a block is rejected after the block commits');

    await beginBoth(); await first.query("UPDATE chat.users SET status='LOCKED' WHERE user_id=$1",[b]);
    pending=resultOf(second.query('SELECT chat.send_text_message($1,$2,$3)',[b,direct,'must reject locked sender']));
    await waitForSecondLock(); await first.query('COMMIT'); failure(await pending,'23514'); await second.query('ROLLBACK');
    await observer.query("UPDATE chat.users SET status='ACTIVE' WHERE user_id=$1",[b]);
    pass('send waiting behind account-lock sees the committed lock');

    await observer.query('BEGIN');
    const encrypted=(await observer.query("SELECT chat.create_group($1,'Concurrent E2EE test',ARRAY[$2::bigint],'E2EE') AS id",[e,f])).rows[0].id;
    const device=(await observer.query("INSERT INTO chat.user_devices(user_id,public_key) VALUES($1,decode('aabb','hex')) RETURNING device_id",[f])).rows[0].device_id;
    await observer.query('INSERT INTO chat.group_key_versions(conversation_id,key_version,created_by) VALUES($1,1,$2)',[encrypted,e]); await observer.query('COMMIT');
    await beginBoth(); await first.query('UPDATE chat.user_devices SET revoked_at=clock_timestamp() WHERE device_id=$1',[device]);
    pending=resultOf(second.query("INSERT INTO chat.group_key_envelopes(conversation_id,key_version,device_id,wrapped_key) VALUES($1,1,$2,decode('aabb','hex'))",[encrypted,device]));
    await waitForSecondLock(); await first.query('COMMIT'); failure(await pending,'23514'); await second.query('ROLLBACK');
    pass('concurrent device revocation prevents a new key envelope');

    await first.query('BEGIN ISOLATION LEVEL REPEATABLE READ');
    failure(await resultOf(first.query('SELECT chat.create_direct_conversation($1,$2)',[a,c])),'25000');
    await first.query('ROLLBACK');
    pass('repeatable-snapshot writes are explicitly rejected rather than weakening invariants');

    console.log(`PASS: ${passed} concurrency scenarios. Committed fixtures use prefix ${prefix}`);
    return passed;
  } finally {
    await Promise.all([first.query('ROLLBACK').catch(()=>{}), second.query('ROLLBACK').catch(()=>{})]);
    await Promise.all([observer.end(), first.end(), second.end()]);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  run().catch(error=>{console.error(error);process.exitCode=1;});
}
