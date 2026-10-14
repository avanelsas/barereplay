# Changelog

## 0.5.0

Depends on BareBuild 0.8.0 and BareDOM 4.5.0, rather than 0.7.0 and 3.7.0. The dock is built from
BareDOM components, so it takes the look of BareDOM 4. An app that takes BareDOM through
BareReplay moves to the 4.x line with it.

Adds `barereplay.export`, which writes the recorded log out as EDN without the live elements a
step carries. Nothing requires the namespace, so an app that does not ask for it is unaffected.

## 0.4.0

BareReplay now lives in its own repository, <https://github.com/avanelsas/barereplay>. The Clojars coordinate `com.github.avanelsas/barereplay` is unchanged, so nothing moves for a consumer. Release tags are `vX.Y.Z` rather than `barereplay-vX.Y.Z`.

Adds a collapse button to the dock header that folds the panel down to its title bar and back.

## 0.3.0

Pins BareBuild 0.6.0, which splits `barebuild.utils` into three namespaces. The dock reads the URL projection from `barebuild.utils.url`. No change to the replay behaviour or to the dock itself.

## 0.2.2

Restores the URL projection while replaying, so walking the timeline back drives the URL to each past intent. Pins BareBuild 0.5.0 for the new on-apply consumer hook and BareDOM 3.7.0.

## 0.2.1

Projects reconstructed state only while replaying, not on every live event. The live view and its animations are left untouched during normal use.

## 0.2.0

Reconstructs and projects multiple server-resource independently. A page with several server-resources now replays each on its own timeline.

## 0.1.0

First release. A time-travel replay debugger for BareBuild apps. It records the event log and reconstructs any past state by replaying the pure `step`, then re-projects it onto the live components. Ships a draggable dock with transport controls, a live-updating event timeline, and a request and response data pane. Published to Clojars.
