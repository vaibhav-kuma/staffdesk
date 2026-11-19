#!/usr/bin/env python3
"""Deploy the tested Git SHA, then require that exact revision at the public API.
No Render API key needed: a deployment hook is already a secret capability.
"""
import os, urllib.parse, urllib.request, json, time, sys
hook=os.environ['RENDER_DEPLOY_HOOK'];base=os.environ['API_URL'].rstrip('/');revision=os.environ['GITHUB_SHA']
h=urllib.parse.urlsplit(hook);origin=urllib.parse.urlsplit(base)
if h.scheme!='https' or h.hostname!='api.render.com' or not h.path.startswith('/deploy/'):
    sys.exit('Invalid Render deployment hook')
if origin.scheme!='https' or not origin.hostname or origin.username or origin.query or origin.fragment or origin.path:
    sys.exit('API_URL must be a public HTTPS origin')
params=dict(urllib.parse.parse_qsl(h.query));params['ref']=revision
url=urllib.parse.urlunsplit(h._replace(query=urllib.parse.urlencode(params)))
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args):return None
opener=urllib.request.build_opener(NoRedirect)
try:
    with opener.open(urllib.request.Request(url,method='POST'),timeout=30) as response: response.read()
except Exception:sys.exit('Render deploy hook failed. Inspect the provider dashboard (secret URL not logged).')
for _ in range(120):
    try:
        with opener.open(base+'/api/v1/version',timeout=15) as response: body=json.load(response)
        with opener.open(base+'/actuator/health/readiness',timeout=15) as response: health=json.load(response)
        if body.get('revision')==revision and health.get('status')=='UP':
            print('Verified deployed revision',revision,'at',base);break
    except Exception: pass
    time.sleep(10)
else:sys.exit('Timed out waiting for the expected revision to become healthy. Inspect Render deployment logs.')
