# chaos

chaos is a purely functional Discord build scraper written in [Scala]. It uses
[cats], [cats-effect], [fs2], and [http4s].

[cats]: https://typelevel.org/cats
[cats-effect]: https://typelevel.org/cats-effect
[fs2]: https://fs2.io
[scala]: https://www.scala-lang.org
[http4s]: https://http4s.org
[sbt]: https://www.scala-sbt.org
[hocon]: https://github.com/lightbend/config/blob/master/HOCON.md

## Compiling

Make sure you have the Java Development Kit (JDK) installed. Then, install [sbt]
([instructions here](https://www.scala-sbt.org/1.x/docs/Setup.html)). After
cloning, run `sbt assembly`:

```sh
$ git clone https://github.com/slice/chaos.git && cd chaos
$ sbt assembly
```

This will take about a minute to compile the source code and assemble a
self-contained JAR file with all bytecode and dependencies.

## Usage

chaos works by repeatedly fetching the Discord client at a user-specified
interval and extracting metadata from the client HTML and scripts. This metadata
can then be published to various consumers (called "publishers"), e.g. `stdout`
or a Discord webhook.

All logs are sent to `stderr`, so feel free to pipe `stdout` to anywhere you'd
like when using the `stdout` publisher.

## Configuration

chaos uses [HOCON] for configuration. Create your configuration file,
`chaos.conf`:

```yaml
# The interval to poll Discord at. Please be courteous when setting this value.
# Supported units: second(s), minute(s), hour(s), day(s)
interval: 1 minute

publishers: [
  # Let's add a `discord` publisher to publish new builds to a Discord webhook.
  {
    type: "discord"
    id: "676263368713306135"
    token: "M6Reo4r2AUJjtIImz-H_pnZlKDS_Q5DNNc_9qRCnYM7jTP9dgoc1-qg7YXIe9JbNvzOL"

    # Only publish new Canary builds to this publisher.
    # Supported values: "stable", "ptb", "canary"
    branches: ["canary"]
  },

  # Let's also output all new builds to `stdout` for good measure:
  {
    type: "stdout"
    format: "o/ Detected a new build for $branch! (build number: $build_number)"
  }
]
```

chaos will only scrape from the branches that you specify in each publisher. By
default, a publisher will publish from all branches. The above configuration
will scrape from all branches because the `stdout` publisher uses the
aforementioned default value, therefore prompting chaos to scrape from all
branches. If it had `branches: ["canary"]` like the `discord` publisher before
it, then only the Canary branch would ever be polled from, since all publishers
only care about Canary builds.

Now, run chaos with `java`:

```sh
$ java -Dconfig.file=chaos.conf -jar target/scala-*/chaos-assembly-*.jar
```

It will run forever, polling Discord and publishing new builds as they are
detected.

### systemd

Since chaos runs forever, it would appropriate to have it live in a systemd
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

### `stdout`

```yaml
{
  type: "stdout"
  format: "Hey, there's a new build for $branch! (build number: $build_number)"
}
```

Prints new builds to `stdout` through a format string. Available variables:

| Variable               | Value                                                                                    |
| ---------------------- | ---------------------------------------------------------------------------------------- |
| `$branch`              | The name of the branch that the build is from.                                           |
| `$build_number`        | The build number of the build from Discord.                                              |
| `$hash`                | The hash ("Version Hash") of the build from Discord.                                     |
| `$asset_filename_list` | A list of all build asset filenames, separated by `,`.                                   |
| `$is_revert`           | A boolean (`true` or `false`) indicating if this build was previously deployed recently. |
