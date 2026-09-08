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
// Las URLs de miniatura/contenido se construyen fuera del HttpClient (van en
// <img src>); sin esto, un ApiBaseUrl remoto (dev contra otro servidor) las
// dejaba apuntando al origen local. En producción queda vacío → relativas.
ApiConfig.BaseUrl = builder.Configuration["ApiBaseUrl"]?.TrimEnd('/') ?? string.Empty;
builder.Services.AddScoped<ApiErrorNotifier>();
builder.Services.AddScoped(sp =>
{
    var notifier = sp.GetRequiredService<ApiErrorNotifier>();
    var refreshHandler = new AuthRefreshHandler(() => sp.GetRequiredService<IAuthService>())
    {
        InnerHandler = new HttpClientHandler()
    };
    var authHeaderHandler = new AuthHeaderHandler(() => sp.GetRequiredService<IAuthService>())
    {
        InnerHandler = refreshHandler
    };
    var errorHandler = new ApiErrorHandler(notifier)
    {
        InnerHandler = authHeaderHandler
    };
    return new HttpClient(errorHandler)
    {
        BaseAddress = new Uri(apiBaseUrl)
    };
});

// Agregar MudBlazor
builder.Services.AddMudServices();

// ── Infraestructura compartida ──────────────────────────────────────────────
builder.Services.AddScoped<BackNavigationService>();
builder.Services.AddScoped<LayoutService>();
builder.Services.AddScoped<ThemeService>();
builder.Services.AddScoped<DeviceLayoutService>();
builder.Services.AddSingleton<PwaUpdateService>();

// ── Autenticación ───────────────────────────────────────────────────────────
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddScoped<AuthService>(sp =>
    (AuthService)sp.GetRequiredService<IAuthService>());

// ── Servicios de administración ─────────────────────────────────────────────
builder.Services.AddScoped<IAssetService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new AssetService(httpClient, async () => await authService.GetTokenAsync());
});
builder.Services.AddScoped<IFolderService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new FolderService(httpClient, async () => await authService.GetTokenAsync());
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
builder.Services.AddScoped<IAdminStatsService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    var authService = sp.GetRequiredService<AuthService>();
    return new AdminStatsService(httpClient, async () => await authService.GetTokenAsync());
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

// ── Indexación y colas ──────────────────────────────────────────────────────
builder.Services.AddScoped<IIndexService, IndexService>();
builder.Services.AddScoped<IAlbumsService, AlbumsService>();
builder.Services.AddScoped<ISearchCatalogService, SearchCatalogService>();
// Transient: cada página del workspace tiene su instancia con su rejilla.
builder.Services.AddTransient<BatchActions>();
builder.Services.AddScoped<IThumbnailQueueService, ThumbnailQueueService>();
builder.Services.AddScoped<IMetadataQueueService, MetadataQueueService>();
builder.Services.AddScoped<IDateRestoreService, DateRestoreService>();
builder.Services.AddScoped<IDuplicatesQueueService, DuplicatesQueueService>();
builder.Services.AddScoped<BackgroundTaskStateService>(sp =>
{
    var httpClient = sp.GetRequiredService<HttpClient>();
    return new BackgroundTaskStateService(httpClient);
});

// ── Otros ───────────────────────────────────────────────────────────────────
builder.Services.AddScoped<IDemoInfoService, DemoInfoService>();

// Visor público de enlaces compartidos (acceso anónimo por token)
builder.Services.AddScoped<IShareService>(sp =>
    new ShareService(sp.GetRequiredService<HttpClient>()));

await builder.Build().RunAsync();
