# Privacy Policy

maekster does not contain advertisements, does not collect personal data and has no telemetry. There is no maekster server for any data to be sent to.

The only data stored on your device is information about the music on the servers you choose to connect to, and what the features below save for offline use. Data sent to those servers is subject to that server's privacy policy.

## Optional third-party services

Each of these is off until you turn it on, and makes no request while off. None of them ever receives your server address, credentials or custom headers.

| Service | Hosts | When | What is sent |
|---|---|---|---|
| LRCLIB lyrics | `lrclib.net` | Lyrics, when "Allow LRCLIB" is on | Track title, artist, album and duration |
| ListenBrainz | `api.listenbrainz.org`, or the server you enter | When you connect a ListenBrainz token | Your token and the songs you listen to (title, artist, album, duration, time) |
| Fresh releases | `musicbrainz.org` | On Discover, when "Fresh releases" is on, about once a day while online | Names and MusicBrainz IDs of album artists in your library, and a date range |
| Fresh releases covers | `coverartarchive.org` (which may redirect to `archive.org`) | When "Fresh releases" is on and showing releases | The MusicBrainz release group ID of each cover shown |
| Photos of recommended artists | `musicbrainz.org`, `www.wikidata.org`, `commons.wikimedia.org` (which redirects to `upload.wikimedia.org`) | When "Photos of recommended artists" is on, online, and a Similar artists shelf shows artists outside your library | The MusicBrainz ID of each such artist, or its name when there is no ID; then the Wikidata item and Commons file name found for it |

## Optional self-hosted AudioMuse

AudioMuse settings are saved separately for each music server. Its token is encrypted on the device.
Saving or removing these settings makes no network request. When AudioMuse features are used, requests
will go only to the configured AudioMuse host and contain its own token, song IDs, search text and mood
choices. Music server credentials and custom headers are never sent to AudioMuse. Requests are made only
when you start something that needs AudioMuse: tapping a mood radio on Discover sends that mood's fixed
English search text (for example "late night calm atmospheric"); the songs it returns are matched against
your local library on the device. Song Path does not make requests yet.

Opening a fresh release takes you to its page on `musicbrainz.org` in your browser.

## Contact

If you have any questions about this policy, you may contact me at: subtracks(@)functionkey(.)xyz
