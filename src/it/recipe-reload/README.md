# Recipe reload integration test

This opt-in Java 25 test runs a fresh Paper server on loopback with Slimefun,
SensibleToolbox and a test plugin. It copies only server binaries, libraries and
cache from an existing Paper installation. It creates its own configuration and
world; never supply an existing server directory as `--work-dir`.

After building the candidate, run with resource limits appropriate to your host:

```sh
python3 src/it/recipe-reload/run.py \
  --server-home "$PAPER_HOME" --server-jar paper-26.2-133.jar \
  --plugin target/SensibleToolbox-drake-1.22-Drake-SNAPSHOT.jar \
  --slimefun "$SLIMEFUN_JAR" --work-dir "$NEW_EMPTY_FIXTURE_PATH"
```

`JAVA_HOME` must point to Java 25; `--work-dir` must not exist. The server source
needs cached libraries sufficient to compile the probe and run the supplied
Paper jar. Allow up to 300 seconds for the run plus graceful shutdown. Artifacts
and logs remain in the fixture directory for inspection; remove that disposable
directory after retaining the evidence you need. Never install the probe on a
live server.

The probe checks actual Bukkit listener registration and three real
`ServerResourcesReloadedEvent` deliveries, without calling the listener directly:

1. Breaker and Sender match real crafting matrices and produce the expected
   ItemStacks, including their metadata, through Paper's crafting API.
2. Removing only the STB reload listener reproduces the loss of both recipes.
3. Re-registering the listener restores all STB recipe keys and crafting after
   two resource reloads.
4. Repeated recovery preserves an existing recipe using the Sender key.

Exit zero and `summary.json` with `PASS` are required. This exercises the server
crafting API without players; it does not test a player's GUI, permissions,
client recipe synchronization, machines, or coexistence with a full server's
plugin set. The Java 25 candidate is not evidence of Java 21 compatibility.
