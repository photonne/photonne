namespace Photonne.Server.Api.Shared.Models;

public class AssetUserTag
{
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    public Guid UserTagId { get; set; }
    public UserTag UserTag { get; set; } = null!;
}
