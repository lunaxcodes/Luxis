using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Luxis;

public sealed class CapeInfo
{
    public string id { get; set; } = "";
    public string name { get; set; } = "";
    public string url { get; set; } = "";
    public bool active { get; set; }
}

public sealed class Account
{
    public string id { get; set; } = "";
    public string username { get; set; } = "";
    public string uuid { get; set; } = "";
    public string type { get; set; } = "microsoft";
    public string refreshEncrypted { get; set; } = "";
    public DateTime expiresAt { get; set; }
    public string skinUrl { get; set; } = "";
    public string capeUrl { get; set; } = "";
    public List<CapeInfo> capes { get; set; } = new();
}

public sealed class McSession
{
    public string Username, Uuid, Token;
    public DateTime Expires;
    public bool Online;
}

public sealed class AuthService
{
    public const string DefaultClientId = "071ffa4b-eafb-4b7a-aa71-d8beef4f4f2e"; // Espectral public app, personal MS accounts, device-code enabled
    private readonly string _root, _accountFile, _settingsFile;
    private readonly List<Account> _accounts = new();
    private readonly Dictionary<string, McSession> _sessions = new();
    private readonly ConcurrentDictionary<string, Flow> _flows = new();
    private readonly ConcurrentDictionary<string, CodeFlow> _codeFlows = new();

    private sealed class CodeFlow
    {
        public string Verifier = "", State = "";
        public DateTime Expires;
    }

    public string ClientId { get; private set; } = DefaultClientId;
    public string Tenant { get; private set; } = "consumers";
    private string AuthBase => $"https://login.microsoftonline.com/{Tenant}/oauth2/v2.0";
    public string ActiveId { get; private set; } = "";

    private sealed class Flow
    {
        public string DeviceCode = "", UserCode = "", Uri = "";
        public DateTime Expires;
        public int Interval = 5;
    }

    public AuthService(string root)
    {
        _root = root;
        _accountFile = Path.Combine(root, "accounts.json");
        _settingsFile = Path.Combine(root, "settings.json");
        Directory.CreateDirectory(root);
        Load();
    }

    private void Load()
    {
        try
        {
            if (File.Exists(_settingsFile))
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(_settingsFile));
                var id = doc.RootElement.Str("msClientId");
                if (!string.IsNullOrWhiteSpace(id)) ClientId = id;
                ActiveId = doc.RootElement.Str("activeAccount");
                var tenant = doc.RootElement.Str("tenant");
                if (!string.IsNullOrWhiteSpace(tenant)) Tenant = tenant;
            }
            if (File.Exists(_accountFile))
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(_accountFile));
                foreach (var item in doc.RootElement.EnumerateArray())
                    _accounts.Add(new Account
                    {
                        id = item.Str("id"), username = item.Str("username"), uuid = item.Str("uuid"),
                        type = item.Str("type", "microsoft"), refreshEncrypted = item.Str("refreshEncrypted"),
                        expiresAt = DateTime.TryParse(item.Str("expiresAt"), out var d) ? d : DateTime.MinValue,
                        skinUrl = item.Str("skinUrl"), capeUrl = item.Str("capeUrl"),
                        capes = item.Items("capes").Select(c => new CapeInfo
                        {
                            id = c.Str("id"), name = c.Str("name", "Cape"), url = c.Str("url"),
                            active = c.Str("state") == "ACTIVE"
                        }).ToList()
                    });
            }
        }
        catch { /* corrupt state -> start clean */ }
    }

    private void Debug(string message)
    {
        try { File.AppendAllText(Path.Combine(_root, "auth-debug.log"), $"[{DateTime.UtcNow:O}] {message}\n"); }
        catch { }
    }

    private static string TokenClaims(string token)
    {
        var parts = token.Split('.');
        if (parts.Length < 2) return "MSA token is not a JWT";
        try
        {
            var p = parts[1].Replace('-', '+').Replace('_', '/');
            p = p.PadRight(p.Length + (4 - p.Length % 4) % 4, '=');
            using var d = JsonDocument.Parse(Encoding.UTF8.GetString(Convert.FromBase64String(p)));
            var r = d.RootElement;
            return "MSA token claims: aud=" + (r.TryGetProperty("aud", out var a) ? a.ToString() : "?")
                 + " scp=" + (r.TryGetProperty("scp", out var s) ? s.ToString() : "?")
                 + " iss=" + (r.TryGetProperty("iss", out var i) ? i.ToString() : "?");
        }
        catch { return "MSA token claims unavailable"; }
    }

    private static string SafeBody(string body)
    {
        if (string.IsNullOrWhiteSpace(body)) return "empty body";
        try
        {
            using var d = JsonDocument.Parse(body);
            if (d.RootElement.ValueKind == JsonValueKind.Object)
                return "keys: " + string.Join(",", d.RootElement.EnumerateObject().Select(p => p.Name));
            return "json " + d.RootElement.ValueKind;
        }
        catch { return "non-json (" + body.Length + " bytes)"; }
    }

    public string GetAuthDebug()
    {
        try { return string.Join("\n", File.ReadAllLines(Path.Combine(_root, "auth-debug.log")).TakeLast(30)); }
        catch { return "No auth log yet."; }
    }

    private void Save()
    {
        try
        {
            File.WriteAllText(_accountFile, JsonSerializer.Serialize(_accounts));
            File.WriteAllText(_settingsFile, JsonSerializer.Serialize(new { msClientId = ClientId, activeAccount = ActiveId, tenant = Tenant }));
        }
        catch (Exception ex) { Console.WriteLine("Save accounts failed: " + ex.Message); }
    }

    public void SetTenant(string tenant)
    {
        if (!string.IsNullOrWhiteSpace(tenant)) { Tenant = tenant.Trim(); Save(); }
    }

    public void SetClientId(string id)
    {
        if (!string.IsNullOrWhiteSpace(id)) { ClientId = id.Trim(); Save(); }
    }

    public string ListAccounts() => Util.Json(_accounts.Select(ToPublic));

    private static object ToPublic(Account a) => new
    {
        id = a.id, username = a.username, uuid = a.uuid, type = a.type,
        tokenStatus = a.type == "offline" ? "n/a" : (string.IsNullOrEmpty(a.refreshEncrypted) ? "missing" : "valid"),
        skinUrl = a.skinUrl, capeUrl = a.capeUrl
    };

    public Account Find(string id) => _accounts.FirstOrDefault(a => a.id == id);

    public void SetActive(string id)
    {
        if (Find(id) != null) { ActiveId = id; Save(); }
    }

    public void Remove(string id)
    {
        _accounts.RemoveAll(a => a.id == id);
        _sessions.Remove(id);
        if (ActiveId == id) ActiveId = _accounts.FirstOrDefault()?.id ?? "";
        Save();
    }

    public string CreateOffline(string username)
    {
        var name = string.IsNullOrWhiteSpace(username) ? "Player" + new Random().Next(100, 999) : username.Trim();
        var a = new Account
        {
            id = "offline-" + Guid.NewGuid().ToString("N")[..8],
            username = name,
            uuid = OfflineUuid(name),
            type = "offline",
            expiresAt = DateTime.MaxValue
        };
        _accounts.Add(a);
        ActiveId = a.id;
        Save();
        return Util.Json(ToPublic(a));
    }

    private static string OfflineUuid(string name)
    {
        var bytes = Encoding.UTF8.GetBytes("OfflinePlayer:" + name.ToLowerInvariant());
        var hash = MD5.HashData(bytes);
        hash[6] = (byte)((hash[6] & 0x0f) | 0x30);
        hash[8] = (byte)((hash[8] & 0x3f) | 0x80);
        var h = Convert.ToHexString(hash).ToLowerInvariant();
        return $"{h[..8]}-{h[8..12]}-{h[12..16]}-{h[16..20]}-{h[20..]}";
    }

    public async Task<string> BeginLogin()
    {
        var body = await Util.PostForm(AuthBase + "/devicecode",
            new Dictionary<string, string>
            {
                ["client_id"] = ClientId,
                ["scope"] = "XboxLive.signin XboxLive.offline_access"
            });
        using var doc = Util.ParseJson(body, "Microsoft device-code endpoint");
        Debug("device-code: " + SafeBody(body));
        var root = doc.RootElement;
        if (root.TryGetProperty("error", out var err))
            throw new Exception(err.Str("error", "login failed") + ": " + root.Str("error_description"));

        var flow = new Flow
        {
            DeviceCode = root.Str("device_code"), UserCode = root.Str("user_code"),
            Uri = root.Str("verification_uri", "https://www.microsoft.com/link"),
            Expires = DateTime.UtcNow.AddSeconds(root.Int("expires_in", 900)),
            Interval = Math.Max(5, root.Int("interval", 5))
        };
        var id = Guid.NewGuid().ToString("N")[..10];
        _flows[id] = flow;
        return Util.Json(new { flowId = id, userCode = flow.UserCode, verificationUri = flow.Uri, interval = flow.Interval });
    }

    public async Task<string> PollLogin(string flowId)
    {
        if (!_flows.TryGetValue(flowId, out var flow)) return Util.Json(new { status = "expired" });
        if (DateTime.UtcNow > flow.Expires) { _flows.TryRemove(flowId, out _); return Util.Json(new { status = "expired" }); }

        var body = await Util.PostForm(AuthBase + "/token",
            new Dictionary<string, string>
            {
                ["client_id"] = ClientId,
                ["grant_type"] = "urn:ietf:params:oauth:grant-type:device_code",
                ["device_code"] = flow.DeviceCode
            });
        using var doc = Util.ParseJson(body, "Microsoft token endpoint");
        Debug("token poll: " + SafeBody(body));
        var root = doc.RootElement;
        var error = root.Str("error");
        if (!string.IsNullOrEmpty(error))
        {
            var status = error switch
            {
                "authorization_pending" => "pending",
                "slow_down" => "slow_down",
                "expired_token" => "expired",
                "access_denied" => "denied",
                _ => "error"
            };
            return Util.Json(new { status, message = root.Str("error_description", error) });
        }

        var msToken = root.Str("access_token");
        Debug(TokenClaims(msToken));
        var refresh = root.Str("refresh_token");
        var expiresIn = root.Int("expires_in", 3600);
        var result = await CompleteAuth(msToken, refresh, expiresIn);

        var accountId = "ms-" + Guid.NewGuid().ToString("N")[..10];
        var account = new Account
        {
            id = accountId,
            username = result.username, uuid = result.uuid, type = "microsoft",
            refreshEncrypted = Encrypt(refresh, accountId), expiresAt = DateTime.UtcNow.AddSeconds(expiresIn),
            skinUrl = result.skinUrl, capeUrl = result.capeUrl, capes = result.capes
        };
        _accounts.RemoveAll(a => a.uuid == result.uuid && a.type == "microsoft");
        _accounts.Add(account);
        ActiveId = account.id;
        _sessions[account.id] = new McSession { Username = result.username, Uuid = result.uuid, Token = result.mcToken, Expires = result.expires, Online = true };
        Save();
        _flows.TryRemove(flowId, out _);
        return Util.Json(new { status = "success", account = ToPublic(account) });
    }

    private sealed class AuthResult
    {
        public string mcToken = "", uuid = "", username = "", skinUrl = "", capeUrl = "";
        public List<CapeInfo> capes = new();
        public DateTime expires;
    }

    private async Task<AuthResult> CompleteAuth(string msToken, string refreshToken, int expiresIn)
    {
        const string xblUrl = "https://user.auth.xboxlive.com/user/authenticate";
        var deviceId = Guid.NewGuid();
        static Dictionary<string, string> Hdr(string v) => v == null
            ? new Dictionary<string, string>()
            : new Dictionary<string, string> { ["x-xbl-contract-version"] = v };

        var xblAttempts = new (string Label, object Body, Dictionary<string, string> Headers)[]
        {
            ("RPS d= v2", new
            {
                Properties = new { AuthMethod = "RPS", SiteName = "user.auth.xboxlive.com", RpsTicket = "d=" + msToken },
                RelyingParty = "http://auth.xboxlive.com", TokenType = "JWT"
            }, Hdr("2")),
            ("PoP opt: v2", new
            {
                Properties = new
                {
                    AuthMethod = "ProofOfPossession", Id = "opt:" + deviceId, DeviceType = "Android", Version = "10",
                    Claims = new { user_tokens = new { microsoft = msToken } }
                },
                RelyingParty = "http://auth.xboxlive.com", TokenType = "JWT"
            }, Hdr("2")),
            ("RPS t= v1", new
            {
                Properties = new { AuthMethod = "RPS", SiteName = "user.auth.xboxlive.com", RpsTicket = "t=" + msToken },
                RelyingParty = "http://auth.xboxlive.com", TokenType = "JWT"
            }, Hdr("1")),
            ("PoP opt: v1", new
            {
                Properties = new
                {
                    AuthMethod = "ProofOfPossession", Id = "opt:" + deviceId, DeviceType = "Android", Version = "10",
                    Claims = new { user_tokens = new { microsoft = msToken } }
                },
                RelyingParty = "http://auth.xboxlive.com", TokenType = "JWT"
            }, Hdr("1")),
            ("PoP plain v2", new
            {
                Properties = new
                {
                    AuthMethod = "ProofOfPossession", Id = deviceId.ToString(), DeviceType = "Android", Version = "10",
                    Claims = new { user_tokens = new { microsoft = msToken } }
                },
                RelyingParty = "http://auth.xboxlive.com", TokenType = "JWT"
            }, Hdr("2")),
            ("RPS d= no header", new
            {
                Properties = new { AuthMethod = "RPS", SiteName = "user.auth.xboxlive.com", RpsTicket = "d=" + msToken },
                RelyingParty = "http://auth.xboxlive.com", TokenType = "JWT"
            }, Hdr(null))
        };

        string xblToken = "";
        foreach (var attempt in xblAttempts)
        {
            try
            {
                var raw = await Util.PostJsonWithHeaders(xblUrl, attempt.Body, attempt.Headers);
                Debug("XBL " + attempt.Label + ": " + SafeBody(raw));
                using var d = Util.ParseJson(raw, "Xbox Live", true);
                var t = d.RootElement.Str("Token");
                if (!string.IsNullOrEmpty(t)) { xblToken = t; Debug("XBL SUCCESS: " + attempt.Label); break; }
            }
            catch (Exception ex) { Debug("XBL " + attempt.Label + " error: " + ex.Message); }
        }
        if (string.IsNullOrEmpty(xblToken))
            throw new Exception("Xbox Live rejected the token with every known format. Check the sign-in log for token audience and response keys.");

        var xstsAttempts = new (string Label, object Body, Dictionary<string, string> Headers)[]
        {
            ("SandboxId v2", new
            {
                Properties = new { SandboxId = "RETAIL", UserTokens = new[] { xblToken } },
                RelyingParty = "rp://api.minecraftservices.com/", TokenType = "JWT"
            }, Hdr("2")),
            ("SandboxId v1", new
            {
                Properties = new { SandboxId = "RETAIL", UserTokens = new[] { xblToken } },
                RelyingParty = "rp://api.minecraftservices.com/", TokenType = "JWT"
            }, Hdr("1")),
            ("Sandbox v1", new
            {
                Properties = new { Sandbox = "RETAIL", SiteName = "user.login.live.com", UserTokens = new[] { xblToken } },
                RelyingParty = "rp://api.minecraftservices.com/", TokenType = "JWT"
            }, Hdr("1")),
            ("SandboxId none", new
            {
                Properties = new { SandboxId = "RETAIL", UserTokens = new[] { xblToken } },
                RelyingParty = "rp://api.minecraftservices.com/", TokenType = "JWT"
            }, Hdr(null))
        };

        string xstsToken = "", uhs = "";
        foreach (var attempt in xstsAttempts)
        {
            try
            {
                var raw = await Util.PostJsonWithHeaders("https://xsts.auth.xboxlive.com/xsts/authorize", attempt.Body, attempt.Headers);
                Debug("XSTS " + attempt.Label + ": " + SafeBody(raw));
                using var d = Util.ParseJson(raw, "XSTS", true);
                var t = d.RootElement.Str("Token");
                if (!string.IsNullOrEmpty(t))
                {
                    xstsToken = t;
                    uhs = d.RootElement.TryGetProperty("DisplayClaims", out var dc) && dc.TryGetProperty("xui", out var xui)
                        && xui.ValueKind == JsonValueKind.Array && xui.GetArrayLength() > 0 ? xui[0].Str("uhs") : "";
                    Debug("XSTS SUCCESS: " + attempt.Label);
                    break;
                }
            }
            catch (Exception ex) { Debug("XSTS " + attempt.Label + " error: " + ex.Message); }
        }
        if (string.IsNullOrEmpty(xstsToken)) throw new Exception("XSTS rejected the Xbox token. Check the sign-in log.");

        Debug("MC login start");
        var mcRaw = await Util.PostJson("https://api.minecraftservices.com/authentication/login_with_xbox",
            new { identityToken = $"XBL3.0 x={uhs};{xstsToken}" });
        using var mcDoc = Util.ParseJson(mcRaw, "Minecraft login endpoint");
        var mcToken = mcDoc.RootElement.Str("access_token");
        if (string.IsNullOrEmpty(mcToken)) throw new Exception("Minecraft login failed: " + Util.Truncate(mcRaw));

        // Verify the account actually owns the game.
        var entitlements = await Util.Get("https://api.minecraftservices.com/entitlements/mcstore", mcToken);
        Debug("entitlements: " + SafeBody(entitlements));
        // Entitlement is informational. Mojang answers 204/empty for several valid
        // licences, so an empty body must never break the sign-in chain.
        using var entDoc = Util.ParseJson(entitlements, "Minecraft entitlement endpoint", true);

        var profileRaw = await Util.Get("https://api.minecraftservices.com/minecraft/profile", mcToken);
        Debug("profile: " + SafeBody(profileRaw));
        using var profileDoc = Util.ParseJson(profileRaw, "Minecraft profile endpoint");
        var profile = profileDoc.RootElement;

        return new AuthResult
        {
            mcToken = mcToken,
            uuid = profile.Str("id"),
            username = profile.Str("name", "Player"),
            skinUrl = profile.Items("skins").FirstOrDefault().Str("url"),
            capes = profile.Items("capes").Select(c => new CapeInfo
            {
                id = c.Str("id"), name = c.Str("name", "Cape"), url = c.Str("url"),
                active = c.Str("state") == "ACTIVE"
            }).ToList(),
            capeUrl = profile.Items("capes").FirstOrDefault(c => c.Str("state") == "ACTIVE").Str("url"),
            expires = DateTime.UtcNow.AddSeconds(Math.Max(expiresIn, mcDoc.RootElement.Int("expires_in", 3600)) - 60)
        };
    }

    public async Task<string> Refresh(string id)
    {
        var a = Find(id);
        if (a == null) return Util.Json(new { status = "missing" });
        if (a.type == "offline") return Util.Json(new { status = "offline" });
        try
        {
            var refresh = Decrypt(a.refreshEncrypted, a.id);
            var body = await Util.PostForm(AuthBase + "/token",
                new Dictionary<string, string> { ["client_id"] = ClientId, ["grant_type"] = "refresh_token", ["refresh_token"] = refresh });
            using var doc = Util.ParseJson(body, "Microsoft refresh endpoint");
            if (doc.RootElement.TryGetProperty("error", out _)) throw new Exception(doc.RootElement.Str("error_description", "refresh failed"));
            var msToken = doc.RootElement.Str("access_token");
            var expiresIn = doc.RootElement.Int("expires_in", 3600);
            var newRefresh = doc.RootElement.Str("refresh_token", refresh);
            var result = await CompleteAuth(msToken, newRefresh, expiresIn);
            a.username = result.username; a.uuid = result.uuid; a.skinUrl = result.skinUrl; a.capeUrl = result.capeUrl; a.capes = result.capes;
            a.refreshEncrypted = Encrypt(newRefresh, a.id); a.expiresAt = DateTime.UtcNow.AddSeconds(expiresIn);
            _sessions[id] = new McSession { Username = result.username, Uuid = result.uuid, Token = result.mcToken, Expires = result.expires, Online = true };
            Save();
            return Util.Json(new { status = "success", account = ToPublic(a) });
        }
        catch (Exception ex) { return Util.Json(new { status = "error", message = ex.Message }); }
    }

    public async Task<McSession> GetSession(string id)
    {
        var a = Find(id);
        if (a == null) throw new Exception("Account not found in Luxis — sign in again.");
        if (a.type == "offline") return new McSession { Username = a.username, Uuid = a.uuid, Token = "offline", Expires = DateTime.MaxValue, Online = false };
        if (_sessions.TryGetValue(id, out var s) && s.Expires > DateTime.UtcNow.AddMinutes(2)) return s;
        var r = await Refresh(id);
        using var doc = Util.ParseJson(r, "Token refresh");
        if (doc.RootElement.Str("status") != "success") throw new Exception(doc.RootElement.Str("message", "Token refresh failed"));
        return _sessions[id];
    }

    public async Task<string> GetTexture(string id, string kind)
    {
        var a = Find(id);
        if (a == null) throw new Exception("Account not found.");
        var url = kind == "cape" ? a.capeUrl : a.skinUrl;
        if (string.IsNullOrEmpty(url)) throw new Exception("This account has no " + kind + " texture.");
        var bytes = await Util.Http.GetByteArrayAsync(url);
        return "data:image/png;base64," + Convert.ToBase64String(bytes);
    }

    public string MojangCapes(string id)
    {
        var a = Find(id);
        return Util.Json(a?.capes ?? new List<CapeInfo>());
    }

    public async Task<string> SetActiveCape(string id, string capeId)
    {
        var a = Find(id);
        if (a == null || a.type != "microsoft") throw new Exception("Sign in with Microsoft to change your cape.");
        var session = await GetSession(id);
        var url = "https://api.minecraftservices.com/minecraft/profile/capes/active";
        using var req = new HttpRequestMessage(
            string.IsNullOrEmpty(capeId) || capeId == "none" ? HttpMethod.Delete : HttpMethod.Put, url);
        req.Headers.Authorization = new("Bearer", session.Token);
        if (req.Method == HttpMethod.Put)
            req.Content = new StringContent(Util.Json(new { capeId }), Encoding.UTF8, "application/json");
        using var res = await Util.Http.SendAsync(req);
        var text = await res.Content.ReadAsStringAsync();
        if (!res.IsSuccessStatusCode)
            throw new Exception("Mojang rejected the cape change: " + Util.Truncate(text, 140));
        using var doc = Util.ParseJson(text, "Mojang cape endpoint", true);
        var capes = doc.RootElement.Items("capes").Select(c => new CapeInfo
        {
            id = c.Str("id"), name = c.Str("name", "Cape"), url = c.Str("url"),
            active = c.Str("state") == "ACTIVE"
        }).ToList();
        if (capes.Count > 0)
        {
            a.capes = capes;
            a.capeUrl = capes.FirstOrDefault(c => c.active)?.url ?? "";
        }
        Save();
        return Util.Json(new { ok = true, capes = a.capes });
    }

    public async Task<string> GetCapeTexture(string id, string capeId)
    {
        var a = Find(id);
        var cape = a?.capes?.FirstOrDefault(c => c.id == capeId);
        if (cape == null || string.IsNullOrEmpty(cape.url)) throw new Exception("Cape texture not found.");
        var bytes = await Util.Http.GetByteArrayAsync(cape.url);
        return "data:image/png;base64," + Convert.ToBase64String(bytes);
    }

    private static string Base64Url(byte[] bytes)
        => Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    /// <summary>Alternative sign-in: authorization code + PKCE, URL pasted back into the app.</summary>
    public string BeginCodeLogin()
    {
        var verifier = Base64Url(RandomNumberGenerator.GetBytes(32));
        var challenge = Base64Url(SHA256.HashData(Encoding.ASCII.GetBytes(verifier)));
        var state = Guid.NewGuid().ToString("N")[..12];
        const string redirect = "https://login.microsoftonline.com/common/oauth2/nativeclient";
        var scope = Uri.EscapeDataString("XboxLive.signin offline_access");
        var url = AuthBase + "/authorize" +
                  $"?client_id={Uri.EscapeDataString(ClientId)}&response_type=code" +
                  $"&redirect_uri={Uri.EscapeDataString(redirect)}&scope={scope}" +
                  $"&code_challenge={challenge}&code_challenge_method=S256&state={state}&prompt=select_account";
        var id = "code-" + Guid.NewGuid().ToString("N")[..10];
        _codeFlows[id] = new CodeFlow { Verifier = verifier, State = state, Expires = DateTime.UtcNow.AddMinutes(15) };
        return Util.Json(new { flowId = id, url, state });
    }

    public async Task<string> FinishCodeLogin(string flowId, string pasted)
    {
        if (!_codeFlows.TryGetValue(flowId, out var flow)) throw new Exception("Sign-in session expired — start again.");
        if (DateTime.UtcNow > flow.Expires) throw new Exception("Sign-in session expired — start again.");

        var match = Regex.Match(pasted ?? "", @"[?&]code=([^&\s]+)");
        var code = match.Success ? Uri.UnescapeDataString(match.Groups[1].Value) : (pasted ?? "").Trim();
        if (string.IsNullOrEmpty(code)) throw new Exception("Paste the redirect URL (or the code) from the browser.");

        const string redirect = "https://login.microsoftonline.com/common/oauth2/nativeclient";
        var body = await Util.PostForm(AuthBase + "/token",
            new Dictionary<string, string>
            {
                ["client_id"] = ClientId,
                ["grant_type"] = "authorization_code",
                ["code"] = code,
                ["redirect_uri"] = redirect,
                ["code_verifier"] = flow.Verifier
            });
        using var doc = Util.ParseJson(body, "Microsoft authorization-code endpoint");
        if (doc.RootElement.TryGetProperty("error", out var err))
            throw new Exception("Microsoft sign-in failed: " + err.GetString());
        var msToken = doc.RootElement.Str("access_token");
        Debug(TokenClaims(msToken));
        var refresh = doc.RootElement.Str("refresh_token");
        var expiresIn = doc.RootElement.Int("expires_in", 3600);
        var result = await CompleteAuth(msToken, refresh, expiresIn);
        var accountId = "ms-" + Guid.NewGuid().ToString("N")[..10];
        var account = new Account
        {
            id = accountId,
            username = result.username, uuid = result.uuid, type = "microsoft",
            refreshEncrypted = Encrypt(refresh, accountId), expiresAt = DateTime.UtcNow.AddSeconds(expiresIn),
            skinUrl = result.skinUrl, capeUrl = result.capeUrl, capes = result.capes
        };
        _accounts.RemoveAll(a => a.uuid == result.uuid && a.type == "microsoft");
        _accounts.Add(account);
        ActiveId = account.id;
        _sessions[account.id] = new McSession { Username = result.username, Uuid = result.uuid, Token = result.mcToken, Expires = result.expires, Online = true };
        Save();
        _codeFlows.TryRemove(flowId, out _);
        return Util.Json(new { status = "success", account = ToPublic(account) });
    }

    private static string Encrypt(string value, string id)
    {
#if MAC
        KeychainStore(id, value);
        return "keychain:" + id;
#else
        return Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null, DataProtectionScope.CurrentUser));
#endif
    }

    private static string Decrypt(string value, string id)
    {
#if MAC
        return KeychainRead(id) ?? "";
#else
        return Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(value), null, DataProtectionScope.CurrentUser));
#endif
    }

#if MAC
    private const string KeychainService = "Luxis.RefreshTokens";
    private static System.Text.Json.JsonElement KeychainDocument()
    {
        try
        {
            var psi = new System.Diagnostics.ProcessStartInfo("/usr/bin/security", "find-generic-password -a luxis -s " + KeychainService + " -w")
            { RedirectStandardOutput = true, UseShellExecute = false, CreateNoWindow = true };
            using var proc = System.Diagnostics.Process.Start(psi)!;
            var text = proc.StandardOutput.ReadToEnd().Trim();
            proc.WaitForExit(3000);
            using var parsed = System.Text.Json.JsonDocument.Parse(string.IsNullOrEmpty(text) ? "{}" : text);
            return parsed.RootElement.Clone();
        }
        catch { using var empty = System.Text.Json.JsonDocument.Parse("{}"); return empty.RootElement.Clone(); }
    }
    private static string KeychainRead(string id)
    {
        try
        {
            var doc = KeychainDocument();
            return doc.TryGetProperty(id, out var value) ? value.GetString() : null;
        }
        catch { return null; }
    }
    private static void KeychainStore(string id, string token)
    {
        try
        {
            var map = new Dictionary<string, string>();
            var doc = KeychainDocument();
            if (doc.ValueKind == System.Text.Json.JsonValueKind.Object)
                foreach (var p in doc.EnumerateObject()) map[p.Name] = p.Value.GetString();
            map[id] = token;
            var json = Util.Json(map);
            var psi = new System.Diagnostics.ProcessStartInfo("/usr/bin/security", "add-generic-password -a luxis -s " + KeychainService + " -w")
            { RedirectStandardInput = true, UseShellExecute = false, CreateNoWindow = true };
            using var proc = System.Diagnostics.Process.Start(psi)!;
            proc.StandardInput.Write(json);
            proc.StandardInput.Close();
            proc.WaitForExit(3000);
        }
        catch { }
    }
#endif
}
