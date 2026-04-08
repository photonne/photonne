namespace Photonne.Client.Web.Services;

public interface ISettingsService
{
    Task<string> GetSettingAsync(string key, string defaultValue = "");
    Task<bool> SaveSettingAsync(string key, string value);
    Task<string> GetAssetsPathAsync();
    Task<int> GetProcessorCountAsync();
}
