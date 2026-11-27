namespace Luxis;

internal static class Platform
{
    public static string Root => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
        "Library", "Application Support", "Luxis");

    public static string ResourceText(string name)
    {
        using var stream = typeof(Platform).Assembly.GetManifestResourceStream(name)
            ?? throw new IOException("Missing embedded resource " + name);
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }
}
