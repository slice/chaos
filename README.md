# chaos

[![Build Status](https://github.com/slice/chaos/workflows/tests/badge.svg)](https://github.com/slice/chaos/actions?query=workflow%3Atests)

chaos is a purely functional Discord build scraper written in [Scala],
leveraging [cats], [cats-effect], [fs2], and [http4s]. It aims to be correct and
highly flexible.

[cats]: https://typelevel.org/cats
[cats-effect]: https://typelevel.org/cats-effect
[fs2]: https://fs2.io
[scala]: https://www.scala-lang.org
[http4s]: https://http4s.org
[sbt]: https://www.scala-sbt.org
[hocon]: https://github.com/lightbend/config/blob/master/HOCON.md

## Requirements

- JDK 8
  - Newer versions may work, but the code is only tested with JDK 8.
- sbt ([instructions](https://www.scala-sbt.org/1.x/docs/Setup.html))

## Compiling

After cloning the code, run `sbt assembly`:

```sh
$ git clone https://github.com/slice/chaos.git && cd chaos
$ sbt assembly
```

This will compile a self-contained JAR file that contains all of the program
bytecode and dependencies.

## Operation

chaos works by repeatedly fetching _builds_ from _sources_ at a user-specified
interval, extracting _metadata_ from those builds, and publishing them to
various _publishers_.

For example, the typical use case usually involves scraping from a Discord
frontend source and publishing builds to a Discord webhook publisher.

You may find this short overview helpful:

<dl>
  <dt>source</dt>
  <dd>something that can be scraped for builds</dd>

  <dt>build</dt>
  <dd>an object representing a deployed version of something</dd>

  <dt>publisher</dt>
  <dd>something that may receive builds</dd>
</dl>

## Configuration

chaos uses [HOCON] syntax for configuration. Create your configuration file,
`chaos.conf`:

```yaml
# The interval to poll Discord at. Please be courteous when setting this value.
# Supported units: second(s), minute(s), hour(s), day(s)
interval: 5 minutes

# The path of the state file. Here, chaos is able to save the last build it saw,
# so it doesn't publish the same build twice.
state_file_path: './state.chaos'

publishers: [
  # Let's add a `discord` publisher to publish new builds to a Discord webhook.
  #
  # Build metadata is contained within a color-coded embed with the data laid
  # out nicely.
  {
    type: "discord"

    # The webhook ID and token can be extracted from the URL that you get from
    # the client:
    id: "676263368713306135"
    token: "M6Reo4r2AUJjtIImz-H_pnZlKDS_Q5DNNc_9qRCnYM7jTP9dgoc1-qg7YXIe9JbNvzOL"

    # Only publish new Canary builds to this publisher:
    scrape: ["fe:canary"]
  },

  # Let's also output _all_ new frontend builds to `stdout` for good measure:
  {
    type: "stdout"
    format: "o/ Detected a new build for $branch! (build number: $number)"

    # For this publisher, we care about all frontend branches:
    scrape: ["fe:*"]
  }
]
```

### Selectors

You can select which sources to poll from using _selectors_ specified in the
`scrape` keys of your publishers. chaos will parse these selectors and compute
the sources to poll from, only polling builds from the necessary sources.

Syntactically, selectors are composed of a source name and variant name,
separated by a colon with no spaces.

For example, the above configuration would scrape from all frontend branches
because `fe:*` was selected in the `stdout` publisher. Because `fe:*` is a
superset of `fe:canary`, all frontend branches would be scraped. If the `stdout`
publisher was omitted, then only the Canary frontend would be scraped.

In the selector `fe:canary`, `fe` refers to the _build source_ (or simply
_source_) while `canary` refers to the _variant_ associated with that source.
The `fe` source has all of the frontend branches as variants: `canary`, `ptb`,
and `stable`.

#### Selector wildcards

You can select all possible variants for a source by using the `*` wildcard, as
in `fe:*`.

#### Multiple selectors

You can also specify more than one variant for a source by using curly braces,
as in `fe:{canary,stable}`.

#### Examples

- `fe:canary`: Scrapes Canary frontend
- `fe:{canary,stable}`: Scrapes both Stable and Canary frontend
- `fe:*`: Scrapes all frontend branches (equivalent to `fe:{canary,ptb,stable}`)

## Running

Now, run chaos with `java`:

```sh
$ java -Dconfig.file=chaos.conf -jar target/scala-*/chaos-assembly-*.jar
```

It will run forever, polling Discord and publishing new builds as they are
detected.

The state file is updated as new builds are detected and published. This makes
sure that builds aren't published more than once, even across restarts of the
program.

### systemd

Since chaos runs forever, it would be appropriate to have it live in a systemd
unit:

```ini
[Unit]
Description=Discord build poller
After=network-online.target

[Service]
User=chaos
Group=chaos
WorkingDirectory=~
ExecStart=/usr/bin/java \
  -Dconfig.file=/home/chaos/chaos.conf \
  -jar /home/chaos/target/scala-2.13/chaos-assembly-0.0.0.jar

[Install]
WantedBy=multi-user.target
```

Ensure that the path to the JAR file is correct before enabling the unit:

```sh
$ sudo mv chaos.service /etc/systemd/system/
$ sudo systemctl enable --now chaos.service
```

---

## Publishers

### `discord`

```yaml
{
  type: "discord"
  id: "676263368713306135"
  token: "M6Reo4r2AUJjtIImz-H_pnZlKDS_Q5DNNc_9qRCnYM7jTP9dgoc1-qg7YXIe9JbNvzOL"
}
```

Posts new builds to a Discord webhook as an embed.

To extract the ID and token from a webhook URL, consult this format:

```
https://discordapp.com/api/webhooks/$id/$token
```

### `webhook`

```yaml
{
  type: "webhook"
  uri: "https://..."
}
```

Publishes new builds to a webhook as JSON.

### `stdout`

```yaml
{
  type: "stdout"
  format: "Hey, there's a new build for $branch! (build number: $number)"
}
```

Prints new builds to `stdout` through a format string. Available variables:

<table>
  <tr>
    <th>Variable</th>
    <th>Value</th>
  </tr>

  <tr>
    <td><code>$branch</code></td>
    <td>The name of the branch that the build is from.
    Either <code>Canary</code>, <code>PTB</code>, or <code>Stable</code>.
    </td>
  </tr>

  <tr>
    <td><code>$number</code></td>
    <td>The numerical representation of the version of this build. For
    frontend builds, this will simply be the "Build Number". For host builds,
    this will be the last numerical component of the version number (i.e.
    <code>Z</code> in <code>X.Y.Z</code>). If chaos is unable to parse the last
    numerical component, the internal Java hash code of the version string
    itself will be used.</td>
  </tr>

  <tr>
    <td><code>$version</code></td>
    <td>The version of this build. For frontend builds, this will be the
    "Build Number". For host builds, this will be the entire version string,
    e.g. <code>0.0.450</code>.</td>
  </tr>

  <tr>
    <td><code>$is_revert</code></td>
    <td>A boolean string (<code>true</code> or <code>false</code>)
    indicating if this build was previously deployed.</td>
  </tr>

  <tr align="center">
    <td colspan="2"><b>Frontend Builds</b></td>
  </tr>

  <tr>
    <td><code>$hash</code></td>
    <td>The hash ("Version Hash") of the frontend build. This hash is provided
    by Discord.</td>
  </tr>

  <tr>
    <td><code>$asset_filename_list</td></code>
    <td>A list of all build asset filenames, separated by a comma and a space.
    </td>
  </tr>

  <tr align="center">
    <td colspan="2"><b>Host Builds</b></td>
  </tr>

  <tr>
    <td><code>$platform</code></td>
    <td>The platform for this host build. Either <code>Windows</code>,
    <code>Mac</code>, or <code>Linux</code>.</td>
  </tr>

  <tr>
    <td><code>$pub_date</code></td>
    <td>The Discord-provided date that this host build was published at.</td>
  </tr>

  <tr>
    <td><code>$url</code></td>
    <td>The URL to this host build. Discord only provides a URL for Mac host
    builds, so chaos generates the URL itself for all platforms and branches.
    </td>
  </tr>

  <tr>
    <td><code>$notes</code></td>
    <td>Any notes provided with this build. If no notes are present (which is
    almost always the case), this will be <code>&lt;none&gt;</code>.</td>
  </tr>
</table>

All logs are sent to `stderr`, so feel free to pipe `stdout` to anywhere you'd
like when using the `stdout` publisher.

## Sources

Sources (and their variants) are selected through selectors. See
[the section on selectors](#selectors) for more information.

### `fe`

`fe` (short for "frontend") refers to the online client (accessible at
https://discordapp.com/channels/@me). Keep in mind that the desktop app acts as
a not-so-thin wrapper around the online client, enabling additional features
such as push-to-talk.

Metadata is extracted via crude regex matching of the script and style tags. The
build number and version hash is extracted from the entrypoint script.

Variants, in order from least stable to most stable:

- `canary`, `c`: Canary
  - Bleeding edge builds; deployed to most often.
  - Usually has the latest features, but can be broken sometimes.
- `ptb`, `p`: PTB (Public Test Build)
  - "Beta" builds. Usually stable enough.
- `stable`, `s`: Stable
  - The primary branch that the majority of users are on; deployed to the least.

### `host`

`host` refers to the Discord host, which is the wrapper against the online
client that enables a multitude additional features. In other words, it's the
desktop application.

This source makes requests to
`https://discordapp.com/api/updates/$BRANCH?platform=$PLATFORM`.

The variant is specified as a platform and a branch, separated by a dash. See
the `fe` source for all branches.

Platforms:

- `osx`, `mac`, `macos`: macOS
- `win`, `windows`: Windows
- `linux`: Linux

Examples:

- `host:mac-*`: Scrapes all branches for Mac host builds.
- `host:*-canary`: Scrapes all platforms for Canary host builds.
- `host:{mac,linux}-{canary,stable}`: Scrapes Canary and Stable host builds for
  Mac and Linux.

### `courgette`

`courgette` fetches [Courgette] update manifests published for the Discord host.

Update manifests are fetched from
`https://discord.com/api/updates/distributions/app/manifests/latest?channel=$BRANCH&platform=$PLATFORM&arch=$ARCH`.

Architectures:

- `x86`
- `x64`
- `armv7`
- `armv8`

Examples:

- `courgette:win-canary-x86`: Scrapes Courgette update manifests for x86 Windows
  on the Canary branch.

[courgette]:
  https://www.chromium.org/developers/design-documents/software-updates-courgette

## Heuristics

### Revert detection

Reverts are detected by checking if the build number has decreased instead of
increased. For example, if build 25000 was deployed after build 26000, that
would be detected as a revert. We assume that a lower build number means that
the build is older and was previously deployed.

## Jargon

### "build"

An instance of deployed code, typically with an associated build number and
metadata. It is produced by a source according to the variant.

### "metadata"

The information associated with a build, e.g. the build number, hash, and any
relevant assets.

### "build source", "source"

Something from which builds can be scraped from. Has variants that may be
selected. [See the list of all sources.](#sources)

### "selector"

A string that specifies a source alongside any desired variants.
[See the help section on selectors.](#selectors)

For example, `fe:canary` is a selector that selects the `canary` variant from
the `fe` source.

### "variant"

A variant associated with a source. Usually, the purpose of a variant is to
narrow down the builds emitted from a source, ultimately determining the variant
of builds emitted from that source. For example, the Discord frontend source
(`fe`) has a variant for each branch, so the poller knows which branch to make
requests to.

### "publisher"

An arbitrary "endpoint" to which builds may be published to.
[See the list of all publishers.](#publishers)
