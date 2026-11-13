namespace Photonne.Server.Api.Shared.Models;

/// <summary>Categorías de relación que admite <see cref="Person.RelationshipCategory"/>.</summary>
public static class PersonRelationship
{
    public const int MaxCategoryLength = 32;
    public const int MaxLabelLength = 100;

    public const string Family = "family";
    public const string Partner = "partner";
    public const string Children = "children";
    public const string Friends = "friends";
    public const string Work = "work";
    public const string Pet = "pet";
    public const string Other = "other";

    public static readonly IReadOnlySet<string> Categories = new HashSet<string>(StringComparer.Ordinal)
    {
        Family, Partner, Children, Friends, Work, Pet, Other
    };
}
