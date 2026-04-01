using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Features.DatabaseBackup;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Services;
using Xabe.FFmpeg;
using Xabe.FFmpeg.Downloader;

namespace PhotoHub.Server.Api;

public static class DependencyInjection
{
    public static void AddApplicationServices(this WebApplicationBuilder builder)
    {
        builder.Services.AddMemoryCache();
        builder.Services.AddScoped<DatabaseBackupService>();
        builder.Services.AddScoped<DirectoryScanner>();
        builder.Services.AddScoped<FileHashService>();
        builder.Services.AddScoped<ExifExtractorService>();
        builder.Services.AddScoped<ThumbnailGeneratorService>();
        builder.Services.AddScoped<MediaRecognitionService>();
        builder.Services.AddScoped<SettingsService>();
        builder.Services.AddScoped<IMlJobService, MlJobService>();
        builder.Services.AddHostedService<MlJobProcessorService>();
        builder.Services.AddScoped<ExternalLibraryScanService>();
        builder.Services.AddHostedService<ExternalLibrarySchedulerService>();
        
        // Registrar AuthService
        builder.Services.AddScoped<IAuthService, AuthService>();
        
        // Registrar UserInitializationService
        builder.Services.AddScoped<UserInitializationService>();
        builder.Services.AddScoped<AssetIndexingService>();
        builder.Services.AddScoped<INotificationService, NotificationService>();

        // Configure FFmpeg
        ConfigureFFmpeg(builder.Configuration);
    }

    private static void ConfigureFFmpeg(IConfiguration configuration)
    {
        // Configure FFmpeg path if provided in configuration
        var ffmpegPath = configuration["FFMPEG_PATH"];
        if (!string.IsNullOrEmpty(ffmpegPath))
        {
            FFmpeg.SetExecutablesPath(ffmpegPath);
        }
        else if (OperatingSystem.IsWindows())
        {
            // Try common locations if not set on Windows
            var commonPaths = new[] { 
                @"C:\ffmpeg\bin", 
                @"C:\Program Files\ffmpeg\bin",
                Path.Combine(Directory.GetCurrentDirectory(), "ffmpeg")
            };
            
            bool found = false;
            foreach (var path in commonPaths)
            {
                if (Directory.Exists(path))
                {
                    FFmpeg.SetExecutablesPath(path);
                    Console.WriteLine($"[INFO] FFmpeg path set to: {path}");
                    found = true;
                    break;
                }
            }

            if (!found)
            {
                // If not found, we'll use a local 'ffmpeg' folder
                var localPath = Path.Combine(Directory.GetCurrentDirectory(), "ffmpeg");
                if (!Directory.Exists(localPath))
                {
                    Directory.CreateDirectory(localPath);
                }
                FFmpeg.SetExecutablesPath(localPath);
                Console.WriteLine($"[INFO] FFmpeg path set to local folder: {localPath}");
            }
        }
        // In Linux/Docker, if not set, Xabe.FFmpeg will look for 'ffmpeg' in the system PATH by default
    }

    public static async Task EnsureFFmpegAsync(this WebApplication app)
    {
        var ffmpegPath = FFmpeg.ExecutablesPath;

        // En Linux/Docker el path puede ser null; FFmpeg se resuelve desde el PATH del sistema
        if (string.IsNullOrEmpty(ffmpegPath))
        {
            Console.WriteLine("[INFO] FFmpeg executables path not set — relying on system PATH.");
            return;
        }

        var ffmpegExe = OperatingSystem.IsWindows() ? "ffmpeg.exe" : "ffmpeg";
        var ffprobeExe = OperatingSystem.IsWindows() ? "ffprobe.exe" : "ffprobe";

        if (!File.Exists(Path.Combine(ffmpegPath, ffmpegExe)) || !File.Exists(Path.Combine(ffmpegPath, ffprobeExe)))
        {
            Console.WriteLine("[INFO] FFmpeg or FFprobe not found. Downloading...");
            try
            {
                await FFmpegDownloader.GetLatestVersion(FFmpegVersion.Official, ffmpegPath);
                Console.WriteLine("[INFO] FFmpeg downloaded successfully.");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Failed to download FFmpeg: {ex.Message}");
            }
        }
        else
        {
            Console.WriteLine("[INFO] FFmpeg and FFprobe found.");
        }
    }

    public static void AddPostgres(this WebApplicationBuilder builder)
    {
        // Configurar Entity Framework Core con PostgreSQL
        var connectionString = builder.Configuration.GetConnectionString("Postgres") 
            ?? throw new InvalidOperationException("Connection string 'Postgres' not found.");

        builder.Services.AddDbContext<ApplicationDbContext>(options =>
            options.UseNpgsql(connectionString));
    }

    public static void RegisterEndpoints(this IEndpointRouteBuilder app)
    {
        var endpointTypes = typeof(Program).Assembly
            .GetTypes()
            .Where(t => typeof(IEndpoint).IsAssignableFrom(t) && t is { IsInterface: false, IsAbstract: false });

        foreach (var type in endpointTypes)
        {
            // Los endpoints ya no tienen dependencias scoped en el constructor,
            // así que podemos crearlos directamente sin scope
            var endpoint = (IEndpoint)Activator.CreateInstance(type)!;
            endpoint.MapEndpoint(app);
        }
    }

    public static void ExecuteMigrations(this WebApplication app)
    {
        using (var scope = app.Services.CreateScope())
        {
            var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
            try
            {
                dbContext.Database.Migrate();
                Console.WriteLine("Database migrations applied successfully.");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error applying migrations: {ex.Message}");
                // No lanzar excepción para permitir que la app continúe
            }
        }
    }

    public static async Task InitializeAdminUserAsync(this WebApplication app)
    {
        using (var scope = app.Services.CreateScope())
        {
            try
            {
                var initializationService = scope.ServiceProvider.GetRequiredService<UserInitializationService>();
                await initializationService.InitializeAdminUserAsync();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Error initializing admin user: {ex.Message}");
                // No lanzar excepción para permitir que la app continúe
            }
        }
    }
}