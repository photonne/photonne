using System.Diagnostics;
using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Photonne.Server.Api;
using Scalar.AspNetCore;
using Microsoft.Extensions.FileProviders;

var builder = WebApplication.CreateBuilder(args);

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

if (builder.Configuration.GetValue<bool>("HTTPS_REDIRECT"))
{
    app.UseHttpsRedirection();
}

// IMPORTANTE: Authentication y Authorization deben ir antes de UseBlazorFrameworkFiles
app.UseCors();
app.UseAuthentication();
app.UseAuthorization();

app.UseBlazorFrameworkFiles();
app.UseStaticFiles();

// Configure static files for thumbnails
var thumbnailsPath = app.Configuration["THUMBNAILS_PATH"] 
    ?? Path.Combine(Directory.GetCurrentDirectory(), "thumbnails");

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
