namespace Photonne.Server.Api.Shared.Services.SmartAlbums;

/// <summary>
/// One node of a smart-album rule tree (deserialized from
/// <c>Album.SmartRule</c> JSON — schema in docs/smart-albums/rule-schema.md).
/// A node is either LOGICAL (<see cref="Op"/> set, children in
/// <see cref="Conditions"/>) or a CONDITION leaf (<see cref="Type"/> set). The
/// same flexible shape carries every condition's payload; only the fields
/// relevant to a given <see cref="Type"/> are read. Self-referential, so the
/// whole tree deserializes in one pass.
/// </summary>
public sealed class SmartRuleNode
{
    // ── Logical node ──────────────────────────────────────────────────────
    /// <summary>"AND" or "OR" for a logical node; null for a condition leaf.</summary>
    public string? Op { get; set; }
    public List<SmartRuleNode>? Conditions { get; set; }

    // ── Condition leaf ────────────────────────────────────────────────────
    /// <summary>Condition type ("person", "folder", "dateRange", …) or "not".</summary>
    public string? Type { get; set; }
    /// <summary>Child for a "not" node.</summary>
    public SmartRuleNode? Condition { get; set; }

    /// <summary>"any" (OR between values) or "all" (AND / intersection).</summary>
    public string? Match { get; set; }

    public List<Guid>? PersonIds { get; set; }
    public List<Guid>? FolderIds { get; set; }
    public bool? IncludeSubfolders { get; set; }
    public DateTime? From { get; set; }
    public DateTime? To { get; set; }
    public List<string>? Labels { get; set; }
    public List<Guid>? UserTagIds { get; set; }
    public string? TagType { get; set; }
    public bool? Value { get; set; }
    public string? MediaType { get; set; }
    public string? Query { get; set; }
}
