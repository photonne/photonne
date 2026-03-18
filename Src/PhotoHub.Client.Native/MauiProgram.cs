using System.Globalization;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using MudBlazor.Services;
using PhotoHub.Client.Shared.Services;
using PhotoHub.Client.Native.Services;

namespace PhotoHub.Client.Native;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        // Evitar que el runtime busque ensamblados satélite (en-US/MudBlazor.resources.dll, en/MudBlazor.resources.dll)
        // que no se incluyen en el bundle de MAUI/Android y provocan warnings y posibles HttpRequestException.
        CultureInfo.DefaultThreadCurrentCulture = CultureInfo.InvariantCulture;
        CultureInfo.DefaultThreadCurrentUICulture = CultureInfo.InvariantCulture;

        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .ConfigureFonts(fonts => { fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular"); });

        builder.Services.AddMauiBlazorWebView();

        // Autorización para AuthorizeView (IAuthorizationPolicyProvider, etc.)
        builder.Services.AddAuthorizationCore();
        builder.Services.AddScoped<AuthenticationStateProvider, MauiAuthenticationStateProvider>();
        builder.Services.AddCascadingAuthenticationState();

        // Evitar spam de "Authorization failed" cuando el usuario no está autenticado (comportamiento esperado)
        builder.Logging.AddFilter("Microsoft.AspNetCore.Authorization", LogLevel.Warning);

#if DEBUG
        builder.Services.AddBlazorWebViewDeveloperTools();
        builder.Logging.AddDebug();
        builder.Logging.AddFilter("Microsoft.AspNetCore.Components.WebView", LogLevel.Trace);
#endif

        // Agregar MudBlazor
        builder.Services.AddMudServices();

        // Configurar HttpClient
        var apiBaseUrl = "http://10.0.2.2:5178"; // Android Emulator address for localhost
#if IOS || MACCATALYST || WINDOWS
        apiBaseUrl = "http://localhost:5178";
#endif

        builder.Services.AddScoped<ApiErrorNotifier>();
        builder.Services.AddScoped(sp =>
        {
            var notifier = sp.GetRequiredService<ApiErrorNotifier>();
            var refreshHandler = new AuthRefreshHandler(() => sp.GetRequiredService<IAuthService>())
            {
                InnerHandler = new HttpClientHandler()
            };
            var errorHandler = new ApiErrorHandler(notifier)
            {
                InnerHandler = refreshHandler
            };
            return new HttpClient(errorHandler)
            {
                BaseAddress = new Uri(apiBaseUrl)
            };
        });

        // Registrar servicios
        builder.Services.AddScoped<IAssetListNavigationState, AssetListNavigationState>();
        builder.Services.AddScoped<LayoutService>();
        builder.Services.AddScoped<ThemeService>();
        builder.Services.AddScoped<IAuthService, MauiAuthService>();
        builder.Services.AddScoped<MauiAuthService>(sp => 
            (MauiAuthService)sp.GetRequiredService<IAuthService>());
        
        builder.Services.AddScoped<IAssetService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new AssetService(httpClient, async () => await authService.GetTokenAsync());
        });
        
        builder.Services.AddScoped<IIndexService, IndexService>();
        builder.Services.AddScoped<IThumbnailQueueService, ThumbnailQueueService>();
        builder.Services.AddScoped<IMetadataQueueService, MetadataQueueService>();
        builder.Services.AddScoped<IDuplicatesQueueService, DuplicatesQueueService>();

        builder.Services.AddScoped<IFolderService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new FolderService(httpClient, async () => await authService.GetTokenAsync());
        });
        
        builder.Services.AddScoped<IPendingAssetsProvider, MauiPendingAssetsProvider>();
        builder.Services.AddScoped<IMapService, MapService>();
        
        builder.Services.AddScoped<IAlbumService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new AlbumService(httpClient, async () => await authService.GetTokenAsync());
        });
        
        builder.Services.AddScoped<ISettingsService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new PhotoHub.Client.Shared.Services.SettingsService(httpClient, async () => await authService.GetTokenAsync());
        });
        
        builder.Services.AddScoped<IUserService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new UserService(httpClient, async () => await authService.GetTokenAsync());
        });
        
        builder.Services.AddScoped<IAlbumPermissionService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new AlbumPermissionService(httpClient, async () => await authService.GetTokenAsync());
        });
        
        builder.Services.AddScoped<IFolderPermissionService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new FolderPermissionService(httpClient, async () => await authService.GetTokenAsync());
        });
        
        builder.Services.AddScoped<IAdminStatsService>(sp =>
        {
            var httpClient = sp.GetRequiredService<HttpClient>();
            var authService = sp.GetRequiredService<IAuthService>();
            return new AdminStatsService(httpClient, async () => await authService.GetTokenAsync());
        });

        builder.Services.AddMudBlazorDialog();
        builder.Services.AddMudBlazorSnackbar();

        return builder.Build();
    }
}