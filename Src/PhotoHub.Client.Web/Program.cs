using Microsoft.AspNetCore.Components.Web;
using Microsoft.AspNetCore.Components.WebAssembly.Hosting;
using PhotoHub.Client.Web;
using MudBlazor.Services;
using PhotoHub.Client.Shared.Services;
using PhotoHub.Client.Web.Services;

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
builder.Services.AddScoped<PhotoHub.Client.Shared.Services.IAuthService, PhotoHub.Client.Web.Services.AuthService>();
builder.Services.AddScoped<PhotoHub.Client.Web.Services.AuthService>(sp => 
    (PhotoHub.Client.Web.Services.AuthService)sp.GetRequiredService<PhotoHub.Client.Shared.Services.IAuthService>());
builder.Services.AddScoped<IAssetService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new AssetService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IIndexService, IndexService>();
builder.Services.AddScoped<IThumbnailQueueService, ThumbnailQueueService>();
builder.Services.AddScoped<IFolderService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new FolderService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IPendingAssetsProvider, WebPendingAssetsProvider>();
builder.Services.AddScoped<IMapService, MapService>();
builder.Services.AddScoped<IAlbumService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new AlbumService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<ISettingsService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new PhotoHub.Client.Shared.Services.SettingsService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IUserService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new UserService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IAlbumPermissionService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new AlbumPermissionService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IFolderPermissionService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new FolderPermissionService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IAdminStatsService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new AdminStatsService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IShareService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<PhotoHub.Client.Web.Services.AuthService>();
    return new ShareService(httpClient, async () => await authService.GetTokenAsync());
});

await builder.Build().RunAsync();