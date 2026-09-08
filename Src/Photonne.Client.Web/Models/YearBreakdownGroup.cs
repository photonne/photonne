namespace Photonne.Client.Web.Models;

/// <summary>Un año del desglose previo de "organizar por año" al mover un lote.</summary>
public class YearBreakdownGroup
{
    public int Year { get; set; }
    public List<Guid> AssetIds { get; set; } = new();
}
