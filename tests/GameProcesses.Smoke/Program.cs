using System.Collections.Concurrent;
using System.Diagnostics;
using System.Reflection;
using System.Text.Json;
using Luxis;

if (args is ["hold"])
{
    Console.WriteLine("game ready");
    await Task.Delay(TimeSpan.FromMinutes(1));
    return;
}
if (args is ["crash"])
{
    Console.Error.WriteLine("profile-specific startup error");
    Environment.ExitCode = 7;
    return;
}

var root = Directory.CreateTempSubdirectory("luxis-game-processes-").FullName;
var events = new ConcurrentQueue<JsonElement>();
var first = new GameProcesses(root, raw => events.Enqueue(Parse(raw)));
var ids = new List<string>();
try
{
    var a = Launch(first, "Alpha", "hold");
    var b = Launch(first, "Beta", "hold");
    var initial = Parse(first.List());
    Check(initial.GetArrayLength() == 2, "Both running games must be listed.");
    Check(initial[0].GetProperty("pid").GetInt32() != initial[1].GetProperty("pid").GetInt32(), "Each game needs its own PID.");
    var duplicateBlocked = false;
    try { using var unused = first.ReserveDirectory(Path.Combine(root, "Alpha"), "Alpha"); }
    catch (InvalidOperationException) { duplicateBlocked = true; }
    Check(duplicateBlocked, "A live game directory must not be shared.");

    // A fresh registry sees the persistent records, as when the launcher is reopened.
    var reopened = new GameProcesses(root, _ => { });
    Check(Parse(reopened.List()).GetArrayLength() == 2, "Both games must survive a launcher restart.");
    Check(Parse(reopened.Stop(b)).GetProperty("stopped").GetBoolean(), "Must be able to close the chosen game.");
    Check(Parse(reopened.List()).EnumerateArray().Single().GetProperty("id").GetString() == a, "Stopping Beta must leave Alpha running.");
    Check(Parse(reopened.Stop(b)).GetProperty("missing").GetBoolean(), "A stale stop must not target a different game.");
    Check(!events.Any(e => e.GetProperty("type").GetString() == "crash" && e.GetProperty("instance").GetString() == a),
        "The remaining game must not report an exit.");

    var crash = Launch(first, "Crash", "crash");
    var deadline = DateTime.UtcNow.AddSeconds(8);
    JsonElement failure = default;
    while (DateTime.UtcNow < deadline)
    {
        failure = events.FirstOrDefault(e => e.GetProperty("type").GetString() == "crash"
            && e.GetProperty("instance").GetString() == crash);
        if (failure.ValueKind != JsonValueKind.Undefined) break;
        await Task.Delay(50);
    }
    Check(failure.ValueKind != JsonValueKind.Undefined && failure.GetProperty("exitCode").GetInt32() == 7,
        "An immediate failure must emit the correct exit event.");
    Check(failure.GetProperty("tail").GetString().Contains("profile-specific startup error"), "The failed game's own logs must be captured.");
    Check(Parse(first.List()).EnumerateArray().Single().GetProperty("id").GetString() == a,
        "Startup failures must not remove another game from the list.");
    Console.WriteLine("PASS: two independent processes, duplicate-folder protection, restart recovery, targeted stop, and crash output.");
}
finally
{
    foreach (var id in ids) first.Stop(id);
    Directory.Delete(root, recursive: true);
}

string Launch(GameProcesses manager, string profile, string command)
{
    var directory = Path.Combine(root, profile);
    using var reservation = manager.ReserveDirectory(directory, profile);
    var info = new ProcessStartInfo(Environment.ProcessPath!)
    {
        WorkingDirectory = directory,
        UseShellExecute = false,
        RedirectStandardOutput = true,
        RedirectStandardError = true,
        CreateNoWindow = true
    };
    if (Path.GetFileNameWithoutExtension(Environment.ProcessPath) == "dotnet")
        info.ArgumentList.Add(Assembly.GetExecutingAssembly().Location);
    info.ArgumentList.Add(command);
    var id = Guid.NewGuid().ToString("N");
    manager.Start(info, id, profile, profile, "test");
    ids.Add(id);
    return id;
}

static JsonElement Parse(string text) => JsonSerializer.Deserialize<JsonElement>(text);
static void Check(bool ok, string message) { if (!ok) throw new InvalidOperationException(message); }
