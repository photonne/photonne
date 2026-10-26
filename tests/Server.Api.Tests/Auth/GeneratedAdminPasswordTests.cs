using Microsoft.Extensions.Configuration;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Auth;

public sealed class GeneratedAdminPasswordTests
{
    [Fact]
    public void GeneratedPassword_MeetsThePasswordPolicy()
    {
        var auth = new AuthService(new ConfigurationBuilder().Build(), new SettingsService(null!));

        for (var i = 0; i < 50; i++)
        {
            var password = UserInitializationService.GeneratePassword();
            Assert.True(auth.ValidatePassword(password).IsValid, password);
            Assert.False(UserInitializationService.IsPlaceholderPassword(password));
        }
    }

    [Fact]
    public void PublishedDefaults_AreTreatedAsPlaceholders()
    {
        Assert.True(UserInitializationService.IsPlaceholderPassword("admin123"));
        Assert.True(UserInitializationService.IsPlaceholderPassword(" cambia_esto_por_password_seguro "));
    }
}
