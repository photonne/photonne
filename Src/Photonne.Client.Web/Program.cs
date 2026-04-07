using Microsoft.AspNetCore.Components.Web;
using Microsoft.AspNetCore.Components.WebAssembly.Hosting;
using Photonne.Client.Web;
using MudBlazor.Services;
using Photonne.Client.Web.Services;

var builder = WebAssemblyHostBuilder.CreateDefault(args);
builder.RootComponents.Add<App>("#app");
builder.RootComponents.Add<HeadOutlet>("head::after");

// Configurar HttpClient para la API
var apiBaseUrl = builder.Configuration["ApiBaseUrl"] ?? builder.HostEnvironment.BaseAddress;
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

// Agregar MudBlazor
builder.Services.AddMudServices();

// Agregar servicios personalizados
builder.Services.AddScoped<IAssetListNavigationState, AssetListNavigationState>();
builder.Services.AddScoped<LayoutService>();
builder.Services.AddScoped<ThemeService>();
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddScoped<AuthService>(sp =>
    (AuthService)sp.GetRequiredService<IAuthService>());
builder.Services.AddScoped<IAssetService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new AssetService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IIndexService, IndexService>();
builder.Services.AddScoped<IThumbnailQueueService, ThumbnailQueueService>();
builder.Services.AddScoped<IMetadataQueueService, MetadataQueueService>();
builder.Services.AddScoped<IDuplicatesQueueService, DuplicatesQueueService>();
builder.Services.AddScoped<IFolderService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new FolderService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddSingleton<DeviceAssetsCache>();
builder.Services.AddSingleton<AlbumsCache>();
builder.Services.AddSingleton<FoldersCache>();
builder.Services.AddScoped<ILocalFolderService, LocalFolderService>();
builder.Services.AddScoped<IPendingAssetsProvider, WebPendingAssetsProvider>();
builder.Services.AddScoped<IMapService, MapService>();
builder.Services.AddScoped<IAlbumService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new AlbumService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<ISettingsService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new SettingsService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IUserService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new UserService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IAlbumPermissionService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new AlbumPermissionService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IFolderPermissionService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new FolderPermissionService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IAdminStatsService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new AdminStatsService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IShareService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new ShareService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<INotificationService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new NotificationService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IDatabaseBackupService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new DatabaseBackupService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IExternalLibraryService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new ExternalLibraryService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IExternalLibraryPermissionService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new ExternalLibraryPermissionService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IMaintenanceService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new MaintenanceService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddSingleton<TimelineCache>();
builder.Services.AddSingleton<PwaUpdateService>();

await builder.Build().RunAsync();
