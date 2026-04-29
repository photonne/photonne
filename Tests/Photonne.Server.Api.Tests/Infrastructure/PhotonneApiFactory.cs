using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Options;
using Npgsql;
using Photonne.Server.Api.Shared.Services;
using Respawn;
using Testcontainers.PostgreSql;

namespace Photonne.Server.Api.Tests.Infrastructure;

/// <summary>
/// Boots the real API against an ephemeral Postgres started via Testcontainers.
/// Shared across all tests in the <see cref="IntegrationCollection"/>.
/// </summary>
public sealed class PhotonneApiFactory : WebApplicationFactory<Program>, IAsyncLifetime
{
    public const string AdminUsername = "admin";
    public const string AdminEmail = "admin@photonne.test";
    public const string AdminPassword = "Admin-Test-1234!";

    // Match the production image (docker-compose.yml). The migrations register
    // the pgvector extension and the Faces table uses vector(512), so the bare
    // `postgres:*-alpine` image fails with `extension "vector" is not available`.
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("pgvector/pgvector:pg17")
        .WithDatabase("photonne_test")
        .WithUsername("photonne")
        .WithPassword("photonne")
        .Build();

    private readonly string _internalAssetsPath = Path.Combine(
        Path.GetTempPath(),
        "photonne-tests",
        Guid.NewGuid().ToString("N"));

    private readonly string _thumbnailsPath = Path.Combine(
        Path.GetTempPath(),
        "photonne-tests-thumbs",
        Guid.NewGuid().ToString("N"));

    private Respawner? _respawner;
    private NpgsqlConnection? _respawnConnection;

    public string ConnectionString => _postgres.GetConnectionString();

    /// <summary>
    /// Managed-library root used for this test run. Equivalent of /data/assets in prod.
    /// Each run gets a unique tmp directory so trash/upload tests don't collide.
    /// </summary>
    public string InternalAssetsPath => _internalAssetsPath;

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Testing");

        builder.UseSetting("ConnectionStrings:Postgres", ConnectionString);
        // 256-bit key — HMAC-SHA256 requires ≥ 32 bytes.
        builder.UseSetting("Jwt:Key", "test-jwt-key-please-ignore-0123456789abcdefghij");
        builder.UseSetting("Jwt:Issuer", "Photonne.Tests");
        builder.UseSetting("Jwt:Audience", "Photonne.Tests");
        builder.UseSetting("AdminUser:Username", AdminUsername);
        builder.UseSetting("AdminUser:Email", AdminEmail);
        builder.UseSetting("AdminUser:Password", AdminPassword);
        builder.UseSetting("InternalAssetsPath", _internalAssetsPath);
        builder.UseSetting("THUMBNAILS_PATH", _thumbnailsPath);

        builder.ConfigureServices(services =>
        {
            // Drop background workers — they'd run real schedulers against the test DB.
            services.RemoveAll<IHostedService>();
        });
    }

    public async Task InitializeAsync()
    {
        Directory.CreateDirectory(_internalAssetsPath);
        Directory.CreateDirectory(_thumbnailsPath);
        await _postgres.StartAsync();

        // The first CreateClient() triggers host startup, which runs migrations
        // and creates the seed admin user. Pre-warm it here so the Respawner sees
        // a fully-migrated schema.
        _ = CreateClient();

        _respawnConnection = new NpgsqlConnection(ConnectionString);
        await _respawnConnection.OpenAsync();
        _respawner = await Respawner.CreateAsync(_respawnConnection, new RespawnerOptions
        {
            DbAdapter = DbAdapter.Postgres,
            SchemasToInclude = new[] { "public" },
            TablesToIgnore = new[] { new Respawn.Graph.Table("public", "__EFMigrationsHistory") }
        });
    }

    /// <summary>
    /// Deletes all rows from every table except __EFMigrationsHistory, then re-seeds
    /// the admin user so tests that rely on it keep working.
    /// </summary>
    public async Task ResetDatabaseAsync()
    {
        if (_respawner is null || _respawnConnection is null)
        {
            throw new InvalidOperationException("Respawner not initialized.");
        }

        await _respawner.ResetAsync(_respawnConnection);

        // Re-seed admin user (InitializeAdminUserAsync only runs at host startup).
        using var scope = Services.CreateScope();
        var initService = scope.ServiceProvider.GetRequiredService<Photonne.Server.Api.Shared.Services.UserInitializationService>();
        await initService.InitializeAdminUserAsync();
    }

    /// <summary>
    /// Returns a derived factory with demo-mode enabled. Swaps the
    /// IOptionsMonitor&lt;DemoModeOptions&gt; so the guard middleware sees
    /// Enabled=true without having to rebuild the Kestrel/rate-limit stack.
    /// Shares the Postgres container with the base factory.
    /// </summary>
    public WebApplicationFactory<Program> WithDemoMode()
    {
        return WithWebHostBuilder(builder =>
        {
            builder.ConfigureServices(services =>
            {
                services.RemoveAll<IOptionsMonitor<DemoModeOptions>>();
                services.AddSingleton<IOptionsMonitor<DemoModeOptions>>(
                    new StaticOptionsMonitor<DemoModeOptions>(new DemoModeOptions { Enabled = true }));
            });
        });
    }

    private sealed class StaticOptionsMonitor<T> : IOptionsMonitor<T>
    {
        public StaticOptionsMonitor(T value) => CurrentValue = value;
        public T CurrentValue { get; }
        public T Get(string? name) => CurrentValue;
        public IDisposable? OnChange(Action<T, string?> listener) => null;
    }

    async Task IAsyncLifetime.DisposeAsync()
    {
        if (_respawnConnection is not null)
        {
            await _respawnConnection.DisposeAsync();
        }
        await _postgres.DisposeAsync();
        await base.DisposeAsync();

        foreach (var dir in new[] { _internalAssetsPath, _thumbnailsPath })
        {
            try
            {
                if (Directory.Exists(dir))
                {
                    Directory.Delete(dir, recursive: true);
                }
            }
            catch
            {
                // Best-effort cleanup; don't mask test failures with cleanup errors.
            }
        }
    }
}
