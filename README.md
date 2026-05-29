<div align="center">
  <img src="assets/logo/logo-square-512.png" width="250" alt="Speed of Sound logo">
</div>

<div align="center">

[![Version](https://img.shields.io/github/v/release/zugaldia/speedofsound)](https://github.com/zugaldia/speedofsound/releases)
[![Build](https://github.com/zugaldia/speedofsound/actions/workflows/build.yml/badge.svg)](https://github.com/zugaldia/speedofsound/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Platform](https://img.shields.io/badge/platform-Linux-lightgrey)

</div>

# Speed of Sound

Voice typing for the Linux desktop:

<div align="center">
  <img src="docs/assets/videos/demo-light.gif" alt="Speed of Sound typing into a text editor">
</div>

## Features

- Offline, on-device transcription powered by Whisper, Parakeet, Canary, and more. No data leaves your machine.
- Multiple activation options: click the in-app button, use a global keyboard shortcut, or control the app from the system tray.
- Types the result directly into any focused application using Portals for wide desktop support (X11, Wayland).
- Configurable repeating alarms with user-defined names, per-alarm weekday selection, a limit, resume catch-up, and per-alarm notification urgency, backed by desktop notifications. Import/export includes the alarm scheduler state.
- Multi-language support with switchable primary and secondary languages on the fly.
- Works out of the box with a built-in multilingual Whisper model. Download additional models from within the app to improve accuracy and language coverage.
- *Optional* text polishing with LLMs (Anthropic, Google, OpenAI), with support for a custom context and vocabulary.
- Supports self-hosted services like vLLM, Ollama, and llama.cpp (cloud services supported but not required).

## Getting Started

<div style="display: flex; gap: 10px; align-items: center;">
    <a href="https://flathub.org/en/apps/io.speedofsound.SpeedOfSound">
      <img width="240" alt="Get it on Flathub" src="https://flathub.org/api/badge?locale=en"/>
    </a>
    <a href="https://snapcraft.io/speedofsound">
      <img width="260" alt="Get it from the Snap Store" src=https://snapcraft.io/en/dark/install.svg />
    </a>
</div>

The easiest and recommended way to install Speed of Sound is from
[Flathub](https://flathub.org/en/apps/io.speedofsound.SpeedOfSound) or from the
[Snap Store](https://snapcraft.io/speedofsound).
Alternatively, AppImage, Deb, and RPM packages are also available from the [releases page](https://github.com/zugaldia/speedofsound/releases/latest).

For initial configuration, troubleshooting, and other resources, visit [speedofsound.io](https://speedofsound.io).

## Contributing

To build the project from source and learn how to contribute, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Built with

Speed of Sound stands on the shoulders of these excellent open source projects:

- [Java-GI](https://codeberg.org/java-gi/java-gi) — GTK/GNOME bindings for Java, enabling access to native libraries
  (including LibAdwaita and GStreamer) via the modern Panama framework.
- [Sherpa ONNX](https://github.com/k2-fsa/sherpa-onnx) — On-device ASR (and more) using the performant ONNX Runtime,
  with pre-built models for Whisper, Parakeet, Canary, and many other popular models.
- [Whisper](https://github.com/openai/whisper) — OpenAI's open-source speech recognition model.
  Its release transformed the on-device ASR landscape.

Additionally, Speed of Sound uses [Stargate](https://github.com/zugaldia/stargate), a companion project by the same
author that provides JVM applications with high-level access to [XDG Desktop Portals](https://flatpak.github.io/xdg-desktop-portal/docs/index.html)
on Linux. Stargate, in turn, depends on the fantastic [dbus-java](https://github.com/hypfvieh/dbus-java) project.

## Support and Contributions

If you run into any issues, have questions, or need troubleshooting help, please open a ticket on the
[GitHub issues page](https://github.com/zugaldia/speedofsound/issues). Pull requests are also welcome.

When reporting an issue, please include the debug information from the About dialog's Troubleshooting
section, it helps identify your runtime environment and system configuration.

There are several ideas already tracked as tickets to improve the project. Everything planned on the
roadmap has a corresponding issue. If you'd like to contribute, please use those tickets to guide your
work. You can also use GitHub emoji reactions on issues to vote for the ones that matter most to you,
which helps with prioritization.

If you find Speed of Sound useful, consider [sponsoring this work](https://github.com/sponsors/zugaldia).

## Development workflow

- `./gradlew check` runs the standard project checks.
- `make smoke-startup` runs a lightweight startup smoke-check for regression coverage, useful when hard exit paths are touched.
- Run with a custom timeout via `SMOKE_TIMEOUT=30 make smoke-startup`.
- `make smoke-startup-cinnamon` adds an additional startup smoke path that exercises the Cinnamon-compatible remote-desktop portal path.
  - Use `SMOKE_FORCE_REMOTE_SESSION=true` to force portal startup behavior even when the text output method is set to clipboard in the profile.
  - In this mode, the run verifies the startup session attempts to restore a portal session and either starts the remote session or cleanly falls back to clipboard output when the desktop portal is unsupported.
  - Timeout for both smoke checks defaults to `60` seconds.
- `make smoke-startup-ci` runs both startup smoke checks with CI-oriented defaults and is used by GitHub Actions.
  - CI canaries use a single run identifier for both logfiles and enforce a minimum Cinnamon timeout via `SMOKE_TIMEOUT_CI_MIN` (defaults to `60` seconds).
