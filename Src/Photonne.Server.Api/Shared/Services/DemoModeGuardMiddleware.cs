using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Blocks destructive endpoints while the app runs in demo mode.
///
/// Blocklist (matches Backup page, user management and external libraries):
///   - User management: POST/PUT/DELETE /api/users, POST /api/users/{id}/reset-password
///   - Database backup: GET /api/admin/database/backup, POST /api/admin/database/restore
///   - External libraries: POST/PUT/DELETE /api/libraries, permissions, scan stream
///
/// Read endpoints (GET /api/users, GET /api/libraries) stay allowed so pages still render.
/// Self-service endpoints (/api/users/me, /api/users/me/change-password) are NOT blocked
/// on purpose — the demo user should still be able to update its own profile.
/// </summary>
public sealed class DemoModeGuardMiddleware
{
    private readonly RequestDelegate _next;
    private readonly IOptionsMonitor<DemoModeOptions> _options;
    private readonly ILogger<DemoModeGuardMiddleware> _logger;

    private static readonly Rule[] Rules = new[]
    {
        // ── User management ────────────────────────────────────────────────
        new Rule("POST",   @"^/api/users/?$"),
        new Rule("PUT",    @"^/api/users/[0-9a-fA-F\-]{36}/?$"),
        new Rule("DELETE", @"^/api/users/[0-9a-fA-F\-]{36}/?$"),
        new Rule("POST",   @"^/api/users/[0-9a-fA-F\-]{36}/reset-password/?$"),

        // ── Backup / restore ───────────────────────────────────────────────
        new Rule("GET",    @"^/api/admin/database/backup/?$"),
        new Rule("POST",   @"^/api/admin/database/restore/?$"),

        // ── Global settings ────────────────────────────────────────────────
        // The demo user has Admin role so the admin panel renders, but global
        // settings (workers, paths, retention, ML, scheduler...) must stay
        // immutable so visitors don't break the demo for everyone else.
        // GET /api/settings stays open so the admin pages can render values.
        new Rule("POST",   @"^/api/settings/?$"),

        // ── External libraries ─────────────────────────────────────────────
        new Rule("POST",   @"^/api/libraries/?$"),
        new Rule("PUT",    @"^/api/libraries/[0-9a-fA-F\-]{36}/?$"),
        new Rule("DELETE", @"^/api/libraries/[0-9a-fA-F\-]{36}/?$"),
        new Rule("POST",   @"^/api/libraries/[0-9a-fA-F\-]{36}/permissions/?$"),
        new Rule("DELETE", @"^/api/libraries/[0-9a-fA-F\-]{36}/permissions/[0-9a-fA-F\-]{36}/?$"),
        new Rule("GET",    @"^/api/libraries/[0-9a-fA-F\-]{36}/scan/stream/?$"),
    };

    public DemoModeGuardMiddleware(
        RequestDelegate next,
        IOptionsMonitor<DemoModeOptions> options,
        ILogger<DemoModeGuardMiddleware> logger)
    {
        _next = next;
        _options = options;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        if (!_options.CurrentValue.Enabled)
        {
            await _next(context);
            return;
        }

        var path = context.Request.Path.Value ?? string.Empty;
        var method = context.Request.Method;

        foreach (var rule in Rules)
        {
            if (rule.Matches(method, path))
            {
                _logger.LogInformation(
                    "[DEMO] Blocking {Method} {Path} (demo mode restriction)",
                    method, path);

                var problem = new ProblemDetails
                {
                    Status = StatusCodes.Status403Forbidden,
                    Title = "Acción deshabilitada en la demo",
                    Detail = "Esta acción está bloqueada en la demo pública de Photonne. "
                           + "Despliega tu propia instancia para tener acceso completo."
                };
                problem.Extensions["demoMode"] = true;

                context.Response.StatusCode = StatusCodes.Status403Forbidden;
                context.Response.ContentType = "application/problem+json";
                await context.Response.WriteAsJsonAsync(problem);
                return;
            }
        }

        await _next(context);
    }

    private sealed class Rule
    {
        private readonly string _method;
        private readonly Regex _path;

        public Rule(string method, string pathPattern)
        {
            _method = method;
            _path = new Regex(pathPattern, RegexOptions.Compiled | RegexOptions.IgnoreCase);
        }

        public bool Matches(string method, string path)
            => string.Equals(method, _method, StringComparison.OrdinalIgnoreCase)
               && _path.IsMatch(path);
    }
}
