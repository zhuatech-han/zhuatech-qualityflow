#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
"""Validate the real quality workflow in an explicitly authorized disposable local database."""
import argparse, json, urllib.request, urllib.error, urllib.parse, http.cookiejar, secrets, datetime, os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8098');p.add_argument('--allow-test-data',action='store_true');p.add_argument('--browser-credentials');p.add_argument('--verify-existing',type=int);args=p.parse_args()
assert args.allow_test_data and urllib.parse.urlsplit(args.base).hostname in {'127.0.0.1','localhost','::1'},'An explicitly authorized local test deployment is required'
root=Path(__file__).resolve().parents[1];env=dict(x.split('=',1) for x in (root/'.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
checks=[]
def check(name,value):
    assert value,name
    checks.append(name);print('PASS '+name,flush=True)
class Client:
    def __init__(self):self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
    def request(self,method,path,body=None,expected=200,csrf=True):
        if method!='GET' and csrf and self.csrf is None:self.csrf=self.request('GET','/api/auth/csrf')
        h={'Content-Type':'application/json'}
        if method!='GET' and csrf:h[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.base+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
        try:
            with self.http.open(req,timeout=30) as r:status=r.status;content=r.read()
        except urllib.error.HTTPError as e:status=e.code;content=e.read()
        v=json.loads(content) if content else None
        assert status==expected,f'{method} {path}: expected {expected}, got {status}, code={v.get("code") if isinstance(v,dict) else "unknown"}'
        return v
    def login(self,u,password):return self.request('POST','/api/auth/login',{'username':u,'password':password})
admin=Client();anonymous=Client()
check('healthy backend',anonymous.request('GET','/actuator/health')['status']=='UP')
check('anonymous blocked',anonymous.request('GET','/api/cases',expected=401)['code']=='UNAUTHENTICATED')
check('administrator login',admin.login('admin',env['ADMIN_PASSWORD'])['username']=='admin')
if args.verify_existing:
    d=admin.request('GET',f'/api/cases/{args.verify_existing}/report.json')
    check('persistent report survived restart',d['kind']=='QUALITY_CORRECTIVE_ACTION_REPORT' and len(d['report']['events'])>=8)
    print(json.dumps({'checks':len(checks),'caseId':args.verify_existing,'result':'PASS'}));raise SystemExit
check('CSRF blocked',admin.request('POST','/api/admin/departments',{'name':'must not save'},expected=403,csrf=False)['code']=='FORBIDDEN')
suffix=secrets.token_hex(3);password='Aa9'+secrets.token_urlsafe(20)
dept=admin.request('POST','/api/admin/departments',{'name':'验收质量部-'+suffix})['id'];roles={x['name']:x['id'] for x in admin.request('GET','/api/admin/roles')}
def user(prefix,name,role,department=dept):
    username='qa-'+prefix+'-'+suffix
    a=admin.request('POST','/api/admin/users',{'username':username,'displayName':name+'（验收）','password':password,'roleId':roles[role],'departmentId':department,'enabled':True})
    c=Client();c.login(username,password);return c,a['id'],username
reporter,reporter_id,reporter_name=user('reporter','问题报告','质量协调员');owner,owner_id,owner_name=user('owner','整改负责','整改执行人');worker,worker_id,worker_name=user('worker','措施执行','整改执行人');reviewer,reviewer_id,reviewer_name=user('reviewer','独立复核','质量复核人');stranger,stranger_id,_=user('stranger','未指派人员','整改执行人')
check('account hashes not exposed',all('passwordHash' not in a for a in admin.request('GET','/api/admin/users')))
check('executor admin blocked',owner.request('GET','/api/admin/users',expected=403)['code']=='FORBIDDEN')
today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date();deadline=today+datetime.timedelta(days=7)
draft={'title':'验收：装配复核记录遗漏','description':'虚构验收资料：检查发现装配复核记录缺少签认，需隔离检查并建立措施。','source':'INTERNAL','category':'PROCESS','severity':'MAJOR','reference':'QA-PROCESS-01','departmentId':dept,'ownerId':owner_id,'reviewerId':reviewer_id,'dueDate':deadline.isoformat()}
d=reporter.request('POST','/api/cases',draft);cid=d['case']['id'];check('draft persisted',d['case']['status']=='DRAFT')
check('unassigned detail blocked',stranger.request('GET',f'/api/cases/{cid}',expected=403)['code']=='OUT_OF_SCOPE')
check('unassigned list empty',stranger.request('GET','/api/cases')['total']==0)
check('unassigned statistics empty',stranger.request('GET','/api/dashboard')['total']==0)
check('unassigned report blocked',stranger.request('GET',f'/api/cases/{cid}/report.json',expected=403)['code']=='OUT_OF_SCOPE')
def cmd(**values):return dict({'version':d['case']['version'],'requestKey':secrets.token_hex(16),'note':'虚构验收复核意见'},**values)
def act(client,name,body=None):return client.request('POST',f'/api/cases/{cid}/{name}',body or cmd())
c=cmd();d=act(reporter,'submit',c);check('report submitted',d['case']['status']=='TRIAGE');again=act(reporter,'submit',c);check('submission retry exactly once',len(again['events'])==len(d['events']))
check('published report frozen',reporter.request('PUT',f'/api/cases/{cid}',dict(draft,version=d['case']['version']),expected=409)['code']=='INVALID_STATE')
d=act(owner,'begin',cmd(containment='验收：停止相关工序流转，核对本批次记录并登记影响范围。'));check('containment persisted',d['case']['status']=='INVESTIGATION' and bool(d['case']['containment']))
check('cannot bypass plan',owner.request('POST',f'/api/cases/{cid}/submit-evidence',cmd(evidence='越级尝试'),expected=409)['code']=='INVALID_STATE')
d=act(owner,'analysis',cmd(rootCause='验收：工序记录模板没有复核必填项，交接前未检查。',verificationPlan='验收：连续抽查十次交接记录，复核签认完整且无遗漏。',verifyAfter=today.isoformat()))
check('cause and verification criteria saved',bool(d['case']['rootCause']) and bool(d['case']['verificationPlan']))
check('empty plan blocked',owner.request('POST',f'/api/cases/{cid}/submit-plan',cmd(),expected=400)['code']=='ACTIONS_REQUIRED')
action={'version':d['case']['version'],'kind':'CORRECTIVE','description':'验收：更新工序记录模板，补齐复核字段并开展交接核对。','ownerId':worker_id,'dueDate':today.isoformat()}
d=owner.request('POST',f'/api/cases/{cid}/actions',action);aid=d['actions'][0]['id'];check('action assigned and persisted',d['actions'][0]['ownerId']==worker_id)
d=act(owner,'submit-plan');check('plan pending independent review',d['case']['status']=='PLAN_REVIEW')
check('administrator cannot replace designated reviewer',admin.request('POST',f'/api/cases/{cid}/approve-plan',cmd(),expected=403)['code']=='NOT_REVIEWER')
d=act(reviewer,'reject-plan',cmd(note='验收：补充验证判定依据后再提交'));check('review return retains plan',d['case']['status']=='INVESTIGATION' and len(d['actions'])==1)
d=act(owner,'submit-plan');d=act(reviewer,'approve-plan',cmd(note='验收：整改措施与验证依据完整，可以执行'));check('approved execution',d['case']['status']=='EXECUTION')
check('incomplete actions block verification',owner.request('POST',f'/api/cases/{cid}/submit-evidence',cmd(evidence='越级提交'),expected=409)['code']=='ACTIONS_INCOMPLETE')
check('another executor cannot complete action',owner.request('POST',f'/api/cases/{cid}/actions/{aid}/complete',cmd(evidence='代填'),expected=403)['code']=='NOT_ACTION_OWNER')
check('empty action evidence rejected',worker.request('POST',f'/api/cases/{cid}/actions/{aid}/complete',cmd(),expected=400)['code']=='INVALID_INPUT')
c=cmd(evidence='验收：模板已更新，十份记录复核字段均完整。');d=worker.request('POST',f'/api/cases/{cid}/actions/{aid}/complete',c);again=worker.request('POST',f'/api/cases/{cid}/actions/{aid}/complete',c);check('action completion retry exactly once',len(d['events'])==len(again['events']) and d['actions'][0]['status']=='COMPLETED')
d=act(owner,'submit-evidence',cmd(evidence='验收：措施完成，提交复核抽查记录与执行结果。'));check('effectiveness verification pending',d['case']['status']=='VERIFY_READY')
d=act(reviewer,'verify',cmd(evidence='验收：十次交接记录均完整，无重复遗漏，整改有效。'));check('independent closure',d['case']['status']=='CLOSED' and d['case']['closedBy']==reviewer_id)
report=owner.request('GET',f'/api/cases/{cid}/report.json');check('complete report without advertising',report['kind']=='QUALITY_CORRECTIVE_ACTION_REPORT' and 'zhuatech2' not in json.dumps(report) and len(report['report']['events'])>=8)
d=act(reviewer,'reopen',cmd(note='验收：后续抽查再次出现遗漏，需要第二轮整改。'));check('reopening retains previous evidence',d['case']['cycle']==2 and d['actions'][0]['cycle']==1 and any('十次交接' in e['note'] for e in d['events']))
check('previous round does not satisfy new plan',owner.request('POST',f'/api/cases/{cid}/submit-plan',cmd(),expected=400)['code']=='INVALID_INPUT')
check('scoped search and pagination',owner.request('GET','/api/cases?search='+urllib.parse.quote('装配')+'&size=1&sort=due')['total']==1)
check('unsafe sort rejected',owner.request('GET','/api/cases?sort=title%20desc',expected=400)['code']=='INVALID_INPUT')
check('review audit persists',any(x['action']=='VERIFY' for x in reviewer.request('GET','/api/audit')))
other_dept=admin.request('POST','/api/admin/departments',{'name':'验收隔离部门-'+suffix})['id'];outsider,_,_=user('other','其他部门','质量协调员',other_dept)
check('other department detail blocked',outsider.request('GET',f'/api/cases/{cid}',expected=403)['code']=='OUT_OF_SCOPE')
check('other department list empty',outsider.request('GET','/api/cases')['total']==0)
# A separate pending case allows browser-only end-to-end acceptance with the same test accounts.
draft['title']='验收：出货检查记录漏项';draft['reference']='QA-SHIP-02';draft['description']='虚构验收资料：出货检查记录未完整填写，需要原因分析、整改与独立验证。';ui=reporter.request('POST','/api/cases',draft);ui_id=ui['case']['id'];ui=reporter.request('POST',f'/api/cases/{ui_id}/submit',{'version':ui['case']['version'],'requestKey':secrets.token_hex(16)})
if args.browser_credentials:
    target=Path(args.browser_credentials);assert root not in target.parents,'Credentials must stay outside publication tree'
    fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as out:json.dump({'reporter':reporter_name,'owner':owner_name,'worker':worker_name,'reviewer':reviewer_name,'password':password,'caseId':cid,'uiCaseId':ui_id,'departmentId':dept},out)
print(json.dumps({'checks':len(checks),'caseId':cid,'uiCaseId':ui_id,'result':'PASS'}))
