# OpenWeights privacy policy

Last updated 2026-09-21.

OpenWeights runs language models on your phone. There is no OpenWeights account, no
OpenWeights server, and no analytics or crash reporting of any kind. Nothing in this app
reports back to its developer.

That is the short version and it is true, but it is not the whole story, because the app can
reach the internet on your behalf. This document says exactly when, and what goes.

## What stays on your device

- Your conversations: what you typed, what the model replied, and the files you attached.
- The models you download, shared generation settings, and model-specific runtime choices.
- Your usage totals: tokens, speed, and time spent generating.
- The contents of any folder you share with the app, and anything the assistant reads from
  it.

OpenWeights does not upload this data on its own. The explicit network and sharing actions
below can send parts of it. Android's automatic backup and device-to-device transfer are
switched off for app-private data. Uninstalling removes app-private data, not files in a
folder you shared through Android's picker or copies you sent to another app.

## What leaves your device, and when

| What | When | Where it goes |
|---|---|---|
| A search term you type in Discover | When you search for a model | Hugging Face |
| A repository or file name | When you open or download a model | Hugging Face, and its content delivery network |
| Your Hugging Face access token, if you set one | With every Hugging Face request | Hugging Face |
| A search query the assistant composed | Whenever it uses `web_search` | Enabled search providers: DuckDuckGo, Brave and Yahoo, stopping at the first answer; Context7 when documentation search is enabled |
| A picture or video search query | Whenever it uses `show_pictures` | DuckDuckGo |
| Requests for remote thumbnails or publisher avatars | When those images are displayed and not already cached | The public image hosts named by those results |
| A web address the assistant chose, and the request for it | Whenever it uses `fetch_url` | Whichever public site the address names |
| Search traffic through a configured proxy | Only when you configure a search proxy | That proxy as well as the selected search provider |
| A generated canvas page | Only after you confirm Open in browser | Your chosen browser; the page can then navigate to internet sites outside OpenWeights' controls |
| A report you wrote about a reply | Only if you tap Report and then pick an app to send it to | Wherever you chose to send it |

Every request above also carries the ordinary information any web request carries, including
your IP address, which the receiving service handles under its own policy.

**The three assistant network tools have individual switches.** A fresh install enables
only `web_search`; `show_pictures` and `fetch_url` are off until you enable them. Existing
choices are preserved. The assistant composes queries and addresses from the conversation,
so they can contain what you typed or attached. Every call is recorded in its reply.
Turning a tool off prevents new calls, but does not erase existing results: displaying a
previous picture result can still load its thumbnail.

In Auto mode, fetching an address chosen by the model after reading untrusted content needs
approval. Searches have configured destinations, so untrusted text alone does not impose
that particular check. Once the conversation carries private tool data, every outbound tool
call also needs approval. These flags survive later turns, compaction and branches that
carry the relevant history. Scheduled watches persist their summary's provenance, rather
than treating earlier tool output as trusted system instructions.

Memory writes and creating a watch always need approval. Durable writes after untrusted
content also need approval, including a page fetch that saves into your shared folder.
These safeguards remain in effect in `/yolo` mode.

**Reporting a reply.** Every model reply has a report action. It asks what was wrong,
takes an optional note, and shows you the whole report before anything happens: the model
name, the reason, your note, and the reply itself. The app does not store it and has
nowhere of its own to send it. Tapping Report hands the text to Android's share sheet, and
where it goes from there is your choice: a mail, an issue, your own notes, or nothing at
all. Backing out of that sheet sends nothing, and nothing is kept behind.

**Files you share.** The folder is not uploaded automatically. The assistant can include
private data in a query or address, so Auto asks before outbound calls once it has read
private tool data. `/yolo` waives that network check. File-edit exemptions apply only to
matching documents created in the current session and folder. Folder changes, deletion
and observable document changes invalidate them; Android document providers determine what
identity and modification metadata the app can observe.

**Opening a canvas elsewhere.** The in-app preview blocks external requests and navigation.
An external browser is outside those controls. The app warns and asks on every launch
because a generated page can navigate to the internet and send data from its canvas folder.

**The `/yolo` exception.** This process-only mode waives Auto's two network checks, so private
data can leave through an enabled tool without another prompt and untrusted content can
choose a fetched address. It does not enable disabled tools, bypass file-identity checks,
or waive memory, watch-creation and untrusted durable-write approvals. Every call remains
visible in the reply.

## Third parties

- **Hugging Face** ([privacy policy](https://huggingface.co/privacy)) receives your model
  searches and downloads, and your access token if you set one.
- **DuckDuckGo** ([privacy policy](https://duckduckgo.com/privacy)),
  **Brave Search** ([privacy policy](https://search.brave.com/help/privacy-policy)) and
  **Yahoo** ([privacy policy](https://legal.yahoo.com/us/en/yahoo/privacy/index.html))
  receive searches when their enabled provider is reached.
- **Context7**, when documentation search is enabled, receives the search query. A search
  proxy you configure can also observe that search traffic.
- **Public page and image hosts** receive requests for the addresses read or displayed.
- **Your chosen browser or sharing app** controls data you explicitly hand over to it.

Data these services hold as a result of your requests is subject to their policies, not this
one.

## Your Hugging Face token

Setting a token is optional; public repositories work without one. If you set one it is
encrypted with a key held in the Android Keystore, is attached only to requests to Hugging
Face, is never written to a log, and is deleted when you remove it or uninstall the app.

## Permissions

- **Internet.** For everything in the table above.
- **Notifications.** To tell you a reply has finished and to show download progress. Asked
  for the first time you send a message or start a download. Declining costs you nothing but
  the notifications.
- **Microphone.** For dictation, and only when you tap the microphone. Speech is transcribed
  by the recogniser on your device; the app requests on-device recognition and does not use
  the online kind. No audio is stored or sent by this app.
- **Read-aloud.** Android text to speech receives the text locally. OpenWeights selects an
  installed voice that the service reports does not require a network connection and refuses
  unavailable languages rather than falling back online. This trusts the selected Android
  speech service's metadata; the app cannot sandbox a third-party speech service.
- **Foreground service.** So a model download keeps running when you leave the app.

The app asks for no storage permission. Folders and files reach it only through Android's own
picker, one you chose at a time, and access can be revoked in system settings without
uninstalling.

## Children

OpenWeights is not directed at children. It runs models published by third parties, whose
output is not controlled by this app.

## Deleting your data

Delete a conversation to remove it and its app-private attachments. Delete a model to remove
its weights. Uninstalling removes app-private data, including your token. Shared-folder files,
exports, and copies sent to other apps remain where you put them. OpenWeights has no server
copy to delete; third parties handle requests sent to them under their own policies.

## Changes

If this policy changes, the date at the top changes with it, and a version that changes what
leaves the device will say so in the app's release notes.

## Contact

Open an issue at https://github.com/ExperimentalMachines/openweights.
