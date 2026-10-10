using Microsoft.Extensions.Configuration;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.DemoMode;

/// <summary>
/// The demo's pinned settings come from <c>DemoMode:Settings</c>, where each nesting
/// level stands for a dot in the setting key — env var names can't carry the dot
/// through the image's <c>/bin/sh</c> entrypoint.
/// </summary>
public sealed class DemoPinnedSettingsTests
{
    private static IConfiguration Config(params (string Key, string Value)[] entries) =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(entries.Select(e => new KeyValuePair<string, string?>(e.Key, e.Value)))
            .Build();

    [Fact]
    public void NestedKey_JoinsLevelsWithADot()
    {
        // What DemoMode__Settings__ServerSettings__MapTileApiKey turns into.
        var settings = DemoModeOptions.ReadPinnedSettings(Config(
            ("DemoMode:Settings:ServerSettings:MapTileApiKey", "key-1")));

        Assert.Equal("key-1", Assert.Single(settings, kv => kv.Key == "ServerSettings.MapTileApiKey").Value);
    }

    [Fact]
    public void DottedKey_IsKeptAsIs()
    {
        // appsettings.json written with the setting key verbatim.
        var settings = DemoModeOptions.ReadPinnedSettings(Config(
            ("DemoMode:Settings:ServerSettings.MapTileApiKey", "key-2")));

        Assert.Equal("key-2", Assert.Single(settings, kv => kv.Key == "ServerSettings.MapTileApiKey").Value);
    }

    [Fact]
    public void NoSection_YieldsNothing()
    {
        var settings = DemoModeOptions.ReadPinnedSettings(Config(("DemoMode:Enabled", "true")));

        Assert.Empty(settings);
    }
}
