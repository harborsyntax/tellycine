"""Google Play publishing, for the Release and Google Play check workflows.

    python .github/scripts/play.py notes                 # check the Play changelog for this versionCode
    python .github/scripts/play.py check                 # read-only: sign in and list the tracks
    python .github/scripts/play.py upload AAB --track T  # upload the bundle and send it for review on track T

The service account key comes from the PLAY_SERVICE_ACCOUNT_JSON environment variable, as downloaded
or base64-encoded. `notes` needs only the standard library; the others need requirements.txt.
"""
import argparse
import base64
import json
import os
import re
import sys
from pathlib import Path

PACKAGE = "app.tellycine"
LANGUAGE = "en-GB"
ROOT = Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app" / "build.gradle.kts"
CHANGELOGS = ROOT / "fastlane" / "metadata" / "android" / LANGUAGE / "changelogs"
NOTES_LIMIT = 500  # Play's limit for "What's new" text

_summary = open(os.environ.get("GITHUB_STEP_SUMMARY") or os.devnull, "a", encoding="utf-8")


def out(line=""):
    print(line, flush=True)
    _summary.write(line + "\n")


def fail(message):
    print(f"::error::{message}", flush=True)
    _summary.write(f"\n**Failed:** {message}\n")
    sys.exit(1)


def version():
    """versionCode and versionName from app/build.gradle.kts."""
    text = GRADLE.read_text(encoding="utf-8")
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code or not name:
        fail("Couldn't find versionCode and versionName in app/build.gradle.kts.")
    return int(code.group(1)), name.group(1)


def release_notes(code):
    path = CHANGELOGS / f"{code}.txt"
    shown = path.relative_to(ROOT)
    if not path.is_file():
        fail(f"{shown} is missing. Add Google Play's \"What's new\" text for versionCode {code}, up to {NOTES_LIMIT} characters.")
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        fail(f"{shown} is empty.")
    if len(text) > NOTES_LIMIT:
        fail(f"{shown} has {len(text)} characters; Google Play takes up to {NOTES_LIMIT}.")
    return text


def load_key():
    raw = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        fail("The PLAY_SERVICE_ACCOUNT_JSON secret is missing or empty. Add the service account's JSON key under Settings > Secrets and variables > Actions.")

    def parse(text):
        # strict=False lets real line breaks inside the private key through, as some editors add them.
        try:
            return json.loads(text, strict=False), None
        except ValueError as e:
            return None, e

    key, error = parse(raw)
    if key is None and not raw.startswith("{"):
        # Also take the key base64-encoded, the way the keystore secret is stored.
        try:
            key, _ = parse(base64.b64decode(raw).decode("utf-8").strip())
        except Exception:
            pass
    if key is None:
        # Describe the secret's shape without showing any of it.
        shape = ", ".join([
            f"{len(raw)} characters",
            "starts with {" if raw.startswith("{") else "doesn't start with {",
            "ends with }" if raw.endswith("}") else "doesn't end with }",
        ])
        fail(f"PLAY_SERVICE_ACCOUNT_JSON isn't valid JSON ({error}). The secret has {shape}. Paste the whole key file as downloaded, braces included.")
    if not isinstance(key, dict) or key.get("type") != "service_account" or "private_key" not in key or "client_email" not in key:
        fail("PLAY_SERVICE_ACCOUNT_JSON is JSON but not a service account key (it needs type, client_email and private_key).")
    return key


def connect(key):
    """The Play Developer API's edits, signed in as the service account."""
    from google.auth.exceptions import RefreshError
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    creds = service_account.Credentials.from_service_account_info(
        key, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    try:
        creds.refresh(Request())
    except RefreshError as e:
        fail(f"Google refused the key ({e}). It may have been deleted, or the service account disabled: create a new JSON key and update the secret.")
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()


def hint(status, message):
    lower = message.lower()
    if "has not been used" in lower or "is disabled" in lower:
        return "Turn on the Google Play Android Developer API in the service account's Google Cloud project."
    if "version code" in lower:
        return "Bump versionCode in app/build.gradle.kts."
    if "track" in lower:
        return "Check the track's name: the Google Play check workflow lists them."
    if status in (401, 403):
        return "In Play Console > Users and permissions, check the service account has access to Tellycine, with Release to testing tracks to upload. New permissions can take a while to start working."
    if status == 404:
        return f"This account can't see an app called {PACKAGE} in Play Console."
    return ""


def error_parts(e):
    return e.resp.status, getattr(e, "reason", None) or str(e)


def run(request, doing, retries=3):
    from googleapiclient.errors import HttpError

    try:
        return request.execute(num_retries=retries)
    except HttpError as e:
        status, message = error_parts(e)
        fail(f"{doing} failed ({status}: {message}). {hint(status, message)}".strip())


def discard(edits, edit_id):
    try:
        edits.delete(packageName=PACKAGE, editId=edit_id).execute()
    except Exception:
        pass  # unsaved edits expire on their own


def notes(_args):
    code, name = version()
    text = release_notes(code)
    print(f"Google Play notes for {name} ({code}), {len(text)} of {NOTES_LIMIT} characters:\n{text}")


def check(_args):
    out("## Google Play connection check")
    out()
    key = load_key()
    out(f"- Service account: `{key['client_email']}`")
    out(f"- App: `{PACKAGE}`")
    edits = connect(key)
    out("- Key accepted by Google ✅")
    edit_id = run(edits.insert(packageName=PACKAGE, body={}), "Opening Tellycine in Play Console")["id"]
    out("- Play Console access to the app ✅")
    try:
        tracks = run(edits.tracks().list(packageName=PACKAGE, editId=edit_id), "Reading the release tracks")
        out()
        out("### Tracks")
        out()
        out("| Track (API name) | Release | Version codes | Status |")
        out("|---|---|---|---|")
        for track in tracks.get("tracks", []):
            for release in track.get("releases") or [{}]:
                codes = ", ".join(release.get("versionCodes", [])) or "—"
                out(f"| `{track['track']}` | {release.get('name', '—')} | {codes} | {release.get('status', '—')} |")
        listings = run(edits.listings().list(packageName=PACKAGE, editId=edit_id), "Reading the store listing")
        out()
        out(f"Store listing languages: {', '.join(l['language'] for l in listings.get('listings', [])) or 'none'}")
    finally:
        discard(edits, edit_id)
    out()
    out("Nothing was changed in Play Console: the check opened a temporary edit, read it and threw it away.")


def upload(args):
    from googleapiclient.http import MediaFileUpload

    code, name = version()
    text = release_notes(code)
    aab = Path(args.aab)
    if not aab.is_file():
        fail(f"{aab} doesn't exist.")
    track = args.track
    out(f"## Google Play: {name} ({code}) to `{track}`")
    out()

    edits = connect(load_key())
    edit_id = run(edits.insert(packageName=PACKAGE, body={}), "Opening Tellycine in Play Console")["id"]
    saved = False
    try:
        # Re-runs, and versions already uploaded by hand, pick up where they left off.
        current = run(edits.tracks().get(packageName=PACKAGE, editId=edit_id, track=track), f"Reading the {track} track")
        if any(str(code) in r.get("versionCodes", []) for r in current.get("releases", [])):
            out(f"Version {code} is already on `{track}`; nothing to do.")
            return
        bundles = run(edits.bundles().list(packageName=PACKAGE, editId=edit_id), "Listing the uploaded bundles")
        if any(b.get("versionCode") == code for b in bundles.get("bundles", [])):
            out(f"- Bundle {code} is already in Play Console; using it")
        else:
            media = MediaFileUpload(str(aab), mimetype="application/octet-stream", resumable=True)
            bundle = run(edits.bundles().upload(packageName=PACKAGE, editId=edit_id, media_body=media), "Uploading the app bundle")
            if bundle.get("versionCode") != code:
                fail(f"The uploaded bundle has versionCode {bundle.get('versionCode')}, but app/build.gradle.kts says {code}.")
            out(f"- Uploaded `{aab.name}` ✅")

        release = {
            "name": f"{code} ({name})",
            "versionCodes": [str(code)],
            "status": "completed",
            "releaseNotes": [{"language": LANGUAGE, "text": text}],
        }
        run(edits.tracks().update(packageName=PACKAGE, editId=edit_id, track=track,
                                  body={"track": track, "releases": [release]}),
            f"Adding the release to {track}")
        out(f"- Release {release['name']} added to `{track}`, with the notes from `{code}.txt` ✅")
        saved = commit(edits, edit_id)
    finally:
        if not saved:
            discard(edits, edit_id)


def commit(edits, edit_id):
    from googleapiclient.errors import HttpError

    try:
        # No retries: a repeated commit fails once the first has gone through.
        edits.commit(packageName=PACKAGE, editId=edit_id).execute()
        out("- Sent for review ✅ It reaches testers once Google approves it.")
        return True
    except HttpError as e:
        status, message = error_parts(e)
        if "changesNotSentForReview" not in message:
            fail(f"Saving the release failed ({status}: {message}). {hint(status, message)}".strip())
    # Play Console sometimes won't take changes for review from the API (after a rejection, say).
    # Save them anyway and have them sent from Play Console.
    run(edits.commit(packageName=PACKAGE, editId=edit_id, changesNotSentForReview=True), "Saving the release", retries=0)
    message = "Saved, but Play Console wouldn't let it be sent for review automatically: open Publishing overview in Play Console and send the changes for review."
    print(f"::warning::{message}", flush=True)
    out(f"- {message}")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("notes", help="check the Play changelog for this versionCode").set_defaults(func=notes)
    commands.add_parser("check", help="sign in and list the tracks, changing nothing").set_defaults(func=check)
    up = commands.add_parser("upload", help="upload an app bundle and send it for review")
    up.add_argument("aab")
    up.add_argument("--track", required=True)
    up.set_defaults(func=upload)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
