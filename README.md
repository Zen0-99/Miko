<div align="center">

<a href="https://aniyomi.org">
    <img src="./.github/assets/logo.png" alt="Miko logo" title="Miko" width="80"/>
</a>

# Miko

### A compact, customisable reader for manga, anime, and novels — built on Aniyomi & Yōkai.

Discover, read, and watch — all in one place, with a streamlined interface that gets out of your way.

[![License: Apache-2.0](https://img.shields.io/github/license/aniyomiorg/aniyomi?labelColor=27303D&color=818cf8)](/LICENSE)

## Download

*Requires Android 8.0 or higher.*

> Releases coming soon.

## What is Miko?

Miko started as a fork of [Yōkai](https://github.com/null2264/yokai) — a manga reader based on TachiyomiJ2K and Mihon. Novel support was added to that fork using providers ported from [QuickNovel](https://github.com/LagradOst/QuickNovel). Later, the project moved to an [Aniyomi](https://github.com/aniyomiorg/aniyomi) base to gain anime support, bringing the novel integration along and applying Yōkai's design principles to make the UI/UX cleaner and more compact.

### What changed from Aniyomi

| Area | What changed |
|------|-------------|
| **Novels** | Full novel reading support alongside manga and anime. Novel extensions install from [MikoNovelSources](https://github.com/keypop3750/MikoNovelSources); the extension system (`NovelSource`, `NovelSourceFactory`, `ConfigurableNovelSource`) is based on the Yōkai pattern. |
| **Compact navigation** | The navigation area has been redesigned to be much more compact while preserving all content and functionality. More screen space for what matters. |
| **UI/UX overhaul** | Extensive visual and interaction changes throughout the app — denser layouts, refined typography, and a cleaner browsing experience, inspired by Yōkai's design approach. |
| **Customisability** | More options to tailor the reading and browsing experience to your preferences. |
| **Chapter comments** | Eligible novel extensions can surface per-chapter comments directly in the reader, creating a more intimate and community-driven reading experience. A comments button appears in the reader bottom bar for sources that support it. |

## Novel Extensions

Miko supports novel source extensions that can be installed directly from the app.

**Extension repo URL** — paste this into *Browse → Extensions → Extension Repos*:

```
https://raw.githubusercontent.com/keypop3750/MikoNovelSources/main/index.min.json
```

See the [MikoNovelSources](https://github.com/keypop3750/MikoNovelSources) repository for the full list of available novel sources and extension development guides.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

### Credits

Miko builds on the work of many open-source projects:

- [Aniyomi](https://github.com/aniyomiorg/aniyomi) — the fork this project is based on.
- [Yōkai](https://github.com/null2264/yokai) — the manga reader where novel support was first added; its design principles guided the UI/UX overhaul.
- [QuickNovel](https://github.com/LagradOst/QuickNovel) — the novel provider integrations were ported from here.
- [Mihon](https://github.com/mihonapp/mihon) — the successor to Tachiyomi.
- [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi) — the original manga reader.
- [mpv-android](https://github.com/mpv-android/mpv-android) — the player engine.

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

### License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2024 Aniyomi Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

</div>
