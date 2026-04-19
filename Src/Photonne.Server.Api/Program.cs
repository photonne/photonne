using System.Diagnostics;
using System.Security.Claims;
using System.Text;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Photonne.Server.Api;
using Photonne.Server.Api.Shared.Services;
using Scalar.AspNetCore;
using Microsoft.Extensions.FileProviders;

var builder = WebApplication.CreateBuilder(args);

// Demo mode: bind DemoMode section so DemoModeOptions is available via IOptions<>.
// When Enabled=false the app runs normally; when true, the guard middleware and
// seeder/reset services activate.
builder.Services.Configure<DemoModeOptions>(
    builder.Configuration.GetSection(DemoModeOptions.SectionName));

var demoEnabled = builder.Configuration
    .GetSection(DemoModeOptions.SectionName)
    .GetValue<bool>(nameof(DemoModeOptions.Enabled));

// Cap request body size when running a public demo so a single visitor can't fill
// the host disk with a giant upload. In normal deployments Kestrel keeps its default.
if (demoEnabled)
{
    builder.WebHost.ConfigureKestrel(o =>
    {
        o.Limits.MaxRequestBodySize = 25L * 1024 * 1024;
    });
}

// Rate limiter is registered ALWAYS so endpoints can declare policies unconditionally.
// Outside demo mode every policy is a no-op (NoLimiter) — zero runtime overhead.
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;

    options.AddPolicy("demo-login", http =>
    {
        if (!demoEnabled)
            return RateLimitPartition.GetNoLimiter<string>("noop");

        var key = http.Connection.RemoteIpAddress?.ToString() ?? "unknown";
        return RateLimitPartition.GetFixedWindowLimiter(key, _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 5,
            Window = TimeSpan.FromMinutes(1),
            QueueLimit = 0
        });
    });

    options.AddPolicy("demo-upload", http =>
    {
        if (!demoEnabled)
            return RateLimitPartition.GetNoLimiter<string>("noop");

        var userId = http.User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                     ?? http.Connection.RemoteIpAddress?.ToString()
                     ?? "anonymous";
        return RateLimitPartition.GetFixedWindowLimiter(userId, _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 10,
            Window = TimeSpan.FromMinutes(1),
            QueueLimit = 0
        });
    });
});

// Add services to the container.
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
//builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

// Configurar JWT Authentication
var jwtKey = builder.Configuration["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured");
var jwtIssuer = builder.Configuration["Jwt:Issuer"] ?? "Photonne";
var jwtAudience = builder.Configuration["Jwt:Audience"] ?? "Photonne";

// CORS — cualquier origen permitido. La seguridad la gestiona el JWT, no el origen.
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod());
});

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwtIssuer,
            ValidAudience = jwtAudience,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey))
        };
        options.Events = new JwtBearerEvents
        {
            OnMessageReceived = context =>
            {
                var accessToken = context.Request.Query["access_token"];
                if (!string.IsNullOrEmpty(accessToken))
                {
                    var path = context.HttpContext.Request.Path;
                    if (path.StartsWithSegments("/api/assets/pending", StringComparison.OrdinalIgnoreCase))
                    {
                        context.Token = accessToken;
                    }
                }

                return Task.CompletedTask;
            }
        };
    });

builder.Services.AddAuthorization();

builder.AddPostgres();

builder.AddApplicationServices();

var app = builder.Build();

app.ExecuteMigrations();
await app.InitializeAdminUserAsync();
await app.EnsureFFmpegAsync();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseWebAssemblyDebugging();
    app.MapOpenApi();
    app.MapScalarApiReference();
}

app.Use(async (context, next) =>
{
    try
    {
        await next();
    }
    catch (Exception ex)
    {
        var problem = new ProblemDetails
        {
            Status = StatusCodes.Status500InternalServerError
        };

        if (ex is DbUpdateException dbEx)
        {
            problem.Title = "Error de base de datos";
            problem.Detail = BuildDbErrorDetail(dbEx);
        }
        else
        {
            problem.Title = "Error interno del servidor";
            problem.Detail = ex.Message;
        }

        problem.Extensions["traceId"] = Activity.Current?.Id ?? context.TraceIdentifier;
        problem.Extensions["stackTrace"] = ex.ToString();

        context.Response.StatusCode = problem.Status.Value;
        context.Response.ContentType = "application/problem+json";
        await context.Response.WriteAsJsonAsync(problem);
    }
});

app.UseForwardedHeaders(new ForwardedHeadersOptions
{
    ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto
});

// Security headers (CSP, X-CTO, Referrer-Policy, X-Frame-Options). Skipped for the
// dev-only Scalar/OpenAPI endpoints, which pull their UI from third-party CDNs.
// In Development we extend connect-src with ws/wss + unpkg.com so the ASP.NET Core
// Browser Refresh WebSocket and library source maps (.js.map) work under DevTools.
var isDev = app.Environment.IsDevelopment();
var connectSrc = isDev
    ? "connect-src 'self' ws: wss: https://unpkg.com"
    : "connect-src 'self'";

var csp = string.Join("; ", new[]
{
    "default-src 'self'",
    "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval' https://unpkg.com",
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://unpkg.com",
    "font-src 'self' https://fonts.gstatic.com data:",
    "img-src 'self' data: blob: https://*.basemaps.cartocdn.com",
    connectSrc,
    "worker-src 'self'",
    "manifest-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'"
});

app.Use(async (context, next) =>
{
    var path = context.Request.Path;
    var isDevDocs = path.StartsWithSegments("/scalar") || path.StartsWithSegments("/openapi");
    if (!isDevDocs)
    {
        var headers = context.Response.Headers;
        headers["Content-Security-Policy"] = csp;
        headers["X-Content-Type-Options"] = "nosniff";
        headers["Referrer-Policy"] = "strict-origin-when-cross-origin";
        headers["X-Frame-Options"] = "DENY";
    }
    await next();
});

if (builder.Configuration.GetValue<bool>("HTTPS_REDIRECT"))
{
    app.UseHttpsRedirection();
}

// IMPORTANTE: Authentication y Authorization deben ir antes de UseBlazorFrameworkFiles
app.UseCors();
app.UseAuthentication();
app.UseAuthorization();

// Demo mode guard — after auth so blocks apply only to authenticated API calls.
// No-op when DemoMode:Enabled = false.
app.UseMiddleware<DemoModeGuardMiddleware>();

// Rate limiter — always present but NoLimiter outside demo mode.
app.UseRateLimiter();

app.UseBlazorFrameworkFiles();
app.UseStaticFiles();

// Configure static files for thumbnails
var thumbnailsPath = app.Configuration["THUMBNAILS_PATH"] ?? "/data/thumbnails";

if (!Directory.Exists(thumbnailsPath))
{
    Directory.CreateDirectory(thumbnailsPath);
}

app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = new PhysicalFileProvider(thumbnailsPath),
    RequestPath = "/thumbnails"
});

app.RegisterEndpoints();

app.MapFallbackToFile("index.html");

app.Run();

static string BuildDbErrorDetail(DbUpdateException exception)
{
    if (exception.InnerException?.Message is { Length: > 0 } innerMessage)
    {
        return $"{exception.Message} | {innerMessage}";
    }

    return exception.Message;
}
