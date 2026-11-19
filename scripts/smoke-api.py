#!/usr/bin/env python3
"""Verify a real deployment; credentials optional. Never prints passwords or JWTs."""
import os, sys, json, urllib.request, urllib.error, urllib.parse, uuid, datetime
base=os.environ.get('API_URL','').rstrip('/')
parsed=urllib.parse.urlparse(base)
if parsed.scheme!='https' and not (os.environ.get('ALLOW_LOCAL_HTTP')=='true' and parsed.hostname in ('127.0.0.1','localhost')):
    sys.exit('API_URL must be HTTPS (or explicit loopback development)')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args): return None
opener=urllib.request.build_opener(NoRedirect)
def req(method,path,body=None,token=None,expected=200):
    headers={'Accept':'application/json'}
    if body is not None:headers['Content-Type']='application/json'
    if token:headers['Authorization']='Bearer '+token
    request=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:
        response=opener.open(request,timeout=30)
    except urllib.error.HTTPError as e:
        if e.code==expected:return None
        raise RuntimeError('Unexpected HTTP '+str(e.code)+' at '+path) from None
    with response:
        if response.status!=expected:raise RuntimeError('Unexpected status at '+path)
        data=response.read();return json.loads(data) if data else None
assert req('GET','/actuator/health/readiness')['status']=='UP'
req('GET','/api/v1/employees',expected=401)
print('PASS: readiness and anonymous access rejection; transport='+parsed.scheme.upper())
if os.environ.get('SMOKE_EMAIL'):
    session=req('POST','/api/v1/auth/login',{'email':os.environ['SMOKE_EMAIL'],'password':os.environ['SMOKE_PASSWORD']});token=session['accessToken']
    body=dict(firstName='Smoke',lastName='Verification',email='smoke-'+uuid.uuid4().hex+'@example.test',department='Operations',jobTitle='Temporary smoke test',hireDate=datetime.datetime.now(datetime.timezone.utc).date().isoformat(),status='ACTIVE')
    created=req('POST','/api/v1/employees',body,token,201);latest=created
    try:
        assert req('GET','/api/v1/employees/'+created['id'],token=token)['id']==created['id']
        body['jobTitle']='Verified';body['version']=created['version'];latest=req('PUT','/api/v1/employees/'+created['id'],body,token)
        assert latest['jobTitle']=='Verified'
        page=req('GET','/api/v1/employees?q='+urllib.parse.quote(body['email']),token=token)
        assert page['totalElements']==1
        print('PASS: authenticated create/read/update/search')
    finally:
        req('DELETE','/api/v1/employees/'+latest['id']+'?version='+str(latest['version']),token=token,expected=204)
    print('PASS: deletion cleanup')
print('Verified API origin:',base)
