# Product direction and feature baseline

## The only goal

Smart Hole aims to become a genuinely useful mobile agent. It is not an Android
substitute for desktop DeepSeek Harness and does not copy a desktop interface.
Success is measured by whether people can naturally and reliably delegate work
from a phone throughout the day.

## Current implementation constraint

The goal is currently implemented on Android because complete local access to
files, apps, and the system presently requires root. Root is a technical
condition and execution boundary, not the product identity.

## DSH is the minimum capability baseline

Smart Hole uses the official DeepSeek Harness as its agent engine. Its native
agent capabilities must all be available; otherwise the mobile agent starts
out incomplete. Sessions, models, shell and file tools, search, background
jobs, skills, todos, web, questions and approvals, goals, Plan Mode, subagents,
workflows, Ralph, providers, and plugins form this baseline.

Meeting the baseline only means the underlying agent is complete. Smart Hole
must still solve mobile interaction, context sharing, foreground/background
reliability, notifications, resource control, project and plugin management,
installation, upgrades, and device compatibility. Those qualities determine
whether it is actually a good mobile agent.

## Native compatibility policy

The native layer is split into protocol models, independent feature modules,
and focused Compose components. Goals, todos, Plan Mode, session metrics, and a
recursive subagent tree currently use this path. Subagents expose running state,
duration, token usage, and navigation into their complete conversation.

Smart Hole prioritizes the DSH core and deliberately selected plugin semantics.
It does not promise to render arbitrary Web UI shipped by every marketplace
plugin. Unknown projections remain preserved by the protocol layer and degrade
safely until a native feature module is added.
