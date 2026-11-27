using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text.Json;

namespace Luxis;

/// <summary>Tracks only games started by Luxis, never unrelated Java processes.</summary>
internal sealed class GameProcesses
{
    private sealed record Identity(string Id, string ProfileId, string Name, string Version,
        string Directory, string Executable, int Pid, long StartedUtcTicks);

    private sealed class Entry(Identity identity, Process process)
    {
        public readonly Identity Identity = identity;
        public readonly Process Process = process;
        public readonly ConcurrentQueue<string> Tail = new();
        public int Stopping;
    }

    private readonly ConcurrentDictionary<string, Entry> _entries = new();
    private readonly string _records;
    private readonly Action<string> _emit;

    public GameProcesses(string root, Action<string> emit)
    {
        _records = Path.Combine(root, "running-instances");
        _emit = emit;
        Directory.CreateDirectory(_records);
    }

    public FileStream ReserveDirectory(string directory, string name)
    {
        Directory.CreateDirectory(directory);
        FileStream reservation;
        try { reservation = File.Open(Path.Combine(directory, ".luxis-launch.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); }
        catch (IOException ex) { throw new IOException($"Could not reserve {name}'s folder. Another launch may be preparing it.", ex); }
        try
        {
            Recover();
            // Compare case-insensitively on both hosts: APFS can be case-insensitive too.
            if (_entries.Values.Any(e => SamePath(e.Identity.Directory, directory) && !Exited(e.Process)))
                throw new InvalidOperationException($"{name}'s game folder is already in use. Play a different profile to keep both games open.");
            return reservation;
        }
        catch
        {
            reservation.Dispose();
            throw;
        }
    }

    public int Start(ProcessStartInfo info, string id, string profileId, string name, string version)
    {
        var process = Process.Start(info) ?? throw new InvalidOperationException("Java did not start.");
        var pid = process.Id;
        long started = DateTime.UtcNow.Ticks;
        var executable = info.FileName;
        try { started = process.StartTime.ToUniversalTime().Ticks; }
        catch (InvalidOperationException) { /* An immediate exit is still reported below. */ }
        try { executable = process.MainModule?.FileName ?? executable; }
        catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception or PlatformNotSupportedException) { }
        var entry = new Entry(new Identity(id, profileId, name, version, info.WorkingDirectory, executable, pid, started), process);
        _entries[id] = entry;
        try
        {
            if (!Exited(process))
            {
                var file = Record(id);
                var temporary = file + ".tmp";
                try
                {
                    File.WriteAllText(temporary, Util.Json(entry.Identity));
                    File.Move(temporary, file);
                }
                finally { TryDelete(temporary); }
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _emit(Util.Json(new { type = "instance", action = "tracking-warning", instance = id }));
        }
        _emit(Util.Json(new { type = "instance", action = "started", instance = id }));
        _ = Observe(entry, ReadOutput(entry, process.StandardOutput), ReadOutput(entry, process.StandardError));
        return pid;
    }

    public string List()
    {
        Recover();
        return Util.Json(_entries.Values.Where(x => !Exited(x.Process)).OrderBy(x => x.Identity.StartedUtcTicks)
            .Select(x => new
            {
                id = x.Identity.Id, profileId = x.Identity.ProfileId, name = x.Identity.Name,
                version = x.Identity.Version, pid = x.Identity.Pid, stopping = Volatile.Read(ref x.Stopping) != 0,
                started = new DateTime(x.Identity.StartedUtcTicks, DateTimeKind.Utc), log = x.Tail.ToArray()
            }));
    }

    public string Stop(string id)
    {
        Recover();
        if (!_entries.TryGetValue(id, out var entry) || Exited(entry.Process))
            return Util.Json(new { stopped = false, missing = true });
        return Util.Json(new { stopped = StopEntry(entry), instance = id });
    }

    public string StopProfile(string profileId)
    {
        Recover();
        var entry = _entries.Values.FirstOrDefault(x => x.Identity.ProfileId == profileId && !Exited(x.Process));
        return Util.Json(new { stopped = entry != null && StopEntry(entry) });
    }

    public string StopAll()
    {
        Recover();
        var entries = _entries.Values.Where(x => !Exited(x.Process)).ToArray();
        var stopped = entries.Count(StopEntry);
        return Util.Json(new { stopped, failed = entries.Length - stopped });
    }

    private bool StopEntry(Entry entry)
    {
        var process = entry.Process;
        if (Exited(process)) return true;
        if (Interlocked.CompareExchange(ref entry.Stopping, 1, 0) != 0) return false;
        try
        {
            if (!Exited(process) && !(process.CloseMainWindow() && process.WaitForExit(6000)))
            {
                if (!Exited(process)) process.Kill(entireProcessTree: true);
                return process.WaitForExit(3000);
            }
            return true;
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception or AggregateException)
        {
            return Exited(process);
        }
        finally
        {
            if (!Exited(process)) Interlocked.Exchange(ref entry.Stopping, 0);
        }
    }

    private async Task ReadOutput(Entry entry, StreamReader reader)
    {
        try
        {
            while (await reader.ReadLineAsync().ConfigureAwait(false) is { } line)
            {
                if (string.IsNullOrWhiteSpace(line)) continue;
                line = line[..Math.Min(line.Length, 2000)];
                entry.Tail.Enqueue(line);
                while (entry.Tail.Count > 40) entry.Tail.TryDequeue(out _);
            }
        }
        catch (Exception ex) when (ex is IOException or ObjectDisposedException) { }
    }

    private async Task Observe(Entry entry, params Task[] readers)
    {
        var info = entry.Identity;
        int? exitCode = null;
        try
        {
            await entry.Process.WaitForExitAsync().ConfigureAwait(false);
            // ExitCode is not necessarily available for a process inherited from a previous launcher session.
            try { exitCode = entry.Process.ExitCode; } catch (InvalidOperationException) { }
            try { await Task.WhenAll(readers).WaitAsync(TimeSpan.FromSeconds(1)).ConfigureAwait(false); }
            catch (TimeoutException) { }
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception) { }
        finally
        {
            if (_entries.TryRemove(new KeyValuePair<string, Entry>(info.Id, entry))) TryDelete(Record(info.Id));
            entry.Process.Dispose();
            _emit((exitCode is > 0 or < 0) && Volatile.Read(ref entry.Stopping) == 0
                ? Util.Json(new { type = "crash", instance = info.Id, profileId = info.ProfileId, instanceName = info.Name,
                    exitCode, tail = string.Join("\n", entry.Tail.TakeLast(14)) })
                : Util.Json(new { type = "instance", action = "exited", instance = info.Id }));
        }
    }

    private void Recover()
    {
        foreach (var file in Directory.EnumerateFiles(_records, "*.json"))
        {
            var id = Path.GetFileNameWithoutExtension(file);
            if (!Guid.TryParseExact(id, "N", out _) || _entries.ContainsKey(id)) continue;
            Process process = null;
            try
            {
                using var stream = File.Open(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                var info = JsonSerializer.Deserialize<Identity>(stream);
                if (info == null || info.Id != id || info.Pid <= 0 || !Path.IsPathFullyQualified(info.Executable)
                    || !Path.IsPathFullyQualified(info.Directory)) continue;
                process = Process.GetProcessById(info.Pid);
                // A PID can be reused. Both the start time and executable must match.
                if (Exited(process) || process.StartTime.ToUniversalTime().Ticks != info.StartedUtcTicks
                    || !SamePath(process.MainModule?.FileName, info.Executable))
                {
                    TryDelete(file);
                    continue;
                }
                var entry = new Entry(info, process);
                if (!_entries.TryAdd(id, entry)) continue;
                process = null; // Owned by Observe now.
                _ = Observe(entry);
            }
            catch (ArgumentException) { TryDelete(file); } // PID no longer exists.
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException
                or InvalidOperationException or System.ComponentModel.Win32Exception or PlatformNotSupportedException) { }
            finally { process?.Dispose(); }
        }
    }

    private string Record(string id) => Path.Combine(_records, id + ".json");
    private static bool SamePath(string a, string b) => string.Equals(a, b, StringComparison.OrdinalIgnoreCase);
    private static bool Exited(Process process)
    {
        try { return process.HasExited; } catch (InvalidOperationException) { return true; }
    }
    private static void TryDelete(string file)
    {
        try { File.Delete(file); } catch (IOException) { } catch (UnauthorizedAccessException) { }
    }
}
