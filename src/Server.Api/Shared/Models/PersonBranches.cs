namespace Photonne.Server.Api.Shared.Models;

/// <summary>Las ramas de la constelación: quién nace de la estela de quién.</summary>
public static class PersonBranches
{
    /// <summary>
    /// Si subiendo de rama en rama desde <paramref name="from"/> se llega a
    /// <paramref name="to"/>. <paramref name="branches"/> es persona → de quién nace.
    /// </summary>
    public static bool Reaches(IReadOnlyDictionary<Guid, Guid> branches, Guid from, Guid to)
    {
        var seen = new HashSet<Guid>();
        for (Guid? current = from; current is Guid id; current = branches.TryGetValue(id, out var next) ? next : null)
        {
            if (id == to) return true;
            if (!seen.Add(id)) return false;
        }
        return false;
    }
}
