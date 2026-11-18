// Optional tests: database must end in _test or _validation.
// Fixtures are committed and retained. Use a disposable development database.
import pg from 'pg';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';

export async function run(options={}) {
  const observer=new pg.Client(options),a=new pg.Client(options),b=new pg.Client(options);
  await Promise.all([observer.connect(),a.connect(),b.connect()]);
  let passed=0;
  try {
    assert.match((await observer.query('SELECT current_database() AS name')).rows[0].name,/_(test|validation)$/);
    for(const c of [observer,a,b])await c.query("SET lock_timeout='5s'; SET statement_timeout='15s'");
    const pid=(await b.query('SELECT pg_backend_pid() AS pid')).rows[0].pid;
    const prefix=`extra_${Date.now()}_${process.pid}_`;
    const hash='$2b$12$B1zVABcLojU.3zd7RlG4gen6H2ey2AXe/jlhb864mMNFOO4HIh19y';
    const ids=[];
    for(let i=0;i<5;i++)ids.push((await observer.query(`INSERT INTO chat.users(username,email,password_hash,full_name,system_role)
      VALUES($1,$2,$3,$4,$5) RETURNING user_id`,[prefix+i,prefix+i+'@example.com',hash,'Concurrent '+i,i>=3?'ADMIN':'USER'])).rows[0].user_id);
    const [u1,u2,u3,admin1,admin2]=ids;
    await observer.query('BEGIN');
    const group=(await observer.query("SELECT chat.create_group($1,'Extra concurrent group',ARRAY[$2::bigint]) AS id",[u1,u2])).rows[0].id;
    await observer.query("SELECT chat.change_group_role($1,$2,$3,'ADMIN')",[u1,group,u2]);await observer.query('COMMIT');
    const outcome=p=>p.then(result=>({result}),error=>({error}));
    const success=o=>{if(o.error)throw o.error;return o.result;};
    const fail=(o,code)=>assert.equal(o.error?.code,code);
    async function begin(){await a.query('BEGIN');await b.query('BEGIN');}
    async function waiting(){
      const deadline=Date.now()+3000;
      while(Date.now()<deadline){
        if((await observer.query('SELECT wait_event_type FROM pg_stat_activity WHERE pid=$1',[pid])).rows[0]?.wait_event_type==='Lock')return;
        await new Promise(r=>setTimeout(r,20));
      }
      throw Error('Second connection did not wait for database lock');
    }
    function pass(label){passed++;console.log(`PASS concurrency ${passed}: ${label}`);}

    await begin();
    const invitation=(await a.query('SELECT chat.create_group_invitation($1,$2,$3) AS id',[u1,group,u3])).rows[0].id;
    let pending=outcome(b.query('SELECT chat.create_group_invitation($1,$2,$3)',[u2,group,u3]));
    await waiting();await a.query('COMMIT');fail(await pending,'23505');await b.query('ROLLBACK');
    pass('two admins cannot create duplicate pending invitations');

    await begin();await a.query("SELECT chat.respond_group_invitation($1,$2,'ACCEPTED')",[u3,invitation]);
    pending=outcome(b.query("SELECT chat.respond_group_invitation($1,$2,'ACCEPTED')",[u3,invitation]));
    await waiting();await a.query('COMMIT');fail(await pending,'23514');await b.query('ROLLBACK');
    assert.equal((await observer.query('SELECT count(*) FROM chat.conversation_members WHERE conversation_id=$1 AND user_id=$2',[group,u3])).rows[0].count,'1');
    pass('simultaneous acceptance produces one membership');

    const token1=Buffer.alloc(32,1),token2=Buffer.alloc(32,2);
    await begin();
    const firstReset=(await a.query('SELECT chat.create_password_reset($1,$2) AS id',[u3,token1])).rows[0].id;
    pending=outcome(b.query('SELECT chat.create_password_reset($1,$2) AS id',[u3,token2]));
    await waiting();await a.query('COMMIT');const secondReset=success(await pending).rows[0].id;await b.query('COMMIT');
    assert.equal((await observer.query('SELECT revoked_at IS NOT NULL AS revoked FROM chat.password_reset_requests WHERE reset_id=$1',[firstReset])).rows[0].revoked,true);
    assert.equal((await observer.query('SELECT count(*) FROM chat.password_reset_requests WHERE user_id=$1 AND consumed_at IS NULL AND revoked_at IS NULL',[u3])).rows[0].count,'1');
    pass('simultaneous reset requests leave one open request');

    assert.equal((await observer.query('SELECT chat.verify_password_reset($1,$2) AS ok',[secondReset,token2])).rows[0].ok,true);
    await begin();assert.equal((await a.query('SELECT chat.complete_password_reset($1,$2,$3) AS ok',[secondReset,token2,hash])).rows[0].ok,true);
    pending=outcome(b.query('SELECT chat.complete_password_reset($1,$2,$3) AS ok',[secondReset,token2,hash]));
    await waiting();await a.query('COMMIT');assert.equal(success(await pending).rows[0].ok,false);await b.query('COMMIT');
    pass('a verified reset can be consumed only once');

    const message=(await observer.query('SELECT chat.send_text_message($1,$2,$3) AS id',[u2,group,'Concurrent report evidence'])).rows[0].id;
    const report=(await observer.query('SELECT chat.submit_spam_report($1,$2,$3) AS id',[u1,message,'Concurrent report'])).rows[0].id;
    await begin();await a.query("SELECT chat.moderate_report($1,$2,'WARN','first admin')",[admin1,report]);
    pending=outcome(b.query("SELECT chat.moderate_report($1,$2,'SUSPEND','second admin')",[admin2,report]));
    await waiting();await a.query('COMMIT');fail(await pending,'23514');await b.query('ROLLBACK');
    assert.equal((await observer.query('SELECT count(*) FROM chat.moderation_actions WHERE report_id=$1',[report])).rows[0].count,'1');
    assert.equal((await observer.query('SELECT status FROM chat.users WHERE user_id=$1',[u2])).rows[0].status,'ACTIVE');
    pass('competing moderation decisions cannot double-process or lock after a warning');

    console.log(`PASS: ${passed} concurrency scenarios; retained fixture prefix: ${prefix}`);
    return passed;
  } finally {
    await Promise.all([a.query('ROLLBACK').catch(()=>{}),b.query('ROLLBACK').catch(()=>{})]);
    await Promise.all([observer.end(),a.end(),b.end()]);
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)run().catch(e=>{console.error(e);process.exitCode=1;});
