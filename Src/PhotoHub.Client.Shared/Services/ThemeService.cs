using MudBlazor;

namespace PhotoHub.Client.Shared.Services;

public class ThemeService
{
    public MudTheme Theme { get; } = new MudTheme()
    {
        PaletteLight = new PaletteLight()
        {
            Primary = "#6366f1",
            AppbarBackground = "#ffffff",
            AppbarText = "#1e1e2e",
            Background = "#f8f9fa",
            Surface = "#ffffff",
            DrawerBackground = "#f1f5f9",
            TextPrimary = "#1e1e2e",
            TextSecondary = "#64748b",
            ActionDefault = "#64748b",
        },
        PaletteDark = new PaletteDark()
        {
            Primary = "#818cf8",
            Surface = "#1a1b26",
            Background = "#0f1117",
            BackgroundGray = "#1e1f2e",
            AppbarBackground = "#1a1b26",
            DrawerBackground = "#1a1b26",
            TextPrimary = "#e2e8f0",
            TextSecondary = "#94a3b8",
            ActionDefault = "#94a3b8",
        },
        LayoutProperties = new LayoutProperties()
        {
            DefaultBorderRadius = "12px",
        },
    };
}
