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

## Usage

Install [sbt], then run `sbt assembly`. This will compile the source code and
assemble a self-contained JAR file with all bytecode and dependencies.

chaos works by repeatedly fetching the Discord client at a user-specified
interval and extracting metadata from the client HTML and scripts. This metadata
can then be published to various consumers, such as `stdout` or a Discord
webhook.

All logs are sent to `stderr`, so feel free to pipe `stdout` to anywhere you'd
like when using the `stdout` publisher.

chaos uses [HOCON] for configuration. Create your configuration file,
`chaos.conf`:

```conf
# The interval to poll Discord at. Please be courteous when setting this value.
# Supported units: second(s), minute(s), hour(s), day(s)
interval: 1 minute

publishers: [
  # Let's add a `discord` publisher to publish new builds to a Discord webhook.
  {
    type: "discord"
    id: "676263368713306135"
    token: "M6Reo4r2AUJjtIImz-H_pnZlKDS_Q5DNNc_9qRCnYM7jTP9dgoc1-qg7YXIe9JbNvzOL"
  },

  # Let's also output new builds to `stdout`:
  {
    type: "stdout"
    format: "Hey, there's a new build for $branch! (build number: $build_number)"
  }
]
```

At the moment, all branches (stable, canary, and PTB) will be polled. This is
planned to be configurable soon.

Now, run chaos with `java`:

```sh
$ java -Dconfig.file=chaos.conf -jar target/scala-*/chaos-assembly-*.jar
```

It will run forever, polling Discord and publishing new builds as they are
detected.

## Publishers

### `discord`

```conf
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

```conf
{
  type: "stdout"
  format: "Hey, there's a new build for $branch! (build number: $build_number)"
}
```

Prints new builds to `stdout` through a format string. Available variables:

| Variable               | Value                                                  |
| ---------------------- | ------------------------------------------------------ |
| `$branch`              | The name of the branch that the build is from.         |
| `$build_number`        | The build number of the build.                         |
| `$asset_filename_list` | A list of all build asset filenames, separated by `,`. |
