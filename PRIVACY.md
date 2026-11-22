# Privacy Policy

maekster does not contain advertisements, does not collect personal data and has no telemetry. There is no maekster server for any data to be sent to.

The only data stored on your device is information about the music on the servers you choose to connect to, and what the features below save for offline use. Data sent to those servers is subject to that server's privacy policy.

## Optional third-party services

ListenBrainz, AudioMuse, Fresh releases and recommended artist photos are managed in Settings > Integrations.
LRCLIB lyrics are managed in Settings > Lyrics.

LRCLIB lyrics are on by default, as in the iOS app, so that lyrics can follow the music when your server only has
plain lyrics. Turn off "Allow LRCLIB lookups" to use only your server. Every other service is off until you turn it on.
Each service makes no request while off, and none of them ever receives your server address, credentials or custom headers.

| Service | Hosts | When | What is sent |
|---|---|---|---|
| LRCLIB lyrics | `lrclib.net` | While "Allow LRCLIB lookups" is on (the default) and Now Playing shows lyrics (the lyrics pane or the current-line preview): for a song whose server lyrics have no timings or are missing (source Auto), or for every song (source LRCLIB). Results are cached on the device | Track title, artist, album and duration |
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
your local library on the device. Building a Queue Builder queue with moods picked (and refilling it) asks
AudioMuse for the mood scores of up to 400 candidate songs by song ID; scores are kept on the device per
server and reused, and offline only kept scores are used. "Test connection" in the AudioMuse settings sends one fixed English search
to the address and token typed in the form, without saving them. Playing a Song Path sends the two selected
endpoints (song IDs or a mood) and the requested length. The picker searches the local library.

Opening a fresh release takes you to its page on `musicbrainz.org` in your browser.

## Contact

If you have any questions about this policy, you may contact me at: subtracks(@)functionkey(.)xyz
