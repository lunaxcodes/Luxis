using System.Diagnostics;
using System.IO.Compression;

namespace Luxis;

/// <summary>Finds and, if needed, downloads a Temurin JDK for the required Java major version.</summary>
public sealed class JavaInstaller
{
    private readonly string _root;
    private readonly Action<string> _emit;
    public JavaInstaller(string root, Action<string> emit) { _root = root; _emit = emit; }

    public string RuntimePath(int major) => Path.Combine(_root, "runtimes", "jdk-" + major);

    public string JavaExe(int major)
    {
        var home = RuntimePath(major);
        if (!Directory.Exists(home)) return "";
#if MAC
        var exe = Directory.GetFiles(home, "java", SearchOption.AllDirectories).FirstOrDefault();
#else
        var exe = Directory.GetFiles(home, "java.exe", SearchOption.AllDirectories).FirstOrDefault();
#endif
        return exe ?? "";
    }

    public async Task<string> Install(int major)
    {
        if (major is not (8 or 17 or 21 or 25)) throw new Exception("Unsupported Java version " + major);
        var existing = JavaExe(major);
        if (!string.IsNullOrEmpty(existing)) return Util.Json(new { ok = true, path = existing, major });

        var dir = RuntimePath(major);
        Directory.CreateDirectory(dir);
        var zip = Path.Combine(Path.GetTempPath(), "luxis-jdk-" + major + ".zip");
#if MAC
        var arch = System.Runtime.InteropServices.RuntimeInformation.ProcessArchitecture
            == System.Runtime.InteropServices.Architecture.Arm64 ? "aarch64" : "x64";
        var url = $"https://api.adoptium.net/v3/binary/latest/{major}/ga/mac/{arch}/jdk/hotspot/normal/eclipse";
        var archive = Path.Combine(Path.GetTempPath(), "luxis-jdk-" + major + ".tar.gz");
#else
        var url = $"https://api.adoptium.net/v3/binary/latest/{major}/ga/windows/x64/jdk/hotspot/normal/eclipse";
        var archive = zip;
#endif
        _emit(Util.Json(new { type = "java", stage = "Downloading Java " + major, progress = 5 }));
        // Adoptium returns 406 if a JSON Accept header is sent — use a clean client.
        using var client = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
        using (var res = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead))
        {
            res.EnsureSuccessStatusCode();
            var total = res.Content.Headers.ContentLength ?? 0;
            await using var src = await res.Content.ReadAsStreamAsync();
            await using var dst = new FileStream(zip, FileMode.Create, FileAccess.Write, FileShare.None, 131072, true);
            var buf = new byte[131072];
            long done = 0;
            int read;
            while ((read = await src.ReadAsync(buf)) > 0)
            {
                await dst.WriteAsync(buf.AsMemory(0, read));
                done += read;
                if (total > 0 && done % (20 * 131072) < buf.Length)
                    _emit(Util.Json(new { type = "java", stage = "Downloading Java " + major,
                        progress = 5 + (int)(85d * done / total) }));
            }
        }
        _emit(Util.Json(new { type = "java", stage = "Extracting Java " + major, progress = 92 }));
        try
        {
#if MAC
            var psi = new ProcessStartInfo("/bin/tar", $"-xzf \"{archive}\" -C \"{dir}\" --strip-components=1")
            { RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true };
            using var proc = Process.Start(psi)!;
            var errors = proc.StandardError.ReadToEnd();
            proc.WaitForExit(10000);
            if (proc.ExitCode != 0) throw new Exception(errors);
#else
            ZipFile.ExtractToDirectory(zip, dir, true);
#endif
        }
        finally { try { File.Delete(archive); } catch { } }

        var exe = JavaExe(major);
        if (string.IsNullOrEmpty(exe)) throw new Exception("Java extraction did not produce a java executable.");
        _emit(Util.Json(new { type = "java", stage = "Java " + major + " ready", progress = 100 }));
        return Util.Json(new { ok = true, path = exe, major });
    }
}
