using System.Reflection;
using System.Text;

namespace Luxis;

/// <summary>Generates a browser shim exposing the native bridge over the loopback server.</summary>
public static class JSBridge
{
    public static string Generate(object bridge, int port)
    {
        var sb = new StringBuilder();
        sb.AppendLine("(function(){");
        sb.AppendLine($"var base='http://127.0.0.1:{port}/';");
        sb.AppendLine("function callSync(m,a){var x=new XMLHttpRequest();x.open('POST',base+'call',false);x.setRequestHeader('Content-Type','application/json');x.send(JSON.stringify({m:m,a:a}));return x.responseText;}");
        sb.AppendLine("function callAsync(m,a){return fetch(base+'call',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({m:m,a:a})}).then(function(r){return r.text()});}");
        sb.AppendLine("var bridge={};");
        var methods = bridge.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public | BindingFlags.DeclaredOnly)
            .Where(m => m.GetParameters().All(p => p.ParameterType == typeof(string) || p.ParameterType == typeof(int) || p.ParameterType == typeof(bool)));
        foreach (var method in methods)
        {
            var async = typeof(Task<string>).IsAssignableFrom(method.ReturnType);
            var fn = async ? $"function(){{\"...a\"}}" : "";
            if (async)
                sb.AppendLine($"bridge.{method.Name}=function(){{return callAsync('{method.Name}',Array.prototype.slice.call(arguments))}};");
            else
                sb.AppendLine($"bridge.{method.Name}=function(){{return callSync('{method.Name}',Array.prototype.slice.call(arguments))}};");
        }
        sb.AppendLine("window.__luxis=bridge;window.__luxisSync=bridge;");
        sb.AppendLine("window.chrome={webview:{hostObjects:{bridge:bridge,sync:{bridge:bridge}},addEventListener:function(){}}};");
        sb.AppendLine("if(window.external&&window.external.receiveMessage){window.external.receiveMessage(function(raw){try{var d=typeof raw==='string'?JSON.parse(raw):raw;window.dispatchEvent(new MessageEvent('message',{data:d}))}catch(e){}})}");
        sb.AppendLine("})();");
        return sb.ToString();
    }
}
