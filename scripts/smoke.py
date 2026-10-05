#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise isolated local MySQL training, permissions, retries and persistence without printing credentials."""
from pathlib import Path
import urllib.request,urllib.error,urllib.parse,http.cookiejar,json,uuid,secrets,datetime,os,argparse
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--base',default='http://127.0.0.1:8106');parser.add_argument('--verify',action='store_true');parser.add_argument('--state',default='.smoke-state.json');args=parser.parse_args()
assert args.state=='.smoke-state.json' or __import__('re').fullmatch(r'\.smoke-state-[a-z0-9]+\.json',args.state), 'Private local state filename required'
statefile=root/args.state
assert urllib.parse.urlparse(args.base).hostname in ('127.0.0.1','localhost'),'Only isolated local test instances are supported'
checks=0
class Client:
    def __init__(self,user=None,password=None):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None
        if user:self.call('/auth/login','POST',{'username':user,'password':password})
    def call(self,path,method='GET',body=None,expected=200):
        global checks
        if method!='GET' and self.csrf is None:self.csrf=self.call('/auth/csrf')
        headers={'Content-Type':'application/json'}
        if method!='GET':headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.base+'/api'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=20) as response:code=response.status;data=json.load(response)
        except urllib.error.HTTPError as e:code=e.code;data=json.load(e)
        assert code==expected,(path,code,data.get('code') if isinstance(data,dict) else None)
        checks+=1;return data
config=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if '=' in line)
a=Client('admin',config['ADMIN_PASSWORD'])
def check(value):
    global checks
    assert value
    checks+=1
def cmd(r,note='TEST 核对依据'):
    return {'version':r['version'],'requestKey':str(uuid.uuid4()),'note':note}
if args.verify:
    state=json.loads((statefile).read_text());l=Client(state['learner'],state['password']);r=l.call('/enrollments/'+str(state['enrollment']))
    check(r['record']['status']=='QUALIFIED');check(len(r['attempts'])==2);check(r['record']['bestScore']==100);check('correctAnswer' not in json.dumps(r));check(l.call('/enrollments?search='+urllib.parse.quote(r['course']['familyCode']))['total']==1)
    print(json.dumps({'persistenceChecks':checks,'result':'PASS'}));raise SystemExit
suffix=uuid.uuid4().hex[:8];password='Aa9'+secrets.token_urlsafe(24)
d=a.call('/admin/departments','POST',{'name':'TEST 培训验收 '+suffix})['id']
roles={r['name']:r['id'] for r in a.call('/admin/roles')}
def user(prefix,role,dept=d):
    name=prefix+'-'+suffix
    id=a.call('/admin/users','POST',dict(username=name,displayName='TEST '+prefix,password=password,roleId=roles[role],departmentId=dept,enabled=True))['id']
    return name,id,Client(name,password)
wn,wi,w=user('training-manager','培训管理员');rn,ri,r=user('training-reviewer','培训复核员');ln,li,l=user('training-learner','学员');on,oi,o=user('other-learner','学员');xn,xi,x=user('outside-manager','培训管理员',1)
body=dict(familyCode='TEST-'+suffix,title='TEST 工位交接培训',category='OPERATIONS',departmentId=d,content='验收测试课程。操作开始前核对记录，完成后保存交接证据。所有内容仅为系统验收。',passScore=80,maxAttempts=3,validDays=365,practicalRequired=True,questions=[dict(prompt='验收题：开始前做什么？',options=['核对记录','忽略记录','删除记录','猜测状态'],correctAnswer=0),dict(prompt='验收题：完成后做什么？',options=['直接离开','保存证据','删除证据','跳过核对'],correctAnswer=1)])
c=w.call('/courses','POST',body);cid=c['id'];cp='/courses/'+str(cid)
def current():return w.call(cp)['record']
w.call(cp+'/commands/publish','POST',cmd(c),403)
c=r.call(cp+'/commands/publish','POST',cmd(c));check(c['status']=='PUBLISHED')
x.call(cp,expected=403);check(x.call('/courses?search=TEST-'+suffix)['total']==0)
asg=cmd(c);asg.update(learnerId=li,reviewerId=ri,dueDate=(datetime.date.today()+datetime.timedelta(days=14)).isoformat())
e=w.call(cp+'/assign','POST',asg);check(w.call(cp+'/assign','POST',asg)['id']==e['id']);ep='/enrollments/'+str(e['id'])
copy=dict(asg,requestKey=str(uuid.uuid4()));w.call(cp+'/assign','POST',copy,409)
o.call(ep,expected=403);check(o.call('/enrollments')['total']==0);check(o.call('/courses')['total']==0)
check('correctAnswer' not in json.dumps(l.call(ep)));check('correctAnswer' not in json.dumps(l.call(cp+'/report.json')));check(len(l.call('/options')['accounts'])==1)
l.call('/admin/users',expected=403);Client().call('/courses',expected=401)
e=l.call(ep+'/commands/read','POST',cmd(e));check(e['status']=='LEARNING')
exam=cmd(e);exam['answers']=[3,3];e=l.call(ep+'/commands/exam','POST',exam);check(e['bestScore']==0);check(e['attemptsUsed']==1);check(l.call(ep+'/commands/exam','POST',exam)['attemptsUsed']==1)
changed=dict(exam,answers=[0,1]);l.call(ep+'/commands/exam','POST',changed,409)
exam=cmd(e);exam['answers']=[0,1];e=l.call(ep+'/commands/exam','POST',exam);check(e['status']=='PRACTICAL');check(e['bestScore']==100)
a.call(ep+'/commands/pass','POST',cmd(e),403)
e=r.call(ep+'/commands/pass','POST',cmd(e));check(e['status']=='QUALIFIED');check(e['qualifiedAt'] is not None);check(e['validUntil'] is not None)
check(l.call('/dashboard')['counts']['QUALIFIED']==1);check(len(l.call(ep)['attempts'])==2)
# New published version cannot overwrite old course content or results.
n=w.call(cp+'/commands/revise','POST',cmd(current()));check(n['edition']==2);np='/courses/'+str(n['id']);nb=dict(body,title='TEST 工位交接培训第二版',version=n['version']);nb['questions'][0]['correctAnswer']=2
n=w.call(np,'PUT',nb);n=r.call(np+'/commands/publish','POST',cmd(n));check(current()['status']=='ARCHIVED');check(l.call(ep)['course']['edition']==1);check(l.call(ep)['record']['bestScore']==100)
check(any(e['action']=='enrollments:exam' for e in w.call('/audit')))
state=dict(learner=ln,manager=wn,reviewer=rn,other=on,password=password,enrollment=e['id'],course=cid,newCourse=n['id'],department=d)
fd=os.open(statefile,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out)
print(json.dumps({'mysqlChecks':checks,'result':'PASS','fixtures':'TEST only'}))
