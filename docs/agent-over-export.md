# An agent over the export

*2026-09-19 · §0.10 item 17, closed. Tendril talks to a model in exactly one place — §0.6.15's
three verbs, which send the selected text and nothing else. Anything wider than a selection —
"what did I write about the garden this month", "draft next week from my tasks" — is not the
app's job and never will be: the app is offline-first with no telemetry, and a feature that
shipped the whole database to an API would be the opposite of that. The honest interface for an
agent is the export. This page says what to export, where to point the agent, and what it cannot
do.*

## What to export

| export | where | what it carries | what it does not |
|---|---|---|---|
| **Markdown zip** — Settings › *Export as Markdown* (both platforms) | one `.zip` you choose | every live page as a `.md` file at its place in the tree, the title as an H1, spans as Markdown, images beside the page, mentions as relative links, block references as quotes with a *source* link, a Canvas page as a `.canvas` file (JSON Canvas — the zip is an Obsidian vault) | a database's *table*: its rows are pages and come along, the columns and views do not (`MarkdownExporter`'s stated limitation); tasks, habits, check-ins, time logs |
| **Calendar `.ics`** — Settings › *Export .ics* (both platforms) | one `.ics` | tasks and events with their dates, times, recurrence and `PRIORITY` | steps' parents, habits, anything without a date |
| **The sync folder** (§9.4) | the folder you chose in Settings, if any | everything, as JSON: `pages/<uid>.json` (blocks, properties, views), `entries_*.json`, `habits.json`, `habit_completions.json`, `check_ins.json`, `time_logs.json`, `reminders.json`, `page_relations.json` | readable only without a passphrase — with one, the files are ciphertext by design; and it is *live*: never let an agent write into it |
| **The `.tendril` archive** — Settings › *Export* (Android) | one file | the same records as the sync folder, for Restore | a working interface — it exists to be restored, not read |

The Markdown zip is the one to hand over. It is prose an agent reads as prose, its links resolve
inside the folder, and it holds nothing the sync folder needs.

## Pointing an agent at it

Unzip to a folder that is **not** the sync folder and not inside the app's data, open a terminal
there, and start the agent:

```bash
claude
```

Then ask in the folder's terms — *"which pages mention the library, and what did I decide"*,
*"summarise September's Journal days"*, *"list every unchecked to-do across these notes"*. Claude
Code reads files with the same tools it uses on code; nothing about the format is special. Any
agent that reads a directory of Markdown works the same way — Obsidian's own plugins, a local
script, a model run on the machine.

Three rules that follow from the export being a copy:

1. **It is read-only towards Tendril.** The app imports Notion's export format (§7) and `.ics`;
   it does not re-import its own Markdown, so an edit the agent makes to a `.md` file is an edit to
   the copy. Bring a result back by hand — paste it into a page, or into quick add.
2. **It is a snapshot.** Export again after a day of notes; an agent over yesterday's zip answers
   about yesterday.
3. **Where the agent runs is where the notes go.** Claude Code sends what it reads to Anthropic's
   API under your account — the same trust as the AI key in Settings, at a wider scope you chose
   by exporting. An agent that must not leave the machine has to be one that runs on it.

## Why not a verb in the app, and why not a local model

An *Ask about my notes* verb would need the page tree, or the database, on the wire — the
boundary §0.6.15 draws (only the selection) is the reason the verbs exist at all, and the export
keeps that boundary by making the wider scope a deliberate act with a file you can look at.

A local model would need a runtime (llama.cpp, an ONNX runtime, a GGUF file) the offline build
cannot fetch and the phone cannot carry; nothing precludes it, nothing plans it. If one ever
arrives it arrives as the same thing this page describes — something that reads the export.
