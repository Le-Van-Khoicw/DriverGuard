"""Publish the online thesis APK to GitHub Releases using local Git credentials.

Never print credentials. The app binary contains Firebase client configuration,
not backend service account or Supabase service role credentials.
"""
import json
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

root = Path(__file__).resolve().parent.parent
version = sys.argv[1]
apk = root / 'Mobile/DriverGuard/app/build/outputs/online/DriverGuard-online.apk'
if not apk.is_file():
    sys.exit('Build the online APK first.')
credential = subprocess.run(
    ['git', 'credential', 'fill'], cwd=root, input='protocol=https\nhost=github.com\nusername=Le-Van-Khoicw\n\n',
    text=True, capture_output=True, check=True,
)
fields = dict(line.split('=', 1) for line in credential.stdout.splitlines() if '=' in line)
token = fields.get('password')
if not token:
    sys.exit('GitHub credentials are unavailable.')
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json',
           'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'DriverGuard-release'}
api = 'https://api.github.com/repos/Le-Van-Khoicw/DriverGuard'

def request(url, data=None, content_type='application/json'):
    req = urllib.request.Request(url, data=data, headers={**headers, 'Content-Type': content_type})
    with urllib.request.urlopen(req, timeout=180) as response:
        return json.load(response)

try:
    try:
        release = request(f'{api}/releases/tags/v{urllib.parse.quote(version)}')
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
        release = request(f'{api}/releases', json.dumps({
            'tag_name': f'v{version}', 'target_commitish': commit, 'name': f'DriverGuard Android {version}',
            'body': 'Android thesis build. Supports online Firebase/Render services, photo and PDF attachments. '
                    'Install DriverGuard.apk on Android 8 or later. This is a debug-signed evaluation build, not a Play Store release.',
            'draft': False, 'prerelease': True,
        }).encode())
    asset_name = 'DriverGuard.apk'
    asset = next((a for a in release['assets'] if a['name'] == asset_name), None)
    if asset:
        sys.exit('An APK already exists for this version. Use a new version to avoid sharing stale builds.')
    upload = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(asset_name)
    asset = request(upload, apk.read_bytes(), 'application/vnd.android.package-archive')
    print(asset['browser_download_url'])
except urllib.error.HTTPError as error:
    sys.exit(f'GitHub release request failed (HTTP {error.code}). Check repository permissions; no credentials were printed.')
